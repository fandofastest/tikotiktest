package ltd.starthub.muly.workers;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;

import ltd.starthub.muly.MainApplication;
import ltd.starthub.muly.R;
import ltd.starthub.muly.data.api.REST;
import ltd.starthub.muly.data.models.Clip;
import ltd.starthub.muly.data.models.Wrappers;
import ltd.starthub.muly.utils.VideoUtil;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;

public class UploadClipWorker extends Worker {

    public static final String KEY_COMMENTS = "comments";
    public static final String KEY_DESCRIPTION = "description";
    public static final String KEY_PREVIEW = "preview";
    public static final String KEY_PRIVATE = "private";
    public static final String KEY_SCREENSHOT = "screenshot";
    public static final String KEY_SONG = "song";
    public static final String KEY_VIDEO = "video";
    public static final String KEY_LANGUAGE = "language";
    public static final int NOTIFICATION_ID = 60600 + 2;
    public static final String TAG = "PostClipWorker";

    public UploadClipWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    private ForegroundInfo createForegroundInfo(Context context) {
        String cancel = context.getString(R.string.cancel_button);
        PendingIntent intent = WorkManager.getInstance(context)
                .createCancelPendingIntent(getId());
        Notification notification =
                new NotificationCompat.Builder(
                        context, context.getString(R.string.notification_channel_id))
                        .setContentTitle(context.getString(R.string.notification_upload_title))
                        .setTicker(context.getString(R.string.notification_upload_title))
                        .setContentText(context.getString(R.string.notification_upload_description))
                        .setSmallIcon(R.drawable.ic_baseline_publish_24)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .addAction(R.drawable.ic_baseline_close_24, cancel, intent)
                        .build();
        return new ForegroundInfo(NOTIFICATION_ID, notification);
    }

    @NonNull
    @Override
    @SuppressWarnings("ConstantConditions")
    public Result doWork() {
        setForegroundAsync(createForegroundInfo(getApplicationContext()));
        String video = getInputData().getString(KEY_VIDEO);
        Log.v(TAG, "Uploading " + video);
        String screenshot = getInputData().getString(KEY_SCREENSHOT);
        String preview = getInputData().getString(KEY_PREVIEW);
        Integer song = getInputData().getInt(KEY_SONG, 0);
        if (song <= 0) {
            song = null;
        }
        String description = getInputData().getString(KEY_DESCRIPTION);
        String language = getInputData().getString(KEY_LANGUAGE);
        long duration = VideoUtil.getDuration(
                getApplicationContext(), Uri.fromFile(new File(video)));
        boolean _private = getInputData().getBoolean(KEY_PRIVATE, false);
        boolean comments = getInputData().getBoolean(KEY_COMMENTS, false);
        REST rest = MainApplication.getContainer().get(REST.class);
        Call<Wrappers.Single<Clip>> call = rest.clipsCreate(
                MultipartBody.Part.createFormData("video", "video.mp4", RequestBody.create(null, new File(video))),
                MultipartBody.Part.createFormData("screenshot", "screenshot.png", RequestBody.create(null, new File(screenshot))),
                MultipartBody.Part.createFormData("preview", "preview.gif", RequestBody.create(null, new File(preview))),
                song != null ? RequestBody.create(null, song + "") : null,
                description != null ? RequestBody.create(null, description) : null,
                RequestBody.create(null, language),
                RequestBody.create(null, _private ? "1" : "0"),
                RequestBody.create(null, comments ? "1" : "0"),
                RequestBody.create(null, duration + "")
        );
        Response<Wrappers.Single<Clip>> response = null;
        try {
            response = call.execute();
        } catch (Exception e) {
            Log.e(TAG, "Failed when updating device token with server.", e);
        }
        if (response != null && response.isSuccessful()) {
            return Result.success();
        }
        return Result.failure();
    }
}
