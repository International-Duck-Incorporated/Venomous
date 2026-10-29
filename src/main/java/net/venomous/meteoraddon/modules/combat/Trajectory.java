package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.LivingEntity;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.EntityPredict;
import net.venomous.meteoraddon.utils.RodAimMath;

public class Trajectory extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Boolean> render = sgGeneral.add(new BoolSetting.Builder()
        .name("render")
        .description("Show trajectory line.")
        .defaultValue(true)
        .build());

    public Trajectory() {
        super(VenomousAddon.CATEGORY, "trajectory", "Predict projectile trajectory using entity motion.");
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null || this.mc.level == null) return;
        LivingEntity target = this.findTarget();
        if (target == null) return;
        double[] predicted = EntityPredict.predict(
            target.getX(), target.getY(), target.getZ(),
            target.getDeltaMovement().x, target.getDeltaMovement().y, target.getDeltaMovement().z,
            target.onGround(), 3);
    }

    private LivingEntity findTarget() {
        if (this.mc.level == null || this.mc.player == null) return null;
        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (LivingEntity e : this.mc.level.getEntitiesOfClass(LivingEntity.class, this.mc.player.getBoundingBox().inflate(8.0))) {
            if (e == this.mc.player || e.isRemoved() || e.isDeadOrDying()) continue;
            double dSq = e.distanceToSqr(this.mc.player);
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                best = e;
            }
        }
        return best;
    }
}
