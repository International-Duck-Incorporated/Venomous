package net.venomous.meteoraddon.modules.world;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.PreRotate;

public class AutoFarm extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> range = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("range")).description("Range to harvest/plant crops."))
            .defaultValue(4.5)
            .range(1.0, 6.0)
            .sliderMax(6.0)
            .build()
      );
   private final Setting<Boolean> harvest = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("harvest"))
                  .description("Harvest mature crops."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> plant = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("plant"))
                  .description("Plant seeds on farmland."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("rotate"))
                  .description("Smoothly rotate to crops."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> autoSwitch = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("auto-switch"))
                  .description("Auto switch to seeds in hotbar."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Integer> actionDelay = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("action-delay"))
                  .description("Delay between actions in ticks (humanlike)."))
               .defaultValue(2))
            .range(0, 10)
            .build()
      );
   private int delayCounter = 0;
   private BlockPos currentTarget = null;

   public AutoFarm() {
      super(VenomousAddon.CATEGORY, "auto-farm", "Automatically harvests and plants crops correctly on servers.");
   }

   public void onActivate() {
      this.delayCounter = 0;
      this.currentTarget = null;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.level != null && this.mc.gameMode != null) {
         this.delayCounter++;
         if (this.delayCounter >= (Integer)this.actionDelay.get()) {
            double rangeSq = (Double)this.range.get() * (Double)this.range.get();
            if ((Boolean)this.harvest.get()) {
               BlockPos harvestPos = this.findMatureCrop(rangeSq);
               if (harvestPos != null) {
                  this.currentTarget = harvestPos;
                  if ((Boolean)this.rotate.get()) {
                     PreRotate.rotateThen(Rotations.getYaw(harvestPos), Rotations.getPitch(harvestPos), () -> {
                        this.mc.gameMode.destroyBlock(harvestPos);
                        this.mc.player.swing(InteractionHand.MAIN_HAND);
                     });
                  } else {
                     this.mc.gameMode.destroyBlock(harvestPos);
                     this.mc.player.swing(InteractionHand.MAIN_HAND);
                  }

                  this.delayCounter = 0;
                  return;
               }
            }

            if ((Boolean)this.plant.get()) {
               BlockPos plantPos = this.findEmptyFarmland(rangeSq);
               if (plantPos != null) {
                  int seedSlot = this.findSeedSlot();
                  if (seedSlot != -1) {
                     this.currentTarget = plantPos;
                     if ((Boolean)this.autoSwitch.get()) {
                        this.mc.player.getInventory().setSelectedSlot(seedSlot);
                     }

                     BlockHitResult bhr = new BlockHitResult(Vec3.atCenterOf(plantPos), Direction.UP, plantPos, false);
                     if ((Boolean)this.rotate.get()) {
                        PreRotate.rotateThen(Rotations.getYaw(plantPos), Rotations.getPitch(plantPos), () -> {
                           this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, bhr);
                           this.mc.player.swing(InteractionHand.MAIN_HAND);
                        });
                     } else {
                        this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, bhr);
                        this.mc.player.swing(InteractionHand.MAIN_HAND);
                     }

                     this.delayCounter = 0;
                  }
               }
            }
         }
      }
   }

   private BlockPos findMatureCrop(double rangeSq) {
      BlockPos playerPos = this.mc.player.blockPosition();
      int r = (int)Math.ceil((Double)this.range.get());

      for (int x = -r; x <= r; x++) {
         for (int y = -r; y <= r; y++) {
            for (int z = -r; z <= r; z++) {
               BlockPos pos = playerPos.offset(x, y, z);
               if (!(pos.distSqr(this.mc.player.blockPosition()) > rangeSq)) {
                  BlockState state = this.mc.level.getBlockState(pos);
                  Block block = state.getBlock();
                  if (block instanceof CropBlock crop && crop.isMaxAge(state)) {
                     return pos;
                  }

                  if (block instanceof NetherWartBlock && (Integer)state.getValue(NetherWartBlock.AGE) >= 3) {
                     return pos;
                  }

                  if (block instanceof SugarCaneBlock || block instanceof CactusBlock) {
                     BlockState below = this.mc.level.getBlockState(pos.below());
                     if (below.getBlock() == block) {
                        return pos;
                     }
                  }
               }
            }
         }
      }

      return null;
   }

   private BlockPos findEmptyFarmland(double rangeSq) {
      BlockPos playerPos = this.mc.player.blockPosition();
      int r = (int)Math.ceil((Double)this.range.get());

      for (int x = -r; x <= r; x++) {
         for (int y = -r; y <= r; y++) {
            for (int z = -r; z <= r; z++) {
               BlockPos pos = playerPos.offset(x, y, z);
               if (!(pos.distSqr(this.mc.player.blockPosition()) > rangeSq)) {
                  BlockState state = this.mc.level.getBlockState(pos);
                  if (state.is(Blocks.FARMLAND)) {
                     BlockState above = this.mc.level.getBlockState(pos.above());
                     if (above.isAir()) {
                        return pos.above();
                     }
                  }

                  if (state.is(Blocks.SOUL_SAND)) {
                     BlockState above = this.mc.level.getBlockState(pos.above());
                     if (above.isAir()) {
                        return pos.above();
                     }
                  }
               }
            }
         }
      }

      return null;
   }

   private int findSeedSlot() {
      for (int i = 0; i < 9; i++) {
         ItemStack stack = this.mc.player.getInventory().getItem(i);
         String name = stack.getItem().toString().toLowerCase();
         if (name.contains("seeds")
            || name.contains("seed")
            || name.contains("nether_wart")
            || name.contains("wart")
            || name.contains("carrot")
            || name.contains("potato")
            || name.contains("beetroot")) {
            return i;
         }
      }

      return -1;
   }

   private void rotateTo(BlockPos pos) {
      double dx = pos.getX() + 0.5 - this.mc.player.getX();
      double dy = pos.getY() + 0.5 - (this.mc.player.getY() + this.mc.player.getEyeHeight());
      double dz = pos.getZ() + 0.5 - this.mc.player.getZ();
      double dist = Math.sqrt(dx * dx + dz * dz);
      float targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
      float targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, dist)));
      float yawDiff = this.wrapDegrees(targetYaw - this.mc.player.getYRot());
      float pitchDiff = this.wrapDegrees(targetPitch - this.mc.player.getXRot());
      float maxTurn = 10.0F;
      if (Math.abs(yawDiff) > maxTurn) {
         yawDiff = Math.signum(yawDiff) * maxTurn;
      }

      if (Math.abs(pitchDiff) > maxTurn) {
         pitchDiff = Math.signum(pitchDiff) * maxTurn;
      }

      this.mc.player.setYRot(this.mc.player.getYRot() + yawDiff);
      this.mc.player.setXRot(this.mc.player.getXRot() + pitchDiff);
   }

   private boolean isLookingAt(BlockPos pos, float threshold) {
      double dx = pos.getX() + 0.5 - this.mc.player.getX();
      double dy = pos.getY() + 0.5 - (this.mc.player.getY() + this.mc.player.getEyeHeight());
      double dz = pos.getZ() + 0.5 - this.mc.player.getZ();
      double dist = Math.sqrt(dx * dx + dz * dz);
      float targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
      float targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, dist)));
      float yawDiff = Math.abs(this.wrapDegrees(targetYaw - this.mc.player.getYRot()));
      float pitchDiff = Math.abs(this.wrapDegrees(targetPitch - this.mc.player.getXRot()));
      return yawDiff < threshold && pitchDiff < threshold;
   }

   private float wrapDegrees(float v) {
      while (v > 180.0F) {
         v -= 360.0F;
      }

      while (v < -180.0F) {
         v += 360.0F;
      }

      return v;
   }

   public String getInfoString() {
      return this.currentTarget != null ? this.currentTarget.toShortString() : null;
   }
}
