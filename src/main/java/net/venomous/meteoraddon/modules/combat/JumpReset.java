package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.JumpResetRules;

import java.util.Random;

/**
 * Jump resetting: pressing the jump key on the tick a hit lands, so the knockback multiplies into
 * the jump and travels a fraction of its range. Ported from Raven B4's {@code JumpReset} module
 * with the same gate list (see {@link JumpResetRules} for the decision); the timings are adapted
 * to Meteor's tick events.
 */
public class JumpReset extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> chance = this.sgGeneral.add(
        new IntSetting.Builder().name("chance")
            .description("Percent of hits to jump-reset.")
            .defaultValue(80)
            .range(0, 100)
            .sliderMax(100)
            .build()
    );
    private final Setting<Boolean> requireMouseDown = this.sgGeneral.add(
        new BoolSetting.Builder().name("require-mouse-down")
            .description("Only reset while the attack key is already held.")
            .defaultValue(false)
            .build()
    );
    private final Setting<Boolean> requireMovingForward = this.sgGeneral.add(
        new BoolSetting.Builder().name("require-moving-forward")
            .description("Only reset while walking forward, into the fight.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> requireAim = this.sgGeneral.add(
        new BoolSetting.Builder().name("require-aim")
            .description("Only reset when the crosshair is over another player.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Integer> motionArc = this.sgGeneral.add(
        new IntSetting.Builder().name("motion-arc")
            .description("The movement-direction arc, in full degrees, knockback must line up with. 330 is the Raven original - it rejects only a movement within 15 degrees of directly backwards - lower it to reject sideways and rear hits too.")
            .defaultValue(330)
            .range(60, 360)
            .sliderMax(360)
            .build()
    );

    /**
     * Falling further than this and landing arms the ignore latch: the hurt time the landing deals
     * is the fall's own, and the next growth of hurt time must not be read as someone's hit.
     */
    private static final double FALL_IGNORE_DISTANCE = 3.0;

    private final Random random = new Random();

    private int lastHurtTime;
    private double lastFallDistance;
    private boolean ignoreNext;

    /**
     * Whether the jump key is being held only because this module pressed it. The key mapping is
     * shared state with the game's own events, so a release this module did not cause would clobber
     * a jump the player is making; only a press that came from here is ever released.
     */
    private boolean jumpPressedByUs;

    public JumpReset() {
        super(VenomousAddon.CATEGORY, "jump-reset", "Presses jump on the tick a hit lands, cutting the knockback short - a jump reset, ported from Raven B4.");
    }

    public void onActivate() {
        this.lastHurtTime = 0;
        this.lastFallDistance = 0.0;
        this.ignoreNext = false;
        this.jumpPressedByUs = false;
    }

    @Override
    public void onDeactivate() {
        this.releaseForcedJump();
        this.lastHurtTime = 0;
        this.lastFallDistance = 0.0;
        this.ignoreNext = false;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null) {
            return;
        }

        // A press made for an earlier hit has been read by that tick's input; clear it before this
        // tick's decision so the tap lasts exactly one input window, never two.
        this.releaseForcedJump();

        int hurtTime = this.mc.player.hurtTime;
        boolean onGround = this.mc.player.onGround();

        if (onGround && !this.mc.player.getAbilities().flying && this.lastFallDistance > FALL_IGNORE_DISTANCE) {
            this.ignoreNext = true;
        }

        if (hurtTime > this.lastHurtTime) {
            if (JumpResetRules.shouldJump(
                true,
                this.ignoreNext,
                onGround,
                this.mc.player.isOnFire(),
                this.hasBadEffect(),
                this.movementLinesUpWithFacing(),
                this.crosshairOnPlayer(),
                this.requireAim.get(),
                this.mc.options.keyAttack.isDown(),
                this.requireMouseDown.get(),
                this.mc.options.keyUp.isDown(),
                this.requireMovingForward.get(),
                this.rollPassed()
            )) {
                // The player holding jump already resets nothing new - pressing into their press
                // would only leave a stuck-down key for this module to release over their hold.
                if (!this.mc.options.keyJump.isDown()) {
                    this.mc.options.keyJump.setDown(true);
                    this.jumpPressedByUs = true;
                }
            }

            // The latch is consumed on the hit tick itself, in the original too, whether or not
            // the reset fired - a landing pre-arms one suppression and no more.
            this.ignoreNext = false;
        }

        this.lastHurtTime = hurtTime;
        this.lastFallDistance = this.mc.player.fallDistance;
    }

    /**
     * Whether the current movement direction sits inside the accepted arc around the facing.
     *
     * <p>A player standing still - most of the hurt tick at the start of a combo, before the
     * knockback is applied - has no direction to speak of, so with no movement the gate is waived
     * rather than read against yaw zero, which would reject whatever the player happens to face.</p>
     */
    private boolean movementLinesUpWithFacing() {
        double x = this.mc.player.getDeltaMovement().x;
        double z = this.mc.player.getDeltaMovement().z;
        if (x * x + z * z < 1.0e-8) {
            return true;
        }

        return JumpResetRules.withinArc(
            this.mc.player.getYRot(),
            JumpResetRules.motionYawOf(x, z),
            (float) this.motionArc.get()
        );
    }

    private boolean crosshairOnPlayer() {
        return this.mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player;
    }

    private boolean hasBadEffect() {
        return this.mc.player.hasEffect(MobEffects.JUMP_BOOST)
            || this.mc.player.hasEffect(MobEffects.POISON)
            || this.mc.player.hasEffect(MobEffects.WITHER);
    }

    private boolean rollPassed() {
        int chance = this.chance.get();
        return chance >= 100 || this.random.nextInt(100) < chance;
    }

    private void releaseForcedJump() {
        if (this.jumpPressedByUs) {
            this.jumpPressedByUs = false;
            this.mc.options.keyJump.setDown(false);
        }
    }

    public String getInfoString() {
        int chance = this.chance.get();
        return chance >= 100 ? "" : chance + "%";
    }
}
