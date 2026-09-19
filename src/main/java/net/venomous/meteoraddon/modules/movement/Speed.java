package net.venomous.meteoraddon.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.McCompat;

public class Speed extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgGrim = this.settings.createGroup("Grim");
   private final SettingGroup sgVulcan = this.settings.createGroup("Vulcan");
   private final SettingGroup sgNCP = this.settings.createGroup("NCP");
   private final SettingGroup sgWeakAC = this.settings.createGroup("Weak AC");
   private final Setting<Speed.Mode> mode = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("mode")).description("Speed mode — select your anticheat.")).defaultValue(Speed.Mode.GrimBoat))
            .build()
      );
   private final Setting<Double> boatSpeed = this.sgGrim
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("boat-speed"))
                  .description("Extra speed from boat exploit."))
               .defaultValue(0.8)
               .min(0.1)
               .sliderMax(1.2)
               .visible(() -> this.mode.get() == Speed.Mode.GrimBoat))
            .build()
      );
   private int tickCounter = 0;
   private int ticksSinceBoat = 999;

   public Speed() {
      super(VenomousAddon.CATEGORY, "speed", "Speed with bypasses for Grim/Vulcan/NCP/Oxa/Negativity/LAC/Themis.");
   }

   public void onActivate() {
      this.tickCounter = 0;
      this.ticksSinceBoat = 999;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null) {
         this.tickCounter++;
         boolean isMoving = this.mc.player.input.keyPresses.forward()
            || this.mc.player.input.keyPresses.backward()
            || this.mc.player.input.keyPresses.left()
            || this.mc.player.input.keyPresses.right();
         if (isMoving || this.mode.get() == Speed.Mode.GrimBoat) {
            switch ((Speed.Mode)this.mode.get()) {
               case GrimBoat:
                  this.doGrimBoat();
                  break;
               case VulcanBHop:
                  this.doVulcanBHop();
                  break;
               case NCPHop:
                  this.doNCPHop();
                  break;
               case WeakAC:
                  this.doWeakAC();
                  break;
               case Vanilla:
                  this.doVanilla();
            }
         }
      }
   }

   private void doGrimBoat() {
      if (this.isBoatNearby()) {
         this.ticksSinceBoat = 0;
      } else {
         this.ticksSinceBoat++;
      }

      if (this.ticksSinceBoat <= 3) {
         this.setDirectionalSpeed(0.287 + (Double)this.boatSpeed.get());
      } else {
         this.mc.player.setSprinting(true);
      }
   }

   private void doVulcanBHop() {
      if (this.mc.player.onGround()) {
         this.mc.player.jumpFromGround();
      }

      this.setDirectionalSpeed(0.33);
   }

   private void doNCPHop() {
      if (this.mc.player.onGround()) {
         this.mc.player.jumpFromGround();
      }

      this.setDirectionalSpeed(0.28);
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

   private void setDirectionalSpeed(double speed) {
      float yaw = this.mc.player.getYRot();
      double fwd = this.mc.player.input.keyPresses.forward() ? 1.0 : (this.mc.player.input.keyPresses.backward() ? -1 : 0);
      double str = this.mc.player.input.keyPresses.left() ? 1.0 : (this.mc.player.input.keyPresses.right() ? -1 : 0);
      if (fwd != 0.0 || str != 0.0) {
         double angle = Math.toRadians(yaw);
         if (fwd < 0.0) {
            angle += Math.PI;
         }

         if (str > 0.0) {
            angle -= (Math.PI / 2) * (fwd == 0.0 ? 1.0 : 0.5);
         }

         if (str < 0.0) {
            angle += (Math.PI / 2) * (fwd == 0.0 ? 1.0 : 0.5);
         }

         this.mc.player.setDeltaMovement(-Math.sin(angle) * speed, this.mc.player.getDeltaMovement().y, Math.cos(angle) * speed);
      }
   }

   private boolean isBoatNearby() {
      return McCompat.<Entity>stream(this.mc.level.entitiesForRendering())
         .anyMatch(e -> e instanceof AbstractBoat && e != this.mc.player.getVehicle() && this.mc.player.distanceTo(e) < 2.5);
   }

   public enum Mode {
      GrimBoat,
      VulcanBHop,
      NCPHop,
      WeakAC,
      Vanilla;
   }
}
