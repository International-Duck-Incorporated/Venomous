package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.KeepSprintRules;

/**
 * Softens the price your own swings charge you.
 *
 * <p>Vanilla punishes the attacker: after every hit, horizontal motion is multiplied by 0.6 and
 * sprint is dropped. This module captures the motion you had going into the swing and puts it
 * back afterwards - scaled by the slider (40% keeps exact vanilla motion; lower keeps more) and
 * with the sprint flag re-raised, so the movement packet leaves with the speed you were
 * keeping, not the speed vanilla took.</p>
 *
 * <p>The shape is Raven B4's {@code KeepSprint}: the same slider range, the same one-way rule
 - the module keeps speed, it never loses more than vanilla. One deviation is deliberate: the
 original's stop-sprint toggle exists to serve its knockback module's own slowdown handling,
 which has no counterpart here, so this port's default is to keep the sprint - the behaviour
 the module is named for - with a toggle to hand it back.</p>
 *
 * <p>And the same honesty as the rest of the family: a server that simulates movement predicts
 * vanilla's 0.6 and your dropped sprint, so on Grim this is a Simulation flag by design. On
 * servers that do not simulate, it is free.</p>
 */
public class KeepSprint extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> slow = this.sgGeneral.add(
        new DoubleSetting.Builder()
            .name("slow-percent")
            .description("How much of vanilla's post-attack slowdown remains. 40 is exact vanilla motion; lower keeps more of your speed.")
            .defaultValue(40.0)
            .range(0.0, KeepSprintRules.MAX_SLOW_PERCENT)
            .sliderMax(KeepSprintRules.MAX_SLOW_PERCENT)
            .build()
    );

    private final Setting<Boolean> keepSprint = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("keep-sprint")
            .description("Re-raise the sprint flag after your own attack.")
            .defaultValue(true)
            .build()
    );

    /** The motion the player had going into the swing, captured before vanilla subtracts its share. */
    private static Vec3 preAttackMotion;
    private static boolean preAttackSprinting;

    public KeepSprint() {
        super(VenomousAddon.CATEGORY, "keep-sprint", "Keeps your speed and your sprint through your own attacks.");
    }

    @Override
    public void onActivate() {
        preAttackMotion = null;
    }

    @Override
    public void onDeactivate() {
        preAttackMotion = null;
    }

    /**
     * Captures the pre-attack state. Fires just before vanilla's penalty is applied, which is
     * the only moment the un-punished motion still exists.
     */
    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (this.mc.player == null) return;

        preAttackMotion = this.mc.player.getDeltaMovement();
        preAttackSprinting = this.mc.player.isSprinting();
    }

    /**
     * Runs from the attack mixin at the tail of {@code Player.attack}, where vanilla's penalty
     * has just been applied. The captured motion is restored through the configured multiplier
     * and the sprint flag is put back the way the settings ask.
     */
    public static void afterOwnAttack(Player attacker) {
        KeepSprint module = module();
        if (module == null || !module.isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player != attacker) return;
        if (preAttackMotion == null) return;

        try {
            float multiplier = KeepSprintRules.motionMultiplier(module.slow.get());

            attacker.setDeltaMovement(new Vec3(
                preAttackMotion.x * multiplier,
                preAttackMotion.y,
                preAttackMotion.z * multiplier));

            if (module.keepSprint.get()) {
                attacker.setSprinting(preAttackSprinting);
            }
        } finally {
            // One capture, one restoration: a stale pre-attack state must never leak into a
            // later attack that never fired its own capture.
            preAttackMotion = null;
        }
    }

    private static KeepSprint module() {
        Modules modules = Modules.get();
        return modules == null ? null : modules.get(KeepSprint.class);
    }

    @Override
    public String getInfoString() {
        return (int) (double) this.slow.get() + "%";
    }
}
