package io.autohmg.cupfight;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

/** The local cup and its remaining water during the fullscreen demo. */
final class LiveDuelView extends FrameLayout {
    private final WaterCupView cup;
    private boolean active;
    private int displayedTenths = -1;

    LiveDuelView(Context context) {
        super(context);
        Backdrop backdrop = new Backdrop(context);
        addView(backdrop, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        cup = new WaterCupView(context, true);
        cup.setContentDescription("내 물잔");
        addView(cup, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));

        setVisibility(GONE);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Leave room for ballistic droplets while limiting the area damaged on each display tick.
        int height = MeasureSpec.getSize(heightMeasureSpec);
        cup.getLayoutParams().width = Math.min(MeasureSpec.getSize(widthMeasureSpec), Math.round(height * 1.18f));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    void start(float remainingPercent, Cosmetic cupStyle) {
        float remaining = Float.isFinite(remainingPercent) ? clampPercent(remainingPercent) : 100f;
        cup.setCupStyle(cupStyle);
        cup.reset(remaining);
        updateScore(remaining);
        active = true;
        cup.setPlaying(true);
        setVisibility(VISIBLE);
    }

    void setFrame(float remainingPercent, float lateralMps2, float longitudinalMps2) {
        if (!active) {
            return;
        }
        cup.setFrame(remainingPercent, lateralMps2, longitudinalMps2);
        if (Float.isFinite(remainingPercent)) {
            updateScore(clampPercent(remainingPercent));
        }
    }

    void setPlaying(boolean playing) {
        cup.setPlaying(active && playing);
    }

    void end() {
        active = false;
        cup.setPlaying(false);
        setVisibility(GONE);
    }

    private void updateScore(float remaining) {
        int tenths = Math.round(remaining * 10f);
        if (displayedTenths != tenths) {
            displayedTenths = tenths;
            String label = (tenths / 10) + "." + (tenths % 10) + "%";
            cup.setContentDescription("내 물잔 · 남은 물 " + label);
        }
    }

    private static float clampPercent(float percent) {
        return Math.max(0f, Math.min(100f, percent));
    }

    private static final class Backdrop extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Backdrop(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setLayerType(LAYER_TYPE_HARDWARE, null);
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            WaterCupView.updateStage(paint, width, height);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }
    }

}
