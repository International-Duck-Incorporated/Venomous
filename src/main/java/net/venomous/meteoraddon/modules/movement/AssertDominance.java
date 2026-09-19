package net.venomous.meteoraddon.modules.movement;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

public class AssertDominance extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgTargeting = this.settings.createGroup("Targeting");
   private final Setting<AssertDominance.Mode> mode = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("mode"))
                  .description("Orbit circles smoothly; Random teleports around the nearest target at intervals."))
               .defaultValue(AssertDominance.Mode.Orbit))
            .build()
      );
   private final Setting<Double> radius = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("radius"))
               .description("Distance from the target."))
            .defaultValue(2.0)
            .range(0.5, 24.0)
            .sliderMax(16.0)
            .build()
      );
   private final Setting<Double> speed = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("speed"))
                  .description("Orbit angular speed per tick."))
               .defaultValue(0.1)
               .range(0.01, 3.0)
               .sliderMax(2.0)
               .visible(() -> this.mode.get() == AssertDominance.Mode.Orbit))
            .build()
      );
   private final Setting<Integer> randomDelay = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("random-delay"))
                     .description("Ticks between random teleports around the target."))
                  .defaultValue(5))
               .range(1, 40)
               .sliderMax(20)
               .visible(() -> this.mode.get() == AssertDominance.Mode.Random))
            .build()
      );
   private final Setting<Double> randomY = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("random-y"))
                  .description("Maximum vertical random offset in Random mode."))
               .defaultValue(0.0)
               .range(0.0, 6.0)
               .sliderMax(3.0)
               .visible(() -> this.mode.get() == AssertDominance.Mode.Random))
            .build()
      );
   private final Setting<Boolean> sendPackets = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("send-packets"))
                  .description("Send movement packets after repositioning."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Set<EntityType<?>>> entities = this.sgTargeting
      .add(
         ((meteordevelopment.meteorclient.settings.EntityTypeListSetting.Builder)((meteordevelopment.meteorclient.settings.EntityTypeListSetting.Builder)new meteordevelopment.meteorclient.settings.EntityTypeListSetting.Builder()
                  .name("entities"))
               .description("Entity types to assert dominance over."))
            .defaultValue(new EntityType[]{EntityTypes.PLAYER})
            .build()
      );
   private final Setting<Double> range = this.sgTargeting
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("range"))
               .description("Only target entities within this many blocks. 0 for unlimited."))
            .defaultValue(0.0)
            .min(0.0)
            .sliderMax(64.0)
            .build()
      );
   private final Setting<SortPriority> priority = this.sgTargeting
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("priority")).description("How the target is picked when several match."))
               .defaultValue(SortPriority.LowestDistance))
            .build()
      );
   private final Setting<Boolean> ignoreFriends = this.sgTargeting
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("ignore-friends"))
                  .description("Never target your friends."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> ignoreNamed = this.sgTargeting
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("ignore-named"))
                  .description("Skip mobs that have a name tag - handy for not harassing your own pets."))
               .defaultValue(false))
            .build()
      );
   private double angle;
   private int ticks;

   public AssertDominance() {
      super(VenomousAddon.CATEGORY, "v-assert-dominance", "Moves around the nearest player or mob using orbit or random teleport mode.");
   }

   public void onActivate() {
      this.ticks = 0;
      this.angle = 0.0;
   }

   @EventHandler
   private void onTick(Post var1) {
      if (this.mc.player != null && this.mc.level != null) {
         Entity var2 = TargetUtils.get(this::isGoodTarget, (SortPriority)this.priority.get());
         if (var2 != null) {
            double var3 = this.sanitizeDouble((Double)this.radius.get(), 2.0, 0.0, 24.0);
            double var5 = this.sanitizeDouble((Double)this.speed.get(), 0.1, 0.0, 3.0);
            double var7;
            double var9;
            double var11;
            if (this.mode.get() == AssertDominance.Mode.Orbit) {
               this.angle += var5;
               var7 = var2.getX() + Math.cos(this.angle) * var3;
               var9 = var2.getY();
               var11 = var2.getZ() + Math.sin(this.angle) * var3;
            } else {
               if (this.mode.get() != AssertDominance.Mode.Random) {
                  Vec3 var21 = var2.position().subtract(this.mc.player.position());
                  Vec3 var22 = new Vec3(var21.x, 0.0, var21.z);
                  if (var22.lengthSqr() <= 1.0E-6) {
                     return;
                  }

                  Vec3 var23 = var22.normalize().scale(var5);
                  this.mc.player.addDeltaMovement(new Vec3(var23.x, 0.1, var23.z));
                  return;
               }

               int var13 = Math.max(1, (Integer)this.randomDelay.get());
               if (++this.ticks < var13) {
                  return;
               }

               this.ticks = 0;
               ThreadLocalRandom var14 = ThreadLocalRandom.current();
               double var15 = var14.nextDouble(0.0, Math.PI * 2);
               double var17 = this.randomDistance(var14, var3);
               double var19 = this.sanitizeDouble((Double)this.randomY.get(), 0.0, 0.0, 6.0);
               var7 = var2.getX() + Math.cos(var15) * var17;
               var9 = var2.getY() + (var19 <= 0.0 ? 0.0 : var14.nextDouble(-var19, var19));
               var11 = var2.getZ() + Math.sin(var15) * var17;
            }

            this.mc.player.setPos(var7, var9, var11);
            if ((Boolean)this.sendPackets.get() && this.mc.getConnection() != null) {
               this.mc.getConnection().send(new Pos(var7, var9, var11, this.mc.player.onGround(), this.mc.player.horizontalCollision));
            }
         }
      }
   }

   private boolean isGoodTarget(Entity var1) {
      if (var1 != this.mc.player && var1.isAlive()) {
         if (!((Set)this.entities.get()).contains(var1.getType())) {
            return false;
         }

         double var2 = (Double)this.range.get();
         if (var2 > 0.0 && !PlayerUtils.isWithin(var1, var2)) {
            return false;
         }

         if (var1 instanceof Player var4) {
            if (var4.isSpectator()) {
               return false;
            }

            if ((Boolean)this.ignoreFriends.get() && !Friends.get().shouldAttack(var4)) {
               return false;
            }
         } else if ((Boolean)this.ignoreNamed.get() && var1.hasCustomName()) {
            return false;
         }

         return true;
      } else {
         return false;
      }
   }

   private double randomDistance(ThreadLocalRandom var1, double var2) {
      double var4 = 0.75;
      return var2 <= var4 ? Math.max(0.0, var2) : var1.nextDouble(var4, var2);
   }

   private double sanitizeDouble(double var1, double var3, double var5, double var7) {
      if (!Double.isFinite(var1)) {
         return var3;
      } else if (var1 < var5) {
         return var5;
      } else {
         return var1 > var7 ? var7 : var1;
      }
   }

   public String getInfoString() {
      return ((AssertDominance.Mode)this.mode.get()).toString();
   }

   public enum Mode {
      Orbit,
      Random,
      Velocity;
   }
}
