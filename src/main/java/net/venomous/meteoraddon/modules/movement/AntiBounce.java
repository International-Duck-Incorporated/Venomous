package net.venomous.meteoraddon.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.venomous.meteoraddon.VenomousAddon;

public class AntiBounce extends Module {
   public AntiBounce() {
      super(VenomousAddon.CATEGORY, "anti-bounce", "Prevents bouncing on slime blocks and beds.");
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.level != null && this.mc.player.getDeltaMovement().y > 0.0) {
         if (this.isBouncingBlock()) {
            this.mc.player.setDeltaMovement(this.mc.player.getDeltaMovement().x, 0.0, this.mc.player.getDeltaMovement().z);
         }
      }
   }

   private boolean isBouncingBlock() {
      BlockPos pos = this.mc.player.getBlockPosBelowThatAffectsMyMovement();
      BlockState state = this.mc.level.getBlockState(pos);
      if (this.isBouncy(state)) {
         return true;
      }

      BlockPos feet = this.mc.player.blockPosition();
      if (this.isBouncy(this.mc.level.getBlockState(feet))) {
         return true;
      }

      return this.isBouncy(this.mc.level.getBlockState(feet.below()));
   }

   private boolean isBouncy(BlockState state) {
      return state.is(Blocks.SLIME_BLOCK) || state.getBlock() instanceof BedBlock;
   }
}
