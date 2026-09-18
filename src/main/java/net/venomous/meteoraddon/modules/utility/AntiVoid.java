package net.venomous.meteoraddon.modules.utility;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.venomous.meteoraddon.VenomousAddon;

public class AntiVoid extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<AntiVoid.Mode> mode = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("mode")).description("Motion boosts you up, Back teleports you to your last safe position."))
               .defaultValue(AntiVoid.Mode.Motion))
            .build()
      );
   private final Setting<Integer> threshold = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("threshold"))
                  .description("Blocks above world bottom to activate."))
               .defaultValue(5))
            .min(0)
            .max(64)
            .build()
      );
   private final Setting<Double> upwardVelocity = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("upward-velocity"))
                  .description("Upward velocity for Motion mode."))
               .defaultValue(0.5)
               .min(0.0)
               .max(5.0)
               .visible(() -> this.mode.get() == AntiVoid.Mode.Motion))
            .build()
      );
   private double safeX;
   private double safeY;
   private double safeZ;

   public AntiVoid() {
      super(VenomousAddon.CATEGORY, "v-anti-void", "Prevents falling into the void.");
   }

   public void onActivate() {
      if (this.mc.player != null) {
         this.rememberSafe();
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.level != null) {
         if (this.mc.player.onGround()) {
            this.rememberSafe();
         }

         if (this.nearVoid()) {
            if (this.mode.get() == AntiVoid.Mode.Motion) {
               this.mc.player.setDeltaMovement(this.mc.player.getDeltaMovement().x, (Double)this.upwardVelocity.get(), this.mc.player.getDeltaMovement().z);
               this.mc.player.fallDistance = 0.0;
            } else {
               this.mc.player.setPos(this.safeX, this.safeY, this.safeZ);
               this.mc.player.fallDistance = 0.0;
            }
         }
      }
   }

   private boolean nearVoid() {
      return this.mc.player.getY() < this.mc.level.getMinY() + (Integer)this.threshold.get() && this.mc.player.getDeltaMovement().y < 0.0;
   }

   private void rememberSafe() {
      this.safeX = this.mc.player.getX();
      this.safeY = this.mc.player.getY();
      this.safeZ = this.mc.player.getZ();
   }

   public enum Mode {
      Motion,
      Back;
   }
}
