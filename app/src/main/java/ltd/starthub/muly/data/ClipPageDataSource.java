package ltd.starthub.muly.data;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.DataSource;
import androidx.paging.PageKeyedDataSource;

import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

import ltd.starthub.muly.MainApplication;
import ltd.starthub.muly.common.LoadingState;
import ltd.starthub.muly.data.api.REST;
import ltd.starthub.muly.data.models.Clip;
import ltd.starthub.muly.data.models.Wrappers;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ClipPageDataSource extends PageKeyedDataSource<Integer, Clip> implements ClipDataSource {

    private static final String TAG = "ClipPageDataSource";

    private final Bundle mParams;
    private final int mSeed = ThreadLocalRandom.current().nextInt(1000, 99999);

    public final MutableLiveData<LoadingState> state = new MutableLiveData<>(LoadingState.IDLE);

    public ClipPageDataSource(@NonNull Bundle params) {
        mParams = params;
    }

    @Override
    public void loadInitial(
            @NonNull LoadInitialParams<Integer> params,
            @NonNull final LoadInitialCallback<Integer, Clip> callback
    ) {
        state.postValue(LoadingState.LOADING);
        REST rest = MainApplication.getContainer().get(REST.class);
        Boolean mine = mParams.getBoolean(PARAM_MINE);
        String q = mParams.getString(PARAM_Q);
        Boolean liked = mParams.getBoolean(PARAM_LIKED);
        Boolean saved = mParams.getBoolean(PARAM_SAVED);
        Boolean following = mParams.getBoolean(PARAM_FOLLOWING);
        Integer user = mParams.getInt(PARAM_USER);
        Integer song = mParams.getInt(PARAM_SONG);
        Iterable<Integer> sections = mParams.getIntegerArrayList(PARAM_SECTIONS);
        Iterable<String> hashtags = mParams.getStringArrayList(PARAM_HASHTAGS);
        Integer first = mParams.getInt(PARAM_FIRST);
        Long seen = mParams.getLong(PARAM_SEEN);
        rest.clipsIndex(mine, q, liked, saved, following, user, song, sections, hashtags, mSeed, seen, first, null, null, null)
                .enqueue(new Callback<Wrappers.Paginated<Clip>>() {

                    @Override
                    public void onResponse(
                            @Nullable Call<Wrappers.Paginated<Clip>> call,
                            @Nullable Response<Wrappers.Paginated<Clip>> response
                    ) {
                        //noinspection ConstantConditions
                        Log.v(TAG, "Server responded with " + response.code() + " status.");
                        if (response.isSuccessful()) {
                            Wrappers.Paginated<Clip> clips = response.body();
                            //noinspection ConstantConditions
                            callback.onResult(clips.data,null, 2);
                            state.postValue(LoadingState.LOADED);
                        } else {
                            state.postValue(LoadingState.ERROR);
                        }
                    }

                    @Override
                    public void onFailure(
                            @Nullable Call<Wrappers.Paginated<Clip>> call,
                            @Nullable Throwable t
                    ) {
                        Log.e(TAG, "Fetching clips has failed.", t);
                        state.postValue(LoadingState.ERROR);
                    }
                });
    }

    @Override
    public void loadBefore(
            @NonNull LoadParams<Integer> params,
            @NonNull LoadCallback<Integer, Clip> callback
    ) {
        state.postValue(LoadingState.LOADING);
        REST rest = MainApplication.getContainer().get(REST.class);
        Boolean mine = mParams.getBoolean(PARAM_MINE);
        String q = mParams.getString(PARAM_Q);
        Boolean liked = mParams.getBoolean(PARAM_LIKED);
        Boolean saved = mParams.getBoolean(PARAM_SAVED);
        Boolean following = mParams.getBoolean(PARAM_FOLLOWING);
        Integer user = mParams.getInt(PARAM_USER);
        Integer song = mParams.getInt(PARAM_SONG);
        Iterable<Integer> sections = mParams.getIntegerArrayList(PARAM_SECTIONS);
        Iterable<String> hashtags = mParams.getStringArrayList(PARAM_HASHTAGS);
        Long seen = mParams.getLong(PARAM_SEEN);
        rest.clipsIndex(mine, q, liked, saved, following, user, song, sections, hashtags, mSeed, seen, null, null, null, params.key)
                .enqueue(new Callback<Wrappers.Paginated<Clip>>() {

                    @Override
                    public void onResponse(
                            @Nullable Call<Wrappers.Paginated<Clip>> call,
                            @Nullable Response<Wrappers.Paginated<Clip>> response
                    ) {
                        //noinspection ConstantConditions
                        Log.v(TAG, "Server responded with " + response.code() + " status.");
                        if (response.isSuccessful()) {
                            Wrappers.Paginated<Clip> clips = response.body();
                            //noinspection ConstantConditions
                            Collections.reverse(clips.data);
                            callback.onResult(clips.data, params.key - 1);
                            state.postValue(LoadingState.LOADED);
                        } else {
                            state.postValue(LoadingState.ERROR);
                        }
                    }

                    @Override
                    public void onFailure(
                            @Nullable Call<Wrappers.Paginated<Clip>> call,
                            @Nullable Throwable t
                    ) {
                        Log.e(TAG, "Fetching clips has failed.", t);
                        state.postValue(LoadingState.ERROR);
                    }
                });
    }

    @Override
    public void loadAfter(
            @NonNull final LoadParams<Integer> params,
            @NonNull final LoadCallback<Integer, Clip> callback
    ) {
        state.postValue(LoadingState.LOADING);
        REST rest = MainApplication.getContainer().get(REST.class);
        Boolean mine = mParams.getBoolean(PARAM_MINE);
        String q = mParams.getString(PARAM_Q);
        Boolean liked = mParams.getBoolean(PARAM_LIKED);
        Boolean saved = mParams.getBoolean(PARAM_SAVED);
        Boolean following = mParams.getBoolean(PARAM_FOLLOWING);
        Integer user = mParams.getInt(PARAM_USER);
        Integer song = mParams.getInt(PARAM_SONG);
        Iterable<Integer> sections = mParams.getIntegerArrayList(PARAM_SECTIONS);
        Iterable<String> hashtags = mParams.getStringArrayList(PARAM_HASHTAGS);
        Long seen = mParams.getLong(PARAM_SEEN);
        rest.clipsIndex(mine, q, liked, saved, following, user, song, sections, hashtags, mSeed, seen, null, null, null, params.key)
                .enqueue(new Callback<Wrappers.Paginated<Clip>>() {

                    @Override
                    public void onResponse(
                            @Nullable Call<Wrappers.Paginated<Clip>> call,
                            @Nullable Response<Wrappers.Paginated<Clip>> response
                    ) {
                        //noinspection ConstantConditions
                        Log.v(TAG, "Server responded with " + response.code() + " status.");
                        if (response.isSuccessful()) {
                            Wrappers.Paginated<Clip> clips = response.body();
                            //noinspection ConstantConditions
                            callback.onResult(clips.data,params.key + 1);
                            state.postValue(LoadingState.LOADED);
                        } else {
                            state.postValue(LoadingState.ERROR);
                        }
                    }

                    @Override
                    public void onFailure(
                            @Nullable Call<Wrappers.Paginated<Clip>> call,
                            @Nullable Throwable t
                    ) {
                        Log.e(TAG, "Fetching clips has failed.", t);
                        state.postValue(LoadingState.ERROR);
                    }
                });
    }

    public static class Factory extends DataSource.Factory<Integer, Clip> {

        @NonNull public Bundle params;

        public MutableLiveData<ClipPageDataSource> source = new MutableLiveData<>();

        public Factory(@NonNull Bundle params) {
            this.params = params;
        }

        @NonNull
        @Override
        public DataSource<Integer, Clip> create() {
            ClipPageDataSource source = new ClipPageDataSource(params);
            this.source.postValue(source);
            return source;
        }
    }
}
