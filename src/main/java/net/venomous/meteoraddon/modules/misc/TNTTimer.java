package net.venomous.meteoraddon.modules.misc;

import java.util.HashSet;
import java.util.Set;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.AABB;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;

public class TNTTimer extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final Setting<Boolean> warn = this.sgGeneral
        .add(
            new BoolSetting.Builder().name("warn").description("Warn in chat when nearby TNT is about to explode.").defaultValue(true)
                .build()
        );
    private final Setting<Integer> warnFuse = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("warn-fuse")
                .description("Warn when TNT fuse is at or below this many ticks.")
                .defaultValue(20)
                .range(1, 80)
                .sliderMax(80)
                .visible(() -> (Boolean)this.warn.get())
                .build()
        );
    private final Setting<Boolean> render = this.sgRender
        .add(new BoolSetting.Builder().name("render").description("Render boxes around primed TNT.").defaultValue(true).build());
    private final Setting<SettingColor> safeColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("safe-color")
                .description("Color for TNT with a longer fuse.")
                .defaultValue(new SettingColor(255, 220, 0, 120))
                .visible(() -> (Boolean)this.render.get())
                .build()
        );
    private final Setting<SettingColor> dangerColor = this.sgRender
        .add(
            new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                .name("danger-color")
                .description("Color for TNT that is close to exploding.")
                .defaultValue(new SettingColor(255, 0, 0, 180))
                .visible(() -> (Boolean)this.render.get())
                .build()
        );
    // Removed: a "text-scale" setting, and with it the per-TNT fuse counter it controlled.
    //
    // The onRender handler computed the fuse count and a world position for it, then discarded
    // both - the timer text was never actually drawn. Implementing it properly is blocked on
    // this Minecraft version's renderer: MultiBufferSource no longer exists, and Font renders
    // through a GpuTextureView pipeline with no BufferSource obtainable from Render3DEvent.
    // Rather than ship a control that still does nothing, the setting is gone until the
    // counters can be drawn for real.
    private final Set<Integer> warned = new HashSet<>();

    public TNTTimer() {
        super(VenomousAddon.CATEGORY, "tnt-timer", "Highlights primed TNT and shows a timer above it.");
    }

    @Override
    public void onActivate() {
        this.warned.clear();
    }

    @Override
    public void onDeactivate() {
        this.warned.clear();
    }

    @EventHandler
    private void onTick(Pre event) {
        if (!(Boolean)this.warn.get() || this.mc.level == null) return;

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

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!(Boolean)this.render.get() || this.mc.level == null || this.mc.player == null) return;

        double maxDistSq = 64.0 * 64.0;

        for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (!(entity instanceof PrimedTnt tnt)) continue;
            if (this.mc.player.distanceToSqr(tnt) > maxDistSq) continue;

            int fuse = tnt.getFuse();
            SettingColor color = fuse <= (Integer)this.warnFuse.get()
                ? (SettingColor)this.dangerColor.get()
                : (SettingColor)this.safeColor.get();
            AABB box = tnt.getBoundingBox();

            // Box height doubles as the fuse readout: taller while the charge is long, so the
            // danger is legible in world space without needing a text renderer.
            float lift = 0.1F + 0.06F * Math.min(fuse, 40);
            AABB marker = new AABB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY + lift, box.maxZ);
            event.renderer.box(marker, color, color, ShapeMode.Both, 0);
            event.renderer.box(box, color, color, ShapeMode.Both, 0);
        }
    }
}
