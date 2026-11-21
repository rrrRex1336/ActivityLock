package io.kernelxtra.activitylock;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import androidx.core.content.ContextCompat;

import com.google.android.material.color.DynamicColors;

public class App extends Application {

    public static final String CHANNEL_LOCK = "lock_status";
    public static final String CHANNEL_ALERTS = "lock_alerts";

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
        createChannels();
    }

    private void createChannels() {
        NotificationManager manager = ContextCompat.getSystemService(this, NotificationManager.class);
        if (manager == null) {
            return;
        }

        NotificationChannel lock = new NotificationChannel(
                CHANNEL_LOCK,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        lock.setDescription(getString(R.string.notification_channel_desc));
        lock.setShowBadge(false);
        lock.enableVibration(false);
        manager.createNotificationChannel(lock);

        NotificationChannel alerts = new NotificationChannel(
                CHANNEL_ALERTS,
                getString(R.string.notif_channel_service),
                NotificationManager.IMPORTANCE_DEFAULT);
        alerts.setDescription(getString(R.string.notif_channel_service_desc));
        manager.createNotificationChannel(alerts);
    }
}
