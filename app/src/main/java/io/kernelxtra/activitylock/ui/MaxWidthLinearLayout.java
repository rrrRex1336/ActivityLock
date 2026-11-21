package io.kernelxtra.activitylock.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

/**
 * Keeps single-column settings content at a readable width on tablets and desktop windows.
 */
public class MaxWidthLinearLayout extends LinearLayout {

    private static final int MAX_WIDTH_DP = 640;

    private final int maxWidthPx;

    public MaxWidthLinearLayout(Context context) {
        this(context, null);
    }

    public MaxWidthLinearLayout(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MaxWidthLinearLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        maxWidthPx = Math.round(MAX_WIDTH_DP
                * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width > maxWidthPx) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidthPx, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
