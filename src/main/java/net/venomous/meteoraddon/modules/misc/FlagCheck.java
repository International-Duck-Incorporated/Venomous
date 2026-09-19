package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

public class FlagCheck extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> chat = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("chat")
            .description("Show flag alerts in chat.")
            .defaultValue(true)
            .build()
      );

   public FlagCheck() {
      super(VenomousAddon.CATEGORY, "v-flagcheck", "Check if you are flagged and rubberbanded by anti-cheat.");
   }

   @EventHandler
   private void onReceivePacket(Receive event) {
      if (event.packet instanceof ClientboundPlayerPositionPacket packet && this.mc.player != null) {
         Vec3 targetPos = packet.change().position();
         if (targetPos != null) {
            double dist = this.mc.player.position().distanceTo(targetPos);
            if (dist > 0.4) {
               if ((Boolean)this.chat.get()) {
                  this.warning("Anticheat flag / rubberband detected! Setback distance: %.2f blocks", new Object[]{dist});
               }
            }
         }
      }
   }
}
