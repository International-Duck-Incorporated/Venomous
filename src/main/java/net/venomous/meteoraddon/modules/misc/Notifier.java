package net.venomous.meteoraddon.modules.misc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.venomous.meteoraddon.VenomousAddon;

/**
 * Chat notifications for things worth knowing about.
 *
 * <p>Merges the former {@code lag-notifier} module. Both modules were "tell me in chat when
 * something happens on this server" and each shipped its own on/off entry in the module list; the
 * only difference was the trigger. They are now toggles in a single module: player join/leave, and
 * server TPS drops.
 */
public class Notifier extends Module {
   private final SettingGroup sgPlayers = this.settings.createGroup("Players");
   private final SettingGroup sgPerformance = this.settings.createGroup("Performance");

   private final Setting<Boolean> playerJoin = this.sgPlayers.add(
      new BoolSetting.Builder()
         .name("player-join")
         .description("Notify when a player joins the server.")
         .defaultValue(true)
         .build()
   );

   private final Setting<Boolean> playerLeave = this.sgPlayers.add(
      new BoolSetting.Builder()
         .name("player-leave")
         .description("Notify when a player leaves the server.")
         .defaultValue(true)
         .build()
   );

   private final Setting<Boolean> lagAlerts = this.sgPerformance.add(
      new BoolSetting.Builder()
         .name("lag-alerts")
         .description("Estimate server TPS from time packets and warn when it drops (former lag-notifier).")
         .defaultValue(true)
         .build()
   );

   private final Setting<Integer> warnBelow = this.sgPerformance.add(
      new IntSetting.Builder()
         .name("warn-below-tps")
         .description("Fire a chat warning when estimated TPS drops below this value.")
         .defaultValue(15)
         .range(1, 20)
         .sliderMax(20)
         .visible(this.lagAlerts::get)
         .build()
   );

   private final Setting<Boolean> recoveryAlert = this.sgPerformance.add(
      new BoolSetting.Builder()
         .name("recovery-alert")
         .description("Also notify when the server recovers from lag.")
         .defaultValue(true)
         .visible(this.lagAlerts::get)
         .build()
   );

   private final Setting<Boolean> alwaysShowTps = this.sgPerformance.add(
      new BoolSetting.Builder()
         .name("always-show-tps")
         .description("Show TPS in the module info string at all times.")
         .defaultValue(true)
         .visible(this.lagAlerts::get)
         .build()
   );

   private final Map<UUID, String> knownPlayers = new HashMap<>();
   private boolean initialised = false;

   private long lastPacketMs = 0L;
   private double smoothTps = 20.0;
   private boolean lagging = false;

   public Notifier() {
      super(VenomousAddon.CATEGORY, "v-notifier", "Chat notifications for player join/leave and server lag.");
   }

   @Override
   public void onActivate() {
      this.reset();
   }

   @Override
   public void onDeactivate() {
      this.reset();
   }

   private void reset() {
      this.knownPlayers.clear();
      this.initialised = false;
      this.lastPacketMs = 0L;
      this.smoothTps = 20.0;
      this.lagging = false;
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.reset();
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player == null || this.mc.getConnection() == null) {
         return;
      }
      if (!this.playerJoin.get() && !this.playerLeave.get()) {
         return;
      }

      Map<UUID, String> current = new HashMap<>();
      for (var entry : this.mc.getConnection().getOnlinePlayers()) {
         if (entry.getProfile() != null) {
            current.put(entry.getProfile().id(), entry.getProfile().name());
         }
      }
      current.remove(this.mc.player.getUUID());

      if (!this.initialised) {
         this.knownPlayers.clear();
         this.knownPlayers.putAll(current);
         this.initialised = true;
         return;
      }

      if (this.playerJoin.get()) {
         for (Map.Entry<UUID, String> entry : current.entrySet()) {
            if (!this.knownPlayers.containsKey(entry.getKey())) {
               this.info("Player joined: %s", entry.getValue());
            }
         }
      }

      if (this.playerLeave.get()) {
         for (Map.Entry<UUID, String> entry : this.knownPlayers.entrySet()) {
            if (!current.containsKey(entry.getKey())) {
               this.info("Player left: %s", entry.getValue());
            }
         }
      }

      this.knownPlayers.clear();
      this.knownPlayers.putAll(current);
   }

   @EventHandler
   private void onReceive(Receive event) {
      if (!this.lagAlerts.get() || this.mc.player == null) {
         return;
      }
      if (!(event.packet instanceof ClientboundSetTimePacket)) {
         return;
      }

      long now = System.currentTimeMillis();
      if (this.lastPacketMs != 0L) {
         long delta = now - this.lastPacketMs;
         if (delta > 0L) {
            double measured = Math.min(20.0, 20000.0 / delta);
            this.smoothTps = 0.7 * this.smoothTps + 0.3 * measured;
            this.smoothTps = Math.round(this.smoothTps * 10.0) / 10.0;

            boolean nowLagging = this.smoothTps < this.warnBelow.get();
            if (nowLagging && !this.lagging) {
               this.warning("Server lag detected - TPS %.1f", this.smoothTps);
            } else if (!nowLagging && this.lagging && this.recoveryAlert.get()) {
               this.info("Server recovered - TPS %.1f", this.smoothTps);
            }
            this.lagging = nowLagging;
         }
      }
      this.lastPacketMs = now;
   }

   @Override
   public String getInfoString() {
      if (!this.lagAlerts.get()) {
         return null;
      }
      return !this.alwaysShowTps.get() && !this.lagging ? null : String.format("%.1f TPS", this.smoothTps);
   }
}
