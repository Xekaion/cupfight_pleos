package io.autohmg.cupfight;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.StateSet;

final class UiSkin {
    private static final int CYAN = 0xFF93E8F5;
    private static final int GRAPHITE = 0xFF1A2A3A;
    private static final int SLATE = 0xFF27394D;
    private static final int CARBON = 0xFF121D2A;
    private static final int BUTTON_RADIUS_DP = 14;

    private final float density;
    private final Bitmap primaryTile;
    private final Bitmap secondaryTile;
    private final Bitmap disabledTile;
    private final Bitmap surfaceTile;

    UiSkin(Context context) {
        Resources resources = context.getResources();
        density = resources.getDisplayMetrics().density;
        primaryTile = BitmapFactory.decodeResource(resources, R.drawable.ui_primary);
        secondaryTile = BitmapFactory.decodeResource(resources, R.drawable.ui_secondary);
        disabledTile = BitmapFactory.decodeResource(resources, R.drawable.ui_disabled);
        surfaceTile = BitmapFactory.decodeResource(resources, R.drawable.ui_surface);
    }

    Drawable button(boolean primary) {
        Bitmap tile = primary ? primaryTile : secondaryTile;
        int fill = primary ? CYAN : GRAPHITE;
        float radius = BUTTON_RADIUS_DP * density;
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled}, new MaterialDrawable(disabledTile,
                SLATE, 0x40081019, 0xFF34485B, radius, density, -1));
        states.addState(new int[]{android.R.attr.state_focused}, new MaterialDrawable(tile,
                fill, 0x12FFFFFF, primary ? 0xFF234A5B : CYAN, radius, 2f * density, -1));
        states.addState(StateSet.WILD_CARD, new MaterialDrawable(tile,
                fill, Color.TRANSPARENT, primary ? 0xFFA6E3EF : 0xFF38566D, radius, density, -1));

        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(radius);
        int ripple = primary ? 0x330A1018 : 0x3393E8F5;
        return new RippleDrawable(ColorStateList.valueOf(ripple), states, mask);
    }

    Drawable surface(int radiusDp) {
        return new MaterialDrawable(surfaceTile, CARBON, 0x5909101B,
                0xFF314457, radiusDp * density, density, -1);
    }

    Drawable replayThumb() {
        int size = Math.round(24 * density);
        return new MaterialDrawable(primaryTile, CYAN, Color.TRANSPARENT,
                0xFFCEEAF2, size * 0.5f, 1.5f * density, size);
    }

    private static final class MaterialDrawable extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint detail = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF panel = new RectF();
        private final RectF rim = new RectF();
        private final Matrix textureMatrix = new Matrix();
        private final Bitmap tile;
        private final BitmapShader shader;
        private final int overlay;
        private final int border;
        private final float radius;
        private final float stroke;
        private final int size;
        private int alpha = 255;

        MaterialDrawable(Bitmap tile, int color, int overlay, int border, float radius,
                         float stroke, int size) {
            this.tile = tile;
            this.overlay = overlay;
            this.border = border;
            this.radius = radius;
            this.stroke = stroke;
            this.size = size;
            fill.setColor(color);
            shader = tile == null ? null : new BitmapShader(tile, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            fill.setShader(shader);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            panel.set(bounds);
            rim.set(panel);
            rim.inset(stroke * 0.5f, stroke * 0.5f);
            if (shader != null && !bounds.isEmpty()) {
                textureMatrix.setScale(panel.width() / tile.getWidth(), panel.height() / tile.getHeight());
                textureMatrix.postTranslate(panel.left, panel.top);
                shader.setLocalMatrix(textureMatrix);
            }
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawRoundRect(panel, radius, radius, fill);
            if (overlay != Color.TRANSPARENT) {
                detail.setColor(overlay);
                detail.setAlpha(Color.alpha(overlay) * alpha / 255);
                detail.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(panel, radius, radius, detail);
            }
            detail.setColor(border);
            detail.setAlpha(Color.alpha(border) * alpha / 255);
            detail.setStyle(Paint.Style.STROKE);
            detail.setStrokeWidth(stroke);
            float rimRadius = Math.max(0f, radius - stroke * 0.5f);
            canvas.drawRoundRect(rim, rimRadius, rimRadius, detail);
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
            fill.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            fill.setColorFilter(filter);
            detail.setColorFilter(filter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
