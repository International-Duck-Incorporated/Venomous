package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.EntityHitResult;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.HitSelectRules;
import net.venomous.meteoraddon.utils.McCompat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Filters unnecessary clicks: a hit inside the target's invulnerability window is voided by the
 * server anyway, so the click is not sent, sparing the weapon cooldown and the feel of the
 * exchange while the frames it would use tick by. Ported from Raven B4's {@code HitSelect}, with
 * its decision living in {@link HitSelectRules}; the click filter sits at the packet level, where
 * the attack arrives as a {@code ServerboundAttackPacket} - a rejected click is cancelled there and
 * vanilla's separate swing packet still leaves, so the swing plays exactly as Raven's "fake swing"
 * option made it play, without the option.
 */
public class HitSelect extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<HitSelect.Mode> mode = this.sgGeneral.add(
        new EnumSetting.Builder<HitSelect.Mode>().name("mode")
            .description("Burst filters the frames after your own hit. Criticals additionally holds ground clicks that cannot become criticals.")
            .defaultValue(HitSelect.Mode.Burst)
            .build()
    );
    private final Setting<Double> pauseDuration = this.sgGeneral.add(
        new DoubleSetting.Builder().name("pause-duration")
            .description("How long each block reason holds, in milliseconds; 0 blocks only on the reason's own tick.")
            .defaultValue(500.0)
            .range(0.0, 500.0)
            .sliderMax(500.0)
            .build()
    );
    private final Setting<Double> waitForFirstHit = this.sgGeneral.add(
        new DoubleSetting.Builder().name("wait-for-first-hit")
            .description("How long to wait for the exchange's first hit before filtering, in milliseconds.")
            .defaultValue(0.0)
            .range(0.0, 500.0)
            .sliderMax(500.0)
            .build()
    );
    private final Setting<Boolean> weaponOnly = this.sgGeneral.add(
        new BoolSetting.Builder().name("weapons-only")
            .description("Only filter clicks while holding a melee weapon.")
            .defaultValue(false)
            .build()
    );
    private final Setting<Boolean> ignoreTeammates = this.sgGeneral.add(
        new BoolSetting.Builder().name("ignore-teammates")
            .description("Never filter clicks on allied players.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> disableDuringKnockback = this.sgGeneral.add(
        new BoolSetting.Builder().name("disable-during-knockback")
            .description("Let every click through while taking or landing knockback.")
            .defaultValue(false)
            .build()
    );
    private final Setting<Boolean> onlyWhileDamaged = this.sgGeneral.add(
        new BoolSetting.Builder().name("only-while-damaged")
            .description("In Criticals mode, only filter from exchanges you have already been hurt in.")
            .defaultValue(false)
            .build()
    );
    private final Setting<Boolean> useServerAttackTime = this.sgGeneral.add(
        new BoolSetting.Builder().name("use-server-attack-time")
            .description("Gate on the hurt window the server showed, rather than predicting it from your own hit.")
            .defaultValue(false)
            .build()
    );
    private final Setting<Double> inCombatCancelRate = this.sgGeneral.add(
        new DoubleSetting.Builder().name("in-combat-cancel-rate")
            .description("Percent of in-combat clicks on blocked reasons that are actually filtered.")
            .defaultValue(100.0)
            .range(0.0, 100.0)
            .sliderMax(100.0)
            .build()
    );
    private final Setting<Double> missedSwingsCancelRate = this.sgGeneral.add(
        new DoubleSetting.Builder().name("missed-swings-cancel-rate")
            .description("Percent of clicks that are not players within reach, held back as well.")
            .defaultValue(0.0)
            .range(0.0, 100.0)
            .sliderMax(100.0)
            .build()
    );
    private final Setting<Boolean> itemBlacklistToggle = this.sgGeneral.add(
        new BoolSetting.Builder().name("filter-blacklisted-items")
            .description("Never filter clicks while holding a blacklisted item.")
            .defaultValue(false)
            .build()
    );
    private final Setting<java.util.List<net.minecraft.world.item.Item>> itemBlacklist = this.sgGeneral.add(
        new ItemListSetting.Builder().name("item-blacklist")
            .description("Items whose clicks pass unfiltered.")
            .build()
    );

    /** The three-block reach Raven's target math and its copy both enforce, squared. */
    private static final double HIT_RANGE_SQ = 3.0 * 3.0;

    private Entity currentTarget;
    private final Map<Integer, HitSelectRules.TargetState> targetStates = new HashMap<>();

    private int lastSelfHurtTime;
    private boolean takingKnockback;
    private boolean waitFirstTracking;
    private int waitFirstStartTick = -1;
    private boolean waitFirstUnlocked;

    private int tickCounter;

    public HitSelect() {
        super(VenomousAddon.CATEGORY, "hit-select", "Filters clicks the server would ignore - stops you clicking into an opponent's invulnerability frames.");
    }

    public void onActivate() {
        this.resetAllState();
    }

    @Override
    public void onDeactivate() {
        this.resetAllState();
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null || this.mc.level == null || !this.mc.player.isAlive()) {
            this.resetAllState();
            return;
        }

        this.tickCounter++;
        int currentTick = this.tickCounter;
        this.pruneTargetStates();

        LivingEntity nextTarget = this.findTarget();
        this.updateCurrentTarget(nextTarget, currentTick);

        int selfHurtTime = this.mc.player.hurtTime;
        boolean hurtAgain = selfHurtTime > this.lastSelfHurtTime;

        if (hurtAgain) {
            if (this.waitFirstTracking && !this.waitFirstUnlocked) {
                this.waitFirstUnlocked = true;
            }

            if (!this.takingKnockback) {
                this.takingKnockback = true;
            }

            if (this.currentTarget instanceof LivingEntity living) {
                this.targetState(living).firstSelfHitSeen = true;
            }
        }

        if (this.takingKnockback && this.mc.player.onGround() && !hurtAgain) {
            this.takingKnockback = false;
        }

        this.lastSelfHurtTime = selfHurtTime;
        this.updateTargetDamage(currentTick);
    }

    @EventHandler
    private void onPacketSend(Send event) {
        if (!(event.packet instanceof net.minecraft.network.protocol.game.ServerboundAttackPacket packet)) {
            return;
        }

        if (!this.canProcessClicks()
            || this.weaponOnly.get() && !this.holdingWeapon()
            || this.itemBlacklistToggle.get() && this.itemBlacklist.get().contains(this.mc.player.getMainHandItem().getItem())) {
            return;
        }

        int currentTick = this.tickCounter;

        Entity clicked = this.mc.level.getEntity(packet.entityId());
        LivingEntity target = this.asValidTarget(clicked);
        if (target == null) {
            if (this.roll(this.missedSwingsCancelRate.get())) {
                event.cancel();
            }
            return;
        }

        this.updateCurrentTarget(target, currentTick);
        HitSelectRules.TargetState state = this.targetState(target);
        int blockMask = this.validHitBlockMask(state, currentTick);

        boolean shouldBlock = (blockMask & HitSelectRules.BLOCK_WAIT_FIRST) != 0
            || (blockMask & HitSelectRules.BLOCK_PREDICTED_BURST) != 0
            || HitSelectRules.applyPauseDuration(state, blockMask & ~HitSelectRules.BLOCK_PREDICTED_BURST, currentTick, this.pauseDuration.get());

        if (shouldBlock && this.roll(this.inCombatCancelRate.get())) {
            // vanilla still sends its own swing packet for the cancelled attack, so the swing plays
            event.cancel();
            return;
        }

        this.recordPassedValidHit(target, state, currentTick);
    }

    private boolean canProcessClicks() {
        return this.mc.player != null && this.mc.level != null && this.mc.player.isAlive();
    }

    /**
     * The opponent being filtered for: the crosshair's entity first, else the closest valid player,
     * the same precedence Raven takes.
     */
    private LivingEntity findTarget() {
        LivingEntity mouseOver = this.mc.hitResult instanceof EntityHitResult hit
            ? this.asValidTarget(hit.getEntity())
            : null;
        if (mouseOver != null) {
            return mouseOver;
        }

        LivingEntity closest = null;
        double closestDistanceSq = Double.MAX_VALUE;
        net.minecraft.world.phys.Vec3 eye = this.mc.player.getEyePosition();

        for (Entity entity : this.mc.level.entitiesForRendering()) {
            LivingEntity candidate = this.asValidTarget(entity);
            if (candidate == null) {
                continue;
            }

            net.minecraft.world.phys.AABB box = candidate.getBoundingBox();
            double distanceSq = HitSelectRules.eyeToBoxSqr(
                eye.x, eye.y, eye.z, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            if (distanceSq < closestDistanceSq) {
                closestDistanceSq = distanceSq;
                closest = candidate;
            }
        }

        return closest != null && closestDistanceSq <= HIT_RANGE_SQ ? closest : null;
    }

    /**
     * A click's resolved target: a living player, not the caller, not dead, within three blocks of
     * the eye, and - in the teammates option - not allied to the caller. Raven also excluded its
     * friends list and bot list; this port has neither equivalent.
     */
    private LivingEntity asValidTarget(Entity entity) {
        if (!(entity instanceof net.minecraft.world.entity.player.Player player)
            || player == this.mc.player
            || !player.isAlive()) {
            return null;
        }

        if (this.ignoreTeammates.get() && player.isAlliedTo(this.mc.player)) {
            return null;
        }

        net.minecraft.world.phys.Vec3 eye = this.mc.player.getEyePosition();
        net.minecraft.world.phys.AABB box = player.getBoundingBox();
        if (!HitSelectRules.withinHitRange(eye.x, eye.y, eye.z, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, HIT_RANGE_SQ)) {
            return null;
        }

        return player;
    }

    private void updateCurrentTarget(LivingEntity nextTarget, int currentTick) {
        if (this.sameTarget(nextTarget)) {
            if (nextTarget != null) {
                this.currentTarget = nextTarget;
                this.targetState(nextTarget);
            }
            return;
        }

        this.currentTarget = nextTarget;

        if (nextTarget == null) {
            this.waitFirstTracking = false;
            this.waitFirstStartTick = -1;
            this.waitFirstUnlocked = false;
        } else if (!this.waitFirstTracking) {
            this.waitFirstTracking = true;
            this.waitFirstStartTick = currentTick;
            this.waitFirstUnlocked = false;
        }

        if (nextTarget != null) {
            this.targetState(nextTarget);
        }
    }

    private boolean sameTarget(LivingEntity nextTarget) {
        if (this.currentTarget == null || nextTarget == null) {
            return this.currentTarget == nextTarget;
        }

        return this.currentTarget.getId() == nextTarget.getId();
    }

    private HitSelectRules.TargetState targetState(LivingEntity entity) {
        HitSelectRules.TargetState state = this.targetStates.get(entity.getId());
        if (state == null) {
            state = new HitSelectRules.TargetState();
            if (this.useServerAttackTime.get()) {
                state.lastObservedTargetHurtTime = entity.hurtTime;
            }
            this.targetStates.put(entity.getId(), state);
        }
        return state;
    }

    private void pruneTargetStates() {
        if (this.mc.level == null) {
            this.targetStates.clear();
            return;
        }

        Iterator<Map.Entry<Integer, HitSelectRules.TargetState>> iterator = this.targetStates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, HitSelectRules.TargetState> entry = iterator.next();
            Entity entity = this.mc.level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                iterator.remove();
            }
        }
    }

    private void updateTargetDamage(int currentTick) {
        if (!(this.currentTarget instanceof LivingEntity living) || !this.useServerAttackTime.get()) {
            return;
        }

        HitSelectRules.TargetState state = this.targetState(living);
        int targetHurtTime = living.hurtTime;

        if (state.pendingServerConfirmationTick >= 0 && currentTick - state.pendingServerConfirmationTick > HitSelectRules.SERVER_CONFIRM_TIMEOUT_TICKS) {
            state.pendingServerConfirmationTick = -1;
        }

        if (state.pendingServerConfirmationTick >= 0 && targetHurtTime > state.lastObservedTargetHurtTime) {
            state.pendingServerConfirmationTick = -1;
            state.lastConfirmedTargetDamageTick = currentTick;
            state.rawBlockMask = HitSelectRules.BLOCK_SERVER_COOLDOWN;
            state.rawBlockStartTick = currentTick;
        }

        state.lastObservedTargetHurtTime = targetHurtTime;
    }

    private int validHitBlockMask(HitSelectRules.TargetState state, int currentTick) {
        if (this.currentTarget == null) {
            return HitSelectRules.NO_BLOCK;
        }

        if (this.disableDuringKnockback.get() && this.isTakingKnockback()) {
            return HitSelectRules.NO_BLOCK;
        }

        int blockMask = HitSelectRules.NO_BLOCK;

        if (HitSelectRules.waitingForFirstHit(this.waitForFirstHit.get(), this.waitFirstTracking, this.waitFirstUnlocked, this.waitFirstStartTick, currentTick)) {
            blockMask |= HitSelectRules.BLOCK_WAIT_FIRST;
        }

        blockMask |= HitSelectRules.burstBlockMask(state, currentTick, this.useServerAttackTime.get(), this.pauseDuration.get());

        boolean selfOnGround = this.mc.player.onGround();
        boolean canCritical = HitSelectRules.canCriticalHit(
            this.mc.player.fallDistance,
            selfOnGround,
            this.mc.player.onClimbable(),
            this.mc.player.isInWater(),
            this.mc.player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS),
            this.mc.player.isPassenger());

        if (HitSelectRules.criticalsBlocked(
            this.mode.get() == HitSelect.Mode.Criticals,
            selfOnGround,
            this.onlyWhileDamaged.get(),
            state.firstSelfHitSeen,
            this.disableDuringKnockback.get() && this.isTakingKnockback(),
            canCritical)) {
            blockMask |= HitSelectRules.BLOCK_CRITICALS;
        }

        return blockMask;
    }

    private boolean isTakingKnockback() {
        return this.takingKnockback || this.mc.player.hurtTime > 0;
    }

    private void recordPassedValidHit(LivingEntity target, HitSelectRules.TargetState state, int currentTick) {
        this.updateCurrentTarget(target, currentTick);

        if (this.useServerAttackTime.get()) {
            state.pendingServerConfirmationTick = currentTick;
            state.lastConfirmedTargetDamageTick = -1;
            return;
        }

        if (!HitSelectRules.isPredictedBurstWindowActive(state, currentTick)) {
            HitSelectRules.startPredictedBurstWindow(state, currentTick);
        }
    }

    private boolean holdingWeapon() {
        net.minecraft.world.item.ItemStack held = this.mc.player.getMainHandItem();
        // McCompat is the repo's canonical sword/axe/mace check: on 26.2 swords carry a weapon
        // data component instead of a SwordItem class (the component trio lives in
        // net.minecraft.world.item.component), so there is no class to instanceof.
        return McCompat.isWeapon(held.getItem())
            || held.getItem() instanceof TridentItem;
    }

    private boolean roll(double chancePercent) {
        if (chancePercent <= 0.0) {
            return false;
        }

        if (chancePercent >= 100.0) {
            return true;
        }

        return Math.random() * 100.0 < chancePercent;
    }

    private void resetAllState() {
        this.currentTarget = null;
        this.targetStates.clear();
        this.lastSelfHurtTime = 0;
        this.takingKnockback = false;
        this.waitFirstTracking = false;
        this.waitFirstStartTick = -1;
        this.waitFirstUnlocked = false;
    }

    public String getInfoString() {
        return ((HitSelect.Mode)this.mode.get()).name();
    }

    public enum Mode {
        Burst,
        Criticals;
    }
}
