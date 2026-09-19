package net.venomous.meteoraddon.modules.misc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.venomous.meteoraddon.VenomousAddon;

public class VNotifier extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> playerJoin = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("player-join")).description("Notify when a player joins the server.")).defaultValue(true)).build());
   private final Setting<Boolean> playerLeave = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("player-leave")).description("Notify when a player leaves the server.")).defaultValue(true)).build());
   private final Map<UUID, String> knownPlayers = new HashMap<>();
   private boolean initialized = false;

   public VNotifier() {
      super(VenomousAddon.CATEGORY, "v-notifier", "Notification system.");
   }

   public void onActivate() {
      this.knownPlayers.clear();
      this.initialized = false;
   }

   public void onDeactivate() {
      this.knownPlayers.clear();
      this.initialized = false;
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.knownPlayers.clear();
      this.initialized = false;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player == null || this.mc.getConnection() == null) {
         return;
      }

      Map<UUID, String> currentPlayers = new HashMap<>();
      for (var entry : this.mc.getConnection().getOnlinePlayers()) {
         if (entry.getProfile() != null) {
            currentPlayers.put(entry.getProfile().id(), entry.getProfile().name());
         }
      }

      currentPlayers.remove(this.mc.player.getUUID());

      if (!this.initialized) {
         this.knownPlayers.clear();
         this.knownPlayers.putAll(currentPlayers);
         this.initialized = true;
         return;
      }

      if ((Boolean)this.playerJoin.get()) {
         for (Map.Entry<UUID, String> entry : currentPlayers.entrySet()) {
            if (!this.knownPlayers.containsKey(entry.getKey())) {
               this.info("Player joined: %s", new Object[]{entry.getValue()});
            }
         }
      }

      if ((Boolean)this.playerLeave.get()) {
         for (Map.Entry<UUID, String> entry : this.knownPlayers.entrySet()) {
            if (!currentPlayers.containsKey(entry.getKey())) {
               this.info("Player left: %s", new Object[]{entry.getValue()});
            }
         }
      }

      this.knownPlayers.clear();
      this.knownPlayers.putAll(currentPlayers);
   }
}
