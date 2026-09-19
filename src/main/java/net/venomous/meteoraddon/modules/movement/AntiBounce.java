package net.venomous.meteoraddon.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

public class AntiBounce extends Module {
   public AntiBounce() {
      super(VenomousAddon.CATEGORY, "v-anti-bounce", "Prevents bouncing on slime blocks and beds.");
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.level != null) {
         BlockPos pos = this.mc.player.blockPosition();
         BlockState current = this.mc.level.getBlockState(pos);
         BlockState below = this.mc.level.getBlockState(pos.below());

         if (current.getBlock() instanceof SlimeBlock || current.getBlock() instanceof BedBlock
            || below.getBlock() instanceof SlimeBlock || below.getBlock() instanceof BedBlock) {
            Vec3 motion = this.mc.player.getDeltaMovement();
            if (motion.y > 0.0) {
               this.mc.player.setDeltaMovement(motion.x, 0.0, motion.z);
            }
         }
      }
   }
}
