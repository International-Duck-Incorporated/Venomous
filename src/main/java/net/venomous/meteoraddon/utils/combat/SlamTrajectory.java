package net.venomous.meteoraddon.utils.combat;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Slam trajectory math helper (MaceKill).
 */
public final class SlamTrajectory {

    private SlamTrajectory() {}

    /**
     * Computes launch velocity to reach the target height.
     *
     * @param delta vector from player to above-target position
     * @param velocitySpeed multiplier for all velocity
     * @param upwardSpeed base upward velocity during launch phase
     * @return the computed launch velocity (horizontal + vertical)
     */
    public static Vec3 computeLaunchVelocity(Vec3 delta, double velocitySpeed, double upwardSpeed) {
        double horizontalDist = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double yVelocity = Mth.clamp(delta.y * 0.35, 0.25, upwardSpeed * velocitySpeed);
        double horizontalVelocity = Math.min(horizontalDist * 0.35, Math.max(0.15, upwardSpeed * 0.35) * velocitySpeed);
        Vec3 horizontalMotion = horizontalDist > 0.05 ? delta.normalize().multiply(horizontalVelocity, 0, horizontalVelocity) : Vec3.ZERO;
        return new Vec3(horizontalMotion.x, yVelocity, horizontalMotion.z);
    }

    /**
     * Computes slam downward velocity.
     *
     * @param delta vector from player to target center
     * @param velocitySpeed multiplier for all velocity
     * @param downwardSpeed base downward velocity during slam phase
     * @return the computed slam velocity (horizontal + vertical)
     */
    public static Vec3 computeSlamVelocity(Vec3 delta, double velocitySpeed, double downwardSpeed) {
        double horizontalDist = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double yVelocity = -Math.max(1.0, downwardSpeed * velocitySpeed);
        double horizontalVelocity = Math.min(horizontalDist * 0.45, Math.max(0.2, downwardSpeed * 0.25) * velocitySpeed);
        Vec3 horizontalMotion = horizontalDist > 0.05 ? delta.normalize().multiply(horizontalVelocity, 0, horizontalVelocity) : Vec3.ZERO;
        return new Vec3(horizontalMotion.x, yVelocity, horizontalMotion.z);
    }

    /**
     * Clamps the above-target position to world height limits.
     */
    public static Vec3 clampAboveTarget(Vec3 targetCenter, double aboveHeight, net.minecraft.world.level.Level level) {
        double ceiling = level.getMaxY() - 1;
        double y = Mth.clamp(targetCenter.y + aboveHeight, level.getMinY() + 1, ceiling);
        return new Vec3(targetCenter.x, y, targetCenter.z);
    }

    /**
     * Computes max launch ticks based on height and speed.
     */
    public static int getMaxLaunchTicks(double aboveHeight, double upwardSpeed) {
        return Math.max(20, (int) Math.ceil((aboveHeight + 8.0) / Math.max(0.25, upwardSpeed) * 8.0));
    }

    /**
     * Computes max slam ticks based on height and speed.
     */
    public static int getMaxSlamTicks(double aboveHeight, double downwardSpeed) {
        return Math.max(20, (int) Math.ceil((aboveHeight + 16.0) / Math.max(1.0, downwardSpeed) * 8.0));
    }
}