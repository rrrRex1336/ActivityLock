package com.rex.activitylock.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AnimationUtils;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.rex.activitylock.R;

public class BeatView extends View {

    private static final int BAR_COUNT = 38;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float[] phases = new float[BAR_COUNT];
    private final float[] speeds = new float[BAR_COUNT];

    private long startTime;
    private boolean running;

    public BeatView(Context context) {
        this(context, null);
    }

    public BeatView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BeatView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        paint.setStyle(Paint.Style.FILL);
        int lime = ContextCompat.getColor(context, R.color.lime);
        int cyan = ContextCompat.getColor(context, R.color.cyan);
        paint.setShader(new LinearGradient(0, 0, getResources().getDisplayMetrics().widthPixels, 0,
                new int[]{lime, cyan, lime}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        paint.setAlpha(125);

        for (int i = 0; i < BAR_COUNT; i++) {
            phases[i] = (float) (i * 0.43);
            speeds[i] = 0.55f + (i % 7) * 0.08f;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startTime = AnimationUtils.currentAnimationTimeMillis();
        running = !isInEditMode();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        if (width <= 0 || getHeight() <= 0) {
            return;
        }

        float gap = width / (BAR_COUNT * 2.35f);
        float barWidth = (width - gap * (BAR_COUNT - 1)) / BAR_COUNT;
        float maxHeight = getHeight() * 0.72f;
        float time = running
                ? (AnimationUtils.currentAnimationTimeMillis() - startTime) / 1000f
                : 0f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float wave = (float) (Math.sin(time * speeds[i] * 2.4f + phases[i]) * 0.5 + 0.5);
            float envelope = 0.32f + 0.68f * Math.abs(
                    (float) Math.sin(i * 0.28f + time * 0.4f));
            float height = maxHeight * (0.18f + 0.82f * wave * envelope);
            float left = i * (barWidth + gap);
            float center = getHeight() * 0.5f;
            float half = height * 0.5f;
            rect.set(left, center - half, left + barWidth, center + half);
            float radius = Math.min(barWidth / 2f, 2.4f * getResources().getDisplayMetrics().density);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
        if (running) {
            postInvalidateOnAnimation();
        }
    }
}
