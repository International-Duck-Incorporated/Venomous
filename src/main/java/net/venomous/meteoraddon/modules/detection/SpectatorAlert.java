package net.venomous.meteoraddon.modules.detection;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.AbstractClientPlayer;
import net.venomous.meteoraddon.VenomousAddon;

public class SpectatorAlert extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

   private final Setting<Double> range = this.sgGeneral.add(
      new DoubleSetting.Builder()
         .name("range")
         .description("Range to check for spectator players.")
         .defaultValue(64.0)
         .range(8.0, 256.0)
         .sliderMax(128.0)
         .build()
   );

   private final Setting<Boolean> showDistance = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("show-distance")
         .description("Include distance in the spectator alert message.")
         .defaultValue(true)
         .build()
   );

   private final Setting<Integer> cooldown = this.sgGeneral.add(
      new IntSetting.Builder()
         .name("cooldown")
         .description("Ticks before the same spectator can alert again.")
         .defaultValue(200)
         .range(20, 1200)
         .sliderMax(600)
         .build()
   );

   private final Set<UUID> alerted = new HashSet<>();
   private int timer;

   public SpectatorAlert() {
      super(VenomousAddon.CATEGORY, "spectator-alert", "Alerts when spectator / staff players are hovering or observing nearby.");
   }

   @Override
   public void onActivate() {
      this.alerted.clear();
      this.timer = 0;
   }

   @Override
   public void onDeactivate() {
      this.alerted.clear();
      this.timer = 0;
   }

   @EventHandler
   private void onTick(Post event) {
      if (this.mc.player == null || this.mc.level == null) {
         return;
      }

      if (this.timer++ >= this.cooldown.get()) {
         this.alerted.clear();
         this.timer = 0;
      }

      double maxDistSq = this.range.get() * this.range.get();

      for (AbstractClientPlayer player : this.mc.level.players()) {
         if (player != this.mc.player && player.isSpectator()) {
            double distSq = this.mc.player.distanceToSqr(player);
            if (distSq <= maxDistSq && this.alerted.add(player.getUUID())) {
               double dist = Math.sqrt(distSq);
               if (this.showDistance.get()) {
                  this.warning("Spectator observing nearby: %s (%.1fm)", player.getName().getString(), dist);
               } else {
                  this.warning("Spectator observing nearby: %s", player.getName().getString());
               }
            }
         }
      }
   }
}
