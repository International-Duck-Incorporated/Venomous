package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.orbit.EventHandler;
import net.venomous.meteoraddon.VenomousAddon;

public class FreecamMining extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> range = this.sgGeneral
      .add(
         new Builder().name("range").description("Maximum range to mine blocks from freecam position.")
            .defaultValue(64.0)
            .min(4.0)
            .max(256.0)
            .sliderMax(128.0)
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("rotate")
            .description("Rotate towards the block before mining.")
            .defaultValue(false)
            .build()
      );

   public FreecamMining() {
      super(VenomousAddon.CATEGORY, "freecam-mining", "Allows mining blocks while freecam is active.");
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.level != null) {
         Freecam freecam = (Freecam)Modules.get().get(Freecam.class);
         if (freecam != null && !freecam.isActive()) {
         }
      }
   }

   public boolean isActiveAndFreecamOn() {
      return this.isActive() && Modules.get().isActive(Freecam.class);
   }

   public double getExtendedRange() {
      return this.isActive() ? (Double)this.range.get() : 4.5;
   }
}
