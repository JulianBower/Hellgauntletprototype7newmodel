package com.hellgauntlet;

/**
 * Modes for the normal orb. Shift + right click switches between them.
 * NORMAL is charged by holding right click: the longer, the bigger (see charge helpers below).
 * ENTITY_ONLY fires instantly as before.
 */
public enum BlastMode {
    NORMAL("Normal sphere (hold to charge)", 3, true),
    ENTITY_ONLY("Entity only", 5, false); // radius is worked out from the entity that gets hit

    /** Ticks of holding for a full charge (8 seconds). */
    public static final int FULL_CHARGE_TICKS = 160;
    /** Radius of a fully charged normal orb. A quick tap gives radius 0: just the thing it hits. */
    public static final int MAX_RADIUS = 8;
    public static final int MIN_COST = 3;
    public static final int MAX_COST = 10;

    public final String displayName;
    public final boolean deleteBlocks;
    /** Durability used up per shot (for NORMAL see costFor). */
    public final int durabilityCost;

    BlastMode(String displayName, int durabilityCost, boolean deleteBlocks) {
        this.displayName = displayName;
        this.durabilityCost = durabilityCost;
        this.deleteBlocks = deleteBlocks;
    }

    public static float fraction(int chargeTicks) {
        return Math.max(0.0F, Math.min(1.0F, chargeTicks / (float) FULL_CHARGE_TICKS));
    }

    public static int radiusFor(int chargeTicks) {
        return Math.round(MAX_RADIUS * fraction(chargeTicks));
    }

    public static int costFor(int chargeTicks) {
        return MIN_COST + Math.round((MAX_COST - MIN_COST) * fraction(chargeTicks));
    }
}
