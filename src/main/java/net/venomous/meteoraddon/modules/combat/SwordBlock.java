package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ShieldItem;
import net.venomous.meteoraddon.VenomousAddon;

public class SwordBlock extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> onlyVisual = this.sgGeneral
      .add(
         new Builder().name("only-visual").description("Only show the blocking animation, don't actually block.")
            .defaultValue(false)
            .build()
      );

   public SwordBlock() {
      super(VenomousAddon.CATEGORY, "sword-block", "Block with swords using an offhand shield (1.8-style blocking).");
   }

   private boolean isHoldingSword() {
      if (this.mc.player == null) {
         return false;
      }

      String name = this.mc.player.getMainHandItem().getItem().toString().toLowerCase();
      return name.contains("sword");
   }

   private boolean hasOffhandShield() {
      return this.mc.player == null ? false : this.mc.player.getOffhandItem().getItem() instanceof ShieldItem;
   }

   @EventHandler
   private void onPacketSend(Send event) {
      if (this.mc.player != null
         && !(Boolean)this.onlyVisual.get()
         && event.packet instanceof ServerboundUseItemPacket packet
         && packet.getHand() == InteractionHand.MAIN_HAND
         && this.isHoldingSword()
         && this.hasOffhandShield()) {
         event.cancel();
         this.mc
            .player
            .connection
            .send(new ServerboundUseItemPacket(InteractionHand.OFF_HAND, packet.getSequence(), this.mc.player.getYRot(), this.mc.player.getXRot()));
      }
   }
}
