package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.venomous.meteoraddon.VenomousAddon;

public class FlagCheck extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> chat = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("chat")).description("Show flag alerts in chat.")).defaultValue(true)).build());

   public FlagCheck() {
      super(VenomousAddon.CATEGORY, "flagcheck", "Check if you're flagged by anti-cheat.");
   }
}
