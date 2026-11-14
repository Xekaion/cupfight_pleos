package io.autohmg.cupfight;

/** Road and vehicle coordinates share a ground plane; x is across, y up, z toward the rear. */
final class RoadVehicleProjection {
    static final float LANE_METERS = 3.6f;
    static final float CAMERA_DEPTH_METERS = 36f;
    final float lateral;
    final float height;
    final float depth;

    RoadVehicleProjection(float lane, float distance, float cameraHeight) {
        lateral = lane * LANE_METERS;
        height = cameraHeight;
        depth = CAMERA_DEPTH_METERS + distance;
        if (!Float.isFinite(lateral) || !Float.isFinite(height) || !Float.isFinite(depth)
                || height <= 0 || depth <= 4f) {
            throw new IllegalArgumentException("Invalid road camera");
        }
    }

    float inverseDepth(float z) {
        return 1f / (depth - z);
    }

    void project(float x, float y, float z, float[] result, int offset) {
        float perspective = depth * inverseDepth(z);
        result[offset] = (lateral + x) * perspective - lateral;
        result[offset + 1] = (height - y) * perspective - height;
    }

    float[] groundQuad(float left, float front, float right, float rear) {
        float[] points = new float[8];
        project(left, 0, front, points, 0);
        project(right, 0, front, points, 2);
        project(right, 0, rear, points, 4);
        project(left, 0, rear, points, 6);
        return points;
    }
}
