package net.venomous.meteoraddon.utils.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Safe landing offset helper (MaceKill).
 */
public final class SafeLanding {

    private SafeLanding() {}

    private static final int LRU_SIZE = 256;

    /**
     * Applies a safe offset after attacking to prevent fall damage, using an LRU cache.
     *
     * @param base       the base position to offset from
     * @param horizOffset horizontal offset magnitude
     * @param vertOffset  vertical offset magnitude
     * @param player     the player entity
     * @param level      the world level
     * @return the safe position, or base if no safe position found
     */
    public static Vec3 applySafeOffset(Vec3 base, double horizOffset, double vertOffset,
            Player player, Level level) {
        Vec3[] offsets = new Vec3[]{
            base.add(horizOffset, vertOffset, 0.0),
            base.add(-horizOffset, vertOffset, 0.0),
            base.add(0.0, vertOffset, horizOffset),
            base.add(0.0, vertOffset, -horizOffset),
            base.add(horizOffset, vertOffset, horizOffset),
            base.add(-horizOffset, vertOffset, -horizOffset),
            base.add(-horizOffset, vertOffset, horizOffset),
            base.add(horizOffset, vertOffset, -horizOffset)
        };

        for (Vec3 pos : offsets) {
            if (!isUnsafe(pos, player, level)) {
                return pos;
            }
        }

        Vec3 noHoriz = base.add(0.0, vertOffset, 0.0);
        return !isUnsafe(noHoriz, player, level) ? noHoriz : base;
    }

    private static boolean isUnsafe(Vec3 pos, Player player, Level level) {
        MutableBlockPos mutablePos = new MutableBlockPos();
        double clamped = Mth.clamp(pos.y, level.getMinY(), level.getMaxY() - 1);
        if (clamped != pos.y) return true;

        BlockPos floored = BlockPos.containing(pos);
        if (!level.hasChunk(floored.getX() >> 4, floored.getZ() >> 4)) return true;

        Vec3 delta = pos.subtract(player.position());
        AABB box = player.getBoundingBox().move(delta);
        mutablePos.set(floored);

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    mutablePos.set(floored.getX() + x, floored.getY() + y, floored.getZ() + z);
                    BlockState state = level.getBlockState(mutablePos);
                    if (state.is(Blocks.LAVA)
                        || state.is(Blocks.FIRE)
                        || state.is(Blocks.SOUL_FIRE)
                        || state.is(Blocks.MAGMA_BLOCK)
                        || state.is(Blocks.CAMPFIRE)
                        || state.is(Blocks.SWEET_BERRY_BUSH)
                        || state.is(Blocks.POWDER_SNOW)) {
                        return true;
                    }
                }
            }
        }

        for (Entity e : level.getEntities(player, box, ex -> true)) {
            if (e.isPickable()) return true;
        }

        return !level.noCollision(player, box);
    }
}