package io.autohmg.cupfight;

import android.content.res.AssetManager;
import java.nio.file.Path;

/** Checks the production 3D projection, using host Android drawing fixtures. */
public final class TrafficDirectionSmoke {
    public static void main(String[] args) {
        AssetManager assets = new AssetManager(Path.of(args[0]));
        CarMeshRenderer renderer = CarMeshRenderer.create(assets);
        if (renderer == null) throw new AssertionError("Missing production car mesh");
        int checks = 0;
        for (int range : new int[]{50, 100, 150, 200}) {
            float zoom = (float) Math.pow(100f / range, 0.30f);
            float width = Math.min(900f * 0.154f, 650f * 0.25f) * zoom;
            float depth = 650f * (0.85f + 0.07f * (zoom - 1f) - 0.28f);
            for (int lane = -2; lane <= 2; lane++) {
                float yaw = CarMeshRenderer.trafficYaw(lane, width, depth);
                renderer.prepare(Math.round(yaw));
                float[] axis = new float[4];
                renderer.project(0, 0.4f, -2f, axis, 0); // Nose.
                renderer.project(0, 0.4f, 2f, axis, 2);  // Rear.
                float dx = axis[0] - axis[2];
                float dy = axis[1] - axis[3];
                if (!(dy < 0) || (lane < 0 && !(dx > 0))
                        || (lane > 0 && !(dx < 0)) || (lane == 0 && Math.abs(dx) > 0.00001f)) {
                    throw new AssertionError("Wrong forward axis: range=" + range
                            + " lane=" + lane + " dx=" + dx + " dy=" + dy);
                }
                checks++;
                for (CarMeshRenderer.TrafficStyle style : CarMeshRenderer.TrafficStyle.values()) {
                    CarMeshRenderer.TrafficMesh mesh = CarMeshRenderer.traffic(assets, style, yaw);
                    if (mesh == null || mesh != CarMeshRenderer.traffic(assets, style, yaw)) {
                        throw new AssertionError("Missing or uncached traffic pose: " + style);
                    }
                    checks++;
                }
            }
        }
        System.out.println("PASS " + checks + " production mesh direction/cache checks");
    }
}
