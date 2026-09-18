package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.venomous.meteoraddon.VenomousAddon;

public class VHideScoreboard extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> hideNumbers = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("hide-numbers")).description("Also hide the scoreboard numbers.")).defaultValue(true)).build());

   public VHideScoreboard() {
      super(VenomousAddon.CATEGORY, "v-hide-scoreboard", "Hides the scoreboard sidebar from the HUD.");
   }

   public boolean shouldHideNumbers() {
      return this.isActive() && (Boolean)this.hideNumbers.get();
   }
}
