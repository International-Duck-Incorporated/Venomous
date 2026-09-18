package net.venomous.meteoraddon.modules.misc;

import java.util.HashSet;
import java.util.Set;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.AABB;
import net.venomous.meteoraddon.VenomousAddon;

public class TNTTimer extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Boolean> warn = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("warn")).description("Warn in chat when nearby TNT is about to explode.")).defaultValue(true))
            .build()
      );
   private final Setting<Integer> warnFuse = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("warn-fuse"))
                     .description("Warn when TNT fuse is at or below this many ticks."))
                  .defaultValue(20))
               .range(1, 80)
               .sliderMax(80)
               .visible(() -> (Boolean)this.warn.get()))
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(((Builder)((Builder)((Builder)new Builder().name("render")).description("Render boxes around primed TNT.")).defaultValue(true)).build());
   private final Setting<SettingColor> safeColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("safe-color"))
                  .description("Color for TNT with a longer fuse."))
               .defaultValue(new SettingColor(255, 220, 0, 120))
               .visible(() -> (Boolean)this.render.get()))
            .build()
      );
   private final Setting<SettingColor> dangerColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("danger-color"))
                  .description("Color for TNT that is close to exploding."))
               .defaultValue(new SettingColor(255, 0, 0, 180))
               .visible(() -> (Boolean)this.render.get()))
            .build()
      );
   private final Setting<Double> textScale = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("text-scale"))
                  .description("Scale of the timer text."))
               .defaultValue(1.5)
               .min(0.5)
               .sliderMax(3.0)
               .visible(() -> (Boolean)this.render.get()))
            .build()
      );
   private final Set<Integer> warned = new HashSet<>();

   public TNTTimer() {
      super(VenomousAddon.CATEGORY, "v-tnt-timer", "Highlights primed TNT and shows a timer above it.");
   }

   public void onDeactivate() {
      this.warned.clear();
   }

   @EventHandler
   private void onTick(Post event) {
      if ((Boolean)this.warn.get() && this.mc.level != null) {
         HashSet<Integer> present = new HashSet<>();

         for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof PrimedTnt) {
               present.add(entity.getId());
            }
         }

         this.warned.retainAll(present);

         for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof PrimedTnt tnt) {
               int fuse = tnt.getFuse();
               if (fuse <= (Integer)this.warnFuse.get() && this.warned.add(tnt.getId())) {
                  this.warning("TNT exploding in %d ticks!", new Object[]{fuse});
               }

               if (fuse > (Integer)this.warnFuse.get()) {
                  this.warned.remove(tnt.getId());
               }
            }
         }
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if ((Boolean)this.render.get() && this.mc.level != null) {
         for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof PrimedTnt tnt) {
               int fuse = tnt.getFuse();
               SettingColor c = fuse <= this.warnFuse.get() ? (SettingColor)this.dangerColor.get() : (SettingColor)this.safeColor.get();
               AABB box = tnt.getBoundingBox();
               event.renderer.box(box, c, c, ShapeMode.Both, 0);
               String text = String.valueOf(fuse);
               double x = (box.minX + box.maxX) / 2.0;
               double y = box.maxY + 0.3;
               double var13 = (box.minZ + box.maxZ) / 2.0;
            }
         }
      }
   }
}
