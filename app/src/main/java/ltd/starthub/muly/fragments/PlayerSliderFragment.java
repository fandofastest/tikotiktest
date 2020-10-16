package ltd.starthub.muly.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.AsyncPagedListDiffer;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.AdapterListUpdateCallback;
import androidx.recyclerview.widget.AsyncDifferConfig;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.pixplicity.easyprefs.library.Prefs;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import ltd.starthub.muly.R;
import ltd.starthub.muly.SharedConstants;
import ltd.starthub.muly.common.DiffUtilCallback;
import ltd.starthub.muly.common.LoadingState;
import ltd.starthub.muly.data.ClipDataSource;
import ltd.starthub.muly.data.ClipPageDataSource;
import ltd.starthub.muly.data.models.Clip;
import ltd.starthub.muly.events.ClipDeletedEvent;

public class PlayerSliderFragment extends Fragment {

    private static final String ARG_PARAMS = "params";
    private static final String TAG = "PlayerSliderFragment";

    private PlayerSliderFragmentViewModel mModel;

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ClipDeletedEvent event) {
        ClipPageDataSource source = mModel.factory.source.getValue();
        if (source != null) {
            source.invalidate();
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle params = requireArguments().getBundle(ARG_PARAMS);
        if (params == null) {
            params = new Bundle();
        }
        long seen = Prefs.getLong(SharedConstants.PREF_SEEN_UNTIL, 0);
        params.putLong(ClipDataSource.PARAM_SEEN, seen);
        Prefs.putLong(SharedConstants.PREF_SEEN_UNTIL, System.currentTimeMillis());
        PlayerSliderFragmentViewModel.Factory factory =
                new PlayerSliderFragmentViewModel.Factory(params);
        mModel = new ViewModelProvider(this, factory).get(PlayerSliderFragmentViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_player_slider, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        PlayerSliderAdapter adapter = new PlayerSliderAdapter(this);
        ViewPager2 pager = view.findViewById(R.id.pager);
        pager.setAdapter(adapter);
        mModel.clips.observe(getViewLifecycleOwner(), adapter::submitList);
        SwipeRefreshLayout swipe = view.findViewById(R.id.swipe);
        swipe.setOnRefreshListener(() -> {
            ClipPageDataSource source = mModel.factory.source.getValue();
            if (source != null) {
                source.invalidate();
            }
        });
        final View loading = view.findViewById(R.id.loading);
        mModel.state.observe(getViewLifecycleOwner(), state -> {
            Log.v(TAG, "Loading state is " + state.name() + ".");
            if (state != LoadingState.LOADING) {
                swipe.setRefreshing(false);
            }
            loading.setVisibility(state == LoadingState.LOADING ? View.VISIBLE : View.GONE);
        });
        if (getResources().getBoolean(R.bool.admob_player_ad_enabled)) {
            InterstitialAd ad = new InterstitialAd(requireContext());
            ad.setAdUnitId(getString(R.string.admob_player_ad_id));
            ad.setAdListener(new AdListener() {

                @Override
                public void onAdClosed() {
                    ad.loadAd(new AdRequest.Builder().build());
                }
            });
            ad.loadAd(new AdRequest.Builder().build());
            int interval = getResources().getInteger(R.integer.admob_player_ad_interval);
            pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {

                @Override
                public void onPageSelected(int position) {
                    mModel.viewed++;
                    if (mModel.viewed >= interval && ad.isLoaded()) {
                        ad.show();
                        mModel.viewed = 0;
                    }
                }
            });
        }
    }

    public static PlayerSliderFragment newInstance(@Nullable Bundle params) {
        PlayerSliderFragment fragment = new PlayerSliderFragment();
        Bundle arguments = new Bundle();
        arguments.putBundle(ARG_PARAMS, params);
        fragment.setArguments(arguments);
        return fragment;
    }

    private static class PlayerSliderAdapter extends FragmentStateAdapter {

        private AsyncPagedListDiffer<Clip> mDiffer;

        public PlayerSliderAdapter(@NonNull Fragment fragment) {
            super(fragment);
            mDiffer = new AsyncPagedListDiffer<>(
                    new AdapterListUpdateCallback(this),
                    new AsyncDifferConfig.Builder<>(new DiffUtilCallback<Clip>(i -> i.id)).build()
            );
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            Clip clip = mDiffer.getItem(position);
            //noinspection ConstantConditions
            return PlayerFragment.newInstance(clip.id);
        }

        @Override
        public int getItemCount() {
            return mDiffer.getItemCount();
        }

        public void submitList(PagedList<Clip> list) {
            mDiffer.submitList(list);
        }
    }

    public static class PlayerSliderFragmentViewModel extends ViewModel {

        public PlayerSliderFragmentViewModel(@NonNull Bundle params) {
            PagedList.Config config = new PagedList.Config.Builder()
                    .setPageSize(10)
                    .build();
            factory = new ClipPageDataSource.Factory(params);
            state = Transformations.switchMap(factory.source, input -> input.state);
            clips = new LivePagedListBuilder<>(factory, config).build();
        }

        public final LiveData<PagedList<Clip>> clips;
        public final ClipPageDataSource.Factory factory;
        public final LiveData<LoadingState> state;
        public int viewed = 0;

        private static class Factory implements ViewModelProvider.Factory {

            @NonNull private final Bundle mParams;

            public Factory(@NonNull Bundle params) {
                mParams = params;
            }

            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                //noinspection unchecked
                return (T)new PlayerSliderFragmentViewModel(mParams);
            }
        }
    }
}
