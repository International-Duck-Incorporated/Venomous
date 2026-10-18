package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.PreRotate;
import meteordevelopment.meteorclient.settings.DoubleSetting;

public class AutoPotion extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Double> healthThreshold = this.sgGeneral
        .add(
            new DoubleSetting.Builder().name("health-threshold").description("Throw healing potion when HP percent is below this value.")
                .defaultValue(60.0)
                .min(5.0)
                .sliderMax(100.0)
                .build()
        );
    private final Setting<Boolean> instantHealth = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("instant-health")
                .description("Use Instant Health splash potions.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> regeneration = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("regeneration")
                .description("Use Regeneration splash potions when healing or prebuffing.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> fireResistance = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("fire-resistance")
                .description("Prebuff with Fire Resistance when missing.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> strength = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("strength")
                .description("Prebuff with Strength when missing.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> speed = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("speed")
                .description("Prebuff with Speed when missing.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Boolean> offhand = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("offhand")
                .description("Use splash potions from offhand if they match the current priority.")
                .defaultValue(true)
                .build()
        );
    private final Setting<AutoPotion.RotateMode> rotate = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<AutoPotion.RotateMode>()
                .name("rotate")
                .description("Rotation behavior before throwing.")
                .defaultValue(AutoPotion.RotateMode.Down)
                .build()
        );
    private final Setting<Integer> delay = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("delay")
                .description("Minimum ticks between throws.")
                .defaultValue(10)
                .min(1)
                .sliderMax(80)
                .build()
        );
    private final Setting<Boolean> smartSenses = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("smart-senses")
                .description("Only use specific buffs when relevant (e.g. fire res near lava).")
                .defaultValue(true)
                .build()
        );
    private int cooldown;

    public AutoPotion() {
        super(VenomousAddon.CATEGORY, "auto-potion", "Smart splash potion helper for healing and prebuffs.");
    }

    public void onDeactivate() {
        this.cooldown = 0;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null && this.mc.gameMode != null && this.cooldown-- <= 0) {
            Holder<MobEffect> desired = this.desiredEffect();
            if (desired != null) {
                this.throwPotion(desired);
            }
        }
    }

    private Holder<MobEffect> desiredEffect() {
        double hp = this.mc.player.getHealth() / this.mc.player.getMaxHealth() * 100.0;
        if (hp <= (Double)this.healthThreshold.get()) {
            if ((Boolean)this.instantHealth.get()) {
                return MobEffects.INSTANT_HEALTH;
            }

            if ((Boolean)this.regeneration.get() && !this.mc.player.hasEffect(MobEffects.REGENERATION)) {
                return MobEffects.REGENERATION;
            }
        }

        if (!(Boolean)this.fireResistance.get() || this.mc.player.hasEffect(MobEffects.FIRE_RESISTANCE) || (Boolean)this.smartSenses.get() && !this.isNearFire()) {
            if (!(Boolean)this.strength.get() || this.mc.player.hasEffect(MobEffects.STRENGTH) || (Boolean)this.smartSenses.get() && !this.isNearEnemy()) {
                return !this.speed.get() || this.mc.player.hasEffect(MobEffects.SPEED) || this.smartSenses.get() && !this.isNearEnemy() ? null : MobEffects.SPEED;
            } else {
                return MobEffects.STRENGTH;
            }
        } else {
            return MobEffects.FIRE_RESISTANCE;
        }
    }

    private boolean isNearFire() {
        BlockPos pos = this.mc.player.blockPosition();

        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    BlockState state = this.mc.level.getBlockState(pos.offset(x, y, z));
                    if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isNearEnemy() {
        return this.mc.level.players().stream().anyMatch(p -> p != this.mc.player && !Friends.get().isFriend(p) && this.mc.player.distanceTo(p) <= 15.0F);
    }

    private void throwPotion(Holder<MobEffect> effect) {
        InteractionHand hand = null;
        int slot = -1;
        int prev = this.mc.player.getInventory().getSelectedSlot();
        if ((Boolean)this.offhand.get() && this.matches(this.mc.player.getOffhandItem(), effect)) {
            hand = InteractionHand.OFF_HAND;
        } else {
            slot = this.findPotion(effect);
            if (slot == -1) {
                return;
            }

            hand = InteractionHand.MAIN_HAND;
        }

        InteractionHand finalHand = hand;
        int finalSlot = slot;
        Runnable action = () -> {
            if (finalSlot != -1) {
                this.mc.player.getInventory().setSelectedSlot(finalSlot);
            }

            this.mc.gameMode.useItem(this.mc.player, finalHand);
            if (finalSlot != -1) {
                this.mc.player.getInventory().setSelectedSlot(prev);
            }

            this.cooldown = (Integer)this.delay.get();
        };
        if (this.rotate.get() == AutoPotion.RotateMode.Down) {
            PreRotate.rotateThen(this, this.mc.player.getYRot(), 90.0, action);
        } else {
            action.run();
        }
    }

    private int findPotion(Holder<MobEffect> effect) {
        for (int i = 0; i < 9; i++) {
            if (this.matches(this.mc.player.getInventory().getItem(i), effect)) {
                return i;
            }
        }

        return -1;
    }

    private boolean matches(ItemStack stack, Holder<MobEffect> effect) {
        if (!stack.isEmpty() && stack.getItem() == Items.SPLASH_POTION) {
            PotionContents pc = (PotionContents)stack.get(DataComponents.POTION_CONTENTS);
            if (pc == null) {
                return false;
            }

            for (MobEffectInstance e : pc.getAllEffects()) {
                if (e.getEffect().equals(effect)) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    public enum RotateMode {
        None,
        Down;
    }
}
