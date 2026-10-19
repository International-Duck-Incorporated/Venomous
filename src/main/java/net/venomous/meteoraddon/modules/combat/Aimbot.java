package net.venomous.meteoraddon.modules.combat;

import java.util.Random;
import java.util.List;
import java.util.Set;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.AimGuard;
import net.venomous.meteoraddon.utils.RotationGcd;
import net.venomous.meteoraddon.utils.McCompat;

public class Aimbot extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgProjectile = this.settings.createGroup("Projectile");
    private final SettingGroup sgHumanlike = this.settings.createGroup("Humanlike");
    private final SettingGroup sgAnticheat = this.settings.createGroup("Anticheat / GCD");

    private final Setting<Set<EntityType<?>>> entities = this.sgGeneral.add(
        new EntityTypeListSetting.Builder()
            .name("entities")
            .description("Entities to aim at.")
            .defaultValue(Set.of(EntityTypes.PLAYER))
            .build()
    );

    private final Setting<Double> range = this.sgGeneral.add(
        new DoubleSetting.Builder()
            .name("range")
            .description("The range at which an entity can be targeted.")
            .defaultValue(5.0)
            .min(0.0)
            .sliderMax(100.0)
            .build()
    );

    private final Setting<Target> aimTarget = this.sgGeneral.add(
        new EnumSetting.Builder<Target>()
            .name("aim-target")
            .description("Which part of the body to aim at.")
            .defaultValue(Target.Body)
            .build()
    );

    private final Setting<Boolean> ignoreWalls = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("ignore-walls")
            .description("Whether or not to ignore aiming through walls.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> predict = this.sgProjectile.add(
        new BoolSetting.Builder()
            .name("predict")
            .description("Predict target movement for projectiles.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> autoShoot = this.sgProjectile.add(
        new BoolSetting.Builder()
            .name("auto-shoot")
            .description("Automatically release bow or fire crossbow.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Double> speed = this.sgHumanlike.add(
        new DoubleSetting.Builder()
            .name("speed")
            .description("How fast to aim at the entity (degrees per tick).")
            .defaultValue(15.0)
            .min(1.0)
            .sliderMax(60.0)
            .build()
    );

    private final Setting<Boolean> clickAim = this.sgHumanlike.add(
        new BoolSetting.Builder()
            .name("click-aim")
            .description("Only aim while the attack key is held. A human's view only moves when "
                + "they choose to move it; an aim assist that keeps tracking someone you are "
                + "walking past is a behaviour no mouse produces.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> checkBlockBreak = this.sgHumanlike.add(
        new BoolSetting.Builder()
            .name("check-block-break")
            .description("Do not aim while mining. Attack held against a block means digging, and "
                + "a person digging is not also lining up a shot.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> limitToItems = this.sgHumanlike.add(
        new BoolSetting.Builder()
            .name("limit-to-items")
            .description("Only aim while holding one of the allowed items.")
            .defaultValue(false)
            .build()
    );

    private final Setting<List<Item>> allowedItems = this.sgHumanlike.add(
        new ItemListSetting.Builder()
            .name("allowed-items")
            .description("Items the aim assist is allowed to hold. Ignored while "
                + "limit-to-items is off, and an empty list means unrestricted.")
            .build()
    );

    private final Setting<Boolean> gcdFix = this.sgAnticheat.add(
        new BoolSetting.Builder()
            .name("gcd-fix")
            .description("Quantize rotation steps to match mouse sensitivity GCD (bypasses Vulcan, Polar, Grim).")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> jitter = this.sgAnticheat.add(
        new BoolSetting.Builder()
            .name("rotation-jitter")
            .description("Add subtle Gaussian jitter to prevent static angle pattern detection.")
            .defaultValue(true)
            .build()
    );

    private final Random random = new Random();
    private Entity target;
    private boolean managedUse;
    private boolean previousUseDown;
    private int crossbowClickTicks;

    public Aimbot() {
        super(VenomousAddon.CATEGORY, "aimbot", "Smooth, human-like aimbot with projectile prediction and GCD rotation bypass.");
    }

    @Override
    public void onActivate() {
        this.target = null;
        this.managedUse = false;
        this.previousUseDown = false;
        this.crossbowClickTicks = 0;
    }

    @Override
    public void onDeactivate() {
        this.releaseUse();
        this.target = null;
        this.crossbowClickTicks = 0;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null || this.mc.level == null) {
            return;
        }

        if (!this.mayAimNow()) {
            this.releaseUse();
            this.crossbowClickTicks = 0;
            return;
        }

        this.target = TargetUtils.get(entity -> {
            if (!entity.isAlive() || entity == this.mc.player) {
                return false;
            } else if (!PlayerUtils.isWithin(entity, this.range.get())) {
                return false;
            } else if (!this.ignoreWalls.get() && !PlayerUtils.canSeeEntity(entity)) {
                return false;
            } else {
                return !this.entities.get().contains(entity.getType()) ? false : !(entity instanceof Player) || (Friends.get().shouldAttack((Player)entity) && !McCompat.isTeammate(entity));
            }
        }, SortPriority.LowestDistance);

        if (this.target != null) {
            this.aim(this.target);
            if (this.autoShoot.get()) {
                this.handleAutoShoot();
            } else {
                this.releaseUse();
            }
        } else {
            this.releaseUse();
            this.crossbowClickTicks = 0;
        }
    }

    /**
     * Whether the guards permit adjusting the view at all this tick.
     *
     * <p>See {@link AimGuard} for why an aimer needs a "when" as well as a "where". All three
     * conditions are gathered here so the module stays free of the decision itself.</p>
     */
    private boolean mayAimNow() {
        if (this.mc.options == null || this.mc.player == null) return false;

        boolean attackDown = this.mc.options.keyAttack.isDown();
        boolean breaking = attackDown
            && this.mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult
            && !this.mc.player.hasInfiniteMaterials();
        boolean holdingOk = AimGuard.itemAllowed(this.allowedItems.get(), this.mc.player.getMainHandItem().getItem());

        return AimGuard.mayAim(attackDown, breaking, holdingOk,
            this.clickAim.get(), this.checkBlockBreak.get(), this.limitToItems.get());
    }

    private void aim(Entity target) {
        Vec3 targetPos = this.getTargetPos(target);
        double dx = targetPos.x - this.mc.player.getX();
        double dy = targetPos.y - (this.mc.player.getY() + this.mc.player.getEyeHeight());
        double dz = targetPos.z - this.mc.player.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, horizontalDist)));

        if (this.isHoldingProjectile() && this.predict.get()) {
            double v = 3.0;
            double g = 0.05;
            double angle = Math.atan2(dy + 0.5 * g * horizontalDist / v, horizontalDist);
            targetPitch = (float)Math.toDegrees(-angle);
        }

        if (this.jitter.get()) {
            targetYaw += (float)(this.random.nextGaussian() * 0.15);
            targetPitch += (float)(this.random.nextGaussian() * 0.1);
        }

        // Smooth client camera directly; never spoof a disconnected server rotation state for aimbot.
        this.mc.player.setYRot(this.smooth(this.mc.player.getYRot(), targetYaw));
        this.mc.player.setXRot(this.smooth(this.mc.player.getXRot(), targetPitch));
    }

    private float smooth(float current, float target) {
        float diff = this.wrap(target - current);
        float step = (float)(this.speed.get() + this.random.nextDouble() * 0.5);
        if (Math.abs(diff) > step) {
            diff = Math.signum(diff) * step;
        }

        if (this.gcdFix.get()) {
            diff = this.applyGcd(diff);
        }

        return current + diff;
    }

    private float applyGcd(float delta) {
        if (this.mc.options == null) return delta;
        return RotationGcd.quantize(this.mc.options.sensitivity().get(), delta);
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
        if (this.predict.get()) {
            double dist = this.mc.player.distanceTo(target);
            double ticks = dist / 3.0;
            pos = pos.add((target.getX() - target.xOld) * ticks, (target.getY() - target.yOld) * ticks, (target.getZ() - target.zOld) * ticks);
        }

        double height = target.getEyeHeight();
        if (this.aimTarget.get() == Target.Body) {
            height /= 2.0;
        }

        return pos.add(0.0, height, 0.0);
    }

    private boolean isHoldingProjectile() {
        if (this.mc.player == null) return false;
        Item item = this.mc.player.getMainHandItem().getItem();
        return item == Items.BOW || item == Items.CROSSBOW || item == Items.TRIDENT;
    }

    private void handleAutoShoot() {
        if (this.mc.player == null) return;
        Item item = this.mc.player.getMainHandItem().getItem();
        if (item == Items.BOW) {
            float charge = (this.mc.player.getUseItem().getUseDuration(this.mc.player) - this.mc.player.getUseItemRemainingTicks()) / 20.0F;
            if (!this.mc.player.isUsingItem()) {
                this.pressUse();
            } else if (charge >= 0.9F) {
                this.releaseUse();
            }
        } else if (item == Items.CROSSBOW) {
            // Keep the input down for a complete client tick. A same-method true/false pulse is
            // never observed by Minecraft's input processing and therefore cannot fire reliably.
            if (!this.managedUse && !this.mc.player.isUsingItem()) {
                this.pressUse();
                this.crossbowClickTicks = 1;
            } else if (this.crossbowClickTicks > 0 && --this.crossbowClickTicks == 0) {
                this.releaseUse();
            }
        } else {
            this.releaseUse();
            this.crossbowClickTicks = 0;
        }
    }

    private void pressUse() {
        if (!this.managedUse) {
            this.previousUseDown = this.mc.options.keyUse.isDown();
            this.managedUse = true;
        }
        this.mc.options.keyUse.setDown(true);
    }

    private void releaseUse() {
        if (this.managedUse && this.mc.options != null) {
            this.mc.options.keyUse.setDown(this.previousUseDown);
        }
        this.managedUse = false;
        this.previousUseDown = false;
    }
}
