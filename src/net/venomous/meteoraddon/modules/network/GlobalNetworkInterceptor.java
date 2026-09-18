package net.venomous.meteoraddon.modules.network;

import java.util.ArrayDeque;
import java.util.Deque;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.modules.movement.AssertDominance;

public class GlobalNetworkInterceptor extends Module {
   private final SettingGroup sgKinematics;
   private final SettingGroup sgRotation;
   private final SettingGroup sgSerializer;
   private final Setting<Boolean> enforceKinematics;
   private final Setting<Double> maxHorizontalPerTick;
   private final Setting<Double> maxVerticalPerTick;
   private final Setting<Boolean> interpolateViolations;
   private final Setting<Boolean> quantizeRotations;
   private final Setting<Boolean> enforceTickOrder;
   private final Setting<Integer> maxQueueDepth;
   private double lastSentX;
   private double lastSentY;
   private double lastSentZ;
   private float lastSentYaw;
   private float lastSentPitch;
   private boolean hasLastPosition;
   private final Deque<Packet<?>> tickQueue = new ArrayDeque<>();
   private boolean movementSentThisTick;
   private boolean bypassing;

   public GlobalNetworkInterceptor() {
      super(
         VenomousAddon.CATEGORY,
         "v-network-optimizer",
         "Ensures outgoing packets are compliant with server-side checks by smoothing movements and snapping rotations."
      );
      this.sgKinematics = this.settings.createGroup("Movement Smoothing");
      this.sgRotation = this.settings.createGroup("Rotation Snapping");
      this.sgSerializer = this.settings.createGroup("Packet Queue");
      this.enforceKinematics = this.sgKinematics
         .add(
            ((Builder)((Builder)((Builder)new Builder().name("smooth-movement"))
                     .description("Ensures your movement packets don't look like teleportation to the server."))
                  .defaultValue(true))
               .build()
         );
      this.maxHorizontalPerTick = this.sgKinematics
         .add(
            ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                        .name("max-horizontal"))
                     .description("Max horizontal distance per tick."))
                  .defaultValue(0.6)
                  .range(0.1, 5.0)
                  .sliderMax(1.0)
                  .visible(() -> (Boolean)this.enforceKinematics.get()))
               .build()
         );
      this.maxVerticalPerTick = this.sgKinematics
         .add(
            ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                        .name("max-vertical"))
                     .description("Max vertical distance per tick."))
                  .defaultValue(0.5)
                  .range(0.1, 5.0)
                  .sliderMax(1.0)
                  .visible(() -> (Boolean)this.enforceKinematics.get()))
               .build()
         );
      this.interpolateViolations = this.sgKinematics
         .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("interpolate")).description("Split fast movements into smaller, safer steps."))
                     .defaultValue(true))
                  .visible(() -> (Boolean)this.enforceKinematics.get()))
               .build()
         );
      this.quantizeRotations = this.sgRotation
         .add(
            ((Builder)((Builder)((Builder)new Builder().name("snap-to-grid")).description("Snap rotations to the vanilla sensitivity grid."))
                  .defaultValue(true))
               .build()
         );
      this.enforceTickOrder = this.sgSerializer
         .add(
            ((Builder)((Builder)((Builder)new Builder().name("limit-to-one-packet"))
                     .description("Only allow one movement packet per tick to prevent spam flags."))
                  .defaultValue(true))
               .build()
         );
      this.maxQueueDepth = this.sgSerializer
         .add(
            ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                           .name("max-queue"))
                        .description("Maximum packets to buffer before forcing a send."))
                     .defaultValue(20))
                  .range(5, 100)
                  .sliderMax(50)
                  .visible(() -> (Boolean)this.enforceTickOrder.get()))
               .build()
         );
   }

   public void onActivate() {
      super.onActivate();
      this.info("Network Optimizer active. Preventing movement and rotation flags.", new Object[0]);
   }

   public void onDeactivate() {
      this.flushQueue();
   }

   @EventHandler
   private void onTick(Pre pre) {
      if (this.mc.player != null && this.mc.getConnection() != null) {
         this.movementSentThisTick = false;
         if (!this.tickQueue.isEmpty() && (Boolean)this.enforceTickOrder.get()) {
            Packet<?> packet = this.tickQueue.poll();
            this.sendBypassed(packet);
         }
      } else {
         this.tickQueue.clear();
      }
   }

   @EventHandler
   private void onSend(Send send) {
      if (!this.bypassing
         && this.mc.player != null
         && this.mc.getConnection() != null
         && !this.isAssertDominanceActive()
         && send.packet instanceof ServerboundMovePlayerPacket serverboundMovePlayerPacket) {
         send.setCancelled(true);
         this.handleMovementPacket(serverboundMovePlayerPacket, send);
      }
   }

   private void handleMovementPacket(ServerboundMovePlayerPacket serverboundMovePlayerPacket, Send send) {
      double d = serverboundMovePlayerPacket.getX(this.mc.player.getX());
      double d2 = serverboundMovePlayerPacket.getY(this.mc.player.getY());
      double d3 = serverboundMovePlayerPacket.getZ(this.mc.player.getZ());
      float f = serverboundMovePlayerPacket.getYRot(this.mc.player.getYRot());
      float f2 = serverboundMovePlayerPacket.getXRot(this.mc.player.getXRot());
      boolean bl = serverboundMovePlayerPacket.isOnGround();
      boolean bl2 = serverboundMovePlayerPacket.horizontalCollision();
      boolean bl3 = serverboundMovePlayerPacket.hasPosition();
      boolean bl4 = serverboundMovePlayerPacket.hasRotation();
      if (bl4 && (Boolean)this.quantizeRotations.get()) {
         float f3 = this.computeSensitivityFactor();
         f = quantize(f, f3);
         f2 = quantize(f2, f3);
         f2 = clampPitch(f2);
      }

      if (bl3 && (Boolean)this.enforceKinematics.get() && this.hasLastPosition) {
         double d4 = d - this.lastSentX;
         double d5 = d2 - this.lastSentY;
         double d6 = d3 - this.lastSentZ;
         double d7 = Math.sqrt(d4 * d4 + d6 * d6);
         double d8 = Math.abs(d5);
         double d9 = (Double)this.maxHorizontalPerTick.get();
         double d10 = (Double)this.maxVerticalPerTick.get();
         if (d7 > d9 || d8 > d10) {
            if ((Boolean)this.interpolateViolations.get()) {
               int n = (int)Math.ceil(d7 / d9);
               int n2 = (int)Math.ceil(d8 / d10);
               int n3 = Math.max(n, n2);
               n3 = Math.max(n3, 2);
               double d11 = d4 / n3;
               double d12 = d5 / n3;
               double d13 = d6 / n3;

               for (int i = 1; i < n3; i++) {
                  double d14 = this.lastSentX + d11 * i;
                  double d15 = this.lastSentY + d12 * i;
                  double d16 = this.lastSentZ + d13 * i;
                  Object object = bl4 && i == n3 - 1 ? new PosRot(d14, d15, d16, f, f2, bl, bl2) : new Pos(d14, d15, d16, bl, bl2);
                  this.enqueueOrSend((Packet<?>)object);
                  this.lastSentX = d14;
                  this.lastSentY = d15;
                  this.lastSentZ = d16;
               }

               ServerboundMovePlayerPacket serverboundMovePlayerPacket2 = rebuildPacket(d, d2, d3, f, f2, bl, bl2, bl3, bl4);
               this.enqueueOrSend(serverboundMovePlayerPacket2);
               this.lastSentX = d;
               this.lastSentY = d2;
               this.lastSentZ = d3;
               if (bl4) {
                  this.lastSentYaw = f;
                  this.lastSentPitch = f2;
               }

               return;
            }

            if (d7 > d9 && d7 > 0.0) {
               double d17 = d9 / d7;
               d = this.lastSentX + d4 * d17;
               d3 = this.lastSentZ + d6 * d17;
            }

            if (d8 > d10) {
               d2 = this.lastSentY + Math.signum(d5) * d10;
            }
         }
      }

      ServerboundMovePlayerPacket serverboundMovePlayerPacket3 = rebuildPacket(d, d2, d3, f, f2, bl, bl2, bl3, bl4);
      this.enqueueOrSend(serverboundMovePlayerPacket3);
      if (bl3) {
         this.lastSentX = d;
         this.lastSentY = d2;
         this.lastSentZ = d3;
         this.hasLastPosition = true;
      }

      if (bl4) {
         this.lastSentYaw = f;
         this.lastSentPitch = f2;
      }
   }

   private void enqueueOrSend(Packet<?> packet) {
      if (!(Boolean)this.enforceTickOrder.get()) {
         this.sendBypassed(packet);
      } else if (!this.movementSentThisTick) {
         this.movementSentThisTick = true;
         this.sendBypassed(packet);
      } else {
         this.tickQueue.addLast(packet);
         if (this.tickQueue.size() > (Integer)this.maxQueueDepth.get()) {
            this.flushQueue();
         }
      }
   }

   private void flushQueue() {
      if (this.mc.getConnection() == null) {
         this.tickQueue.clear();
      } else {
         this.bypassing = true;

         while (!this.tickQueue.isEmpty()) {
            this.mc.getConnection().send(this.tickQueue.poll());
         }

         this.bypassing = false;
      }
   }

   private void sendBypassed(Packet<?> packet) {
      if (this.mc.getConnection() != null) {
         this.bypassing = true;
         this.mc.getConnection().send(packet);
         this.bypassing = false;
      }
   }

   private float computeSensitivityFactor() {
      float f = ((Double)this.mc.options.sensitivity().get()).floatValue();
      float f2 = f * 0.6F + 0.2F;
      return f2 * f2 * f2 * 8.0F;
   }

   private static float quantize(float f, float f2) {
      return f2 <= 0.0F ? f : Math.round(f / f2) * f2;
   }

   private static float clampPitch(float f) {
      if (f > 90.0F) {
         return 90.0F;
      } else {
         return f < -90.0F ? -90.0F : f;
      }
   }

   private static ServerboundMovePlayerPacket rebuildPacket(
      double d, double d2, double d3, float f, float f2, boolean bl, boolean bl2, boolean bl3, boolean bl4
   ) {
      if (bl3 && bl4) {
         return new PosRot(d, d2, d3, f, f2, bl, bl2);
      } else if (bl3) {
         return new Pos(d, d2, d3, bl, bl2);
      } else {
         return (ServerboundMovePlayerPacket)(bl4 ? new Rot(f, f2, bl, bl2) : new StatusOnly(bl, bl2));
      }
   }

   private boolean isAssertDominanceActive() {
      Module module = Modules.get().get(AssertDominance.class);
      return module != null && module.isActive();
   }

   public String getInfoString() {
      int n = this.tickQueue.size();
      return n > 0 ? "Queue: " + n : "Active";
   }
}
