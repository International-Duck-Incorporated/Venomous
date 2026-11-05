package net.venomous.meteoraddon.modules.render;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.venomous.meteoraddon.VenomousAddon;

/**
 * Chams visibility module.
 *
 * <p>Ships the behaviour the name promises rather than sitting in the module list doing nothing:
 * every qualifying entity's hitbox is outlined on the ESP render pass, which is drawn without
 * depth occlusion, so the markers stay visible through terrain and walls. This is the render-time
 * half of chams - the entities remain ordinary world entities, only their visibility changes.</p>
 */
public class Chams extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> range = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("range")
            .description("Radius in blocks within which entities are highlighted.")
            .defaultValue(32)
            .min(4)
            .max(128)
            .sliderMax(128)
            .build()
    );

    private final Setting<Boolean> players = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("players")
            .description("Highlight players.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> livingEntities = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("living-entities")
            .description("Highlight non-player living entities.")
            .defaultValue(false)
            .build()
    );

    public Chams() {
        super(VenomousAddon.CATEGORY, "chams", "Chams visibility module.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.mc.player == null || this.mc.level == null) {
            return;
        }

        boolean wantPlayers = this.players.get();
        boolean wantLiving = this.livingEntities.get();
        if (!wantPlayers && !wantLiving) {
            return;
        }

        double rangeSquare = (double)this.range.get() * this.range.get();
        Color fill = new Color(255, 60, 60, 40);
        Color outline = new Color(255, 60, 60, 200);

        for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity == this.mc.player || !entity.isAlive()) {
                continue;
            }

            boolean player = entity instanceof Player;
            if (player ? !wantPlayers : !wantLiving) {
                continue;
            }
            if (!(entity instanceof LivingEntity)) {
                continue;
            }

            if (this.mc.player.distanceToSqr(entity) > rangeSquare) {
                continue;
            }

            event.renderer.box(entity.getBoundingBox(), fill, outline, ShapeMode.Both, 0);
        }
    }

    @Override
    public String getInfoString() {
        return this.players.get() || this.livingEntities.get()
            ? "range " + this.range.get()
            : null;
    }
}
