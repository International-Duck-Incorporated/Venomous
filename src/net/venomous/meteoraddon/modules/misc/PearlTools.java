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
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

public class PearlTools extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Boolean> landingWarning = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("landing-warning")).description("Warn when a non-owned pearl is predicted to land near you."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> dangerRange = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("danger-range"))
               .description("Distance from predicted landing to warn."))
            .defaultValue(6.0)
            .range(1.0, 32.0)
            .sliderMax(16.0)
            .build()
      );
   private final Setting<Integer> simSteps = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("sim-steps"))
                  .description("Ticks to simulate pearl trajectory."))
               .defaultValue(80))
            .range(10, 200)
            .sliderMax(120)
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(((Builder)((Builder)((Builder)new Builder().name("render")).description("Render pearl trajectory and landing boxes.")).defaultValue(true)).build());
   private final Setting<SettingColor> color = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("color"))
               .description("Pearl render color."))
            .defaultValue(new SettingColor(80, 200, 255, 210))
            .build()
      );
   private final Set<Integer> warned = new HashSet<>();

   public PearlTools() {
      super(VenomousAddon.CATEGORY, "v-pearl-tools", "Tracks ender pearls, predicts landing spots and warns about dangerous pearls.");
   }

   public void onDeactivate() {
      this.warned.clear();
   }

   @EventHandler
   private void onTick(Post event) {
      if ((Boolean)this.landingWarning.get() && this.mc.player != null && this.mc.level != null) {
         HashSet<Integer> present = new HashSet<>();

         for (Entity e : this.mc.level.entitiesForRendering()) {
            if (e.getType() == EntityType.ENDER_PEARL) {
               present.add(e.getId());
            }
         }

         this.warned.retainAll(present);

         for (Entity e : this.mc.level.entitiesForRendering()) {
            Vec3 landing;
            Projectile p;
            if (e.getType() == EntityType.ENDER_PEARL
               && e instanceof Projectile
               && (p = (Projectile)e).getOwner() != this.mc.player
               && (landing = this.simulate(e, null)) != null
               && this.mc.player.distanceToSqr(landing) <= (Double)this.dangerRange.get() * (Double)this.dangerRange.get()
               && this.warned.add(e.getId())) {
               this.warning("Danger pearl landing nearby: %.1f blocks", new Object[]{Math.sqrt(this.mc.player.distanceToSqr(landing))});
            }
         }
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if ((Boolean)this.render.get() && this.mc.player != null && this.mc.level != null) {
         for (Entity e : this.mc.level.entitiesForRendering()) {
            if (e.getType() == EntityType.ENDER_PEARL) {
               Vec3 landing = this.simulate(e, event);
               if (landing != null) {
                  event.renderer
                     .box(
                        landing.x - 0.15,
                        landing.y - 0.15,
                        landing.z - 0.15,
                        landing.x + 0.15,
                        landing.y + 0.15,
                        landing.z + 0.15,
                        (Color)this.color.get(),
                        (Color)this.color.get(),
                        ShapeMode.Both,
                        0
                     );
               }
            }
         }
      }
   }

   private Vec3 simulate(Entity pearl, Render3DEvent event) {
      if (this.mc.level == null) {
         return null;
      }

      Vec3 pos = pearl.position();
      Vec3 vel = pearl.getDeltaMovement();
      Vec3 prev = pos;

      for (int i = 0; i < this.simSteps.get(); i++) {
         vel = new Vec3(vel.x * 0.99, (vel.y - 0.03) * 0.99, vel.z * 0.99);
         Vec3 next = pos.add(vel);
         if (event != null) {
            event.renderer.line(prev.x, prev.y, prev.z, next.x, next.y, next.z, (Color)this.color.get());
         }

         BlockPos bp = BlockPos.containing(next.x, next.y, next.z);
         if (!this.mc.level.getBlockState(bp).isAir() && !this.mc.level.getBlockState(bp).getFluidState().isEmpty()) {
            return next;
         }

         if (!this.mc.level.getBlockState(bp).isAir()) {
            return next;
         }

         prev = next;
         pos = next;
      }

      return pos;
   }
}
