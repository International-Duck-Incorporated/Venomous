package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import net.venomous.meteoraddon.utils.combat.CombatContext;
import net.venomous.meteoraddon.utils.combat.CombatContext.TargetPriority;
import net.venomous.meteoraddon.utils.combat.CrosshairScanner;
import net.venomous.meteoraddon.utils.combat.FakeFallPackets;
import net.venomous.meteoraddon.utils.combat.SafeLanding;
import net.venomous.meteoraddon.utils.combat.SlamTrajectory;
import net.venomous.meteoraddon.utils.combat.config.MaceKillConfig;

/**
 * MaceKill using shared CombatContext targeting, CrosshairScanner raycasting,
 * SlamTrajectory velocity math, SafeLanding offset, and FakeFallPackets packet trick.
 */
public class MaceKill extends Module {

    private final SettingGroup sgGeneral = this.settings.createGroup("General");
    private final SettingGroup sgTarget = this.settings.createGroup("Targeting");
    private final SettingGroup sgVelocity = this.settings.createGroup("Velocity");
    private final SettingGroup sgSafety = this.settings.createGroup("Safety");

    private final MaceKillConfig config = new MaceKillConfig(sgGeneral, sgTarget, sgVelocity, sgSafety);

    private Phase phase = Phase.IDLE;
    private LivingEntity killTarget;
    private Vec3 aboveTargetPos;
    private boolean hasAttacked;
    private int attackCooldown;
    private Vec3 previousPos;
    private int phaseTicks;
    private final MutableBlockPos mutablePos = new MutableBlockPos();
    private final java.util.Map<Vec3, Boolean> positionCache = new java.util.LinkedHashMap<Vec3, Boolean>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Vec3, Boolean> eldest) {
            return this.size() > 256;
        }
    };

    public enum Mode {
        Slam,
        FakeFall
    }

    public enum TargetListMode {
        Whitelist,
        Blacklist
    }

    public MaceKill() {
        super(VenomousAddon.CATEGORY, "mace-kill", "Velocity-launches you above a target, then slams down with the mace.");
    }

    @Override
    public void onActivate() {
        this.resetState();
    }

    @Override
    public void onDeactivate() {
        this.resetState();
    }

    private void resetState() {
        if (this.phase != Phase.IDLE && this.mc.player != null) {
            this.mc.player.setDeltaMovement(Vec3.ZERO);
            this.mc.player.setSprinting(false);
        }

        this.phase = Phase.IDLE;
        this.killTarget = null;
        this.aboveTargetPos = null;
        this.hasAttacked = false;
        this.attackCooldown = 0;
        this.previousPos = null;
        this.phaseTicks = 0;
        this.positionCache.clear();
    }

    private boolean isHoldingMace() {
        return this.mc.player != null && this.mc.player.getMainHandItem().getItem() == Items.MACE;
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (this.config.mode.get() != Mode.FakeFall) {
            return;
        }
        if (this.mc.player == null || this.mc.level == null || this.mc.player.connection == null) {
            return;
        }
        if (!this.isHoldingMace()) {
            return;
        }
        if (this.config.fakeFallOnlyOnGround.get() && !this.mc.player.onGround()) {
            return;
        }

        int height = this.determineFakeFallHeight();
        if (height <= 0) {
            return;
        }

        FakeFallPackets.sendFakeFall((net.minecraft.client.player.LocalPlayer) this.mc.player, height);
    }

    /** Largest unobstructed spoof height up to the configured maximum. */
    private int determineFakeFallHeight() {
        AABB box = this.mc.player.getBoundingBox();
        for (int i = this.config.fakeFallHeight.get(); i >= 1; i--) {
            if (this.mc.level.noBlockCollision(this.mc.player, box.move(0.0, i, 0.0))) {
                return i;
            }
        }
        return 0;
    }

    @EventHandler
    private void onPreTick(Pre event) {
        if (this.config.mode.get() != Mode.Slam) {
            if (this.phase != Phase.IDLE) {
                this.resetState();
            }
            return;
        }

        if (this.mc.player != null && this.mc.level != null) {
            if (!this.mc.player.isPassenger()) {
                if (!this.isHoldingMace()) {
                    this.resetState();
                } else if (this.attackCooldown > 0) {
                    this.attackCooldown--;
                } else {
                    switch (this.phase) {
                        case IDLE:
                            this.killTarget = this.findTarget();
                            if (this.killTarget == null) {
                                return;
                            }

                            if (!this.isValidTarget(this.killTarget)) {
                                this.killTarget = null;
                                return;
                            }

                            Vec3 targetCenter = this.killTarget.getBoundingBox().getCenter();
                            this.aboveTargetPos = SlamTrajectory.clampAboveTarget(targetCenter, this.config.aboveHeight.get(), this.mc.level);
                            double ceiling = this.mc.level.getMaxY() - 1;
                            if (this.aboveTargetPos.y > ceiling) {
                                this.aboveTargetPos = new Vec3(this.aboveTargetPos.x, ceiling, this.aboveTargetPos.z);
                                if (this.aboveTargetPos.y - this.mc.player.getY() < 5.0) {
                                    this.killTarget = null;
                                    this.aboveTargetPos = null;
                                    return;
                                }
                            }

                            this.hasAttacked = false;
                            this.previousPos = this.mc.player.position();
                            this.phaseTicks = 0;
                            this.phase = Phase.LAUNCHING_UP;
                            break;
                        case LAUNCHING_UP:
                            if (this.killTarget == null || !this.killTarget.isAlive() || !this.isValidTarget(this.killTarget)) {
                                this.resetState();
                                return;
                            }

                            this.phaseTicks++;
                            Vec3 launchTargetCenter = this.killTarget.getBoundingBox().getCenter();
                            this.aboveTargetPos = SlamTrajectory.clampAboveTarget(launchTargetCenter, this.config.aboveHeight.get(), this.mc.level);
                            double distToAbove = this.mc.player.position().distanceTo(this.aboveTargetPos);
                            double verticalRemaining = this.aboveTargetPos.y - this.mc.player.getY();
                            if (verticalRemaining <= 0.75 || distToAbove <= this.config.nearTargetThreshold.get() || this.phaseTicks >= SlamTrajectory.getMaxLaunchTicks(this.config.aboveHeight.get(), this.config.upwardSpeed.get())) {
                                this.startSlam();
                                this.applySlamVelocity();
                                return;
                            }

                            this.rotateToTarget(this.killTarget);
                            this.applyLaunchVelocity();
                            break;
                        case SLAMMING_DOWN:
                            if (this.killTarget == null || !this.killTarget.isAlive() || !this.isValidTarget(this.killTarget)) {
                                this.resetState();
                                return;
                            }

                            this.phaseTicks++;
                            this.rotateToTarget(this.killTarget);
                            this.applySlamVelocity();
                            if (!this.hasAttacked && this.shouldAttack(this.killTarget)) {
                                this.finishAttack(this.killTarget);
                                return;
                            }

                            double distToTarget = this.mc.player.position().distanceTo(this.killTarget.getBoundingBox().getCenter());
                            if (distToTarget > this.config.maxRange.get() * 1.5
                                || this.mc.player.getY() < this.killTarget.getBoundingBox().minY - 8.0
                                || this.phaseTicks >= SlamTrajectory.getMaxSlamTicks(this.config.aboveHeight.get(), this.config.downwardSpeed.get())) {
                                this.resetState();
                                return;
                            }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.mc.player != null && this.mc.level != null) {
            if (this.isHoldingMace()) {
                if (this.killTarget != null && this.killTarget.isAlive()) {
                    this.rotateToTarget(this.killTarget);
                }
            }
        }
    }

    private void startSlam() {
        this.phase = Phase.SLAMMING_DOWN;
        this.phaseTicks = 0;
        this.mc.player.setSprinting(true);
    }

    private void applyLaunchVelocity() {
        if (this.aboveTargetPos != null) {
            Vec3 delta = this.aboveTargetPos.subtract(this.mc.player.position());
            Vec3 velocity = SlamTrajectory.computeLaunchVelocity(delta, this.config.velocitySpeed.get(), this.config.upwardSpeed.get());
            this.mc.player.setSprinting(false);
            this.mc.player.setDeltaMovement(velocity);
        }
    }

    private void applySlamVelocity() {
        Vec3 targetCenter = this.killTarget.getBoundingBox().getCenter();
        Vec3 delta = targetCenter.subtract(this.mc.player.position());
        Vec3 velocity = SlamTrajectory.computeSlamVelocity(delta, this.config.velocitySpeed.get(), this.config.downwardSpeed.get());
        this.mc.player.setSprinting(true);
        this.mc.player.setDeltaMovement(velocity);
    }

    private boolean shouldAttack(LivingEntity target) {
        AABB targetBox = target.getBoundingBox();
        AABB playerBox = this.mc.player.getBoundingBox().inflate(this.config.stopDistance.get());
        if (playerBox.intersects(targetBox)) {
            return true;
        }

        Vec3 playerPos = this.mc.player.position();
        Vec3 targetCenter = targetBox.getCenter();
        double horizontalDistance = Math.hypot(playerPos.x - targetCenter.x, playerPos.z - targetCenter.z);
        double allowedHorizontal = Math.max(1.25, this.config.stopDistance.get());
        double allowedVertical = Math.max(1.0, this.config.stopDistance.get());
        return horizontalDistance <= allowedHorizontal && playerPos.y <= targetBox.maxY + allowedVertical && playerPos.y >= targetBox.minY - 4.0;
    }

    private void finishAttack(LivingEntity target) {
        this.executeAttack(target);
        this.hasAttacked = true;
        if (this.config.useOffset.get()) {
            Vec3 safe = SafeLanding.applySafeOffset(this.previousPos != null ? this.previousPos : this.mc.player.position(),
                this.config.offsetHorizontal.get(), this.config.offsetY.get(), this.mc.player, this.mc.level);
            this.mc.player.setPos(safe.x, safe.y, safe.z);
        }

        if (this.config.stopOnTarget.get()) {
            this.mc.player.setDeltaMovement(Vec3.ZERO);
            this.mc.player.setSprinting(false);
        }

        if (this.config.chatFlush.get()) {
            double dist = this.previousPos != null ? this.previousPos.distanceTo(this.mc.player.position()) : this.config.aboveHeight.get();
            this.info("Smashed " + target.getName().getString() + " - " + String.format("%.1f", dist) + " blocks");
        }

        this.attackCooldown = 5;
        this.phase = Phase.IDLE;
        this.killTarget = null;
        this.aboveTargetPos = null;
        this.phaseTicks = 0;
    }

    private void executeAttack(LivingEntity target) {
        if (this.mc.player != null && this.mc.player.connection != null) {
            if (this.config.swingHand.get()) {
                this.mc.player.swing(InteractionHand.MAIN_HAND);
            }

            this.mc.player.connection.send(new ServerboundAttackPacket(target.getId()));
        }
    }

    private LivingEntity findTarget() {
        if (this.mc.player != null && this.mc.level != null) {
            // Check crosshair first
            if (this.mc.hitResult instanceof EntityHitResult hit && this.isValidTarget(hit.getEntity()) && hit.getEntity() instanceof LivingEntity le) {
                return le;
            } else {
                double maxDist = this.config.maxRange.get();
                Vec3 eyePos = this.mc.player.getEyePosition();
                Vec3 lookVec = this.mc.player.getViewVector(1.0F);
                BlockHitResult blockHit = this.mc
                    .level
                    .clip(new ClipContext(eyePos, eyePos.add(lookVec.scale(maxDist)), Block.COLLIDER, Fluid.NONE, this.mc.player));
                if (blockHit.getType() != Type.MISS) {
                    eyePos.distanceTo(blockHit.getLocation());
                }

                // Use CombatContext for target selection
                return CombatContext.findBestEntity(this.mc.level.entitiesForRendering(), this.mc.player, maxDist, e -> {
                    if (e != this.mc.player && e instanceof LivingEntity le && le.isAlive() && this.isValidTarget(e)) {
                        double dist = eyePos.distanceToSqr(e.getBoundingBox().getCenter());
                        if (!(dist > maxDist * maxDist) && this.canSeeTarget(e)) {
                            return true;
                        }
                    }
                    return false;
                }, TargetPriority.NEAREST)
                    .filter(e -> e instanceof LivingEntity)
                    .map(e -> (LivingEntity) e)
                    .orElse(null);
            }
        } else {
            return null;
        }
    }

    private boolean canSeeTarget(Entity target) {
        if (this.mc.player != null && this.mc.level != null) {
            Vec3 eyePos = this.mc.player.getEyePosition();
            Vec3 targetCenter = target.getBoundingBox().getCenter();
            BlockHitResult result = this.mc.level.clip(new ClipContext(eyePos, targetCenter, Block.COLLIDER, Fluid.NONE, this.mc.player));
            return result.getType() == Type.MISS ? true : eyePos.distanceTo(result.getLocation()) >= eyePos.distanceTo(targetCenter) - 0.5;
        } else {
            return false;
        }
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null) {
            return false;
        } else if (entity == this.mc.player) {
            return false;
        } else if (entity instanceof Player player && this.config.ignoreFriends.get() && Friends.get().isFriend(player)) {
            return false;
        } else {
            EntityType<?> type = entity.getType();
            boolean inList = this.config.targetEntities.get().contains(type);
            return this.config.targetListMode.get() != TargetListMode.Whitelist ? !inList : inList || this.config.targetEntities.get().isEmpty();
        }
    }

    private void rotateToTarget(Entity target) {
        if (this.mc.player != null && target != null) {
            Vec3 playerPos = this.mc.player.getEyePosition();
            AABB box = target.getBoundingBox();
            double targetCenterY = box.getCenter().y;
            double heightDiff = targetCenterY - playerPos.y;
            double boxHeight = box.maxY - box.minY;
            double targetY;
            if (Math.abs(heightDiff) < 1.0) {
                targetY = targetCenterY;
            } else if (heightDiff > 0.0) {
                double offset = Math.min(heightDiff / 5.0, 0.4);
                targetY = targetCenterY - boxHeight * offset;
            } else {
                double offset = Math.min(-heightDiff / 5.0, 0.4);
                targetY = targetCenterY + boxHeight * offset;
            }

            Vec3 targetPos = new Vec3(box.getCenter().x, targetY, box.getCenter().z);
            Vec3 toTarget = targetPos.subtract(playerPos).normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
            float pitch = (float) (-Math.toDegrees(Math.asin(toTarget.y)));
            this.mc.player.setYRot(yaw);
            this.mc.player.setXRot(pitch);
        }
    }

    private enum Phase {
        IDLE,
        LAUNCHING_UP,
        SLAMMING_DOWN
    }
}