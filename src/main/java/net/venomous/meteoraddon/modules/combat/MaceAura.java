package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.MaceItem;
import net.venomous.meteoraddon.VenomousAddon;

public class MaceAura extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range").description("Attack range.").defaultValue(4.5).range(1.0, 6.0).sliderMax(6.0).build());

    private final Setting<Boolean> onlyMace = sgGeneral.add(new BoolSetting.Builder()
        .name("only-mace").description("Only when holding mace.").defaultValue(true).build());

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay").description("Ticks between attacks.").defaultValue(10).range(1, 20).sliderMax(20).build());

    private int ticksSinceAttack = 0;

    public MaceAura() {
        super(VenomousAddon.CATEGORY, "mace-aura", "Basic mace aura targeting.");
    }

    @Override
    public void onActivate() {
        this.ticksSinceAttack = 0;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null || this.mc.level == null || this.mc.getConnection() == null) return;
        if (this.onlyMace.get() && !(this.mc.player.getMainHandItem().getItem() instanceof MaceItem)) return;

        this.ticksSinceAttack++;
        if (this.ticksSinceAttack < this.delay.get()) return;
        this.ticksSinceAttack = 0;

        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        double maxRangeSq = this.range.get() * this.range.get();

        for (LivingEntity e : this.mc.level.getEntitiesOfClass(LivingEntity.class, this.mc.player.getBoundingBox().inflate(this.range.get()))) {
            if (e == this.mc.player || e.isRemoved() || e.isDeadOrDying()) continue;
            double dSq = e.distanceToSqr(this.mc.player);
            if (dSq < bestDistSq && dSq <= maxRangeSq) {
                bestDistSq = dSq;
                best = e;
            }
        }

        if (best != null && this.mc.gameMode != null) {
            this.mc.gameMode.attack(this.mc.player, best);
        }
    }
}
