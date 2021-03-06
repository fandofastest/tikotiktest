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
import ltd.starthub.muly.data.models.Song;
import ltd.starthub.muly.data.models.Wrappers;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SongDataSource extends PageKeyedDataSource<Integer, Song> {

    private static final String TAG = "SongDataSource";

    @Nullable private final Iterable<Integer> mSections;

    public final MutableLiveData<LoadingState> state = new MutableLiveData<>(LoadingState.IDLE);

    public SongDataSource(@Nullable Iterable<Integer> sections) {
        mSections = sections;
    }

    @Override
    public void loadInitial(
            @NonNull LoadInitialParams<Integer> params,
            @NonNull final LoadInitialCallback<Integer, Song> callback
    ) {
        state.postValue(LoadingState.LOADING);
        REST rest = MainApplication.getContainer().get(REST.class);
        rest.songsIndex(null, mSections, 1)
                .enqueue(new Callback<Wrappers.Paginated<Song>>() {

                    @Override
                    public void onResponse(
                            @Nullable Call<Wrappers.Paginated<Song>> call,
                            @Nullable Response<Wrappers.Paginated<Song>> response
                    ) {
                        //noinspection ConstantConditions
                        Log.v(TAG, "Server responded with " + response.code() + " status.");
                        if (response.isSuccessful()) {
                            Wrappers.Paginated<Song> songs = response.body();
                            //noinspection ConstantConditions
                            callback.onResult(songs.data,null, 2);
                            state.postValue(LoadingState.LOADED);
                        } else {
                            state.postValue(LoadingState.ERROR);
                        }
                    }

                    @Override
                    public void onFailure(
                            @Nullable Call<Wrappers.Paginated<Song>> call,
                            @Nullable Throwable t
                    ) {
                        Log.e(TAG, "Fetching songs has failed.", t);
                        state.postValue(LoadingState.ERROR);
                    }
                });
    }

    @Override
    public void loadBefore(
            @NonNull LoadParams<Integer> params,
            @NonNull LoadCallback<Integer, Song> callback
    ) {
    }

    @Override
    public void loadAfter(
            @NonNull final LoadParams<Integer> params,
            @NonNull final LoadCallback<Integer, Song> callback
    ) {
        state.postValue(LoadingState.LOADING);
        REST rest = MainApplication.getContainer().get(REST.class);
        rest.songsIndex(null, mSections, params.key)
                .enqueue(new Callback<Wrappers.Paginated<Song>>() {

                    @Override
                    public void onResponse(
                            @Nullable Call<Wrappers.Paginated<Song>> call,
                            @Nullable Response<Wrappers.Paginated<Song>> response
                    ) {
                        //noinspection ConstantConditions
                        Log.v(TAG, "Server responded with " + response.code() + " status.");
                        if (response.isSuccessful()) {
                            Wrappers.Paginated<Song> songs = response.body();
                            //noinspection ConstantConditions
                            callback.onResult(songs.data,params.key + 1);
                            state.postValue(LoadingState.LOADED);
                        } else {
                            state.postValue(LoadingState.ERROR);
                        }
                    }

                    @Override
                    public void onFailure(
                            @Nullable Call<Wrappers.Paginated<Song>> call,
                            @Nullable Throwable t
                    ) {
                        Log.e(TAG, "Fetching songs has failed.", t);
                        state.postValue(LoadingState.ERROR);
                    }
                });
    }

    public static class Factory extends DataSource.Factory<Integer, Song> {

        @Nullable public Iterable<Integer> sections;

        public MutableLiveData<SongDataSource> source = new MutableLiveData<>();

        public Factory(@Nullable Iterable<Integer> sections) {
            this.sections = sections;
        }

        @NonNull
        @Override
        public DataSource<Integer, Song> create() {
            SongDataSource source = new SongDataSource(sections);
            this.source.postValue(source);
            return source;
        }
    }
}
