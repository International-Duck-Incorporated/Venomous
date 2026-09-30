package net.venomous.meteoraddon.modules.combat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;

public class MaceKill extends Module {
   private final SettingGroup sgGeneral = this.settings.createGroup("General");
   private final SettingGroup sgTarget = this.settings.createGroup("Targeting");
   private final SettingGroup sgVelocity = this.settings.createGroup("Velocity");
   private final SettingGroup sgSafety = this.settings.createGroup("Safety");
   private final Setting<MaceKill.Mode> mode = this.sgGeneral.add(
      new EnumSetting.Builder<MaceKill.Mode>()
         .name("mode")
         .description("Slam physically launches you above the target; FakeFall spoofs fall distance with position packets.")
         .defaultValue(MaceKill.Mode.Slam)
         .build()
   );

   private final Setting<Integer> fakeFallHeight = this.sgGeneral.add(
      new IntSetting.Builder()
         .name("fake-fall-height")
         .description("Fall height to spoof when attacking with a mace.")
         .defaultValue(22)
         .range(1, 170)
         .sliderMax(170)
         .visible(() -> this.mode.get() == MaceKill.Mode.FakeFall)
         .build()
   );

   private final Setting<Boolean> fakeFallOnlyOnGround = this.sgGeneral.add(
      new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
         .name("fake-fall-only-on-ground")
         .description("Only spoof fall height while standing on the ground.")
         .defaultValue(true)
         .visible(() -> this.mode.get() == MaceKill.Mode.FakeFall)
         .build()
   );

   private final Setting<Boolean> swingHand = this.sgGeneral
      .add(
         new BoolSetting.Builder().name("swing-hand").description("Send swing animation alongside the attack.").defaultValue(true).build()
      );
   private final Setting<Boolean> chatFlush = this.sgGeneral
      .add(
         new BoolSetting.Builder().name("chat-feedback").description("Log attack distance in chat on impact.").defaultValue(false).build()
      );
   private final Setting<Double> maxRange = this.sgTarget
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("max-range")
            .description("Maximum targeting range in blocks.")
            .defaultValue(256.0)
            .min(0.0)
            .sliderMax(512.0)
            .build()
      );
   private final Setting<MaceKill.TargetListMode> targetListMode = this.sgTarget
      .add(
         new meteordevelopment.meteorclient.settings.EnumSetting.Builder<MaceKill.TargetListMode>()
            .name("target-list-mode")
            .description("Whitelist = only target these. Blacklist = never target these.")
            .defaultValue(MaceKill.TargetListMode.Blacklist)
            .build()
      );
   private final Setting<Set<EntityType<?>>> targetEntities = this.sgTarget
      .add(
         new meteordevelopment.meteorclient.settings.EntityTypeListSetting.Builder()
            .name("target-entities")
            .description("Entities to whitelist or blacklist.")
            .onlyAttackable()
            .build()
      );
   private final Setting<Boolean> ignoreFriends = this.sgTarget
      .add(new BoolSetting.Builder().name("ignore-friends").description("Don't attack Meteor friends.").defaultValue(true).build());
   private final Setting<Boolean> stopOnTarget = this.sgTarget
      .add(
         new BoolSetting.Builder().name("stop-on-target").description("Kill velocity when you reach the target.").defaultValue(true)
            .build()
      );
   private final Setting<Double> stopDistance = this.sgTarget
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("stop-distance")
            .description("Hitbox inflation for stop check.")
            .defaultValue(2.0)
            .min(0.0)
            .sliderMax(10.0)
            .visible(this.stopOnTarget::get)
            .build()
      );
   private final Setting<Double> aboveHeight = this.sgVelocity
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("above-height")
            .description("How many blocks above the target to launch before slamming down.")
            .defaultValue(22.0)
            .min(5.0)
            .sliderMax(100.0)
            .build()
      );
   private final Setting<Double> velocitySpeed = this.sgVelocity
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("velocity-speed")
            .description("Multiplier for all velocity.")
            .defaultValue(1.0)
            .min(0.25)
            .sliderMax(5.0)
            .build()
      );
   private final Setting<Double> upwardSpeed = this.sgVelocity
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("upward-speed")
            .description("Base upward velocity during launch phase.")
            .defaultValue(2.5)
            .min(0.5)
            .sliderMax(10.0)
            .build()
      );
   private final Setting<Double> downwardSpeed = this.sgVelocity
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("downward-speed")
            .description("Base downward velocity during slam phase.")
            .defaultValue(5.0)
            .min(1.0)
            .sliderMax(15.0)
            .build()
      );
   private final Setting<Double> nearTargetThreshold = this.sgVelocity
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("near-threshold")
            .description("Distance from above-target position at which the slam phase begins.")
            .defaultValue(3.0)
            .min(1.0)
            .sliderMax(15.0)
            .build()
      );
   private final Setting<Boolean> useOffset = this.sgSafety
      .add(
         new BoolSetting.Builder().name("safe-offset").description("Apply a small safe offset after attacking to prevent fall damage.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> offsetHorizontal = this.sgSafety
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("horizontal-offset")
            .description("Horizontal nudge after attacking.")
            .defaultValue(0.05)
            .min(0.0)
            .sliderMax(0.99)
            .visible(this.useOffset::get)
            .build()
      );
   private final Setting<Double> offsetY = this.sgSafety
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("y-offset")
            .description("Vertical nudge after attacking.")
            .defaultValue(0.01)
            .min(0.0)
            .sliderMax(0.99)
            .visible(this.useOffset::get)
            .build()
      );
   private final Setting<Boolean> checkPathInvalid = this.sgSafety
      .add(
         new BoolSetting.Builder().name("validate-above-path")
            .description("Cancel the attack if the path or position above the target is obstructed.")
            .defaultValue(true)
            .build()
      );
   private MaceKill.Phase phase = MaceKill.Phase.IDLE;
   private LivingEntity killTarget;
   private Vec3 aboveTargetPos;
   private boolean hasAttacked;
   private int attackCooldown;
   private Vec3 previousPos;
   private int phaseTicks;
   private final MutableBlockPos mutablePos = new MutableBlockPos();
   private final Map<Vec3, Boolean> positionCache = new LinkedHashMap<Vec3, Boolean>(256, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Entry<Vec3, Boolean> eldest) {
         return this.size() > 256;
      }
   };

   /**
    * FakeFall mode, merged in from the former v-mace-kill module.
    *
    * <p>Rather than physically launching the player, this spoofs the fall distance the server sees
    * by sending a raised position immediately followed by the real one, so the mace's fall-damage
    * bonus applies without any actual movement.
    */
   @EventHandler
   private void onAttackEntity(AttackEntityEvent event) {
      if (this.mode.get() != MaceKill.Mode.FakeFall) {
         return;
      }
      if (this.mc.player == null || this.mc.level == null || this.mc.player.connection == null) {
         return;
      }
      if (!this.isHoldingMace()) {
         return;
      }
      if (this.fakeFallOnlyOnGround.get() && !this.mc.player.onGround()) {
         return;
      }

      int height = this.determineFakeFallHeight();
      if (height <= 0) {
         return;
      }

      Vec3 pos = this.mc.player.getPosition(0.0F);
      boolean collided = this.mc.player.horizontalCollision;
      this.mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(pos.x, pos.y + height, pos.z, false, collided));
      this.mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(pos.x, pos.y, pos.z, false, collided));
   }

   /** Largest unobstructed spoof height up to the configured maximum. */
   private int determineFakeFallHeight() {
      AABB box = this.mc.player.getBoundingBox();
      for (int i = this.fakeFallHeight.get(); i >= 1; i--) {
         if (this.mc.level.noBlockCollision(this.mc.player, box.move(0.0, i, 0.0))) {
            return i;
         }
      }
      return 0;
   }

   public enum Mode {
      /** Launch above the target and slam down (original mace-kill behaviour). */
      Slam,
      /** Spoof fall distance with position packets (former v-mace-kill). */
      FakeFall
   }

   public MaceKill() {
      super(VenomousAddon.CATEGORY, "mace-kill", "Velocity-launches you above a target, then slams down with the mace.");
   }

   public void onActivate() {
      this.resetState();
   }

   public void onDeactivate() {
      this.resetState();
   }

   private void resetState() {
      if (this.phase != MaceKill.Phase.IDLE && this.mc.player != null) {
         this.mc.player.setDeltaMovement(Vec3.ZERO);
         this.mc.player.setSprinting(false);
      }

      this.phase = MaceKill.Phase.IDLE;
      this.killTarget = null;
      this.aboveTargetPos = null;
      this.hasAttacked = false;
      this.attackCooldown = 0;
      this.previousPos = null;
      this.phaseTicks = 0;
      this.positionCache.clear();
   }

   private boolean isHoldingMace() {
      return this.mc.player != null && this.mc.player.getMainHandItem().getItem() == Items.MACE;
   }

   @EventHandler
   private void onPreTick(Pre event) {
      if (this.mode.get() != MaceKill.Mode.Slam) {
         if (this.phase != MaceKill.Phase.IDLE) {
            this.resetState();
         }
         return;
      }

      if (this.mc.player != null && this.mc.level != null) {
         if (!this.mc.player.isPassenger()) {
            if (!this.isHoldingMace()) {
               this.resetState();
            } else if (this.attackCooldown > 0) {
               this.attackCooldown--;
            } else {
               switch (this.phase) {
                  case IDLE:
                     this.killTarget = this.findTarget();
                     if (this.killTarget == null) {
                        return;
                     }

                     if (!this.isValidTarget(this.killTarget)) {
                        this.killTarget = null;
                        return;
                     }

                     Vec3 targetCenter = this.killTarget.getBoundingBox().getCenter();
                     this.aboveTargetPos = new Vec3(targetCenter.x, targetCenter.y + (Double)this.aboveHeight.get(), targetCenter.z);
                     if ((Boolean)this.checkPathInvalid.get() && !this.isAbovePathValid(this.aboveTargetPos, this.killTarget)) {
                        this.killTarget = null;
                        this.aboveTargetPos = null;
                        return;
                     }

                     double ceiling = this.mc.level.getMaxY() - 1;
                     if (this.aboveTargetPos.y > ceiling) {
                        this.aboveTargetPos = new Vec3(this.aboveTargetPos.x, ceiling, this.aboveTargetPos.z);
                        if (this.aboveTargetPos.y - this.mc.player.getY() < 5.0) {
                           this.killTarget = null;
                           this.aboveTargetPos = null;
                           return;
                        }
                     }

                     this.hasAttacked = false;
                     this.previousPos = this.mc.player.position();
                     this.phaseTicks = 0;
                     this.phase = MaceKill.Phase.LAUNCHING_UP;
                     break;
                  case LAUNCHING_UP:
                     if (this.killTarget == null || !this.killTarget.isAlive() || !this.isValidTarget(this.killTarget)) {
                        this.resetState();
                        return;
                     }

                     this.phaseTicks++;
                     Vec3 launchTargetCenter = this.killTarget.getBoundingBox().getCenter();
                     this.aboveTargetPos = this.clampedAboveTarget(launchTargetCenter);
                     double distToAbove = this.mc.player.position().distanceTo(this.aboveTargetPos);
                     double verticalRemaining = this.aboveTargetPos.y - this.mc.player.getY();
                     if (verticalRemaining <= 0.75 || distToAbove <= (Double)this.nearTargetThreshold.get() || this.phaseTicks >= this.getMaxLaunchTicks()) {
                        this.startSlam();
                        this.applySlamVelocity();
                        return;
                     }

                     this.rotateToTarget(this.killTarget);
                     this.applyLaunchVelocity();
                     break;
                  case SLAMMING_DOWN:
                     if (this.killTarget == null || !this.killTarget.isAlive() || !this.isValidTarget(this.killTarget)) {
                        this.resetState();
                        return;
                     }

                     this.phaseTicks++;
                     this.rotateToTarget(this.killTarget);
                     this.applySlamVelocity();
                     if (!this.hasAttacked && this.shouldAttack(this.killTarget)) {
                        this.finishAttack(this.killTarget);
                        return;
                     }

                     double distToTarget = this.mc.player.position().distanceTo(this.killTarget.getBoundingBox().getCenter());
                     if (distToTarget > (Double)this.maxRange.get() * 1.5
                        || this.mc.player.getY() < this.killTarget.getBoundingBox().minY - 8.0
                        || this.phaseTicks >= this.getMaxSlamTicks()) {
                        this.resetState();
                        return;
                     }
               }
            }
         }
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.mc.player != null && this.mc.level != null) {
         if (this.isHoldingMace()) {
            if (this.killTarget != null && this.killTarget.isAlive()) {
               this.rotateToTarget(this.killTarget);
            }
         }
      }
   }

   private Vec3 clampedAboveTarget(Vec3 targetCenter) {
      double ceiling = this.mc.level.getMaxY() - 1;
      double y = Mth.clamp(targetCenter.y + (Double)this.aboveHeight.get(), this.mc.level.getMinY() + 1, ceiling);
      return new Vec3(targetCenter.x, y, targetCenter.z);
   }

   private int getMaxLaunchTicks() {
      return Math.max(20, (int)Math.ceil(((Double)this.aboveHeight.get() + 8.0) / Math.max(0.25, (Double)this.upwardSpeed.get()) * 8.0));
   }

   private int getMaxSlamTicks() {
      return Math.max(20, (int)Math.ceil(((Double)this.aboveHeight.get() + 16.0) / Math.max(1.0, (Double)this.downwardSpeed.get()) * 8.0));
   }

   private void startSlam() {
      this.phase = MaceKill.Phase.SLAMMING_DOWN;
      this.phaseTicks = 0;
      this.mc.player.setSprinting(true);
   }

   private void applyLaunchVelocity() {
      if (this.aboveTargetPos != null) {
         Vec3 delta = this.aboveTargetPos.subtract(this.mc.player.position());
         Vec3 horizontal = new Vec3(delta.x, 0.0, delta.z);
         double horizontalDistance = horizontal.length();
         double speed = (Double)this.velocitySpeed.get();
         double yVelocity = Mth.clamp(delta.y * 0.35, 0.25, (Double)this.upwardSpeed.get() * speed);
         double horizontalVelocity = Math.min(horizontalDistance * 0.35, Math.max(0.15, (Double)this.upwardSpeed.get() * 0.35) * speed);
         Vec3 horizontalMotion = horizontalDistance > 0.05 ? horizontal.normalize().scale(horizontalVelocity) : Vec3.ZERO;
         this.mc.player.setSprinting(false);
         this.mc.player.setDeltaMovement(horizontalMotion.x, yVelocity, horizontalMotion.z);
      }
   }

   private void applySlamVelocity() {
      Vec3 targetCenter = this.killTarget.getBoundingBox().getCenter();
      Vec3 delta = targetCenter.subtract(this.mc.player.position());
      Vec3 horizontal = new Vec3(delta.x, 0.0, delta.z);
      double horizontalDistance = horizontal.length();
      double speed = (Double)this.velocitySpeed.get();
      double yVelocity = -Math.max(1.0, (Double)this.downwardSpeed.get() * speed);
      double horizontalVelocity = Math.min(horizontalDistance * 0.45, Math.max(0.2, (Double)this.downwardSpeed.get() * 0.25) * speed);
      Vec3 horizontalMotion = horizontalDistance > 0.05 ? horizontal.normalize().scale(horizontalVelocity) : Vec3.ZERO;
      this.mc.player.setSprinting(true);
      this.mc.player.setDeltaMovement(horizontalMotion.x, yVelocity, horizontalMotion.z);
   }

   private boolean shouldAttack(LivingEntity target) {
      AABB targetBox = target.getBoundingBox();
      AABB playerBox = this.mc.player.getBoundingBox().inflate((Double)this.stopDistance.get());
      if (playerBox.intersects(targetBox)) {
         return true;
      }

      Vec3 playerPos = this.mc.player.position();
      Vec3 targetCenter = targetBox.getCenter();
      double horizontalDistance = Math.hypot(playerPos.x - targetCenter.x, playerPos.z - targetCenter.z);
      double allowedHorizontal = Math.max(1.25, (Double)this.stopDistance.get());
      double allowedVertical = Math.max(1.0, (Double)this.stopDistance.get());
      return horizontalDistance <= allowedHorizontal && playerPos.y <= targetBox.maxY + allowedVertical && playerPos.y >= targetBox.minY - 4.0;
   }

   private void finishAttack(LivingEntity target) {
      this.executeAttack(target);
      this.hasAttacked = true;
      if ((Boolean)this.useOffset.get()) {
         Vec3 safe = this.findSafeOffset(this.previousPos != null ? this.previousPos : this.mc.player.position());
         this.mc.player.setPos(safe.x, safe.y, safe.z);
      }

      if ((Boolean)this.stopOnTarget.get()) {
         this.mc.player.setDeltaMovement(Vec3.ZERO);
         this.mc.player.setSprinting(false);
      }

      if ((Boolean)this.chatFlush.get()) {
         double dist = this.previousPos != null ? this.previousPos.distanceTo(this.mc.player.position()) : (Double)this.aboveHeight.get();
         this.info("Smashed " + target.getName().getString() + " - " + String.format("%.1f", dist) + " blocks", new Object[0]);
      }

      this.attackCooldown = 5;
      this.phase = MaceKill.Phase.IDLE;
      this.killTarget = null;
      this.aboveTargetPos = null;
      this.phaseTicks = 0;
   }

   private void executeAttack(LivingEntity target) {
      if (this.mc.player != null && this.mc.player.connection != null) {
         if ((Boolean)this.swingHand.get()) {
            this.mc.player.swing(InteractionHand.MAIN_HAND);
         }

         this.mc.player.connection.send(new ServerboundAttackPacket(target.getId()));
      }
   }

   private LivingEntity findTarget() {
      if (this.mc.player != null && this.mc.level != null) {
         if (this.mc.hitResult instanceof EntityHitResult hit && this.isValidTarget(hit.getEntity()) && hit.getEntity() instanceof LivingEntity le) {
            return le;
         } else {
            double maxDist = (Double)this.maxRange.get();
            Vec3 eyePos = this.mc.player.getEyePosition();
            Vec3 lookVec = this.mc.player.getViewVector(1.0F);
            BlockHitResult blockHit = this.mc
               .level
               .clip(new ClipContext(eyePos, eyePos.add(lookVec.scale(maxDist)), Block.COLLIDER, Fluid.NONE, this.mc.player));
            if (blockHit.getType() != Type.MISS) {
               eyePos.distanceTo(blockHit.getLocation());
            }

            List<Entity> candidates = new ArrayList<>();

            for (Entity e : this.mc.level.entitiesForRendering()) {
               if (e != this.mc.player && e instanceof LivingEntity le && le.isAlive() && this.isValidTarget(e)) {
                  double dist = eyePos.distanceToSqr(e.getBoundingBox().getCenter());
                  if (!(dist > maxDist * maxDist) && this.canSeeTarget(e)) {
                     candidates.add(e);
                  }
               }
            }

            candidates.sort(Comparator.comparingDouble(ex -> eyePos.distanceToSqr(ex.getBoundingBox().getCenter())));
            double coneAngle = 0.999;

            for (Entity e : candidates) {
               Vec3 toEntity = e.getBoundingBox().getCenter().subtract(eyePos).normalize();
               if (lookVec.dot(toEntity) > coneAngle && e instanceof LivingEntity le) {
                  return le;
               }
            }

            return !candidates.isEmpty() && candidates.get(0) instanceof LivingEntity le ? le : null;
         }
      } else {
         return null;
      }
   }

   private boolean canSeeTarget(Entity target) {
      if (this.mc.player != null && this.mc.level != null) {
         Vec3 eyePos = this.mc.player.getEyePosition();
         Vec3 targetCenter = target.getBoundingBox().getCenter();
         BlockHitResult result = this.mc.level.clip(new ClipContext(eyePos, targetCenter, Block.COLLIDER, Fluid.NONE, this.mc.player));
         return result.getType() == Type.MISS ? true : eyePos.distanceTo(result.getLocation()) >= eyePos.distanceTo(targetCenter) - 0.5;
      } else {
         return false;
      }
   }

   private boolean isValidTarget(Entity entity) {
      if (entity == null) {
         return false;
      } else if (entity == this.mc.player) {
         return false;
      } else if (entity instanceof Player player && (Boolean)this.ignoreFriends.get() && Friends.get().isFriend(player)) {
         return false;
      } else {
         EntityType<?> type = entity.getType();
         boolean inList = ((Set)this.targetEntities.get()).contains(type);
         return this.targetListMode.get() != MaceKill.TargetListMode.Whitelist ? !inList : inList || ((Set)this.targetEntities.get()).isEmpty();
      }
   }

   private void rotateToTarget(Entity target) {
      if (this.mc.player != null && target != null) {
         Vec3 playerPos = this.mc.player.getEyePosition();
         AABB box = target.getBoundingBox();
         double targetCenterY = box.getCenter().y;
         double heightDiff = targetCenterY - playerPos.y;
         double boxHeight = box.maxY - box.minY;
         double targetY;
         if (Math.abs(heightDiff) < 1.0) {
            targetY = targetCenterY;
         } else if (heightDiff > 0.0) {
            double offset = Math.min(heightDiff / 5.0, 0.4);
            targetY = targetCenterY - boxHeight * offset;
         } else {
            double offset = Math.min(-heightDiff / 5.0, 0.4);
            targetY = targetCenterY + boxHeight * offset;
         }

         Vec3 targetPos = new Vec3(box.getCenter().x, targetY, box.getCenter().z);
         Vec3 toTarget = targetPos.subtract(playerPos).normalize();
         float yaw = (float)Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
         float pitch = (float)(-Math.toDegrees(Math.asin(toTarget.y)));
         this.mc.player.setYRot(yaw);
         this.mc.player.setXRot(pitch);
      }
   }

   private boolean isAbovePathValid(Vec3 abovePos, Entity target) {
      if (this.mc.level != null && abovePos != null) {
         Vec3 targetCenter = target.getBoundingBox().getCenter();
         if (this.isUnsafe(abovePos)) {
            return false;
         }

         double radius = (Double)this.nearTargetThreshold.get();
         int r = (int)radius;

         for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
               for (int z = -r; z <= r; z++) {
                  Vec3 testPos = abovePos.add(x, y, z);
                  if (testPos.distanceTo(abovePos) <= radius && this.isUnsafe(testPos)) {
                     return false;
                  }
               }
            }
         }

         int pathSteps = Math.max(10, (int)(abovePos.distanceTo(targetCenter) * 2.5));

         for (int i = 1; i < pathSteps; i++) {
            double t = (double)i / pathSteps;
            Vec3 sample = abovePos.lerp(targetCenter, t);
            if (this.isUnsafe(sample)) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private boolean isUnsafe(Vec3 pos) {
      if (this.mc.level == null) {
         return true;
      }

      double clamped = Mth.clamp(pos.y, this.mc.level.getMinY(), this.mc.level.getMaxY() - 1);
      if (clamped != pos.y) {
         return true;
      }

      BlockPos floored = BlockPos.containing(pos);
      if (!this.mc.level.hasChunk(floored.getX() >> 4, floored.getZ() >> 4)) {
         return true;
      }

      if (this.positionCache.containsKey(pos)) {
         return this.positionCache.get(pos);
      }

      Entity self = this.mc.player;
      Vec3 delta = pos.subtract(self.position());
      AABB box = self.getBoundingBox().move(delta);
      this.mutablePos.set(floored);

      for (int x = -1; x <= 1; x++) {
         for (int y = -1; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
               this.mutablePos.set(floored.getX() + x, floored.getY() + y, floored.getZ() + z);
               BlockState state = this.mc.level.getBlockState(this.mutablePos);
               if (state.is(Blocks.LAVA)
                  || state.is(Blocks.FIRE)
                  || state.is(Blocks.SOUL_FIRE)
                  || state.is(Blocks.MAGMA_BLOCK)
                  || state.is(Blocks.CAMPFIRE)
                  || state.is(Blocks.SWEET_BERRY_BUSH)
                  || state.is(Blocks.POWDER_SNOW)) {
                  this.positionCache.put(pos, true);
                  return true;
               }
            }
         }
      }

      for (Entity e : this.mc.level.getEntities(self, box, ex -> true)) {
         if (e.isPickable()) {
            this.positionCache.put(pos, true);
            return true;
         }
      }

      boolean collides = !this.mc.level.noCollision(self, box);
      this.positionCache.put(pos, collides);
      return collides;
   }

   private Vec3 findSafeOffset(Vec3 base) {
      double dx = (Double)this.offsetHorizontal.get();
      double dy = (Double)this.offsetY.get();
      Vec3[] offsets = new Vec3[]{
         base.add(dx, dy, 0.0),
         base.add(-dx, dy, 0.0),
         base.add(0.0, dy, dx),
         base.add(0.0, dy, -dx),
         base.add(dx, dy, dx),
         base.add(-dx, dy, -dx),
         base.add(-dx, dy, dx),
         base.add(dx, dy, -dx)
      };
      List<Vec3> list = Arrays.asList(offsets);
      Collections.shuffle(list);

      for (Vec3 pos : list) {
         if (!this.isUnsafe(pos)) {
            return pos;
         }
      }

      Vec3 noHz = base.add(0.0, dy, 0.0);
      return !this.isUnsafe(noHz) ? noHz : base;
   }

   private enum Phase {
      IDLE,
      LAUNCHING_UP,
      SLAMMING_DOWN;
   }

   public enum TargetListMode {
      Whitelist,
      Blacklist;
   }
}
