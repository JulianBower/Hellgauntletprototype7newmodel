package com.hellgauntlet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * One straight black beam. It shoots forward over a few ticks (the red lightning on its edge appears at
 * the moving front) and deletes entities it passes through. Where it hits a block a void ball appears.
 *
 * Single shot: the ball pulses 3 times, then the beam fades.
 * Continuous: like water from a tap. The beam follows the player's aim while they hold the button, with a
 * ball splashing at the end. When they let go, the back end leaves the hand and flies after the front, so
 * the cut-off piece keeps going until it lands and splashes (and keeps flying past its old end if it hadn't
 * hit anything yet).
 */
public class LaserBeamEntity extends Entity {
    public static final int GROW_TICKS = 5;
    public static final int HOLD_TICKS = 3;
    public static final int FADE_TICKS = 8;
    public static final float THICKNESS = 0.3F;

    /** How close to the beam line an entity has to be to get deleted. */
    private static final double HIT_MARGIN = 0.6;
    private static final int PULSES = 3;
    private static final int PULSE_INTERVAL = 4;
    /** Continuous beam: a new ball every this many ticks, and durability paid every 2.5 seconds. */
    private static final int CONTINUOUS_BALL_INTERVAL = 8;
    private static final int CONTINUOUS_COST_INTERVAL = 50;
    /** Held on the same block this long without it vanishing counts as "stuck" (something it can't delete). */
    private static final int STUCK_TICKS = 25;
    /** How far the tip of the beam moves in and out while stuck, in blocks. */
    private static final double VIBRATE_AMPLITUDE = 1.0;
    /** How fast the ends of a continuous beam travel, in blocks per tick: the full range in GROW_TICKS ticks. */
    public static final float STREAM_SPEED = (float) GauntletItem.LASER_RANGE / GROW_TICKS;
    /** A released beam that finds nothing flies at most this far, and the whole thing lives at most this long. */
    private static final double MAX_FLIGHT = 320.0;
    private static final int MAX_STREAM_TICKS = 90;

    private static final EntityDataAccessor<Float> LENGTH =
            SynchedEntityData.defineId(LaserBeamEntity.class, EntityDataSerializers.FLOAT);
    /**
     * Single shot: tick at which the beam starts fading.
     * Continuous: tick at which the button was released and the back end left the hand.
     * -1 while the beam is still going / still held.
     */
    private static final EntityDataAccessor<Integer> FADE_START =
            SynchedEntityData.defineId(LaserBeamEntity.class, EntityDataSerializers.INT);
    /** True for the continuous beam (it has a back end that can leave the hand). */
    private static final EntityDataAccessor<Boolean> STREAM =
            SynchedEntityData.defineId(LaserBeamEntity.class, EntityDataSerializers.BOOLEAN);
    /** True while the continuous beam is held on a spot it can't dig into: the tip then shakes in and out. */
    private static final EntityDataAccessor<Boolean> VIBRATING =
            SynchedEntityData.defineId(LaserBeamEntity.class, EntityDataSerializers.BOOLEAN);
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0F, 0.0F, 0.0F), 0.7F);

    private UUID owner;
    private int ballRadius;
    private int cost;
    private boolean hitBlock;
    private boolean continuous;
    /** Server only: the real distance to the hit block (the synced length is a bit shorter while vibrating). */
    private double trueLength;
    private boolean stuck;
    private BlockPos hitPos;
    private int sameSpotTicks;
    /** Block the last ball was made at, so a stuck beam doesn't keep making balls that fade away. */
    private BlockPos blastSpot;
    /** Released without having hit anything: the front keeps flying until it does. */
    private boolean flying;
    /** Where the back end was last tick, so the deletion sweep has no gaps. */
    private float lastTail;

    public LaserBeamEntity(EntityType<? extends LaserBeamEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Single shot with a fixed length. */
    public void setup(float length, UUID owner, int ballRadius, boolean hitBlock) {
        this.entityData.set(LENGTH, length);
        this.entityData.set(FADE_START, GROW_TICKS + HOLD_TICKS);
        this.owner = owner;
        this.ballRadius = ballRadius;
        this.hitBlock = hitBlock;
        this.continuous = false;
    }

    /** Continuous beam that follows the owner while they keep using the gauntlet. */
    public void setupContinuous(UUID owner, int ballRadius, int cost) {
        this.entityData.set(FADE_START, -1);
        this.owner = owner;
        this.ballRadius = ballRadius;
        this.cost = cost;
        this.continuous = true;
        this.entityData.set(STREAM, true);
    }

    public float getLength() {
        return this.entityData.get(LENGTH);
    }

    public int getFadeStart() {
        return this.entityData.get(FADE_START);
    }

    public boolean isVibrating() {
        return this.entityData.get(VIBRATING);
    }

    public boolean isStream() {
        return this.entityData.get(STREAM);
    }

    /** Continuous beam: how far the front has got after t ticks (it never goes past the end of the beam). */
    public static float streamHead(float length, float t) {
        return Math.min(length, STREAM_SPEED * Math.max(0.0F, t));
    }

    /** Continuous beam: where the back end is. 0 (the hand) while held, then it flies after the front. */
    public static float streamTail(float head, int releaseTick, float t) {
        if (releaseTick < 0) {
            return 0.0F;
        }
        return Math.min(head, STREAM_SPEED * Math.max(0.0F, t - releaseTick));
    }

    /** How far the front of the beam has travelled after t ticks. */
    public static float headLength(float length, float t) {
        return length * Math.max(0.0F, Math.min(1.0F, t / GROW_TICKS));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            clientEffects();
        } else if (this.level() instanceof ServerLevel serverLevel) {
            serverTick(serverLevel);
        }
    }

    private void serverTick(ServerLevel level) {
        if (this.continuous) {
            if (getFadeStart() < 0) {
                followOwner(level);
            }
            tickStream(level);
            return;
        }

        float length = getLength();
        Vec3 dir = this.getLookAngle();
        Vec3 start = this.position();

        // Single shots delete only the part the front has just travelled through
        float prev = headLength(length, this.tickCount - 1);
        float cur = headLength(length, this.tickCount);
        if (cur > prev) {
            deleteAlong(level, start.add(dir.scale(prev)), start.add(dir.scale(cur)));
        }

        if (this.hitBlock && this.tickCount == GROW_TICKS) {
            VoidBlasts.startPulsing(level, start.add(dir.scale(length)), this.owner, this.ballRadius,
                    PULSES, PULSE_INTERVAL);
        }

        int fadeStart = getFadeStart();
        if (fadeStart >= 0 && this.tickCount > fadeStart + FADE_TICKS) {
            this.discard();
        }
    }

    /**
     * Continuous beam. While the button is held it runs from the hand to where it hits. After the button is
     * released the back end leaves the hand and flies after the front, so what is left is a piece of beam
     * that keeps flying until it lands (past its old end too, if it hadn't hit anything) and splashes there.
     */
    private void tickStream(ServerLevel level) {
        int release = getFadeStart();
        if (release >= 0 && this.flying && this.tickCount >= GROW_TICKS) {
            flyOn(level);
        }

        float length = getLength();
        Vec3 dir = this.getLookAngle();
        Vec3 start = this.position();
        float head = streamHead(length, this.tickCount);
        float tail = streamTail(head, release, this.tickCount);

        if (tail < head) {
            // sweep from where the back end was last tick, so a fast moving piece can't skip over things
            deleteAlong(level, start.add(dir.scale(this.lastTail)), start.add(dir.scale(head)));
        }
        this.lastTail = tail;

        // Splashes at the end for as long as stream is still arriving there. The synced length is a little
        // short while vibrating, so the ball goes where the beam really ends.
        double reach = this.trueLength > 0.0 ? this.trueLength : length;
        if (this.hitBlock && tail < head && this.tickCount >= GROW_TICKS
                && (this.tickCount - GROW_TICKS) % CONTINUOUS_BALL_INTERVAL == 0) {
            // On a spot it can't dig into, one ball clears what it can and then the beam just vibrates
            // there instead of making a new ball that fades away every few ticks.
            if (!this.stuck || this.hitPos == null || !this.hitPos.equals(this.blastSpot)) {
                VoidBlasts.start(level, start.add(dir.scale(reach)), this.owner, this.ballRadius, true, false);
                this.blastSpot = this.hitPos;
            }
        }

        if (release >= 0 && ((!this.flying && tail >= head) || this.tickCount > release + MAX_STREAM_TICKS)) {
            this.discard();
        }
    }

    /** The front of a released beam that hadn't hit anything keeps flying until it lands on something. */
    private void flyOn(ServerLevel level) {
        Vec3 dir = this.getLookAngle();
        Vec3 start = this.position();
        double head = getLength();
        Vec3 from = start.add(dir.scale(head));
        Vec3 to = start.add(dir.scale(head + STREAM_SPEED));
        BlockPos toPos = BlockPos.containing(to);
        if (head > MAX_FLIGHT || level.isOutsideBuildHeight(toPos) || !level.hasChunkAt(toPos)) {
            this.flying = false; // out of the world or into land that isn't loaded: it simply ends here
            return;
        }
        BlockHitResult hit = level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.BLOCK) {
            head += from.distanceTo(hit.getLocation());
            this.hitBlock = true;
            this.flying = false;
            this.hitPos = hit.getBlockPos();
            this.blastSpot = this.hitPos;
            this.trueLength = head;
            VoidBlasts.start(level, hit.getLocation(), this.owner, this.ballRadius, true, false);
        } else {
            head += STREAM_SPEED;
        }
        this.entityData.set(LENGTH, (float) head);
    }

    /**
     * Continuous beam: follow the player's aim while they hold the button and pay durability.
     * The moment they let go the beam stops following; tickStream then lets the back end fly after the front.
     */
    private void followOwner(ServerLevel level) {
        Player player = this.owner == null ? null : level.getPlayerByUUID(this.owner);
        ItemStack used = player == null ? ItemStack.EMPTY : player.getUseItem();
        boolean active = player != null && player.isUsingItem()
                && used.getItem() instanceof GauntletItem && GauntletItem.isContinuousLaser(used);

        // Aim while held, and always once at the very start so even a quick tap gets a direction
        if (player != null && (active || getLength() <= 0.0F)) {
            Vec3 look = player.getLookAngle();
            Vec3 start = player.getEyePosition().add(look.scale(0.6)).add(0.0, -0.25, 0.0);
            Vec3 far = start.add(look.scale(GauntletItem.LASER_RANGE));
            BlockHitResult hit = level.clip(new ClipContext(start, far,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            this.hitBlock = hit.getType() == HitResult.Type.BLOCK;
            double length = this.hitBlock ? start.distanceTo(hit.getLocation()) : GauntletItem.LASER_RANGE;
            this.trueLength = length;
            updateStuck(level, hit);
            this.moveTo(start.x, start.y, start.z, player.getYRot(), player.getXRot());

            // Stuck on something it can't delete: the tip moves in and out instead of everything fading away
            double shown = length;
            if (this.stuck) {
                shown = Math.max(0.5, length - VIBRATE_AMPLITUDE * (0.5 + 0.5 * Math.sin(this.tickCount * 2.1)));
            }
            this.entityData.set(LENGTH, (float) shown);
            this.entityData.set(VIBRATING, this.stuck);
        }

        if (active) {
            // the first payment is made by the item when the button is pressed
            if (this.tickCount > 1 && (this.tickCount - 1) % CONTINUOUS_COST_INTERVAL == 0) {
                used.hurtAndBreak(this.cost, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));
            }
            return;
        }

        // Button released (or player gone): the back end leaves the hand and flies after the front.
        // If nothing was hit yet, the front keeps flying along the last aim until it lands on something.
        this.entityData.set(FADE_START, this.tickCount);
        this.entityData.set(VIBRATING, false);
        this.flying = !this.hitBlock && getLength() > 0.0F;
    }

    /**
     * "Stuck" means the beam is held on a spot it can't dig any deeper: the block it hits can't be deleted
     * (bedrock, portal frames...), or it has been on the same block for a while without it disappearing.
     */
    private void updateStuck(ServerLevel level, BlockHitResult hit) {
        if (!this.hitBlock) {
            this.stuck = false;
            this.hitPos = null;
            this.sameSpotTicks = 0;
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (pos.equals(this.hitPos)) {
            this.sameSpotTicks++;
        } else {
            this.hitPos = pos;
            this.sameSpotTicks = 0;
        }
        BlockState state = level.getBlockState(pos);
        this.stuck = VoidBlasts.isUnbreakable(level, pos, state) || this.sameSpotTicks >= STUCK_TICKS;
    }

    private void deleteAlong(ServerLevel level, Vec3 a, Vec3 b) {
        AABB area = new AABB(a, b).inflate(HIT_MARGIN + 1.0);
        for (Entity e : level.getEntities((Entity) null, area, x -> VoidBlasts.canErase(x, this.owner))) {
            AABB box = e.getBoundingBox().inflate(HIT_MARGIN);
            if (box.contains(a) || box.clip(a, b).isPresent()) {
                VoidBlasts.erase(level, e, this.owner);
            }
        }
    }

    /** Red zigzag lightning around the beam, strongest at the moving front. */
    private void clientEffects() {
        float length = getLength();
        int fadeStart = getFadeStart();
        if (length <= 0.0F) {
            return;
        }
        float prev;
        float cur;
        float tail = 0.0F;
        if (isStream()) {
            cur = streamHead(length, this.tickCount);
            prev = streamHead(length, this.tickCount - 1);
            tail = streamTail(cur, fadeStart, this.tickCount);
            if (tail >= cur) {
                return; // the last of the stream has landed
            }
        } else {
            if (fadeStart >= 0 && this.tickCount > fadeStart + FADE_TICKS / 2) {
                return;
            }
            cur = headLength(length, this.tickCount);
            prev = headLength(length, this.tickCount - 1);
        }
        Vec3 dir = this.getLookAngle();
        Vec3 start = this.position();
        Vec3 up = Math.abs(dir.y) > 0.99 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 u = dir.cross(up).normalize();
        Vec3 v = dir.cross(u).normalize();

        boolean growing = cur > prev;
        float from = Math.max(tail, prev - 1.5F);
        float span = cur - tail;

        if (growing) {
            for (int bolt = 0; bolt < 14; bolt++) {
                bolt(start, dir, u, v, from + this.random.nextDouble() * Math.max(0.0F, cur - from), cur);
            }
        }
        if (!growing || isStream()) {
            // steady beam (or the body of a flying piece): some lightning along it, extra at the front
            for (int bolt = 0; bolt < 6; bolt++) {
                bolt(start, dir, u, v, tail + this.random.nextDouble() * span, cur);
            }
            for (int bolt = 0; bolt < 5; bolt++) {
                bolt(start, dir, u, v, Math.max(tail, cur - 2.0) + this.random.nextDouble() * Math.min(2.0, span), cur);
            }
            if (isVibrating()) {
                // sparks flying off the spot the beam is pressing against
                Vec3 tip = start.add(dir.scale(cur));
                for (int i = 0; i < 10; i++) {
                    this.level().addParticle(RED,
                            tip.x + (this.random.nextDouble() - 0.5) * 1.6,
                            tip.y + (this.random.nextDouble() - 0.5) * 1.6,
                            tip.z + (this.random.nextDouble() - 0.5) * 1.6, 0, 0, 0);
                }
            }
        }
    }

    private void bolt(Vec3 start, Vec3 dir, Vec3 u, Vec3 v, double d, double max) {
        double angle = this.random.nextDouble() * Math.PI * 2.0;
        for (int step = 0; step < 6; step++) {
            if (d > max) {
                break;
            }
            double r = THICKNESS + 0.08 + (this.random.nextDouble() - 0.5) * 0.3;
            Vec3 p = start.add(dir.scale(d)).add(u.scale(Math.cos(angle) * r)).add(v.scale(Math.sin(angle) * r));
            this.level().addParticle(RED, p.x, p.y, p.z, 0, 0, 0);
            d += 0.35;
            angle += (this.random.nextDouble() - 0.5) * 0.9;
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LENGTH, 0.0F);
        builder.define(FADE_START, -1);
        builder.define(STREAM, false);
        builder.define(VIBRATING, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
