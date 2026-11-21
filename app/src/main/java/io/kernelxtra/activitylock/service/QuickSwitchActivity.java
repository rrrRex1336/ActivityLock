package io.kernelxtra.activitylock.service;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import io.kernelxtra.activitylock.R;
import io.kernelxtra.activitylock.data.AppEntry;
import io.kernelxtra.activitylock.data.Prefs;
import io.kernelxtra.activitylock.data.SessionStore;
import io.kernelxtra.activitylock.ui.AppPickerSheet;

/**
 * Transparent host for the app picker while a lock is active. Selecting an app
 * keeps the current lock session alive and simply relaunches the new target.
 */
public class QuickSwitchActivity extends AppCompatActivity implements AppPickerSheet.Listener {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SessionStore.isActive(this) || !LockService.isRunning()) {
            finish();
            return;
        }
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        AppPickerSheet sheet = AppPickerSheet.newInstance(Prefs.getTarget(this));
        sheet.setListener(this);
        sheet.setOnDismissListener(this::finish);
        sheet.show(getSupportFragmentManager(), "quick_switch");
    }

    @Override
    public void onAppPicked(@NonNull AppEntry entry) {
        Prefs.setTarget(this, entry.packageName);
        LockService.switchTarget(this, entry.packageName, entry.label);
        finish();
    }
}
