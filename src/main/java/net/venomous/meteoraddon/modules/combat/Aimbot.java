package net.venomous.meteoraddon.modules.combat;

import java.util.Random;
import java.util.List;
import java.util.Set;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.AimGuard;
import net.venomous.meteoraddon.utils.McCompat;
import net.venomous.meteoraddon.utils.combat.CombatContext;
import net.venomous.meteoraddon.utils.combat.CombatContext.TargetPriority;
import net.venomous.meteoraddon.utils.combat.config.AimbotConfig;
import net.venomous.meteoraddon.utils.combat.AutoShootController;

/**
 * Aimbot using shared CombatContext config and auto-shoot; rotation stays inline
 * because its velocity-carry easing must match the original exactly.
 */
public class Aimbot extends Module {

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgProjectile = this.settings.createGroup("Projectile");
    private final SettingGroup sgHumanlike = this.settings.createGroup("Humanlike");
    private final SettingGroup sgAnticheat = this.settings.createGroup("Anticheat / GCD");

    private final AimbotConfig config = new AimbotConfig(sgGeneral, sgProjectile, sgHumanlike, sgAnticheat);

    private final Random random = new Random();
    private Entity target;
    private final AutoShootController autoShoot = new AutoShootController();

    public Aimbot() {
        super(VenomousAddon.CATEGORY, "aimbot", "Smooth, human-like aimbot with projectile prediction and GCD rotation bypass.");
    }

    @Override
    public void onActivate() {
        this.target = null;
        this.autoShoot.release();
    }

    @Override
    public void onDeactivate() {
        this.autoShoot.release();
        this.target = null;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null || this.mc.level == null) {
            return;
        }

        if (!this.mayAimNow()) {
            this.autoShoot.release();
            return;
        }

        this.target = TargetUtils.get(entity -> {
            if (!entity.isAlive() || entity == this.mc.player) {
                return false;
            } else if (!PlayerUtils.isWithin(entity, this.config.range.get())) {
                return false;
            } else if (!this.config.ignoreWalls.get() && !PlayerUtils.canSeeEntity(entity)) {
                return false;
            } else {
                return !this.config.allowedItems.get().contains(entity.getType()) ? false
                    : !(entity instanceof Player) || (Friends.get().shouldAttack((Player) entity) && !McCompat.isTeammate(entity));
            }
        }, SortPriority.LowestDistance);

        if (this.target != null) {
            this.aim(this.target);
            if (this.config.autoShoot.get()) {
                this.autoShoot.tick(System.currentTimeMillis());
            } else {
                this.autoShoot.release();
            }
        } else {
            this.autoShoot.release();
        }
    }

    /**
     * Whether the guards permit adjusting the view at all this tick.
     */
    private boolean mayAimNow() {
        if (this.mc.options == null || this.mc.player == null) return false;

        boolean attackDown = this.mc.options.keyAttack.isDown();
        boolean breaking = attackDown
            && this.mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult
            && !this.mc.player.hasInfiniteMaterials();
        boolean holdingOk = AimGuard.itemAllowed(this.config.allowedItems.get(), this.mc.player.getMainHandItem().getItem());

        return AimGuard.mayAim(attackDown, breaking, holdingOk,
            this.config.clickAim.get(), this.config.checkBlockBreak.get(), this.config.limitToItems.get());
    }

    private void aim(Entity target) {
        Vec3 targetPos = this.getTargetPos(target);
        double dx = targetPos.x - this.mc.player.getX();
        double dy = targetPos.y - (this.mc.player.getY() + this.mc.player.getEyeHeight());
        double dz = targetPos.z - this.mc.player.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontalDist)));

        if (this.isHoldingProjectile() && this.config.predict.get()) {
            double v = 3.0;
            double g = 0.05;
            double angle = Math.atan2(dy + 0.5 * g * horizontalDist / v, horizontalDist);
            targetPitch = (float) Math.toDegrees(-angle);
        }

        if (this.config.jitter.get()) {
            targetYaw += (float) (this.random.nextGaussian() * 0.15);
            targetPitch += (float) (this.random.nextGaussian() * 0.1);
        }

        // Smooth client camera directly; never spoof a disconnected server rotation state for aimbot.
        this.mc.player.setYRot(this.smooth(this.mc.player.getYRot(), targetYaw));
        this.mc.player.setXRot(this.smooth(this.mc.player.getXRot(), targetPitch));
    }

    private float smooth(float current, float target) {
        float diff = this.wrap(target - current);
        float step = (float) (this.config.speed.get() + this.random.nextDouble() * 0.5);
        if (Math.abs(diff) > step) {
            diff = Math.signum(diff) * step;
        }

        if (this.config.gcdFix.get()) {
            diff = this.applyGcd(diff);
        }

        return current + diff;
    }

    private float applyGcd(float delta) {
        if (this.mc.options == null) return delta;
        return net.venomous.meteoraddon.utils.RotationGcd.quantize(this.mc.options.sensitivity().get(), delta);
    }

    private float wrap(float v) {
        while (v > 180.0F) {
            v -= 360.0F;
        }

        while (v < -180.0F) {
            v += 360.0F;
        }

        return v;
    }

    private Vec3 getTargetPos(Entity target) {
        Vec3 pos = target.position();
        if (this.config.predict.get()) {
            double dist = this.mc.player.distanceTo(target);
            double ticks = dist / 3.0;
            pos = pos.add((target.getX() - target.xOld) * ticks, (target.getY() - target.yOld) * ticks, (target.getZ() - target.zOld) * ticks);
        }

        double height = target.getEyeHeight();
        if (this.config.aimTarget.get() == Target.Body) {
            height /= 2.0;
        }

        return pos.add(0.0, height, 0.0);
    }

    private boolean isHoldingProjectile() {
        if (this.mc.player == null) return false;
        Item item = this.mc.player.getMainHandItem().getItem();
        return item == Items.BOW || item == Items.CROSSBOW || item == Items.TRIDENT;
    }
}