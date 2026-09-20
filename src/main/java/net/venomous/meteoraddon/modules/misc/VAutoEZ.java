package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.venomous.meteoraddon.VenomousAddon;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class VAutoEZ extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<String> message = this.sgGeneral.add(
        new StringSetting.Builder()
            .name("message")
            .description("Message to send upon killing an enemy. Placeholders: {player}, {kills}, {hp}.")
            .defaultValue("GG {player}! Venomous V7 on top!")
            .build()
    );

    private final Setting<Boolean> killStreak = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("kill-streak")
            .description("Track your kill streak count in the session.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> clientSide = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("client-side")
            .description("Display the message only on your client screen.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Double> targetRange = this.sgGeneral.add(
        new DoubleSetting.Builder()
            .name("target-range")
            .description("Maximum distance to register target kills.")
            .defaultValue(15.0)
            .min(1.0)
            .sliderMax(30.0)
            .build()
    );

    private final Map<UUID, Long> attackedTargets = new HashMap<>();
    private int kills = 0;

    public VAutoEZ() {
        super(VenomousAddon.CATEGORY, "v-auto-ez", "Sends automated taunt or GG messages when you eliminate an enemy.");
    }

    @Override
    public void onActivate() {
        this.attackedTargets.clear();
        this.kills = 0;
    }

    @Override
    public void onDeactivate() {
        this.attackedTargets.clear();
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (this.mc.player == null) return;

        if (event.entity instanceof Player player && player != this.mc.player) {
            if (!Friends.get().isFriend(player)) {
                this.attackedTargets.put(player.getUUID(), System.currentTimeMillis());
            }
        }
    }

    @EventHandler
    private void onTick(Post event) {
        if (this.mc.player == null || this.mc.level == null) return;

        if (!this.mc.player.isAlive() || this.mc.player.getHealth() <= 0.0f) {
            this.kills = 0;
            this.attackedTargets.clear();
            return;
        }

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = this.attackedTargets.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            UUID uuid = entry.getKey();
            long timestamp = entry.getValue();

            // Expire targets older than 8 seconds
            if (now - timestamp > 8000L) {
                it.remove();
                continue;
            }

            Player player = this.mc.level.getPlayerByUUID(uuid);
            if (player != null && (!player.isAlive() || player.getHealth() <= 0.0f)) {
                if (player.distanceToSqr(this.mc.player) <= this.targetRange.get() * this.targetRange.get()) {
                    this.kills++;
                    this.sendKillMessage(player);
                    it.remove();
                }
            }
        }
    }

    private void sendKillMessage(Player target) {
        String targetName = target.getName().getString();
        float hp = this.mc.player != null ? this.mc.player.getHealth() : 20.0f;
        String formattedHp = String.format("%.1f", hp);

        String text = this.message.get()
            .replace("{player}", targetName)
            .replace("{kills}", String.valueOf(this.kills))
            .replace("{hp}", formattedHp);

        if (this.clientSide.get()) {
            this.info("§7[§aVenomous EZ§7] " + text, new Object[0]);
        } else if (this.mc.player != null && this.mc.player.connection != null) {
            this.mc.player.connection.sendChat(text);
        }
    }
}
