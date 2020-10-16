package ltd.starthub.muly;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.multidex.MultiDex;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.pixplicity.easyprefs.library.Prefs;
import com.vaibhavpandey.katora.Container;

import java.util.Collections;

import ltd.starthub.muly.providers.RetrofitProvider;

public class MainApplication extends Application {

    private static final Container CONTAINER = new Container();

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    public static Container getContainer() {
        return CONTAINER;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressWarnings("SameParameterValue")
    private void createChannel(String id, String name, int visibility, int importance) {
        NotificationChannel channel = new NotificationChannel(id, name, importance);
        channel.enableLights(true);
        channel.setLightColor(ContextCompat.getColor(this, R.color.colorPrimary));
        channel.setLockscreenVisibility(visibility);
        if (importance == NotificationManager.IMPORTANCE_LOW) {
            channel.setShowBadge(false);
        }

        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(channel);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Fresco.initialize(this);
        if (BuildConfig.DEBUG) {
            RequestConfiguration configuration = new RequestConfiguration.Builder()
                    .setTestDeviceIds(
                            Collections.singletonList(getString(R.string.admob_test_device_id)))
                    .build();
            MobileAds.setRequestConfiguration(configuration);
        }
        MobileAds.initialize(this, status -> { /* eaten */ });
        new Prefs.Builder()
                .setContext(this)
                .setUseDefaultSharedPreference(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(
                    getString(R.string.notification_channel_id),
                    getString(R.string.notification_channel_name),
                    Notification.VISIBILITY_PUBLIC,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
        }

        CONTAINER.install(new RetrofitProvider(this));
    }
}
