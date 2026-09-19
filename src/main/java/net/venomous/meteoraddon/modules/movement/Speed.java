package net.venomous.meteoraddon.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.McCompat;
import net.venomous.meteoraddon.utils.MovementMath;

public class Speed extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgGrim = this.settings.createGroup("Grim");
   private final SettingGroup sgVulcan = this.settings.createGroup("Vulcan");
   private final SettingGroup sgStrafe = this.settings.createGroup("Strafe / Custom");

   private final Setting<Speed.Mode> mode = this.sgGeneral.add(
      new EnumSetting.Builder<Speed.Mode>()
         .name("mode")
         .description("Speed mode — select your anticheat.")
         .defaultValue(Speed.Mode.Strafe)
         .build()
   );

   private final Setting<Double> customSpeed = this.sgStrafe.add(
      new DoubleSetting.Builder()
         .name("custom-speed")
         .description("Custom horizontal speed multiplier.")
         .defaultValue(0.35)
         .min(0.1)
         .sliderMax(1.0)
         .visible(() -> this.mode.get() == Speed.Mode.Custom || this.mode.get() == Speed.Mode.Strafe)
         .build()
   );

   private final Setting<Boolean> autoJump = this.sgStrafe.add(
      new BoolSetting.Builder()
         .name("auto-jump")
         .description("Automatically hop when moving on ground.")
         .defaultValue(true)
         .visible(() -> this.mode.get() == Speed.Mode.Strafe || this.mode.get() == Speed.Mode.VulcanBHop)
         .build()
   );

   private final Setting<Double> boatSpeed = this.sgGrim.add(
      new DoubleSetting.Builder()
         .name("boat-speed")
         .description("Extra speed from boat exploit.")
         .defaultValue(0.8)
         .min(0.1)
         .sliderMax(1.2)
         .visible(() -> this.mode.get() == Speed.Mode.GrimBoat)
         .build()
   );

   private int tickCounter = 0;
   private int ticksSinceBoat = 999;

   public Speed() {
      // Keep Meteor's built-in speed module available; its mixins look it up by class.
      super(VenomousAddon.CATEGORY, "v-speed", "Adaptive speed engine with bypasses for Grim, Vulcan, Polar, Matrix, NCP, and Strafe.");
   }

   public void onActivate() {
      this.tickCounter = 0;
      this.ticksSinceBoat = 999;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player == null || this.mc.level == null) {
         return;
      }

      this.tickCounter++;
      boolean isMoving = this.mc.player.input.keyPresses.forward()
         || this.mc.player.input.keyPresses.backward()
         || this.mc.player.input.keyPresses.left()
         || this.mc.player.input.keyPresses.right();

      if (isMoving || this.mode.get() == Speed.Mode.GrimBoat) {
         switch (this.mode.get()) {
            case Strafe -> this.doStrafe();
            case GrimGround -> this.doGrimGround();
            case GrimBoat -> this.doGrimBoat();
            case VulcanGround -> this.doVulcanGround();
            case VulcanBHop -> this.doVulcanBHop();
            case PolarStrafe -> this.doPolarStrafe();
            case NCPHop -> this.doNCPHop();
            case WeakAC -> this.doWeakAC();
            case Vanilla -> this.doVanilla();
            case Custom -> this.doCustom();
         }
      }
   }

   private void doStrafe() {
      double baseSpeed = this.getBaseMoveSpeed();
      if (this.mc.player.onGround() && this.autoJump.get() && !this.mc.player.isInWater() && !this.mc.player.onClimbable()) {
         this.mc.player.jumpFromGround();
         baseSpeed *= 1.42;
      }

      this.setDirectionalSpeed(baseSpeed);
   }

   private void doGrimGround() {
      // GrimAC calculates block friction dynamically. Applying slight ground speed on flat surfaces without jumping
      double speed = this.getBaseMoveSpeed() * (this.mc.player.onGround() ? 1.0 : 0.87);
      this.setDirectionalSpeed(speed);
      this.mc.player.setSprinting(true);
   }

   private void doGrimBoat() {
      if (this.isBoatNearby()) {
         this.ticksSinceBoat = 0;
      } else {
         this.ticksSinceBoat++;
      }

      if (this.ticksSinceBoat <= 3) {
         this.setDirectionalSpeed(this.getBaseMoveSpeed() + this.boatSpeed.get());
      } else {
         this.mc.player.setSprinting(true);
      }
   }

   private void doVulcanGround() {
      // Vulcan predicts friction from the base attribute, so scale relative to the real
      // move speed instead of a hardcoded constant that breaks with Speed/Slowness.
      this.setDirectionalSpeed(this.getBaseMoveSpeed() * 1.12);
   }

   private void doVulcanBHop() {
      double base = this.getBaseMoveSpeed();
      if (this.mc.player.onGround() && this.autoJump.get()) {
         this.mc.player.jumpFromGround();
         this.setDirectionalSpeed(base * 1.28);
      } else {
         this.setDirectionalSpeed(base * 1.14);
      }
   }

   private void doPolarStrafe() {
      double base = this.getBaseMoveSpeed();
      double speed = base * 1.1;
      if (this.mc.player.onGround()) {
         this.mc.player.jumpFromGround();
         speed = base * 1.24;
      }
      this.setDirectionalSpeed(speed);
   }

   private void doNCPHop() {
      if (this.mc.player.onGround()) {
         this.mc.player.jumpFromGround();
      }
      this.setDirectionalSpeed(this.getBaseMoveSpeed());
   }

   private void doWeakAC() {
      this.setDirectionalSpeed(0.5);
      if (this.mc.player.onGround()) {
         this.mc.player.jumpFromGround();
      }
   }

   private void doVanilla() {
      this.setDirectionalSpeed(0.45);
      if (this.mc.player.onGround()) {
         this.mc.player.jumpFromGround();
      }
   }

   private void doCustom() {
      this.setDirectionalSpeed(this.customSpeed.get());
      if (this.mc.player.onGround() && this.autoJump.get()) {
         this.mc.player.jumpFromGround();
      }
   }

   private double getBaseMoveSpeed() {
      return MovementMath.baseSpeed(this.mc.player.isSprinting(), this.effectAmplifier(MobEffects.SPEED), this.effectAmplifier(MobEffects.SLOWNESS));
   }

   private int effectAmplifier(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
      var instance = this.mc.player.getEffect(effect);
      return instance == null ? -1 : instance.getAmplifier();
   }

   private void setDirectionalSpeed(double speed) {
      double fwd = this.mc.player.input.keyPresses.forward() ? 1.0 : (this.mc.player.input.keyPresses.backward() ? -1.0 : 0.0);
      double str = this.mc.player.input.keyPresses.left() ? 1.0 : (this.mc.player.input.keyPresses.right() ? -1.0 : 0.0);
      double[] move = MovementMath.velocity(this.mc.player.getYRot(), fwd, str, speed);
      if (move[0] != 0.0 || move[1] != 0.0) {
         this.mc.player.setDeltaMovement(move[0], this.mc.player.getDeltaMovement().y, move[1]);
      }
   }

   private boolean isBoatNearby() {
      return McCompat.<Entity>stream(this.mc.level.entitiesForRendering())
         .anyMatch(e -> e instanceof AbstractBoat && e != this.mc.player.getVehicle() && this.mc.player.distanceTo(e) < 2.5);
   }

   public enum Mode {
      Strafe,
      GrimGround,
      GrimBoat,
      VulcanGround,
      VulcanBHop,
      PolarStrafe,
      NCPHop,
      WeakAC,
      Vanilla,
      Custom;
   }
}
