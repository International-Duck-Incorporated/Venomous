package net.venomous.meteoraddon.modules.combat;

import java.util.Random;
import java.util.Set;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting.Builder;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

public class Aimbot extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgProjectile = this.settings.createGroup("Projectile");
   private final SettingGroup sgHumanlike = this.settings.createGroup("Humanlike");
   private final Setting<Set<EntityType<?>>> entities = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("entities")).description("Entities to aim at.")).defaultValue(Set.of(EntityTypes.PLAYER))).build());
   private final Setting<Double> range = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("range"))
               .description("The range at which an entity can be targeted."))
            .defaultValue(5.0)
            .min(0.0)
            .sliderMax(100.0)
            .build()
      );
   private final Setting<Target> aimTarget = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("aim-target"))
                  .description("Which part of the body to aim at."))
               .defaultValue(Target.Body))
            .build()
      );
   private final Setting<Boolean> ignoreWalls = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("ignore-walls"))
                  .description("Whether or not to ignore aiming through walls."))
               .defaultValue(false))
            .build()
      );
   private final Setting<Boolean> predict = this.sgProjectile
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("predict"))
                  .description("Predict target movement for projectiles."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> autoShoot = this.sgProjectile
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("auto-shoot"))
                  .description("Automatically release bow or fire crossbow."))
               .defaultValue(false))
            .build()
      );
   private final Setting<Double> speed = this.sgHumanlike
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("speed"))
               .description("How fast to aim at the entity."))
            .defaultValue(5.0)
            .min(1.0)
            .sliderMax(20.0)
            .build()
      );
   private final Setting<Boolean> serverSide = this.sgHumanlike
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("server-side"))
                  .description("Spoof rotations to the server to avoid jittery camera for the player."))
               .defaultValue(true))
            .build()
      );
   private final Random random = new Random();
   private Entity target;

   public Aimbot() {
      super(VenomousAddon.CATEGORY, "aimbot", "Smooth, human-like aimbot with projectile prediction.");
   }

   @EventHandler
   private void onTick(Post event) {
      this.target = TargetUtils.get(entity -> {
         if (!entity.isAlive() || entity == this.mc.player) {
            return false;
         } else if (!PlayerUtils.isWithin(entity, (Double)this.range.get())) {
            return false;
         } else if (!(Boolean)this.ignoreWalls.get() && !PlayerUtils.canSeeEntity(entity)) {
            return false;
         } else {
            return !((Set)this.entities.get()).contains(entity.getType()) ? false : !(entity instanceof Player) || Friends.get().shouldAttack((Player)entity);
         }
      }, SortPriority.LowestDistance);
      if (this.target != null) {
         this.aim(this.target);
         if ((Boolean)this.autoShoot.get()) {
            this.handleAutoShoot();
         }
      }
   }

   private void aim(Entity target) {
      Vec3 targetPos = this.getTargetPos(target);
      double dx = targetPos.x - this.mc.player.getX();
      double dy = targetPos.y - (this.mc.player.getY() + this.mc.player.getEyeHeight());
      double dz = targetPos.z - this.mc.player.getZ();
      double horizontalDist = Math.sqrt(dx * dx + dz * dz);
      float targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
      float targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, horizontalDist)));
      if (this.isHoldingProjectile() && (Boolean)this.predict.get()) {
         double v = 3.0;
         double g = 0.05;
         double angle = Math.atan2(dy + 0.5 * g * horizontalDist / v, horizontalDist);
         targetPitch = (float)Math.toDegrees(-angle);
      }

      if ((Boolean)this.serverSide.get()) {
         Rotations.rotate(targetYaw, targetPitch, 10, null);
      } else {
         this.mc.player.setYRot(this.smooth(this.mc.player.getYRot(), targetYaw));
         this.mc.player.setXRot(this.smooth(this.mc.player.getXRot(), targetPitch));
      }
   }

   private float smooth(float current, float target) {
      float diff = this.wrap(target - current);
      float step = (float)((Double)this.speed.get() + this.random.nextDouble());
      if (Math.abs(diff) > step) {
         diff = Math.signum(diff) * step;
      }

      return current + diff;
   }

   private float wrap(float v) {
      while (v > 180.0F) {
         v -= 360.0F;
      }

      while (v < -180.0F) {
         v += 360.0F;
      }

      return v;
   }

   private Vec3 getTargetPos(Entity target) {
      Vec3 pos = target.position();
      if ((Boolean)this.predict.get()) {
         double dist = this.mc.player.distanceTo(target);
         double ticks = dist / 3.0;
         pos = pos.add((target.getX() - target.xOld) * ticks, (target.getY() - target.yOld) * ticks, (target.getZ() - target.zOld) * ticks);
      }

      double height = target.getEyeHeight();
      if (this.aimTarget.get() == Target.Body) {
         height /= 2.0;
      }

      return pos.add(0.0, height, 0.0);
   }

   private boolean isHoldingProjectile() {
      Item item = this.mc.player.getMainHandItem().getItem();
      return item == Items.BOW || item == Items.CROSSBOW || item == Items.TRIDENT;
   }

   private void handleAutoShoot() {
      Item item = this.mc.player.getMainHandItem().getItem();
      if (item == Items.BOW) {
         float charge = (this.mc.player.getUseItem().getUseDuration(this.mc.player) - this.mc.player.getUseItemRemainingTicks()) / 20.0F;
         if (!this.mc.player.isUsingItem()) {
            this.mc.options.keyUse.setDown(true);
         } else if (charge >= 0.9) {
            this.mc.options.keyUse.setDown(false);
         }
      } else if (item == Items.CROSSBOW && !this.mc.player.isUsingItem()) {
         this.mc.options.keyUse.setDown(true);
         this.mc.options.keyUse.setDown(false);
      }
   }
}
