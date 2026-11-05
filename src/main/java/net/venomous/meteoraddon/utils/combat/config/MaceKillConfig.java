package net.venomous.meteoraddon.utils.combat.config;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.minecraft.world.entity.EntityType;
import net.venomous.meteoraddon.modules.combat.MaceKill;

/**
 * MaceKill's settings, registered on the module's own groups.
 */
public final class MaceKillConfig {

    public final Setting<MaceKill.Mode> mode;
    public final Setting<Integer> fakeFallHeight;
    public final Setting<Boolean> fakeFallOnlyOnGround;
    public final Setting<Boolean> swingHand;
    public final Setting<Boolean> chatFlush;
    public final Setting<Double> maxRange;
    public final Setting<MaceKill.TargetListMode> targetListMode;
    public final Setting<java.util.Set<EntityType<?>>> targetEntities;
    public final Setting<Boolean> ignoreFriends;
    public final Setting<Boolean> stopOnTarget;
    public final Setting<Double> stopDistance;
    public final Setting<Double> aboveHeight;
    public final Setting<Double> velocitySpeed;
    public final Setting<Double> upwardSpeed;
    public final Setting<Double> downwardSpeed;
    public final Setting<Double> nearTargetThreshold;
    public final Setting<Boolean> useOffset;
    public final Setting<Double> offsetHorizontal;
    public final Setting<Double> offsetY;

    public MaceKillConfig(SettingGroup general, SettingGroup targeting, SettingGroup velocity,
                          SettingGroup safety) {
        this.mode = general.add(
            new EnumSetting.Builder<MaceKill.Mode>()
                .name("mode")
                .description("Slam physically launches you above the target; FakeFall spoofs fall distance with position packets.")
                .defaultValue(MaceKill.Mode.Slam)
                .build()
        );

        this.fakeFallHeight = general.add(
            new IntSetting.Builder()
                .name("fake-fall-height")
                .description("Fall height to spoof when attacking with a mace.")
                .defaultValue(22)
                .range(1, 170)
                .sliderMax(170)
                .visible(() -> this.mode.get() == MaceKill.Mode.FakeFall)
                .build()
        );

        this.fakeFallOnlyOnGround = general.add(
            new BoolSetting.Builder()
                .name("fake-fall-only-on-ground")
                .description("Only spoof fall height while standing on the ground.")
                .defaultValue(true)
                .visible(() -> this.mode.get() == MaceKill.Mode.FakeFall)
                .build()
        );

        this.swingHand = general.add(
            new BoolSetting.Builder().name("swing-hand").description("Send swing animation alongside the attack.").defaultValue(true).build()
        );

        this.chatFlush = general.add(
            new BoolSetting.Builder().name("chat-feedback").description("Log attack distance in chat on impact.").defaultValue(false).build()
        );

        this.maxRange = targeting.add(
            new DoubleSetting.Builder()
                .name("max-range")
                .description("Maximum targeting range in blocks.")
                .defaultValue(256.0)
                .min(0.0)
                .sliderMax(512.0)
                .build()
        );

        this.targetListMode = targeting.add(
            new EnumSetting.Builder<MaceKill.TargetListMode>()
                .name("target-list-mode")
                .description("Whitelist = only target these. Blacklist = never target these.")
                .defaultValue(MaceKill.TargetListMode.Blacklist)
                .build()
        );

        this.targetEntities = targeting.add(
            new meteordevelopment.meteorclient.settings.EntityTypeListSetting.Builder()
                .name("target-entities")
                .description("Entities to whitelist or blacklist.")
                .onlyAttackable()
                .build()
        );

        this.ignoreFriends = targeting.add(
            new BoolSetting.Builder().name("ignore-friends").description("Don't attack Meteor friends.").defaultValue(true).build()
        );

        this.stopOnTarget = targeting.add(
            new BoolSetting.Builder().name("stop-on-target").description("Kill velocity when you reach the target.").defaultValue(true)
                .build()
        );

        this.stopDistance = targeting.add(
            new DoubleSetting.Builder()
                .name("stop-distance")
                .description("Hitbox inflation for stop check.")
                .defaultValue(2.0)
                .min(0.0)
                .sliderMax(10.0)
                .visible(this.stopOnTarget::get)
                .build()
        );

        this.aboveHeight = velocity.add(
            new DoubleSetting.Builder()
                .name("above-height")
                .description("How many blocks above the target to launch before slamming down.")
                .defaultValue(22.0)
                .min(5.0)
                .sliderMax(100.0)
                .build()
        );

        this.velocitySpeed = velocity.add(
            new DoubleSetting.Builder()
                .name("velocity-speed")
                .description("Multiplier for all velocity.")
                .defaultValue(1.0)
                .min(0.25)
                .sliderMax(5.0)
                .build()
        );

        this.upwardSpeed = velocity.add(
            new DoubleSetting.Builder()
                .name("upward-speed")
                .description("Base upward velocity during launch phase.")
                .defaultValue(2.5)
                .min(0.5)
                .sliderMax(10.0)
                .build()
        );

        this.downwardSpeed = velocity.add(
            new DoubleSetting.Builder()
                .name("downward-speed")
                .description("Base downward velocity during slam phase.")
                .defaultValue(5.0)
                .min(1.0)
                .sliderMax(15.0)
                .build()
        );

        this.nearTargetThreshold = velocity.add(
            new DoubleSetting.Builder()
                .name("near-threshold")
                .description("Distance from above-target position at which the slam phase begins.")
                .defaultValue(3.0)
                .min(1.0)
                .sliderMax(15.0)
                .build()
        );

        this.useOffset = safety.add(
            new BoolSetting.Builder().name("safe-offset").description("Apply a small safe offset after attacking to prevent fall damage.")
                .defaultValue(true).build()
        );

        this.offsetHorizontal = safety.add(
            new DoubleSetting.Builder()
                .name("horizontal-offset")
                .description("Horizontal nudge after attacking.")
                .defaultValue(0.05)
                .min(0.0)
                .sliderMax(0.99)
                .visible(this.useOffset::get)
                .build()
        );

        this.offsetY = safety.add(
            new DoubleSetting.Builder()
                .name("y-offset")
                .description("Vertical nudge after attacking.")
                .defaultValue(0.01)
                .min(0.0)
                .sliderMax(0.99)
                .visible(this.useOffset::get)
                .build()
        );
    }
}