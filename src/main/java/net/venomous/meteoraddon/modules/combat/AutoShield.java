package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

public class AutoShield extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> meleeRange = this.sgGeneral
      .add(
         new Builder().name("melee-range").description("Block when a non-friend player/living entity is this close and looking at you.")
            .defaultValue(4.0)
            .range(1.0, 8.0)
            .sliderMax(6.0)
            .build()
      );
   private final Setting<Boolean> blockProjectiles = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("block-projectiles")
            .description("Also block when an incoming projectile is detected.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> packetPrediction = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("packet-prediction")
            .description("Predict attacks by listening to server packets (swing, bow shots).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> projectileRange = this.sgGeneral
      .add(
         new Builder().name("projectile-range").description("Projectile detection range.")
            .defaultValue(12.0)
            .range(2.0, 64.0)
            .sliderMax(32.0)
            .visible(() -> (Boolean)this.blockProjectiles.get())
            .build()
      );
   private final Setting<Integer> holdTicks = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("hold-ticks")
            .description("Ticks to keep shield raised after the last threat.")
            .defaultValue(4)
            .range(0, 20)
            .sliderMax(10)
            .build()
      );
   private final Setting<Boolean> switchToShield = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("switch-to-shield")
            .description("Switch to a hotbar shield if no shield is in either hand.")
            .defaultValue(true)
            .build()
      );
   private int blockTicks;
   private int previousSlot = -1;
   private boolean blocking;
   private boolean previousUseDown;

   public AutoShield() {
      super(VenomousAddon.CATEGORY, "auto-shield", "Raises a shield when threats are detected or predicted.");
   }

   public void onDeactivate() {
      this.stopBlocking();
   }

   @EventHandler
   private void onPacketReceive(Receive event) {
      if (this.mc.level != null && this.mc.player != null && (Boolean)this.packetPrediction.get()) {
         if (event.packet instanceof ClientboundAnimatePacket p) {
            Entity entity = this.mc.level.getEntity(p.getId());
            if (entity instanceof LivingEntity
               && entity != this.mc.player
               && this.mc.player.distanceTo(entity) <= (Double)this.meleeRange.get() + 1.0
               && p.getAction() == 0) {
               this.blockTicks = (Integer)this.holdTicks.get() + 2;
            }
         } else if (event.packet instanceof ClientboundSoundPacket p
            && (p.getSound().value() == SoundEvents.ARROW_SHOOT || p.getSound().value() == SoundEvents.CROSSBOW_SHOOT)) {
            double dist = Math.sqrt(this.mc.player.distanceToSqr(p.getX(), p.getY(), p.getZ()));
            if (dist <= (Double)this.projectileRange.get()) {
               this.blockTicks = (Integer)this.holdTicks.get() + 5;
            }
         }
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.level != null && this.mc.gameMode != null) {
         if (this.hasThreat()) {
            this.blockTicks = (Integer)this.holdTicks.get() + 1;
         } else if (this.blockTicks > 0) {
            this.blockTicks--;
         }

         if (this.blockTicks > 0) {
            this.startBlocking();
         } else {
            this.stopBlocking();
         }
      }
   }

   private boolean hasThreat() {
      double meleeSq = (Double)this.meleeRange.get() * (Double)this.meleeRange.get();

      for (Entity entity : this.mc.level.entitiesForRendering()) {
         if (entity instanceof LivingEntity living
            && entity != this.mc.player
            && !(entity instanceof Player player && !Friends.get().shouldAttack(player))
            && this.mc.player.distanceToSqr(entity) <= meleeSq
            && this.isLookingAt(living)) {
            return true;
         }
      }

      if (!(Boolean)this.blockProjectiles.get()) {
         return false;
      }

      double projectileSq = (Double)this.projectileRange.get() * (Double)this.projectileRange.get();

      for (Entity entity : this.mc.level.entitiesForRendering()) {
         Projectile projectile;
         if (entity instanceof Projectile
            && (projectile = (Projectile)entity).getOwner() != this.mc.player
            && this.mc.player.distanceToSqr(entity) <= projectileSq
            && this.isIncoming(entity)) {
            return true;
         }
      }

      return false;
   }

   private boolean isLookingAt(LivingEntity entity) {
      Vec3 toPlayer = this.mc.player.position().subtract(entity.position()).normalize();
      Vec3 look = entity.getViewVector(1.0F).normalize();
      return look.dot(toPlayer) > 0.65;
   }

   private boolean isIncoming(Entity projectile) {
      Vec3 velocity = projectile.getDeltaMovement();
      if (velocity.lengthSqr() < 1.0E-4) {
         return false;
      }

      Vec3 toPlayer = this.mc.player.position().add(0.0, 0.9, 0.0).subtract(projectile.position()).normalize();
      return velocity.normalize().dot(toPlayer) > 0.65;
   }

   private void startBlocking() {
      InteractionHand hand = this.shieldHand();
      int slot;
      if (hand == null && (Boolean)this.switchToShield.get() && (slot = this.findShield()) != -1) {
         if (this.previousSlot == -1) {
            this.previousSlot = this.mc.player.getInventory().getSelectedSlot();
         }

         this.mc.player.getInventory().setSelectedSlot(slot);
         hand = InteractionHand.MAIN_HAND;
      }

      if (hand != null) {
         if (!this.blocking) {
            this.previousUseDown = this.mc.options.keyUse.isDown();
         }

         this.blocking = true;
         this.mc.options.keyUse.setDown(true);
         if (!this.mc.player.isUsingItem()) {
            this.mc.gameMode.useItem(this.mc.player, hand);
         }
      }
   }

   private void stopBlocking() {
      if (this.blocking) {
         this.mc.options.keyUse.setDown(this.previousUseDown);
      }

      if (this.blocking && this.mc.player != null && this.mc.player.isUsingItem() && !this.previousUseDown) {
         this.mc.player.stopUsingItem();
      }

      if (this.mc.player != null && this.previousSlot != -1) {
         this.mc.player.getInventory().setSelectedSlot(this.previousSlot);
      }

      this.previousSlot = -1;
      this.previousUseDown = false;
      this.blocking = false;
      this.blockTicks = 0;
   }

   private InteractionHand shieldHand() {
      if (this.mc.player.getOffhandItem().getItem() == Items.SHIELD) {
         return InteractionHand.OFF_HAND;
      } else {
         return this.mc.player.getMainHandItem().getItem() == Items.SHIELD ? InteractionHand.MAIN_HAND : null;
      }
   }

   private int findShield() {
      for (int i = 0; i < 9; i++) {
         if (this.mc.player.getInventory().getItem(i).getItem() == Items.SHIELD) {
            return i;
         }
      }

      return -1;
   }
}
