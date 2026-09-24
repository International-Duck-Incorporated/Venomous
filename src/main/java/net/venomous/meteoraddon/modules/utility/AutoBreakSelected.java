package net.venomous.meteoraddon.modules.utility;

import java.util.List;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BlockListSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.PreRotate;

public class AutoBreakSelected extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<List<Block>> blocks = this.sgGeneral
      .add(new Builder().name("blocks").description("Block types to automatically mine.").build());
   private final Setting<Integer> radius = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("radius")
            .description("Block radius around the player to scan for targets.")
            .defaultValue(4)
            .range(1, 6)
            .build()
      );
   private final Setting<Integer> failTicks = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("fail-ticks")
            .description("Give up on the current block after this many ticks without it breaking.")
            .defaultValue(40)
            .range(10, 200)
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("rotate")
            .description("Rotate toward the target block while mining.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> grimBypass = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("grim-vulcan-bypass")
            .description("Bypass Grim/Vulcan by slowing down actions and smoothing rotations.")
            .defaultValue(true)
            .build()
      );
   private BlockPos currentTarget = null;
   private int miningTicks = 0;
   private int bypassDelay = 0;

   public AutoBreakSelected() {
      super(VenomousAddon.CATEGORY, "auto-break-selected", "Automatically mines nearby blocks of the selected types.");
   }

   @Override
   public void onActivate() {
      this.resetMining();
      this.bypassDelay = 0;
   }

   @Override
   public void onDeactivate() {
      this.resetMining();
      this.bypassDelay = 0;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.level != null) {
         if (this.bypassDelay > 0) {
            this.bypassDelay--;
         } else {
            if (this.currentTarget != null && this.mc.level.isEmptyBlock(this.currentTarget)) {
               this.resetMining();
               if ((Boolean)this.grimBypass.get()) {
                  this.bypassDelay = 2;
               }
            }

            if (this.currentTarget != null && this.miningTicks > (Integer)this.failTicks.get()) {
               this.error("Mining timed out.", new Object[0]);
               this.toggle();
            } else {
               if (this.currentTarget == null) {
                  this.currentTarget = this.findTarget();
                  if (this.currentTarget == null) {
                     return;
                  }

                  this.miningTicks = 0;
               }

               if (!this.isInRange(this.currentTarget)) {
                  this.resetMining();
               } else if ((Boolean)this.rotate.get()) {
                  PreRotate.rotateThen(Rotations.getYaw(this.currentTarget), Rotations.getPitch(this.currentTarget), this::doBreak);
               } else {
                  this.doBreak();
               }
            }
         }
      }
   }

   private void doBreak() {
      if (this.mc.gameMode != null) {
         this.mc.gameMode.continueDestroyBlock(this.currentTarget, this.getDirection(this.currentTarget));
         this.mc.player.swing(InteractionHand.MAIN_HAND);
         this.miningTicks++;
      }
   }

   private BlockPos findTarget() {
      if (((List)this.blocks.get()).isEmpty()) {
         return null;
      }

      int r = (Integer)this.radius.get();
      BlockPos playerPos = this.mc.player.blockPosition();
      BlockPos best = null;
      double bestDist = Double.MAX_VALUE;

      for (int x = -r; x <= r; x++) {
         for (int y = -r; y <= r; y++) {
            for (int z = -r; z <= r; z++) {
               BlockPos pos = playerPos.offset(x, y, z);
               BlockState state = this.mc.level.getBlockState(pos);
               double dist;
               if (((List)this.blocks.get()).contains(state.getBlock())
                  && !(state.getDestroySpeed(this.mc.level, pos) < 0.0F)
                  && (dist = this.mc.player.distanceToSqr(Vec3.atCenterOf(pos))) < bestDist) {
                  bestDist = dist;
                  best = pos;
               }
            }
         }
      }

      return best;
   }

   private boolean isInRange(BlockPos pos) {
      double r = ((Integer)this.radius.get()).intValue();
      return this.mc.player.distanceToSqr(Vec3.atCenterOf(pos)) <= r * r;
   }

   private Direction getDirection(BlockPos pos) {
      Vec3 eyes = this.mc.player.getEyePosition();
      double dx = pos.getX() + 0.5 - eyes.x;
      double dy = pos.getY() + 0.5 - eyes.y;
      double dz = pos.getZ() + 0.5 - eyes.z;
      Direction best = Direction.UP;
      double bestDot = -Double.MAX_VALUE;

      for (Direction dir : Direction.values()) {
         double dot = -(dir.getStepX() * dx + dir.getStepY() * dy + dir.getStepZ() * dz);
         if (dot > bestDot) {
            bestDot = dot;
            best = dir;
         }
      }

      return best;
   }

   private void resetMining() {
      this.currentTarget = null;
      this.miningTicks = 0;
   }
}
