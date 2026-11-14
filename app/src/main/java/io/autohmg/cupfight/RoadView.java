package io.autohmg.cupfight;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import java.util.EnumSet;
import java.util.Set;

import io.autohmg.cupfight.CarMeshRenderer.TrafficMesh;
import io.autohmg.cupfight.CarMeshRenderer.TrafficStyle;

public final class RoadView extends View {
    private static final int EGO_COLOR = 0xff91e1e1;
    private static final int OPPONENT_COLOR = 0xffb8c1dc;
    private static final int MARKING_COLOR = 0xff9baeb7;
    private static final float LANE_METERS = RoadVehicleProjection.LANE_METERS;
    private static final float CAMERA_DEPTH_METERS = RoadVehicleProjection.CAMERA_DEPTH_METERS;
    private static final float DASH_INTERVAL = 14f;
    private static final float ARRIVAL_METERS = 36f;
    private static final long COSMETIC_FRAME_MILLIS = 33L;
    private static final float OPPONENT_LANE = 1f;
    private static final float OPPONENT_DISTANCE = 17f;
    private static final float[] EFFECT_PLANE = {-1f, 0f, 1f, 0f, 1f, 1f, -1f, 1f};
    private static final float[][] TRAFFIC = {{-1f, 9f}, {2f, 31f}, {-2f, 47f},
            {0f, 71f}, {-1f, 116f}, {2f, 149f}, {-2f, 184f}};
    private static final TrafficStyle[] TRAFFIC_STYLES = {
            TrafficStyle.COPPER, TrafficStyle.TEAL, TrafficStyle.GRAPHITE, TrafficStyle.SILVER};
    private static final float[] LAMP_DISTANCES = {17f, 39f, 73f, 125f};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spritePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path shape = new Path();
    private final Picture street = new Picture();
    private final EnumSet<Cosmetic> ownCosmetics = EnumSet.noneOf(Cosmetic.class);
    private final Rect ownCarSource = new Rect();
    private final Rect otherCarSource = new Rect();
    private final RectF spriteDestination = new RectF();
    private final Bitmap backdrop;
    private final Bitmap ownCarSprite;
    private final Bitmap otherCarSprite;
    private final Bitmap wingSprite;
    private final Rect wingSource = new Rect();
    private final RectF effectBounds = new RectF();
    private final Matrix effectTransform = new Matrix();
    private final Matrix projectedEffect = new Matrix();
    private final float[] effectCorners = new float[8];
    private final CarMeshRenderer vehicleMesh;
    private final PorterDuffXfermode bodyMask = new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP);
    private final LightingColorFilter emberFinish = new LightingColorFilter(0xffffba91, 0x00200b05);
    private final PremiumCarEffects premiumEffects = new PremiumCarEffects();
    private final float density;
    private final Typeface labelTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final RadialGradient lampGlow = new RadialGradient(0f, 0f, 1f,
            0x299abdc0, 0x009abdc0, Shader.TileMode.CLAMP);
    private final RadialGradient brakeGlow = new RadialGradient(0f, 0f, 1f,
            0x9bfa6854, 0x00fa6854, Shader.TileMode.CLAMP);
    private final RadialGradient underglow = new RadialGradient(0f, 0f, 1f,
            new int[]{0xbb61fff4, 0x7f3ddbe5, 0x77705ae8, 0x00705ae8},
            new float[]{0f, 0.40f, 0.69f, 1f}, Shader.TileMode.CLAMP);
    private final LinearGradient auroraFinish = new LinearGradient(-1.45f, -1.25f, 1.45f, 0f,
            new int[]{0x004ef4e0, 0xa049e8c9, 0x924888f5, 0x977a4cca, 0x004ef4e0},
            new float[]{0f, 0.28f, 0.50f, 0.74f, 1f}, Shader.TileMode.CLAMP);
    private final LinearGradient tailSweep = new LinearGradient(-0.60f, 0f, 0.60f, 0f,
            new int[]{0x00ff526c, 0xfff65376, 0xffffb3aa, 0xfff65376, 0x00ff526c},
            new float[]{0f, 0.27f, 0.50f, 0.73f, 1f}, Shader.TileMode.CLAMP);
    private final LinearGradient exhaustGlow = new LinearGradient(0f, -0.02f, 0f, 0.82f,
            new int[]{0xcda9f9ff, 0xa35b84ff, 0x00813ff0}, new float[]{0f, 0.33f, 1f},
            Shader.TileMode.CLAMP);

    private LinearGradient skyGradient;
    private LinearGradient roadGradient;
    private LinearGradient backdropShade;
    private RadialGradient horizonGlow;
    private float centerX;
    private float horizon;
    private float egoY;
    private float laneWidth;
    private int rangeMeters = 100;
    private float turnPreview;
    private float arrivalProgress = 1f;
    private Bitmap showroom;
    private boolean garagePreview;
    private boolean animatedCosmetics;
    private long animationStartedAt;
    private boolean matchHighlighted;
    private boolean streetDirty = true;

    public RoadView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        vehicleMesh = CarMeshRenderer.create(getResources().getAssets());
        otherCarSprite = loadSprite(R.drawable.car_cupfight, 2, otherCarSource);
        Bitmap replacement = loadSprite(optionalDrawable("car_cupfight_v2"), 2, ownCarSource);
        if (replacement == null) ownCarSource.set(otherCarSource);
        ownCarSprite = replacement == null ? otherCarSprite : replacement;
        wingSprite = loadSprite(R.drawable.neon_wing, 8, wingSource);
        backdrop = decodeBitmap(optionalDrawable("road_backdrop_v2"), 2);
        setContentDescription("3인칭 차량 화면");
    }

    private int optionalDrawable(String name) {
        return getResources().getIdentifier(name, "drawable", getContext().getPackageName());
    }

    private Bitmap decodeBitmap(int resourceId, int sampleSize) {
        if (resourceId == 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inSampleSize = sampleSize;
        return BitmapFactory.decodeResource(getResources(), resourceId, options);
    }

    private Bitmap loadSprite(int resourceId, int sampleSize, Rect source) {
        Bitmap bitmap = decodeBitmap(resourceId, sampleSize);
        if (bitmap == null || !bitmap.hasAlpha()) return null;

        // Fit the visible silhouette to the lane without changing the bitmap.
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int left = width;
        int right = 0;
        int top = height;
        int bottom = 0;
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x++) {
                if ((row[x] >>> 24) < 32) continue;
                left = Math.min(left, x);
                right = Math.max(right, x + 1);
                top = Math.min(top, y);
                bottom = y + 1;
            }
        }
        if (left >= right || top >= bottom) return null;
        source.set(Math.max(0, left - 1), Math.max(0, top - 1),
                Math.min(width, right + 1), Math.min(height, bottom + 1));
        return bitmap;
    }

    public void setRangeMeters(int meters) {
        int nextRange = Math.max(20, Math.min(250, meters));
        if (rangeMeters == nextRange) return;
        rangeMeters = nextRange;
        updateProjection();
        invalidate();
    }

    public void setCosmetics(Set<Cosmetic> equipped) {
        if (ownCosmetics.equals(equipped)) return;
        ownCosmetics.clear();
        ownCosmetics.addAll(equipped);
        animatedCosmetics = false;
        for (Cosmetic item : ownCosmetics) {
            if (item.animated && item.slot != Cosmetic.Slot.CUP) {
                animatedCosmetics = true;
                break;
            }
        }
        animationStartedAt = SystemClock.uptimeMillis();
        invalidate();
    }

    public void setGaragePreview(boolean active) {
        if (garagePreview == active) return;
        garagePreview = active;
        if (!active) turnPreview = Math.max(-45f, Math.min(45f, turnPreview));
        if (active && showroom == null) showroom = decodeBitmap(R.drawable.showroom_backdrop, 2);
        updateAccessibility();
        invalidate();
    }

    public void setTurnPreview(float degrees) {
        if (!Float.isFinite(degrees)) return;
        float limit = garagePreview ? 180f : 45f;
        float next = Math.max(-limit, Math.min(limit, degrees));
        if (turnPreview == next) return;
        turnPreview = next;
        invalidate();
    }

    /** Advances a visual approach to the existing parked road scene. */
    public void setArrivalProgress(float progress) {
        if (!Float.isFinite(progress)) return;
        float next = Math.max(0f, Math.min(1f, progress));
        if (arrivalProgress == next) return;
        arrivalProgress = next;
        invalidate();
    }

    public void setMatchHighlight(boolean active) {
        if (matchHighlighted == active) return;
        matchHighlighted = active;
        streetDirty = true;
        updateAccessibility();
        invalidate();
    }

    private void updateAccessibility() {
        setContentDescription(garagePreview ? "차량 꾸미기 미리보기"
                : matchHighlighted ? "내 차와 매칭 차량" : "내 차와 주변 차량");
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (isVisible) restartAnimation();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus) restartAnimation();
    }

    private void restartAnimation() {
        if (!canAnimate()) return;
        animationStartedAt = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (height == 0 || width == 0) return;
        centerX = width * 0.5f;
        horizon = height * 0.28f;
        skyGradient = new LinearGradient(0, 0, 0, height,
                new int[]{0xff0b1524, 0xff253f54, 0xff172633, 0xff0d1520},
                new float[]{0f, 0.28f, 0.48f, 1f}, Shader.TileMode.CLAMP);
        horizonGlow = new RadialGradient(centerX, horizon, Math.max(1f, width * 0.44f),
                0x58699cae, 0x00699cae, Shader.TileMode.CLAMP);
        roadGradient = new LinearGradient(0, horizon, 0, height,
                new int[]{0xff354a56, 0xff253847, 0xff1b2937, 0xff141e2b},
                new float[]{0f, 0.19f, 0.68f, 1f}, Shader.TileMode.CLAMP);
        backdropShade = new LinearGradient(0f, 0f, 0f, horizon + height * 0.16f,
                new int[]{0x50101a2a, 0x00101a2a, 0xde142331},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        updateProjection();
    }

    private void updateProjection() {
        float zoom = (float) Math.pow(100f / rangeMeters, 0.30f);
        laneWidth = Math.min(getWidth() * 0.154f, getHeight() * 0.25f) * zoom;
        egoY = getHeight() * (0.85f + 0.07f * (zoom - 1f));
        streetDirty = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (skyGradient == null || laneWidth == 0f) return;
        if (garagePreview) {
            drawShowroom(canvas);
            drawVehicle(canvas, 0f, 0f, EGO_COLOR, null, true, null);
        } else if (arrivalProgress < 1f) {
            float remaining = 1f - arrivalProgress;
            float arrivalOffset = ARRIVAL_METERS * remaining * remaining;
            drawStreet(canvas, arrivalOffset);
            drawVehicle(canvas, 0f, arrivalOffset * 0.22f, EGO_COLOR, "내 차", true, null);
        } else {
            if (streetDirty) {
                Canvas recording = street.beginRecording(getWidth(), getHeight());
                drawStreet(recording, 0f);
                street.endRecording();
                streetDirty = false;
            }
            canvas.drawPicture(street);
            drawVehicle(canvas, 0f, 0f, EGO_COLOR, "내 차", true, null);
        }
        if (canAnimate()) postInvalidateDelayed(COSMETIC_FRAME_MILLIS);
    }

    private boolean canAnimate() {
        return animatedCosmetics && isAttachedToWindow() && isShown()
                && getWindowVisibility() == VISIBLE && hasWindowFocus()
                && ValueAnimator.areAnimatorsEnabled();
    }

    private void drawShowroom(Canvas canvas) {
        canvas.drawColor(0xff0d1824);
        if (showroom == null) return;
        float scale = Math.max((float) getWidth() / showroom.getWidth(),
                (float) getHeight() / showroom.getHeight());
        float width = showroom.getWidth() * scale;
        float height = showroom.getHeight() * scale;
        float top = (getHeight() - height) * 0.5f;
        spriteDestination.set(centerX - width * 0.5f, top, centerX + width * 0.5f, top + height);
        canvas.drawBitmap(showroom, null, spriteDestination, spritePaint);
    }

    private void drawStreet(Canvas canvas, float arrivalOffset) {
        paint.setColor(Color.WHITE);
        paint.setShader(skyGradient);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        paint.setShader(horizonGlow);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        paint.setShader(null);
        if (backdrop == null) {
            drawBackdrop(canvas);
        } else {
            drawBackdropArtwork(canvas);
        }
        drawTerrain(canvas);
        drawRoad(canvas, arrivalOffset);

        float opponentDistance = OPPONENT_DISTANCE + arrivalOffset * 0.62f;
        float trafficOffset = arrivalOffset * 0.44f;
        boolean drawOpponent = inRange(OPPONENT_LANE, opponentDistance);
        for (int i = TRAFFIC.length - 1; i >= 0; i--) {
            float[] vehicle = TRAFFIC[i];
            float distance = vehicle[1] + trafficOffset;
            if (drawOpponent && opponentDistance >= distance) {
                drawOpponent(canvas, opponentDistance);
                drawOpponent = false;
            }
            if (inRange(vehicle[0], distance)) {
                int color = i % 3 == 0 ? 0xff748699 : i % 3 == 1 ? 0xff9aa4ab : 0xff526876;
                TrafficMesh mesh = trafficMesh(TRAFFIC_STYLES[i % TRAFFIC_STYLES.length], vehicle[0], distance);
                drawVehicle(canvas, vehicle[0], distance, color, null, false, mesh);
            }
        }
        if (drawOpponent) drawOpponent(canvas, opponentDistance);
    }

    private void drawBackdropArtwork(Canvas canvas) {
        float width = getWidth();
        float bottom = horizon + getHeight() * 0.16f;
        float scale = Math.max(width / backdrop.getWidth(), bottom / backdrop.getHeight());
        float photoWidth = backdrop.getWidth() * scale;
        float photoHeight = backdrop.getHeight() * scale;
        float top = Math.min(0f, horizon - photoHeight * 0.68f);
        spriteDestination.set(centerX - photoWidth * 0.5f, top,
                centerX + photoWidth * 0.5f, top + photoHeight);

        canvas.save();
        canvas.clipRect(0f, 0f, width, bottom);
        spritePaint.setAlpha(220);
        canvas.drawBitmap(backdrop, null, spriteDestination, spritePaint);
        spritePaint.setAlpha(255);
        paint.setColor(Color.WHITE);
        paint.setShader(backdropShade);
        canvas.drawRect(0f, 0f, width, bottom, paint);
        paint.setShader(null);
        canvas.restore();
    }

    private void drawBackdrop(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        paint.setColor(0xff253746);
        shape.reset();
        shape.moveTo(0f, horizon - height * 0.090f);
        shape.cubicTo(width * 0.19f, horizon - height * 0.12f, width * 0.26f,
                horizon - height * 0.01f, centerX, horizon);
        shape.cubicTo(width * 0.77f, horizon, width * 0.86f,
                horizon - height * 0.084f, width, horizon - height * 0.067f);
        shape.lineTo(width, horizon + height * 0.13f);
        shape.lineTo(0f, horizon + height * 0.13f);
        shape.close();
        canvas.drawPath(shape, paint);

        float unit = width / 29f;
        for (int i = 0; i < 29; i++) {
            if (i >= 12 && i <= 16) continue;
            float left = i * unit;
            float buildingWidth = unit * (0.40f + ((i * 11) % 7) * 0.085f);
            float top = horizon - height * (0.014f + ((i * 19) % 13) * 0.0042f);
            paint.setColor(i % 4 == 0 ? 0xff182632 : 0xff1d2d3a);
            canvas.drawRect(left, top, left + buildingWidth, horizon + 2f, paint);
            if (i % 3 == 0) {
                paint.setColor(0x558aa2a6);
                float window = Math.max(density, buildingWidth * 0.065f);
                canvas.drawRect(left + buildingWidth * 0.29f, top + height * 0.011f,
                        left + buildingWidth * 0.29f + window, top + height * 0.011f + window, paint);
            }
        }

    }

    private void drawTerrain(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        paint.setColor(0xff13212e);
        shape.reset();
        shape.moveTo(0f, horizon + height * 0.065f);
        shape.lineTo(centerX, horizon);
        shape.lineTo(width, horizon + height * 0.077f);
        shape.lineTo(width, height);
        shape.lineTo(0f, height);
        shape.close();
        canvas.drawPath(shape, paint);
    }

    private void drawRoad(Canvas canvas, float arrivalOffset) {
        float bottom = getHeight() + 2f;
        float projectedWidth = laneWidth * (bottom - horizon) / (egoY - horizon);
        roadSurface(canvas, bottom, projectedWidth * 3.08f, 0xff182b38);
        roadSurface(canvas, bottom, projectedWidth * 2.85f, 0xff293e4a);
        paint.setShader(roadGradient);
        roadSurface(canvas, bottom, projectedWidth * 2.5f, Color.WHITE);
        paint.setShader(null);

        paint.setColor(0x26101a24);
        for (int lane = -2; lane <= 2; lane++) {
            laneStrip(canvas, lane - 0.22f, 0.062f, horizon, 0f, bottom, projectedWidth);
            laneStrip(canvas, lane + 0.22f, 0.062f, horizon, 0f, bottom, projectedWidth);
        }
        drawRoadLights(canvas, arrivalOffset);

        for (int side = -1; side <= 1; side += 2) {
            paint.setColor(0xff485b69);
            laneStrip(canvas, side * 2.84f, 0.046f, horizon, 0f, bottom, projectedWidth);
            paint.setColor(0xff84979f);
            laneStrip(canvas, side * 2.82f, 0.014f, horizon, 0f, bottom, projectedWidth);
            paint.setColor(0xff8d9aa2);
            laneStrip(canvas, side * 2.48f, 0.018f, horizon, 0f, bottom, projectedWidth);
        }

        float dashPhase = arrivalOffset % DASH_INTERVAL;
        for (float z = -13f - DASH_INTERVAL + dashPhase; z <= 210f; z += DASH_INTERVAL) {
            float near = Math.max(-13f, z);
            float far = z + 5.4f;
            if (far <= near) continue;
            float nearScale = perspective(near);
            float farScale = perspective(far);
            float nearY = projectY(nearScale);
            float farY = projectY(farScale);
            float nearWidth = laneWidth * nearScale;
            float farWidth = laneWidth * farScale;
            paint.setColor(MARKING_COLOR);
            paint.setAlpha(Math.round(164f * nearScale / (nearScale + 0.40f)));
            for (int lane = -2; lane < 2; lane++) {
                laneStrip(canvas, lane + 0.5f, 0.016f, farY, farWidth, nearY, nearWidth);
            }
            float reflectorWidth = Math.max(density * 0.65f, nearWidth * 0.022f);
            paint.setColor(0x9c91b4bd);
            canvas.drawRect(centerX - 2.68f * nearWidth - reflectorWidth, nearY - reflectorWidth,
                    centerX - 2.68f * nearWidth, nearY, paint);
            canvas.drawRect(centerX + 2.68f * nearWidth, nearY - reflectorWidth,
                    centerX + 2.68f * nearWidth + reflectorWidth, nearY, paint);
        }
    }

    private void drawRoadLights(Canvas canvas, float arrivalOffset) {
        for (int index = LAMP_DISTANCES.length - 1; index >= 0; index--) {
            float scale = perspective(LAMP_DISTANCES[index] + arrivalOffset);
            float projectedLane = laneWidth * scale;
            float y = projectY(scale);
            int side = index % 2 == 0 ? -1 : 1;
            float x = centerX + side * 2.83f * projectedLane;
            float lampX = x - side * projectedLane * 0.27f;
            float lampY = y - projectedLane * 0.64f;

            canvas.save();
            canvas.translate(lampX - side * projectedLane * 0.17f, y);
            canvas.scale(projectedLane * 0.56f, projectedLane * 0.26f);
            paint.setColor(Color.WHITE);
            paint.setShader(lampGlow);
            canvas.drawCircle(0f, 0f, 1f, paint);
            paint.setShader(null);
            canvas.restore();

            paint.setColor(0xff5c7886);
            paint.setStrokeWidth(Math.max(density * 0.65f, projectedLane * 0.024f));
            canvas.drawLine(x, y, x, lampY, paint);
            shape.reset();
            shape.moveTo(x, lampY + projectedLane * 0.10f);
            shape.quadTo(x, lampY, lampX, lampY);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(shape, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xffbacfce);
            float tip = Math.max(density, projectedLane * 0.032f);
            canvas.drawRoundRect(lampX - tip, lampY - tip * 0.33f, lampX + tip,
                    lampY + tip * 0.33f, tip * 0.30f, tip * 0.30f, paint);
        }
    }

    private void roadSurface(Canvas canvas, float bottom, float halfWidth, int color) {
        paint.setColor(color);
        shape.reset();
        shape.moveTo(centerX, horizon);
        shape.lineTo(centerX + halfWidth, bottom);
        shape.lineTo(centerX - halfWidth, bottom);
        shape.close();
        canvas.drawPath(shape, paint);
    }

    private void laneStrip(Canvas canvas, float lane, float halfWidth, float farY,
                           float farWidth, float nearY, float nearWidth) {
        shape.reset();
        shape.moveTo(centerX + (lane - halfWidth) * farWidth, farY);
        shape.lineTo(centerX + (lane + halfWidth) * farWidth, farY);
        shape.lineTo(centerX + (lane + halfWidth) * nearWidth, nearY);
        shape.lineTo(centerX + (lane - halfWidth) * nearWidth, nearY);
        shape.close();
        canvas.drawPath(shape, paint);
    }

    private boolean inRange(float lane, float distance) {
        return distance >= -12f && Math.hypot(lane * LANE_METERS, distance) <= rangeMeters;
    }

    private void drawOpponent(Canvas canvas, float distance) {
        TrafficMesh mesh = trafficMesh(TrafficStyle.SILVER, OPPONENT_LANE, distance);
        if (matchHighlighted) drawMatchBrackets(canvas, mesh, distance);
        drawVehicle(canvas, OPPONENT_LANE, distance,
                matchHighlighted ? EGO_COLOR : OPPONENT_COLOR,
                matchHighlighted ? "매칭 차량" : "옆 차",
                false, mesh);
    }

    private TrafficMesh trafficMesh(TrafficStyle style, float lane, float distance) {
        float cameraHeight = (egoY - horizon) * LANE_METERS / laneWidth;
        return CarMeshRenderer.traffic(getResources().getAssets(), style, lane, distance, cameraHeight);
    }

    private void drawMatchBrackets(Canvas canvas, TrafficMesh mesh, float distance) {
        float scale = perspective(distance);
        float size = laneWidth / LANE_METERS * scale;
        float x = centerX + OPPONENT_LANE * laneWidth * scale;
        float y = projectY(scale);
        float bodyHeight = mesh == null ? carHeight(otherCarSprite, otherCarSource, 1.10f) : mesh.bodyHeight();
        float left = x - size * 1.40f;
        float right = x + size * 1.40f;
        float top = y - size * bodyHeight - density * 2f;
        float bottom = y + size * 0.28f;
        float corner = size * 0.43f;

        paint.setColor(0x188ff0e9);
        canvas.drawOval(left, y - size * 0.27f, right, bottom, paint);
        paint.setColor(EGO_COLOR);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(density * 1.1f, size * 0.045f));
        shape.reset();
        shape.moveTo(left, top + corner);
        shape.lineTo(left, top);
        shape.lineTo(left + corner, top);
        shape.moveTo(right - corner, top);
        shape.lineTo(right, top);
        shape.lineTo(right, top + corner);
        shape.moveTo(right, bottom - corner);
        shape.lineTo(right, bottom);
        shape.lineTo(right - corner, bottom);
        shape.moveTo(left + corner, bottom);
        shape.lineTo(left, bottom);
        shape.lineTo(left, bottom - corner);
        canvas.drawPath(shape, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawVehicle(Canvas canvas, float lane, float distance, int color,
                             String label, boolean ownVehicle, TrafficMesh trafficMesh) {
        boolean featured = label != null || garagePreview;
        float scale = perspective(distance);
        float x = centerX + lane * laneWidth * scale;
        float y = garagePreview ? getHeight() * 0.67f : projectY(scale);
        float size = garagePreview ? Math.min(getWidth() * 0.235f, getHeight() * 0.26f)
                : (trafficMesh != null || ownVehicle && vehicleMesh != null)
                ? laneWidth / LANE_METERS * scale
                : laneWidth * (featured ? 0.32f : 0.26f) * scale;
        float seconds = ownVehicle && canAnimate()
                ? ((SystemClock.uptimeMillis() - animationStartedAt) % 24_000L) / 1000f : 0f;
        Bitmap body = ownVehicle ? ownCarSprite : otherCarSprite;
        Rect bodySource = ownVehicle ? ownCarSource : otherCarSource;
        float halfWidth = featured ? 1.10f : 1.01f;
        boolean useMesh = ownVehicle && vehicleMesh != null;
        float bodyHeight = useMesh ? garagePreview ? vehicleMesh.prepare(turnPreview)
                : -vehicleMesh.prepareRoad(new RoadVehicleProjection(lane, distance,
                        (egoY - horizon) * LANE_METERS / laneWidth), turnPreview)
                : trafficMesh != null ? trafficMesh.bodyHeight() : carHeight(body, bodySource, halfWidth);
        if (garagePreview) size = Math.min(size, getHeight() * 0.59f / bodyHeight);
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(size, size);

        if (ownVehicle) {
            if (useMesh) {
                drawProjectedGroundEffects(canvas, seconds);
                premiumEffects.drawGround(canvas, vehicleMesh, ownCosmetics, seconds);
            } else {
                drawGroundEffects(canvas, seconds);
            }
        }
        if (useMesh && !garagePreview) {
            drawProjectedGroundQuad(canvas, -1.08f, -1.95f, 1.08f, 1.95f, 0x19070b10);
            drawProjectedGroundQuad(canvas, -0.90f, -1.68f, 0.90f, 1.68f, 0x42070c12);
            for (int side : new int[]{-1, 1}) {
                for (float axle : new float[]{-1.36f, 1.34f}) {
                    float wheel = side * (axle < 0 ? 0.867f : 0.895f);
                    drawProjectedGroundQuad(canvas, wheel - 0.13f, axle - 0.20f,
                            wheel + 0.13f, axle + 0.20f, 0xA006090D);
                }
            }
        } else if (trafficMesh != null) {
            drawGroundQuad(canvas, trafficMesh.softShadow(), 0x19070b10);
            drawGroundQuad(canvas, trafficMesh.bodyShadow(), 0x42070c12);
            for (float[] contact : trafficMesh.wheelContacts()) {
                drawGroundQuad(canvas, contact, 0xA006090D);
            }
        } else {
            paint.setColor(0x4207090e);
            canvas.drawOval(-1.22f, -0.25f, 1.22f, 0.23f, paint);
            paint.setColor(0x83080d14);
            canvas.drawOval(-1.01f, -0.18f, 1.01f, 0.11f, paint);
            if (featured) {
                paint.setColor(withAlpha(color, 45));
                canvas.drawOval(-1.12f, -0.14f, 1.12f, 0.15f, paint);
            }
        }
        if (label != null && arrivalProgress > 0.40f && arrivalProgress < 1f) {
            drawBrakeReflection(canvas);
        }

        if (useMesh) {
            vehicleMesh.draw(canvas, ownCosmetics, seconds);
        } else if (trafficMesh != null) {
            if (!featured) spritePaint.setAlpha(trafficAlpha(scale));
            trafficMesh.draw(canvas, spritePaint);
            spritePaint.setAlpha(255);
        } else if (body != null) {
            spriteDestination.set(-halfWidth, 0.06f - bodyHeight, halfWidth, 0.06f);
            if (ownVehicle) {
                drawOwnBody(canvas, body, bodySource, seconds);
            } else {
                if (!featured) spritePaint.setAlpha(trafficAlpha(scale));
                canvas.drawBitmap(body, bodySource, spriteDestination, spritePaint);
            }
            spritePaint.setAlpha(255);
        } else {
            drawVectorVehicle(canvas, color);
        }

        if (ownVehicle) {
            if (useMesh) {
                drawProjectedRearEffects(canvas, seconds);
                premiumEffects.drawBody(canvas, vehicleMesh, ownCosmetics, seconds, turnPreview);
            } else {
                drawBodyEffects(canvas, bodyHeight, seconds);
            }
        }
        canvas.restore();
        if (label != null) drawVehicleLabel(canvas, x, y - size * bodyHeight - dp(5f), color, label);
    }

    private static int trafficAlpha(float scale) {
        return Math.round(255f * (0.90f + 0.10f * scale));
    }

    private void drawGroundQuad(Canvas canvas, float[] corners, int color) {
        shape.reset();
        shape.moveTo(corners[0], corners[1]);
        for (int i = 2; i < corners.length; i += 2) {
            shape.lineTo(corners[i], corners[i + 1]);
        }
        shape.close();
        paint.setColor(color);
        canvas.drawPath(shape, paint);
    }

    private void drawProjectedGroundQuad(Canvas canvas, float left, float front,
                                         float right, float rear, int color) {
        vehicleMesh.project(left, 0, front, effectCorners, 0);
        vehicleMesh.project(right, 0, front, effectCorners, 2);
        vehicleMesh.project(right, 0, rear, effectCorners, 4);
        vehicleMesh.project(left, 0, rear, effectCorners, 6);
        drawGroundQuad(canvas, effectCorners, color);
    }

    private void drawBrakeReflection(Canvas canvas) {
        float phase = (arrivalProgress - 0.40f) / 0.60f;
        int alpha = Math.round(220f * (float) Math.sin(Math.PI * phase));
        canvas.save();
        canvas.translate(0f, 0.14f);
        canvas.scale(1.04f, 0.26f);
        paint.setColor(withAlpha(Color.WHITE, alpha));
        paint.setShader(brakeGlow);
        canvas.drawCircle(0f, 0f, 1f, paint);
        paint.setShader(null);
        canvas.restore();
    }

    private void drawOwnBody(Canvas canvas, Bitmap body, Rect source, float seconds) {
        if (ownCosmetics.contains(Cosmetic.AURORA_PAINT)) {
            // The isolated layer keeps the travelling finish inside the car's alpha silhouette.
            int layer = canvas.saveLayer(spriteDestination, null);
            canvas.drawBitmap(body, source, spriteDestination, spritePaint);
            effectTransform.setTranslate(0.62f * pulse(seconds, 6f), 0f);
            auroraFinish.setLocalMatrix(effectTransform);
            paint.setColor(Color.WHITE);
            paint.setShader(auroraFinish);
            paint.setXfermode(bodyMask);
            canvas.drawRect(spriteDestination, paint);
            paint.setXfermode(null);
            paint.setShader(null);
            canvas.restoreToCount(layer);
        } else {
            if (ownCosmetics.contains(Cosmetic.EMBER_PAINT)) spritePaint.setColorFilter(emberFinish);
            canvas.drawBitmap(body, source, spriteDestination, spritePaint);
            spritePaint.setColorFilter(null);
        }
    }

    private void drawGroundEffects(Canvas canvas, float seconds) {
        if (ownCosmetics.contains(Cosmetic.PIXEL_TRAIL)) drawPixelTrail(canvas, seconds);
        drawFloorLighting(canvas, seconds);
        if (ownCosmetics.contains(Cosmetic.ION_EXHAUST)) drawIonExhaust(canvas, seconds);
    }

    private void drawFloorLighting(Canvas canvas, float seconds) {
        if (ownCosmetics.contains(Cosmetic.HOLO_RING)) drawHoloRing(canvas, seconds);
        if (ownCosmetics.contains(Cosmetic.AURORA_UNDERGLOW)) {
            canvas.save();
            canvas.translate(0f, 0.07f);
            canvas.scale(1.62f, 0.49f);
            paint.setColor(withAlpha(Color.WHITE, Math.round(220 + 35 * pulse(seconds, 4f))));
            paint.setShader(underglow);
            canvas.drawCircle(0f, 0f, 1f, paint);
            paint.setShader(null);
            canvas.restore();
        }
    }

    private void drawProjectedGroundEffects(Canvas canvas, float seconds) {
        if ((ownCosmetics.contains(Cosmetic.HOLO_RING) || ownCosmetics.contains(Cosmetic.AURORA_UNDERGLOW))
                && enterEffectPlane(canvas, 0.01f, -0.09f, 0f, 3.0f)) {
            drawFloorLighting(canvas, seconds);
            canvas.restore();
        }
        if (ownCosmetics.contains(Cosmetic.PIXEL_TRAIL)
                && enterEffectPlane(canvas, 0.016f, 2.17f, 0f, 1.15f)) {
            drawPixelTrail(canvas, seconds);
            canvas.restore();
        }
        if (ownCosmetics.contains(Cosmetic.ION_EXHAUST)
                && enterEffectPlane(canvas, 0.222f, 2.23f, 0.16f, 1.08f)) {
            drawIonExhaust(canvas, seconds);
            canvas.restore();
        }
    }

    private void drawProjectedRearEffects(Canvas canvas, float seconds) {
        if (Math.abs(turnPreview) >= 79f) return;
        boolean cyberWing = ownCosmetics.contains(Cosmetic.CYBER_WING);
        if ((cyberWing || ownCosmetics.contains(Cosmetic.NEON_WING))
                && enterEffectPlane(canvas, 0.79f, 2.07f, 0.67f, 0f)) {
            if (cyberWing) drawCyberWing(canvas, 0f, seconds);
            else drawWing(canvas, 0f);
            canvas.restore();
        }
        if (ownCosmetics.contains(Cosmetic.CARBON_DIFFUSER)
                && enterEffectPlane(canvas, 0.267f, 2.293f, 0.40f, 0f)) {
            drawDiffuser(canvas);
            canvas.restore();
        }
    }

    private boolean enterEffectPlane(Canvas canvas, float baseY, float baseZ,
                                     float verticalScale, float lengthScale) {
        float lowerY = baseY - verticalScale;
        float lowerZ = baseZ + lengthScale;
        vehicleMesh.project(-0.96f, baseY, baseZ, effectCorners, 0);
        vehicleMesh.project(0.96f, baseY, baseZ, effectCorners, 2);
        vehicleMesh.project(0.96f, lowerY, lowerZ, effectCorners, 4);
        vehicleMesh.project(-0.96f, lowerY, lowerZ, effectCorners, 6);
        if (!projectedEffect.setPolyToPoly(EFFECT_PLANE, 0, effectCorners, 0, 4)) return false;
        canvas.save();
        canvas.concat(projectedEffect);
        return true;
    }

    private void drawBodyEffects(Canvas canvas, float bodyHeight, float seconds) {
        float deck = -bodyHeight * 0.42f;
        if (ownCosmetics.contains(Cosmetic.CYBER_WING)) {
            drawCyberWing(canvas, deck, seconds);
        } else if (ownCosmetics.contains(Cosmetic.NEON_WING)) {
            drawWing(canvas, deck);
        }
        if (ownCosmetics.contains(Cosmetic.TURBINE_WHEELS)) drawTurbines(canvas, seconds);
        if (ownCosmetics.contains(Cosmetic.CARBON_DIFFUSER)) drawDiffuser(canvas);
        if (ownCosmetics.contains(Cosmetic.PULSE_TAIL)) drawTailSweep(canvas, bodyHeight, seconds);
    }

    private static float carHeight(Bitmap body, Rect source, float halfWidth) {
        return body == null ? 1.45f : 2f * halfWidth * source.height() / source.width();
    }

    private void drawVectorVehicle(Canvas canvas, int color) {
        paint.setColor(0xff101723);
        canvas.drawRoundRect(-1.02f, -0.56f, -0.67f, 0.03f, 0.14f, 0.14f, paint);
        canvas.drawRoundRect(0.67f, -0.56f, 1.02f, 0.03f, 0.14f, 0.14f, paint);

        paint.setColor(shade(color, 0.63f));
        shape.reset();
        shape.moveTo(-1f, -0.23f);
        shape.lineTo(-0.94f, -0.76f);
        shape.lineTo(-0.54f, -1.35f);
        shape.quadTo(0, -1.5f, 0.54f, -1.35f);
        shape.lineTo(0.94f, -0.76f);
        shape.lineTo(1f, -0.23f);
        shape.quadTo(0.97f, -0.08f, 0.8f, -0.07f);
        shape.lineTo(-0.8f, -0.07f);
        shape.quadTo(-0.97f, -0.08f, -1f, -0.23f);
        shape.close();
        canvas.drawPath(shape, paint);

        paint.setColor(color);
        shape.reset();
        shape.moveTo(-0.91f, -0.71f);
        shape.lineTo(-0.51f, -1.31f);
        shape.quadTo(0, -1.42f, 0.51f, -1.31f);
        shape.lineTo(0.91f, -0.71f);
        shape.quadTo(0, -0.54f, -0.91f, -0.71f);
        shape.close();
        canvas.drawPath(shape, paint);

        paint.setColor(0xff20334a);
        shape.reset();
        shape.moveTo(-0.49f, -1.20f);
        shape.quadTo(0, -1.27f, 0.49f, -1.20f);
        shape.lineTo(0.68f, -0.91f);
        shape.quadTo(0, -0.79f, -0.68f, -0.91f);
        shape.close();
        canvas.drawPath(shape, paint);

        paint.setColor(0xff263241);
        canvas.drawRoundRect(-0.74f, -0.47f, 0.74f, -0.19f, 0.08f, 0.08f, paint);
        paint.setColor(0xfffb7184);
        canvas.drawRoundRect(-0.89f, -0.51f, -0.42f, -0.37f, 0.06f, 0.06f, paint);
        canvas.drawRoundRect(0.42f, -0.51f, 0.89f, -0.37f, 0.06f, 0.06f, paint);
        paint.setColor(0xffacbbca);
        canvas.drawRoundRect(-0.22f, -0.36f, 0.22f, -0.24f, 0.035f, 0.035f, paint);
    }

    private void drawPixelTrail(Canvas canvas, float seconds) {
        for (int i = 0; i < 8; i++) {
            float age = (cycle(seconds, 2f) + i / 8f) % 1f;
            float y = 0.06f + 1.04f * age;
            float halfLength = 0.032f + 0.095f * age;
            float halfWidth = 0.075f + 0.057f * age;
            paint.setColor(withAlpha(i % 2 == 0 ? 0xff7aabff : 0xffbdb3fb,
                    Math.round(176f * (1f - age))));
            for (int side = -1; side <= 1; side += 2) {
                float x = side * (0.70f + 0.10f * age);
                canvas.drawRoundRect(x - halfWidth, y - halfLength, x + halfWidth,
                        y + halfLength, 0.025f, 0.025f, paint);
            }
        }
    }

    private void drawHoloRing(Canvas canvas, float seconds) {
        effectBounds.set(-1.56f, -0.47f, 1.56f, 0.64f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.032f);
        paint.setColor(0x736993ff);
        canvas.drawOval(effectBounds, paint);
        float angle = 360f * cycle(seconds, 4f);
        paint.setStrokeWidth(0.046f);
        paint.setColor(0xf0a2f7f7);
        canvas.drawArc(effectBounds, angle, 82f, false, paint);
        paint.setColor(0xc58191fa);
        canvas.drawArc(effectBounds, angle + 180f, 65f, false, paint);
        effectBounds.inset(0.17f, 0.07f);
        paint.setStrokeWidth(0.018f);
        paint.setColor(0x5368dce8);
        canvas.drawOval(effectBounds, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawIonExhaust(Canvas canvas, float seconds) {
        float plume = 0.75f + 0.18f * pulse(seconds, 2f);
        for (int side = -1; side <= 1; side += 2) {
            float x = side * 0.66f;
            shape.reset();
            shape.moveTo(x - 0.095f, -0.04f);
            shape.cubicTo(x - 0.18f, plume * 0.34f, x - 0.13f,
                    plume * 0.73f, x + side * 0.13f, plume);
            shape.cubicTo(x + 0.20f, plume * 0.60f, x + 0.17f, plume * 0.19f,
                    x + 0.095f, -0.04f);
            shape.close();
            paint.setColor(Color.WHITE);
            paint.setShader(exhaustGlow);
            canvas.drawPath(shape, paint);
            paint.setShader(null);
            for (int i = 0; i < 5; i++) {
                float age = (cycle(seconds, 2f) + i / 5f) % 1f;
                float sparkX = x + side * age * 0.23f + (i % 2 == 0 ? -0.10f : 0.08f) * age;
                paint.setColor(withAlpha(0xffc7dcff, Math.round(185f * (1f - age))));
                canvas.drawCircle(sparkX, 0.09f + 0.80f * age,
                        0.017f + 0.016f * (1f - age), paint);
            }
        }
    }

    private void drawCyberWing(Canvas canvas, float deck, float seconds) {
        paint.setColor(0xff284152);
        canvas.drawRoundRect(-0.67f, deck - 0.32f, -0.54f, deck + 0.02f, 0.035f, 0.035f, paint);
        canvas.drawRoundRect(0.54f, deck - 0.32f, 0.67f, deck + 0.02f, 0.035f, 0.035f, paint);
        paint.setColor(0xff1e3145);
        shape.reset();
        shape.moveTo(-1.22f, deck - 0.52f);
        shape.lineTo(-0.79f, deck - 0.43f);
        shape.lineTo(0.79f, deck - 0.43f);
        shape.lineTo(1.22f, deck - 0.52f);
        shape.lineTo(1.11f, deck - 0.29f);
        shape.lineTo(-1.11f, deck - 0.29f);
        shape.close();
        canvas.drawPath(shape, paint);
        for (int i = 0; i < 9; i++) {
            float x = -1.10f + i * 0.245f;
            float glow = 0.50f + 0.50f * pulse(seconds + (i / 3) * 0.62f, 3f);
            paint.setColor(withAlpha(0xff83eafe, Math.round(96f + 159f * glow)));
            canvas.drawRoundRect(x, deck - 0.386f, x + 0.21f,
                    deck - 0.340f, 0.014f, 0.014f, paint);
        }
    }

    private void drawTurbines(Canvas canvas, float seconds) {
        paint.setStyle(Paint.Style.STROKE);
        for (int side = -1; side <= 1; side += 2) {
            float x = side * 1.01f;
            effectBounds.set(x - 0.145f, -0.35f, x + 0.145f, 0.07f);
            paint.setColor(0x7458cbe6);
            paint.setStrokeWidth(0.027f);
            canvas.drawOval(effectBounds, paint);
            paint.setStrokeWidth(0.049f);
            paint.setColor(0xf6baf6fd);
            float angle = side * 360f * cycle(seconds, 2f);
            canvas.drawArc(effectBounds, angle, 77f, false, paint);
            canvas.drawArc(effectBounds, angle + 180f, 51f, false, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawDiffuser(Canvas canvas) {
        paint.setColor(0xff17202a);
        canvas.drawRoundRect(-0.88f, -0.23f, 0.88f, 0.035f, 0.085f, 0.085f, paint);
        paint.setColor(0xff7c8c97);
        paint.setStrokeWidth(0.028f);
        canvas.drawLine(-0.78f, -0.20f, 0.78f, -0.20f, paint);
        for (int fin = -2; fin <= 2; fin++) {
            float x = fin * 0.29f;
            paint.setColor(0xff374652);
            shape.reset();
            shape.moveTo(x - 0.052f, -0.18f);
            shape.lineTo(x + 0.048f, -0.17f);
            shape.lineTo(x + 0.024f, 0.09f);
            shape.lineTo(x - 0.062f, 0.09f);
            shape.close();
            canvas.drawPath(shape, paint);
        }
    }

    private void drawTailSweep(Canvas canvas, float bodyHeight, float seconds) {
        float tail = -bodyHeight * 0.355f;
        shape.reset();
        shape.moveTo(-0.94f, tail - 0.065f);
        shape.cubicTo(-0.77f, tail + 0.045f, 0.77f, tail + 0.045f, 0.94f, tail - 0.065f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(0.059f);
        paint.setColor(0xaaff5778);
        canvas.drawPath(shape, paint);
        effectTransform.setTranslate(1.28f * pulse(seconds, 3f), 0f);
        tailSweep.setLocalMatrix(effectTransform);
        paint.setColor(Color.WHITE);
        paint.setShader(tailSweep);
        paint.setStrokeWidth(0.046f);
        canvas.drawPath(shape, paint);
        paint.setShader(null);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private static float cycle(float seconds, float period) {
        return seconds % period / period;
    }

    private static float pulse(float seconds, float period) {
        return (float) Math.sin(2d * Math.PI * cycle(seconds, period));
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }

    private void drawWing(Canvas canvas, float deck) {
        if (wingSprite != null) {
            float halfWidth = 1.20f;
            float height = 2f * halfWidth * wingSource.height() / wingSource.width();
            spriteDestination.set(-halfWidth, deck - height, halfWidth, deck);
            canvas.drawBitmap(wingSprite, wingSource, spriteDestination, spritePaint);
            return;
        }
        paint.setColor(0xff506877);
        canvas.drawRoundRect(-0.64f, deck - 0.33f, -0.52f, deck + 0.02f, 0.04f, 0.04f, paint);
        canvas.drawRoundRect(0.52f, deck - 0.33f, 0.64f, deck + 0.02f, 0.04f, 0.04f, paint);
        paint.setColor(0xff2d414e);
        shape.reset();
        shape.moveTo(-1.20f, deck - 0.46f);
        shape.quadTo(0, deck - 0.33f, 1.20f, deck - 0.46f);
        shape.lineTo(1.06f, deck - 0.24f);
        shape.quadTo(0, deck - 0.14f, -1.06f, deck - 0.24f);
        shape.close();
        canvas.drawPath(shape, paint);
        paint.setColor(0xffa0c8cf);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.035f);
        shape.reset();
        shape.moveTo(-1.16f, deck - 0.44f);
        shape.quadTo(0, deck - 0.32f, 1.16f, deck - 0.44f);
        canvas.drawPath(shape, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawVehicleLabel(Canvas canvas, float x, float bottom, int color, String label) {
        paint.setTypeface(labelTypeface);
        paint.setTextSize(12f * density);
        paint.setTextAlign(Paint.Align.CENTER);
        float halfWidth = paint.measureText(label) * 0.5f + 15f * density;
        float top = bottom - 24f * density;
        float radius = 7f * density;
        paint.setColor(0xed101824);
        canvas.drawRoundRect(x - halfWidth, top, x + halfWidth, bottom, radius, radius, paint);
        paint.setColor(0x88536975);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density * 0.75f);
        canvas.drawRoundRect(x - halfWidth, top, x + halfWidth, bottom, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawCircle(x - halfWidth + 9f * density, bottom - 12f * density, 2.4f * density, paint);
        paint.setColor(0xffeef6f8);
        canvas.drawText(label, x + 5f * density, bottom - 7.5f * density, paint);
    }

    private float projectY(float scale) {
        return horizon + (egoY - horizon) * scale;
    }

    private static float perspective(float distance) {
        return CAMERA_DEPTH_METERS / (CAMERA_DEPTH_METERS + distance);
    }

    private static int shade(int color, float factor) {
        return Color.rgb(Math.round(Color.red(color) * factor), Math.round(Color.green(color) * factor),
                Math.round(Color.blue(color) * factor));
    }

    private float dp(float value) {
        return value * density;
    }

}
