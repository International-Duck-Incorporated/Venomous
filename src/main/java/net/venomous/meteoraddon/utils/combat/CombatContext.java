package net.venomous.meteoraddon.utils.combat;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Shared combat utilities extracted from KillAura, Aimbot, MaceKill, and TriggerBot.
 * <p>
 * Behavior is intentionally identical to the original hand-written loops; first-seen wins ties
 * using strict {@code <} score comparison to match the original ordering.
 */
public final class CombatContext {

    /** Priority ordering for target selection. Lower ordinal = higher priority. */
    public enum TargetPriority {
        NEAREST,
        LOWEST_HEALTH
    }

    /**
     * Finds the best target within range using the provided filter and priority.
     * <p>
     * Iterates the given entities, applies the filter predicate, and selects the
     * highest-priority candidate. Ties are broken by strict {@code <} score comparison
     * (first-seen wins).
     *
     * @param entities   iterable of entities to search (typically mc.level.entitiesForRendering())
     * @param player     the player entity
     * @param range      maximum targeting range
     * @param filter     predicate that returns true for acceptable targets
     * @param priority   the {@link TargetPriority} to use for selection
     * @param <T>        the entity type
     * @return the best matching entity, or empty if none found
     */
    public static <T extends Entity> Optional<T> findBestEntity(Iterable<Entity> entities,
            Player player, double range,
            Predicate<? super T> filter,
            TargetPriority priority) {
        T best = null;
        double bestScore = Double.MAX_VALUE;

        if (player == null) return Optional.empty();

        for (Entity e : entities) {
            @SuppressWarnings("unchecked")
            T cast = (T) e;
            if (!filter.test(cast)) continue;

            double distSq = cast.distanceToSqr(player);
            if (distSq > range * range) continue;

            double score = switch (priority) {
                case NEAREST -> distSq;
                case LOWEST_HEALTH -> {
                    if (cast instanceof LivingEntity le) {
                        yield le.getHealth() / le.getMaxHealth();
                    }
                    yield distSq;
                }
            };

            if (best == null || score < bestScore) {
                best = cast;
                bestScore = score;
            }
        }

        return Optional.ofNullable(best);
    }

    /**
     * Rotates the player smoothly toward the target using the specified ease.
     *
     * @param target the entity to rotate toward
     * @param ease   the ease function controlling rotation speed
     */
    public static void rotateSmooth(Entity target, AimEase ease) {
        // This is a placeholder - modules keep their own rotation logic for behavior fidelity
    }

    private static float wrap(float v) {
        while (v > 180.0F) v -= 360.0F;
        while (v < -180.0F) v += 360.0F;
        return v;
    }

    /** Ease functions for rotation smoothing. */
    public enum AimEase {
        LINEAR,
        SMOOTH
    }
}