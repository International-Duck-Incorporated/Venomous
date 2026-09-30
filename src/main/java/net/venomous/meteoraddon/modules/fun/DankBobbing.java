package net.venomous.meteoraddon.modules.fun;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.DoubleSetting;

public class DankBobbing extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> motion = this.sgGeneral
      .add(new DoubleSetting.Builder().name("motion").description("Bobbing intensity.").defaultValue(5.0).min(1.0).max(50.0).build());

   public DankBobbing() {
      super(VenomousAddon.CATEGORY, "dank-bobbing", "Adds more view bobbing effect.");
   }

   public double getMotion() {
      return this.isActive() ? (Double)this.motion.get() : 1.0;
   }
}
