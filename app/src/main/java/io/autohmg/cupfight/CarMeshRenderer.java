package io.autohmg.cupfight;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Projects the shared car mesh and composites its depth-tested image into the parked scene. */
final class CarMeshRenderer {
    private static final String OBJ = "cupfight_car.obj";
    private static final String MTL = "cupfight_car.mtl";
    private static final float CAMERA_PITCH = (float) Math.toRadians(29);
    private static final float COS_PITCH = (float) Math.cos(CAMERA_PITCH);
    private static final float SIN_PITCH = (float) Math.sin(CAMERA_PITCH);
    private static final float CAMERA_DISTANCE = 7.8f;
    private static final float TARGET_HEIGHT = 0.56f;
    private static final float HALF_WIDTH = 1.10f;
    private static final int RASTER_WIDTH = 384;
    private static final int TRAFFIC_RASTER_WIDTH = 112;
    private static final int RASTER_PADDING = 2;
    private static final Vector KEY_LIGHT = new Vector(-0.52f, 0.82f, 0.46f).normalized();
    private static final Vector FILL_LIGHT = new Vector(0.74f, 0.45f, -0.38f).normalized();

    private static boolean attemptedLoad;
    private static Model sharedModel;
    private static final Model[] TRAFFIC_MODELS = new Model[TrafficStyle.values().length];
    private static final int MAX_TRAFFIC_POSES = 96;
    private static final Map<TrafficPose, TrafficMesh> TRAFFIC_CACHE =
            new LinkedHashMap<>(MAX_TRAFFIC_POSES, 0.75f, true);

    private final Model model;
    private final int rasterStride;
    private final float pixelsPerUnit;
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect bitmapSource = new Rect();
    private final RectF bitmapDestination = new RectF();
    private final RectF roadDestination = new RectF();
    private final float[] screenX;
    private final float[] screenY;
    private final float[] depth;
    private final int[] drawOrder;
    private final int[] colors;
    private int[] pixels;
    private float[] zBuffer;
    private Bitmap image;

    private float yaw = Float.NaN;
    private float yawSin;
    private float yawCos;
    private float zoom;
    private float centerX;
    private float bottomY;
    private float bodyHeight;
    private int visibleCount;
    private boolean lightingDirty;
    private Vector viewDirection;
    private Vector specularDirection;
    private MaterialLook lastLook;
    private float lastSeconds;
    private RoadVehicleProjection roadCamera;
    private float roadTop;

    static synchronized CarMeshRenderer create(AssetManager assets) {
        Model model = loadSharedModel(assets);
        return model == null ? null : new CarMeshRenderer(model, RASTER_WIDTH);
    }

    static synchronized TrafficMesh traffic(AssetManager assets, TrafficStyle style, float lane,
                                            float distance, float cameraHeight) {
        TrafficPose pose = new TrafficPose(style, Math.round(lane * 1_000f),
                Math.round(distance * 2f), Math.round(cameraHeight * 100f));
        TrafficMesh cached = TRAFFIC_CACHE.get(pose);
        if (cached != null) return cached;
        Model model = loadSharedModel(assets);
        if (model == null) return null;

        Model trafficModel = TRAFFIC_MODELS[style.ordinal()];
        if (trafficModel == null) {
            trafficModel = trafficModel(model, style);
            TRAFFIC_MODELS[style.ordinal()] = trafficModel;
        }
        CarMeshRenderer renderer = new CarMeshRenderer(trafficModel, TRAFFIC_RASTER_WIDTH);
        RoadVehicleProjection projection = new RoadVehicleProjection(pose.lane / 1_000f,
                pose.distance / 2f, pose.height / 100f);
        float top = renderer.prepareRoad(projection, 0f);
        renderer.updateRaster(Collections.emptySet(), 0f);
        RectF destination = renderer.bitmapDestination;
        RectF onRoad = new RectF(destination.left / renderer.zoom + renderer.centerX,
                (destination.top - 0.06f) / renderer.zoom + renderer.bottomY,
                destination.right / renderer.zoom + renderer.centerX,
                (destination.bottom - 0.06f) / renderer.zoom + renderer.bottomY);
        float[][] contacts = new float[4][];
        int wheel = 0;
        for (int side : new int[]{-1, 1}) {
            for (float axle : new float[]{-1.36f, 1.34f}) {
                float x = side * (axle < 0 ? 0.867f : 0.895f) * style.width;
                float z = axle * style.length;
                contacts[wheel++] = projection.groundQuad(x - 0.13f, z - 0.20f,
                        x + 0.13f, z + 0.20f);
            }
        }
        cached = new TrafficMesh(renderer.image, renderer.bitmapSource, onRoad, -top,
                projection.groundQuad(-1.08f * style.width, -1.95f * style.length,
                        1.08f * style.width, 1.95f * style.length),
                projection.groundQuad(-0.90f * style.width, -1.68f * style.length,
                        0.90f * style.width, 1.68f * style.length), contacts);
        TRAFFIC_CACHE.put(pose, cached);
        if (TRAFFIC_CACHE.size() > MAX_TRAFFIC_POSES) {
            TRAFFIC_CACHE.remove(TRAFFIC_CACHE.keySet().iterator().next());
        }
        return cached;
    }

    private record TrafficPose(TrafficStyle style, int lane, int distance, int height) {}

    private static Model loadSharedModel(AssetManager assets) {
        if (!attemptedLoad) {
            attemptedLoad = true;
            try {
                sharedModel = readModel(assets);
            } catch (IOException | IllegalArgumentException | IndexOutOfBoundsException e) {
                Log.w("CupFightCar", "Unable to load vehicle mesh", e);
            }
        }
        return sharedModel;
    }

    private CarMeshRenderer(Model model, int rasterWidth) {
        this.model = model;
        rasterStride = rasterWidth + 2 * RASTER_PADDING;
        pixelsPerUnit = rasterWidth / (2f * HALF_WIDTH);
        screenX = new float[model.vertices.length];
        screenY = new float[model.vertices.length];
        depth = new float[model.vertices.length];
        drawOrder = new int[model.faces.length];
        colors = new int[model.faces.length * 3];
    }

    float prepare(float degrees) {
        if (roadCamera == null && degrees == yaw) return bodyHeight;
        roadCamera = null;
        yaw = degrees;
        float radians = (float) Math.toRadians(degrees);
        yawSin = (float) Math.sin(radians);
        yawCos = (float) Math.cos(radians);
        viewDirection = new Vector(-yawSin * COS_PITCH, SIN_PITCH, yawCos * COS_PITCH);
        specularDirection = KEY_LIGHT.plus(viewDirection).normalized();

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < model.vertices.length; i++) {
            Vector vertex = model.vertices[i];
            float cameraX = vertex.x * yawCos + vertex.z * yawSin;
            float cameraZ = vertex.z * yawCos - vertex.x * yawSin;
            float cameraY = vertex.y - TARGET_HEIGHT;
            float toward = cameraZ * COS_PITCH + cameraY * SIN_PITCH;
            float perspective = CAMERA_DISTANCE / (CAMERA_DISTANCE - toward);
            float x = cameraX * perspective;
            float y = (cameraZ * SIN_PITCH - cameraY * COS_PITCH) * perspective;
            screenX[i] = x;
            screenY[i] = y;
            depth[i] = perspective / CAMERA_DISTANCE;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        finishProjection(minX, minY, maxX, maxY);
        return bodyHeight;
    }

    float prepareRoad(RoadVehicleProjection projection, float degrees) {
        if (roadCamera != null && roadCamera.lateral == projection.lateral
                && roadCamera.height == projection.height && roadCamera.depth == projection.depth
                && yaw == degrees) return roadTop;
        roadCamera = projection;
        yaw = degrees;
        float radians = (float) Math.toRadians(degrees);
        yawSin = (float) Math.sin(radians);
        yawCos = (float) Math.cos(radians);
        viewDirection = new Vector(-projection.lateral * yawCos - projection.depth * yawSin,
                projection.height, projection.depth * yawCos - projection.lateral * yawSin).normalized();
        specularDirection = KEY_LIGHT.plus(viewDirection).normalized();
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float[] point = new float[2];
        for (int i = 0; i < model.vertices.length; i++) {
            Vector vertex = model.vertices[i];
            float rotatedX = vertex.x * yawCos + vertex.z * yawSin;
            float rotatedZ = vertex.z * yawCos - vertex.x * yawSin;
            projection.project(rotatedX, vertex.y, rotatedZ, point, 0);
            screenX[i] = point[0];
            screenY[i] = point[1];
            depth[i] = projection.inverseDepth(rotatedZ);
            minX = Math.min(minX, point[0]);
            minY = Math.min(minY, point[1]);
            maxX = Math.max(maxX, point[0]);
            maxY = Math.max(maxY, point[1]);
        }
        finishProjection(minX, minY, maxX, maxY);
        roadTop = minY;
        return minY;
    }

    private void finishProjection(float minX, float minY, float maxX, float maxY) {
        zoom = 2f * HALF_WIDTH / Math.max(0.01f, maxX - minX);
        centerX = (minX + maxX) * 0.5f;
        bottomY = maxY;
        bodyHeight = (maxY - minY) * zoom;
        for (int i = 0; i < screenX.length; i++) {
            screenX[i] = (screenX[i] - centerX) * zoom;
            screenY[i] = (screenY[i] - bottomY) * zoom + 0.06f;
        }

        visibleCount = 0;
        for (int i = 0; i < model.faces.length; i++) {
            Face face = model.faces[i];
            int a = face.a;
            int b = face.b;
            int c = face.c;
            float area = (screenX[b] - screenX[a]) * (screenY[c] - screenY[a])
                    - (screenY[b] - screenY[a]) * (screenX[c] - screenX[a]);
            if (area >= -0.000001f) continue;
            drawOrder[visibleCount++] = i;
        }
        lightingDirty = true;
    }

    void project(float x, float y, float z, float[] output, int offset) {
        float cameraX = x * yawCos + z * yawSin;
        float cameraZ = z * yawCos - x * yawSin;
        if (roadCamera != null) {
            roadCamera.project(cameraX, y, cameraZ, output, offset);
            return;
        }
        float cameraY = y - TARGET_HEIGHT;
        float toward = cameraZ * COS_PITCH + cameraY * SIN_PITCH;
        float perspective = CAMERA_DISTANCE / (CAMERA_DISTANCE - toward);
        output[offset] = (cameraX * perspective - centerX) * zoom;
        output[offset + 1] = ((cameraZ * SIN_PITCH - cameraY * COS_PITCH) * perspective - bottomY)
                * zoom + 0.06f;
    }

    void draw(Canvas canvas, Set<Cosmetic> equipment, float seconds) {
        updateRaster(equipment, seconds);
        if (image == null) return;
        RectF destination = bitmapDestination;
        if (roadCamera != null) {
            roadDestination.set(destination.left / zoom + centerX,
                    (destination.top - 0.06f) / zoom + bottomY,
                    destination.right / zoom + centerX,
                    (destination.bottom - 0.06f) / zoom + bottomY);
            destination = roadDestination;
        }
        canvas.drawBitmap(image, bitmapSource, destination, bitmapPaint);
    }

    private void updateRaster(Set<Cosmetic> equipment, float seconds) {
        MaterialLook look = new MaterialLook(firstEquipped(equipment, Cosmetic.STARFIELD_COAT,
                Cosmetic.CERAMIC_WHITE, Cosmetic.AURORA_PAINT, Cosmetic.EMBER_PAINT),
                firstEquipped(equipment, Cosmetic.PLASMA_WHEELS, Cosmetic.TURBINE_WHEELS),
                firstEquipped(equipment, Cosmetic.COMET_TAIL, Cosmetic.PULSE_TAIL),
                equipment.contains(Cosmetic.AURORA_GRILLE), equipment.contains(Cosmetic.PHOTON_DIFFUSER));
        float lightFrame = (float) Math.floor(seconds * 20f) / 20f;
        if (lightingDirty || !look.equals(lastLook) || (look.animated() && lightFrame != lastSeconds)) {
            float pearlPhase = (float) (lightFrame * Math.PI / 3d);
            float wheelPhase = (float) (lightFrame * Math.PI);
            float tailCenter = (float) Math.sin(lightFrame * Math.PI * 2d / 3d) * 1.25f;
            float sweep = lightFrame % 2.4f / 2.4f;
            for (int i = 0; i < visibleCount; i++) {
                Face face = model.faces[drawOrder[i]];
                colors[i * 3] = shade(face.material, face.normalA, model.vertices[face.a],
                        look, pearlPhase, wheelPhase, tailCenter, sweep);
                colors[i * 3 + 1] = shade(face.material, face.normalB, model.vertices[face.b],
                        look, pearlPhase, wheelPhase, tailCenter, sweep);
                colors[i * 3 + 2] = shade(face.material, face.normalC, model.vertices[face.c],
                        look, pearlPhase, wheelPhase, tailCenter, sweep);
            }
            lastLook = look;
            lastSeconds = lightFrame;
            lightingDirty = false;
            int fleckLight = look.coating == Cosmetic.STARFIELD_COAT
                    ? 46 + Math.round(61f * (0.5f + 0.5f * (float) Math.sin(pearlPhase * 2f))) : 0;
            rasterize(fleckLight);
        }
    }

    private static Cosmetic firstEquipped(Set<Cosmetic> equipment, Cosmetic... choices) {
        for (Cosmetic item : choices) {
            if (equipment.contains(item)) return item;
        }
        return null;
    }

    private record MaterialLook(Cosmetic coating, Cosmetic wheels, Cosmetic tail,
                                boolean grille, boolean diffuser) {
        boolean animated() {
            return wheels != null || tail != null || grille || diffuser
                    || coating == Cosmetic.AURORA_PAINT || coating == Cosmetic.STARFIELD_COAT;
        }
    }

    private void rasterize(int fleckLight) {
        float top = 0.06f - bodyHeight;
        int height = (int) Math.ceil(bodyHeight * pixelsPerUnit) + 2 * RASTER_PADDING;
        if (image == null || height > image.getHeight()) {
            int capacity = ((height + 63) / 64) * 64;
            image = Bitmap.createBitmap(rasterStride, capacity, Bitmap.Config.ARGB_8888);
            pixels = new int[rasterStride * capacity];
            zBuffer = new float[pixels.length];
        }
        int length = rasterStride * height;
        Arrays.fill(pixels, 0, length, 0);
        Arrays.fill(zBuffer, 0, length, Float.NEGATIVE_INFINITY);
        for (int i = 0; i < visibleCount; i++) {
            Face face = model.faces[drawOrder[i]];
            drawTriangle(face, i * 3, top, height, face.material.paint ? fleckLight : 0);
        }
        image.setPixels(pixels, 0, rasterStride, 0, 0, rasterStride, height);
        bitmapSource.set(0, 0, rasterStride, height);
        float padding = RASTER_PADDING / pixelsPerUnit;
        bitmapDestination.set(-HALF_WIDTH - padding, top - padding, HALF_WIDTH + padding,
                top - padding + height / pixelsPerUnit);
    }

    private void drawTriangle(Face face, int colorOffset, float top, int height, int fleckLight) {
        float ax = (screenX[face.a] + HALF_WIDTH) * pixelsPerUnit + RASTER_PADDING;
        float bx = (screenX[face.b] + HALF_WIDTH) * pixelsPerUnit + RASTER_PADDING;
        float cx = (screenX[face.c] + HALF_WIDTH) * pixelsPerUnit + RASTER_PADDING;
        float ay = (screenY[face.a] - top) * pixelsPerUnit + RASTER_PADDING;
        float by = (screenY[face.b] - top) * pixelsPerUnit + RASTER_PADDING;
        float cy = (screenY[face.c] - top) * pixelsPerUnit + RASTER_PADDING;
        float area = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy);
        if (Math.abs(area) < 0.00001f) return;

        int left = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx))));
        int right = Math.min(rasterStride - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
        int topRow = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cy))));
        int bottomRow = Math.min(height - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
        float stepAX = (by - cy) / area;
        float stepAY = (cx - bx) / area;
        float stepBX = (cy - ay) / area;
        float stepBY = (ax - cx) / area;
        float zC = depth[face.c] + face.material.depthBias;
        float zA = depth[face.a] - depth[face.c];
        float zB = depth[face.b] - depth[face.c];

        int colorC = colors[colorOffset + 2];
        int colorA = colors[colorOffset];
        int colorB = colors[colorOffset + 1];
        int red = Color.red(colorC);
        int green = Color.green(colorC);
        int blue = Color.blue(colorC);
        int redA = Color.red(colorA) - red;
        int greenA = Color.green(colorA) - green;
        int blueA = Color.blue(colorA) - blue;
        int redB = Color.red(colorB) - red;
        int greenB = Color.green(colorB) - green;
        int blueB = Color.blue(colorB) - blue;

        // Body-space cells keep the metallic flecks attached as the camera turns.
        Vector va = model.vertices[face.a];
        Vector vb = model.vertices[face.b];
        Vector vc = model.vertices[face.c];
        float u = 68f * (6f + vc.x + 0.63f * vc.y);
        float v = 68f * (6f + vc.z + 0.47f * vc.y);
        float uA = 68f * (va.x - vc.x + 0.63f * (va.y - vc.y));
        float uB = 68f * (vb.x - vc.x + 0.63f * (vb.y - vc.y));
        float vA = 68f * (va.z - vc.z + 0.47f * (va.y - vc.y));
        float vB = 68f * (vb.z - vc.z + 0.47f * (vb.y - vc.y));

        // Reciprocal distance is affine in projected space, including at crossing triangles.
        for (int y = topRow; y <= bottomRow; y++) {
            float wA = stepAX * (left + 0.5f - cx) + stepAY * (y + 0.5f - cy);
            float wB = stepBX * (left + 0.5f - cx) + stepBY * (y + 0.5f - cy);
            int index = y * rasterStride + left;
            for (int x = left; x <= right; x++, index++) {
                if (wA >= -0.00002f && wB >= -0.00002f && wA + wB <= 1.00002f) {
                    float z = zC + wA * zA + wB * zB;
                    if (z >= zBuffer[index]) {
                        zBuffer[index] = z;
                        int r = Math.round(red + wA * redA + wB * redB);
                        int g = Math.round(green + wA * greenA + wB * greenB);
                        int b = Math.round(blue + wA * blueA + wB * blueB);
                        if (fleckLight != 0 && starCell((int) (u + wA * uA + wB * uB),
                                (int) (v + wA * vA + wB * vB))) {
                            r = Math.min(255, r + fleckLight);
                            g = Math.min(255, g + fleckLight);
                            b = Math.min(255, b + fleckLight * 2);
                        }
                        pixels[index] = 0xff000000 | (r << 16) | (g << 8) | b;
                    }
                }
                wA += stepAX;
                wB += stepBX;
            }
        }
    }

    private static boolean starCell(int u, int v) {
        int hash = (u * 374761393) ^ (v * 668265263);
        hash = (hash ^ (hash >>> 13)) * 1274126177;
        return ((hash ^ (hash >>> 16)) & 63) == 0;
    }

    private int shade(Material material, Vector normal, Vector vertex, MaterialLook look,
                      float pearlPhase, float wheelPhase, float tailCenter, float sweep) {
        float red = material.diffuse.x;
        float green = material.diffuse.y;
        float blue = material.diffuse.z;
        float emitRed = material.emission.x;
        float emitGreen = material.emission.y;
        float emitBlue = material.emission.z;
        if (material.paint && look.coating == Cosmetic.AURORA_PAINT) {
            float phase = 0.5f + 0.5f * (float) Math.sin(pearlPhase + vertex.x * 1.3f + vertex.z * 0.52f);
            red = 0.18f + 0.23f * phase;
            green = 0.45f - 0.25f * phase;
            blue = 0.47f + 0.22f * phase;
        } else if (material.paint && look.coating == Cosmetic.EMBER_PAINT) {
            red = 0.58f;
            green = 0.27f;
            blue = 0.13f;
        } else if (material.paint && look.coating == Cosmetic.STARFIELD_COAT) {
            float phase = 0.5f + 0.5f * (float) Math.sin(pearlPhase + vertex.x * 0.90f - vertex.z * 0.54f);
            red = 0.105f + 0.105f * phase;
            green = 0.055f + 0.085f * (1f - phase);
            blue = 0.29f + 0.17f * phase;
        } else if (material.paint && look.coating == Cosmetic.CERAMIC_WHITE) {
            red = 0.72f;
            green = 0.78f;
            blue = 0.83f;
        }
        if (material.wheel && look.wheels == Cosmetic.TURBINE_WHEELS) {
            float arc = 0.5f + 0.5f * (float) Math.sin(wheelPhase + vertex.z * 7f + vertex.y * 5f);
            emitRed += 0.11f * arc;
            emitGreen += 0.57f * arc;
            emitBlue += 0.75f * arc;
        } else if (material.wheel && look.wheels == Cosmetic.PLASMA_WHEELS) {
            float axle = vertex.z < 0f ? -1.36f : 1.34f;
            float spoke = (float) Math.atan2(vertex.y - 0.335f, vertex.z - axle);
            float arc = 0.5f + 0.5f * (float) Math.sin(spoke * 3f + wheelPhase * 2f);
            red += 0.08f * arc;
            emitRed += 0.18f + 0.45f * arc;
            emitGreen += 0.05f + 0.15f * arc;
            emitBlue += 0.27f + 0.62f * arc;
        }
        if (material.tail && look.tail == Cosmetic.PULSE_TAIL) {
            float shine = Math.max(0f, 1f - Math.abs(vertex.x - tailCenter) * 1.9f);
            emitRed += 0.36f * shine;
            emitGreen += 0.36f * shine;
            emitBlue += 0.27f * shine;
        } else if (material.tail && look.tail == Cosmetic.COMET_TAIL) {
            float along = (vertex.x + 0.86f) / 1.72f;
            float lag = (sweep - along + 1f) % 1f;
            float shine = Math.max(0f, 1f - lag * 3.2f);
            emitGreen += 0.68f * shine * shine;
            emitBlue += 0.17f * shine;
        }
        if (material.grille && look.grille) {
            float phase = 0.5f + 0.5f * (float) Math.sin(pearlPhase * 2f + vertex.x * 4.6f);
            emitRed = 0.05f + 0.40f * phase;
            emitGreen = 0.78f - 0.63f * phase;
            emitBlue = 0.68f + 0.24f * phase;
        }
        if (material.diffuser && look.diffuser) {
            float scan = Math.max(0f, 1f - Math.abs(vertex.x - tailCenter) * 2.8f);
            float low = Math.max(0f, 1f - Math.abs(vertex.y - 0.23f) * 9f);
            emitRed += 0.09f * scan * low;
            emitGreen += (0.10f + 0.64f * scan) * low;
            emitBlue += (0.22f + 0.71f * scan) * low;
        }

        float sky = Math.max(0f, normal.y);
        float diffuse = 0.40f + 0.44f * Math.max(0f, normal.dot(KEY_LIGHT))
                + 0.24f * Math.max(0f, normal.dot(FILL_LIGHT)) + 0.14f * sky;
        float specular = 0.55f * (float) Math.pow(Math.max(0f, normal.dot(specularDirection)),
                Math.max(6f, Math.min(48f, material.shininess * 0.24f)));
        float facing = Math.max(0f, normal.dot(viewDirection));
        float reflection = 1f - facing;
        reflection *= reflection;
        // Broad studio lights give the curved shoulders and the glass different reflections.
        float reflectedY = 2f * facing * normal.y - viewDirection.y;
        float reflectedX = 2f * facing * normal.x - viewDirection.x;
        float shoulder = 1f - Math.min(1f, Math.abs(Math.abs(reflectedX) - 0.63f) * 2.2f);
        float studio = material.paint ? 0.20f * shoulder * shoulder * (0.55f + 0.45f * sky) : 0f;
        if (material.glass) studio += 0.15f * Math.max(0f, reflectedY) * (0.70f + 0.30f * shoulder);
        return Color.rgb(channel(red * diffuse + material.specular.x * specular + emitRed
                        + 0.024f * reflection + 0.56f * studio),
                channel(green * diffuse + material.specular.y * specular + emitGreen
                        + 0.040f * reflection + 0.76f * studio),
                channel(blue * diffuse + material.specular.z * specular + emitBlue
                        + 0.057f * reflection + studio));
    }

    private static int channel(float value) {
        return Math.round(Math.max(0f, Math.min(1f, value)) * 255f);
    }

    private static Model trafficModel(Model source, TrafficStyle style) {
        Vector[] vertices = new Vector[source.vertices.length];
        for (int i = 0; i < vertices.length; i++) {
            Vector vertex = source.vertices[i];
            vertices[i] = new Vector(vertex.x * style.width, vertex.y * style.height,
                    vertex.z * style.length);
        }
        Map<Material, Material> repainted = new IdentityHashMap<>();
        Face[] faces = new Face[source.faces.length];
        for (int i = 0; i < faces.length; i++) {
            Face face = source.faces[i];
            Material material = face.material;
            if (material.paint) {
                material = repainted.computeIfAbsent(material, base -> trafficPaint(base, style.finish));
            }
            faces[i] = new Face(face.a, face.b, face.c,
                    trafficNormal(face.normalA, style), trafficNormal(face.normalB, style),
                    trafficNormal(face.normalC, style), material);
        }
        return new Model(vertices, faces);
    }

    private static Vector trafficNormal(Vector normal, TrafficStyle style) {
        return new Vector(normal.x / style.width, normal.y / style.height,
                normal.z / style.length).normalized();
    }

    private static Material trafficPaint(Material base, Vector finish) {
        Material material = new Material("body");
        // Keep the roof's darker finish and the different amounts of gloss on each body surface.
        material.diffuse = new Vector(base.diffuse.x * finish.x / 0.28f,
                base.diffuse.y * finish.y / 0.38f, base.diffuse.z * finish.z / 0.50f);
        material.specular = base.specular;
        material.shininess = base.shininess;
        return material;
    }

    enum TrafficStyle {
        SILVER(1.04f, 1.24f, 0.93f, new Vector(0.57f, 0.65f, 0.72f)),
        COPPER(0.98f, 0.92f, 1.08f, new Vector(0.45f, 0.29f, 0.24f)),
        TEAL(1.03f, 1.09f, 1.05f, new Vector(0.16f, 0.34f, 0.39f)),
        GRAPHITE(0.92f, 1.09f, 0.84f, new Vector(0.29f, 0.33f, 0.42f));

        final float width;
        final float height;
        final float length;
        final Vector finish;

        TrafficStyle(float width, float height, float length, Vector finish) {
            this.width = width;
            this.height = height;
            this.length = length;
            this.finish = finish;
        }
    }

    // The parked traffic poses are shared between the road and its preview. Only the finished image is cached.
    record TrafficMesh(Bitmap image, Rect source, RectF destination, float bodyHeight,
                       float[] softShadow, float[] bodyShadow, float[][] wheelContacts) {
        void draw(Canvas canvas, Paint paint) {
            canvas.drawBitmap(image, source, destination, paint);
        }
    }

    private static Model readModel(AssetManager assets) throws IOException {
        Map<String, Material> materials = readMaterials(assets);
        Material fallback = new Material("body");
        Material material = fallback;
        List<Vector> vertices = new ArrayList<>();
        List<Vector> normals = new ArrayList<>();
        List<Face> faces = new ArrayList<>();
        try (BufferedReader input = open(assets, OBJ)) {
            for (String line; (line = input.readLine()) != null; ) {
                String[] token = tokens(line);
                switch (token[0]) {
                    case "v" -> vertices.add(vector(token));
                    case "vn" -> normals.add(vector(token).normalized());
                    case "usemtl" -> material = materials.getOrDefault(token[1], fallback);
                    case "f" -> {
                        Corner first = Corner.parse(token[1], vertices.size(), normals.size());
                        Corner previous = Corner.parse(token[2], vertices.size(), normals.size());
                        for (int i = 3; i < token.length; i++) {
                            Corner corner = Corner.parse(token[i], vertices.size(), normals.size());
                            Vector a = vertices.get(first.vertex);
                            Vector faceNormal = vertices.get(previous.vertex).minus(a)
                                    .cross(vertices.get(corner.vertex).minus(a)).normalized();
                            faces.add(new Face(first.vertex, previous.vertex, corner.vertex,
                                    first.normalOr(normals, faceNormal), previous.normalOr(normals, faceNormal),
                                    corner.normalOr(normals, faceNormal), material));
                            previous = corner;
                        }
                    }
                    default -> { }
                }
            }
        }
        if (vertices.isEmpty() || faces.isEmpty()) throw new IOException("Empty vehicle model");
        return new Model(vertices.toArray(new Vector[0]), faces.toArray(new Face[0]));
    }

    private static Map<String, Material> readMaterials(AssetManager assets) throws IOException {
        Map<String, Material> materials = new HashMap<>();
        Material material = new Material("unassigned");
        try (BufferedReader input = open(assets, MTL)) {
            for (String line; (line = input.readLine()) != null; ) {
                String[] token = tokens(line);
                switch (token[0]) {
                    case "newmtl" -> {
                        material = new Material(token[1]);
                        materials.put(token[1], material);
                    }
                    case "Kd" -> material.diffuse = vector(token);
                    case "Ks" -> material.specular = vector(token);
                    case "Ke" -> material.emission = vector(token);
                    case "Ns" -> material.shininess = Float.parseFloat(token[1]);
                    default -> { }
                }
            }
        }
        return materials;
    }

    private static BufferedReader open(AssetManager assets, String name) throws IOException {
        return new BufferedReader(new InputStreamReader(assets.open(name), StandardCharsets.UTF_8));
    }

    private static String[] tokens(String line) {
        int comment = line.indexOf('#');
        return (comment < 0 ? line : line.substring(0, comment)).trim().split("\\s+");
    }

    private static Vector vector(String[] token) {
        return new Vector(Float.parseFloat(token[1]), Float.parseFloat(token[2]), Float.parseFloat(token[3]));
    }

    private record Model(Vector[] vertices, Face[] faces) { }

    private record Face(int a, int b, int c, Vector normalA, Vector normalB, Vector normalC,
                        Material material) { }

    private record Corner(int vertex, int normal) {
        static Corner parse(String token, int vertexCount, int normalCount) {
            String[] indices = token.split("/", -1);
            return new Corner(index(indices[0], vertexCount),
                    indices.length >= 3 && !indices[2].isEmpty() ? index(indices[2], normalCount) : -1);
        }

        Vector normalOr(List<Vector> normals, Vector fallback) {
            return normal < 0 ? fallback : normals.get(normal);
        }

        private static int index(String value, int count) {
            int index = Integer.parseInt(value);
            int resolved = index < 0 ? count + index : index - 1;
            if (resolved < 0 || resolved >= count) throw new IllegalArgumentException("Invalid mesh index");
            return resolved;
        }
    }

    private record Vector(float x, float y, float z) {
        Vector minus(Vector other) { return new Vector(x - other.x, y - other.y, z - other.z); }
        Vector plus(Vector other) { return new Vector(x + other.x, y + other.y, z + other.z); }
        Vector cross(Vector other) {
            return new Vector(y * other.z - z * other.y, z * other.x - x * other.z,
                    x * other.y - y * other.x);
        }
        float dot(Vector other) { return x * other.x + y * other.y + z * other.z; }
        Vector normalized() {
            float length = (float) Math.sqrt(dot(this));
            return length > 0.00001f ? new Vector(x / length, y / length, z / length) : this;
        }
    }

    private static final class Material {
        final boolean paint;
        final boolean glass;
        final boolean wheel;
        final boolean tail;
        final boolean grille;
        final boolean diffuser;
        final float depthBias;
        Vector diffuse = new Vector(0.24f, 0.35f, 0.45f);
        Vector specular = new Vector(0.28f, 0.32f, 0.38f);
        Vector emission = new Vector(0f, 0f, 0f);
        float shininess = 40f;

        Material(String name) {
            paint = name.equals("body") || name.equals("roof") || name.equals("mirror");
            glass = name.equals("glass");
            wheel = name.equals("wheel") || name.equals("wheel_hub");
            tail = name.equals("tail_glow");
            grille = name.equals("front_grille") || name.equals("headlamp");
            diffuser = name.equals("diffuser") || name.equals("bright_trim");
            depthBias = tail ? 0.000012f : 0f;
        }
    }
}
