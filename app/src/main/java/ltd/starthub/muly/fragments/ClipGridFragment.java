package ltd.starthub.muly.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.facebook.drawee.view.SimpleDraweeView;

import java.util.List;

import jp.wasabeef.recyclerview.adapters.SlideInBottomAnimationAdapter;
import ltd.starthub.muly.R;
import ltd.starthub.muly.activities.MainActivity;
import ltd.starthub.muly.common.DiffUtilCallback;
import ltd.starthub.muly.common.LoadingState;
import ltd.starthub.muly.data.ClipItemDataSource;
import ltd.starthub.muly.data.models.Clip;
import ltd.starthub.muly.utils.TextFormatUtil;

public class ClipGridFragment extends Fragment {

    private static final String ARG_PARAMS = "params";

    private ClipGridFragmentViewModel mModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = getArguments();
        Bundle params = null;
        if (arguments != null) {
            params = arguments.getBundle(ARG_PARAMS);
        }
        ClipGridFragmentViewModel.Factory factory =
                new ClipGridFragmentViewModel.Factory(params != null ? params : Bundle.EMPTY);
        mModel = new ViewModelProvider(this, factory).get(ClipGridFragmentViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_clip_grid, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView clips = view.findViewById(R.id.clips);
        ClipGridAdapter adapter = new ClipGridAdapter();
        clips.setAdapter(new SlideInBottomAnimationAdapter(adapter));
        GridLayoutManager glm = new GridLayoutManager(requireContext(), 3);
        clips.setLayoutManager(glm);
        mModel.clips.observe(getViewLifecycleOwner(), adapter::submitList);
        SwipeRefreshLayout swipe = view.findViewById(R.id.swipe);
        swipe.setOnRefreshListener(() -> {
            ClipItemDataSource source = mModel.factory.source.getValue();
            if (source != null) {
                source.invalidate();
            }
        });
        View empty = view.findViewById(R.id.empty);
        View loading = view.findViewById(R.id.loading);
        mModel.state.observe(getViewLifecycleOwner(), state -> {
            if (state != LoadingState.LOADING) {
                swipe.setRefreshing(false);
            }
            List<?> list = mModel.clips.getValue();
            if (state == LoadingState.LOADING) {
                empty.setVisibility(View.GONE);
            } else {
                empty.setVisibility(list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
            }
            loading.setVisibility(state == LoadingState.LOADING ? View.VISIBLE : View.GONE);
        });
    }

    public static ClipGridFragment newInstance(@Nullable Bundle params) {
        ClipGridFragment fragment = new ClipGridFragment();
        Bundle arguments = new Bundle();
        arguments.putBundle(ARG_PARAMS, params);
        fragment.setArguments(arguments);
        return fragment;
    }

    private void showClipPlayer(int clip) {
        ((MainActivity)requireActivity()).showPlayerSlider(clip, null);
    }

    private class ClipGridAdapter extends PagedListAdapter<Clip, ClipGridViewHolder> {

        protected ClipGridAdapter() {
            super(new DiffUtilCallback<>(i -> i.id));
        }

        @NonNull
        @Override
        public ClipGridViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_clip, parent, false);
            return new ClipGridViewHolder(view);
        }

        @Override
        @SuppressLint("SetTextI18n")
        public void onBindViewHolder(@NonNull ClipGridViewHolder holder, int position) {
            Clip clip = getItem(position);
            //noinspection ConstantConditions
            holder.likes.setText(TextFormatUtil.toShortNumber(clip.likesCount));
            holder.preview.setImageURI(clip.screenshot);
            holder.itemView.setOnClickListener(v -> showClipPlayer(clip.id));
        }
    }

    public static class ClipGridFragmentViewModel extends ViewModel {

        public ClipGridFragmentViewModel(@NonNull Bundle params) {
            PagedList.Config config = new PagedList.Config.Builder()
                    .setPageSize(10)
                    .build();
            factory = new ClipItemDataSource.Factory(params);
            state = Transformations.switchMap(factory.source, input -> input.state);
            clips = new LivePagedListBuilder<>(factory, config).build();
        }

        public final LiveData<PagedList<Clip>> clips;
        public final ClipItemDataSource.Factory factory;
        public final LiveData<LoadingState> state;

        private static class Factory implements ViewModelProvider.Factory {

            @NonNull private final Bundle mParams;

            public Factory(@NonNull Bundle params) {
                mParams = params;
            }

            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                //noinspection unchecked
                return (T)new ClipGridFragmentViewModel(mParams);
            }
        }
    }

    private static class ClipGridViewHolder extends RecyclerView.ViewHolder {

        public SimpleDraweeView preview;
        public TextView likes;

        public ClipGridViewHolder(@NonNull View root) {
            super(root);
            preview = root.findViewById(R.id.preview);
            likes = root.findViewById(R.id.likes);
        }
    }
}
