package net.venomous.meteoraddon.utils;

/**
 * When an auto clicker is allowed to click at all.
 *
 * <p>Free of Minecraft types so it unit-tests on a plain JVM, in the same spirit as {@link AimGuard}
 * and {@link ScaffoldGates}.</p>
 *
 * <h2>Why a clicker needs a "when" as well as a "how fast"</h2>
 *
 * <p>CPS and jitter decide the rhythm. They say nothing about whether clicking is plausible at all.
 * A clicker firing on empty air, or while the player is walking somewhere unrelated, produces a
 * perfectly-timed stream of attacks that all miss - which is a stronger signal than an irregular
 * one, because real misses are irregular.</p>
 *
 * <p>Vape models this with {@code autoclicker.should_trigger_bot}, "Stops clicking if not hovering
 * on entity". Ours had {@code respect-cooldown}, which covers the hit-window half of the idea
 * (attack strength scale) but nothing about whether there is anything to hit.</p>
 */
public final class ClickGuard {
    private ClickGuard() {
    }

    /**
     * Whether clicking is permitted this tick.
     *
     * @param hoveringEntity the crosshair currently resolves to an entity
     * @param requireEntity  enforce "only click when aiming at something"
     */
    public static boolean mayClick(boolean hoveringEntity, boolean requireEntity) {
        if (requireEntity && !hoveringEntity) return false;
        return true;
    }
}
