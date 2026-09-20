package net.venomous.meteoraddon.modules.combat;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.venomous.meteoraddon.VenomousAddon;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PopCounter extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Boolean> ignoreSelf = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("ignore-self")
            .description("Ignore your own totem pops.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> ignoreFriends = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("ignore-friends")
            .description("Ignore friends totem pops.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> announcePops = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("announce-pops")
            .description("Notify in chat when an enemy pops a totem.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> announceKills = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("announce-kills")
            .description("Notify in chat when an enemy dies.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> publicChat = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("public-chat")
            .description("Send announcements to public chat instead of client chat.")
            .defaultValue(false)
            .build()
    );

    private final Object2IntMap<UUID> popMap = new Object2IntOpenHashMap<>();

    public PopCounter() {
        super(VenomousAddon.CATEGORY, "pop-counter", "Counts and announces totem pops and kills for nearby players.");
    }

    @Override
    public void onActivate() {
        this.popMap.clear();
    }

    @Override
    public void onDeactivate() {
        this.popMap.clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (this.mc.level == null) return;

        if (event.packet instanceof ClientboundEntityEventPacket packet) {
            if (packet.getEventId() == 35) { // EntityEvent.USE_TOTEM_OF_UNDYING
                Entity entity = packet.getEntity(this.mc.level);
                if (entity instanceof Player player) {
                    if (player == this.mc.player && this.ignoreSelf.get()) return;
                    if (this.ignoreFriends.get() && !Friends.get().shouldAttack(player)) return;

                    UUID uuid = player.getUUID();
                    int count = this.popMap.getOrDefault(uuid, 0) + 1;
                    this.popMap.put(uuid, count);

                    if (this.announcePops.get()) {
                        String name = player.getName().getString();
                        String suffix = count == 1 ? " totem!" : " totems!";
                        String msg = name + " popped " + count + suffix;

                        if (this.publicChat.get() && this.mc.player != null && this.mc.player.connection != null) {
                            this.mc.player.connection.sendChat(msg);
                        } else {
                            this.info("§7[§aVenomous§7] §b" + name + "§f popped §c§l" + count + "§f" + suffix, new Object[0]);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onTick(Post event) {
        if (this.mc.level == null || this.popMap.isEmpty()) return;

        List<UUID> deadPlayers = new ArrayList<>();

        for (UUID uuid : this.popMap.keySet()) {
            Player player = this.mc.level.getPlayerByUUID(uuid);
            if (player != null && (!player.isAlive() || player.getHealth() <= 0.0f)) {
                deadPlayers.add(uuid);
            }
        }

        for (UUID uuid : deadPlayers) {
            int pops = this.popMap.removeInt(uuid);
            if (this.announceKills.get()) {
                Player player = this.mc.level.getPlayerByUUID(uuid);
                String name = player != null ? player.getName().getString() : "Unknown";
                String suffix = pops == 1 ? " totem" : " totems";
                String msg = name + " died after popping " + pops + " " + suffix + "!";

                if (this.publicChat.get() && this.mc.player != null && this.mc.player.connection != null) {
                    this.mc.player.connection.sendChat(msg);
                } else {
                    this.info("§7[§aVenomous§7] §b" + name + "§f died after popping §c§l" + pops + "§f " + suffix + "!", new Object[0]);
                }
            }
        }
    }

    public int getPops(Player player) {
        if (player == null) return 0;
        return this.popMap.getOrDefault(player.getUUID(), 0);
    }
}
