package io.autohmg.cupfight;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

final class MatchBackdrop extends View {
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF outerRing = new RectF();
    private final RectF innerRing = new RectF();

    MatchBackdrop(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        float x = width * 0.5f;
        float y = height * 0.44f;
        float radius = Math.max(width * 0.55f, height * 0.72f);
        if (radius <= 0) return;
        glow.setShader(new RadialGradient(x, y, radius,
                new int[]{0xFF193449, 0xFF0E1D2C, 0xFF090F18},
                new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
        float ringRadius = Math.min(width * 0.37f, height * 0.62f);
        outerRing.set(x - ringRadius, y - ringRadius, x + ringRadius, y + ringRadius);
        innerRing.set(outerRing);
        innerRing.inset(ringRadius * 0.10f, ringRadius * 0.10f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), glow);
        line.setColor(0x355B9FB0);
        canvas.drawArc(outerRing, -36, 72, false, line);
        canvas.drawArc(outerRing, 144, 72, false, line);
        line.setColor(0x205B9FB0);
        canvas.drawArc(innerRing, -31, 62, false, line);
        canvas.drawArc(innerRing, 149, 62, false, line);
    }
}
