package com.rex.activitylock.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;

import com.rex.activitylock.R;

/**
 * Overlay used to leave the lock. The only interactive area is a small hot corner;
 * the rest of the screen remains fully usable by the game.
 */
final class LockOverlay {

    interface Callback {
        void onExitConfirmed();
    }

    private static final long LONG_PRESS_MS = 1500L;
    private static final long HINT_VISIBLE_MS = 2800L;

    private final Context service;
    private final WindowManager windowManager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Callback callback;

    @Nullable
    private View hotspot;
    @Nullable
    private View hint;
    @Nullable
    private View exitDialog;

    private final Runnable hintDismisser = this::dismissHint;

    private boolean keepScreenOn;
    private boolean exitShowing;

    LockOverlay(@NonNull Context service, @NonNull Callback callback) {
        this.service = service;
        this.windowManager = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        this.callback = callback;
    }

    @SuppressLint("InflateParams")
    boolean show(boolean keepScreenOn, boolean gestureWarning) {
        this.keepScreenOn = keepScreenOn;
        removeAll();

        if (windowManager == null) {
            return false;
        }

        LayoutInflater inflater = LayoutInflater.from(service);
        hotspot = inflater.inflate(R.layout.overlay_hotspot, null);
        WindowManager.LayoutParams params = baseParams(44, 44);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(4);
        params.y = dp(46);
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        if (keepScreenOn) {
            params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        }
        installLongPress(hotspot);

        try {
            windowManager.addView(hotspot, params);
        } catch (Exception e) {
            hotspot = null;
            return false;
        }

        showHint(gestureWarning
                ? service.getString(R.string.overlay_hint_failed)
                : service.getString(R.string.overlay_hint), gestureWarning);
        return true;
    }

    @SuppressLint("InflateParams")
    void showHint(@NonNull String text, boolean warning) {
        if (windowManager == null) {
            return;
        }
        main.removeCallbacks(hintDismisser);
        dismissHint();
        LayoutInflater inflater = LayoutInflater.from(service);
        View view = inflater.inflate(R.layout.overlay_hint, null);
        TextView label = view.findViewById(R.id.hint_text);
        label.setText(text);
        if (warning) {
            label.setTextColor(service.getColor(R.color.warning));
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
            // A blocked overlay must not prevent the lock from starting.
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

    @SuppressLint({"ClickableViewAccessibility", "InflateParams"})
    private void installLongPress(View view) {
        int slop = ViewConfiguration.get(service).getScaledTouchSlop();
        final float[] down = new float[2];
        final boolean[] moved = new boolean[1];

        Runnable trigger = () -> {
            if (exitShowing) {
                return;
            }
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showExitDialog();
        };

        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    moved[0] = false;
                    down[0] = event.getRawX();
                    down[1] = event.getRawY();
                    main.postDelayed(trigger, LONG_PRESS_MS);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!moved[0]
                            && (Math.abs(event.getRawX() - down[0]) > slop
                            || Math.abs(event.getRawY() - down[1]) > slop)) {
                        moved[0] = true;
                        main.removeCallbacks(trigger);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    main.removeCallbacks(trigger);
                    return true;
                default:
                    return false;
            }
        });
    }

    @SuppressLint("InflateParams")
    private void showExitDialog() {
        if (exitShowing || windowManager == null) {
            return;
        }
        Context themed = new ContextThemeWrapper(service, R.style.Theme_ActivityLock);
        View view = LayoutInflater.from(themed).inflate(R.layout.overlay_exit, null);
        WindowManager.LayoutParams params = baseParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;
        params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

        view.findViewById(R.id.btn_exit_confirm).setOnClickListener(v -> {
            hideExitDialog();
            callback.onExitConfirmed();
        });
        view.findViewById(R.id.btn_exit_keep).setOnClickListener(v -> hideExitDialog());
        view.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                    && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                hideExitDialog();
                return true;
            }
            return false;
        });

        try {
            windowManager.addView(view, params);
            exitDialog = view;
            exitShowing = true;
            view.requestFocus();
        } catch (Exception ignored) {
            exitShowing = false;
        }
    }

    private void hideExitDialog() {
        View current = exitDialog;
        exitDialog = null;
        exitShowing = false;
        if (current != null && windowManager != null) {
            try {
                windowManager.removeView(current);
            } catch (Exception ignored) {
            }
        }
    }

    void hide() {
        removeAll();
    }

    private void removeAll() {
        main.removeCallbacksAndMessages(null);
        hideExitDialog();
        if (hint != null) {
            View current = hint;
            hint = null;
            try {
                windowManager.removeView(current);
            } catch (Exception ignored) {
            }
        }
        if (hotspot != null) {
            View current = hotspot;
            hotspot = null;
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

    private int dp(float value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }
}
