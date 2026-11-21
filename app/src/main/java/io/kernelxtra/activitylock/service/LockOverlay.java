package io.kernelxtra.activitylock.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import io.kernelxtra.activitylock.R;
import io.kernelxtra.activitylock.data.Prefs;

/**
 * Small draggable floating bubble. A single tap opens a compact control panel with
 * one-tap unlock and app switching. No full-screen touch interceptor is used over
 * the game itself.
 */
final class LockOverlay {

    interface Callback {
        void onExitConfirmed();

        void onSwitchRequested();
    }

    private static final long HINT_VISIBLE_MS = 2800L;

    private final Context service;
    private final WindowManager windowManager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Callback callback;

    @Nullable
    private View bubble;
    @Nullable
    private WindowManager.LayoutParams bubbleParams;
    @Nullable
    private View hint;
    @Nullable
    private View controlPanel;

    private final Runnable hintDismisser = this::dismissHint;

    private boolean keepScreenOn;
    private boolean controlShowing;
    private String targetPackage;
    private String targetName = "";

    LockOverlay(@NonNull Context service, @NonNull Callback callback) {
        this.service = service;
        this.windowManager = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        this.callback = callback;
    }

    @SuppressLint("InflateParams")
    boolean show(boolean keepScreenOn, boolean gestureWarning,
                 @Nullable String targetPackage, @Nullable String targetName) {
        this.keepScreenOn = keepScreenOn;
        this.targetPackage = targetPackage;
        this.targetName = targetName == null ? "" : targetName;
        removeAll();

        if (windowManager == null) {
            return false;
        }

        Context overlayContext = overlayContext();
        LayoutInflater inflater = LayoutInflater.from(overlayContext);
        try {
            bubble = inflater.inflate(R.layout.overlay_hotspot, null);
        } catch (Exception e) {
            bubble = null;
            return false;
        }
        MaterialCardView bubbleCard = bubble.findViewById(R.id.hotspot_bubble);
        tintSurface(bubbleCard);
        bubbleParams = baseParams(dp(48), dp(48));
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = Prefs.getBubbleX(service, dp(6));
        bubbleParams.y = Prefs.getBubbleY(service, dp(58));
        clampBubblePosition();
        bubbleParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        if (keepScreenOn) {
            bubbleParams.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        }
        installBubbleTouch(bubble);

        try {
            windowManager.addView(bubble, bubbleParams);
        } catch (Exception e) {
            bubble = null;
            bubbleParams = null;
            return false;
        }

        showHint(gestureWarning
                ? service.getString(R.string.overlay_hint_failed)
                : service.getString(R.string.overlay_hint), gestureWarning);
        return true;
    }

    void updateTarget(@Nullable String packageName, @Nullable String name) {
        targetPackage = packageName;
        targetName = name == null ? "" : name;
        if (controlShowing && controlPanel != null) {
            bindPanelTarget(controlPanel);
        }
    }

    @SuppressLint({"ClickableViewAccessibility", "InflateParams"})
    private void installBubbleTouch(@NonNull View view) {
        final int slop = ViewConfiguration.get(service).getScaledTouchSlop();
        final float[] down = new float[2];
        final int[] start = new int[2];
        final boolean[] dragged = new boolean[1];

        view.setClickable(true);
        view.setOnClickListener(v -> showControlPanel());
        view.setOnTouchListener((v, event) -> {
            WindowManager.LayoutParams params = bubbleParams;
            if (params == null) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getRawX();
                    down[1] = event.getRawY();
                    start[0] = params.x;
                    start[1] = params.y;
                    dragged[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - down[0];
                    float dy = event.getRawY() - down[1];
                    if (!dragged[0] && Math.hypot(dx, dy) > slop) {
                        dragged[0] = true;
                    }
                    if (dragged[0]) {
                        params.x = start[0] + Math.round(dx);
                        params.y = start[1] + Math.round(dy);
                        clampBubblePosition();
                        try {
                            windowManager.updateViewLayout(v, params);
                        } catch (Exception ignored) {
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragged[0]) {
                        Prefs.setBubblePosition(service, params.x, params.y);
                    } else {
                        v.performClick();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (dragged[0]) {
                        Prefs.setBubblePosition(service, params.x, params.y);
                    }
                    return true;
                default:
                    return false;
            }
        });
    }

    private void clampBubblePosition() {
        WindowManager.LayoutParams params = bubbleParams;
        if (params == null) {
            return;
        }
        Point size = screenSize();
        int maxX = Math.max(0, size.x - dp(48));
        int maxY = Math.max(0, size.y - dp(48));
        params.x = Math.max(0, Math.min(params.x, maxX));
        params.y = Math.max(0, Math.min(params.y, maxY));
    }

    @NonNull
    private Point screenSize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = windowManager.getCurrentWindowMetrics().getBounds();
            return new Point(bounds.width(), bounds.height());
        }
        Point point = new Point();
        windowManager.getDefaultDisplay().getRealSize(point);
        return point;
    }

    @SuppressLint("InflateParams")
    private void showControlPanel() {
        if (controlShowing || windowManager == null) {
            return;
        }
        bubble.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        Context overlayContext = overlayContext();
        View panel;
        try {
            panel = LayoutInflater.from(overlayContext).inflate(R.layout.overlay_control, null);
        } catch (Exception e) {
            return;
        }
        MaterialCardView surface = panel.findViewById(R.id.panel_surface);
        tintSurface(surface);
        if (surface != null) {
            ViewGroup.LayoutParams surfaceParams = surface.getLayoutParams();
            surfaceParams.width = Math.min(screenSize().x - dp(32), dp(560));
            surface.setLayoutParams(surfaceParams);
        }
        WindowManager.LayoutParams params = baseParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;
        params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        if (keepScreenOn) {
            params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        }

        bindPanelTarget(panel);
        panel.findViewById(R.id.btn_switch_app).setOnClickListener(v -> {
            hideControlPanel();
            callback.onSwitchRequested();
        });
        panel.findViewById(R.id.btn_exit_confirm).setOnClickListener(v -> {
            hideControlPanel();
            callback.onExitConfirmed();
        });
        panel.findViewById(R.id.btn_exit_keep).setOnClickListener(v -> hideControlPanel());
        panel.findViewById(R.id.control_root).setOnClickListener(v -> hideControlPanel());
        panel.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                hideControlPanel();
                return true;
            }
            return false;
        });

        try {
            windowManager.addView(panel, params);
            controlPanel = panel;
            controlShowing = true;
            panel.requestFocus();
        } catch (Exception ignored) {
            controlShowing = false;
        }
    }

    private void bindPanelTarget(@NonNull View panel) {
        TextView label = panel.findViewById(R.id.panel_app_name);
        label.setText(targetName.isEmpty() ? service.getString(R.string.control_title) : targetName);
        ImageView icon = panel.findViewById(R.id.panel_app_icon);
        if (targetPackage != null) {
            try {
                PackageManager pm = service.getPackageManager();
                Drawable drawable = pm.getApplicationIcon(targetPackage);
                icon.setImageDrawable(drawable);
                return;
            } catch (Exception ignored) {
            }
        }
        icon.setImageResource(R.drawable.ic_lock);
    }

    private void hideControlPanel() {
        View current = controlPanel;
        controlPanel = null;
        controlShowing = false;
        if (current != null && windowManager != null) {
            try {
                windowManager.removeView(current);
            } catch (Exception ignored) {
            }
        }
    }

    @SuppressLint("InflateParams")
    void showHint(@NonNull String text, boolean warning) {
        if (windowManager == null) {
            return;
        }
        main.removeCallbacks(hintDismisser);
        dismissHint();
        LayoutInflater inflater = LayoutInflater.from(overlayContext());
        View view;
        try {
            view = inflater.inflate(R.layout.overlay_hint, null);
        } catch (Exception e) {
            return;
        }
        tintSurface(view.findViewById(R.id.hint_surface));
        TextView label = view.findViewById(R.id.hint_text);
        label.setText(text);
        if (warning) {
            label.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                    view, com.google.android.material.R.attr.colorError));
        }

        WindowManager.LayoutParams params = baseParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dp(46);
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        if (keepScreenOn) {
            params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        }

        try {
            windowManager.addView(view, params);
            hint = view;
            view.setAlpha(0f);
            view.animate().alpha(1f).setDuration(180).start();
            main.postDelayed(hintDismisser, HINT_VISIBLE_MS);
        } catch (Exception ignored) {
        }
    }

    private void dismissHint() {
        View current = hint;
        hint = null;
        if (current == null) {
            return;
        }
        current.animate().alpha(0f).setDuration(160).withEndAction(() -> {
            if (windowManager != null) {
                try {
                    windowManager.removeView(current);
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    void hide() {
        removeAll();
    }

    private void removeAll() {
        main.removeCallbacksAndMessages(null);
        hideControlPanel();
        if (hint != null) {
            View current = hint;
            hint = null;
            try {
                windowManager.removeView(current);
            } catch (Exception ignored) {
            }
        }
        if (bubble != null) {
            View current = bubble;
            bubble = null;
            bubbleParams = null;
            try {
                windowManager.removeView(current);
            } catch (Exception ignored) {
            }
        }
    }

    @NonNull
    private WindowManager.LayoutParams baseParams(int width, int height) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        return params;
    }

    @NonNull
    private Context overlayContext() {
        return new ContextThemeWrapper(
                DynamicColors.wrapContextIfAvailable(service), R.style.Theme_ActivityLock);
    }

    private void tintSurface(@Nullable MaterialCardView card) {
        if (card == null) {
            return;
        }
        card.setCardBackgroundColor(MaterialColors.getColor(card,
                com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xFF1C1B1F));
    }

    private int dp(float value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }
}
