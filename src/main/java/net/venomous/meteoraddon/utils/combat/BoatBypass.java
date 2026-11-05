package net.venomous.meteoraddon.utils.combat;

import java.lang.Iterable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;

/**
 * Boat bypass helper (KillAura Grim mode).
 */
public final class BoatBypass {

    private BoatBypass() {}

    /**
     * Checks if a boat is nearby the player.
     *
     * @param player  the player
     * @param entities iterable of entities (typically mc.level.entitiesForRendering())
     * @return true if a boat is within 2.5 blocks
     */
    public static boolean isBoatNearby(net.minecraft.world.entity.player.Player player, Iterable<Entity> entities) {
        if (entities == null) return false;

        for (Entity e : entities) {
            if (e instanceof AbstractBoat && e != player.getVehicle() && player.distanceTo(e) < 2.5) {
                return true;
            }
        }
        return false;
    }

    /**
     * Attempts to place a boat from the player's hotbar.
     */
    public static void placeBoat(net.minecraft.world.entity.player.Player player, net.minecraft.client.Minecraft mc) {
        int slot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem().toString().toLowerCase().contains("boat")) {
                slot = i;
                break;
            }
        }

        if (slot != -1) {
            int prev = player.getInventory().getSelectedSlot();
            player.getInventory().setSelectedSlot(slot);
            float pp = player.getXRot();
            player.setXRot(80.0F);
            mc.gameMode.useItem(player, net.minecraft.world.InteractionHand.MAIN_HAND);
            player.setXRot(pp);
            player.getInventory().setSelectedSlot(prev);
        }
    }
}