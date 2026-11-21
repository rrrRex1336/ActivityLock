package io.kernelxtra.activitylock;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import io.kernelxtra.activitylock.data.AppEntry;
import io.kernelxtra.activitylock.data.Prefs;
import io.kernelxtra.activitylock.data.SessionStore;
import io.kernelxtra.activitylock.data.SettingsSnapshot;
import io.kernelxtra.activitylock.service.LockService;
import io.kernelxtra.activitylock.system.SettingsController;
import io.kernelxtra.activitylock.ui.AppPickerSheet;
import io.kernelxtra.activitylock.util.Shell;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements AppPickerSheet.Listener {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private ScrollView scrollView;
    private View activeCard;
    private View setupCard;
    private View permissionRows;
    private MaterialToolbar toolbar;
    private MaterialButton btnStart;
    private MaterialButton btnEndLock;
    private MaterialButton btnRootSetup;
    private MaterialButton btnOverlay;
    private MaterialButton btnDetect;
    private MaterialSwitch switchGestures;
    private MaterialSwitch switchBars;
    private MaterialSwitch switchAwake;
    private TextView targetName;
    private TextView targetPackage;
    private TextView setupTitle;
    private TextView setupDesc;
    private TextView statusRoot;
    private TextView statusOverlay;
    private ImageView targetIcon;

    private volatile boolean rootAvailable;
    private boolean rootChecked;
    private boolean rootCheckRunning;
    private boolean rootSetupRunning;
    private boolean cleanupStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(!night);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(!night);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        setContentView(R.layout.activity_main);

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (!isFinishing() && rootAvailable
                            && Settings.canDrawOverlays(this)
                            && !TextUtils.isEmpty(Prefs.getTarget(this))) {
                        beginLock();
                    }
                });

        bindViews();
        applyInsets();
        bindInteractions();
        refreshAll();
        handleIntent(getIntent());
        scrollView.setFocusableInTouchMode(true);
        scrollView.requestFocus();
        checkRoot(false);
    }

    private void bindViews() {
        scrollView = findViewById(R.id.main_scroll);
        activeCard = findViewById(R.id.active_card);
        setupCard = findViewById(R.id.setup_card);
        permissionRows = findViewById(R.id.permission_rows);
        toolbar = findViewById(R.id.toolbar);
        btnStart = findViewById(R.id.btn_start);
        btnEndLock = findViewById(R.id.btn_end_lock);
        btnRootSetup = findViewById(R.id.btn_root_setup);
        btnOverlay = findViewById(R.id.btn_overlay);
        btnDetect = findViewById(R.id.btn_detect);
        switchGestures = findViewById(R.id.switch_gestures);
        switchBars = findViewById(R.id.switch_bars);
        switchAwake = findViewById(R.id.switch_awake);
        targetName = findViewById(R.id.target_name);
        targetPackage = findViewById(R.id.target_package);
        targetIcon = findViewById(R.id.target_icon);
        setupTitle = findViewById(R.id.setup_title);
        setupDesc = findViewById(R.id.setup_desc);
        statusRoot = findViewById(R.id.status_root);
        statusOverlay = findViewById(R.id.status_overlay);
    }

    private void applyInsets() {
        final View bottomBar = findViewById(R.id.bottom_bar);
        final int baseBottomPadding = bottomBar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    baseBottomPadding + bars.bottom);
            return insets;
        });
    }

    private void bindInteractions() {
        findViewById(R.id.target_card).setOnClickListener(v -> openAppPicker());
        toolbar.setOnMenuItemClickListener(this::onMenuItemClick);
        if (toolbar.getMenu().findItem(R.id.action_help) != null) {
            Drawable icon = toolbar.getMenu().findItem(R.id.action_help).getIcon();
            if (icon != null) {
                icon.mutate().setTint(MaterialColors.getColor(toolbar,
                        com.google.android.material.R.attr.colorOnSurfaceVariant));
            }
        }
        btnEndLock.setOnClickListener(v -> endLock());
        btnStart.setOnClickListener(v -> startFlow());
        btnRootSetup.setOnClickListener(v -> runRootSetup(false));
        btnOverlay.setOnClickListener(v -> openOverlaySettings());
        btnDetect.setOnClickListener(v -> checkRoot(true));

        switchGestures.setChecked(Prefs.blockGestures(this));
        switchBars.setChecked(Prefs.hideBars(this));
        switchAwake.setChecked(Prefs.keepAwake(this));

        switchGestures.setOnCheckedChangeListener((buttonView, isChecked) ->
                Prefs.setBlockGestures(this, isChecked));
        switchBars.setOnCheckedChangeListener((buttonView, isChecked) ->
                Prefs.setHideBars(this, isChecked));
        switchAwake.setOnCheckedChangeListener((buttonView, isChecked) ->
                Prefs.setKeepAwake(this, isChecked));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
        if (SessionStore.isActive(this) && !LockService.isRunning()) {
            cleanupStaleSession();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String error = intent.getStringExtra(LockService.EXTRA_ERROR);
        if (!TextUtils.isEmpty(error)) {
            intent.removeExtra(LockService.EXTRA_ERROR);
            refreshAll();
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.notification_error_title)
                    .setMessage(error)
                    .setPositiveButton(R.string.close, null)
                    .show();
        }
    }

    private void refreshAll() {
        refreshTargetUi();
        refreshSetupUi();
        refreshActiveUi();
    }

    private void refreshActiveUi() {
        boolean active = SessionStore.isActive(this) && LockService.isRunning();
        activeCard.setVisibility(active ? View.VISIBLE : View.GONE);
        btnStart.setEnabled(!active);
        btnStart.setText(active ? R.string.start_locked_button : R.string.start_lock);
    }

    private void refreshTargetUi() {
        String pkg = Prefs.getTarget(this);
        if (TextUtils.isEmpty(pkg)) {
            targetName.setText(R.string.target_empty);
            targetPackage.setText(R.string.target_empty_hint);
            targetIcon.setImageResource(R.drawable.ic_lock);
            return;
        }

        try {
            PackageManager pm = getPackageManager();
            android.content.pm.ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            CharSequence label = pm.getApplicationLabel(info);
            targetName.setText(TextUtils.isEmpty(label) ? pkg : label);
            targetPackage.setText(pkg);
            targetIcon.setImageDrawable(pm.getApplicationIcon(info));
        } catch (PackageManager.NameNotFoundException e) {
            Prefs.setTarget(this, null);
            targetName.setText(R.string.target_empty);
            targetPackage.setText(R.string.target_empty_hint);
            targetIcon.setImageResource(R.drawable.ic_lock);
        }
    }

    private void refreshSetupUi() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean ready = rootChecked && rootAvailable && overlay;

        if (!rootChecked || rootCheckRunning) {
            setStatus(statusRoot, null);
        } else {
            setStatus(statusRoot, rootAvailable);
        }
        setStatus(statusOverlay, overlay);

        if (ready) {
            setupTitle.setText(R.string.root_ready);
            setupDesc.setText(R.string.root_ready_desc);
        } else if (!rootChecked || rootCheckRunning) {
            setupTitle.setText(R.string.root_detecting);
            setupDesc.setText(R.string.root_detecting_desc);
        } else {
            setupTitle.setText(R.string.root_need);
            setupDesc.setText(rootAvailable
                    ? R.string.root_overlay_desc : R.string.root_need_desc);
        }

        permissionRows.setVisibility(ready ? View.GONE : View.VISIBLE);
        btnRootSetup.setVisibility(ready ? View.GONE : View.VISIBLE);
        btnOverlay.setVisibility(!ready && rootAvailable && !overlay
                ? View.VISIBLE : View.GONE);
        btnDetect.setVisibility(ready ? View.GONE : View.VISIBLE);
        btnRootSetup.setEnabled(!rootSetupRunning && !rootCheckRunning);
        btnRootSetup.setText(rootSetupRunning
                ? R.string.root_setup_running : R.string.root_setup);
        btnDetect.setEnabled(!rootSetupRunning && !rootCheckRunning);
    }

    private void setStatus(TextView view, Boolean ok) {
        if (ok == null) {
            view.setText(R.string.status_detecting);
            view.setTextColor(MaterialColors.getColor(view,
                    com.google.android.material.R.attr.colorOnSurfaceVariant));
            return;
        }
        view.setText(ok ? R.string.permission_ok : R.string.permission_missing);
        view.setTextColor(MaterialColors.getColor(view,
                ok ? com.google.android.material.R.attr.colorPrimary
                        : com.google.android.material.R.attr.colorError));
    }

    private void checkRoot(boolean showToast) {
        if (rootCheckRunning || rootSetupRunning) {
            return;
        }
        rootCheckRunning = true;
        refreshSetupUi();
        executor.execute(() -> {
            boolean root = Shell.isRootAvailable();
            main.post(() -> {
                rootCheckRunning = false;
                rootChecked = true;
                rootAvailable = root;
                refreshAll();
                if (showToast) {
                    Toast.makeText(this, root
                                    ? R.string.toast_root_success
                                    : R.string.toast_root_missing,
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void runRootSetup(boolean thenStart) {
        if (rootSetupRunning) {
            return;
        }
        rootSetupRunning = true;
        if (thenStart) {
            btnStart.setEnabled(false);
            btnStart.setText(R.string.root_setup_running);
        }
        refreshSetupUi();

        final String pkg = getPackageName();
        executor.execute(() -> {
            boolean root = Shell.isRootAvailable();
            boolean overlay = false;
            if (root) {
                Shell.su("appops set " + pkg + " SYSTEM_ALERT_WINDOW allow");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Shell.su("pm grant " + pkg + " android.permission.POST_NOTIFICATIONS");
                }
                overlay = Settings.canDrawOverlays(this);
            }

            boolean rootResult = root;
            boolean overlayResult = overlay;
            main.post(() -> {
                rootChecked = true;
                rootAvailable = rootResult;
                rootSetupRunning = false;
                refreshAll();

                if (thenStart) {
                    if (rootResult && overlayResult) {
                        requestNotificationAndStart();
                    } else {
                        Toast.makeText(this, R.string.toast_root_missing,
                                Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(this,
                            rootResult && overlayResult
                                    ? R.string.toast_root_success
                                    : R.string.toast_root_partial,
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void openAppPicker() {
        AppPickerSheet sheet = AppPickerSheet.newInstance(Prefs.getTarget(this));
        sheet.setListener(this);
        sheet.show(getSupportFragmentManager(), "app_picker");
    }

    @Override
    public void onAppPicked(@NonNull AppEntry entry) {
        Prefs.setTarget(this, entry.packageName);
        refreshTargetUi();
    }

    private void startFlow() {
        if (SessionStore.isActive(this) && LockService.isRunning()) {
            Toast.makeText(this, R.string.lock_active_title, Toast.LENGTH_SHORT).show();
            return;
        }

        String pkg = Prefs.getTarget(this);
        if (TextUtils.isEmpty(pkg)) {
            Toast.makeText(this, R.string.toast_no_target, Toast.LENGTH_SHORT).show();
            openAppPicker();
            return;
        }
        if (getPackageManager().getLaunchIntentForPackage(pkg) == null) {
            Toast.makeText(this, R.string.toast_launch_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!rootChecked || !rootAvailable || !Settings.canDrawOverlays(this)) {
            runRootSetup(true);
            return;
        }
        requestNotificationAndStart();
    }

    private void requestNotificationAndStart() {
        if (!rootAvailable || !Settings.canDrawOverlays(this)) {
            runRootSetup(true);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        beginLock();
    }

    private void beginLock() {
        String pkg = Prefs.getTarget(this);
        if (TextUtils.isEmpty(pkg)) {
            return;
        }
        CharSequence label = targetName.getText();
        String name = TextUtils.isEmpty(label) ? pkg : label.toString();
        btnStart.setEnabled(false);
        btnStart.setText(R.string.start_lock_preparing);
        try {
            LockService.start(this, pkg, name,
                    switchGestures.isChecked(),
                    switchBars.isChecked(),
                    switchAwake.isChecked());
        } catch (Exception e) {
            btnStart.setEnabled(true);
            btnStart.setText(R.string.start_lock);
            Toast.makeText(this, R.string.toast_settings_failed, Toast.LENGTH_LONG).show();
            return;
        }
        main.postDelayed(this::refreshActiveUi, 1200L);
    }

    private void endLock() {
        LockService.stop(this);
        btnEndLock.setEnabled(false);
        main.postDelayed(() -> {
            btnEndLock.setEnabled(true);
            refreshAll();
        }, 900L);
    }

    private void openOverlaySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (Exception ignored) {
                Toast.makeText(this, R.string.toast_no_overlay, Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.action_help) {
            showHelp();
            return true;
        }
        return false;
    }

    private void showHelp() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.help_title)
                .setMessage(R.string.help_body)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private void cleanupStaleSession() {
        if (cleanupStarted) {
            return;
        }
        cleanupStarted = true;
        executor.execute(() -> {
            SettingsSnapshot snapshot = SessionStore.getSnapshot(this);
            SettingsController.restore(this, snapshot);
            SessionStore.end(this);
            main.post(() -> {
                cleanupStarted = false;
                refreshAll();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }
}
