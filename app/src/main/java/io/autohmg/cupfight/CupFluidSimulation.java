package io.autohmg.cupfight;

import java.util.Arrays;

/** Damped shallow-water flow in a tapered glass. The replay score supplies its volume. */
final class CupFluidSimulation {
    private static final int GRID = 15;
    private static final int CELLS = GRID * GRID;
    private static final float SPACING = 2f / (GRID - 1);
    private static final double STEP_SECONDS = 1.0 / 240.0;
    private static final float DT = (float) STEP_SECONDS;
    private static final float MAX_ELAPSED_SECONDS = 0.125f;
    private static final float BASE_RADIUS = 0.77f;
    private static final float TAPER = 1f - BASE_RADIUS;
    private static final float FULL_HEIGHT = 0.97f;
    private static final float RADIUS_METERS = 0.052f;
    private static final float HEIGHT_METERS = 0.13364f;
    static final float HEIGHT_TO_RADIUS = HEIGHT_METERS / RADIUS_METERS;
    private static final float GRAVITY = 9.81f;
    private static final float VERTICAL_GRAVITY = GRAVITY / HEIGHT_METERS;
    private static final float FLOW_DRAG = 0.987f;
    private static final float SURFACE_DIFFUSION = 0.017f;
    private static final int RIM_SAMPLES = 24;
    private static final int MAX_DROPLETS = 36;
    private static final int MAX_STREAMS = 2;
    private static final int STREAM_POINTS = 12;
    private static final float STREAM_SAMPLE_SECONDS = 3 * DT;
    private static final float SPILL_WINDOW_SECONDS = 0.32f;
    private static final int RANDOM_SEED = 0x435550;

    private final int[] cells = new int[CELLS];
    private final int[] east = new int[CELLS];
    private final int[] west = new int[CELLS];
    private final int[] rear = new int[CELLS];
    private final int[] front = new int[CELLS];
    private final int[] nearest = new int[CELLS];
    private final float[] eastFlow = new float[CELLS];
    private final float[] rearFlow = new float[CELLS];
    private final float[] rimX = new float[RIM_SAMPLES];
    private final float[] rimZ = new float[RIM_SAMPLES];
    private final float[] rimHeight = new float[RIM_SAMPLES];
    private final Droplet[] droplets = new Droplet[MAX_DROPLETS];
    private final SpillStream[] streams = new SpillStream[MAX_STREAMS];

    private float[] height = new float[CELLS];
    private float[] nextHeight = new float[CELLS];
    private int cellCount;
    private int dropletCount;
    private int streamCount;
    private int randomState;
    private double accumulatedSeconds;
    private float remaining;
    private float level;
    private float surfaceRadius;
    private float lateral;
    private float longitudinal;
    private float spillBudget;
    private float spillTime;
    private SpillStream activeStream;
    private boolean surfaceMoving;

    CupFluidSimulation() {
        initializeGrid();
        for (int i = 0; i < RIM_SAMPLES; i++) {
            double angle = 2 * Math.PI * i / RIM_SAMPLES;
            rimX[i] = (float) Math.cos(angle);
            rimZ[i] = (float) Math.sin(angle);
        }
        for (int i = 0; i < droplets.length; i++) {
            droplets[i] = new Droplet();
        }
        for (int i = 0; i < streams.length; i++) {
            streams[i] = new SpillStream();
        }
        reset(100f);
    }

    void reset(float remainingPercent) {
        remaining = Float.isFinite(remainingPercent) ? clamp(remainingPercent, 0f, 100f) : 100f;
        updateLevel();
        clearSurface();
        accumulatedSeconds = 0;
        lateral = longitudinal = spillBudget = spillTime = 0f;
        dropletCount = 0;
        streamCount = 0;
        activeStream = null;
        randomState = RANDOM_SEED;
    }

    void advance(float elapsedSeconds, float remainingPercent, float lateralMps2,
                 float longitudinalMps2) {
        if (Float.isFinite(remainingPercent)) {
            float nextRemaining = clamp(remainingPercent, 0f, 100f);
            if (remaining != nextRemaining) {
                updateVolume(nextRemaining);
            }
        }
        float nextLateral = acceleration(lateralMps2);
        float nextLongitudinal = acceleration(longitudinalMps2);
        if (level > 0f && (lateral != nextLateral || longitudinal != nextLongitudinal)) {
            surfaceMoving = true;
        }
        lateral = nextLateral;
        longitudinal = nextLongitudinal;
        if (Float.isFinite(elapsedSeconds) && elapsedSeconds > 0f && isMoving()) {
            accumulatedSeconds += Math.min(elapsedSeconds, MAX_ELAPSED_SECONDS);
            while (accumulatedSeconds >= STEP_SECONDS) {
                step();
                accumulatedSeconds -= STEP_SECONDS;
            }
        }
    }

    /** Interior radius in units of the rim radius, where y=0 is the floor and y=1 the rim. */
    static float radiusAtHeight(float height) {
        return BASE_RADIUS + TAPER * clamp(height, 0f, 1f);
    }

    float getLevel() {
        return level;
    }

    /** x points right and z points towards the viewer. Outside the surface, use the nearest rim. */
    float surfaceHeight(float x, float z) {
        if (level == 0f || !Float.isFinite(x) || !Float.isFinite(z)) {
            return level;
        }
        double distanceSquared = (double) x * x + (double) z * z;
        if (distanceSquared > surfaceRadius * surfaceRadius) {
            float scale = (float) (surfaceRadius / Math.sqrt(distanceSquared));
            x *= scale;
            z *= scale;
        }
        return clamp(level + sample(x / surfaceRadius, z / surfaceRadius), 0f, 1f);
    }

    boolean isMoving() {
        return surfaceMoving || dropletCount > 0 || streamCount > 0 || spillTime > 0f;
    }

    int getDropletCount() {
        return dropletCount;
    }

    float getDropletX(int index) {
        return droplets[index].x;
    }

    float getDropletY(int index) {
        return droplets[index].y;
    }

    float getDropletZ(int index) {
        return droplets[index].z;
    }

    float getDropletRadius(int index) {
        return droplets[index].radius;
    }

    float getDropletOpacity(int index) {
        return clamp(droplets[index].life / 0.085f, 0f, 1f);
    }

    float getDropletVelocityX(int index) {
        return droplets[index].vx;
    }

    float getDropletVelocityY(int index) {
        return droplets[index].vy;
    }

    float getDropletVelocityZ(int index) {
        return droplets[index].vz;
    }

    int getSpillStreamCount() {
        return streamCount;
    }

    /** Stream points run from the leading tip back to the current source at the rim. */
    int getSpillStreamPointCount(int index) {
        SpillStream stream = streams[index];
        return stream.count + (stream == activeStream ? 1 : 0);
    }

    float getSpillStreamX(int index, int point) {
        return streamPoint(index, point).x;
    }

    float getSpillStreamY(int index, int point) {
        return streamPoint(index, point).y;
    }

    float getSpillStreamZ(int index, int point) {
        return streamPoint(index, point).z;
    }

    /** Full sheet width in units of the rim radius. */
    float getSpillStreamWidth(int index, int point) {
        StreamPoint sample = streamPoint(index, point);
        return sample.width * (1f - 0.54f * sample.age / sample.breakAge);
    }

    float getSpillStreamOpacity(int index, int point) {
        StreamPoint sample = streamPoint(index, point);
        return 1f - 0.49f * sample.age / sample.breakAge;
    }

    private StreamPoint streamPoint(int index, int point) {
        SpillStream stream = streams[index];
        return point == stream.count ? stream.anchor : stream.point(point);
    }

    private void initializeGrid() {
        boolean[] inside = new boolean[CELLS];
        int center = (GRID - 1) / 2;
        for (int z = 0; z < GRID; z++) {
            for (int x = 0; x < GRID; x++) {
                int dx = x - center;
                int dz = z - center;
                if (dx * dx + dz * dz <= center * center) {
                    int i = z * GRID + x;
                    inside[i] = true;
                    cells[cellCount++] = i;
                }
            }
        }
        for (int i = 0; i < CELLS; i++) {
            int x = i % GRID;
            int z = i / GRID;
            if (inside[i]) {
                nearest[i] = i;
                east[i] = x < GRID - 1 && inside[i + 1] ? i + 1 : -1;
                west[i] = x > 0 && inside[i - 1] ? i - 1 : -1;
                rear[i] = z < GRID - 1 && inside[i + GRID] ? i + GRID : -1;
                front[i] = z > 0 && inside[i - GRID] ? i - GRID : -1;
            } else {
                int minimum = Integer.MAX_VALUE;
                for (int j = 0; j < cellCount; j++) {
                    int candidate = cells[j];
                    int dx = x - candidate % GRID;
                    int dz = z - candidate / GRID;
                    int squared = dx * dx + dz * dz;
                    if (squared < minimum) {
                        minimum = squared;
                        nearest[i] = candidate;
                    }
                }
            }
        }
    }

    private void updateVolume(float nextRemaining) {
        float loss = remaining - nextRemaining;
        if (loss > 0f) {
            spillBudget = Math.min(18f, spillBudget + loss * 1.9f);
            spillTime = SPILL_WINDOW_SECONDS;
        } else {
            // A score correction can restore liquid without creating an outward splash.
            spillBudget = 0f;
            spillTime = 0f;
            activeStream = null;
        }
        remaining = nextRemaining;
        updateLevel();
        if (level == 0f) {
            clearSurface();
            spillBudget = spillTime = 0f;
            activeStream = null;
        } else if (lateral != 0f || longitudinal != 0f) {
            surfaceMoving = true;
        }
    }

    private void updateLevel() {
        level = levelFor(remaining);
        surfaceRadius = radiusAtHeight(level);
    }

    private void clearSurface() {
        Arrays.fill(height, 0f);
        Arrays.fill(nextHeight, 0f);
        Arrays.fill(eastFlow, 0f);
        Arrays.fill(rearFlow, 0f);
        surfaceMoving = false;
    }

    private void step() {
        if (surfaceMoving) {
            stepSurface();
        }
        stepDroplets();
        stepStreams();
        if (spillTime > 0f) {
            spillTime -= DT;
            if (spillTime <= 0f) {
                spillBudget = 0f;
            }
        }
        if (spillBudget >= (activeStream == null ? 0.65f : 0.04f)) {
            flowOverRim();
        } else {
            activeStream = null;
        }
    }

    private void stepSurface() {
        float radius = surfaceRadius * RADIUS_METERS;
        float pressure = GRAVITY * HEIGHT_METERS / (radius * radius * SPACING);
        float forceX = -lateral / radius;
        // Positive forward acceleration makes the water lag towards the viewer.
        float forceZ = longitudinal / radius;
        float fastestFlow = 0f;
        for (int j = 0; j < cellCount; j++) {
            int i = cells[j];
            int e = east[i];
            int r = rear[i];
            if (e >= 0) {
                eastFlow[i] = FLOW_DRAG * (eastFlow[i] + DT * (forceX - pressure * (height[e] - height[i])));
                fastestFlow = Math.max(fastestFlow, Math.abs(eastFlow[i]));
            }
            if (r >= 0) {
                rearFlow[i] = FLOW_DRAG * (rearFlow[i] + DT * (forceZ - pressure * (height[r] - height[i])));
                fastestFlow = Math.max(fastestFlow, Math.abs(rearFlow[i]));
            }
        }
        float fluxScale = level * DT / SPACING;
        float largestChange = 0f;
        for (int j = 0; j < cellCount; j++) {
            int i = cells[j];
            int w = west[i];
            int f = front[i];
            float divergence = eastFlow[i] - (w < 0 ? 0f : eastFlow[w])
                    + rearFlow[i] - (f < 0 ? 0f : rearFlow[f]);
            float h = height[i];
            float diffusion = heightDifference(east[i], h) + heightDifference(w, h)
                    + heightDifference(rear[i], h) + heightDifference(f, h);
            float change = -fluxScale * divergence + SURFACE_DIFFUSION * diffusion;
            nextHeight[i] = h + change;
            largestChange = Math.max(largestChange, Math.abs(change));
        }
        float[] previous = height;
        height = nextHeight;
        nextHeight = previous;
        boolean wasMoving = surfaceMoving;
        surfaceMoving = fastestFlow > 0.008f || largestChange > 0.00005f;
        if (wasMoving && !surfaceMoving && lateral == 0f && longitudinal == 0f) {
            clearSurface();
        }
    }

    private float heightDifference(int neighbor, float reference) {
        return neighbor < 0 ? 0f : height[neighbor] - reference;
    }

    private float sample(float x, float z) {
        float u = clamp((x + 1f) / SPACING, 0f, GRID - 1);
        float v = clamp((z + 1f) / SPACING, 0f, GRID - 1);
        int col = Math.min((int) u, GRID - 2);
        int row = Math.min((int) v, GRID - 2);
        int i = row * GRID + col;
        float top = lerp(height[nearest[i]], height[nearest[i + 1]], u - col);
        float bottom = lerp(height[nearest[i + GRID]], height[nearest[i + GRID + 1]], u - col);
        return lerp(top, bottom, v - row);
    }

    private void flowOverRim() {
        float highest = activeStream == null ? 1f : 0.998f;
        int crest = -1;
        for (int i = 0; i < RIM_SAMPLES; i++) {
            float y = level + sample(rimX[i], rimZ[i]);
            rimHeight[i] = y;
            if (y > highest) {
                highest = y;
                crest = i;
            }
        }
        if (crest < 0) {
            activeStream = null;
            return;
        }
        float nx = 0f;
        float nz = 0f;
        // Interpolate adjacent wet segments so the sheet follows the crest smoothly around the rim.
        for (int offset = -2; offset <= 2; offset++) {
            int i = (crest + offset + RIM_SAMPLES) % RIM_SAMPLES;
            float weight = Math.max(0f, rimHeight[i] - 0.997f) * (3 - Math.abs(offset));
            nx += rimX[i] * weight;
            nz += rimZ[i] * weight;
        }
        float length = (float) Math.sqrt(nx * nx + nz * nz);
        nx /= length;
        nz /= length;
        if (activeStream != null && nx * activeStream.nx + nz * activeStream.nz < 0.72f) {
            activeStream = null;
        }
        if (activeStream == null) {
            if (streamCount == MAX_STREAMS) {
                return;
            }
            activeStream = streams[streamCount++];
            activeStream.reset(nx, nz);
        }
        SpillStream stream = activeStream;
        nx = lerp(stream.nx, nx, 0.26f);
        nz = lerp(stream.nz, nz, 0.26f);
        length = (float) Math.sqrt(nx * nx + nz * nz);
        stream.nx = nx / length;
        stream.nz = nz / length;

        // Consume only an already-scored visual budget. The surface volume is untouched.
        float strength = clamp((highest - 1f) / 0.12f, 0f, 1f);
        float flux = (15f + 14f * strength) * DT;
        float used = Math.min(spillBudget, flux);
        spillBudget -= used;
        float width = (0.055f + 0.135f * strength) * lerp(0.5f, 1f, used / flux);
        stream.anchor.set(stream.nx * 0.986f, 0.997f, stream.nz * 0.986f, width);
        stream.sampleTime -= DT;
        if (stream.sampleTime <= 0f && stream.count < STREAM_POINTS) {
            sampleStream(stream, width, strength);
            stream.sampleTime = STREAM_SAMPLE_SECONDS;
        }
    }

    private void sampleStream(SpillStream stream, float width, float strength) {
        float nx = stream.nx;
        float nz = stream.nz;
        StreamPoint point = stream.point(stream.count++);
        point.set(nx, 1.001f, nz, width * (0.89f + random() * 0.22f));
        float radial = (0.16f + 0.16f * strength) / RADIUS_METERS;
        float tangential = (random() - 0.5f) * 0.68f;
        point.vx = nx * radial - nz * tangential;
        point.vy = (0.30f + 0.38f * strength + random() * 0.045f) / HEIGHT_METERS;
        point.vz = nz * radial + nx * tangential;
        point.breakAge = 0.078f + point.width * 0.23f;
        // Strong outflow throws a few fine satellites above the coherent sheet.
        if (strength > 0.24f && random() < 0.36f) {
            detachDrop(point, true);
        }
    }

    private void stepStreams() {
        for (int i = 0; i < streamCount;) {
            SpillStream stream = streams[i];
            for (int j = 0; j < stream.count; j++) {
                StreamPoint point = stream.point(j);
                point.fall();
                point.age += DT;
            }
            while (stream.count > 0) {
                StreamPoint tip = stream.point(0);
                if (tip.age < tip.breakAge && tip.y >= -0.06f) {
                    break;
                }
                if (tip.y >= -0.06f) {
                    detachDrop(tip, false);
                    if (tip.width > 0.10f && random() < 0.31f) {
                        detachDrop(tip, true);
                    }
                }
                stream.start = (stream.start + 1) % STREAM_POINTS;
                stream.count--;
            }
            if (stream.count == 0 && stream != activeStream) {
                streams[i] = streams[--streamCount];
                streams[streamCount] = stream;
            } else {
                i++;
            }
        }
    }

    private void detachDrop(StreamPoint source, boolean fine) {
        if (dropletCount == MAX_DROPLETS) {
            return;
        }
        Droplet drop = droplets[dropletCount++];
        drop.x = source.x;
        drop.y = source.y;
        drop.z = source.z;
        float spread = fine ? 2.2f : 0.70f;
        drop.vx = source.vx + (random() - 0.5f) * spread;
        drop.vy = source.vy + random() * (fine ? 2.15f : 0.54f);
        drop.vz = source.vz + (random() - 0.5f) * spread;
        drop.radius = fine ? 0.007f + random() * 0.008f
                : 0.011f + source.width * (0.08f + random() * 0.10f);
        drop.life = 0.16f + random() * 0.10f;
    }

    private void stepDroplets() {
        for (int i = 0; i < dropletCount;) {
            Droplet drop = droplets[i];
            drop.fall();
            drop.life -= DT;
            if (drop.life <= 0f || drop.y < -0.06f) {
                droplets[i] = droplets[--dropletCount];
                droplets[dropletCount] = drop;
            } else {
                i++;
            }
        }
    }

    private float random() {
        randomState ^= randomState << 13;
        randomState ^= randomState >>> 17;
        randomState ^= randomState << 5;
        return (randomState >>> 8) * 0x1.0p-24f;
    }

    private static float levelFor(float remaining) {
        // A frustum's volume is proportional to its top radius cubed minus its base radius cubed.
        float fullRadius = radiusAtHeight(FULL_HEIGHT);
        float baseCubed = BASE_RADIUS * BASE_RADIUS * BASE_RADIUS;
        float fullCubed = fullRadius * fullRadius * fullRadius;
        return Math.max(0f, ((float) Math.cbrt(lerp(baseCubed, fullCubed, remaining / 100f))
                - BASE_RADIUS) / TAPER);
    }

    private static float acceleration(float input) {
        return Float.isFinite(input) ? clamp(input, -12f, 12f) : 0f;
    }

    private static float lerp(float from, float to, float fraction) {
        return from + (to - from) * fraction;
    }

    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }

    private static class BallisticPoint {
        float x, y, z;
        float vx, vy, vz;

        void fall() {
            x += vx * DT;
            y += (vy - 0.5f * VERTICAL_GRAVITY * DT) * DT;
            z += vz * DT;
            vy -= VERTICAL_GRAVITY * DT;
        }
    }

    private static final class Droplet extends BallisticPoint {
        float radius;
        float life;
    }

    private static final class StreamPoint extends BallisticPoint {
        float width;
        float age;
        float breakAge;

        void set(float x, float y, float z, float width) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.width = width;
            age = 0f;
            breakAge = 1f;
        }
    }

    private static final class SpillStream {
        final StreamPoint[] points = new StreamPoint[STREAM_POINTS];
        final StreamPoint anchor = new StreamPoint();
        int start;
        int count;
        float nx;
        float nz;
        float sampleTime;

        SpillStream() {
            for (int i = 0; i < points.length; i++) {
                points[i] = new StreamPoint();
            }
        }

        void reset(float nx, float nz) {
            this.nx = nx;
            this.nz = nz;
            start = count = 0;
            sampleTime = 0f;
        }

        StreamPoint point(int i) {
            return points[(start + i) % points.length];
        }
    }
}
