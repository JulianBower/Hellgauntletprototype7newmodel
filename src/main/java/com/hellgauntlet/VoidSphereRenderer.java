package com.hellgauntlet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** The explosion: same look as the orb (black sphere, dark red glow, small lightning), grown to the blast size. */
public class VoidSphereRenderer extends EntityRenderer<VoidSphereEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(HellGauntletMod.MODID, "textures/entity/void_sphere.png");

    public VoidSphereRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(VoidSphereEntity entity, Frustum frustum, double x, double y, double z) {
        return true; // the sphere is much bigger than the entity's tiny hitbox
    }

    @Override
    public void render(VoidSphereEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light) {
        float age = entity.tickCount + partialTick;
        float radius = VoidSphereEntity.visualRadius(entity.getRadius(), age);

        float opacity = 1.0F;
        float fadeStart = VoidSphereEntity.GROW_TICKS + VoidSphereEntity.HOLD_TICKS;
        if (age > fadeStart) {
            opacity = 1.0F - (age - fadeStart) / VoidSphereEntity.FADE_TICKS;
        }
        int alpha = (int) (Math.max(0.0F, Math.min(1.0F, opacity)) * 255.0F);
        if (alpha <= 0) {
            return;
        }

        int stacks = Math.max(14, Math.min(32, (int) (radius * 3)));
        VertexConsumer core = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
        VoidFx.sphere(core, poseStack.last(), radius, stacks, stacks + 6, alpha);

        VertexConsumer additive = buffer.getBuffer(RenderType.lightning());

        // glow ring that faces the camera and hugs the outline of the sphere
        poseStack.pushPose();
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        float glowWidth = Math.min(radius * 0.9F, 0.4F + radius * 0.25F);
        VoidFx.haloRing(additive, poseStack.last(), radius * 0.97F, radius + glowWidth, (int) (150 * alpha / 255.0F));
        poseStack.popPose();

        // lightning, a new shape every 2 ticks; more of it on bigger spheres
        int arcs = 6 + (int) (radius * 2.0F);
        VoidFx.sphereArcs(additive, poseStack.last(), radius, arcs, entity.getId(), entity.tickCount,
                0.05F + radius * 0.01F, alpha);

        super.render(entity, yaw, partialTick, poseStack, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(VoidSphereEntity entity) {
        return TEXTURE;
    }
}
