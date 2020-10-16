package ltd.starthub.muly.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.facebook.drawee.view.SimpleDraweeView;
import com.google.android.material.chip.Chip;
import com.kaopiz.kprogresshud.KProgressHUD;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import jp.wasabeef.recyclerview.adapters.SlideInBottomAnimationAdapter;
import jp.wasabeef.recyclerview.adapters.SlideInLeftAnimationAdapter;
import ltd.starthub.muly.R;
import ltd.starthub.muly.common.DiffUtilCallback;
import ltd.starthub.muly.common.LoadingState;
import ltd.starthub.muly.data.SongDataSource;
import ltd.starthub.muly.data.SongSectionDataSource;
import ltd.starthub.muly.data.models.Song;
import ltd.starthub.muly.data.models.SongSection;
import ltd.starthub.muly.workers.FileDownloadWorker;
import me.everything.android.ui.overscroll.OverScrollDecoratorHelper;

public class SongPickerActivity extends AppCompatActivity {

    public static String EXTRA_SONG_FILE = "song_file";
    public static String EXTRA_SONG_ID = "song_id";
    public static String EXTRA_SONG_NAME = "song_name";
    private static final String TAG = "SongPickerActivity";

    private SongPickerActivityViewModel mModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_song_picker);
        ImageButton close = findViewById(R.id.header_back);
        close.setImageResource(R.drawable.ic_baseline_close_24);
        close.setOnClickListener(view -> finish());
        TextView title = findViewById(R.id.header_title);
        title.setText(R.string.songs_label);
        findViewById(R.id.header_more).setVisibility(View.GONE);
        mModel = new ViewModelProvider(this).get(SongPickerActivityViewModel.class);
        RecyclerView articles = findViewById(R.id.songs);
        SongAdapter adapter1 = new SongAdapter();
        articles.setAdapter(new SlideInLeftAnimationAdapter(adapter1));
        mModel.songs.observe(this, adapter1::submitList);
        SwipeRefreshLayout swipe = findViewById(R.id.swipe);
        swipe.setOnRefreshListener(() -> {
            SongDataSource source = mModel.factory1.source.getValue();
            if (source != null) {
                source.invalidate();
            }
        });
        View empty = findViewById(R.id.empty);
        View loading1 = findViewById(R.id.loading1);
        mModel.state1.observe(this, state -> {
            if (state != LoadingState.LOADING) {
                swipe.setRefreshing(false);
            }

            List<?> list = mModel.songs.getValue();
            if (state == LoadingState.LOADING) {
                empty.setVisibility(View.GONE);
            } else {
                empty.setVisibility(list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
            }

            loading1.setVisibility(state == LoadingState.LOADING ? View.VISIBLE : View.GONE);
        });
        RecyclerView sections = findViewById(R.id.sections);
        LinearLayoutManager llm =
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        sections.setLayoutManager(llm);
        SongSectionAdapter adapter2 = new SongSectionAdapter();
        sections.setAdapter(new SlideInBottomAnimationAdapter(adapter2));
        OverScrollDecoratorHelper.setUpOverScroll(
                sections, OverScrollDecoratorHelper.ORIENTATION_HORIZONTAL);
        mModel.sections.observe(this, adapter2::submitList);
        mModel.selection.observe(this, integers -> {
            mModel.factory1.sections = integers;
            SongDataSource source = mModel.factory1.source.getValue();
            if (source != null) {
                source.invalidate();
            }
        });
    }

    public void downloadSelectedSong(final Song song) {
        File songs = new File(getFilesDir(), "songs");
        if (!songs.exists() && !songs.mkdirs()) {
            Log.w(TAG, "Could not create directory at " + songs);
        }

        final File audio = new File(songs, song.id + ".aac");
        if (audio.exists()) {
            closeWithSelection(song, Uri.fromFile(audio));
            return;
        }

        KProgressHUD progress = KProgressHUD.create(this)
                .setStyle(KProgressHUD.Style.SPIN_INDETERMINATE)
                .setLabel(getString(R.string.progress_title))
                .setCancellable(false)
                .show();
        Data input = new Data.Builder()
                .putString(FileDownloadWorker.KEY_URL, song.audio)
                .putString(FileDownloadWorker.KEY_PATH, audio.getAbsolutePath())
                .build();
        WorkRequest request = new OneTimeWorkRequest.Builder(FileDownloadWorker.class)
                .setInputData(input)
                .build();
        WorkManager wm = WorkManager.getInstance(this);
        wm.enqueue(request);
        wm.getWorkInfoByIdLiveData(request.getId())
                .observe(this, info -> {
                    boolean ended = info.getState() == WorkInfo.State.CANCELLED
                            || info.getState() == WorkInfo.State.FAILED;
                    if (info.getState() == WorkInfo.State.SUCCEEDED) {
                        progress.dismiss();
                        closeWithSelection(song, Uri.fromFile(audio));
                    } else if (ended) {
                        progress.dismiss();
                    }
                });
    }

    private void closeWithSelection(Song song, Uri file) {
        Intent data = new Intent();
        data.putExtra(EXTRA_SONG_ID, song.id);
        data.putExtra(EXTRA_SONG_NAME, song.title);
        data.putExtra(EXTRA_SONG_FILE, file);
        setResult(RESULT_OK, data);
        finish();
    }

    private class SongAdapter extends PagedListAdapter<Song, SongViewHolder> {

        public SongAdapter() {
            super(new DiffUtilCallback<>(i -> i.id));
        }

        @Override
        public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
            Song song = getItem(position);
            //noinspection ConstantConditions
            if (TextUtils.isEmpty(song.cover)) {
                holder.icon.setActualImageResource(R.drawable.image_placeholder);
            } else {
                holder.icon.setImageURI(song.cover);
            }
            holder.title.setText(song.title);
            List<String> details = new ArrayList<>();
            if (!TextUtils.isEmpty(song.album)) {
                details.add(song.album);
            }
            if (!TextUtils.isEmpty(song.artist)) {
                details.add(song.artist);
            }
            details.add(song.duration + "s");
            holder.details.setText(StringUtils.join(details, " | "));
            holder.itemView.setOnClickListener(view -> downloadSelectedSong(song));
        }

        @NonNull
        @Override
        public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(SongPickerActivity.this)
                    .inflate(R.layout.item_song, parent, false);
            return new SongViewHolder(view);
        }
    }

    public static class SongPickerActivityViewModel extends ViewModel {

        public SongPickerActivityViewModel() {
            PagedList.Config config1 = new PagedList.Config.Builder()
                    .setPageSize(10)
                    .build();
            factory1 = new SongDataSource.Factory(null);
            state1 = Transformations.switchMap(factory1.source, input -> input.state);
            songs = new LivePagedListBuilder<>(factory1, config1).build();
            PagedList.Config config2 = new PagedList.Config.Builder()
                    .setPageSize(100)
                    .build();
            factory2 = new SongSectionDataSource.Factory();
            state2 = Transformations.switchMap(factory2.source, input -> input.state);
            sections = new LivePagedListBuilder<>(factory2, config2).build();
        }

        public final LiveData<PagedList<Song>> songs;
        public final SongDataSource.Factory factory1;
        public final SongSectionDataSource.Factory factory2;
        public final LiveData<PagedList<SongSection>> sections;
        public final MutableLiveData<List<Integer>> selection = new MutableLiveData<>(new ArrayList<>());
        public final LiveData<LoadingState> state1;
        public final LiveData<LoadingState> state2;
    }

    private class SongSectionAdapter extends PagedListAdapter<SongSection, SongSectionViewHolder> {

        public SongSectionAdapter() {
            super(new DiffUtilCallback<>(i -> i.id));
        }

        @Override
        public void onBindViewHolder(@NonNull SongSectionViewHolder holder, int position) {
            SongSection section = getItem(position);
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
        public SongSectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(SongPickerActivity.this)
                    .inflate(R.layout.item_article_section, parent, false);
            return new SongSectionViewHolder(view);
        }
    }

    private static class SongSectionViewHolder extends RecyclerView.ViewHolder {

        public Chip chip;

        public SongSectionViewHolder(@NonNull View root) {
            super(root);
            chip = root.findViewById(R.id.chip);
            chip.setCheckable(true);
        }
    }

    private static class SongViewHolder extends RecyclerView.ViewHolder {

        public SimpleDraweeView icon;
        public TextView title;
        public TextView details;

        public SongViewHolder(@NonNull View root) {
            super(root);
            icon = root.findViewById(R.id.icon);
            title = root.findViewById(R.id.title);
            details = root.findViewById(R.id.details);
        }
    }
}
