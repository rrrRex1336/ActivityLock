package com.rex.activitylock;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.rex.activitylock.data.AppEntry;
import com.rex.activitylock.data.Prefs;
import com.rex.activitylock.data.SessionStore;
import com.rex.activitylock.data.SettingsSnapshot;
import com.rex.activitylock.service.LockService;
import com.rex.activitylock.system.SettingsController;
import com.rex.activitylock.ui.AppPickerSheet;
import com.rex.activitylock.util.Shell;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements AppPickerSheet.Listener {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private ScrollView scrollView;
    private View activeCard;
    private View setupCard;
    private View adbBox;
    private View permissionRows;
    private View dotSetup;
    private View dotSecure;
    private View dotOverlay;
    private MaterialButtonToggleGroup modeGroup;
    private MaterialButton modeRoot;
    private MaterialButton modeAdb;
    private MaterialButton btnStart;
    private MaterialButton btnEndLock;
    private MaterialButton btnRootSetup;
    private MaterialButton btnOverlay;
    private MaterialButton btnDetect;
    private MaterialButton btnCopy;
    private MaterialSwitch switchGestures;
    private MaterialSwitch switchBars;
    private MaterialSwitch switchAwake;
    private TextView targetName;
    private TextView targetPackage;
    private TextView modeCaption;
    private TextView setupTitle;
    private TextView setupDesc;
    private TextView statusSecure;
    private TextView statusOverlay;
    private TextView adbCommands;
    private ImageView targetIcon;

    private boolean rootSetupRunning;
    private boolean cleanupStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        setContentView(R.layout.activity_main);

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (!isFinishing() && hasBasePermissions()) {
                        beginLock();
                    }
                });

        bindViews();
        applyInsets();
        bindInteractions();
        refreshAll();
        handleIntent(getIntent());
    }

    private void bindViews() {
        scrollView = findViewById(R.id.main_scroll);
        activeCard = findViewById(R.id.active_card);
        setupCard = findViewById(R.id.setup_card);
        adbBox = findViewById(R.id.adb_box);
        permissionRows = findViewById(R.id.permission_rows);
        dotSetup = findViewById(R.id.setup_dot);
        dotSecure = findViewById(R.id.dot_secure);
        dotOverlay = findViewById(R.id.dot_overlay);
        modeGroup = findViewById(R.id.mode_group);
        modeRoot = findViewById(R.id.mode_root);
        modeAdb = findViewById(R.id.mode_adb);
        btnStart = findViewById(R.id.btn_start);
        btnEndLock = findViewById(R.id.btn_end_lock);
        btnRootSetup = findViewById(R.id.btn_root_setup);
        btnOverlay = findViewById(R.id.btn_overlay);
        btnDetect = findViewById(R.id.btn_detect);
        btnCopy = findViewById(R.id.btn_copy);
        switchGestures = findViewById(R.id.switch_gestures);
        switchBars = findViewById(R.id.switch_bars);
        switchAwake = findViewById(R.id.switch_awake);
        targetName = findViewById(R.id.target_name);
        targetPackage = findViewById(R.id.target_package);
        targetIcon = findViewById(R.id.target_icon);
        modeCaption = findViewById(R.id.mode_caption);
        setupTitle = findViewById(R.id.setup_title);
        setupDesc = findViewById(R.id.setup_desc);
        statusSecure = findViewById(R.id.status_secure);
        statusOverlay = findViewById(R.id.status_overlay);
        adbCommands = findViewById(R.id.adb_commands);
    }

    private void applyInsets() {
        final View bottomBar = findViewById(R.id.bottom_bar);
        final int baseBottomPadding = bottomBar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(scrollView, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(),
                    v.getPaddingBottom());
            bottomBar.setPadding(bottomBar.getPaddingLeft(), bottomBar.getPaddingTop(),
                    bottomBar.getPaddingRight(), baseBottomPadding + bars.bottom);
            return insets;
        });
    }

    private void bindInteractions() {
        findViewById(R.id.target_card).setOnClickListener(v -> openAppPicker());
        findViewById(R.id.btn_help).setOnClickListener(v -> showHelp());
        btnEndLock.setOnClickListener(v -> endLock());
        btnStart.setOnClickListener(v -> startFlow());
        btnRootSetup.setOnClickListener(v -> runRootSetup());
        btnOverlay.setOnClickListener(v -> openOverlaySettings());
        btnDetect.setOnClickListener(v -> checkPermissionsNow());
        btnCopy.setOnClickListener(v -> copyAdbCommands());

        String mode = Prefs.getMode(this);
        modeGroup.check(Prefs.MODE_ROOT.equals(mode) ? R.id.mode_root : R.id.mode_adb);
        modeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            Prefs.setMode(this, checkedId == R.id.mode_root
                    ? Prefs.MODE_ROOT : Prefs.MODE_ADB);
            refreshModeUi();
            refreshSetupUi();
        });

        switchGestures.setChecked(Prefs.blockGestures(this));
        switchBars.setChecked(Prefs.hideBars(this));
        switchAwake.setChecked(Prefs.keepAwake(this));

        switchGestures.setOnCheckedChangeListener((buttonView, isChecked) ->
                Prefs.setBlockGestures(this, isChecked));
        switchBars.setOnCheckedChangeListener((buttonView, isChecked) ->
                Prefs.setHideBars(this, isChecked));
        switchAwake.setOnCheckedChangeListener((buttonView, isChecked) ->
                Prefs.setKeepAwake(this, isChecked));

        updateAdbCommands();
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
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.notification_error_title)
                    .setMessage(error)
                    .setPositiveButton(R.string.close, null)
                    .show();
        }
    }

    private void refreshAll() {
        refreshTargetUi();
        refreshModeUi();
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
            targetIcon.setImageResource(R.drawable.ic_logo);
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
            targetIcon.setImageResource(R.drawable.ic_logo);
        }
    }

    private void refreshModeUi() {
        boolean root = Prefs.MODE_ROOT.equals(Prefs.getMode(this));
        int wantedId = root ? R.id.mode_root : R.id.mode_adb;
        if (modeGroup.getCheckedButtonId() != wantedId) {
            modeGroup.check(wantedId);
        }
        modeCaption.setText(root ? R.string.mode_hint_root : R.string.mode_hint_adb);
    }

    private void refreshSetupUi() {
        boolean secure = SettingsController.hasWritePermission(this);
        boolean overlay = Settings.canDrawOverlays(this);
        boolean ready = secure && overlay;
        boolean rootMode = Prefs.MODE_ROOT.equals(Prefs.getMode(this));

        tintStatus(statusSecure, secure);
        tintStatus(statusOverlay, overlay);
        tintDot(dotSecure, secure);
        tintDot(dotOverlay, overlay);
        tintDot(dotSetup, ready);
        setupTitle.setText(ready ? R.string.setup_ready : R.string.setup_need);
        setupDesc.setText(ready
                ? R.string.setup_ready_desc
                : rootMode ? R.string.setup_need_root : R.string.setup_need_adb);

        adbBox.setVisibility(!ready && !rootMode ? View.VISIBLE : View.GONE);
        btnRootSetup.setVisibility(!ready && rootMode ? View.VISIBLE : View.GONE);
        btnDetect.setVisibility(!ready && !rootMode ? View.VISIBLE : View.GONE);
        btnOverlay.setVisibility(overlay ? View.GONE : View.VISIBLE);
        permissionRows.setVisibility(ready ? View.GONE : View.VISIBLE);
        btnRootSetup.setEnabled(!rootSetupRunning);
        btnRootSetup.setText(rootSetupRunning
                ? R.string.root_setup_running : R.string.root_setup);
    }

    private void tintStatus(TextView view, boolean ok) {
        view.setText(ok ? R.string.permission_ok : R.string.permission_missing);
        view.setTextColor(ContextCompat.getColor(this,
                ok ? R.color.success : R.color.warning));
    }

    private void tintDot(View dot, boolean ok) {
        ViewCompat.setBackgroundTintList(dot, ContextCompat.getColorStateList(this,
                ok ? R.color.success : R.color.warning));
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
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.toast_no_overlay, Toast.LENGTH_LONG).show();
            openOverlaySettings();
            return;
        }
        if (!SettingsController.hasWritePermission(this)) {
            showSecureSettingHelp();
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

    private boolean hasBasePermissions() {
        return Settings.canDrawOverlays(this)
                && SettingsController.hasWritePermission(this)
                && !TextUtils.isEmpty(Prefs.getTarget(this));
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

    private void showSecureSettingHelp() {
        scrollToSetup();
        Snackbar.make(scrollView, R.string.toast_no_secure, Snackbar.LENGTH_LONG).show();
    }

    private void scrollToSetup() {
        scrollView.post(() -> scrollView.smoothScrollTo(0,
                Math.max(0, setupCard.getTop() - dp(70))));
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

    private void checkPermissionsNow() {
        boolean secure = SettingsController.hasWritePermission(this);
        boolean overlay = Settings.canDrawOverlays(this);
        refreshAll();
        Toast.makeText(this, secure && overlay
                        ? R.string.toast_permission_ok : R.string.toast_permission_missing,
                Toast.LENGTH_SHORT).show();
    }

    private void runRootSetup() {
        if (rootSetupRunning) {
            return;
        }
        rootSetupRunning = true;
        refreshSetupUi();
        final String pkg = getPackageName();

        executor.execute(() -> {
            boolean root = Shell.isRootAvailable();
            if (!root) {
                main.post(() -> {
                    rootSetupRunning = false;
                    refreshAll();
                    Toast.makeText(this, R.string.toast_root_missing, Toast.LENGTH_LONG).show();
                });
                return;
            }

            Shell.su("pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS");
            Shell.su("appops set " + pkg + " SYSTEM_ALERT_WINDOW allow");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Shell.su("pm grant " + pkg + " android.permission.POST_NOTIFICATIONS");
            }

            boolean secure = SettingsController.hasWritePermission(this);
            boolean overlay = Settings.canDrawOverlays(this);
            main.post(() -> {
                rootSetupRunning = false;
                refreshAll();
                Toast.makeText(this,
                        secure && overlay
                                ? R.string.toast_root_success : R.string.toast_root_partial,
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private void copyAdbCommands() {
        ClipboardManager clipboard = ContextCompat.getSystemService(this, ClipboardManager.class);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("ActivityLock ADB",
                buildAdbCommands(getPackageName())));
        Toast.makeText(this, R.string.commands_copied, Toast.LENGTH_SHORT).show();
    }

    private void updateAdbCommands() {
        if (adbCommands != null) {
            adbCommands.setText(buildAdbCommands(getPackageName()));
        }
    }

    private static String buildAdbCommands(String pkg) {
        return "adb shell pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS\n"
                + "adb shell appops set " + pkg + " SYSTEM_ALERT_WINDOW allow";
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

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }
}
