package net.venomous.meteoraddon.utils;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ContainerAutomator {
   private static final double ARRIVE_DIST_SQ = 17.64;
   private static final int OPEN_TIMEOUT_TICKS = 60;
   private static final int PATH_TIMEOUT_TICKS = 400;
   private static final int ROTATION_PRIORITY = 70;
   private final Deque<BlockPos> queue = new ArrayDeque<>();
   private final ContainerAutomator.Callback callback;
   private BlockPos current;
   private ContainerAutomator.State state = ContainerAutomator.State.IDLE;
   private int timer;
   private boolean warnedNoBaritone;

   public ContainerAutomator(ContainerAutomator.Callback var1) {
      this.callback = var1;
   }

   public void start(Collection<BlockPos> var1) {
      this.stop();
      if (!var1.isEmpty()) {
         if (!BaritoneUtils.IS_AVAILABLE && !this.warnedNoBaritone) {
            ChatUtils.warning("No Baritone (or Voyager) found - this will only work on containers you're already next to.", new Object[0]);
            this.warnedNoBaritone = true;
         }

         this.queue.addAll(var1);
         this.next();
      }
   }

   public boolean isRunning() {
      return this.state != ContainerAutomator.State.IDLE;
   }

   public int remaining() {
      return this.queue.size() + (this.current != null ? 1 : 0);
   }

   public void stop() {
      if (this.state == ContainerAutomator.State.OPENING || this.state == ContainerAutomator.State.WORKING) {
         this.closeScreen();
      }

      if (PathManagers.get().isPathing()) {
         PathManagers.get().stop();
      }

      this.queue.clear();
      this.current = null;
      this.state = ContainerAutomator.State.IDLE;
      this.timer = 0;
   }

   public void tick() {
      if (this.state != ContainerAutomator.State.IDLE) {
         if (MeteorClient.mc.player != null && MeteorClient.mc.level != null) {
            switch (this.state) {
               case IDLE:
               default:
                  break;
               case PATHING:
                  this.tickPathing();
                  break;
               case OPENING:
                  this.tickOpening();
                  break;
               case WORKING:
                  this.tickWorking();
            }
         } else {
            this.stop();
         }
      }
   }

   private void next() {
      this.current = this.queue.poll();
      if (this.current == null) {
         this.state = ContainerAutomator.State.IDLE;
         this.callback.onFinished();
      } else {
         PathManagers.get().moveTo(this.current, false);
         this.state = ContainerAutomator.State.PATHING;
         this.timer = 0;
      }
   }

   private void tickPathing() {
      this.timer++;
      double var1 = PlayerUtils.squaredDistanceTo(this.current.getX() + 0.5, this.current.getY() + 0.5, this.current.getZ() + 0.5);
      if (var1 <= 17.64) {
         if (PathManagers.get().isPathing()) {
            PathManagers.get().stop();
         }

         BlockPos var3 = this.current;
         Rotations.rotate(Rotations.getYaw(var3), Rotations.getPitch(var3), 70, () -> {
            BlockHitResult var1x = new BlockHitResult(Vec3.atCenterOf(var3), Direction.UP, var3, false);
            BlockUtils.interact(var1x, InteractionHand.MAIN_HAND, true);
         });
         this.state = ContainerAutomator.State.OPENING;
         this.timer = 0;
      } else {
         if (!PathManagers.get().isPathing() || this.timer > 400) {
            this.next();
         }
      }
   }

   private void tickOpening() {
      this.timer++;
      if (MeteorClient.mc.screen instanceof AbstractContainerScreen) {
         this.state = ContainerAutomator.State.WORKING;
         this.timer = 0;
      } else {
         if (this.timer > 60) {
            this.next();
         }
      }
   }

   private void tickWorking() {
      if (MeteorClient.mc.screen instanceof AbstractContainerScreen var1) {
         this.timer++;
         if (this.callback.onOpen(var1, this.current, this.timer)) {
            this.closeScreen();
            this.next();
         }
      } else {
         this.next();
      }
   }

   private void closeScreen() {
      if (MeteorClient.mc.screen != null) {
         MeteorClient.mc.setScreen(null);
      }
   }

   public static boolean isSupportedContainer(BlockEntity var0) {
      return var0 instanceof ChestBlockEntity
         || var0 instanceof BarrelBlockEntity
         || var0 instanceof ShulkerBoxBlockEntity
         || var0 instanceof EnderChestBlockEntity
         || var0 instanceof AbstractFurnaceBlockEntity
         || var0 instanceof BrewingStandBlockEntity
         || var0 instanceof DispenserBlockEntity
         || var0 instanceof HopperBlockEntity;
   }

   public interface Callback {
      boolean onOpen(AbstractContainerScreen<?> var1, BlockPos var2, int var3);

      default void onFinished() {
      }
   }

   private enum State {
      IDLE,
      PATHING,
      OPENING,
      WORKING;
   }
}
