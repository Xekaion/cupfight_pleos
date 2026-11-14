package io.autohmg.cupfight;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Set;

/** Animated attachments and lighting drawn in the same coordinate system as the parked mesh. */
final class PremiumCarEffects {
    private static final float TAU = (float) (2d * Math.PI);
    private static final int AQUA = 0xff6dfbff;
    private static final int VIOLET = 0xffaa82ff;
    private static final int ICE = 0xffe0fcff;
    private static final int GOLD = 0xffffca72;
    private static final RadialGradient BLUE_GLOW = new RadialGradient(0f, 0f, 1f,
            new int[]{0xc963f7ff, 0x5563c7ff, 0x003060ff},
            new float[]{0f, 0.46f, 1f}, Shader.TileMode.CLAMP);
    private static final RadialGradient VIOLET_GLOW = new RadialGradient(0f, 0f, 1f,
            new int[]{0xafb473ff, 0x5b7950ff, 0x007950ff},
            new float[]{0f, 0.47f, 1f}, Shader.TileMode.CLAMP);
    private static final RadialGradient MAGENTA_GLOW = new RadialGradient(0f, 0f, 1f,
            new int[]{0xafef69d8, 0x5b9b50dc, 0x009b50dc},
            new float[]{0f, 0.47f, 1f}, Shader.TileMode.CLAMP);
    private static final Orbit INNER_ORBIT = new Orbit(1.54f, 2.43f, 0.19f, 0.10f, 0.07f);
    private static final Orbit OUTER_ORBIT = new Orbit(1.66f, 2.55f, 0.18f, -0.14f, 0.08f);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final float[] points = new float[8];

    void drawGround(Canvas canvas, CarMeshRenderer mesh, Set<Cosmetic> items, float seconds) {
        if (items.contains(Cosmetic.PULSE_UNDERGLOW)) drawUnderglow(canvas, mesh, seconds);
        if (items.contains(Cosmetic.AURORA_GRILLE)) {
            float phase = wave(seconds, 3f);
            glow(canvas, mesh, -0.54f, -2.28f, 0.50f, 0.40f, BLUE_GLOW, 95f + 85f * phase);
            glow(canvas, mesh, 0.54f, -2.28f, 0.50f, 0.40f, VIOLET_GLOW, 180f - 85f * phase);
        }
        if (items.contains(Cosmetic.PHOTON_DIFFUSER)) {
            glow(canvas, mesh, 0f, 2.37f, 1.00f, 0.47f, BLUE_GLOW,
                    110f + 110f * wave(seconds, 2.4f));
        }
        if (items.contains(Cosmetic.QUANTUM_ORBIT)) drawOrbit(canvas, mesh, seconds);
        if (items.contains(Cosmetic.STARDUST_TRAIL)) drawStardust(canvas, mesh, seconds);
        if (items.contains(Cosmetic.ION_TURBO)) drawTurbo(canvas, mesh, seconds);
        if (items.contains(Cosmetic.ACTIVE_WING) && wingBehindBody(mesh)) {
            drawActiveWing(canvas, mesh, seconds, 180f);
        }
    }

    void drawBody(Canvas canvas, CarMeshRenderer mesh, Set<Cosmetic> items, float seconds,
                  float yaw) {
        if (items.contains(Cosmetic.ACTIVE_WING) && !wingBehindBody(mesh)) {
            drawActiveWing(canvas, mesh, seconds, yaw);
        }
    }

    private boolean wingBehindBody(CarMeshRenderer mesh) {
        mesh.project(0f, 0f, 2f, points, 0);
        mesh.project(0f, 0f, -2f, points, 2);
        return points[1] < points[3] - 0.25f;
    }

    private void drawUnderglow(Canvas canvas, CarMeshRenderer mesh, float seconds) {
        float phase = wave(seconds, 2.4f);
        glow(canvas, mesh, 0f, 0f, 1.48f, 2.45f, MAGENTA_GLOW, 130f + 80f * phase);
        glow(canvas, mesh, -1.02f, 0.04f, 0.50f, 1.86f, BLUE_GLOW, 166f + 75f * phase);
        glow(canvas, mesh, 1.02f, 0.04f, 0.50f, 1.86f, BLUE_GLOW, 241f - 75f * phase);
    }

    private void glow(Canvas canvas, CarMeshRenderer mesh, float x, float z, float width,
                      float length, RadialGradient gradient, float opacity) {
        mesh.project(x, 0.02f, z, points, 0);
        mesh.project(x + width, 0.02f, z, points, 2);
        mesh.project(x, 0.02f, z + length, points, 4);
        float radiusX = Math.max(0.11f, Math.abs(points[2] - points[0]) + Math.abs(points[4] - points[0]));
        float radiusY = Math.max(0.06f, Math.abs(points[3] - points[1]) + Math.abs(points[5] - points[1]));
        canvas.save();
        canvas.translate(points[0], points[1]);
        canvas.scale(radiusX, radiusY);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(gradient);
        paint.setAlpha(Math.round(opacity));
        canvas.drawCircle(0f, 0f, 1f, paint);
        paint.setShader(null);
        canvas.restore();
    }

    private void drawOrbit(Canvas canvas, CarMeshRenderer mesh, float seconds) {
        float phase = TAU * cycle(seconds, 4f);
        orbitArc(canvas, mesh, INNER_ORBIT, 0f, TAU, 0x526381ff, 0.019f, 40);
        orbitArc(canvas, mesh, OUTER_ORBIT, 0f, TAU, 0x344ce4ff, 0.014f, 40);
        orbitArc(canvas, mesh, INNER_ORBIT, phase, 1.30f, 0x6278d5ff, 0.082f, 12);
        orbitArc(canvas, mesh, INNER_ORBIT, phase, 1.30f, AQUA, 0.035f, 12);
        orbitArc(canvas, mesh, OUTER_ORBIT, phase + 3.14f, 1.05f, VIOLET, 0.040f, 11);
        for (int i = 0; i < 5; i++) {
            float angle = phase + i * TAU / 5f;
            projectOrbit(mesh, OUTER_ORBIT, angle);
            spark(canvas, points[0], points[1], 0.044f, i % 2 == 0 ? AQUA : VIOLET);
        }
    }

    private void orbitArc(Canvas canvas, CarMeshRenderer mesh, Orbit orbit, float start,
                          float sweep, int color, float width, int steps) {
        shape.reset();
        for (int i = 0; i <= steps; i++) {
            projectOrbit(mesh, orbit, start + i * sweep / steps);
            if (i == 0) shape.moveTo(points[0], points[1]);
            else shape.lineTo(points[0], points[1]);
        }
        stroke(canvas, color, width);
    }

    private void projectOrbit(CarMeshRenderer mesh, Orbit orbit, float angle) {
        float cosine = (float) Math.cos(angle);
        float sine = (float) Math.sin(angle);
        mesh.project(orbit.width * cosine, orbit.height + orbit.tiltX * cosine + orbit.tiltZ * sine,
                orbit.length * sine, points, 0);
    }

    private record Orbit(float width, float length, float height, float tiltX, float tiltZ) { }

    private void drawStardust(Canvas canvas, CarMeshRenderer mesh, float seconds) {
        for (int i = 0; i < 20; i++) {
            float age = (cycle(seconds, 2.4f) + i * 0.137f) % 1f;
            float side = i % 2 == 0 ? 1f : -1f;
            float drift = (float) Math.sin(i * 2.399f + age * 2.7f);
            float x = side * (0.49f + 0.64f * age) + drift * (0.08f + 0.19f * age);
            float z = 2.18f + (0.99f + (i % 4) * 0.12f) * age;
            mesh.project(x, 0.10f + age * (0.14f + (i % 3) * 0.09f), z, points, 0);
            int color = alpha(i % 3 == 0 ? VIOLET : i % 3 == 1 ? ICE : GOLD,
                    250f * (1f - age));
            spark(canvas, points[0], points[1], 0.021f + (1f - age) * 0.031f, color);
        }
    }

    private void drawTurbo(Canvas canvas, CarMeshRenderer mesh, float seconds) {
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 5; i >= 0; i--) {
                float age = (cycle(seconds, 1.2f) + i / 6f) % 1f;
                float radius = 0.10f + 0.105f * age;
                float x = side * (0.64f + 0.13f * age);
                float z = 2.22f + 0.91f * age;
                float start = TAU * (age + i * 0.22f) * side;
                shape.reset();
                for (int segment = 0; segment <= 9; segment++) {
                    float angle = start + segment * 0.48f;
                    mesh.project(x + radius * (float) Math.cos(angle),
                            0.27f + age * 0.09f + radius * (float) Math.sin(angle), z, points, 0);
                    if (segment == 0) shape.moveTo(points[0], points[1]);
                    else shape.lineTo(points[0], points[1]);
                }
                float opacity = (1f - age) * 236f;
                stroke(canvas, alpha(VIOLET, opacity * 0.50f), 0.068f);
                stroke(canvas, alpha(i % 2 == 0 ? AQUA : ICE, opacity), 0.024f);
            }
        }
    }

    private void drawActiveWing(Canvas canvas, CarMeshRenderer mesh, float seconds, float yaw) {
        int far = yaw >= 0f ? 1 : -1;
        drawBlade(canvas, mesh, far, seconds);
        quad(canvas, mesh, -0.19f, 0.89f, 1.82f, 0.19f, 0.89f, 1.82f,
                0.14f, 0.89f, 2.10f, -0.14f, 0.89f, 2.10f, 0xff425771);
        quad(canvas, mesh, -0.09f, 0.94f, 1.87f, 0.09f, 0.94f, 1.87f,
                0.065f, 0.91f, 2.13f, -0.065f, 0.91f, 2.13f, 0xff233348);
        line(canvas, mesh, 0f, 0.949f, 1.90f, 0f, 0.919f, 2.095f, VIOLET, 0.021f);
        drawBlade(canvas, mesh, -far, seconds);
    }

    private void drawBlade(Canvas canvas, CarMeshRenderer mesh, int side, float seconds) {
        float phase = cycle(seconds + (side < 0 ? 0.20f : 0f), 4.8f);
        float travel = Math.min(1f, Math.min(phase / 0.28f, (1f - phase) / 0.30f));
        float lift = travel * travel * (3f - 2f * travel);
        float frontY = 0.89f + 0.27f * lift;
        float rearY = frontY + 0.035f + 0.092f * lift;
        float tipRise = 0.17f * lift;
        float frontZ = 1.82f - 0.058f * lift;
        float rearZ = 2.10f + 0.055f * lift;
        float tipX = side * (0.78f + 0.32f * lift);
        float rootX = side * 0.13f;
        float tipFrontX = tipX - side * 0.09f;
        drawStrut(canvas, mesh, side * 0.52f, frontY + tipRise * 0.50f, frontZ, lift);
        quad(canvas, mesh, rootX, rearY - 0.064f, rearZ, tipX, rearY + tipRise - 0.064f, rearZ,
                tipX, rearY + tipRise, rearZ, rootX, rearY, rearZ, 0xff1b2b42);
        quad(canvas, mesh, rootX, frontY, frontZ, tipFrontX, frontY + tipRise, frontZ,
                tipX, rearY + tipRise, rearZ, rootX, rearY, rearZ, 0xff465876);
        quad(canvas, mesh, rootX, frontY + 0.002f, frontZ + 0.057f,
                tipFrontX - side * 0.08f, frontY + tipRise + 0.002f, frontZ + 0.057f,
                tipX - side * 0.10f, rearY + tipRise, rearZ - 0.066f,
                rootX, rearY, rearZ - 0.066f, 0xff28334a);
        quad(canvas, mesh, tipFrontX, frontY + tipRise + 0.059f, frontZ,
                tipX, rearY + tipRise + 0.071f, rearZ, tipX, rearY + tipRise - 0.078f, rearZ,
                tipFrontX, frontY + tipRise - 0.040f, frontZ, 0xff394d6b);

        int light = side < 0 ? AQUA : VIOLET;
        for (int i = 0; i < 5; i++) {
            float start = 0.05f + i * 0.181f;
            float end = start + 0.15f;
            float shine = wave(seconds + i * 0.12f, 1.2f);
            float ax = rootX + (tipX - rootX) * start;
            float bx = rootX + (tipX - rootX) * end;
            float ay = rearY - 0.019f + tipRise * start;
            float by = rearY - 0.019f + tipRise * end;
            line(canvas, mesh, ax, ay, rearZ + 0.005f, bx, by, rearZ + 0.005f,
                    alpha(light, 56f + 90f * shine), 0.058f);
            line(canvas, mesh, ax, ay, rearZ + 0.005f, bx, by, rearZ + 0.005f,
                    alpha(light, 160f + 95f * shine), 0.025f);
        }
    }

    private void drawStrut(Canvas canvas, CarMeshRenderer mesh, float x, float top,
                           float frontZ, float lift) {
        float hingeY = 0.87f;
        float hingeZ = 1.92f;
        float midY = hingeY + 0.18f * lift;
        float midZ = hingeZ - 0.13f * lift;
        line(canvas, mesh, x, hingeY, hingeZ, x, midY, midZ, 0xff17243a, 0.084f);
        line(canvas, mesh, x, midY, midZ, x, top - 0.014f, frontZ + 0.055f, 0xff778da9, 0.063f);
        line(canvas, mesh, x, midY, midZ, x, top - 0.014f, frontZ + 0.055f, 0xffd1f6ff, 0.017f);
    }

    private void quad(Canvas canvas, CarMeshRenderer mesh, float ax, float ay, float az,
                      float bx, float by, float bz, float cx, float cy, float cz,
                      float dx, float dy, float dz, int color) {
        mesh.project(ax, ay, az, points, 0);
        mesh.project(bx, by, bz, points, 2);
        mesh.project(cx, cy, cz, points, 4);
        mesh.project(dx, dy, dz, points, 6);
        shape.reset();
        shape.moveTo(points[0], points[1]);
        shape.lineTo(points[2], points[3]);
        shape.lineTo(points[4], points[5]);
        shape.lineTo(points[6], points[7]);
        shape.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawPath(shape, paint);
    }

    private void line(Canvas canvas, CarMeshRenderer mesh, float ax, float ay, float az,
                      float bx, float by, float bz, int color, float width) {
        mesh.project(ax, ay, az, points, 0);
        mesh.project(bx, by, bz, points, 2);
        shape.reset();
        shape.moveTo(points[0], points[1]);
        shape.lineTo(points[2], points[3]);
        stroke(canvas, color, width);
    }

    private void spark(Canvas canvas, float x, float y, float size, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(alpha(color, Color.alpha(color) * 0.20f));
        canvas.drawCircle(x, y, size * 1.45f, paint);
        paint.setColor(color);
        shape.reset();
        shape.moveTo(x, y - size);
        shape.lineTo(x + size * 0.23f, y - size * 0.23f);
        shape.lineTo(x + size * 0.82f, y);
        shape.lineTo(x + size * 0.23f, y + size * 0.23f);
        shape.lineTo(x, y + size);
        shape.lineTo(x - size * 0.23f, y + size * 0.23f);
        shape.lineTo(x - size * 0.82f, y);
        shape.lineTo(x - size * 0.23f, y - size * 0.23f);
        shape.close();
        canvas.drawPath(shape, paint);
    }

    private void stroke(Canvas canvas, int color, float width) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(width);
        paint.setColor(color);
        canvas.drawPath(shape, paint);
    }

    private static float cycle(float seconds, float period) {
        return seconds % period / period;
    }

    private static float wave(float seconds, float period) {
        return 0.5f + 0.5f * (float) Math.sin(TAU * cycle(seconds, period));
    }

    private static int alpha(int color, float opacity) {
        return (color & 0x00ffffff) | (Math.round(opacity) << 24);
    }
}
