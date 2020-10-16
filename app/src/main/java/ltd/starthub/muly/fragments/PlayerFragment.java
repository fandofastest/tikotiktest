package ltd.starthub.muly.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomViewTarget;
import com.bumptech.glide.request.transition.Transition;
import com.facebook.drawee.view.SimpleDraweeView;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.source.LoopingMediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kaopiz.kprogresshud.KProgressHUD;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.UUID;

import ltd.starthub.muly.MainApplication;
import ltd.starthub.muly.R;
import ltd.starthub.muly.activities.MainActivity;
import ltd.starthub.muly.activities.RecorderActivity;
import ltd.starthub.muly.common.LoadingState;
import ltd.starthub.muly.data.api.REST;
import ltd.starthub.muly.data.models.Clip;
import ltd.starthub.muly.data.models.User;
import ltd.starthub.muly.data.models.Wrappers;
import ltd.starthub.muly.events.ClipDeletedEvent;
import ltd.starthub.muly.utils.TextFormatUtil;
import ltd.starthub.muly.workers.FileDownloadWorker;
import ltd.starthub.muly.workers.WatermarkWorker;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PlayerFragment extends Fragment implements AnalyticsListener {

    private static final String ARG_CLIP = "clip";
    private static final String TAG = "PlayerFragment";

    private View mBufferingProgressBar;
    private int mClip;
    private PlayerFragmentViewModel mModel1;
    private MainActivity.MainActivityViewModel mModel2;
    private SimpleExoPlayer mPlayer;
    private View mMusicDisc;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mClip = requireArguments().getInt(ARG_CLIP);
        mPlayer = new SimpleExoPlayer.Builder(requireContext()).build();
        mPlayer.addAnalyticsListener(this);
        mModel1 = new ViewModelProvider(this).get(PlayerFragmentViewModel.class);
        mModel2 = new ViewModelProvider(requireActivity())
                .get(MainActivity.MainActivityViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_player, container, false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopPlayer();
        mPlayer.release();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopPlayer();
    }

    @Override
    public void onPlayerStateChanged(EventTime time, boolean play, @Player.State int state) {
        if (mBufferingProgressBar != null) {
            mBufferingProgressBar.setVisibility(
                    state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        LoadingState state = mModel1.state.getValue();
        Clip clip = mModel1.clip.getValue();
        if (clip == null && state != LoadingState.LOADING) {
            loadClip();
        } else if (clip != null) {
            startPlayer();
        }
    }

    @Override
    @SuppressLint("SetTextI18n")
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mBufferingProgressBar = view.findViewById(R.id.buffering);
        mMusicDisc = view.findViewById(R.id.disc);
        mMusicDisc.setOnClickListener(v -> confirmUseAudio());
        PlayerView player = view.findViewById(R.id.player);
        player.setPlayer(mPlayer);
        View overlay = view.findViewById(R.id.overlay);
        View play = view.findViewById(R.id.play);
        overlay.setOnClickListener(v -> {
            Log.v(TAG, "Playback overlay is clicked.");
            mPlayer.setPlayWhenReady(!mPlayer.getPlayWhenReady());
            play.setVisibility(!mPlayer.getPlayWhenReady() ? View.VISIBLE : View.GONE);
        });
        play.setOnClickListener(v -> {
            Log.v(TAG, "Play button is clicked.");
            mPlayer.setPlayWhenReady(true);
            play.setVisibility(View.GONE);
        });
        view.findViewById(R.id.badge).setOnClickListener(v -> showProfile());
        View report = view.findViewById(R.id.report);
        report.setOnClickListener(v -> {
            if (mModel2.isLoggedIn) {
                ((MainActivity)requireActivity()).reportSubject("clip", mClip);
            } else {
                ((MainActivity) requireActivity()).showLoginSheet();
            }
        });
        View delete = view.findViewById(R.id.delete);
        delete.setOnClickListener(v -> {
            if (mModel2.isLoggedIn) {
                confirmDeletion();
            } else {
                ((MainActivity) requireActivity()).showLoginSheet();
            }
        });
        TextView likes = view.findViewById(R.id.likes);
        CheckBox like = view.findViewById(R.id.like);
        like.setOnCheckedChangeListener((v, checked) -> {
            if (mModel2.isLoggedIn) {
                likeUnlike(checked);
            } else {
                ((MainActivity) requireActivity()).showLoginSheet();
            }
        });
        View comment = view.findViewById(R.id.comment);
        comment.setOnClickListener(v -> showComments());
        View share = view.findViewById(R.id.share);
        share.setOnClickListener(v ->
                downloadAndRun(true, file -> shareClip(requireContext(), file)));
        CheckBox save = view.findViewById(R.id.save);
        save.setOnCheckedChangeListener((v, checked) -> {
            if (mModel2.isLoggedIn) {
                saveUnsave(checked);
            } else {
                ((MainActivity) requireActivity()).showLoginSheet();
            }
        });
        TextView username = view.findViewById(R.id.username);
        username.setOnClickListener(v -> showProfile());
        mModel1.clip.observe(getViewLifecycleOwner(), clip -> {
            SimpleDraweeView photo = view.findViewById(R.id.photo);
            if (TextUtils.isEmpty(clip.user.photo)) {
                photo.setActualImageResource(R.drawable.photo_placeholder);
            } else {
                photo.setImageURI(clip.user.photo);
            }
            delete.setVisibility(clip.user.me ? View.VISIBLE : View.GONE);
            like.setChecked(clip.liked);
            likes.setText(TextFormatUtil.toShortNumber(clip.likesCount));
            comment.setVisibility(clip.comments ? View.VISIBLE : View.GONE);
            TextView comments = view.findViewById(R.id.comments);
            comments.setText(TextFormatUtil.toShortNumber(clip.commentsCount));
            comments.setVisibility(clip.comments ? View.VISIBLE : View.GONE);
            save.setChecked(clip.saved);
            view.findViewById(R.id.verified)
                    .setVisibility(clip.user.verified ? View.VISIBLE : View.GONE);
            username.setText('@' + clip.user.username);
            TextView song = view.findViewById(R.id.song);
            song.setSelected(true);
            if (clip.song != null) {
                song.setText(clip.song.title);
            } else {
                song.setText(R.string.original_audio);
            }
            TextView description = view.findViewById(R.id.description);
            description.setText(clip.description);
            description.setVisibility(TextUtils.isEmpty(clip.description) ? View.GONE : View.VISIBLE);
            ChipGroup tags = view.findViewById(R.id.tags);
            if (clip.mentions.isEmpty() && clip.hashtags.isEmpty()) {
                tags.setVisibility(View.GONE);
            } else {
                tags.setVisibility(View.VISIBLE);
                tags.removeAllViews();
                for (User user : clip.mentions) {
                    Chip chip = new Chip(requireContext());
                    chip.setOnClickListener(v -> showProfile(user.id));
                    chip.setText("@" + user.username);
                    tags.addView(chip);
                    if (TextUtils.isEmpty(user.photo)) {
                        Glide.with(requireContext())
                                .load(R.drawable.photo_placeholder)
                                .circleCrop()
                                .into(new ChipTarget(chip));
                    } else {
                        Glide.with(requireContext())
                                .load(user.photo)
                                .placeholder(R.drawable.photo_placeholder)
                                .circleCrop()
                                .into(new ChipTarget(chip));
                    }
                }
                for (String hashtag : clip.hashtags) {
                    Chip chip = new Chip(requireContext());
                    chip.setText("#" + hashtag);
                    tags.addView(chip);
                }
            }
            if (isAdded()) {
                startPlayer();
            }
        });
        View content = view.findViewById(R.id.content);
        View loading = view.findViewById(R.id.loading);
        mModel1.state.observe(getViewLifecycleOwner(), state -> {
            loading.setVisibility(state == LoadingState.LOADING ? View.VISIBLE : View.GONE);
            content.setVisibility(state == LoadingState.LOADED ? View.VISIBLE : View.GONE);
        });
        loadClip();
    }

    private void confirmDeletion() {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.confirmation_delete_clip)
                .setNegativeButton(R.string.cancel_button, (dialog, i) -> dialog.cancel())
                .setPositiveButton(R.string.yes_button, (dialog, i) -> {
                    dialog.dismiss();
                    deleteClip();
                })
                .show();
    }

    private void confirmUseAudio() {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.confirmation_use_audio)
                .setNegativeButton(R.string.cancel_button, (dialog, i) -> dialog.cancel())
                .setPositiveButton(R.string.yes_button, (dialog, i) -> {
                    dialog.dismiss();
                    downloadAndRun(false, file -> {
                        Intent intent = new Intent(requireContext(), RecorderActivity.class);
                        intent.putExtra(RecorderActivity.EXTRA_AUDIO, Uri.fromFile(file));
                        startActivity(intent);
                    });
                })
                .show();
    }

    private void deleteClip() {
        KProgressHUD progress = KProgressHUD.create(requireActivity())
                .setStyle(KProgressHUD.Style.SPIN_INDETERMINATE)
                .setLabel(getString(R.string.progress_title))
                .setCancellable(false)
                .show();
        REST rest = MainApplication.getContainer().get(REST.class);
        rest.clipsDelete(mClip)
                .enqueue(new Callback<ResponseBody>() {

                    @Override
                    public void onResponse(
                            @Nullable Call<ResponseBody> call,
                            @Nullable Response<ResponseBody> response
                    ) {
                        int code = response != null ? response.code() : -1;
                        Log.v(TAG, "Deleting clip returned " + code + '.');
                        if (code == 200) {
                            EventBus.getDefault().post(new ClipDeletedEvent());
                        }
                        progress.dismiss();
                    }

                    @Override
                    public void onFailure(
                            @Nullable Call<ResponseBody> call,
                            @Nullable Throwable t
                    ) {
                        Log.e(TAG, "Failed to delete selected clip.", t);
                        progress.dismiss();
                    }
                });
    }

    private void downloadAndRun(boolean watermark, OnFinish callback) {
        Clip clip = mModel1.clip.getValue();
        if (clip == null) {
            return;
        }
        File clips = new File(requireContext().getFilesDir(), "clips");
        if (!clips.exists() && !clips.mkdirs()) {
            Log.w(TAG, "Could not create directory at " + clips);
        }
        File processed = new File(clips, clip.id + ".mp4");
        if (processed.exists()) {
            callback.finished(processed);
            return;
        }
        KProgressHUD progress = KProgressHUD.create(requireActivity())
                .setStyle(KProgressHUD.Style.SPIN_INDETERMINATE)
                .setLabel(getString(R.string.progress_title))
                .setCancellable(false)
                .show();
        File original = new File(
                requireContext().getCacheDir(),
                UUID.randomUUID().toString() + ".mp4");
        Data data1 = new Data.Builder()
                .putString(FileDownloadWorker.KEY_URL, clip.video)
                .putString(FileDownloadWorker.KEY_PATH, original.getAbsolutePath())
                .build();
        OneTimeWorkRequest request1 = new OneTimeWorkRequest.Builder(FileDownloadWorker.class)
                .setInputData(data1)
                .build();
        WorkManager wm = WorkManager.getInstance(requireContext());
        if (watermark) {
            Data data2 = new Data.Builder()
                    .putString(WatermarkWorker.KEY_INPUT, original.getAbsolutePath())
                    .putString(WatermarkWorker.KEY_OUTPUT, processed.getAbsolutePath())
                    .build();
            OneTimeWorkRequest request2 = new OneTimeWorkRequest.Builder(WatermarkWorker.class)
                    .setInputData(data2)
                    .build();
            wm.beginWith(request1).then(request2).enqueue();
            wm.getWorkInfoByIdLiveData(request2.getId())
                    .observe(getViewLifecycleOwner(), info -> {
                        boolean ended = info.getState() == WorkInfo.State.CANCELLED
                                || info.getState() == WorkInfo.State.FAILED;
                        if (info.getState() == WorkInfo.State.SUCCEEDED) {
                            progress.dismiss();
                            callback.finished(processed);
                        } else if (ended) {
                            progress.dismiss();
                        }
                    });
        } else {
            wm.enqueue(request1);
            wm.getWorkInfoByIdLiveData(request1.getId())
                    .observe(getViewLifecycleOwner(), info -> {
                        boolean ended = info.getState() == WorkInfo.State.CANCELLED
                                || info.getState() == WorkInfo.State.FAILED;
                        if (info.getState() == WorkInfo.State.SUCCEEDED) {
                            progress.dismiss();
                            callback.finished(original);
                        } else if (ended) {
                            progress.dismiss();
                        }
                    });
        }
    }

    private void loadClip() {
        mModel1.state.setValue(LoadingState.LOADING);
        REST rest = MainApplication.getContainer().get(REST.class);
        rest.clipsShow(mClip)
                .enqueue(new Callback<Wrappers.Single<Clip>>() {

                    @Override
                    public void onResponse(
                            @Nullable Call<Wrappers.Single<Clip>> call,
                            @Nullable Response<Wrappers.Single<Clip>> response
                    ) {
                        if (response != null && response.isSuccessful()) {
                            //noinspection ConstantConditions
                            Clip clip = response.body().data;
                            mModel1.clip.setValue(clip);
                            mModel1.state.setValue(LoadingState.LOADED);
                        } else {
                            mModel1.state.setValue(LoadingState.ERROR);
                        }
                    }

                    @Override
                    public void onFailure(
                            @Nullable Call<Wrappers.Single<Clip>> call,
                            @Nullable Throwable t
                    ) {
                        Log.e(TAG, "Failed when trying to fetch clip.", t);
                        mModel1.state.setValue(LoadingState.ERROR);
                    }
                });
    }

    public static PlayerFragment newInstance(int clip) {
        PlayerFragment fragment = new PlayerFragment();
        Bundle arguments = new Bundle();
        arguments.putInt(ARG_CLIP, clip);
        fragment.setArguments(arguments);
        return fragment;
    }

    private void likeUnlike(boolean like) {
        Clip clip = mModel1.clip.getValue();
        if (clip == null) {
            return;
        }
        REST rest = MainApplication.getContainer().get(REST.class);
        Call<ResponseBody> call;
        if (like) {
            call = rest.likesLike(clip.id);
        } else {
            call = rest.likesUnlike(clip.id);
        }
        call.enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(
                    @Nullable Call<ResponseBody> call,
                    @Nullable Response<ResponseBody> response
            ) {
                int code = response != null ? response.code() : -1;
                Log.v(TAG, "Updating like/unlike returned " + code + '.');
            }

            @Override
            public void onFailure(
                    @Nullable Call<ResponseBody> call,
                    @Nullable Throwable t
            ) {
                Log.e(TAG, "Failed to update like/unlike status.", t);
            }
        });
        if (like) {
            clip.likesCount++;
        } else {
            clip.likesCount--;
        }
        clip.liked = like;
        mModel1.clip.postValue(clip);
    }

    private void saveUnsave(boolean save) {
        Clip clip = mModel1.clip.getValue();
        if (clip == null) {
            return;
        }
        REST rest = MainApplication.getContainer().get(REST.class);
        Call<ResponseBody> call;
        if (save) {
            call = rest.savesSave(clip.id);
        } else {
            call = rest.savesUnsave(clip.id);
        }
        call.enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(
                    @Nullable Call<ResponseBody> call,
                    @Nullable Response<ResponseBody> response
            ) {
                int code = response != null ? response.code() : -1;
                Log.v(TAG, "Updating save/unsave returned " + code + '.');
            }

            @Override
            public void onFailure(
                    @Nullable Call<ResponseBody> call,
                    @Nullable Throwable t
            ) {
                Log.e(TAG, "Failed to update save/unsave status.", t);
            }
        });
        clip.saved = save;
    }

    private void shareClip(Context context, File file) {
        Log.v(TAG, "Showing sharing options for " + file);
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName(), file);
        Intent intent = ShareCompat.IntentBuilder.from(requireActivity())
                .setStream(uri)
                .setText(getString(R.string.share_clip_text, context.getPackageName()))
                .setType("clip/mp4")
                .setChooserTitle(getString(R.string.share_clip_title))
                .getIntent()
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private void showComments() {
        Clip clip = mModel1.clip.getValue();
        if (clip != null) {
            ((MainActivity)requireActivity()).showCommentsPage(clip.id);
        }
    }

    private void showProfile() {
        Clip clip = mModel1.clip.getValue();
        if (clip != null) {
            showProfile(clip.user.id);
        }
    }

    private void showProfile(int user) {
        ((MainActivity)requireActivity()).showProfilePage(user);
    }

    private void startPlayer() {
        if (mPlayer.isPlaying()) {
            return;
        }
        Clip clip = mModel1.clip.getValue();
        DefaultDataSourceFactory factory =
                new DefaultDataSourceFactory(requireContext(), getString(R.string.app_name));
        //noinspection ConstantConditions
        ProgressiveMediaSource source = new ProgressiveMediaSource.Factory(factory)
                .createMediaSource(Uri.parse(clip.video));
        mPlayer.setPlayWhenReady(true);
        mPlayer.seekTo(mModel1.window, mModel1.position);
        mPlayer.prepare(new LoopingMediaSource(source), false, false);
        mMusicDisc.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.rotate_360));
        //noinspection ConstantConditions
        View play = getView().findViewById(R.id.play);
        play.setVisibility(View.GONE);
    }

    private void stopPlayer() {
        mModel1.position = mPlayer.getCurrentPosition();
        mModel1.window = mPlayer.getCurrentWindowIndex();
        mPlayer.setPlayWhenReady(false);
        mPlayer.stop(true);
        mMusicDisc.clearAnimation();
    }

    public static class PlayerFragmentViewModel extends ViewModel {

        public final MutableLiveData<Clip> clip = new MutableLiveData<>();
        public long position = 0;
        public final MutableLiveData<LoadingState> state = new MutableLiveData<>();
        public int window = 0;
    }

    private static class ChipTarget extends CustomViewTarget<Chip, Drawable> {

        private final Chip mChip;

        public ChipTarget(@NonNull Chip chip) {
            super(chip);
            mChip = chip;
        }

        @Override
        public void onLoadFailed(@Nullable Drawable drawable) {
            mChip.setChipIcon(drawable);
        }

        @Override
        public void onResourceReady(
                @NonNull Drawable resource,
                @Nullable Transition<? super Drawable> transition
        ) {
            mChip.setChipIcon(resource);
        }

        @Override
        protected void onResourceCleared(@Nullable Drawable placeholder) {
            mChip.setChipIcon(placeholder);
        }
    }

    private interface OnFinish {

        void finished(File file);
    }
}
