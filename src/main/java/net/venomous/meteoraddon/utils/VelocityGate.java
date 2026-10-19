package net.venomous.meteoraddon.utils;

/**
 * Situations in which a velocity module should leave knockback alone.
 *
 * <p>Free of Minecraft types so it unit-tests on a plain JVM, in the same spirit as {@link AimGuard},
 * {@link ClickGuard} and {@link ScaffoldGates}.</p>
 *
 * <h2>Why context matters as much as the reduction</h2>
 *
 * <p>Reducing every knockback, every time, unconditionally, is itself a pattern. Real players take
 * full knockback sometimes - in water, and while not even facing the attacker. Vape models this with
 * {@code velocity.water_check} ("Won't reduce knockback if in water"), {@code
 * velocity.only_when_targeting} ("Only reduce knockback while being face to face with opponent")
 * and {@code velocity.chance}. Ours reduced by default with no notion of whether the situation
 * warranted it.</p>
 *
 * <p>Note the ordering: {@code chance} is rolled by the caller and passed in, because the roll has
 * to happen once per packet and this must stay a pure function to be testable.</p>
 */
public final class VelocityGate {
    private VelocityGate() {
    }

    /**
     * Whether the knockback packet should be reduced.
     *
     * @param chanceRoll    the caller rolled the configured chance and it passed
     * @param inWater       player is in water
     * @param notFacing     player is not facing the opponent
     * @param waterCheck    enforce "leave it alone in water"
     * @param whenTargeting enforce "leave it alone unless face to face"
     */
    public static boolean shouldReduce(boolean chanceRoll, boolean inWater, boolean notFacing,
                                       boolean waterCheck, boolean whenTargeting) {
        if (!chanceRoll) return false;
        if (waterCheck && inWater) return false;
        if (whenTargeting && notFacing) return false;
        return true;
    }

    /**
     * Renders a configured percentage as a roll.
     *
     * @param percent 0-100
     * @param roll    a value in [0, 100) produced by the caller's RNG
     */
    public static boolean rollPassed(int percent, int roll) {
        if (percent >= 100) return true;
        if (percent <= 0) return false;
        return roll < percent;
    }
}