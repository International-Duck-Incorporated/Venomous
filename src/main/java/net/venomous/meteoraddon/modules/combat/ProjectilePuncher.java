package net.venomous.meteoraddon.modules.combat;

import java.util.Random;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.venomous.meteoraddon.VenomousAddon;

public class ProjectilePuncher extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> range = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("range")).description("Range to punch projectiles.")).defaultValue(4.0).range(1.0, 6.0).sliderMax(6.0).build()
      );
   private final Setting<Integer> minCps = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("min-cps"))
                  .description("Minimum clicks per second for punching."))
               .defaultValue(6))
            .range(1, 20)
            .sliderMax(15)
            .build()
      );
   private final Setting<Integer> maxCps = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("max-cps"))
                  .description("Maximum clicks per second for punching."))
               .defaultValue(9))
            .range(1, 20)
            .sliderMax(15)
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("rotate"))
                  .description("Rotate to projectile before punching."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> rotationSpeed = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("rotation-speed")).description("Rotation speed (degrees per tick)."))
               .defaultValue(180.0)
               .range(30.0, 360.0)
               .sliderMax(360.0)
               .visible(this.rotate::get))
            .build()
      );
   private final Setting<Boolean> onlyIncoming = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("only-incoming"))
                  .description("Only punch projectiles heading toward you."))
               .defaultValue(false))
            .build()
      );
   private final Setting<Boolean> respectCooldown = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("respect-cooldown"))
                  .description("Respect attack cooldown (humanlike)."))
               .defaultValue(true))
            .build()
      );
   private int ticksUntilPunch = 0;
   private final Random random = new Random();

   public ProjectilePuncher() {
      super(VenomousAddon.CATEGORY, "projectile-puncher", "Automatically punches incoming projectiles with humanlike timing.");
   }

   public void onActivate() {
      this.ticksUntilPunch = 0;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.level != null) {
         this.ticksUntilPunch--;
         if (this.ticksUntilPunch <= 0) {
            Entity closestProjectile = this.findProjectile();
            if (closestProjectile != null) {
               if ((Boolean)this.rotate.get()) {
                  this.rotateTo(closestProjectile);
               }

               if ((Boolean)this.rotate.get()) {
                  double dx = closestProjectile.getX() - this.mc.player.getX();
                  double dy = closestProjectile.getY() - (this.mc.player.getY() + this.mc.player.getEyeHeight());
                  double dz = closestProjectile.getZ() - this.mc.player.getZ();
                  double dist = Math.sqrt(dx * dx + dz * dz);
                  float targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
                  float targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, dist)));
                  float yawDiff = Math.abs(this.wrapDegrees(targetYaw - this.mc.player.getYRot()));
                  float pitchDiff = Math.abs(this.wrapDegrees(targetPitch - this.mc.player.getXRot()));
                  if (yawDiff > 45.0F || pitchDiff > 45.0F) {
                     return;
                  }
               }

               if (!(Boolean)this.respectCooldown.get() || !(this.mc.player.getAttackStrengthScale(0.0F) < 0.9F)) {
                  this.mc.player.attack(closestProjectile);
                  this.mc.player.swing(InteractionHand.MAIN_HAND);
                  this.mc.player.resetAttackStrengthTicker();
                  int avgCps = ((Integer)this.minCps.get() + (Integer)this.maxCps.get()) / 2;
                  double baseInterval = 20.0 / avgCps;
                  double variation = (this.random.nextDouble() - 0.5) * 2.0 * ((Integer)this.maxCps.get() - (Integer)this.minCps.get()) / avgCps;
                  this.ticksUntilPunch = Math.max(1, (int)Math.round(baseInterval + variation));
               }
            }
         }
      }
   }

   private Entity findProjectile() {
      Entity closest = null;
      double closestDist = (Double)this.range.get() * (Double)this.range.get();

      for (Entity entity : this.mc.level.entitiesForRendering()) {
         if (entity instanceof Projectile && entity.isAlive()) {
            if ((Boolean)this.onlyIncoming.get()) {
               double dx = this.mc.player.getX() - entity.getX();
               double dz = this.mc.player.getZ() - entity.getZ();
               double motionToPlayer = entity.getDeltaMovement().x * dx + entity.getDeltaMovement().z * dz;
               if (motionToPlayer <= 0.0) {
                  continue;
               }
            }

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
      double dy = target.getY() - (this.mc.player.getY() + this.mc.player.getEyeHeight());
      double dz = target.getZ() - this.mc.player.getZ();
      double dist = Math.sqrt(dx * dx + dz * dz);
      float targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
      float targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, dist)));
      float yawDiff = this.wrapDegrees(targetYaw - this.mc.player.getYRot());
      float pitchDiff = this.wrapDegrees(targetPitch - this.mc.player.getXRot());
      float maxTurn = ((Double)this.rotationSpeed.get()).floatValue() / 20.0F;
      if (Math.abs(yawDiff) > maxTurn) {
         yawDiff = Math.signum(yawDiff) * maxTurn;
      }

      if (Math.abs(pitchDiff) > maxTurn) {
         pitchDiff = Math.signum(pitchDiff) * maxTurn;
      }

      this.mc.player.setYRot(this.mc.player.getYRot() + yawDiff);
      this.mc.player.setXRot(this.mc.player.getXRot() + pitchDiff);
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

   public String getInfoString() {
      return this.ticksUntilPunch > 0 ? "waiting" : "ready";
   }
}
