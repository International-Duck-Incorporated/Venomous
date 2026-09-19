package net.venomous.meteoraddon.modules.misc;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.PlaySoundEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

public class SoundESP extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Boolean> useFilter = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("use-filter")).description("Only show sounds in the filter list.")).defaultValue(false)).build());
   private final Setting<List<SoundEvent>> filter = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.SoundEventListSetting.Builder)((meteordevelopment.meteorclient.settings.SoundEventListSetting.Builder)((meteordevelopment.meteorclient.settings.SoundEventListSetting.Builder)((meteordevelopment.meteorclient.settings.SoundEventListSetting.Builder)new meteordevelopment.meteorclient.settings.SoundEventListSetting.Builder()
                        .name("filter"))
                     .description("Sounds to track when the filter is enabled."))
                  .defaultValue(new ArrayList(0)))
               .visible(() -> (Boolean)this.useFilter.get()))
            .build()
      );
   private final Setting<Integer> lifetime = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("lifetime"))
                  .description("Ticks a sound marker stays visible."))
               .defaultValue(80))
            .min(10)
            .sliderMax(400)
            .build()
      );
   private final Setting<Integer> maxSounds = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("max-sounds"))
                  .description("Maximum markers tracked simultaneously. Oldest evicted first."))
               .defaultValue(64))
            .min(8)
            .sliderMax(256)
            .build()
      );
   private final Setting<Boolean> logChat = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("log-to-chat")).description("Print each sound position to chat.")).defaultValue(false)).build());
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                  .name("shape-mode"))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder().name("side-color"))
            .defaultValue(new SettingColor(255, 165, 0, 50))
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder().name("line-color"))
            .defaultValue(new SettingColor(255, 165, 0, 210))
            .build()
      );
   private final List<Vec3> positions = new ArrayList<>();
   private final List<Integer> timers = new ArrayList<>();

   public SoundESP() {
      super(VenomousAddon.CATEGORY, "sound-esp", "Renders ESP boxes at the origin of nearby sound events.");
   }

   public void onDeactivate() {
      this.positions.clear();
      this.timers.clear();
   }

   @EventHandler
   private void onTick(Pre event) {
      for (int i = this.timers.size() - 1; i >= 0; i--) {
         this.timers.set(i, this.timers.get(i) - 1);
         if (this.timers.get(i) <= 0) {
            this.timers.remove(i);
            this.positions.remove(i);
         }
      }
   }

   @EventHandler
   private void onPlaySound(PlaySoundEvent event) {
      SoundInstance si = event.sound;
      if (!(Boolean)this.useFilter.get()
         || this.filter.get().isEmpty()
         || this.filter.get().stream().anyMatch(se -> se.location().equals(si.getIdentifier()))) {
         Vec3 pos = new Vec3(si.getX() - 0.5, si.getY() - 0.5, si.getZ() - 0.5);
         if (!this.positions.contains(pos)) {
            if (this.positions.size() >= (Integer)this.maxSounds.get()) {
               this.positions.remove(0);
               this.timers.remove(0);
            }

            this.positions.add(pos);
            this.timers.add((Integer)this.lifetime.get());
            if ((Boolean)this.logChat.get()) {
               this.info("Sound [%s] at %.1f %.1f %.1f", new Object[]{si.getIdentifier().getPath(), pos.x + 0.5, pos.y + 0.5, pos.z + 0.5});
            }
         }
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      for (Vec3 pos : this.positions) {
         event.renderer
            .box(
               new AABB(pos.x, pos.y, pos.z, pos.x + 1.0, pos.y + 1.0, pos.z + 1.0),
               (Color)this.sideColor.get(),
               (Color)this.lineColor.get(),
               (ShapeMode)this.shapeMode.get(),
               0
            );
      }
   }
}
