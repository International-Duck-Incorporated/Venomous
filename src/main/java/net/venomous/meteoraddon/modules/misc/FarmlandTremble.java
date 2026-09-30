package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.IntSetting;

public class FarmlandTremble extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> radius = this.sgGeneral
      .add(
         new IntSetting.Builder().name("radius").description("Radius in blocks to tremble farmland.").defaultValue(5)
            .min(1)
            .max(16)
            .build()
      );

   public FarmlandTremble() {
      super(VenomousAddon.CATEGORY, "farmland-tremble", "Automatically breaks nearby farmland (turns to dirt) within a radius.");
   }

   @EventHandler
   private void onTick(Post var1) {
      if (this.mc.level != null && this.mc.player != null) {
         BlockPos var2 = this.mc.player.blockPosition();
         int var3 = (Integer)this.radius.get();

         for (int var4 = -var3; var4 <= var3; var4++) {
            for (int var5 = -var3; var5 <= var3; var5++) {
               for (int var6 = -var3; var6 <= var3; var6++) {
                  BlockPos var7 = var2.offset(var4, var5, var6);
                  if (this.mc.level.getBlockState(var7).getBlock() instanceof FarmlandBlock) {
                     this.mc.level.setBlock(var7, Blocks.DIRT.defaultBlockState(), 3);
                  }
               }
            }
         }
      }
   }
}
