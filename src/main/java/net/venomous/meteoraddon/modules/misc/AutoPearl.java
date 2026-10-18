package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.venomous.meteoraddon.VenomousAddon;

/**
 * Throws ender pearls, either automatically at low health or instantly on demand.
 *
 * <p>Absorbs the former {@code key-pearl} module, which was a 38-line duplicate of the pearl-throwing
 * half of this module — it searched the hotbar, swapped, used the item and swapped back, exactly as
 * {@link #throwPearl()} does. It is now the {@link Mode#Instant} mode.
 */
public class AutoPearl extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Mode> mode = this.sgGeneral.add(
        new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("LowHealth throws when your health drops; Instant throws once as soon as you enable the module.")
            .defaultValue(Mode.LowHealth)
            .build()
    );

    private final Setting<Integer> health = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("health")
            .description("Health to throw a pearl at.")
            .defaultValue(10)
            .range(0, 20)
            .sliderMax(20)
            .visible(() -> this.mode.get() == Mode.LowHealth)
            .build()
    );

    private final Setting<Boolean> onGround = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("on-ground")
            .description("Only throw when on the ground.")
            .defaultValue(true)
            .visible(() -> this.mode.get() == Mode.LowHealth)
            .build()
    );

    private final Setting<Boolean> swing = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("swing")
            .description("Send the swing animation when throwing.")
            .defaultValue(true)
            .build()
    );

    private int cooldown = 0;

    public AutoPearl() {
        super(VenomousAddon.CATEGORY, "autopearl", "Throws ender pearls automatically at low health, or instantly on demand.");
    }

    @Override
    public void onActivate() {
        this.cooldown = 0;
        if (this.mode.get() != Mode.Instant) {
            return;
        }

        // Deferred rather than toggling inline: onActivate is still unwinding here, and calling
        // toggle() from inside it re-enters Meteor's activation logic mid-activation. This is
        // the trap that made v-coord-snapper double-fire.
        if (this.mc.player == null) {
            this.mc.execute(() -> {
                if (this.isActive()) this.toggle();
            });
            return;
        }

        if (!this.throwPearl()) {
            this.error("No ender pearls found.");
        }

        // Instant mode is a one-shot action; switch back off once it has fired.
        this.mc.execute(() -> {
            if (this.isActive()) this.toggle();
        });
    }

    @Override
    public void onDeactivate() {
        this.cooldown = 0;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.cooldown > 0) {
            this.cooldown--;
        }

        if (this.mode.get() != Mode.LowHealth || this.mc.player == null) {
            return;
        }

        if (this.cooldown <= 0 && this.mc.player.getHealth() <= this.health.get() && (!this.onGround.get() || this.mc.player.onGround())) {
            if (this.throwPearl()) {
                this.cooldown = 20;
            }
        }
    }

    /** @return true if a pearl was found and thrown. */
    private boolean throwPearl() {
        if (this.mc.player == null || this.mc.gameMode == null) {
            return false;
        }

        FindItemResult result = InvUtils.findInHotbar(new Item[]{Items.ENDER_PEARL});
        if (!result.found()) {
            return false;
        }

        InvUtils.swap(result.slot(), true);
        this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
        if (this.swing.get()) {
            this.mc.player.swing(InteractionHand.MAIN_HAND);
        }
        InvUtils.swapBack();
        return true;
    }

    @Override
    public String getInfoString() {
        return this.mode.get().name();
    }

    public enum Mode {
        /** Throw automatically when health drops below the threshold. */
        LowHealth,
        /** Throw once immediately on enable (former key-pearl). */
        Instant
    }
}
