package com.hellgauntlet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Visual only: a black sphere with a dark red glow and small lightning (drawn by VoidSphereRenderer). It grows fast, holds a moment, then fades out. */
public class VoidSphereEntity extends Entity {
    public static final int GROW_TICKS = 4;
    public static final int HOLD_TICKS = 2;
    public static final int FADE_TICKS = 8;
    public static final int TOTAL_TICKS = GROW_TICKS + HOLD_TICKS + FADE_TICKS;

    private static final EntityDataAccessor<Integer> RADIUS =
            SynchedEntityData.defineId(VoidSphereEntity.class, EntityDataSerializers.INT);

    public VoidSphereEntity(EntityType<? extends VoidSphereEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public int getRadius() {
        return this.entityData.get(RADIUS);
    }

    public void setRadius(int radius) {
        this.entityData.set(RADIUS, radius);
    }

    /** Radius of the drawn sphere after the given age in ticks (eases out quickly). */
    public static float visualRadius(int maxRadius, float age) {
        float x = Math.min(1.0F, age / GROW_TICKS);
        float eased = 1.0F - (1.0F - x) * (1.0F - x);
        return maxRadius * eased + 0.5F;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount > TOTAL_TICKS) {
            this.discard();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 1);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
