package net.venomous.meteoraddon.utils;

/**
 * How much damage an explosion lands on an entity, in vanilla's own units.
 *
 * <p>AutoAnchor scores placements by it, and until now it was scoring them with a made-up
 * formula. The old body returned {@code max(0, 4 * (1 - distance / 6))}: a straight line from 4
 * at the centre to 0 at range 6, for both the target and the player. A respawn anchor does not
 * deal 4 damage and its damage does not fall off linearly, so every placement the module
 * thought was a kill was a guess, and the anti-suicide threshold was comparing a real health
 * pool against a fiction.</p>
 *
 * <p>The reference clients all use the same curve this class implements. Aoba's
 * {@code DamageUtils.anchorDamage} and Autism's {@code AutismExplosionDamage} both derive from
 * vanilla's {@code Explosion}: the blast has a radius twice its power, an entity at distance
 * {@code d} receives a fraction {@code 1 - d/range} of its exposure, and the raw impact feeds
 * through {@code (impact^2 + impact) / 2 * 7 * range + 1}. What survives that is then run through
 * armor, resistance and protection, which is a separate step and not part of this class.</p>
 *
 * <p>Free of Minecraft types so it unit tests on a plain JVM, in the same spirit as
 * {@link RodAimMath} and {@link EntityPredict}.</p>
 */
public final class AnchorDamageMath {
    /** The blast radius is twice the power, per vanilla's {@code Explosion}. */
    public static final double RADIUS_MULTIPLIER = 2.0D;

    /** Vanilla's impact-to-damage coefficient: {@code (impact^2 + impact) / 2 * 7 * range + 1}. */
    public static final double IMPACT_COEFFICIENT = 7.0D;

    /** A respawn anchor detonated at full charge explodes with power 5, matching a nether bed. */
    public static final double RESPAWN_ANCHOR_POWER = 5.0D;

    private AnchorDamageMath() {
    }

    /**
     * The fraction of the blast that reaches an entity, before armor.
     *
     * <p>{@code exposure} is 1.0 for a fully exposed entity and falls toward 0.0 as walls, blocks
     * or other entities occlude it; it is normally produced by raycasting the entity's box and
     * counting the rays that escape, and is a caller concern, not this class's. A caller that has
     * no raycast handy passes 1.0 and gets the unobstructed worst case.</p>
     *
     * @param distance  the distance from the blast centre to the entity, in blocks
     * @param power     the blast power, e.g. {@link #RESPAWN_ANCHOR_POWER}
     * @param exposure  the occlusion fraction in {@code [0, 1]}
     * @return the impact in {@code [0, 1]}, or 0 when the entity is outside the blast radius
     */
    public static double impact(double distance, double power, double exposure) {
        if (power <= 0.0D) {
            return 0.0D;
        }

        double range = power * RADIUS_MULTIPLIER;
        if (distance >= range) {
            return 0.0D;
        }

        double decay = 1.0D - distance / range;
        return clamp01(exposure * decay);
    }

    /**
     * The raw damage an entity takes from an unobstructed blast, before armor.
     *
     * <p>This is the value the reference clients hand to the reduction pipeline. It is exposed
     * separately from {@link #impact} so callers can score a placement without committing to a
     * raycast: pass {@code exposure = 1.0} for the worst case, or a measured value for the real
     * one.</p>
     *
     * @param distance  the distance from the blast centre to the entity, in blocks
     * @param power     the blast power
     * @param exposure  the occlusion fraction in {@code [0, 1]}
     * @return raw damage, 0 when the entity is outside the blast radius
     */
    public static double rawDamage(double distance, double power, double exposure) {
        double impact = impact(distance, power, exposure);
        if (impact <= 0.0D) {
            return 0.0D;
        }

        double range = power * RADIUS_MULTIPLIER;
        return (impact * impact + impact) / 2.0D * IMPACT_COEFFICIENT * range + 1.0D;
    }

    /**
     * Whether an entity at the given distance is inside the blast radius at all.
     *
     * @param distance the distance from the blast centre to the entity, in blocks
     * @param power    the blast power
     * @return true when the entity can take damage from this blast
     */
    public static boolean withinRange(double distance, double power) {
        return power > 0.0D && distance < power * RADIUS_MULTIPLIER;
    }

    /**
     * The raw damage from a fully detonated respawn anchor, unobstructed.
     *
     * <p>Convenience for the common case: AutoAnchor only ever scores respawn anchors, so the
     * power is a constant rather than a parameter here.</p>
     */
    public static double respawnAnchorDamage(double distance) {
        return rawDamage(distance, RESPAWN_ANCHOR_POWER, 1.0D);
    }

    private static double clamp01(double value) {
        if (value < 0.0D) {
            return 0.0D;
        }
        if (value > 1.0D) {
            return 1.0D;
        }
        return value;
    }
}