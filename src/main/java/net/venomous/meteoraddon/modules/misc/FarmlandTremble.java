package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.FarmlandBlock;
import net.venomous.meteoraddon.VenomousAddon;

public class FarmlandTremble extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> radius = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("radius")
            .description("Radius in blocks to tremble farmland.")
            .defaultValue(4)
            .min(1)
            .max(8)
            .build()
      );

   public FarmlandTremble() {
      super(VenomousAddon.CATEGORY, "farmland-tremble", "Automatically breaks nearby farmland within a radius.");
   }

   @EventHandler
   private void onTick(Post var1) {
      if (this.mc.level != null && this.mc.player != null && this.mc.gameMode != null) {
         BlockPos center = this.mc.player.blockPosition();
         int r = (Integer)this.radius.get();

         for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
               for (int z = -r; z <= r; z++) {
                  BlockPos pos = center.offset(x, y, z);
                  if (this.mc.level.getBlockState(pos).getBlock() instanceof FarmlandBlock) {
                     this.mc.gameMode.destroyBlock(pos);
                  }
               }
            }
         }
      }
   }
}
