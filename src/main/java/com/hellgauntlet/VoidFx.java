package com.hellgauntlet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Drawing helpers shared by the orb and the laser: black core, dark red glow, small jagged lightning. */
public final class VoidFx {
    /** Dark red used for the glow and the lightning. */
    public static final int RED_R = 140;
    public static final int RED_G = 0;
    public static final int RED_B = 20;

    private VoidFx() {
    }

    /** Pitch black, fullbright latitude/longitude sphere. Use with an entityTranslucent render type. */
    public static void sphere(VertexConsumer vc, PoseStack.Pose pose, float radius, int stacks, int slices) {
        sphere(vc, pose, radius, stacks, slices, 255);
    }

    public static void sphere(VertexConsumer vc, PoseStack.Pose pose, float radius, int stacks, int slices, int alpha) {
        for (int i = 0; i < stacks; i++) {
            double phi0 = Math.PI * i / stacks;
            double phi1 = Math.PI * (i + 1) / stacks;
            for (int j = 0; j < slices; j++) {
                double t0 = 2.0 * Math.PI * j / slices;
                double t1 = 2.0 * Math.PI * (j + 1) / slices;
                blackVertex(vc, pose, radius, phi0, t0, alpha);
                blackVertex(vc, pose, radius, phi0, t1, alpha);
                blackVertex(vc, pose, radius, phi1, t1, alpha);
                blackVertex(vc, pose, radius, phi1, t0, alpha);
            }
        }
    }

    private static void blackVertex(VertexConsumer vc, PoseStack.Pose pose, float radius, double phi, double theta, int alpha) {
        float nx = (float) (Math.sin(phi) * Math.cos(theta));
        float ny = (float) Math.cos(phi);
        float nz = (float) (Math.sin(phi) * Math.sin(theta));
        vc.addVertex(pose, nx * radius, ny * radius, nz * radius)
                .setColor(0, 0, 0, alpha)
                .setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }

    /**
     * Glow that always hugs the outline of the sphere as seen from the camera: a flat ring that faces the camera
     * (the pose must already be rotated to face it), strongest at the edge of the sphere and fading outwards.
     * Use with the additive lightning render type.
     */
    public static void haloRing(VertexConsumer vc, PoseStack.Pose pose, float inner, float outer, int alpha) {
        int n = 28;
        for (int i = 0; i < n; i++) {
            double a0 = 2.0 * Math.PI * i / n;
            double a1 = 2.0 * Math.PI * (i + 1) / n;
            Vec3 i0 = new Vec3(Math.cos(a0) * inner, Math.sin(a0) * inner, 0.0);
            Vec3 i1 = new Vec3(Math.cos(a1) * inner, Math.sin(a1) * inner, 0.0);
            Vec3 o0 = new Vec3(Math.cos(a0) * outer, Math.sin(a0) * outer, 0.0);
            Vec3 o1 = new Vec3(Math.cos(a1) * outer, Math.sin(a1) * outer, 0.0);
            colored(vc, pose, i0, alpha);
            colored(vc, pose, o0, 0);
            colored(vc, pose, o1, 0);
            colored(vc, pose, i1, alpha);
            // the lightning render type culls back faces, so draw the other side as well
            colored(vc, pose, i1, alpha);
            colored(vc, pose, o1, 0);
            colored(vc, pose, o0, 0);
            colored(vc, pose, i0, alpha);
        }
    }

    /** Open tube (no end caps) from `back` to `front` along dir, for the glow of the laser. */
    public static void haloTube(VertexConsumer vc, PoseStack.Pose pose, Vec3 dir, Vec3 u, Vec3 v,
                                double back, double front, float radius, int alpha) {
        int sides = 8;
        for (int i = 0; i < sides; i++) {
            double a0 = 2.0 * Math.PI * i / sides;
            double a1 = 2.0 * Math.PI * (i + 1) / sides;
            Vec3 n0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0))).scale(radius);
            Vec3 n1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1))).scale(radius);
            Vec3 b = dir.scale(back);
            Vec3 f = dir.scale(front);
            Vec3 p0 = n0.add(b);
            Vec3 p1 = n0.add(f);
            Vec3 p2 = n1.add(f);
            Vec3 p3 = n1.add(b);
            colored(vc, pose, p0, alpha);
            colored(vc, pose, p1, alpha);
            colored(vc, pose, p2, alpha);
            colored(vc, pose, p3, alpha);
            colored(vc, pose, p3, alpha);
            colored(vc, pose, p2, alpha);
            colored(vc, pose, p1, alpha);
            colored(vc, pose, p0, alpha);
        }
    }

    /** `count` small arcs close to the surface of a sphere. New random shape every 2 ticks. */
    public static void sphereArcs(VertexConsumer vc, PoseStack.Pose pose, float radius, int count, long seed, int tick,
                                  float width, int alpha) {
        long step = tick / 2;
        for (int arc = 0; arc < count; arc++) {
            RandomSource rnd = RandomSource.create(seed * 341873128712L + step * 132897987541L + arc * 7919L);
            Vec3 a = randomDir(rnd);
            Vec3 tangent = a.cross(randomDir(rnd)).normalize();
            double span = 0.8 + rnd.nextDouble() * 0.7; // angle covered along the surface
            int segments = Math.max(6, Math.min(24, (int) (radius * span / 0.6)));
            double lift = Math.min(radius * 0.08, 0.3);
            double wobble = Math.min(radius * 0.18, 0.45);
            List<Vec3> pts = new ArrayList<>();
            for (int i = 0; i <= segments; i++) {
                double t = (double) i / segments;
                double ang = (t - 0.5) * span;
                Vec3 dir = a.scale(Math.cos(ang)).add(tangent.scale(Math.sin(ang)));
                double r = radius + lift + rnd.nextDouble() * Math.min(radius * 0.3, 0.6);
                Vec3 jitter = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5)
                        .scale(wobble);
                pts.add(dir.scale(r).add(i == 0 || i == segments ? Vec3.ZERO : jitter));
            }
            polyline(vc, pose, pts, width, alpha);
        }
    }

    /** `count` small jagged arcs wrapped around a straight beam between back and front. New shape every 2 ticks. */
    public static void beamArcs(VertexConsumer vc, PoseStack.Pose pose, Vec3 dir, Vec3 u, Vec3 v,
                                double back, double front, float beamRadius, int count, long seed, int tick) {
        long step = tick / 2;
        double len = front - back;
        if (len <= 0.0) {
            return;
        }
        int pieces = Math.max(2, Math.min(48, (int) (len / 1.2)));
        for (int arc = 0; arc < count; arc++) {
            RandomSource rnd = RandomSource.create(seed * 341873128712L + step * 132897987541L + arc * 7919L);
            double angle = rnd.nextDouble() * Math.PI * 2.0;
            List<Vec3> pts = new ArrayList<>();
            for (int i = 0; i <= pieces; i++) {
                double d = back + len * i / pieces;
                angle += (rnd.nextDouble() - 0.5) * 1.6;
                double r = beamRadius * (1.25 + rnd.nextDouble() * 0.9);
                pts.add(dir.scale(d).add(u.scale(Math.cos(angle) * r)).add(v.scale(Math.sin(angle) * r)));
            }
            polyline(vc, pose, pts, beamRadius * 0.12F, 255);
        }
    }

    private static Vec3 randomDir(RandomSource rnd) {
        for (int i = 0; i < 8; i++) {
            Vec3 d = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian());
            if (d.lengthSqr() > 1.0E-4) {
                return d.normalize();
            }
        }
        return new Vec3(0.0, 1.0, 0.0);
    }

    /** A line of two crossed thin quads per segment, so it is visible from every side. */
    private static void polyline(VertexConsumer vc, PoseStack.Pose pose, List<Vec3> pts, float width, int alpha) {
        for (int i = 0; i + 1 < pts.size(); i++) {
            Vec3 a = pts.get(i);
            Vec3 b = pts.get(i + 1);
            Vec3 d = b.subtract(a);
            if (d.lengthSqr() < 1.0E-8) {
                continue;
            }
            d = d.normalize();
            Vec3 ref = Math.abs(d.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
            Vec3 s = d.cross(ref).normalize().scale(width);
            Vec3 t = d.cross(s).normalize().scale(width);
            quadBoth(vc, pose, a.add(s), b.add(s), b.subtract(s), a.subtract(s), alpha);
            quadBoth(vc, pose, a.add(t), b.add(t), b.subtract(t), a.subtract(t), alpha);
        }
    }

    private static void quadBoth(VertexConsumer vc, PoseStack.Pose pose, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int alpha) {
        colored(vc, pose, p0, alpha);
        colored(vc, pose, p1, alpha);
        colored(vc, pose, p2, alpha);
        colored(vc, pose, p3, alpha);
        colored(vc, pose, p3, alpha);
        colored(vc, pose, p2, alpha);
        colored(vc, pose, p1, alpha);
        colored(vc, pose, p0, alpha);
    }

    private static void colored(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, int alpha) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(RED_R, RED_G, RED_B, alpha);
    }
}
