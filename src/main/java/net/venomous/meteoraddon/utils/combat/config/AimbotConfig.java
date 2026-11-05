package net.venomous.meteoraddon.utils.combat.config;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.entity.Target;
import net.minecraft.world.entity.EntityType;

/**
 * Aimbot's settings, registered on the module's own groups.
 */
public final class AimbotConfig {

    public final Setting<Double> range;
    public final Setting<Boolean> ignoreWalls;
    public final Setting<Boolean> clickAim;
    public final Setting<Boolean> limitToItems;
    public final Setting<Boolean> checkBlockBreak;
    public final Setting<java.util.Set<EntityType<?>>> allowedItems;
    public final Setting<Double> speed;
    public final Setting<Boolean> gcdFix;
    public final Setting<Boolean> jitter;
    public final Setting<Boolean> predict;
    public final Setting<Target> aimTarget;
    public final Setting<Boolean> autoShoot;
    public final Setting<Double> minDamage;
    public final Setting<Integer> delayMin;
    public final Setting<Integer> delayMax;

    public AimbotConfig(SettingGroup general, SettingGroup projectile, SettingGroup humanlike, SettingGroup anticheat) {
        this.range = general.add(
            new DoubleSetting.Builder()
                .name("range")
                .description("Aim range.")
                .defaultValue(20.0)
                .range(1.0, 50.0)
                .sliderMax(50.0)
                .build()
        );

        this.ignoreWalls = general.add(
            new BoolSetting.Builder()
                .name("ignore-walls")
                .description("Ignore walls when targeting.")
                .defaultValue(false)
                .build()
        );

        this.clickAim = general.add(
            new BoolSetting.Builder()
                .name("click-aim")
                .description("Only aim while clicking.")
                .defaultValue(false)
                .build()
        );

        this.limitToItems = general.add(
            new BoolSetting.Builder()
                .name("limit-to-items")
                .description("Only aim when holding a bow, crossbow or trident.")
                .defaultValue(false)
                .build()
        );

        this.checkBlockBreak = general.add(
            new BoolSetting.Builder()
                .name("check-block-break")
                .description("Don't aim while breaking blocks.")
                .defaultValue(true)
                .build()
        );

        this.allowedItems = general.add(
            new meteordevelopment.meteorclient.settings.EntityTypeListSetting.Builder()
                .name("allowed-items")
                .description("Items that are allowed for aimbot.")
                .build()
        );

        this.speed = humanlike.add(
            new DoubleSetting.Builder()
                .name("speed")
                .description("Aim speed (deg/tick).")
                .defaultValue(30.0)
                .range(1.0, 360.0)
                .sliderMax(360.0)
                .build()
        );

        this.gcdFix = anticheat.add(
            new BoolSetting.Builder()
                .name("gcd-fix")
                .description("Quantize rotation steps to match mouse sensitivity GCD.")
                .defaultValue(true)
                .build()
        );

        this.jitter = humanlike.add(
            new BoolSetting.Builder()
                .name("jitter")
                .description("Add random jitter to rotation.")
                .defaultValue(false)
                .build()
        );

        this.predict = projectile.add(
            new BoolSetting.Builder()
                .name("predict")
                .description("Predict target movement.")
                .defaultValue(true)
                .build()
        );

        this.aimTarget = projectile.add(
            new EnumSetting.Builder<Target>()
                .name("aim-target")
                .description("Which part of the entity to aim at.")
                .defaultValue(Target.Head)
                .build()
        );

        this.autoShoot = general.add(
            new BoolSetting.Builder()
                .name("auto-shoot")
                .description("Automatically shoot when aiming at a target.")
                .defaultValue(false)
                .build()
        );

        this.minDamage = projectile.add(
            new DoubleSetting.Builder()
                .name("min-damage")
                .description("Minimum damage to shoot.")
                .defaultValue(4.0)
                .range(0.0, 30.0)
                .sliderMax(30.0)
                .build()
        );

        this.delayMin = general.add(
            new IntSetting.Builder()
                .name("delay-min")
                .description("Minimum delay between shots (ms).")
                .defaultValue(0)
                .range(0, 1000)
                .sliderMax(1000)
                .build()
        );

        this.delayMax = general.add(
            new IntSetting.Builder()
                .name("delay-max")
                .description("Maximum delay between shots (ms).")
                .defaultValue(0)
                .range(0, 1000)
                .sliderMax(1000)
                .build()
        );
    }
}