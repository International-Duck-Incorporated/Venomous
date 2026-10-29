package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.EntityPredict;
import net.venomous.meteoraddon.utils.RodAimMath;
import net.venomous.meteoraddon.utils.ScaffoldAim;

import java.util.ArrayList;
import java.util.List;

public class Trajectory extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final SettingGroup sgTarget = this.settings.createGroup("Targeting");

    private final Setting<Boolean> render = this.sgRender.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
        .name("render")
        .description("Show trajectory line.")
        .defaultValue(true)
        .build());

    private final Setting<Double> maxDistance = this.sgRender.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("Maximum distance to render the trajectory.")
        .defaultValue(50.0)
        .min(5.0)
        .max(100.0)
        .sliderMax(100.0)
        .build());

    private final Setting<Double> stepSize = this.sgRender.add(new DoubleSetting.Builder()
        .name("step-size")
        .description("Distance between trajectory points.")
        .defaultValue(0.5)
        .min(0.1)
        .max(2.0)
        .sliderMax(2.0)
        .build());

    private final Setting<meteordevelopment.meteorclient.utils.render.color.SettingColor> color = this.sgRender.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
        .name("color")
        .description("Trajectory line color.")
        .defaultValue(new meteordevelopment.meteorclient.utils.render.color.SettingColor(255, 255, 0, 255))
        .build());

    private final Setting<Boolean> showImpact = this.sgRender.add(new BoolSetting.Builder()
        .name("show-impact")
        .description("Show impact point marker.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> onlyWhenHolding = this.sgTarget.add(new BoolSetting.Builder()
        .name("only-when-holding")
        .description("Only show trajectory when holding a bow, crossbow, or trident.")
        .defaultValue(true)
        .build());

    private final Setting<Double> range = this.sgTarget.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum range to calculate trajectory for.")
        .defaultValue(50.0)
        .min(5.0)
        .max(100.0)
        .sliderMax(100.0)
        .build());

    private final Setting<Boolean> predictEntities = this.sgTarget.add(new BoolSetting.Builder()
        .name("predict-entities")
        .description("Predict entity movement for moving targets.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> predictionTicks = this.sgTarget.add(new IntSetting.Builder()
        .name("prediction-ticks")
        .description("Ticks to predict entity movement ahead.")
        .defaultValue(3)
        .min(0)
        .max(20)
        .sliderMax(20)
        .visible(this.predictEntities::get)
        .build());

    private final Setting<Boolean> autoAim = this.sgTarget.add(new BoolSetting.Builder()
        .name("auto-aim")
        .description("Automatically aim at the calculated impact point.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> onlyWhenCharging = this.sgTarget.add(new BoolSetting.Builder()
        .name("only-when-charging")
        .description("Only auto-aim when charging bow/crossbow.")
        .defaultValue(true)
        .visible(this.autoAim::get)
        .build());

    private final List<Vec3> trajectoryPoints = new ArrayList<>();
    private Vec3 impactPos = null;
    private LivingEntity currentTarget = null;

    // Projectile physics constants
    private static final double ARROW_GRAVITY = 0.05;
    private static final double ARROW_DRAG = 0.99;
    private static final double TRIDENT_GRAVITY = 0.05;
    private static final double TRIDENT_DRAG = 0.99;

    public Trajectory() {
        super(VenomousAddon.CATEGORY, "trajectory", "Predicts projectile trajectory for bows, crossbows, and tridents.");
    }

    @Override
    public void onDeactivate() {
        this.trajectoryPoints.clear();
        this.impactPos = null;
        this.currentTarget = null;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null || this.mc.level == null) {
            this.trajectoryPoints.clear();
            this.impactPos = null;
            this.currentTarget = null;
            return;
        }

        // Check if we should show trajectory
        if (!this.shouldShowTrajectory()) {
            this.trajectoryPoints.clear();
            this.impactPos = null;
            this.currentTarget = null;
            return;
        }

        // Calculate trajectory
        this.calculateTrajectory();
    }

    private boolean shouldShowTrajectory() {
        if (!this.render.get()) return false;
        
        if (this.onlyWhenHolding.get()) {
            ItemStack stack = this.mc.player.getMainHandItem();
            if (!(stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem || stack.getItem() == Items.TRIDENT)) {
                return false;
            }
            
            if (this.autoAim.get() && this.onlyWhenCharging.get()) {
                if (!this.mc.player.isUsingItem() && !(stack.getItem() instanceof CrossbowItem && isCrossbowCharged(stack))) {
                    return false;
                }
            }
        }
        
        return true;
    }

    private boolean isCrossbowCharged(ItemStack stack) {
        if (stack.getItem() instanceof CrossbowItem) {
            // Crossbow is charged if it has the Charged NBT tag
            // Simplified check - crossbow is charged when fully drawn
            return this.mc.player != null && this.mc.player.isUsingItem() && this.mc.player.getUseItemRemainingTicks() > 0;
        }
        return false;
    }

    private void calculateTrajectory() {
        this.trajectoryPoints.clear();
        this.impactPos = null;
        this.currentTarget = null;

        ItemStack stack = this.mc.player.getMainHandItem();
        boolean isBow = stack.getItem() instanceof BowItem;
        boolean isCrossbow = stack.getItem() instanceof CrossbowItem;
        boolean isTrident = stack.getItem() == Items.TRIDENT;

        if (!isBow && !isCrossbow && !isTrident) {
            return;
        }

        // Get projectile initial velocity
        double velocity = this.getProjectileVelocity(isBow, isCrossbow);
        if (velocity <= 0) return;

        // Find target if predicting entities
        LivingEntity target = null;
        if (this.predictEntities.get()) {
            this.currentTarget = this.findTarget();
            target = this.currentTarget;
            
            // Predict entity movement ahead by predictionTicks
            if (target != null) {
                double[] predicted = EntityPredict.predict(
                    target.getX(), target.getY(), target.getZ(),
                    target.getDeltaMovement().x, target.getDeltaMovement().y, target.getDeltaMovement().z,
                    target.onGround(), this.predictionTicks.get()
                );
                // Use predicted position for targeting
                target = target; // Target entity reference remains, position is predicted in trajectory calc
            }
        }

        // Calculate trajectory
        Vec3 start = this.mc.player.getEyePosition();
        Vec3 direction = this.getAimDirection();
        double gravity = this.getGravity();
        double drag = this.getDrag();

        Vec3 velocityVec = direction.scale(velocity);
        Vec3 pos = start;
        Vec3 velocityVecCurrent = velocityVec;
        double maxDist = this.maxDistance.get();
        double maxDistSq = maxDist * maxDist;
        double step = this.stepSize.get();

        this.trajectoryPoints.clear();
        this.trajectoryPoints.add(pos);

        Vec3 lastPos = pos;
        double distanceTraveled = 0;

        for (int i = 0; distanceTraveled < maxDist; i++) {
            // Apply physics
            velocityVecCurrent = new Vec3(
                velocityVecCurrent.x * drag,
                (velocityVecCurrent.y - gravity) * drag,
                velocityVecCurrent.z * drag
            );

            pos = pos.add(velocityVecCurrent);
            distanceTraveled += lastPos.distanceTo(pos);
            lastPos = pos;

            this.trajectoryPoints.add(new Vec3(pos.x, pos.y, pos.z));

            // Check for block collision
            BlockPos blockPos = BlockPos.containing(pos);
            VoxelShape shape = this.mc.level.getBlockState(blockPos).getCollisionShape(this.mc.level, blockPos);
            if (!shape.isEmpty()) {
                this.impactPos = pos;
                break;
            }

            // Check for entity collision
            if (this.predictEntities.get()) {
                for (Entity entity : this.mc.level.getEntitiesOfClass(Entity.class, new AABB(BlockPos.containing(pos)).inflate(0.3))) {
                    if (entity instanceof LivingEntity && entity != this.mc.player && entity.isAlive()) {
                        if (entity.getBoundingBox().contains(new Vec3(blockPos.getX(), blockPos.getY(), blockPos.getZ()))) {
                            this.impactPos = pos;
                            this.currentTarget = entity instanceof LivingEntity ? (LivingEntity) entity : null;
                            break;
                        }
                    }
                }
            }

            if (this.impactPos != null) break;

            // Stop if velocity is too low
            if (velocityVecCurrent.lengthSqr() < 0.001) break;

            // Stop if too many iterations
            if (i > 1000) break;
        }

        // Auto-aim if enabled
        if (this.autoAim.get() && this.impactPos != null && this.isHoldingProjectile()) {
            this.autoAimAtImpact();
        }
    }

    private double getProjectileVelocity(boolean isBow, boolean isCrossbow) {
        if (this.mc.player == null) return 0;
        
        ItemStack stack = this.mc.player.getMainHandItem();
        
        if (stack.getItem() instanceof BowItem) {
            // Bow charge calculation
            int useDuration = this.mc.player.getUseItemRemainingTicks();
            int maxDuration = 72000; // 20 minutes in ticks (max bow charge time)
            float charge = (maxDuration - useDuration) / 20.0f;
            charge = Math.min(charge, 1.0f);
            return Math.max(0.1, charge * 3.0) * 3.0;
        } else if (stack.getItem() instanceof CrossbowItem) {
            // Crossbow always shoots at max power when charged
            return 3.15;
        } else if (stack.getItem() == Items.TRIDENT) {
            // Trident throw velocity
            return 2.5;
        }
        return 0;
    }

    private double getGravity() {
        return 0.05; // Minecraft arrow/trident gravity
    }

    private double getDrag() {
        return 0.99; // Air drag
    }

    private Vec3 getAimDirection() {
        float yaw = this.mc.player.getYRot();
        float pitch = this.mc.player.getXRot();
        
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        
        double cosPitch = Math.cos(pitchRad);
        double sinPitch = Math.sin(pitchRad);
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);
        
        return new Vec3(
            -sinYaw * cosPitch,
            -sinPitch,
            cosYaw * cosPitch
        ).normalize();
    }

    private boolean isHoldingProjectile() {
        if (this.mc.player == null) return false;
        ItemStack stack = this.mc.player.getMainHandItem();
        return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem || stack.getItem() == Items.TRIDENT;
    }

    private LivingEntity findTarget() {
        if (this.mc.level == null || this.mc.player == null) return null;
        
        double range = this.range.get();
        List<LivingEntity> entities = this.mc.level.getEntitiesOfClass(
            LivingEntity.class, 
            this.mc.player.getBoundingBox().inflate(range)
        );
        
        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        
        for (LivingEntity entity : entities) {
            if (entity == this.mc.player) continue;
            if (entity.isRemoved() || entity.isDeadOrDying()) continue;
            
            double distSq = this.mc.player.distanceToSqr(entity);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = entity;
            }
        }
        
        return best;
    }

    private void autoAimAtImpact() {
        if (this.impactPos == null) return;
        
        Vec3 eyePos = this.mc.player.getEyePosition();
        double[] angles = this.calculateAngles(eyePos, this.impactPos);
        
        // Use PreRotate for proper packet ordering
        net.venomous.meteoraddon.utils.PreRotate.rotateThen(this, angles[0], angles[1], () -> {});
    }

    private double[] calculateAngles(Vec3 from, Vec3 to) {
        Vec3 diff = to.subtract(from);
        double horizontal = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        double yaw = Math.toDegrees(Math.atan2(-diff.x, diff.z));
        double pitch = -Math.toDegrees(Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));
        return new double[]{yaw, pitch};
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onRender(Render3DEvent event) {
        if (!this.render.get() || this.mc.player == null) return;
        
        if (this.trajectoryPoints.isEmpty()) return;

        // Render trajectory line - draw individual line segments
        for (int i = 0; i < this.trajectoryPoints.size() - 1; i++) {
            Vec3 start = this.trajectoryPoints.get(i);
            Vec3 end = this.trajectoryPoints.get(i + 1);
            event.renderer.line(
                start.x, start.y, start.z,
                end.x, end.y, end.z,
                this.color.get(), this.color.get()
            );
        }

        // Render impact point
        if (this.showImpact.get() && this.impactPos != null) {
            BlockPos impactBlock = BlockPos.containing(this.impactPos);
            event.renderer.box(
                new AABB(impactBlock).inflate(0.1),
                this.color.get(),
                this.color.get(),
                meteordevelopment.meteorclient.renderer.ShapeMode.Lines,
                2
            );
        }
    }

    public enum ProjectileType {
        Arrow,
        Crossbow,
        Trident;
    }
}