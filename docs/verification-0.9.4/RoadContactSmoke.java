package io.autohmg.cupfight;

public final class RoadContactSmoke {
    public static void main(String[] args) {
        int checks = 0;
        for (int range : new int[]{50, 100, 150, 200}) {
            float zoom = (float) Math.pow(100f / range, 0.30f);
            float laneWidth = Math.min(900f * 0.154f, 650f * 0.25f) * zoom;
            float roadDepth = 650f * (0.85f + 0.07f * (zoom - 1f) - 0.28f);
            for (int lane = -2; lane <= 2; lane++) {
                for (float distance : new float[]{9, 17, 47, 149}) {
                    RoadVehicleProjection camera = new RoadVehicleProjection(lane, distance,
                            roadDepth * 3.6f / laneWidth);
                    float scale = 36f / (36f + distance);
                    float unit = laneWidth * scale / 3.6f;
                    for (float x : new float[]{-0.895f, 0.895f}) {
                        for (float z : new float[]{-1.36f, 1.34f}) {
                            float[] point = new float[2];
                            camera.project(x, 0, z, point, 0);
                            float onRoadScale = 36f / (36f + distance - z);
                            float roadX = (lane + x / 3.6f) * laneWidth * onRoadScale;
                            float roadY = roadDepth * onRoadScale;
                            float wheelX = lane * laneWidth * scale + point[0] * unit;
                            float wheelY = roadDepth * scale + point[1] * unit;
                            if (Math.abs(roadX - wheelX) > 0.001f
                                    || Math.abs(roadY - wheelY) > 0.001f) {
                                throw new AssertionError("Tire does not meet road plane");
                            }
                            checks++;
                        }
                    }
                    float[] axis = new float[4];
                    camera.project(0, 0.4f, -2, axis, 0);
                    camera.project(0, 0.4f, 2, axis, 2);
                    float dx = axis[0] - axis[2];
                    if (!(axis[1] < axis[3]) || (lane < 0 && !(dx > 0))
                            || (lane > 0 && !(dx < 0)) || (lane == 0 && Math.abs(dx) > 0.00001f)) {
                        throw new AssertionError("Incorrect projected forward direction");
                    }
                    checks++;
                }
            }
        }
        System.out.println("PASS " + checks + " tire-ground and forward-direction checks");
    }
}
