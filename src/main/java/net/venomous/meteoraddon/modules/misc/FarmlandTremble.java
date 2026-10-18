package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.FarmlandBlock;
import net.venomous.meteoraddon.VenomousAddon;

/** Tramples only the farmland the player actually stands on, using normal server-authoritative movement. */
public final class FarmlandTremble extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Integer> jumpDelay = this.sgGeneral.add(new IntSetting.Builder()
        .name("jump-delay")
        .description("Ticks to wait between real jump attempts while standing on farmland.")
        .defaultValue(8)
        .range(1, 40)
        .sliderRange(1, 40)
        .build());

    private int cooldown;

    public FarmlandTremble() {
        super(VenomousAddon.CATEGORY, "farmland-tremble",
            "Repeatedly jumps on the farmland under you so the server performs normal trampling without client-world desync.");
    }

    @Override
    public void onActivate() {
        this.cooldown = 0;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.level == null || this.mc.player == null) return;
        if (this.cooldown > 0) {
            this.cooldown--;
            return;
        }
        if (!this.mc.player.onGround() || this.mc.player.isPassenger() || this.mc.player.isInWater()) return;

        BlockPos below = this.mc.player.blockPosition().below();
        if (!(this.mc.level.getBlockState(below).getBlock() instanceof FarmlandBlock)) return;

        // Never mutate the client world. A genuine jump/fall lets the server apply vanilla trampling
        // and broadcast the authoritative dirt state back to every client.
        this.mc.player.jumpFromGround();
        this.cooldown = this.jumpDelay.get();
    }
}
