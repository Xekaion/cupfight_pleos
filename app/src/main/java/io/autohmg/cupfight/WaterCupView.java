package io.autohmg.cupfight;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** Advances the visual fluid model and projects its geometry independently of duel scoring. */
public final class WaterCupView extends View {
    private static final float SAMPLE_SECONDS = DriveSimulation.FRAME_MILLIS / 1000f;
    private static final float MAX_ELAPSED_SECONDS = 0.10f;

    private final CupFluidSimulation fluid = new CupFluidSimulation();
    private final WaterMeshRenderer mesh = new WaterMeshRenderer();
    private final Paint stage;
    private final boolean continuousWhilePlaying;
    private Cosmetic cupStyle;
    private boolean playing = true;
    private long lastFrameNanos;
    private float remainingPercent = 100f;
    private float lateralMps2;
    private float longitudinalMps2;
    private float styleSeconds;

    public WaterCupView(Context context) {
        this(context, false);
    }

    WaterCupView(Context context, boolean continuousWhilePlaying) {
        super(context);
        this.continuousWhilePlaying = continuousWhilePlaying;
        stage = continuousWhilePlaying ? null : new Paint(Paint.ANTI_ALIAS_FLAG);
        fluid.reset(remainingPercent);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("가상 물잔");
    }

    public void setFrame(float remaining, float lateral, float longitudinal) {
        if (playing && isShown()) {
            advanceClock(System.nanoTime());
        }
        setInput(remaining, lateral, longitudinal);
        fluid.advance(0f, remainingPercent, lateralMps2, longitudinalMps2);
        postInvalidateOnAnimation();
    }

    public void reset(float remaining) {
        remainingPercent = Float.isFinite(remaining) ? Math.max(0f, Math.min(100f, remaining)) : 100f;
        lateralMps2 = 0f;
        longitudinalMps2 = 0f;
        styleSeconds = 0f;
        lastFrameNanos = 0L;
        fluid.reset(remainingPercent);
        invalidate();
    }

    // Rebuild the waves and airborne water for a seek using the recorded input interval.
    void restoreFrame(float remaining, float lateral, float longitudinal) {
        advance(SAMPLE_SECONDS);
        setInput(remaining, lateral, longitudinal);
        fluid.advance(0f, remainingPercent, lateralMps2, longitudinalMps2);
    }

    public void setPlaying(boolean next) {
        if (playing != next) {
            playing = next;
            lastFrameNanos = 0L;
            invalidate();
        }
    }

    void setCupStyle(Cosmetic style) {
        cupStyle = style;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (stage != null) {
            updateStage(stage, width, height);
        }
    }

    static void updateStage(Paint paint, int width, int height) {
        paint.setColor(0xff090d14);
        if (width > 0 && height > 0) {
            paint.setShader(new RadialGradient(width * 0.5f, height * 0.71f,
                    Math.max(width * 0.48f, height * 0.61f),
                    new int[]{0xff28363e, 0xff181f2a, 0xff090d14},
                    new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (playing) {
            advanceClock(System.nanoTime());
        }
        if (stage != null) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), stage);
        }
        float height = Math.min(getHeight() * 0.65f, getWidth() * 0.66f);
        float width = height * 0.69f;
        boolean animateFinish = cupStyle == Cosmetic.PRISM_CUP && ValueAnimator.areAnimatorsEnabled();
        mesh.draw(canvas, (getWidth() - width) / 2f, getHeight() * 0.53f - height * 0.40f,
                width, height, fluid, cupStyle, animateFinish ? styleSeconds : 0f);
        if (playing && (continuousWhilePlaying || fluid.isMoving() || animateFinish)) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        lastFrameNanos = 0L;
        super.onDetachedFromWindow();
    }

    private void setInput(float remaining, float lateral, float longitudinal) {
        if (Float.isFinite(remaining)) {
            remainingPercent = Math.max(0f, Math.min(100f, remaining));
        }
        lateralMps2 = lateral;
        longitudinalMps2 = longitudinal;
    }

    private void advanceClock(long now) {
        if (lastFrameNanos != 0L && now > lastFrameNanos) {
            advance(Math.min(MAX_ELAPSED_SECONDS, (now - lastFrameNanos) / 1_000_000_000f));
        }
        lastFrameNanos = now;
    }

    private void advance(float seconds) {
        fluid.advance(seconds, remainingPercent, lateralMps2, longitudinalMps2);
        styleSeconds = (styleSeconds + seconds) % 120f;
    }
}
