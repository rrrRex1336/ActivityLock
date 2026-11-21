package io.kernelxtra.activitylock.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.kernelxtra.activitylock.data.SessionStore;
import io.kernelxtra.activitylock.system.SettingsController;

/**
 * If the device rebooted during a session, restore the navigation settings on boot.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        if (!SessionStore.isActive(context)) {
            return;
        }

        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                SettingsController.restore(app, SessionStore.getSnapshot(app));
                SessionStore.end(app);
            } finally {
                pending.finish();
            }
        }, "activitylock-boot-restore").start();
    }
}
