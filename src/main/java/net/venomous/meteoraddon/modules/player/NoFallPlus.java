package net.venomous.meteoraddon.modules.player;

import java.util.ArrayDeque;
import java.util.Queue;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.venomous.meteoraddon.VenomousAddon;

public class NoFallPlus extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<NoFallPlus.Mode> mode = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("mode")).description("NoFall mode — select your anticheat.")).defaultValue(NoFallPlus.Mode.Packet))
            .build()
      );
   private final Setting<Double> triggerFallDistance = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("trigger-fall-distance"))
               .description("Fall distance at which this module starts acting."))
            .defaultValue(2.5)
            .range(0.5, 10.0)
            .sliderMax(5.0)
            .build()
      );
   private final Setting<Double> catchVelocity = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("catch-velocity"))
                  .description("Downward velocity used by Catch mode."))
               .defaultValue(-0.05)
               .range(-1.0, 0.0)
               .sliderMin(-0.5)
               .sliderMax(0.0)
               .visible(() -> this.mode.get() == NoFallPlus.Mode.Catch))
            .build()
      );
   private final Setting<Integer> maxBlinkPackets = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("max-blink-packets"))
                     .description("Maximum queued movement packets in Blink mode before auto flushing."))
                  .defaultValue(80))
               .range(10, 500)
               .sliderMax(200)
               .visible(() -> this.mode.get() == NoFallPlus.Mode.Blink))
            .build()
      );
   private final Setting<Integer> grimSaveTicks = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("grim-save-ticks"))
                     .description("Ticks before landing to start sending ground=true (GrimSafe mode)."))
                  .defaultValue(2))
               .range(1, 5)
               .visible(() -> this.mode.get() == NoFallPlus.Mode.GrimSafe))
            .build()
      );
   private final Queue<Packet<?>> blinkQueue = new ArrayDeque<>();
   private boolean sending = false;
   private int ticksFalling = 0;

   public NoFallPlus() {
      super(VenomousAddon.CATEGORY, "no-fall-plus", "NoFall bypasses for Grim/Vulcan/NCP/Oxa/Negativity/LAC.");
   }

   public void onDeactivate() {
      this.flushBlink();
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null) {
         if (this.mc.player.onGround()) {
            this.ticksFalling = 0;
         } else {
            this.ticksFalling++;
         }

         switch ((NoFallPlus.Mode)this.mode.get()) {
            case GrimSafe:
               if (this.mc.player.fallDistance >= (Double)this.triggerFallDistance.get()) {
                  double velocityY = this.mc.player.getDeltaMovement().y;
                  if (velocityY < -0.5) {
                     BlockState below = this.mc
                        .level
                        .getBlockState(BlockPos.containing(this.mc.player.getX(), this.mc.player.getY() - 1.5, this.mc.player.getZ()));
                     BlockState below2 = this.mc
                        .level
                        .getBlockState(BlockPos.containing(this.mc.player.getX(), this.mc.player.getY() - 2.5, this.mc.player.getZ()));
                     if (!below.isAir() || !below2.isAir()) {
                        this.mc.player.connection.send(new StatusOnly(true, this.mc.player.horizontalCollision));
                     }
                  }
               }
               break;
            case VulcanMLG:
               if (this.mc.player.fallDistance >= (Double)this.triggerFallDistance.get() + 1.0) {
                  double velocityY = this.mc.player.getDeltaMovement().y;
                  if (velocityY < -0.8) {
                     BlockState below = this.mc
                        .level
                        .getBlockState(BlockPos.containing(this.mc.player.getX(), this.mc.player.getY() - 2.0, this.mc.player.getZ()));
                     if (!below.isAir()) {
                        int waterSlot = this.findWaterBucketSlot();
                        if (waterSlot != -1) {
                           int prev = this.mc.player.getInventory().getSelectedSlot();
                           this.mc.player.getInventory().setSelectedSlot(waterSlot);
                           float prevPitch = this.mc.player.getXRot();
                           this.mc.player.setXRot(90.0F);
                           this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
                           this.mc.player.setXRot(prevPitch);
                           this.mc.player.getInventory().setSelectedSlot(prev);
                        }
                     }
                  }
               }
            case SpoofGround:
            default:
               break;
            case Packet:
               if (this.mc.player.fallDistance >= (Double)this.triggerFallDistance.get() && this.mc.getConnection() != null) {
                  this.mc.getConnection().send(new StatusOnly(true, this.mc.player.horizontalCollision));
                  this.mc.player.fallDistance = 0.0;
               }
               break;
            case Blink:
               if (this.mc.player.onGround()) {
                  this.flushBlink();
               }
               break;
            case Catch:
               if (this.mc.player.fallDistance >= (Double)this.triggerFallDistance.get()) {
                  this.mc.player.setDeltaMovement(this.mc.player.getDeltaMovement().x, (Double)this.catchVelocity.get(), this.mc.player.getDeltaMovement().z);
                  this.mc.player.fallDistance = 0.0;
               }
         }
      }
   }

   @EventHandler
   private void onSend(Send event) {
      if (!this.sending
         && this.mc.player != null
         && event.packet instanceof ServerboundMovePlayerPacket packetMove
         && !(this.mc.player.fallDistance < (Double)this.triggerFallDistance.get())) {
         switch ((NoFallPlus.Mode)this.mode.get()) {
            case SpoofGround:
               event.setCancelled(true);
               this.sending = true;
               double x = packetMove.getX(this.mc.player.getX());
               double y = packetMove.getY(this.mc.player.getY());
               double z = packetMove.getZ(this.mc.player.getZ());
               float yaw = packetMove.getYRot(this.mc.player.getYRot());
               float pitch = packetMove.getXRot(this.mc.player.getXRot());
               if (packetMove.hasPosition() && packetMove.hasRotation()) {
                  event.sendSilently(new PosRot(x, y, z, yaw, pitch, true, this.mc.player.horizontalCollision));
               } else if (packetMove.hasPosition()) {
                  event.sendSilently(new Pos(x, y, z, true, this.mc.player.horizontalCollision));
               } else if (packetMove.hasRotation()) {
                  event.sendSilently(new Rot(yaw, pitch, true, this.mc.player.horizontalCollision));
               } else {
                  event.sendSilently(new StatusOnly(true, this.mc.player.horizontalCollision));
               }

               this.sending = false;
               break;
            case Blink:
               event.setCancelled(true);
               this.blinkQueue.add(event.packet);
               if (this.blinkQueue.size() >= (Integer)this.maxBlinkPackets.get()) {
                  this.flushBlink();
               }
         }
      }
   }

   private void flushBlink() {
      if (this.mc.getConnection() == null) {
         this.blinkQueue.clear();
      } else {
         this.sending = true;

         while (!this.blinkQueue.isEmpty()) {
            this.mc.getConnection().send(this.blinkQueue.poll());
         }

         this.sending = false;
      }
   }

   private int findWaterBucketSlot() {
      for (int i = 0; i < 9; i++) {
         ItemStack stack = this.mc.player.getInventory().getItem(i);
         if (stack.is(Items.WATER_BUCKET)) {
            return i;
         }
      }

      return -1;
   }

   public String getInfoString() {
      return ((NoFallPlus.Mode)this.mode.get()).name();
   }

   public enum Mode {
      GrimSafe,
      VulcanMLG,
      SpoofGround,
      Packet,
      Blink,
      Catch;
   }
}
