package net.venomous.meteoraddon.modules.esp;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

public class VLightESP extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> range = this.sgGeneral
      .add(((Builder)((Builder)new Builder().name("range")).defaultValue(8)).min(2).max(16).sliderMax(16).build());
   private final Setting<Double> minLight = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder().name("min-light"))
            .defaultValue(0.0)
            .min(0.0)
            .max(15.0)
            .sliderMax(15.0)
            .build()
      );
   private final Setting<Double> maxLight = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder().name("max-light"))
            .defaultValue(7.0)
            .min(0.0)
            .max(15.0)
            .sliderMax(15.0)
            .build()
      );
   private final Map<BlockPos, Integer> lightLevels = new HashMap<>();

   public VLightESP() {
      super(VenomousAddon.CATEGORY, "v-light-esp", "Displays light levels on blocks.");
   }

   public void onDeactivate() {
      this.lightLevels.clear();
   }

   @EventHandler
   private void onTick(Post event) {
      if (this.mc.player != null && this.mc.level != null) {
         this.lightLevels.clear();
         BlockPos playerPos = this.mc.player.blockPosition();
         int r = (Integer)this.range.get();

         for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
               for (int z = -r; z <= r; z++) {
                  BlockPos pos = playerPos.offset(x, y, z);
                  int light = this.mc.level.getMaxLocalRawBrightness(pos);
                  if (light >= (Double)this.minLight.get() && light <= (Double)this.maxLight.get()) {
                     this.lightLevels.put(pos, light);
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      for (Entry<BlockPos, Integer> entry : this.lightLevels.entrySet()) {
         BlockPos pos = entry.getKey();
         int light = entry.getValue();
         String text = String.valueOf(light);
         Vec3 var7 = Vec3.atCenterOf(pos);
      }
   }

   public String getInfoString() {
      return this.lightLevels.isEmpty() ? null : this.lightLevels.size() + " blocks";
   }
}
