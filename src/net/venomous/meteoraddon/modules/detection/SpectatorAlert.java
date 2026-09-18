package net.venomous.meteoraddon.modules.detection;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.AbstractClientPlayer;
import net.venomous.meteoraddon.VenomousAddon;

public class SpectatorAlert extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> range = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("range")).description("Range to check for spectator players."))
            .defaultValue(64.0)
            .range(8.0, 256.0)
            .sliderMax(128.0)
            .build()
      );
   private final Setting<Integer> cooldown = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("cooldown"))
                  .description("Ticks before the same spectator can alert again."))
               .defaultValue(200))
            .range(20, 1200)
            .sliderMax(600)
            .build()
      );
   private final Set<UUID> alerted = new HashSet<>();
   private int timer;

   public SpectatorAlert() {
      super(VenomousAddon.CATEGORY, "v-spectator-alert", "Alerts when spectator players are nearby.");
   }

   public void onActivate() {
      this.alerted.clear();
      this.timer = 0;
   }

   @EventHandler
   private void onTick(Post event) {
      if (this.mc.player != null && this.mc.level != null) {
         if (this.timer++ >= (Integer)this.cooldown.get()) {
            this.alerted.clear();
            this.timer = 0;
         }

         for (AbstractClientPlayer player : this.mc.level.players()) {
            if (player != this.mc.player
               && player.isSpectator()
               && !(this.mc.player.distanceToSqr(player) > (Double)this.range.get() * (Double)this.range.get())
               && this.alerted.add(player.getUUID())) {
               this.warning("Spectator nearby: %s", new Object[]{player.getName().getString()});
            }
         }
      }
   }
}
