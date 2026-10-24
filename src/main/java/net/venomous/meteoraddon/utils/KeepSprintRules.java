package net.venomous.meteoraddon.utils;

/**
 * The numbers behind keeping your sprint through your own swing.
 *
 * <p>Vanilla punishes every attack the attacker makes: horizontal motion is multiplied by 0.6
 * and sprint is dropped. This is the pure half of softening that - how much of the slowdown
 * survives the slider, with the same one-way range as the slowdown override: the module's
 * business is keeping speed, never losing more of it than vanilla does.</p>
 *
 * <p>Free of Minecraft types so it unit tests on a plain JVM.</p>
 */
public final class KeepSprintRules {
    /** Vanilla's post-attack horizontal multiplier. */
    public static final double VANILLA_ATTACK_MULTIPLIER = 0.6;
    /** The largest slowdown the slider may leave: vanilla's own. */
    public static final double MAX_SLOW_PERCENT = 40.0;

    private KeepSprintRules() {
    }

    /**
     * The horizontal multiplier to restore after an attack.
     *
     * @param slowPercent how much of vanilla's attack slowdown remains, 0 to 40
     * @return the multiplier; 0.6 at the default 40 is exact vanilla motion
     */
    public static float motionMultiplier(double slowPercent) {
        double clamped = Math.max(0.0D, Math.min(MAX_SLOW_PERCENT, slowPercent));
        float multiplier = (float) (1.0D - clamped / 100.0D);

        return (float) Math.max(multiplier, VANILLA_ATTACK_MULTIPLIER);
    }
}
