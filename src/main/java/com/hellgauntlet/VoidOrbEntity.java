package com.hellgauntlet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class VoidOrbEntity extends ThrowableItemProjectile {
    private static final int MAX_LIFETIME = 200;

    private BlastMode mode = BlastMode.NORMAL;
    private int chargedRadius = 0;

    public VoidOrbEntity(EntityType<? extends VoidOrbEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public VoidOrbEntity(Level level, LivingEntity owner) {
        super(ModEntities.VOID_ORB.get(), owner, level);
        this.setNoGravity(true);
    }

    public void setMode(BlastMode mode) {
        this.mode = mode;
    }

    /** Blast radius of a charged normal orb (0 = only what it touches). */
    public void setChargedRadius(int radius) {
        this.chargedRadius = radius;
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.VOID_ORB.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount > MAX_LIFETIME) {
            this.discard();
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 center = result.getLocation();
        int radius = this.chargedRadius;
        boolean blocks = this.mode.deleteBlocks;

        if (this.mode == BlastMode.ENTITY_ONLY) {
            if (result instanceof EntityHitResult entityHit) {
                // Just big enough to cover whatever was hit
                Entity target = entityHit.getEntity();
                radius = (int) Math.ceil(Math.max(target.getBbWidth(), target.getBbHeight()) / 2.0) + 1;
                radius = Mth.clamp(radius, 2, 12);
                center = target.getBoundingBox().getCenter();
            } else {
                radius = 1; // hit a block: only a tiny puff
            }
        }

        VoidBlasts.start(serverLevel, center, this.getOwner(), radius, blocks);
        this.discard();
    }
}
