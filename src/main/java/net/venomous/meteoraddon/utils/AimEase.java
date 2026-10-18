package net.venomous.meteoraddon.utils;

/**
 * Human-shaped easing for a camera slew.
 *
 * <p>Clamping the remaining distance to a fixed per-tick maximum - the obvious implementation -
 * produces a constant angular velocity followed by a hard stop. That profile is not something a
 * hand produces: real aim accelerates away from rest and decelerates into the target, and the
 * per-tick delta is never twice the same value.</p>
 *
 * <p>Deliberately free of any Minecraft types so the curve can be unit tested on a plain JVM.</p>
 */
public final class AimEase {
    /** Share of the gap between current and desired speed closed each tick. */
    private static final float ACCELERATION = 0.45F;

    /** Largest fraction of {@code maxStep} that may be shaved off by noise. */
    private static final float NOISE_CEILING = 0.25F;

    private AimEase() {
    }

    /**
     * One tick of an eased, slightly noisy slew toward a target.
     *
     * <p>Velocity is carried between calls, so the caller stores it per axis and resets it when
     * the slew is abandoned. Ease-in falls out of the velocity ramp; ease-out falls out of the
     * desired speed shrinking as the target is approached.</p>
     *
     * @param remaining signed distance still to travel, in degrees
     * @param maxStep   ceiling on movement for this tick, in degrees
     * @param velocity  angular velocity carried in from the previous tick, degrees per tick
     * @param capture   distance at which the movement counts as finished, in degrees
     * @param noise     signed jitter in [-1, 1]; pass 0 for a perfectly smooth slew
     * @return the signed step to apply this tick
     */
    public static float step(float remaining, float maxStep, float velocity, float capture, float noise) {
        float magnitude = Math.abs(remaining);

        // Close enough to call it arrived. Snapping the last fraction is deliberate: overshoot
        // would leave the camera pointing away from the block being placed.
        if (magnitude <= capture) {
            return remaining;
        }

        // Desired speed falls off as the target is approached, which is the deceleration.
        float desired = maxStep * Math.min(1.0F, magnitude / capture);
        float next = velocity + (desired - velocity) * ACCELERATION;

        // Noise varies the per-tick delta so the sweep is not a constant rate.
        next += noise * maxStep * NOISE_CEILING;

        // Clamped into [0, magnitude] rather than [-magnitude, magnitude]: noise may vary how far
        // the camera travels each tick, but must never reverse it. A step back toward the origin
        // wastes a tick and leaves the view oscillating around the block being placed.
        next = clamp(next, 0.0F, magnitude);

        return Math.signum(remaining) * next;
    }

    /**
     * The velocity to carry into the next tick.
     *
     * <p>Returned as a magnitude, matching what {@link #step} consumes. It previously returned the
     * signed step, which made the ramp compare a negative velocity against a positive target and
     * collapse toward zero - so a sweep to the left crawled while the same sweep to the right
     * moved normally.</p>
     *
     * <p>Returned separately from the step so the caller can keep it; recomputing it from the
     * step would lose the ramp.</p>
     */
    public static float nextVelocity(float remaining, float maxStep, float velocity, float capture, float noise) {
        return Math.abs(step(remaining, maxStep, velocity, capture, noise));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
