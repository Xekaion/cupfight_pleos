package io.autohmg.cupfight;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Build;

import java.util.Arrays;

/** Projects a glass shell, its fluid volume, and ballistic droplets into the same camera. */
final class WaterMeshRenderer {
    private static final int SIDES = 48;
    private static final int RINGS = 7;
    private static final int SURFACE_VERTICES = 1 + SIDES * RINGS;
    private static final int SURFACE_TRIANGLES = SIDES * (2 * RINGS - 1);
    private static final float GLASS_HEIGHT = CupFluidSimulation.HEIGHT_TO_RADIUS;
    private static final float CAMERA_Y = 1.20f;
    private static final float CAMERA_DISTANCE = 7.4f;
    private static final float PITCH_SIN = 0.423f;
    private static final float PITCH_COS = 0.906f;
    private static final float YAW_SIN = -0.156f;
    private static final float YAW_COS = 0.988f;
    private static final float EYE_X = -YAW_SIN * PITCH_COS * CAMERA_DISTANCE;
    private static final float EYE_Y = CAMERA_Y + PITCH_SIN * CAMERA_DISTANCE;
    private static final float EYE_Z = YAW_COS * PITCH_COS * CAMERA_DISTANCE;
    private static final float AIR_TO_WATER = 0.7502f;
    private static final float WATER_REFLECTANCE = 0.0204f;
    private static final float TAU = (float) (2.0 * Math.PI);
    private static final int MAX_STREAM_POINTS = 32;
    private static final float[] GLASS_LEVELS = {-0.070f, -0.052f, -0.012f, 0.045f, 0.155f, 0.74f, 0.97f, 1f};
    private static final float[] GLASS_RADII = {0.735f, 0.776f, 0.811f, 0.823f, 0.852f, 0.976f, 1.019f, 1.027f};
    private static final int[] EDGE_REFLECTIONS = {2, 26};
    private static final RadialGradient SHADOW = new RadialGradient(0f, 0f, 1f,
            new int[]{0xb1010b19, 0x62040d1a, 0x00040d1a}, new float[]{0f, 0.42f, 1f},
            Shader.TileMode.CLAMP);
    private static final RadialGradient FLOOR_LIGHT = new RadialGradient(0f, 0f, 1f,
            new int[]{0x006997a2, 0x4168979f, 0x0077a6b3}, new float[]{0f, 0.58f, 1f},
            Shader.TileMode.CLAMP);
    private static final Palette CLEAR = new Palette(0x86a4af, 0xf1fdff, 0x668992, 0xa4c3c8, 0x15272e);
    private static final Palette CRYSTAL = new Palette(0xa394b6, 0xf3e9ff, 0x887da2, 0xcbbfdd, 0x282338);
    private static final Palette PRISM = new Palette(0x81a2b4, 0xe8faff, 0x6d91a8, 0xb1c9d9, 0x212940);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Path edge = new Path();
    private final Batch backGlass = new Batch(SIDES * GLASS_LEVELS.length * 6);
    private final Batch frontGlass = new Batch(SIDES * GLASS_LEVELS.length * 6);
    private final Batch rim = new Batch(SIDES * 6);
    private final Batch farWater = new Batch(SIDES * 6);
    private final Batch nearWater = new Batch(SIDES * 6);
    private final Batch surface = new Batch(SURFACE_TRIANGLES * 3);
    private final Batch caustic = new Batch(SIDES * 12);
    private final Batch farSpill = new Batch(MAX_STREAM_POINTS * 36);
    private final Batch nearSpill = new Batch(MAX_STREAM_POINTS * 36);
    private final float[] unitX = new float[SIDES];
    private final float[] unitZ = new float[SIDES];
    private final float[] facing = new float[SIDES];
    private final float[] cupX = new float[GLASS_LEVELS.length * SIDES];
    private final float[] cupY = new float[cupX.length];
    private final float[] lipX = new float[SIDES];
    private final float[] lipY = new float[SIDES];
    private final float[] liquidX = new float[SURFACE_VERTICES];
    private final float[] liquidZ = new float[SURFACE_VERTICES];
    private final float[] liquidY = new float[SURFACE_VERTICES];
    private final float[] screenX = new float[SURFACE_VERTICES];
    private final float[] screenY = new float[SURFACE_VERTICES];
    private final float[] depth = new float[SURFACE_VERTICES];
    private final int[] surfaceColors = new int[SURFACE_VERTICES];
    private final int[] cornerA = new int[SURFACE_TRIANGLES];
    private final int[] cornerB = new int[SURFACE_TRIANGLES];
    private final int[] cornerC = new int[SURFACE_TRIANGLES];
    private final long[] sortKeys = new long[SURFACE_TRIANGLES];
    private final float[] floorX = new float[SIDES];
    private final float[] floorY = new float[SIDES];
    private final float[] causticX = new float[SIDES * 3];
    private final float[] causticY = new float[SIDES * 3];
    private final float[] streamX = new float[MAX_STREAM_POINTS];
    private final float[] streamY = new float[MAX_STREAM_POINTS];
    private final float[] streamDepth = new float[MAX_STREAM_POINTS];
    private final float[] streamHalfWidth = new float[MAX_STREAM_POINTS];
    private final float[] streamOpacity = new float[MAX_STREAM_POINTS];
    private final float[] streamSideX = new float[MAX_STREAM_POINTS];
    private final float[] streamSideY = new float[MAX_STREAM_POINTS];
    private final float[] projected = new float[3];

    private Cosmetic glassStyle;
    private boolean glassPrepared;
    private Palette waterPalette;
    private float previousLevel = -1f;
    private boolean waterWasMoving;

    WaterMeshRenderer() {
        float floorRadius = CupFluidSimulation.radiusAtHeight(0f);
        for (int i = 0; i < SIDES; i++) {
            float theta = TAU * i / SIDES;
            unitX[i] = (float) Math.cos(theta);
            unitZ[i] = (float) Math.sin(theta);
            facing[i] = unitZ[i] * YAW_COS - unitX[i] * YAW_SIN;
            project(floorRadius * unitX[i], 0f, floorRadius * unitZ[i]);
            floorX[i] = projected[0];
            floorY[i] = projected[1];
        }
        int triangle = 0;
        for (int ring = 1; ring <= RINGS; ring++) {
            for (int i = 0; i < SIDES; i++) {
                int next = (i + 1) % SIDES;
                cornerA[triangle] = index(ring - 1, i);
                cornerB[triangle] = index(ring, i);
                cornerC[triangle++] = index(ring, next);
                if (ring > 1) {
                    cornerA[triangle] = index(ring - 1, i);
                    cornerB[triangle] = index(ring, next);
                    cornerC[triangle++] = index(ring - 1, next);
                }
            }
        }
    }

    void draw(Canvas canvas, float left, float top, float width, float height, CupFluidSimulation fluid,
              Cosmetic style, float styleSeconds) {
        if (width <= 0f || height <= 0f) return;
        Palette palette = style == Cosmetic.CRYSTAL_CUP ? CRYSTAL : style == Cosmetic.PRISM_CUP ? PRISM : CLEAR;
        float seconds = Float.isFinite(styleSeconds) ? styleSeconds : 0f;
        boolean crystal = style == Cosmetic.CRYSTAL_CUP;
        boolean prism = style == Cosmetic.PRISM_CUP;
        if (!glassPrepared || glassStyle != style) {
            prepareGlass(crystal);
            prepareShell(palette, crystal);
            updateRim(palette, prism, seconds);
            glassStyle = style;
            glassPrepared = true;
        } else if (prism) {
            updateRim(palette, true, seconds);
        }
        float level = fluid.getLevel();
        boolean moving = fluid.isMoving();
        if (level > 0.001f && (moving || waterWasMoving || previousLevel != level || waterPalette != palette)) {
            updateFluid(fluid, palette);
            waterPalette = palette;
        }
        previousLevel = level;
        waterWasMoving = moving;
        updateSpill(fluid, palette);
        canvas.save();
        canvas.translate(left + width * 0.5f, top + height * 0.49f);
        float scale = Math.min(width / 2.42f, height / 3.28f);
        canvas.scale(scale, scale);
        drawShadow(canvas);
        if (level > 0.001f) drawFloorCaustic(canvas, level);
        farSpill.draw(canvas, paint);
        drawDroplets(canvas, fluid, palette, false);
        backGlass.draw(canvas, paint);
        if (level > 0.001f) {
            farWater.draw(canvas, paint);
            nearWater.draw(canvas, paint);
            caustic.draw(canvas, paint);
            surface.draw(canvas, paint);
            drawMeniscus(canvas, palette);
        }
        frontGlass.draw(canvas, paint);
        rim.draw(canvas, paint);
        drawGlassEdges(canvas, palette, crystal);
        nearSpill.draw(canvas, paint);
        drawDroplets(canvas, fluid, palette, true);
        canvas.restore();
    }

    private void prepareGlass(boolean faceted) {
        for (int i = 0; i < SIDES; i++) {
            // A circumscribed polygon leaves the circular inner cavity clear at each facet.
            float facet = faceted ? 1f / (float) Math.cos((i % 4 - 2) * TAU / SIDES) : 1f;
            for (int level = 0; level < GLASS_LEVELS.length; level++) {
                float radius = GLASS_RADII[level] * facet;
                int vertex = level * SIDES + i;
                project(radius * unitX[i], GLASS_LEVELS[level], radius * unitZ[i]);
                cupX[vertex] = projected[0];
                cupY[vertex] = projected[1];
            }
            project(unitX[i], 0.989f, unitZ[i]);
            lipX[i] = projected[0];
            lipY[i] = projected[1];
        }
    }

    private void prepareShell(Palette palette, boolean crystal) {
        backGlass.clear();
        frontGlass.clear();
        for (int i = 0; i < SIDES; i++) {
            int next = (i + 1) % SIDES;
            boolean front = facing[i] + facing[next] >= 0f;
            Batch shell = front ? frontGlass : backGlass;
            float facet = crystal ? 0.75f + 0.25f * ((i / 4) % 3) : 1f;
            for (int level = 0; level < GLASS_LEVELS.length - 1; level++) {
                int a = level * SIDES + i;
                int b = level * SIDES + next;
                int c = b + SIDES;
                int d = a + SIDES;
                int ca = glassColor(palette, i, level, front, facet);
                int cb = glassColor(palette, next, level, front, facet);
                int cc = glassColor(palette, next, level + 1, front, facet);
                int cd = glassColor(palette, i, level + 1, front, facet);
                shell.quad(cupX[a], cupY[a], ca, cupX[b], cupY[b], cb,
                        cupX[c], cupY[c], cc, cupX[d], cupY[d], cd);
            }
        }
    }

    private void updateRim(Palette palette, boolean prism, float seconds) {
        rim.clear();
        for (int i = 0; i < SIDES; i++) {
            int next = (i + 1) % SIDES;
            boolean front = facing[i] + facing[next] >= 0f;
            int a = cupX.length - SIDES + i;
            int b = cupX.length - SIDES + next;
            int colorA = prism ? spectral(i, seconds) : palette.highlight;
            int colorB = prism ? spectral(next, seconds) : palette.highlight;
            int opacity = prism ? (front ? 215 : 158) : (front ? 181 : 123);
            rim.quad(cupX[a], cupY[a], withAlpha(colorA, opacity),
                    cupX[b], cupY[b], withAlpha(colorB, opacity),
                    lipX[next], lipY[next], withAlpha(colorB, 43), lipX[i], lipY[i], withAlpha(colorA, 43));
        }
    }

    private int glassColor(Palette palette, int i, int level, boolean front, float facet) {
        float edgeWeight = 1f - Math.abs(facing[i]);
        float edgeLight = fifth(edgeWeight);
        float key = softBand(unitX[i], -0.77f, 0.21f) * (front ? 1f : 0.38f);
        float fill = softBand(unitX[i], 0.84f, 0.12f) * 0.33f;
        float reflection = edgeLight * 0.66f + key * 0.52f + fill;
        float baseGlow = Math.max(0f, 1f - (GLASS_LEVELS[level] + 0.07f) * 5.0f);
        int alpha = Math.round((front ? 5f : 12f) + reflection * (front ? 96f : 55f)
                + baseGlow * 62f + Math.abs(facet - 1f) * 33f);
        return withAlpha(blend(palette.glass, palette.highlight, reflection + baseGlow * 0.22f), alpha);
    }

    private void updateFluid(CupFluidSimulation fluid, Palette palette) {
        float levelRadius = CupFluidSimulation.radiusAtHeight(fluid.getLevel());
        for (int ring = 0; ring <= RINGS; ring++) {
            float fraction = (float) ring / RINGS;
            for (int i = 0; i < (ring == 0 ? 1 : SIDES); i++) {
                int vertex = index(ring, i);
                float radius = fraction * levelRadius;
                float x = radius * unitX[i];
                float z = radius * unitZ[i];
                float y = fluid.surfaceHeight(x, z);
                radius = fraction * CupFluidSimulation.radiusAtHeight(y);
                liquidX[vertex] = radius * unitX[i];
                liquidZ[vertex] = radius * unitZ[i];
                liquidY[vertex] = fluid.surfaceHeight(liquidX[vertex], liquidZ[vertex]);
                project(liquidX[vertex], liquidY[vertex], liquidZ[vertex]);
                screenX[vertex] = projected[0];
                screenY[vertex] = projected[1];
                depth[vertex] = projected[2];
            }
        }
        shadeSurface(palette);
        farWater.clear();
        nearWater.clear();
        for (int i = 0; i < SIDES; i++) {
            int next = (i + 1) % SIDES;
            int a = index(RINGS, i);
            int b = index(RINGS, next);
            boolean front = facing[i] + facing[next] >= 0f;
            Batch wall = front ? nearWater : farWater;
            int topA = wallColor(palette, i, front, liquidY[a], false);
            int topB = wallColor(palette, next, front, liquidY[b], false);
            int baseA = wallColor(palette, i, front, liquidY[a], true);
            int baseB = wallColor(palette, next, front, liquidY[b], true);
            wall.quad(screenX[a], screenY[a], topA, screenX[b], screenY[b], topB,
                    floorX[next], floorY[next], baseB, floorX[i], floorY[i], baseA);
        }
        updateCaustic(palette, fluid.getLevel());
        surface.clear();
        for (int i = 0; i < SURFACE_TRIANGLES; i++) {
            long key = (long) ((depth[cornerA[i]] + depth[cornerB[i]] + depth[cornerC[i]] + 24f) * 4096f);
            sortKeys[i] = (key << 32) | i;
        }
        Arrays.sort(sortKeys);
        for (long key : sortKeys) {
            int triangle = (int) key;
            addSurfaceVertex(cornerA[triangle]);
            addSurfaceVertex(cornerB[triangle]);
            addSurfaceVertex(cornerC[triangle]);
        }
    }

    private void shadeSurface(Palette palette) {
        for (int ring = 0; ring <= RINGS; ring++) {
            for (int i = 0; i < (ring == 0 ? 1 : SIDES); i++) {
                int vertex = index(ring, i);
                float slopeX;
                float slopeZ;
                if (ring == 0) {
                    int right = index(1, 0);
                    int left = index(1, SIDES / 2);
                    int near = index(1, SIDES / 4);
                    int far = index(1, 3 * SIDES / 4);
                    slopeX = (liquidY[right] - liquidY[left]) / Math.max(0.01f, liquidX[right] - liquidX[left]);
                    slopeZ = (liquidY[near] - liquidY[far]) / Math.max(0.01f, liquidZ[near] - liquidZ[far]);
                } else {
                    int outside = index(Math.min(RINGS, ring + 1), i);
                    int inside = index(ring - 1, i);
                    int next = index(ring, (i + 1) % SIDES);
                    int previous = index(ring, (i + SIDES - 1) % SIDES);
                    float radialSpan = (liquidX[outside] - liquidX[inside]) * unitX[i]
                            + (liquidZ[outside] - liquidZ[inside]) * unitZ[i];
                    float tangentSpan = (liquidZ[next] - liquidZ[previous]) * unitX[i]
                            - (liquidX[next] - liquidX[previous]) * unitZ[i];
                    float radial = (liquidY[outside] - liquidY[inside]) / Math.max(0.01f, radialSpan);
                    float tangent = (liquidY[next] - liquidY[previous]) / Math.max(0.01f, tangentSpan);
                    slopeX = radial * unitX[i] - tangent * unitZ[i];
                    slopeZ = radial * unitZ[i] + tangent * unitX[i];
                }
                surfaceColors[vertex] = shadeWaterVertex(palette, vertex, slopeX, slopeZ);
            }
        }
    }

    private int shadeWaterVertex(Palette palette, int vertex, float slopeX, float slopeZ) {
        float worldY = liquidY[vertex] * GLASS_HEIGHT;
        float nx = -slopeX * GLASS_HEIGHT;
        float nz = -slopeZ * GLASS_HEIGHT;
        float ny = 1f / (float) Math.sqrt(1f + nx * nx + nz * nz);
        nx *= ny;
        nz *= ny;

        float vx = EYE_X - liquidX[vertex];
        float vy = EYE_Y - worldY;
        float vz = EYE_Z - liquidZ[vertex];
        float inverseDistance = 1f / (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        vx *= inverseDistance;
        vy *= inverseDistance;
        vz *= inverseDistance;
        float dot = nx * vx + ny * vy + nz * vz;
        if (dot < 0f) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
            dot = -dot;
        }
        float cosine = clamp(dot, 0f, 1f);
        float fresnel = WATER_REFLECTANCE + (1f - WATER_REFLECTANCE) * fifth(1f - cosine);

        float rx = 2f * cosine * nx - vx;
        float ry = 2f * cosine * ny - vy;
        float rz = 2f * cosine * nz - vz;
        float sky = clamp(ry * 0.78f + 0.30f, 0f, 1f);
        // A reflected softbox and a narrower strip light move with the local normal.
        float box = softBand(rx, -0.21f, 0.43f) * softBand(ry, 0.47f, 0.53f)
                * clamp(-rz * 1.3f, 0f, 1f);
        float strip = softBand(rx + ry * 0.36f, -0.12f, 0.15f)
                * softBand(rz, -0.84f, 0.33f);
        float specular = box * box * 0.35f + strip * strip * 0.64f;

        // Snell refraction samples the procedural stage through the liquid to its base.
        float transmittedCosine = (float) Math.sqrt(1f - AIR_TO_WATER * AIR_TO_WATER * (1f - cosine * cosine));
        float bend = AIR_TO_WATER * cosine - transmittedCosine;
        float tx = bend * nx - AIR_TO_WATER * vx;
        float ty = bend * ny - AIR_TO_WATER * vy;
        float tz = bend * nz - AIR_TO_WATER * vz;
        float travel = Math.min(4f, worldY / Math.max(0.22f, -ty));
        float hitX = liquidX[vertex] + tx * travel;
        float hitZ = liquidZ[vertex] + tz * travel;
        float floorPool = clamp(1f - (hitX * hitX + hitZ * hitZ) * 0.34f, 0f, 1f);
        float floorBand = softBand(hitX + hitZ * 0.43f, -0.67f, 0.44f) * 0.32f;
        int floor = blend(0x151d29, 0x303c45, floorPool);
        int transmission = blend(floor, palette.water,
                clamp(0.19f + 0.15f * travel + floorBand, 0f, 0.72f));
        int environment = blend(0x24303a, palette.surface, 0.18f + sky * 0.54f + box * 0.19f);
        int rgb = blend(transmission, environment, fresnel);
        rgb = blend(rgb, palette.highlight, specular);
        return withAlpha(rgb, Math.round(106f + 102f * fresnel + 39f * specular + 6f * travel));
    }

    private int wallColor(Palette palette, int i, boolean front, float height, boolean base) {
        float radialDepth = Math.abs(facing[i]);
        float opticalPath = 0.26f + height * 0.60f + radialDepth * 0.85f;
        float attenuation = opticalPath / (1f + opticalPath);
        float grazing = fifth(1f - radialDepth);
        float refractedKey = softBand(unitX[i], -0.67f, 0.42f) * 0.32f;
        float ambient = (base ? 0.32f : 0.58f) + refractedKey + 0.23f * grazing;
        int tint = blend(palette.deepWater, palette.water, ambient);
        tint = blend(tint, palette.highlight, 0.15f * grazing + refractedKey * 0.28f);
        int alpha = Math.round((front ? 33f : 13f) + attenuation * (front ? 133f : 59f)
                + grazing * (front ? 34f : 10f));
        return withAlpha(tint, alpha);
    }

    private void updateCaustic(Palette palette, float level) {
        caustic.clear();
        if (level < 0.025f) return;
        for (int i = 0; i < SIDES; i++) {
            float waterHeight = liquidY[index(RINGS, i)];
            float bend = 0.17f + 0.085f * unitX[i] + 0.076f * unitZ[i] * unitX[i]
                    + 0.24f * (waterHeight - level);
            float width = 0.045f + 0.032f * unitZ[i] * unitZ[i];
            for (int stripe = 0; stripe < 3; stripe++) {
                float y = waterHeight * clamp(bend + (stripe - 1) * width, 0.01f, 0.80f);
                float r = CupFluidSimulation.radiusAtHeight(y);
                project(r * unitX[i], y, r * unitZ[i]);
                int point = stripe * SIDES + i;
                causticX[point] = projected[0];
                causticY[point] = projected[1];
            }
        }
        int color = blend(palette.surface, palette.highlight, 0.53f);
        int transparent = withAlpha(color, 0);
        for (int i = 0; i < SIDES; i++) {
            int next = (i + 1) % SIDES;
            if (facing[i] + facing[next] < 0f) continue;
            int a = SIDES + i;
            int b = SIDES + next;
            int ca = withAlpha(color, Math.round(causticIntensity(i) * 92f));
            int cb = withAlpha(color, Math.round(causticIntensity(next) * 92f));
            caustic.quad(causticX[i], causticY[i], transparent, causticX[next], causticY[next], transparent,
                    causticX[b], causticY[b], cb, causticX[a], causticY[a], ca);
            caustic.quad(causticX[a], causticY[a], ca, causticX[b], causticY[b], cb,
                    causticX[b + SIDES], causticY[b + SIDES], transparent,
                    causticX[a + SIDES], causticY[a + SIDES], transparent);
        }
    }

    private float causticIntensity(int side) {
        return softBand(unitX[side], -0.53f, 0.50f) * clamp(facing[side] * 2.2f, 0f, 1f)
                + 0.44f * softBand(unitX[side], 0.47f, 0.33f);
    }

    private void drawFloorCaustic(Canvas canvas, float level) {
        float side = liquidY[index(RINGS, 0)] - liquidY[index(RINGS, SIDES / 2)];
        project(0.32f - 0.32f * side, -0.078f, 0.29f);
        canvas.save();
        canvas.translate(projected[0], projected[1] + 0.08f);
        canvas.scale(1.16f + 0.16f * level, 0.24f + 0.07f * level);
        paint.setShader(FLOOR_LIGHT);
        paint.setColor(withAlpha(0xffffff, Math.round(76f + level * 71f)));
        canvas.drawCircle(0f, 0f, 1f, paint);
        paint.setShader(null);
        canvas.restore();
    }

    private void addSurfaceVertex(int index) {
        surface.vertex(screenX[index], screenY[index], surfaceColors[index]);
    }

    private void drawMeniscus(Canvas canvas, Palette palette) {
        drawMeniscus(canvas, palette, false);
        drawMeniscus(canvas, palette, true);
    }

    private void drawMeniscus(Canvas canvas, Palette palette, boolean front) {
        edge.reset();
        for (int i = 0; i < SIDES; i++) {
            int next = (i + 1) % SIDES;
            if ((facing[i] + facing[next] >= 0f) != front) continue;
            int a = index(RINGS, i);
            int b = index(RINGS, next);
            edge.moveTo(screenX[a], screenY[a]);
            edge.lineTo(screenX[b], screenY[b]);
        }
        stroke(canvas, edge, withAlpha(palette.highlight, front ? 132 : 66), front ? 0.0085f : 0.0065f);
    }

    private void drawGlassEdges(Canvas canvas, Palette palette, boolean crystal) {
        int rimStart = cupX.length - SIDES;
        contour(canvas, rimStart, 0, SIDES, withAlpha(palette.highlight, 102), 0.009f);
        contour(canvas, SIDES, 0, SIDES, withAlpha(palette.glass, 70), 0.014f);
        contour(canvas, SIDES * 2, 2, 19, withAlpha(palette.highlight, 146), 0.017f);
        contour(canvas, rimStart, 7, 16, withAlpha(palette.highlight, 205), 0.015f);
        contour(canvas, rimStart, 27, 34, withAlpha(palette.highlight, 115), 0.009f);
        for (int i : EDGE_REFLECTIONS) {
            int low = SIDES * 2 + i;
            int high = rimStart + i;
            edge.reset();
            edge.moveTo(cupX[low], cupY[low]);
            edge.lineTo(cupX[high], cupY[high]);
            stroke(canvas, edge, withAlpha(palette.highlight, i == 2 ? 87 : 128), 0.010f);
        }
        if (crystal) {
            for (int i = 4; i <= 24; i += 4) {
                int a = SIDES * 2 + i;
                int b = SIDES * 5 + i;
                edge.reset();
                edge.moveTo(cupX[a], cupY[a]);
                edge.lineTo(cupX[b], cupY[b]);
                stroke(canvas, edge, withAlpha(palette.highlight, 91), 0.013f);
            }
        }
    }

    private void contour(Canvas canvas, int start, int from, int to, int color, float thickness) {
        edge.reset();
        for (int i = from; i <= to; i++) {
            int vertex = start + (i % SIDES);
            if (i == from) edge.moveTo(cupX[vertex], cupY[vertex]);
            else edge.lineTo(cupX[vertex], cupY[vertex]);
        }
        stroke(canvas, edge, color, thickness);
    }

    private void stroke(Canvas canvas, Path path, int color, float thickness) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(thickness);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawShadow(Canvas canvas) {
        canvas.save();
        canvas.translate(0f, 1.28f);
        canvas.scale(1.52f, 0.39f);
        paint.setColor(0xffffffff);
        paint.setShader(SHADOW);
        canvas.drawCircle(0f, 0f, 1f, paint);
        paint.setShader(null);
        canvas.restore();
    }

    private void updateSpill(CupFluidSimulation fluid, Palette palette) {
        farSpill.clear();
        nearSpill.clear();
        int streamCount = fluid.getSpillStreamCount();
        int streamColor = blend(palette.water, palette.surface, 0.58f);
        int streak = blend(palette.surface, palette.highlight, 0.73f);
        for (int stream = 0; stream < streamCount; stream++) {
            int count = Math.min(MAX_STREAM_POINTS, fluid.getSpillStreamPointCount(stream));
            if (count < 2) continue;
            for (int i = 0; i < count; i++) {
                float x = fluid.getSpillStreamX(stream, i);
                float z = fluid.getSpillStreamZ(stream, i);
                project(x, fluid.getSpillStreamY(stream, i), z);
                streamX[i] = projected[0];
                streamY[i] = projected[1];
                streamDepth[i] = cameraZ(x, z);
                streamHalfWidth[i] = 0.5f * fluid.getSpillStreamWidth(stream, i) * perspective(projected[2]);
                streamOpacity[i] = clamp(fluid.getSpillStreamOpacity(stream, i), 0f, 1f);
            }
            for (int i = 0; i < count; i++) {
                int before = Math.max(0, i - 1);
                int after = Math.min(count - 1, i + 1);
                float dx = streamX[after] - streamX[before];
                float dy = streamY[after] - streamY[before];
                float width = streamHalfWidth[i] / Math.max(0.004f, (float) Math.sqrt(dx * dx + dy * dy));
                streamSideX[i] = -dy * width;
                streamSideY[i] = dx * width;
            }
            for (int i = 0; i < count - 1; i++) {
                Batch layer = streamDepth[i] + streamDepth[i + 1] >= 0f ? nearSpill : farSpill;
                addSpillSegment(layer, i, -1f, -0.18f, streamColor, streak, 24f, 153f);
                addSpillSegment(layer, i, -0.18f, 0.21f, streak, streamColor, 153f, 123f);
                addSpillSegment(layer, i, 0.21f, 1f, streamColor, streamColor, 123f, 25f);
            }
        }
    }

    private void addSpillSegment(Batch batch, int a, float left, float right, int leftColor, int rightColor,
                                 float leftAlpha, float rightAlpha) {
        int b = a + 1;
        float ax = streamX[a];
        float ay = streamY[a];
        float bx = streamX[b];
        float by = streamY[b];
        float asx = streamSideX[a];
        float asy = streamSideY[a];
        float bsx = streamSideX[b];
        float bsy = streamSideY[b];
        batch.quad(ax + asx * left, ay + asy * left, withAlpha(leftColor, Math.round(leftAlpha * streamOpacity[a])),
                bx + bsx * left, by + bsy * left, withAlpha(leftColor, Math.round(leftAlpha * streamOpacity[b])),
                bx + bsx * right, by + bsy * right, withAlpha(rightColor, Math.round(rightAlpha * streamOpacity[b])),
                ax + asx * right, ay + asy * right, withAlpha(rightColor, Math.round(rightAlpha * streamOpacity[a])));
    }

    private void drawDroplets(Canvas canvas, CupFluidSimulation fluid, Palette palette, boolean front) {
        int count = fluid.getDropletCount();
        for (int i = 0; i < count; i++) {
            float x = fluid.getDropletX(i);
            float y = fluid.getDropletY(i);
            float z = fluid.getDropletZ(i);
            boolean nearest = cameraZ(x, z) >= 0f;
            if (nearest != front) continue;
            project(x, y, z);
            float size = fluid.getDropletRadius(i) * perspective(projected[2]);
            float sx = projected[0];
            float sy = projected[1];
            project(x + 0.012f * fluid.getDropletVelocityX(i), y + 0.012f * fluid.getDropletVelocityY(i),
                    z + 0.012f * fluid.getDropletVelocityZ(i));
            float dx = projected[0] - sx;
            float dy = projected[1] - sy;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            float rotation = (float) Math.toDegrees(Math.atan2(dy, dx));
            float stretch = size * (1.03f + clamp(distance * 0.14f / Math.max(0.004f, size), 0f, 0.78f));
            float opacity = clamp(fluid.getDropletOpacity(i), 0f, 1f) * (nearest ? 1f : 0.85f);
            canvas.save();
            canvas.translate(sx, sy);
            canvas.rotate(rotation);
            paint.setColor(withAlpha(palette.water, Math.round(opacity * 133f)));
            canvas.drawOval(-stretch, -size * 0.83f, stretch, size * 0.83f, paint);
            paint.setColor(withAlpha(palette.surface, Math.round(opacity * 158f)));
            canvas.drawOval(-stretch * 0.74f, -size * 0.75f, stretch * 0.53f, size * 0.19f, paint);
            canvas.restore();
            paint.setColor(withAlpha(palette.highlight, Math.round(opacity * 237f)));
            canvas.drawCircle(sx - size * 0.31f, sy - size * 0.32f, size * 0.28f, paint);
        }
    }

    private void project(float x, float y, float z) {
        float cameraX = x * YAW_COS + z * YAW_SIN;
        float cameraZ = cameraZ(x, z);
        float cameraY = y * GLASS_HEIGHT - CAMERA_Y;
        float toward = cameraZ * PITCH_COS + cameraY * PITCH_SIN;
        float scale = perspective(toward);
        projected[0] = cameraX * scale;
        projected[1] = (cameraZ * PITCH_SIN - cameraY * PITCH_COS) * scale;
        projected[2] = toward;
    }

    private static float cameraZ(float x, float z) {
        return z * YAW_COS - x * YAW_SIN;
    }

    private static float perspective(float depth) {
        return CAMERA_DISTANCE / Math.max(1.5f, CAMERA_DISTANCE - depth);
    }

    private static float fifth(float value) {
        float squared = value * value;
        return squared * squared * value;
    }

    private static float softBand(float value, float center, float radius) {
        float distance = clamp(1f - Math.abs(value - center) / radius, 0f, 1f);
        return distance * distance * (3f - 2f * distance);
    }

    private static int index(int ring, int angular) {
        return ring == 0 ? 0 : 1 + (ring - 1) * SIDES + angular;
    }

    private static int spectral(int index, float seconds) {
        float phase = TAU * (index / (float) SIDES + seconds / 3.2f);
        int r = Math.round(177f + 77f * (float) Math.cos(phase));
        int g = Math.round(177f + 77f * (float) Math.cos(phase + TAU / 3f));
        int b = Math.round(177f + 77f * (float) Math.cos(phase + TAU * 2f / 3f));
        return r << 16 | g << 8 | b;
    }

    private static int blend(int from, int to, float weight) {
        float mix = clamp(weight, 0f, 1f);
        int red = Math.round((from >> 16 & 255) * (1f - mix) + (to >> 16 & 255) * mix);
        int green = Math.round((from >> 8 & 255) * (1f - mix) + (to >> 8 & 255) * mix);
        int blue = Math.round((from & 255) * (1f - mix) + (to & 255) * mix);
        return red << 16 | green << 8 | blue;
    }

    private static int withAlpha(int rgb, int alpha) {
        return (rgb & 0x00ffffff) | (Math.min(255, alpha) << 24);
    }

    private static float clamp(float value, float min, float max) {
        return Math.min(max, Math.max(min, value));
    }

    private record Palette(int glass, int highlight, int water, int surface, int deepWater) { }

    private static final class Batch {
        private final float[] positions;
        private final int[] colors;
        private final Path triangle = new Path();
        private int size;

        Batch(int capacity) {
            positions = new float[capacity * 2];
            colors = new int[capacity];
        }

        void clear() { size = 0; }

        void vertex(float x, float y, int color) {
            positions[size * 2] = x;
            positions[size * 2 + 1] = y;
            colors[size++] = color;
        }

        void quad(float ax, float ay, int a, float bx, float by, int b,
                  float cx, float cy, int c, float dx, float dy, int d) {
            vertex(ax, ay, a);
            vertex(bx, by, b);
            vertex(cx, cy, c);
            vertex(ax, ay, a);
            vertex(cx, cy, c);
            vertex(dx, dy, d);
        }

        void draw(Canvas canvas, Paint paint) {
            if (size == 0) return;
            if (Build.VERSION.SDK_INT < 29 && canvas.isHardwareAccelerated()) {
                // Android 9 can rasterize the same triangles as paths on a hardware canvas.
                for (int i = 0; i < size; i += 3) {
                    int point = i * 2;
                    triangle.reset();
                    triangle.moveTo(positions[point], positions[point + 1]);
                    triangle.lineTo(positions[point + 2], positions[point + 3]);
                    triangle.lineTo(positions[point + 4], positions[point + 5]);
                    triangle.close();
                    paint.setColor(colors[i + 1]);
                    canvas.drawPath(triangle, paint);
                }
                return;
            }
            paint.setColor(0xffffffff);
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES, size * 2, positions, 0, null,
                    0, colors, 0, null, 0, 0, paint);
        }
    }
}
