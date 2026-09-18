package net.venomous.meteoraddon.modules.movement;

import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

public class Clip extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Clip.Mode> mode = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("mode")).description("Clip mode: HClip, VClip, NoClip, or VulcanClip."))
               .defaultValue(Clip.Mode.HClip))
            .build()
      );
   private final Setting<Double> distance = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("distance"))
                  .description("Horizontal clip distance. Positive is forward, negative is backward."))
               .defaultValue(3.0)
               .range(-20.0, 20.0)
               .sliderMin(-10.0)
               .sliderMax(10.0)
               .visible(() -> this.mode.get() == Clip.Mode.HClip))
            .build()
      );
   private final Setting<Double> height = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("height"))
                  .description("Vertical clip height."))
               .defaultValue(3.0)
               .range(-20.0, 20.0)
               .sliderMin(-10.0)
               .sliderMax(10.0)
               .visible(() -> this.mode.get() == Clip.Mode.VClip))
            .build()
      );
   private final Setting<Boolean> autoVClip = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("auto-vclip"))
                     .description("In VClip mode, find a nearby free spot up/down instead of using custom height."))
                  .defaultValue(false))
               .visible(() -> this.mode.get() == Clip.Mode.VClip))
            .build()
      );
   private final Setting<Clip.DirectionMode> direction = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)((Builder)new Builder().name("direction")).description("Auto VClip search direction."))
                  .defaultValue(Clip.DirectionMode.Up))
               .visible(() -> this.mode.get() == Clip.Mode.VClip && (Boolean)this.autoVClip.get()))
            .build()
      );
   private final Setting<Double> noClipHorizontal = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("noclip-horizontal-speed"))
                  .description("Horizontal velocity in NoClip mode."))
               .defaultValue(0.4)
               .range(0.0, 3.0)
               .sliderMax(1.5)
               .visible(() -> this.mode.get() == Clip.Mode.NoClip))
            .build()
      );
   private final Setting<Double> noClipVertical = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("noclip-vertical-speed"))
                  .description("Vertical velocity in NoClip mode."))
               .defaultValue(0.25)
               .range(0.0, 3.0)
               .sliderMax(1.5)
               .visible(() -> this.mode.get() == Clip.Mode.NoClip))
            .build()
      );
   private final Setting<Boolean> sendPackets = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("send-packets"))
                     .description("Send movement packets after instant clip actions."))
                  .defaultValue(true))
               .visible(() -> this.mode.get() == Clip.Mode.HClip || this.mode.get() == Clip.Mode.VClip))
            .build()
      );
   private final Setting<Integer> packetRepeats = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("packet-repeats"))
                     .description("How many position packets to send after an instant clip."))
                  .defaultValue(1))
               .range(1, 5)
               .sliderMax(5)
               .visible(() -> (Boolean)this.sendPackets.get() && (this.mode.get() == Clip.Mode.HClip || this.mode.get() == Clip.Mode.VClip)))
            .build()
      );
   private boolean waitFlag;
   private boolean canGlide;
   private int ticks;
   private Timer timer;

   public Clip() {
      super(VenomousAddon.CATEGORY, "v-clip", "Combined HClip, VClip, NoClip and VulcanClip movement module.");
   }

   public void onActivate() {
      if (this.mc.player == null) {
         this.toggle();
      } else {
         switch ((Clip.Mode)this.mode.get()) {
            case HClip:
               this.doHClip();
               this.toggle();
               break;
            case VClip:
               this.doVClip();
               this.toggle();
            case NoClip:
            default:
               break;
            case VulcanClip:
               this.startVulcanClip();
         }
      }
   }

   public void onDeactivate() {
      if (this.mc.player != null) {
         this.mc.player.noPhysics = false;
      }

      if (this.timer != null) {
         this.timer.setOverride(1.0);
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mode.get() == Clip.Mode.NoClip) {
         this.mc.player.noPhysics = true;
         this.mc.player.fallDistance = 0.0;
         double forward = (this.mc.options.keyUp.isDown() ? 1 : 0) - (this.mc.options.keyDown.isDown() ? 1 : 0);
         double strafe = (this.mc.options.keyLeft.isDown() ? 1 : 0) - (this.mc.options.keyRight.isDown() ? 1 : 0);
         double yaw = Math.toRadians(this.mc.player.getYRot());
         double x = (strafe * Math.cos(yaw) - forward * Math.sin(yaw)) * (Double)this.noClipHorizontal.get();
         double z = (forward * Math.cos(yaw) + strafe * Math.sin(yaw)) * (Double)this.noClipHorizontal.get();
         double y = 0.0;
         if (this.mc.options.keyJump.isDown()) {
            y += this.noClipVertical.get();
         }

         if (this.mc.options.keyShift.isDown()) {
            y -= this.noClipVertical.get();
         }

         this.mc.player.setDeltaMovement(x, y, z);
      }
   }

   @EventHandler
   private void onMovePre(meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent.Pre event) {
      if (this.mc.player != null && this.mode.get() == Clip.Mode.VulcanClip && this.canGlide) {
         if (this.timer != null) {
            this.timer.setOverride(1.0);
         }

         Vec3 velocity = this.mc.player.getDeltaMovement();
         this.mc.player.setDeltaMovement(velocity.x, velocity.y - (this.ticks % 2 == 0 ? 0.17 : 0.1) - (this.ticks == 0 ? 0.07 : 0.0), velocity.z);
         this.ticks++;
      }
   }

   @EventHandler
   private void onReceive(Receive event) {
      if (this.mc.player != null && this.mode.get() == Clip.Mode.VulcanClip && this.waitFlag && event.packet instanceof ClientboundPlayerPositionPacket packet2
         )
       {
         Vec3 old = this.mc.player.position();
         Vec3 server = packet2.change().position();
         this.waitFlag = false;
         this.mc.player.setPos(server.x, server.y, server.z);
         this.mc.player.connection.send(new Pos(old.x, old.y, old.z, false, this.mc.player.horizontalCollision));
         event.setCancelled(true);
         this.mc.player.jumpFromGround();
         this.vulcanClip(0.127318, 0.0);
         this.vulcanClip(3.425559, 3.7);
         this.vulcanClip(3.14285, 3.54);
         this.vulcanClip(2.88522, 3.4);
         this.canGlide = true;
      }
   }

   private void doHClip() {
      double yaw = Math.toRadians(this.mc.player.getYRot());
      this.moveTo(
         this.mc.player.getX() - Math.sin(yaw) * (Double)this.distance.get(),
         this.mc.player.getY(),
         this.mc.player.getZ() + Math.cos(yaw) * (Double)this.distance.get()
      );
   }

   private void doVClip() {
      double h = this.autoVClip.get()
         ? this.calculateHeight(this.direction.get() == Clip.DirectionMode.Up ? Direction.UP : Direction.DOWN)
         : (Double)this.height.get();
      this.moveTo(this.mc.player.getX(), this.mc.player.getY() + h, this.mc.player.getZ());
   }

   private void moveTo(double x, double y, double z) {
      this.mc.player.setPos(x, y, z);
      if ((Boolean)this.sendPackets.get() && this.mc.getConnection() != null) {
         for (int i = 0; i < this.packetRepeats.get(); i++) {
            this.mc.getConnection().send(new Pos(x, y, z, this.mc.player.onGround(), this.mc.player.horizontalCollision));
         }
      }
   }

   private double calculateHeight(Direction direction) {
      if (this.mc.level == null) {
         return direction == Direction.UP ? 3.0 : -3.0;
      }

      AABB box = this.mc.player.getBoundingBox();

      for (int i = 1; i <= 10; i++) {
         double h = direction.getStepY() * i;
         if (this.mc.level.noCollision(this.mc.player, box.move(0.0, h, 0.0))) {
            return h;
         }
      }

      return direction == Direction.UP ? 3.0 : -3.0;
   }

   private void startVulcanClip() {
      this.timer = (Timer)Modules.get().get(Timer.class);
      this.ticks = 0;
      if (this.mc.player.onGround()) {
         this.vulcanClip(0.0, -0.1);
         this.waitFlag = true;
         this.canGlide = false;
         if (this.timer != null) {
            this.timer.setOverride(0.1);
         }
      } else {
         this.waitFlag = false;
         this.canGlide = true;
      }
   }

   private void vulcanClip(double dist, double y) {
      double yaw = Math.toRadians(this.mc.player.getYRot());
      double x = -Math.sin(yaw) * dist;
      double z = Math.cos(yaw) * dist;
      this.mc.player.setPos(this.mc.player.getX() + x, this.mc.player.getY() + y, this.mc.player.getZ() + z);
      if (this.mc.getConnection() != null) {
         this.mc.getConnection().send(new Pos(this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ(), false, this.mc.player.horizontalCollision));
      }
   }

   public enum DirectionMode {
      Up,
      Down;
   }

   public enum Mode {
      HClip,
      VClip,
      NoClip,
      VulcanClip;
   }
}
