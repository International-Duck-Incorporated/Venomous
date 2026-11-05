package net.venomous.meteoraddon.utils.combat.config;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.minecraft.world.entity.EntityType;
import net.venomous.meteoraddon.modules.combat.TriggerBot;

/**
 * TriggerBot's settings, registered on the module's own groups.
 */
public final class TriggerBotConfig {

    public final Setting<Double> radius;
    public final Setting<Boolean> triggerOnClick;
    public final Setting<Integer> attackDelayMin;
    public final Setting<Integer> attackDelayMax;
    public final Setting<TriggerBot.TargetEntities> targetEntities;
    public final Setting<Boolean> targetFriends;
    public final Setting<Boolean> ignoreDead;
    public final Setting<Boolean> ignoreInvisible;
    public final Setting<Boolean> ignoreSleeping;
    public final Setting<Boolean> ignoreNPCs;

    public TriggerBotConfig(SettingGroup general, SettingGroup behavior, SettingGroup targeting) {
        this.radius = general.add(
            new DoubleSetting.Builder()
                .name("radius")
                .description("Radius that TriggerBot will trigger.")
                .defaultValue(3.0)
                .min(0.1)
                .max(10.0)
                .sliderMax(10.0)
                .build()
        );

        this.triggerOnClick = behavior.add(
            new BoolSetting.Builder()
                .name("trigger-on-click")
                .description("Only attack while the attack key is held down.")
                .defaultValue(false)
                .build()
        );

        this.attackDelayMin = behavior.add(
            new IntSetting.Builder()
                .name("attack-delay-min")
                .description("Minimum delay between attacks in milliseconds.")
                .defaultValue(50)
                .min(0)
                .max(1000)
                .sliderMax(1000)
                .build()
        );

        this.attackDelayMax = behavior.add(
            new IntSetting.Builder()
                .name("attack-delay-max")
                .description("Maximum delay between attacks in milliseconds.")
                .defaultValue(150)
                .min(0)
                .max(1000)
                .sliderMax(1000)
                .build()
        );

        this.targetEntities = targeting.add(
            new EnumSetting.Builder<TriggerBot.TargetEntities>()
                .name("target-entities")
                .description("Entity types that TriggerBot will target.")
                .defaultValue(TriggerBot.TargetEntities.Players)
                .build()
        );

        this.targetFriends = targeting.add(
            new BoolSetting.Builder()
                .name("target-friends")
                .description("Target friends.")
                .defaultValue(false)
                .build()
        );

        this.ignoreDead = targeting.add(
            new BoolSetting.Builder()
                .name("ignore-dead")
                .description("Skip entities that are dead or dying.")
                .defaultValue(true)
                .build()
        );

        this.ignoreInvisible = targeting.add(
            new BoolSetting.Builder()
                .name("ignore-invisible")
                .description("Skip entities that are invisible.")
                .defaultValue(true)
                .build()
        );

        this.ignoreSleeping = targeting.add(
            new BoolSetting.Builder()
                .name("ignore-sleeping")
                .description("Skip players that are sleeping.")
                .defaultValue(true)
                .build()
        );

        this.ignoreNPCs = targeting.add(
            new BoolSetting.Builder()
                .name("ignore-npcs")
                .description("Attempt to ignore NPCs based on entity UUID.")
                .defaultValue(true)
                .build()
        );
    }
}