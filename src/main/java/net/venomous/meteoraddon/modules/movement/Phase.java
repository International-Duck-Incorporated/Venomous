package net.venomous.meteoraddon.modules.movement;

import java.util.ArrayDeque;
import java.util.Queue;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.venomous.meteoraddon.VenomousAddon;

public class Phase extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Phase.Mode> mode = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("mode"))
                  .description("Normal sets no-physics, Blink queues movement packets, Clip pulses through nearby collision."))
               .defaultValue(Phase.Mode.Normal))
            .build()
      );
   private final Setting<Double> speed = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("speed"))
               .description("Movement speed while phasing."))
            .defaultValue(0.35)
            .range(0.05, 3.0)
            .sliderMax(1.5)
            .build()
      );
   private final Setting<Integer> blinkLimit = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("blink-limit"))
                     .description("Maximum queued move packets in Blink mode before flushing."))
                  .defaultValue(60))
               .range(10, 300)
               .sliderMax(150)
               .visible(() -> this.mode.get() == Phase.Mode.Blink))
            .build()
      );
   private final Queue<Packet<?>> blinkQueue = new ArrayDeque<>();
   private boolean flushing;

   public Phase() {
      super(VenomousAddon.CATEGORY, "phase", "Phases through blocks using Normal, Blink, or Clip modes.");
   }

   public void onDeactivate() {
      if (this.mc.player != null) {
         this.mc.player.noPhysics = false;
      }

      this.flush();
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null) {
         if (this.mode.get() == Phase.Mode.Normal || this.mode.get() == Phase.Mode.Clip) {
            this.mc.player.noPhysics = true;
            this.mc.player.fallDistance = 0.0;
         }

         double forward = (this.mc.options.keyUp.isDown() ? 1 : 0) - (this.mc.options.keyDown.isDown() ? 1 : 0);
         double strafe = (this.mc.options.keyLeft.isDown() ? 1 : 0) - (this.mc.options.keyRight.isDown() ? 1 : 0);
         if (forward != 0.0 || strafe != 0.0) {
            double yaw = Math.toRadians(this.mc.player.getYRot());
            double x = (strafe * Math.cos(yaw) - forward * Math.sin(yaw)) * (Double)this.speed.get();
            double z = (forward * Math.cos(yaw) + strafe * Math.sin(yaw)) * (Double)this.speed.get();
            if (this.mode.get() == Phase.Mode.Clip) {
               this.mc.player.setPos(this.mc.player.getX() + x, this.mc.player.getY(), this.mc.player.getZ() + z);
               if (this.mc.getConnection() != null) {
                  this.mc
                     .getConnection()
                     .send(
                        new Pos(
                           this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ(), this.mc.player.onGround(), this.mc.player.horizontalCollision
                        )
                     );
               }
            } else if (this.mode.get() == Phase.Mode.Normal) {
               this.mc.player.setDeltaMovement(x, this.mc.player.getDeltaMovement().y, z);
            }
         }
      }
   }

   @EventHandler
   private void onSend(Send event) {
      if (this.mode.get() == Phase.Mode.Blink && !this.flushing && event.packet instanceof ServerboundMovePlayerPacket) {
         event.setCancelled(true);
         this.blinkQueue.add(event.packet);
         if (this.blinkQueue.size() >= (Integer)this.blinkLimit.get()) {
            this.flush();
         }
      }
   }

   private void flush() {
      if (this.mc.getConnection() == null) {
         this.blinkQueue.clear();
      } else {
         this.flushing = true;

         while (!this.blinkQueue.isEmpty()) {
            this.mc.getConnection().send(this.blinkQueue.poll());
         }

         this.flushing = false;
      }
   }

   public enum Mode {
      Normal,
      Blink,
      Clip;
   }
}
