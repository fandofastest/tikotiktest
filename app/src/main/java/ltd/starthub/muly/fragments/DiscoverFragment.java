package ltd.starthub.muly.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.arlib.floatingsearchview.FloatingSearchView;
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

import java.util.ArrayList;
import java.util.List;

import jp.wasabeef.recyclerview.adapters.SlideInBottomAnimationAdapter;
import jp.wasabeef.recyclerview.adapters.SlideInLeftAnimationAdapter;
import ltd.starthub.muly.MainApplication;
import ltd.starthub.muly.R;
import ltd.starthub.muly.activities.MainActivity;
import ltd.starthub.muly.common.DiffUtilCallback;
import ltd.starthub.muly.common.LoadingState;
import ltd.starthub.muly.data.ClipDataSource;
import ltd.starthub.muly.data.ClipItemDataSource;
import ltd.starthub.muly.data.ClipSectionDataSource;
import ltd.starthub.muly.data.api.REST;
import ltd.starthub.muly.data.models.Clip;
import ltd.starthub.muly.data.models.ClipSection;
import ltd.starthub.muly.data.models.User;
import ltd.starthub.muly.data.models.Wrappers;
import ltd.starthub.muly.utils.TextFormatUtil;
import me.everything.android.ui.overscroll.OverScrollDecoratorHelper;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DiscoverFragment extends Fragment {

    private static final String TAG = "DiscoverFragment";

    private Call<Wrappers.Paginated<User>> mCall;
    private DiscoverFragmentViewModel mModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mModel = new ViewModelProvider(this).get(DiscoverFragmentViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_discover, container, false);
    }

    @Override
    @SuppressLint("SetTextI18n")
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.header_back).setVisibility(View.INVISIBLE);
        TextView title = view.findViewById(R.id.header_title);
        title.setText(R.string.discover_label);
        view.findViewById(R.id.header_more).setVisibility(View.INVISIBLE);
        RecyclerView sections = view.findViewById(R.id.sections);
        VerticalAdapter adapter = new VerticalAdapter();
        sections.setAdapter(new SlideInLeftAnimationAdapter(adapter));
        SwipeRefreshLayout swipe = view.findViewById(R.id.swipe);
        swipe.setOnRefreshListener(() -> {
            ClipSectionDataSource source = mModel.factory.source.getValue();
            if (source != null) {
                source.invalidate();
            }
        });
        mModel.sections.observe(getViewLifecycleOwner(), adapter::submitList);
        View loading = view.findViewById(R.id.loading);
        mModel.state1.observe(getViewLifecycleOwner(), state -> {
            if (state != LoadingState.LOADING) {
                swipe.setRefreshing(false);
            }
            loading.setVisibility(state == LoadingState.LOADING ? View.VISIBLE : View.GONE);
        });
        FloatingSearchView search = view.findViewById(R.id.search);
        search.setOnBindSuggestionCallback((v, icon, text, item, position) -> {
            User suggestion = (User)item;
            if (TextUtils.isEmpty(suggestion.photo)) {
                Glide.with(requireContext())
                        .load(R.drawable.photo_placeholder)
                        .circleCrop()
                        .into(icon);
            } else {
                Glide.with(requireContext())
                        .load(suggestion.photo)
                        .circleCrop()
                        .into(icon);
            }
            text.setText('@' + suggestion.username);
        });
        search.setOnQueryChangeListener((previous, now) -> {
            if (!TextUtils.isEmpty(previous) && TextUtils.isEmpty(now)) {
                search.clearSuggestions();
            } else {
                findSuggestions(now);
            }
        });
        search.setOnSearchListener(new FloatingSearchView.OnSearchListener() {

            @Override
            public void onSuggestionClicked(SearchSuggestion ss) {
                search.clearQuery();
                User suggestion = (User)ss;
                showProfile(suggestion.id);
            }

            @Override
            public void onSearchAction(String q) { }
        });
        mModel.state2.observe(getViewLifecycleOwner(), state -> {
            if (state == LoadingState.LOADING) {
                search.showProgress();
            } else {
                search.hideProgress();
            }
        });
        mModel.suggestions.observe(getViewLifecycleOwner(), search::swapSuggestions);
        if (getResources().getBoolean(R.bool.admob_discover_ad_enabled)) {
            AdView ad = new AdView(requireContext());
            ad.setAdSize(AdSize.BANNER);
            ad.setAdUnitId(getString(R.string.admob_discover_ad_id));
            ad.loadAd(new AdRequest.Builder().build());
            LinearLayout banner = view.findViewById(R.id.banner);
            banner.addView(ad);
        }
    }

    private void findSuggestions(String q) {
        if (mCall != null) {
            mCall.cancel();
        }
        mModel.state2.postValue(LoadingState.LOADING);
        REST rest = MainApplication.getContainer().get(REST.class);
        mCall = rest.usersIndex(q, 1);
        mCall.enqueue(new Callback<Wrappers.Paginated<User>>() {

            @Override
            public void onResponse(
                    @Nullable Call<Wrappers.Paginated<User>> call,
                    @Nullable Response<Wrappers.Paginated<User>> response) {
                if (response != null && response.isSuccessful()) {
                    //noinspection ConstantConditions
                    List<User> suggestions = response.body().data;
                    Log.v(TAG, "Found " + suggestions.size() + " matching q=" + q);
                    mModel.suggestions.postValue(suggestions);
                    mModel.state2.postValue(LoadingState.LOADED);
                } else {
                    mModel.state2.postValue(LoadingState.ERROR);
                }
            }

            @Override
            public void onFailure(
                    @Nullable Call<Wrappers.Paginated<User>> call,
                    @Nullable Throwable t
            ) {
                Log.e(TAG, "Failed to load user suggestions from server.", t);
                mModel.state2.postValue(LoadingState.ERROR);
            }
        });
    }

    public static DiscoverFragment newInstance() {
        return new DiscoverFragment();
    }

    private void showPlayerSlider(int clip, List<ClipSection> sections) {
        ArrayList<Integer> ids = new ArrayList<>();
        for (ClipSection section : sections) {
            ids.add(section.id);
        }
        Bundle params = new Bundle();
        params.putIntegerArrayList(ClipDataSource.PARAM_SECTIONS, ids);
        ((MainActivity)requireActivity()).showPlayerSlider(clip, params);
    }

    private void showProfile(int user) {
        ((MainActivity)requireActivity()).showProfilePage(user);
    }

    private class HorizontalAdapter extends PagedListAdapter<Clip, ClipViewHolder> {

        protected HorizontalAdapter() {
            super(new DiffUtilCallback<>(i -> i.id));
        }

        @NonNull
        @Override
        public ClipViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_clip_discover, parent, false);
            return new ClipViewHolder(view);
        }

        @Override
        @SuppressLint("SetTextI18n")
        public void onBindViewHolder(@NonNull ClipViewHolder holder, int position) {
            Clip clip = getItem(position);
            //noinspection ConstantConditions
            holder.likes.setText(TextFormatUtil.toShortNumber(clip.likesCount));
            //noinspection unchecked
            Glide.with(requireContext())
                    .asGif()
                    .load(clip.preview)
                    .thumbnail(new RequestBuilder[]{
                            Glide.with(requireContext()).load(clip.screenshot).centerCrop()
                    })
                    .apply(RequestOptions.placeholderOf(R.drawable.image_placeholder).centerCrop())
                    .into(holder.preview);
            holder.itemView.setOnClickListener(v -> showPlayerSlider(clip.id, clip.sections));
        }
    }

    private class VerticalAdapter extends PagedListAdapter<ClipSection, SectionViewHolder> {

        protected VerticalAdapter() {
            super(new DiffUtilCallback<>(i -> i.id));
        }

        @Override
        public void onBindViewHolder(@NonNull SectionViewHolder holder, int position) {
            ClipSection section = getItem(position);
            //noinspection ConstantConditions
            holder.title.setText(section.name);
            holder.load(section.id);
        }

        @NonNull
        @Override
        public SectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View root = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_discover_section, parent, false);
            return new SectionViewHolder(root);
        }
    }

    public static class DiscoverFragmentViewModel extends ViewModel {

        public DiscoverFragmentViewModel() {
            PagedList.Config config = new PagedList.Config.Builder()
                    .setPageSize(10)
                    .build();
            factory = new ClipSectionDataSource.Factory();
            state1 = Transformations.switchMap(factory.source, input -> input.state);
            sections = new LivePagedListBuilder<>(factory, config).build();
        }

        public final LiveData<PagedList<ClipSection>> sections;
        public final ClipSectionDataSource.Factory factory;
        public final LiveData<LoadingState> state1;
        public final MutableLiveData<LoadingState> state2 = new MutableLiveData<>(LoadingState.IDLE);
        public final MutableLiveData<List<User>> suggestions = new MutableLiveData<>();
    }

    private static class ClipViewHolder extends RecyclerView.ViewHolder {

        public ImageView preview;
        public TextView likes;

        public ClipViewHolder(@NonNull View root) {
            super(root);
            preview = root.findViewById(R.id.preview);
            likes = root.findViewById(R.id.likes);
        }
    }

    private class SectionViewHolder extends RecyclerView.ViewHolder {

        public TextView title;
        public ProgressBar loading;
        public RecyclerView clips;

        public LiveData<PagedList<Clip>> items;
        public LiveData<LoadingState> state;

        public SectionViewHolder(@NonNull View root) {
            super(root);
            title = root.findViewById(R.id.title);
            clips = root.findViewById(R.id.clips);
            loading = root.findViewById(R.id.loading);
            LinearLayoutManager llm =
                    new LinearLayoutManager(
                            requireContext(), LinearLayoutManager.HORIZONTAL, false);
            clips.setLayoutManager(llm);
            OverScrollDecoratorHelper.setUpOverScroll(
                    clips, OverScrollDecoratorHelper.ORIENTATION_HORIZONTAL);
        }

        public void load(int section) {
            HorizontalAdapter adapter = new HorizontalAdapter();
            clips.setAdapter(new SlideInBottomAnimationAdapter(adapter));
            PagedList.Config config = new PagedList.Config.Builder()
                    .setPageSize(10)
                    .build();
            ArrayList<Integer> sections = new ArrayList<>();
            sections.add(section);
            Bundle params = new Bundle();
            params.putIntegerArrayList(ClipDataSource.PARAM_SECTIONS, sections);
            ClipItemDataSource.Factory factory = new ClipItemDataSource.Factory(params);
            state = Transformations.switchMap(factory.source, input -> input.state);
            state.observe(getViewLifecycleOwner(), state ->
                    loading.setVisibility(state == LoadingState.LOADING ? View.VISIBLE : View.GONE));
            items = new LivePagedListBuilder<>(factory, config).build();
            items.observe(getViewLifecycleOwner(), adapter::submitList);
        }
    }
}
