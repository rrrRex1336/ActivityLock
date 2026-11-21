package com.rex.activitylock.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.rex.activitylock.App;
import com.rex.activitylock.MainActivity;
import com.rex.activitylock.R;
import com.rex.activitylock.data.Prefs;
import com.rex.activitylock.data.SessionStore;
import com.rex.activitylock.data.SettingsSnapshot;
import com.rex.activitylock.system.SettingsController;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LockService extends Service implements LockOverlay.Callback {

    public static final String ACTION_START = "com.rex.activitylock.action.START";
    public static final String ACTION_STOP = "com.rex.activitylock.action.STOP";
    public static final String EXTRA_TARGET = "target_package";
    public static final String EXTRA_TARGET_NAME = "target_name";
    public static final String EXTRA_BLOCK_GESTURES = "block_gestures";
    public static final String EXTRA_HIDE_BARS = "hide_bars";
    public static final String EXTRA_KEEP_AWAKE = "keep_awake";
    public static final String EXTRA_ERROR = "start_error";

    private static final int NOTIFICATION_ID = 4101;
    private static final int ERROR_NOTIFICATION_ID = 4102;
    private static final long LAUNCH_DELAY_MS = 320L;

    private static volatile boolean running;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private LockOverlay overlay;
    private volatile boolean stopping;
    private boolean startHandled;
    private String targetPackage;
    private String targetName;

    public static boolean isRunning() {
        return running;
    }

    public static void start(Context context, String target, String targetName,
                             boolean blockGestures, boolean hideBars, boolean keepAwake) {
        Intent intent = new Intent(context, LockService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_TARGET, target)
                .putExtra(EXTRA_TARGET_NAME, targetName)
                .putExtra(EXTRA_BLOCK_GESTURES, blockGestures)
                .putExtra(EXTRA_HIDE_BARS, hideBars)
                .putExtra(EXTRA_KEEP_AWAKE, keepAwake);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, LockService.class).setAction(ACTION_STOP);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            requestStop();
            return START_NOT_STICKY;
        }

        if (!ACTION_START.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForegroundNow(getString(R.string.start_lock_preparing));

        if (startHandled) {
            return START_NOT_STICKY;
        }
        startHandled = true;

        targetPackage = intent.getStringExtra(EXTRA_TARGET);
        targetName = intent.getStringExtra(EXTRA_TARGET_NAME);
        if (targetPackage == null || targetPackage.isEmpty()) {
            finishWithError(getString(R.string.toast_no_target));
            return START_NOT_STICKY;
        }
        if (targetName == null || targetName.trim().isEmpty()) {
            targetName = targetPackage;
        }

        boolean blockGestures = intent.getBooleanExtra(EXTRA_BLOCK_GESTURES, true);
        boolean hideBars = intent.getBooleanExtra(EXTRA_HIDE_BARS, true);
        boolean keepAwake = intent.getBooleanExtra(EXTRA_KEEP_AWAKE, true);
        String finalName = targetName;

        executor.execute(() -> prepareLock(blockGestures, hideBars, keepAwake, finalName));
        return START_NOT_STICKY;
    }

    private void prepareLock(boolean blockGestures, boolean hideBars, boolean keepAwake,
                             String name) {
        if (stopping) {
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            main.post(() -> finishWithError(getString(R.string.error_overlay_missing)));
            return;
        }

        SettingsSnapshot snapshot = SettingsController.capture(this);
        SessionStore.begin(this, targetPackage, name, snapshot);

        SettingsController.ApplyResult result =
                SettingsController.apply(this, blockGestures, hideBars);
        if (!result.success) {
            main.post(() -> finishWithError(getString(R.string.toast_settings_failed)));
            return;
        }
        if (stopping) {
            return;
        }

        main.post(() -> completeStart(name, result, keepAwake));
    }

    private void completeStart(String name, SettingsController.ApplyResult result,
                               boolean keepAwake) {
        if (stopping) {
            return;
        }
        overlay = new LockOverlay(this, this);
        boolean gestureWarning = !result.gesturesVerified || !result.barsVerified;
        if (!overlay.show(keepAwake, gestureWarning)) {
            finishWithError(getString(R.string.error_overlay_missing));
            return;
        }

        startForegroundNow(getString(R.string.notification_title, name));
        main.postDelayed(this::launchTarget, LAUNCH_DELAY_MS);
    }

    private void launchTarget() {
        if (stopping) {
            return;
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage(targetPackage);
        if (launch == null) {
            finishWithError(getString(R.string.toast_launch_failed));
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(launch);
        } catch (Exception e) {
            finishWithError(getString(R.string.toast_launch_failed));
        }
    }

    @Override
    public void onExitConfirmed() {
        requestStop();
    }

    private void requestStop() {
        if (stopping) {
            return;
        }
        stopping = true;
        executor.execute(() -> {
            restoreIfNeeded();
            main.post(() -> {
                if (overlay != null) {
                    overlay.hide();
                    overlay = null;
                }
                SessionStore.end(this);
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
                stopSelf();
            });
        });
    }

    private void finishWithError(String message) {
        if (stopping) {
            return;
        }
        stopping = true;
        executor.execute(() -> {
            restoreIfNeeded();
            SessionStore.end(this);
            main.post(() -> {
                if (overlay != null) {
                    overlay.hide();
                    overlay = null;
                }
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
                postErrorNotification(message);
                openErrorUi(message);
                stopSelf();
            });
        });
    }

    private void restoreIfNeeded() {
        if (!SessionStore.isActive(this)) {
            return;
        }
        SettingsController.restore(this, SessionStore.getSnapshot(this));
    }

    private void startForegroundNow(String title) {
        Notification notification = buildNotification(title);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                : 0;
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type);
    }

    private Notification buildNotification(String title) {
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 10, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, LockService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 11, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, App.CHANNEL_LOCK)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(getString(R.string.notification_text))
                .setColor(ContextCompat.getColor(this, R.color.lime))
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(openPending)
                .addAction(0, getString(R.string.notification_end), stopPending)
                .build();
    }

    private void postErrorNotification(String message) {
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_ERROR, message);
        PendingIntent openPending = PendingIntent.getActivity(this, 12, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, App.CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notification_error_title))
                .setContentText(message)
                .setColor(ContextCompat.getColor(this, R.color.warning))
                .setAutoCancel(true)
                .setContentIntent(openPending)
                .build();
        NotificationManager manager = ContextCompat.getSystemService(this, NotificationManager.class);
        if (manager != null) {
            manager.notify(ERROR_NOTIFICATION_ID, notification);
        }
    }

    private void openErrorUi(String message) {
        try {
            Intent intent = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA_ERROR, message);
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        stopping = true;
        if (overlay != null) {
            overlay.hide();
            overlay = null;
        }
        if (SessionStore.isActive(this)) {
            SettingsController.restore(this, SessionStore.getSnapshot(this));
            SessionStore.end(this);
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        executor.shutdownNow();
        running = false;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
