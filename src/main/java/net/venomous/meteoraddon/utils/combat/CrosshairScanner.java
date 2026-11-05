package net.venomous.meteoraddon.utils.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;

/**
 * Crosshair raycast helper shared across modules.
 */
public final class CrosshairScanner {

    private CrosshairScanner() {}

    /**
     * Returns the first entity hit by a raycast from the player's eye position along their view vector.
     *
     * @param player the player entity
     * @param level  the world level (ClientLevel at runtime)
     * @param reach  maximum raycast distance
     * @return the hit entity, or null if none
     */
    @SuppressWarnings("unchecked")
    public static Entity raycastEntities(Player player, Level level, double reach) {
        if (player == null || level == null) return null;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 lookEndPos = eyePos.add(lookVec.x * reach, lookVec.y * reach, lookVec.z * reach);

        BlockHitResult blockHit = level.clip(new ClipContext(eyePos, lookEndPos, Block.COLLIDER, Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            lookEndPos = blockHit.getLocation();
        }

        double rayDistSqr = eyePos.distanceToSqr(lookEndPos);
        AABB hitcastSearchBox = player.getBoundingBox().expandTowards(lookVec.scale(reach)).inflate(1.0, 1.0, 1.0);

        EntityHitResult result = ProjectileUtil.getEntityHitResult(
            player, eyePos, lookEndPos, hitcastSearchBox,
            e -> !e.isSpectator() && e.isPickable() && e != player, rayDistSqr
        );

        return result != null ? result.getEntity() : null;
    }
}