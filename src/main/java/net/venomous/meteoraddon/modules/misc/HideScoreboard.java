package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;

public class HideScoreboard extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> hideNumbers = this.sgGeneral
      .add(new BoolSetting.Builder().name("hide-numbers").description("Also hide the scoreboard numbers.").defaultValue(true).build());

   public HideScoreboard() {
      super(VenomousAddon.CATEGORY, "hide-scoreboard", "Hides the scoreboard sidebar from the HUD.");
   }

   public boolean shouldHideNumbers() {
      return this.isActive() && (Boolean)this.hideNumbers.get();
   }
}
