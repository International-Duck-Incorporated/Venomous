package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.venomous.meteoraddon.VenomousAddon;

public class VNotifier extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> playerJoin = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("player-join")).description("Notify on player join.")).defaultValue(true)).build());
   private final Setting<Boolean> playerLeave = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("player-leave")).description("Notify on player leave.")).defaultValue(true)).build());

   public VNotifier() {
      super(VenomousAddon.CATEGORY, "v-notifier", "Notification system.");
   }
}
