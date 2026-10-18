package net.venomous.meteoraddon.modules.misc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.DoubleSetting;

public class InventoryTracker extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Double> range = this.sgGeneral
        .add(
            new DoubleSetting.Builder().name("range").description("Range to track player inventory changes.").defaultValue(8.0).min(1.0).max(64.0).build()
        );
    private final Map<UUID, Map<Integer, ItemStack>> trackedInventories = new HashMap<>();

    public InventoryTracker() {
        super(VenomousAddon.CATEGORY, "inventorytracker", "Tracks items in nearby players' inventories via container packets.");
    }

    public void onActivate() {
        this.trackedInventories.clear();
    }

    public void onDeactivate() {
        this.trackedInventories.clear();
    }

    @EventHandler
    private void onPacket(Receive event) {
        if (this.mc.player != null && this.mc.level != null) {
            Packet<?> packet = event.packet;
            if (!(packet instanceof ClientboundContainerSetContentPacket) && packet instanceof ClientboundContainerSetSlotPacket) {
            }
        }
    }

    public ItemStack getTrackedItem(Player player, int slot) {
        Map<Integer, ItemStack> inv = this.trackedInventories.get(player.getUUID());
        return inv != null ? inv.getOrDefault(slot, ItemStack.EMPTY) : ItemStack.EMPTY;
    }

    public String getInfoString() {
        return this.trackedInventories.size() + " players";
    }
}
