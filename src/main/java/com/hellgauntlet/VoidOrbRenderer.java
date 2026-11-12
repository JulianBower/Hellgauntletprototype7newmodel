package com.hellgauntlet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * The orb, drawn in code (no model file): a pitch black sphere, a dark red glow that hugs its outline from
 * wherever the camera is, and four small jagged lightning arcs that change shape every 2 ticks.
 */
public class VoidOrbRenderer extends EntityRenderer<VoidOrbEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(HellGauntletMod.MODID, "textures/entity/void_sphere.png");
    private static final float CORE_RADIUS = 0.3F;

    public VoidOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(VoidOrbEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light) {
        poseStack.pushPose();
        poseStack.translate(0.0F, entity.getBbHeight() / 2.0F, 0.0F);

        // 1. core (drawn first; asking for another render type below finishes this batch)
        VertexConsumer core = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
        VoidFx.sphere(core, poseStack.last(), CORE_RADIUS, 12, 18);

        VertexConsumer additive = buffer.getBuffer(RenderType.lightning());

        // 2. glow: a ring that always faces the camera, so it sits right on the sphere's outline
        poseStack.pushPose();
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        VoidFx.haloRing(additive, poseStack.last(), CORE_RADIUS * 0.97F, CORE_RADIUS * 1.9F, 150);
        poseStack.popPose();

        // 3. lightning
        VoidFx.sphereArcs(additive, poseStack.last(), CORE_RADIUS, 4, entity.getId(), entity.tickCount,
                CORE_RADIUS * 0.07F, 255);

        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(VoidOrbEntity entity) {
        return TEXTURE;
    }
}
