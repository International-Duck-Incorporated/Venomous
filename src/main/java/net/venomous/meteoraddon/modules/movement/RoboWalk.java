package net.venomous.meteoraddon.modules.movement;

import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.venomous.meteoraddon.VenomousAddon;

public class RoboWalk extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> playerPackets = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("player-packets")).description("Round outgoing player movement X/Z coordinates to two decimals."))
               .defaultValue(true))
            .build()
      );
   private boolean sending;

   public RoboWalk() {
      super(VenomousAddon.CATEGORY, "robo-walk", "Rounds outgoing movement coordinates for LiveOverflow-style movement checks.");
   }

   @EventHandler
   private void onSend(Send event) {
      if (!this.sending
         && (Boolean)this.playerPackets.get()
         && this.mc.player != null
         && event.packet instanceof ServerboundMovePlayerPacket packet2
         && packet2.hasPosition()) {
         double x = this.smooth(packet2.getX(this.mc.player.getX()));
         double y = packet2.getY(this.mc.player.getY());
         double z = this.smooth(packet2.getZ(this.mc.player.getZ()));
         float yaw = packet2.getYRot(this.mc.player.getYRot());
         float pitch = packet2.getXRot(this.mc.player.getXRot());
         boolean onGround = packet2.isOnGround();
         boolean horizontalCollision = packet2.horizontalCollision();
         event.setCancelled(true);
         this.sending = true;
         if (packet2.hasRotation()) {
            event.sendSilently(new PosRot(x, y, z, yaw, pitch, onGround, horizontalCollision));
         } else {
            event.sendSilently(new Pos(x, y, z, onGround, horizontalCollision));
         }

         this.sending = false;
      }
   }

   private double smooth(double d) {
      double temp = Math.round(d * 100.0) / 100.0;
      return Math.nextAfter(temp, temp + Math.signum(d));
   }
}
