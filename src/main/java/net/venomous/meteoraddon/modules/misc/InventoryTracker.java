package net.venomous.meteoraddon.modules.misc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.DoubleSetting;

/**
 * Reads the contents of containers other players have open, from the packets their client
 * receives.
 *
 * <p>The server sends container contents to every client that can see the opener, so a nearby
 * player looking into a chest produces a packet on our side too. That is the only way to see
 * what someone is holding - there is no packet that states "player X has item Y".</p>
 */
public class InventoryTracker extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Double> range = this.sgGeneral
        .add(
            new DoubleSetting.Builder().name("range").description("Range to track player inventory changes.")
                .defaultValue(8.0)
                .min(1.0)
                .max(64.0)
                .sliderMax(32.0)
                .build()
        );
    private final Map<UUID, Map<Integer, ItemStack>> trackedInventories = new HashMap<>();
    private UUID lastOpener;
    private long lastSeen = 0L;

    public InventoryTracker() {
        super(VenomousAddon.CATEGORY, "inventorytracker", "Tracks items in nearby players' inventories via container packets.");
    }

    @Override
    public void onActivate() {
        this.trackedInventories.clear();
        this.lastOpener = null;
        this.lastSeen = 0L;
    }

    @Override
    public void onDeactivate() {
        this.trackedInventories.clear();
        this.lastOpener = null;
        this.lastSeen = 0L;
    }

    @EventHandler
    private void onPacket(Receive event) {
        if (this.mc.player == null || this.mc.level == null) return;

        Packet<?> packet = event.packet;

        // Was this our own container open? Then the packet is about us and carries no
        // information about anybody else. Anything else came from a player we can see.
        if (this.isOwnContainer()) return;

        Player opener = this.nearestViewer();
        if (opener == null) return;

        if (packet instanceof ClientboundContainerSetContentPacket full) {
            Map<Integer, ItemStack> items = new HashMap<>();
            for (int slot = 0; slot < full.items().size(); slot++) {
                ItemStack stack = full.items().get(slot);
                if (!stack.isEmpty()) items.put(slot, stack.copy());
            }

            this.trackedInventories.put(opener.getUUID(), items);
            this.lastOpener = opener.getUUID();
            this.lastSeen = System.currentTimeMillis();
        } else if (packet instanceof ClientboundContainerSetSlotPacket single) {
            Map<Integer, ItemStack> items = this.trackedInventories.computeIfAbsent(
                opener.getUUID(), uuid -> new HashMap<>());

            ItemStack stack = single.getItem();
            if (stack.isEmpty()) {
                items.remove(single.getSlot());
            } else {
                items.put(single.getSlot(), stack.copy());
            }

            this.lastOpener = opener.getUUID();
            this.lastSeen = System.currentTimeMillis();
        }
    }

    /**
     * True when a container screen is open on this client.
     *
     * <p>Any container screen means we opened that container, so its packets describe us rather
     * than anyone else. Comparing menu ids would be equivalent here but far more convoluted.</p>
     */
    private boolean isOwnContainer() {
        return this.mc.gui.screen() instanceof AbstractContainerScreen<?>;
    }

    /**
     * The closest player within range, excluding this client.
     *
     * <p>A container packet carries no sender, so the nearest viewer is the best available
     * attribution. It is wrong whenever several players open the same chest together, which is
     * the honest limit of what the protocol exposes.</p>
     */
    private Player nearestViewer() {
        Player nearest = null;
        double bestSq = (Double)this.range.get() * (Double)this.range.get();

        for (Player player : this.mc.level.players()) {
            if (player == this.mc.player) continue;

            double distSq = this.mc.player.distanceToSqr(player);
            if (distSq < bestSq) {
                bestSq = distSq;
                nearest = player;
            }
        }

        return nearest;
    }

    public ItemStack getTrackedItem(Player player, int slot) {
        Map<Integer, ItemStack> inv = this.trackedInventories.get(player.getUUID());
        return inv != null ? inv.getOrDefault(slot, ItemStack.EMPTY) : ItemStack.EMPTY;
    }

    @Override
    public String getInfoString() {
        return this.trackedInventories.size() + " players";
    }
}
