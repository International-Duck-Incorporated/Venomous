package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.EnumSetting;

public class SuperKnockback extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<SuperKnockback.Mode> mode = this.sgGeneral
      .add(
         new EnumSetting.Builder<SuperKnockback.Mode>().name("mode").description("The method to increase knockback.").defaultValue(SuperKnockback.Mode.W_TAP)
            .build()
      );
   private final Setting<Integer> hitDelay = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("hit-delay")
            .description("Delay between hits for knockback.")
            .defaultValue(10)
            .min(0)
            .max(20)
            .visible(() -> this.mode.get() == SuperKnockback.Mode.HIT_DELAY)
            .build()
      );
   private final Setting<Boolean> onlyPlayers = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("only-players")
            .description("Only apply to players.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> requireSprint = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("require-sprint")
            .description("Only apply when sprinting.")
            .defaultValue(true)
            .build()
      );
   private int ticksSinceLastHit = 0;

   public SuperKnockback() {
      super(VenomousAddon.CATEGORY, "super-kb", "Increases the knockback dealt to other entities.");
   }

   @Override
   public void onActivate() {
      this.ticksSinceLastHit = 0;
   }

   @Override
   public void onDeactivate() {
      this.ticksSinceLastHit = 0;
   }

   @EventHandler
   private void onAttack(AttackEntityEvent event) {
      if (this.mc.player != null
         && this.mc.level != null
         && (!(Boolean)this.onlyPlayers.get() || event.entity instanceof Player)
         && (!(Boolean)this.requireSprint.get() || this.mc.player.isSprinting())) {
         switch ((SuperKnockback.Mode)this.mode.get()) {
            case PACKET:
               if (this.mc.getConnection() != null) {
                  this.mc
                     .getConnection()
                     .send(new ServerboundInteractPacket(event.entity.getId(), InteractionHand.MAIN_HAND, Vec3.ZERO, this.mc.player.isShiftKeyDown()));
               }
               break;
            case W_TAP:
               if (this.mc.getConnection() != null) {
                  this.mc.getConnection().send(new ServerboundPlayerCommandPacket(this.mc.player, Action.STOP_SPRINTING));
                  this.mc.getConnection().send(new ServerboundPlayerCommandPacket(this.mc.player, Action.START_SPRINTING));
               }
               this.mc.player.setSprinting(true);
               break;
            case S_TAP:
               if (this.mc.getConnection() != null) {
                  this.mc.getConnection().send(new ServerboundPlayerCommandPacket(this.mc.player, Action.STOP_SPRINTING));
               }
               this.mc.player.setSprinting(false);
               break;
            case HIT_DELAY:
               if (this.ticksSinceLastHit < (Integer)this.hitDelay.get()) {
                  return;
               }

               this.ticksSinceLastHit = 0;
         }
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      this.ticksSinceLastHit++;
   }

   public enum Mode {
      PACKET,
      W_TAP,
      S_TAP,
      HIT_DELAY;
   }
}
