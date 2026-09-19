package net.venomous.meteoraddon.modules.combat;

import java.util.Random;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.venomous.meteoraddon.VenomousAddon;

public class VKillAura extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgVulcan = this.settings.createGroup("Vulcan");
   private final Setting<Double> range = this.sgGeneral
      .add(((Builder)((Builder)new Builder().name("range")).description("Attack range.")).defaultValue(3.8).range(1.0, 6.0).sliderMax(6.0).build());
   private final Setting<Boolean> players = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("players"))
                  .description("Target players."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> mobs = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("mobs"))
                  .description("Target mobs."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> animals = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("animals"))
                  .description("Target animals."))
               .defaultValue(false))
            .build()
      );
   private final Setting<VKillAura.Technique> technique = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("technique"))
                  .description("Click pattern."))
               .defaultValue(VKillAura.Technique.Stabilized))
            .build()
      );
   private final Setting<Integer> minCps = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("min-cps"))
                  .description("Min CPS."))
               .defaultValue(8))
            .range(1, 20)
            .sliderMax(15)
            .build()
      );
   private final Setting<Integer> maxCps = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("max-cps"))
                  .description("Max CPS."))
               .defaultValue(11))
            .range(1, 20)
            .sliderMax(15)
            .build()
      );
   private final Setting<Boolean> respectCooldown = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("respect-cooldown"))
                  .description("Respect attack cooldown (REQUIRED for Grim/NCP)."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("rotate"))
                  .description("Rotate to target."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> rotationSpeed = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("rotation-speed")).description("Rotation speed (deg/tick)."))
               .defaultValue(120.0)
               .range(10.0, 360.0)
               .sliderMax(360.0)
               .visible(this.rotate::get))
            .build()
      );
   private final Setting<Double> rotationThreshold = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("rotation-threshold")).description("Angle threshold to attack."))
               .defaultValue(30.0)
               .range(1.0, 180.0)
               .sliderMax(90.0)
               .visible(this.rotate::get))
            .build()
      );
   private final Setting<Boolean> autoBlock = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("auto-block"))
                  .description("Block while not attacking."))
               .defaultValue(false))
            .build()
      );
   private final Setting<VKillAura.ACMode> acMode = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("ac-mode"))
                  .description("Anticheat mode — tunes settings automatically."))
               .defaultValue(VKillAura.ACMode.Grim))
            .build()
      );
   private final Setting<Boolean> vulcanSpeedRandomization = this.sgVulcan
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("speed-randomization"))
                     .description("Add ±100ms randomization to attack timing (bypasses Vulcan AutoClicker)."))
                  .defaultValue(true))
               .visible(() -> this.acMode.get() == VKillAura.ACMode.Vulcan))
            .build()
      );
   private final Setting<Integer> vulcanRandomizationMs = this.sgVulcan
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("randomization-ms"))
                     .description("±Milliseconds of attack timing randomization."))
                  .defaultValue(100))
               .range(20, 300)
               .visible(() -> this.acMode.get() == VKillAura.ACMode.Vulcan && (Boolean)this.vulcanSpeedRandomization.get()))
            .build()
      );
   private int ticksUntilAttack = 0;
   private Entity target = null;
   private final Random random = new Random();

   public VKillAura() {
      super(VenomousAddon.CATEGORY, "v-annihilate", "KillAura bypasses for Grim/Vulcan/NCP/Oxa/Negativity/LAC.");
   }

   public void onActivate() {
      this.ticksUntilAttack = 0;
      this.target = null;
   }

   public void onDeactivate() {
      if (this.mc.player != null && (Boolean)this.autoBlock.get()) {
         this.mc.options.keyUse.setDown(false);
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.level != null) {
         if (this.acMode.get() == VKillAura.ACMode.Grim && !this.isBoatNearby()) {
            this.placeBoat();
         }

         this.target = this.findTarget();
         if (this.target == null) {
            if ((Boolean)this.autoBlock.get()) {
               this.mc.options.keyUse.setDown(false);
            }
         } else {
            if ((Boolean)this.rotate.get()) {
               this.rotateTo(this.target);
            }

            if ((Boolean)this.rotate.get()) {
               double angle = this.getAngleTo(this.target);
               double thresh = this.acMode.get() == VKillAura.ACMode.Grim
                  ? Math.min((Double)this.rotationThreshold.get(), 15.0)
                  : (Double)this.rotationThreshold.get();
               if (angle > thresh) {
                  if ((Boolean)this.autoBlock.get()) {
                     this.mc.options.keyUse.setDown(true);
                  }

                  return;
               }
            }

            this.ticksUntilAttack--;
            if (this.ticksUntilAttack <= 0) {
               this.doAttack();
               this.ticksUntilAttack = this.getNextAttackDelay();
            } else if ((Boolean)this.autoBlock.get()) {
               this.mc.options.keyUse.setDown(true);
            }
         }
      }
   }

   private void doAttack() {
      if (this.mc.player != null && this.target != null) {
         float minStrength = this.acMode.get() != VKillAura.ACMode.Grim && this.acMode.get() != VKillAura.ACMode.NCP ? 0.9F : 1.0F;
         if (!(Boolean)this.respectCooldown.get() || !(this.mc.player.getAttackStrengthScale(0.0F) < minStrength)) {
            if ((Boolean)this.autoBlock.get()) {
               this.mc.options.keyUse.setDown(false);
            }

            this.mc.player.attack(this.target);
            this.mc.player.swing(InteractionHand.MAIN_HAND);
            this.mc.player.resetAttackStrengthTicker();
         }
      }
   }

   private int getNextAttackDelay() {
      int avg = ((Integer)this.minCps.get() + (Integer)this.maxCps.get()) / 2;
      double base = 20.0 / avg;
      if (this.acMode.get() == VKillAura.ACMode.Vulcan) {
         base = 20.0 / Math.max(9, Math.min(12, avg));
      }

      double var = (this.random.nextDouble() - 0.5) * 2.0 * ((Integer)this.maxCps.get() - (Integer)this.minCps.get()) / avg;

      double interval = switch ((VKillAura.Technique)this.technique.get()) {
         case Stabilized -> base + var;
         case Butterfly -> this.random.nextBoolean() ? 20.0 / Math.max(1, avg + 2) : 20.0 / Math.max(1, avg - 2);
         case NormalDistribution -> base + this.random.nextGaussian() * base * 0.25;
         case Efficient -> base;
         case Switch -> base + (this.random.nextDouble() - 0.5) * 2.0;
      };
      if (this.acMode.get() == VKillAura.ACMode.Vulcan && (Boolean)this.vulcanSpeedRandomization.get()) {
         double msVariation = (this.random.nextDouble() - 0.5) * 2.0 * ((Integer)this.vulcanRandomizationMs.get()).intValue();
         interval += msVariation / 50.0;
      }

      return Math.max(1, (int)Math.round(interval));
   }

   private Entity findTarget() {
      double effectiveRange = (Double)this.range.get();
      if (this.acMode.get() == VKillAura.ACMode.Grim) {
         effectiveRange = Math.min((Double)this.range.get(), 3.0 + (this.isBoatNearby() ? 0.2 : 0.0));
      } else if (this.acMode.get() == VKillAura.ACMode.Vulcan) {
         effectiveRange = Math.min((Double)this.range.get(), 3.2);
      } else if (this.acMode.get() == VKillAura.ACMode.NCP) {
         effectiveRange = Math.min((Double)this.range.get(), 3.1);
      }

      Entity closest = null;
      double closestDist = effectiveRange * effectiveRange;

      for (Entity entity : this.mc.level.entitiesForRendering()) {
         if (entity != this.mc.player
            && entity.isAlive()
            && !(
               entity instanceof Player p
                  ? p.isSpectator() || !Friends.get().shouldAttack(p) || !(Boolean)this.players.get()
                  : !(entity instanceof LivingEntity) || (entity instanceof Animal ? !(Boolean)this.animals.get() : !(Boolean)this.mobs.get())
            )) {
            double dist = this.mc.player.distanceToSqr(entity);
            if (dist < closestDist) {
               closestDist = dist;
               closest = entity;
            }
         }
      }

      return closest;
   }

   private void rotateTo(Entity target) {
      double dx = target.getX() - this.mc.player.getX();
      double dy = target.getY() + target.getEyeHeight() * 0.8 - (this.mc.player.getY() + this.mc.player.getEyeHeight());
      double dz = target.getZ() - this.mc.player.getZ();
      double dist = Math.sqrt(dx * dx + dz * dz);
      float targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
      float targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, dist)));
      float yawDiff = this.wrapDegrees(targetYaw - this.mc.player.getYRot());
      float pitchDiff = this.wrapDegrees(targetPitch - this.mc.player.getXRot());
      float maxTurn = this.acMode.get() == VKillAura.ACMode.Grim ? 80.0F : ((Double)this.rotationSpeed.get()).floatValue();
      if (Math.abs(yawDiff) > maxTurn) {
         yawDiff = Math.signum(yawDiff) * maxTurn;
      }

      if (Math.abs(pitchDiff) > maxTurn) {
         pitchDiff = Math.signum(pitchDiff) * maxTurn;
      }
      double jitter = switch ((VKillAura.ACMode)this.acMode.get()) {
         case Grim -> 1.0;
         case Vulcan -> 1.5;
         case NCP -> 0.5;
         default -> 0.3;
      };
      if (jitter > 0.0) {
         yawDiff += (this.random.nextFloat() - 0.5F) * 2.0F * (float)jitter;
         pitchDiff += (this.random.nextFloat() - 0.5F) * 2.0F * (float)jitter * 0.3F;
      }

      this.mc.player.setYRot(this.mc.player.getYRot() + yawDiff);
      this.mc.player.setXRot(this.mc.player.getXRot() + pitchDiff);
   }

   private double getAngleTo(Entity target) {
      double dx = target.getX() - this.mc.player.getX();
      double dy = target.getY() + target.getEyeHeight() - (this.mc.player.getY() + this.mc.player.getEyeHeight());
      double dz = target.getZ() - this.mc.player.getZ();
      double dist = Math.sqrt(dx * dx + dz * dz);
      float yawDiff = Math.abs(this.wrapDegrees((float)Math.toDegrees(Math.atan2(-dx, dz)) - this.mc.player.getYRot()));
      float pitchDiff = Math.abs(this.wrapDegrees((float)(-Math.toDegrees(Math.atan2(dy, dist))) - this.mc.player.getXRot()));
      return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
   }

   private float wrapDegrees(float v) {
      while (v > 180.0F) {
         v -= 360.0F;
      }

      while (v < -180.0F) {
         v += 360.0F;
      }

      return v;
   }

   private boolean isBoatNearby() {
      if (this.mc.level == null) {
         return false;
      }

      for (Entity e : this.mc.level.entitiesForRendering()) {
         if (e instanceof AbstractBoat && e != this.mc.player.getVehicle() && this.mc.player.distanceTo(e) < 2.5) {
            return true;
         }
      }

      return false;
   }

   private void placeBoat() {
      int slot = -1;

      for (int i = 0; i < 9; i++) {
         ItemStack s = this.mc.player.getInventory().getItem(i);
         if (!s.isEmpty() && s.getItem().toString().toLowerCase().contains("boat")) {
            slot = i;
            break;
         }
      }

      if (slot != -1) {
         int prev = this.mc.player.getInventory().getSelectedSlot();
         this.mc.player.getInventory().setSelectedSlot(slot);
         float pp = this.mc.player.getXRot();
         this.mc.player.setXRot(80.0F);
         this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
         this.mc.player.setXRot(pp);
         this.mc.player.getInventory().setSelectedSlot(prev);
      }
   }

   public String getInfoString() {
      String extra = switch ((VKillAura.ACMode)this.acMode.get()) {
         case Grim -> this.isBoatNearby() ? " [Grim+Boat ⚡]" : " [Grim]";
         case Vulcan -> " [Vulcan]";
         case NCP -> " [NCP]";
         case WeakAC -> " [Weak]";
         default -> "";
      };
      return (this.target != null ? this.target.getName().getString() : null) + extra;
   }

   public enum ACMode {
      Grim,
      Vulcan,
      NCP,
      WeakAC,
      None;
   }

   public enum Technique {
      Stabilized,
      Butterfly,
      NormalDistribution,
      Efficient,
      Switch;
   }
}
