package net.venomous.meteoraddon.modules.combat;

import java.util.Random;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.McCompat;
import net.venomous.meteoraddon.utils.combat.CombatContext;
import net.venomous.meteoraddon.utils.combat.CombatContext.TargetPriority;
import net.venomous.meteoraddon.utils.combat.AttackTiming;
import net.venomous.meteoraddon.utils.combat.BoatBypass;
import net.venomous.meteoraddon.utils.combat.config.KillAuraConfig;

/**
 * KillAura using shared CombatContext target selection and AttackTiming.
 * <p>Range accounting, filter logic and attack cadence now live in shared utilities; only the
 * per-ac-mode clamp and boat trick stay here.</p>
 */
public class KillAura extends Module {

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgVulcan = this.settings.createGroup("Vulcan");
    private final SettingGroup sgAnticheat = this.settings.createGroup("GCD / Anticheat");

    public enum Technique {
        STABILIZED,
        BUTTERFLY,
        NORMAL_DISTRIBUTION,
        EFFICIENT,
        SWITCH
    }

    public enum ACMode {
        Grim, Vulcan, NCP, WeakAC, None
    }

    private final KillAuraConfig config = new KillAuraConfig(sgGeneral, sgVulcan, sgAnticheat);
    private final Random random = new Random();
    private int ticksUntilAttack = 0;
    private Entity target = null;

    public KillAura() {
        super(VenomousAddon.CATEGORY, "annihilate", "KillAura bypasses for Grim, Vulcan, Polar, Matrix, NCP, and vanilla.");
    }

    @Override
    public void onActivate() {
        this.ticksUntilAttack = 0;
        this.target = null;
    }

    @Override
    public void onDeactivate() {
        if (this.mc.options != null && this.mc.options.keyUse != null && this.config.autoBlock.get()) {
            this.mc.options.keyUse.setDown(false);
        }
        this.ticksUntilAttack = 0;
        this.target = null;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null || this.mc.level == null) {
            return;
        }

        Iterable<Entity> entities = this.mc.level.entitiesForRendering();
        if (this.config.acMode.get() == KillAura.ACMode.Grim && !BoatBypass.isBoatNearby(this.mc.player, entities)) {
            BoatBypass.placeBoat(this.mc.player, this.mc);
        }

        this.target = this.findTarget();
        if (this.target == null) {
            if (this.config.autoBlock.get()) {
                this.mc.options.keyUse.setDown(false);
            }
        } else {
            if (this.config.rotate.get()) {
                this.rotateTo(this.target);
            }

            if (this.config.rotate.get()) {
                double angle = this.getAngleTo(this.target);
                double thresh = this.config.acMode.get() == KillAura.ACMode.Grim
                    ? Math.min(this.config.rotationThreshold.get(), 15.0)
                    : this.config.rotationThreshold.get();
                if (angle > thresh) {
                    if (this.config.autoBlock.get()) {
                        this.mc.options.keyUse.setDown(true);
                    }
                    return;
                }
            }

            this.ticksUntilAttack--;
            if (this.ticksUntilAttack <= 0) {
                this.doAttack();
                this.ticksUntilAttack = this.getNextAttackDelay();
            } else if (this.config.autoBlock.get()) {
                this.mc.options.keyUse.setDown(true);
            }
        }
    }

    private void doAttack() {
        if (this.mc.player != null && this.target != null && this.mc.gameMode != null) {
            float minStrength = this.config.acMode.get() != KillAura.ACMode.Grim && this.config.acMode.get() != KillAura.ACMode.NCP ? 0.9F : 1.0F;
            if (!this.config.respectCooldown.get() || !(this.mc.player.getAttackStrengthScale(0.0F) < minStrength)) {
                if (this.config.autoBlock.get()) {
                    this.mc.options.keyUse.setDown(false);
                }

                this.mc.gameMode.attack(this.mc.player, this.target);
                this.mc.player.swing(InteractionHand.MAIN_HAND);
                this.mc.player.resetAttackStrengthTicker();
            }
        }
    }

    private int getNextAttackDelay() {
        return AttackTiming.nextCpsDelay(this.random, mapTechnique(this.config.technique.get()),
            this.config.minCps.get(), this.config.maxCps.get(),
            this.config.acMode.get() == KillAura.ACMode.Vulcan,
            this.config.vulcanRandomizationMs.get(),
            this.config.vulcanSpeedRandomization.get());
    }

    private static AttackTiming.Mode mapTechnique(KillAura.Technique technique) {
        return switch (technique) {
            case STABILIZED -> AttackTiming.Mode.STABILIZED;
            case BUTTERFLY -> AttackTiming.Mode.BUTTERFLY;
            case NORMAL_DISTRIBUTION -> AttackTiming.Mode.NORMAL_DISTRIBUTION;
            case EFFICIENT -> AttackTiming.Mode.EFFICIENT;
            case SWITCH -> AttackTiming.Mode.SWITCH;
        };
    }

    private Entity findTarget() {
        double effectiveRange = this.config.range.get();
        if (this.config.acMode.get() == KillAura.ACMode.Grim) {
            effectiveRange = Math.min(this.config.range.get(), 3.0 + (BoatBypass.isBoatNearby(this.mc.player, this.mc.level.entitiesForRendering()) ? 0.2 : 0.0));
        } else if (this.config.acMode.get() == KillAura.ACMode.Vulcan) {
            effectiveRange = Math.min(this.config.range.get(), 3.2);
        } else if (this.config.acMode.get() == KillAura.ACMode.NCP) {
            effectiveRange = Math.min(this.config.range.get(), 3.1);
        }

        return CombatContext.findBestEntity(this.mc.level.entitiesForRendering(), this.mc.player, effectiveRange, entity -> {
            if (entity == this.mc.player || !entity.isAlive()) {
                return false;
            }
            if (entity instanceof Player p) {
                return !p.isSpectator() && Friends.get().shouldAttack(p) && !McCompat.isTeammate(p) && this.config.players.get();
            }
            if (entity instanceof Animal) {
                return this.config.animals.get();
            }
            if (entity instanceof LivingEntity) {
                return this.config.mobs.get();
            }
            return false;
        }, TargetPriority.NEAREST).orElse(null);
    }

    private void rotateTo(Entity target) {
        double dx = target.getX() - this.mc.player.getX();
        double dy = target.getY() + target.getEyeHeight() * 0.8 - (this.mc.player.getY() + this.mc.player.getEyeHeight());
        double dz = target.getZ() - this.mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, dist)));
        float yawDiff = this.wrapDegrees(targetYaw - this.mc.player.getYRot());
        float pitchDiff = this.wrapDegrees(targetPitch - this.mc.player.getXRot());
        float maxTurn = this.config.acMode.get() == KillAura.ACMode.Grim ? 80.0F : this.config.rotationSpeed.get().floatValue();

        if (Math.abs(yawDiff) > maxTurn) {
            yawDiff = Math.signum(yawDiff) * maxTurn;
        }

        if (Math.abs(pitchDiff) > maxTurn) {
            pitchDiff = Math.signum(pitchDiff) * maxTurn;
        }

        if (this.config.gcdFix.get()) {
            yawDiff = this.applyGcd(yawDiff);
            pitchDiff = this.applyGcd(pitchDiff);
        }

        this.mc.player.setYRot(this.mc.player.getYRot() + yawDiff);
        this.mc.player.setXRot(this.mc.player.getXRot() + pitchDiff);
    }

    private float applyGcd(float delta) {
        if (this.mc.options == null) return delta;
        return net.venomous.meteoraddon.utils.RotationGcd.quantize(this.mc.options.sensitivity().get(), delta);
    }

    private double getAngleTo(Entity target) {
        double dx = target.getX() - this.mc.player.getX();
        double dy = target.getY() + target.getEyeHeight() - (this.mc.player.getY() + this.mc.player.getEyeHeight());
        double dz = target.getZ() - this.mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yawDiff = Math.abs(this.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)) - this.mc.player.getYRot()));
        float pitchDiff = Math.abs(this.wrapDegrees((float) (-Math.toDegrees(Math.atan2(dy, dist))) - this.mc.player.getXRot()));
        return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
    }

    private float wrapDegrees(float v) {
        while (v > 180.0F) {
            v -= 360.0F;
        }

        while (v < -180.0F) {
            v += 360.0F;
        }

        return v;
    }

    @Override
    public String getInfoString() {
        String extra = switch (this.config.acMode.get()) {
            case Grim -> BoatBypass.isBoatNearby(this.mc.player, this.mc.level.entitiesForRendering()) ? " [Grim+Boat]" : " [Grim]";
            case Vulcan -> " [Vulcan]";
            case NCP -> " [NCP]";
            case WeakAC -> " [Weak]";
            default -> "";
        };
        return (this.target != null ? this.target.getName().getString() : null) + extra;
    }
}