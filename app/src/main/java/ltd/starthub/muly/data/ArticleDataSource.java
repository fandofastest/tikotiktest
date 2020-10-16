package ltd.starthub.muly.data;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.DataSource;
import androidx.paging.PageKeyedDataSource;

import ltd.starthub.muly.MainApplication;
import ltd.starthub.muly.common.LoadingState;
import ltd.starthub.muly.data.api.REST;
import ltd.starthub.muly.data.models.Article;
import ltd.starthub.muly.data.models.Wrappers;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ArticleDataSource extends PageKeyedDataSource<Integer, Article> {

    private static final String TAG = "ArticleDataSource";

    @Nullable private final Iterable<Integer> mSections;

    public final MutableLiveData<LoadingState> state = new MutableLiveData<>(LoadingState.IDLE);

    public ArticleDataSource(@Nullable Iterable<Integer> sections) {
        mSections = sections;
    }

    @Override
    public void loadInitial(
            @NonNull LoadInitialParams<Integer> params,
            @NonNull final LoadInitialCallback<Integer, Article> callback
    ) {
        state.postValue(LoadingState.LOADING);
        REST rest = MainApplication.getContainer().get(REST.class);
        rest.articlesIndex(null, mSections, 1)
                .enqueue(new Callback<Wrappers.Paginated<Article>>() {

                    @Override
                    public void onResponse(
                            @Nullable Call<Wrappers.Paginated<Article>> call,
                            @Nullable Response<Wrappers.Paginated<Article>> response
                    ) {
                        //noinspection ConstantConditions
                        Log.v(TAG, "Server responded with " + response.code() + " status.");
                        if (response.isSuccessful()) {
                            Wrappers.Paginated<Article> articles = response.body();
                            //noinspection ConstantConditions
                            callback.onResult(articles.data,null, 2);
                            state.postValue(LoadingState.LOADED);
                        } else {
                            state.postValue(LoadingState.ERROR);
                        }
                    }

                    @Override
                    public void onFailure(
                            @Nullable Call<Wrappers.Paginated<Article>> call,
                            @Nullable Throwable t
                    ) {
                        Log.e(TAG, "Fetching articles has failed.", t);
                        state.postValue(LoadingState.ERROR);
                    }
                });
    }

    @Override
    public void loadBefore(
            @NonNull LoadParams<Integer> params,
            @NonNull LoadCallback<Integer, Article> callback
    ) {
    }

    @Override
    public void loadAfter(
            @NonNull final LoadParams<Integer> params,
            @NonNull final LoadCallback<Integer, Article> callback
    ) {
        state.postValue(LoadingState.LOADING);
        REST rest = MainApplication.getContainer().get(REST.class);
        rest.articlesIndex(null, mSections, params.key)
                .enqueue(new Callback<Wrappers.Paginated<Article>>() {

                    @Override
                    public void onResponse(
                            @Nullable Call<Wrappers.Paginated<Article>> call,
                            @Nullable Response<Wrappers.Paginated<Article>> response
                    ) {
                        //noinspection ConstantConditions
                        Log.v(TAG, "Server responded with " + response.code() + " status.");
                        if (response.isSuccessful()) {
                            Wrappers.Paginated<Article> articles = response.body();
                            //noinspection ConstantConditions
                            callback.onResult(articles.data,params.key + 1);
                            state.postValue(LoadingState.LOADED);
                        } else {
                            state.postValue(LoadingState.ERROR);
                        }
                    }

                    @Override
                    public void onFailure(
                            @Nullable Call<Wrappers.Paginated<Article>> call,
                            @Nullable Throwable t
                    ) {
                        Log.e(TAG, "Fetching articles has failed.", t);
                        state.postValue(LoadingState.ERROR);
                    }
                });
    }

    public static class Factory extends DataSource.Factory<Integer, Article> {

        @Nullable public Iterable<Integer> sections;

        public MutableLiveData<ArticleDataSource> source = new MutableLiveData<>();

        public Factory(@Nullable Iterable<Integer> sections) {
            this.sections = sections;
        }

        @NonNull
        @Override
        public DataSource<Integer, Article> create() {
            ArticleDataSource source = new ArticleDataSource(sections);
            this.source.postValue(source);
            return source;
        }
    }
}
