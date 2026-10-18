package net.venomous.meteoraddon.utils;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.venomous.meteoraddon.modules.misc.Teams;

public final class McCompat {
    private McCompat() {
    }

    public static boolean isSword(Item item) {
        return item == Items.WOODEN_SWORD
            || item == Items.COPPER_SWORD
            || item == Items.STONE_SWORD
            || item == Items.GOLDEN_SWORD
            || item == Items.IRON_SWORD
            || item == Items.DIAMOND_SWORD
            || item == Items.NETHERITE_SWORD;
    }

    public static boolean isAxe(Item item) {
        return item == Items.WOODEN_AXE
            || item == Items.COPPER_AXE
            || item == Items.STONE_AXE
            || item == Items.GOLDEN_AXE
            || item == Items.IRON_AXE
            || item == Items.DIAMOND_AXE
            || item == Items.NETHERITE_AXE;
    }

    public static boolean isWeapon(Item item) {
        return isSword(item) || isAxe(item) || item == Items.MACE;
    }

    public static boolean isTeammate(Entity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        Modules modules = Modules.get();
        if (modules == null) {
            return false;
        }

        Teams teams = modules.get(Teams.class);
        return teams != null && teams.isActive() && teams.isTeammate(player);
    }

    public static <T> Stream<T> stream(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }
}
