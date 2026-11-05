package net.venomous.meteoraddon.utils.combat.config;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.venomous.meteoraddon.modules.combat.KillAura;

/**
 * KillAura's settings, registered on the module's own groups.
 * <p>Names, descriptions, defaults, ranges and visibility are copied verbatim: Meteor saves
 * settings by name, so the user's existing configuration keeps loading.</p>
 */
public final class KillAuraConfig {

    public final Setting<Double> range;
    public final Setting<Boolean> players;
    public final Setting<Boolean> mobs;
    public final Setting<Boolean> animals;
    public final Setting<KillAura.Technique> technique;
    public final Setting<Integer> minCps;
    public final Setting<Integer> maxCps;
    public final Setting<Boolean> respectCooldown;
    public final Setting<Boolean> rotate;
    public final Setting<Boolean> gcdFix;
    public final Setting<Double> rotationSpeed;
    public final Setting<Double> rotationThreshold;
    public final Setting<Boolean> autoBlock;
    public final Setting<KillAura.ACMode> acMode;
    public final Setting<Boolean> vulcanSpeedRandomization;
    public final Setting<Integer> vulcanRandomizationMs;

    public KillAuraConfig(SettingGroup general, SettingGroup vulcan, SettingGroup anticheat) {
        this.range = general.add(
            new DoubleSetting.Builder()
                .name("range")
                .description("Attack range.")
                .defaultValue(3.8)
                .range(1.0, 6.0)
                .sliderMax(6.0)
                .build()
        );

        this.players = general.add(
            new BoolSetting.Builder()
                .name("players")
                .description("Target players.")
                .defaultValue(true)
                .build()
        );

        this.mobs = general.add(
            new BoolSetting.Builder()
                .name("mobs")
                .description("Target mobs.")
                .defaultValue(true)
                .build()
        );

        this.animals = general.add(
            new BoolSetting.Builder()
                .name("animals")
                .description("Target animals.")
                .defaultValue(false)
                .build()
        );

        this.technique = general.add(
            new EnumSetting.Builder<KillAura.Technique>()
                .name("technique")
                .description("Click timing pattern.")
                .defaultValue(KillAura.Technique.STABILIZED)
                .build()
        );

        this.minCps = general.add(
            new IntSetting.Builder()
                .name("min-cps")
                .description("Min CPS.")
                .defaultValue(8)
                .range(1, 20)
                .sliderMax(15)
                .build()
        );

        this.maxCps = general.add(
            new IntSetting.Builder()
                .name("max-cps")
                .description("Max CPS.")
                .defaultValue(11)
                .range(1, 20)
                .sliderMax(15)
                .build()
        );

        this.respectCooldown = general.add(
            new BoolSetting.Builder()
                .name("respect-cooldown")
                .description("Respect attack cooldown (REQUIRED for Grim/NCP).")
                .defaultValue(true)
                .build()
        );

        this.rotate = general.add(
            new BoolSetting.Builder()
                .name("rotate")
                .description("Rotate to target.")
                .defaultValue(true)
                .build()
        );

        this.gcdFix = anticheat.add(
            new BoolSetting.Builder()
                .name("gcd-fix")
                .description("Quantize rotation steps to match mouse sensitivity GCD (bypasses Vulcan/Polar/Grim).")
                .defaultValue(true)
                .build()
        );

        this.rotationSpeed = general.add(
            new DoubleSetting.Builder()
                .name("rotation-speed")
                .description("Rotation speed (deg/tick).")
                .defaultValue(120.0)
                .range(10.0, 360.0)
                .sliderMax(360.0)
                .visible(this.rotate::get)
                .build()
        );

        this.rotationThreshold = general.add(
            new DoubleSetting.Builder()
                .name("rotation-threshold")
                .description("Angle threshold to attack.")
                .defaultValue(30.0)
                .range(1.0, 180.0)
                .sliderMax(90.0)
                .visible(this.rotate::get)
                .build()
        );

        this.autoBlock = general.add(
            new BoolSetting.Builder()
                .name("auto-block")
                .description("Block while not attacking.")
                .defaultValue(false)
                .build()
        );

        this.acMode = general.add(
            new EnumSetting.Builder<KillAura.ACMode>()
                .name("ac-mode")
                .description("Anticheat mode — tunes settings automatically.")
                .defaultValue(KillAura.ACMode.Grim)
                .build()
        );

        this.vulcanSpeedRandomization = vulcan.add(
            new BoolSetting.Builder()
                .name("speed-randomization")
                .description("Add ±100ms randomization to attack timing (bypasses Vulcan AutoClicker).")
                .defaultValue(true)
                .visible(() -> this.acMode.get() == KillAura.ACMode.Vulcan)
                .build()
        );

        this.vulcanRandomizationMs = vulcan.add(
            new IntSetting.Builder()
                .name("randomization-ms")
                .description("±Milliseconds of attack timing randomization.")
                .defaultValue(100)
                .range(20, 300)
                .visible(() -> this.acMode.get() == KillAura.ACMode.Vulcan && this.vulcanSpeedRandomization.get())
                .build()
        );
    }
}