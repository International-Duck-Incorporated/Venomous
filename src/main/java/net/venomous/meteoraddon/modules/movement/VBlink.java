package net.venomous.meteoraddon.modules.movement;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

public class VBlink extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> cancelOutgoing = this.sgGeneral
      .add(new Builder().name("cancel-outgoing").description("Cancel outgoing movement packets.").defaultValue(true).build());
   private final Setting<Integer> autoDisable = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("auto-disable")
            .description("Automatically disable after this many ticks. 0 = never.")
            .defaultValue(0)
            .min(0)
            .sliderMax(500)
            .build()
      );
   private final Setting<Boolean> dummy = this.sgGeneral
      .add(
         new Builder().name("dummy").description("Show a fake player at your original position.").defaultValue(false).build()
      );
   private final List<Packet<?>> outgoingPackets = new ArrayList<>();
   private Vec3 startPos = null;
   private int ticks = 0;

   public VBlink() {
      super(VenomousAddon.CATEGORY, "v-blink", "Suspends movement packets, making you appear frozen to others.");
   }

   @Override
   public void onActivate() {
      this.outgoingPackets.clear();
      this.startPos = this.mc.player != null ? this.mc.player.position() : null;
      this.ticks = 0;
   }

   @Override
   public void onDeactivate() {
      if (this.mc.player != null && this.mc.player.connection != null) {
         for (Packet<?> packet : this.outgoingPackets) {
            this.mc.player.connection.send(packet);
         }
      }

      this.outgoingPackets.clear();
      this.startPos = null;
      this.ticks = 0;
   }

   @EventHandler
   private void onPacketSend(Send event) {
      if ((Boolean)this.cancelOutgoing.get() && this.mc.player != null) {
         Packet<?> packet = event.packet;
         if (packet instanceof ServerboundMovePlayerPacket) {
            event.cancel();
            this.outgoingPackets.add(packet);
         }
      }
   }

   @EventHandler
   private void onPacketReceive(Receive event) {
      if (this.mc.player != null && event.packet instanceof ClientboundPlayerPositionPacket posPacket) {
         this.mc.player.connection.send(new ServerboundAcceptTeleportationPacket(posPacket.id()));
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      this.ticks++;
      if ((Integer)this.autoDisable.get() > 0 && this.ticks >= (Integer)this.autoDisable.get()) {
         this.info("Auto-disabled after %d ticks.", new Object[]{this.ticks});
         this.toggle();
      }
   }

   public String getInfoString() {
      return this.outgoingPackets.size() + " packets";
   }
}
