package ltd.starthub.muly.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

import com.bumptech.glide.Glide;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.material.chip.Chip;
import ltd.starthub.muly.R;
import ltd.starthub.muly.common.DiffUtilCallback;
import ltd.starthub.muly.common.LoadingState;
import ltd.starthub.muly.data.ArticleDataSource;
import ltd.starthub.muly.data.ArticleSectionDataSource;
import com.thefinestartist.finestwebview.FinestWebView;

import java.util.ArrayList;
import java.util.List;

import jp.wasabeef.recyclerview.adapters.SlideInBottomAnimationAdapter;
import jp.wasabeef.recyclerview.adapters.SlideInLeftAnimationAdapter;
import ltd.starthub.muly.data.models.Article;
import ltd.starthub.muly.data.models.ArticleSection;
import me.everything.android.ui.overscroll.OverScrollDecoratorHelper;

public class NewsFragment extends Fragment {

    private NewsFragmentViewModel mModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mModel = new ViewModelProvider(this).get(NewsFragmentViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_news, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.header_back).setVisibility(View.INVISIBLE);
        TextView title = view.findViewById(R.id.header_title);
        title.setText(R.string.news_label);
        view.findViewById(R.id.header_more).setVisibility(View.INVISIBLE);
        RecyclerView articles = view.findViewById(R.id.articles);
        ArticleAdapter adapter1 = new ArticleAdapter();
        articles.setAdapter(new SlideInLeftAnimationAdapter(adapter1));
        mModel.articles.observe(getViewLifecycleOwner(), adapter1::submitList);
        SwipeRefreshLayout swipe = view.findViewById(R.id.swipe);
        swipe.setOnRefreshListener(() -> {
            ArticleDataSource source = mModel.factory1.source.getValue();
            if (source != null) {
                source.invalidate();
            }
        });
        View empty = view.findViewById(R.id.empty);
        View loading1 = view.findViewById(R.id.loading1);
        mModel.state1.observe(getViewLifecycleOwner(), state -> {
            if (state != LoadingState.LOADING) {
                swipe.setRefreshing(false);
            }

            List<?> list = mModel.articles.getValue();
            if (state == LoadingState.LOADING) {
                empty.setVisibility(View.GONE);
            } else {
                empty.setVisibility(list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
            }

            loading1.setVisibility(state == LoadingState.LOADING ? View.VISIBLE : View.GONE);
        });
        RecyclerView sections = view.findViewById(R.id.sections);
        LinearLayoutManager llm =
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
        sections.setLayoutManager(llm);
        ArticleSectionAdapter adapter2 = new ArticleSectionAdapter();
        sections.setAdapter(new SlideInBottomAnimationAdapter(adapter2));
        OverScrollDecoratorHelper.setUpOverScroll(
                sections, OverScrollDecoratorHelper.ORIENTATION_HORIZONTAL);
        mModel.sections.observe(getViewLifecycleOwner(), adapter2::submitList);
        mModel.selection.observe(getViewLifecycleOwner(), integers -> {
            mModel.factory1.sections = integers;
            ArticleDataSource source = mModel.factory1.source.getValue();
            if (source != null) {
                source.invalidate();
            }
        });
        if (getResources().getBoolean(R.bool.admob_news_ad_enabled)) {
            AdView ad = new AdView(requireContext());
            ad.setAdSize(AdSize.BANNER);
            ad.setAdUnitId(getString(R.string.admob_news_ad_id));
            ad.loadAd(new AdRequest.Builder().build());
            LinearLayout banner = view.findViewById(R.id.banner);
            banner.addView(ad);
        }
    }

    public static NewsFragment newInstance() {
        return new NewsFragment();
    }

    private void openArticleReader(Article article) {
        new FinestWebView.Builder(requireActivity())
                .titleDefault(article.title)
                .show(article.link);
    }

    private class ArticleAdapter extends PagedListAdapter<Article, ArticleViewHolder> {

        public ArticleAdapter() {
            super(new DiffUtilCallback<>(i -> i.id));
        }

        @Override
        public void onBindViewHolder(@NonNull ArticleViewHolder holder, int position) {
            Article article = getItem(position);
            //noinspection ConstantConditions
            Glide.with(requireContext())
                    .load(article.image)
                    .placeholder(R.drawable.image_placeholder)
                    .into(holder.image);
            holder.title.setText(article.title);
            holder.snippet.setText(article.snippet);
            if (TextUtils.isEmpty(article.source)) {
                holder.publisher.setVisibility(View.GONE);
                holder.publisherContainer.setVisibility(View.GONE);
            } else {
                holder.publisher.setText(article.source);
                holder.publisher.setVisibility(View.VISIBLE);
                holder.publisherContainer.setVisibility(View.VISIBLE);
            }

            holder.when.setText(
                    DateUtils.getRelativeTimeSpanString(
                            requireContext(), article.publishedAt.getTime(), true));
            holder.image.setOnClickListener(v -> openArticleReader(article));
            holder.title.setOnClickListener(v -> openArticleReader(article));
            holder.snippet.setOnClickListener(v -> openArticleReader(article));
        }

        @NonNull
        @Override
        public ArticleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_article, parent, false);
            return new ArticleViewHolder(view);
        }
    }

    private class ArticleSectionAdapter extends PagedListAdapter<ArticleSection, ArticleSectionViewHolder> {

        public ArticleSectionAdapter() {
            super(new DiffUtilCallback<>(i -> i.id));
        }

        @Override
        public void onBindViewHolder(@NonNull ArticleSectionViewHolder holder, int position) {
            ArticleSection section = getItem(position);
            //noinspection ConstantConditions
            holder.chip.setText(section.name);
            List<Integer> now = mModel.selection.getValue();
            holder.chip.setChecked(now != null && now.contains(section.id));
            holder.chip.setOnCheckedChangeListener((v, checked) -> {
                List<Integer> then = mModel.selection.getValue();
                if (checked && !then.contains(section.id)) {
                    then.add(section.id);
                } else if (!checked && then.contains(section.id)) {
                    then.remove((Integer) section.id);
                }

                mModel.selection.postValue(then);
            });
        }

        @NonNull
        @Override
        public ArticleSectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_article_section, parent, false);
            return new ArticleSectionViewHolder(view);
        }
    }

    private static class ArticleSectionViewHolder extends RecyclerView.ViewHolder {

        public Chip chip;

        public ArticleSectionViewHolder(@NonNull View root) {
            super(root);
            chip = root.findViewById(R.id.chip);
            chip.setCheckable(true);
        }
    }

    private static class ArticleViewHolder extends RecyclerView.ViewHolder {

        ImageView image;
        TextView title;
        TextView snippet;
        TextView publisher;
        View publisherContainer;
        TextView when;

        public ArticleViewHolder(@NonNull View root) {
            super(root);
            image = root.findViewById(R.id.image);
            title = root.findViewById(R.id.title);
            snippet = root.findViewById(R.id.snippet);
            publisher = root.findViewById(R.id.publisher);
            publisherContainer = root.findViewById(R.id.publisher_container);
            when = root.findViewById(R.id.when);
        }
    }

    public static class NewsFragmentViewModel extends ViewModel {

        public NewsFragmentViewModel() {
            PagedList.Config config1 = new PagedList.Config.Builder()
                    .setPageSize(10)
                    .build();
            factory1 = new ArticleDataSource.Factory(null);
            state1 = Transformations.switchMap(factory1.source, input -> input.state);
            articles = new LivePagedListBuilder<>(factory1, config1).build();
            PagedList.Config config2 = new PagedList.Config.Builder()
                    .setPageSize(100)
                    .build();
            factory2 = new ArticleSectionDataSource.Factory();
            state2 = Transformations.switchMap(factory2.source, input -> input.state);
            sections = new LivePagedListBuilder<>(factory2, config2).build();
        }

        public final LiveData<PagedList<Article>> articles;
        public final ArticleDataSource.Factory factory1;
        public final ArticleSectionDataSource.Factory factory2;
        public final LiveData<PagedList<ArticleSection>> sections;
        public final MutableLiveData<List<Integer>> selection = new MutableLiveData<>(new ArrayList<>());
        public final LiveData<LoadingState> state1;
        public final LiveData<LoadingState> state2;
    }
}
