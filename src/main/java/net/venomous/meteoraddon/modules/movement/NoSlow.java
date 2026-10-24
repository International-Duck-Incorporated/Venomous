package net.venomous.meteoraddon.modules.movement;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.McCompat;
import net.venomous.meteoraddon.utils.NoSlowRules;

/**
 * Decides for itself how much an in-use item slows you down.
 *
 * <p>26.2 keeps an item's slowdown on the item - the {@code USE_EFFECTS} data component, read
 * through one seam the client consults every tick it is eating, drinking, drawing or blocking.
 * The mixin at that seam hands the decision to {@link NoSlowRules}: the slider sets how much of
 * vanilla's 80% slowdown remains, and the filters say which items the override never touches.</p>
 *
 * <p>The cost is stated plainly, the same way the desync family's is: a server that simulates
 * movement predicts vanilla's 0.2, and a client moving faster than its own prediction is a
 * Simulation flag. At the default 80% the module is exact vanilla parity and changes nothing;
 * the useful range depends on the server, not on this module.</p>
 */
public class NoSlow extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> slow = this.sgGeneral.add(
        new DoubleSetting.Builder()
            .name("slow-percent")
            .description("How much of vanilla's using-item slowdown remains. 80 is exact vanilla parity; lower moves faster.")
            .defaultValue(80.0)
            .range(0.0, NoSlowRules.MAX_SLOW_PERCENT)
            .sliderMax(NoSlowRules.MAX_SLOW_PERCENT)
            .build()
    );

    private final Setting<Boolean> swordOnly = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("sword-only")
            .description("Only override the slowdown while a sword is the item in use.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> disableBow = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("disable-bow")
            .description("Never override the slowdown while drawing a bow.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> disablePotions = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("disable-potions")
            .description("Never override the slowdown while drinking a potion. Thrown potions stay overridable, as in the original.")
            .defaultValue(false)
            .build()
    );

    public NoSlow() {
        super(VenomousAddon.CATEGORY, "no-slow", "Overrides how much items slow you down while using them.");
    }

    /**
     * The multiplier to hand the client this tick, or -1 to leave vanilla's alone.
     *
     * <p>Called from the mixin on the seam every use-slowdown consumer passes through; the
     * item asked about is the one <em>in use</em>, which is what vanilla reads, not merely
     * whatever is in the hand.</p>
     */
    public float overrideMultiplier(Player player) {
        ItemStack useItem = player.getUseItem();
        if (useItem.isEmpty()) return -1.0F;

        return NoSlowRules.multiplier(
            player.isUsingItem(),
            McCompat.isSword(useItem.getItem()),
            useItem.getItem() instanceof BowItem,
            useItem.is(Items.POTION),
            useItem.is(Items.SPLASH_POTION) || useItem.is(Items.LINGERING_POTION),
            this.swordOnly.get(),
            this.disableBow.get(),
            this.disablePotions.get(),
            this.slow.get()
        );
    }

    @Override
    public String getInfoString() {
        return (int) (double) this.slow.get() + "%";
    }
}
