package net.venomous.meteoraddon.modules.detection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.venomous.meteoraddon.VenomousAddon;

public class AntiStaff extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<List<String>> staffNames = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("staff-names")).description("Names to treat as staff. Matching is case-insensitive."))
               .defaultValue(new ArrayList()))
            .build()
      );
   private final Setting<Boolean> alertJoin = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("alert-join"))
                  .description("Warn when a configured staff name appears in tab/player info."))
               .defaultValue(true))
            .build()
      );
   private final Set<String> alerted = new HashSet<>();

   public AntiStaff() {
      super(VenomousAddon.CATEGORY, "v-anti-staff", "Warns when configured staff names appear in player info packets.");
   }

   public void onActivate() {
      this.alerted.clear();
   }

   @EventHandler
   private void onPacket(Receive event) {
      if ((Boolean)this.alertJoin.get() && event.packet instanceof ClientboundPlayerInfoUpdatePacket packet2) {
         for (Entry entry : packet2.newEntries()) {
            String name;
            if (entry.profile() != null && this.isStaff(name = entry.profile().name()) && this.alerted.add(name.toLowerCase(Locale.ROOT))) {
               this.warning("Staff detected: %s", new Object[]{name});
            }
         }
      }
   }

   private boolean isStaff(String name) {
      for (String staff : this.staffNames.get()) {
         if (staff.equalsIgnoreCase(name)) {
            return true;
         }
      }

      return false;
   }
}
