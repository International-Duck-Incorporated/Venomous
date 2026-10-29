package net.venomous.meteoraddon.modules.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.AnchorDamageMath;
import net.venomous.meteoraddon.utils.PreRotate;

public class AutoAnchor extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgTargeting = this.settings.createGroup("Targeting");
    private final SettingGroup sgSafety = this.settings.createGroup("Safety");
    private final SettingGroup sgRotation = this.settings.createGroup("Rotation");
    private final SettingGroup sgRender = this.settings.createGroup("Render");

    private final Setting<Double> radius = this.sgGeneral
        .add(new DoubleSetting.Builder()
            .name("radius")
            .description("Search radius for anchors and placement.")
            .defaultValue(5.0)
            .min(1.0)
            .max(15.0)
            .sliderMax(15.0)
            .build());

    private final Setting<Boolean> autoPlace = this.sgGeneral
        .add(new BoolSetting.Builder()
            .name("auto-place")
            .description("Automatically place respawn anchors near targets.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> autoSwitch = this.sgGeneral
        .add(new BoolSetting.Builder()
            .name("auto-switch")
            .description("Automatically switch to Respawn Anchor / Glowstone.")
            .defaultValue(true)
            .build());

    private final Setting<Integer> placeDelay = this.sgGeneral
        .add(new IntSetting.Builder()
            .name("place-delay")
            .description("Delay in ticks between placing anchors.")
            .defaultValue(4)
            .min(0)
            .max(40)
            .sliderMax(40)
            .build());

    private final Setting<Integer> chargeDelay = this.sgGeneral
        .add(new IntSetting.Builder()
            .name("charge-delay")
            .description("Delay in ticks between charging/detonating anchors.")
            .defaultValue(4)
            .min(0)
            .max(40)
            .sliderMax(40)
            .build());

    private final Setting<Boolean> safeAnchor = this.sgSafety
        .add(new BoolSetting.Builder()
            .name("safe-anchor")
            .description("Place a shield block to reduce self-damage.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> antiSuicide = this.sgSafety
        .add(new BoolSetting.Builder()
            .name("anti-suicide")
            .description("Prevent activating anchors if it would kill you.")
            .defaultValue(true)
            .build());

    private final Setting<Double> minDamage = this.sgSafety
        .add(new DoubleSetting.Builder()
            .name("min-damage")
            .description("Minimum damage an anchor must deal to be placed/activated.")
            .defaultValue(6.0)
            .min(0.0)
            .max(36.0)
            .sliderMax(36.0)
            .build());

    private final Setting<Double> maxSelfDamage = this.sgSafety
        .add(new DoubleSetting.Builder()
            .name("max-self-damage")
            .description("Maximum self-damage allowed from a single anchor activation.")
            .defaultValue(4.0)
            .min(0.0)
            .max(20.0)
            .sliderMax(20.0)
            .visible(this.antiSuicide::get)
            .build());

    private final Setting<Double> enemyRange = this.sgTargeting
        .add(new DoubleSetting.Builder()
            .name("enemy-range")
            .description("Maximum distance an enemy can be to be targeted.")
            .defaultValue(12.0)
            .min(0.0)
            .max(32.0)
            .sliderMax(32.0)
            .build());

    private final Setting<Boolean> ignoreWalls = this.sgTargeting
        .add(new BoolSetting.Builder()
            .name("ignore-walls")
            .description("Ignore walls when targeting enemies.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> targetPlayers = this.sgTargeting
        .add(new BoolSetting.Builder()
            .name("target-players")
            .description("Target players.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> targetAnimals = this.sgTargeting
        .add(new BoolSetting.Builder()
            .name("target-animals")
            .description("Target animals/mobs.")
            .defaultValue(false)
            .build());

    private final Setting<RotationMode> rotationMode = this.sgRotation
        .add(new EnumSetting.Builder<RotationMode>()
            .name("rotation-mode")
            .description("How to rotate to the anchor.")
            .defaultValue(RotationMode.Silent)
            .build());

    private final Setting<Boolean> useRaycast = this.sgRotation
        .add(new BoolSetting.Builder()
            .name("use-raycast")
            .description("Only interact with anchors while aiming at them.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> activateOnClick = this.sgRotation
        .add(new BoolSetting.Builder()
            .name("activate-on-click")
            .description("Only charge/detonate anchors while right-click is held.")
            .defaultValue(false)
            .build());

    private final Setting<SettingColor> color = this.sgRender
        .add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
            .name("color")
            .description("Render color for anchor placements.")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .build());

    private final Setting<Boolean> renderBox = this.sgRender
        .add(new BoolSetting.Builder()
            .name("render-box")
            .description("Render a box around anchor placements.")
            .defaultValue(true)
            .build());

    private int placeTimer = 0;
    private int chargeTimer = 0;
    private BlockPos currentPlacement = null;
    private BlockPos currentAnchor = null;

    public AutoAnchor() {
        super(VenomousAddon.CATEGORY, "auto-anchor", "Advanced automatic respawn anchor placer and activator.");
    }

    @Override
    public void onActivate() {
        this.placeTimer = 0;
        this.chargeTimer = 0;
        this.currentPlacement = null;
        this.currentAnchor = null;
    }

    @Override
    public void onDeactivate() {
        this.currentPlacement = null;
        this.currentAnchor = null;
        PreRotate.cancelAll();
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null || this.mc.level == null) {
            return;
        }

        // Update timers
        if (this.placeTimer > 0) this.placeTimer--;
        if (this.chargeTimer > 0) this.chargeTimer--;

        // Find targets
        List<net.minecraft.world.entity.LivingEntity> targets = this.findTargets();
        if (targets.isEmpty()) {
            PreRotate.cancelAll();
            return;
        }

        // Find nearby anchor
        BlockPos anchor = this.findNearbyAnchor();
        
        if (anchor == null) {
            // No anchor found, try to place one
            if (this.autoPlace.get() && this.placeTimer <= 0) {
                Optional<BlockPlacement> placement = this.pickBestPlacement(targets);
                if (placement.isPresent()) {
                    this.currentPlacement = placement.get().targetPos();
                    this.placeAnchor(placement.get());
                    this.placeTimer = this.placeDelay.get();
                    return;
                }
            }
            
            // Rotate to best placement for manual placement
            if (this.rotationMode.get() != RotationMode.Off && this.currentPlacement != null) {
                this.rotateToBlock(this.currentPlacement);
            }
            return;
        }

        // Anchor found
        this.currentAnchor = anchor;
        int charge = this.mc.level.getBlockState(anchor).getValue(RespawnAnchorBlock.CHARGE);
        boolean willDetonate = charge >= 4;

        // Check if anchor will damage any target
        if (!this.willDamageAnyTarget(anchor, targets)) {
            PreRotate.cancelAll();
            return;
        }

        // Place shield if about to detonate
        if (willDetonate && this.safeAnchor.get() && !this.isShielded(anchor) && this.chargeTimer <= 0) {
            BlockPos shieldPos = this.findShieldPosition(anchor);
            if (shieldPos != null) {
                this.placeShield(shieldPos);
                this.chargeTimer = this.chargeDelay.get();
                return;
            }
        }

        // Anti-suicide check
        if (willDetonate && this.antiSuicide.get()) {
            double selfDamage = this.calculateSelfDamage(anchor);
            if (selfDamage > this.maxSelfDamage.get()) {
                PreRotate.cancelAll();
                return;
            }
        }

        // Wall check
        if (!this.ignoreWalls.get() && this.isBehindWall(anchor)) {
            PreRotate.cancelAll();
            return;
        }

        // Click the anchor (charge or detonate)
        boolean activateClickOk = !this.activateOnClick.get() || this.mc.options.keyUse.isDown();
        if (activateClickOk && this.chargeTimer <= 0) {
            this.chargeAnchor(anchor);
            this.chargeTimer = this.chargeDelay.get();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onRender(Render3DEvent event) {
        if (this.renderBox.get() && this.mc.player != null) {
            Vec3 playerPos = this.mc.player.getPosition(1.0F);
            if (this.currentPlacement != null) {
                AABB box = new AABB(this.currentPlacement).move(-playerPos.x, -playerPos.y, -playerPos.z);
                event.renderer.box(box, this.color.get(), this.color.get(), meteordevelopment.meteorclient.renderer.ShapeMode.Both, 0);
            }
            if (this.currentAnchor != null) {
                AABB box = new AABB(this.currentAnchor).move(-playerPos.x, -playerPos.y, -playerPos.z);
                event.renderer.box(box, this.color.get(), this.color.get(), meteordevelopment.meteorclient.renderer.ShapeMode.Both, 0);
            }
        }
    }

    private List<net.minecraft.world.entity.LivingEntity> findTargets() {
        List<net.minecraft.world.entity.LivingEntity> targets = new ArrayList<>();
        double rangeSq = this.enemyRange.get() * this.enemyRange.get();

        for (net.minecraft.world.entity.Entity entity : this.mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, this.mc.player.getBoundingBox().inflate(this.enemyRange.get()))) {
            if (entity == this.mc.player) continue;
            if (!entity.isAlive()) continue;
            
            if (entity instanceof net.minecraft.world.entity.player.Player playerEntity) {
                if (!this.targetPlayers.get()) continue;
                // TODO: Check friends list
            } else {
                if (!this.targetAnimals.get()) continue;
            }

            if (this.mc.player.distanceToSqr(entity) <= rangeSq) {
                targets.add((net.minecraft.world.entity.LivingEntity) entity);
            }
        }

        // Sort by distance (closest first)
        targets.sort(Comparator.comparingDouble(e -> this.mc.player.distanceToSqr(e)));
        return targets;
    }

    private BlockPos findNearbyAnchor() {
        if (this.mc.player == null || this.mc.level == null) return null;
        
        BlockPos playerPos = this.mc.player.blockPosition();
        int rad = this.radius.get().intValue();

        for (int x = -rad; x <= rad; x++) {
            for (int y = -rad; y <= rad; y++) {
                for (int z = -rad; z <= rad; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    if (this.mc.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private Optional<BlockPlacement> pickBestPlacement(List<net.minecraft.world.entity.LivingEntity> targets) {
        BlockPlacement best = null;
        double bestDamage = 0;
        double bestDistSq = Double.MAX_VALUE;

        for (net.minecraft.world.entity.LivingEntity target : targets) {
            Optional<BlockPlacement> placement = this.findPlacement(target);
            if (placement.isEmpty()) continue;

            double damage = this.calculateDamage(target, placement.get().targetPos());
            if (damage < this.minDamage.get()) continue;

            if (this.antiSuicide.get()) {
                double selfDamage = this.calculateSelfDamage(placement.get().targetPos());
                if (selfDamage > this.maxSelfDamage.get()) continue;
            }

            double distSq = this.mc.player.distanceToSqr(target);
            if (damage > bestDamage || (damage == bestDamage && distSq < bestDistSq)) {
                bestDamage = damage;
                bestDistSq = distSq;
                best = placement.get();
            }
        }

        return Optional.ofNullable(best);
    }

    private Optional<BlockPlacement> findPlacement(net.minecraft.world.entity.LivingEntity target) {
        if (this.mc.player == null || this.mc.level == null) return Optional.empty();

        BlockPos playerPos = this.mc.player.blockPosition();
        double radiusSq = this.radius.get() * this.radius.get();
        BlockPlacement best = null;
        double maxDamage = 0;
        double minDist = Double.MAX_VALUE;

        int extent = (int) Math.ceil(this.radius.get());
        for (int x = -extent; x <= extent; x++) {
            for (int y = -extent; y <= extent; y++) {
                for (int z = -extent; z <= extent; z++) {
                    BlockPos anchorPos = playerPos.offset(x, y, z);
                    if (Vec3.atCenterOf(anchorPos).distanceToSqr(this.mc.player.position()) > radiusSq) continue;
                    if (!this.mc.level.getBlockState(anchorPos).canBeReplaced()) continue;

                    // Check if obstructed by players
                    AABB anchorBox = new AABB(anchorPos);
                    boolean obstructed = false;
                    for (net.minecraft.world.entity.player.Player p : this.mc.level.players()) {
                        if (p.getBoundingBox().intersects(anchorBox)) {
                            obstructed = true;
                            break;
                        }
                    }
                    if (obstructed) continue;

                    // Find placement face
                    BlockPos placementBlockPos = null;
                    Direction placementFace = null;
                    for (Direction d : Direction.values()) {
                        BlockPos neighbor = anchorPos.relative(d);
                        if (!this.mc.level.getBlockState(neighbor).isFaceSturdy(this.mc.level, neighbor, d)) continue;
                        if (d == Direction.DOWN) {
                            placementBlockPos = neighbor;
                            placementFace = Direction.UP;
                            break;
                        }
                        if (placementBlockPos == null) {
                            placementBlockPos = neighbor;
                            placementFace = d.getOpposite();
                        }
                    }
                    if (placementBlockPos == null) continue;
                    if (!this.ignoreWalls.get() && this.isBehindWall(placementBlockPos)) continue;

                    double damage = this.calculateDamage(target, anchorPos);
                    double distance = target.blockPosition().distSqr(anchorPos);

                    if (damage > maxDamage || (damage == maxDamage && distance < minDist)) {
                        maxDamage = damage;
                        minDist = distance;
                        best = new BlockPlacement(anchorPos, placementBlockPos, placementFace);
                    }
                }
            }
        }

        return Optional.ofNullable(best);
    }

    private double calculateDamage(net.minecraft.world.entity.LivingEntity target, net.minecraft.core.BlockPos anchorPos) {
        // Delegate to the shared, reference-matched curve instead of the old linear guess.
        // A fully detonated respawn anchor has power 5 and a blast radius of 10, and the damage
        // falls off quadratically with distance. Armor, resistance and protection are applied by
        // the server after this and are not part of the placement score.
        Vec3 explosionPos = Vec3.atCenterOf(anchorPos);
        double distance = target.position().distanceTo(explosionPos);
        return AnchorDamageMath.rawDamage(distance, AnchorDamageMath.RESPAWN_ANCHOR_POWER, 1.0D);
    }

    private double calculateSelfDamage(net.minecraft.core.BlockPos anchorPos) {
        if (this.mc.player == null) return 0;
        Vec3 explosionPos = Vec3.atCenterOf(anchorPos);
        double distance = this.mc.player.position().distanceTo(explosionPos);
        return AnchorDamageMath.rawDamage(distance, AnchorDamageMath.RESPAWN_ANCHOR_POWER, 1.0D);
    }

    private boolean willDamageAnyTarget(BlockPos anchorPos, List<net.minecraft.world.entity.LivingEntity> targets) {
        for (net.minecraft.world.entity.LivingEntity target : targets) {
            if (this.calculateDamage(target, anchorPos) >= this.minDamage.get()) {
                return true;
            }
        }
        return false;
    }

    private void placeAnchor(BlockPlacement placement) {
        if (this.mc.player == null || this.mc.player.connection == null) return;

        FindItemResult anchorItem = InvUtils.findInHotbar(Items.RESPAWN_ANCHOR);
        if (!anchorItem.found()) return;

        BlockPos placementPos = placement.targetPos();
        Direction face = placement.face();
        
        Vec3 targetVec = Vec3.atCenterOf(placementPos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        
        PreRotate.rotateThen(this, 
            Rotations.getYaw(targetVec), 
            Rotations.getPitch(targetVec), 
            () -> {
                BlockHitResult hit;
                if (this.useRaycast.get()) {
                    // Simple raycast check
                    hit = new BlockHitResult(targetVec, face, placementPos, false);
                } else {
                    hit = new BlockHitResult(targetVec, face, placementPos, false);
                }

                if (this.autoSwitch.get()) {
                    InvUtils.swap(anchorItem.slot(), true);
                }

                this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, hit);
                this.mc.player.swing(InteractionHand.MAIN_HAND);
                
                if (this.autoSwitch.get()) {
                    InvUtils.swapBack();
                }
            });
    }

    private void chargeAnchor(BlockPos anchorPos) {
        if (this.mc.player == null || this.mc.player.connection == null) return;

        Direction clickFace = this.getClosestDirection(anchorPos);
        if (clickFace == null) return;

        Vec3 hitPos = Vec3.atCenterOf(anchorPos).add(clickFace.getStepX() * 0.5, clickFace.getStepY() * 0.5, clickFace.getStepZ() * 0.5);
        
        PreRotate.rotateThen(this,
            Rotations.getYaw(hitPos),
            Rotations.getPitch(hitPos),
            () -> {
                FindItemResult glowstone = InvUtils.findInHotbar(Items.GLOWSTONE);
                if (!glowstone.found()) return;

                BlockHitResult hit = new BlockHitResult(hitPos, clickFace, anchorPos, false);

                if (this.autoSwitch.get()) {
                    InvUtils.swap(glowstone.slot(), true);
                }

                this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, hit);
                this.mc.player.swing(InteractionHand.MAIN_HAND);

                if (this.autoSwitch.get()) {
                    InvUtils.swapBack();
                }
            });
    }

    private Direction getClosestDirection(BlockPos pos) {
        if (this.mc.player == null) return null;
        Vec3 playerPos = this.mc.player.getEyePosition();
        Direction best = null;
        double bestDist = Double.MAX_VALUE;

        for (Direction d : Direction.values()) {
            BlockPos facePos = pos.relative(d);
            Vec3 faceVec = Vec3.atCenterOf(facePos).add(d.getStepX() * 0.5, d.getStepY() * 0.5, d.getStepZ() * 0.5);
            double dist = playerPos.distanceTo(faceVec);
            if (dist < bestDist) {
                bestDist = dist;
                best = d;
            }
        }
        return best;
    }

    private BlockPos findShieldPosition(BlockPos anchorPos) {
        if (this.mc.player == null || this.mc.level == null) return null;

        Vec3 from = this.mc.player.getBoundingBox().getCenter();
        Vec3 to = Vec3.atCenterOf(anchorPos);
        AABB playerBox = this.mc.player.getBoundingBox();

        // Simple: find first placeable block between player and anchor
        BlockPos best = null;
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos pos = anchorPos.offset(x, y, z);
                    if (pos.equals(anchorPos)) continue;
                    if (playerBox.intersects(new AABB(pos))) continue;
                    if (!this.mc.level.getBlockState(pos).canBeReplaced()) continue;
                    if (!this.hasPlaceableFace(pos)) continue;
                    
                    // Check if it's between player and anchor
                    if (from.distanceTo(Vec3.atCenterOf(pos)) < from.distanceTo(to)) {
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private boolean hasPlaceableFace(BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos neighbor = pos.relative(d);
            BlockState state = this.mc.level.getBlockState(neighbor);
            if (state.is(Blocks.RESPAWN_ANCHOR)) continue;
            if (state.isFaceSturdy(this.mc.level, neighbor, d.getOpposite())) return true;
        }
        return false;
    }

    private boolean isShielded(BlockPos anchorPos) {
        if (this.mc.player == null || this.mc.level == null) return false;
        
        Vec3 from = this.mc.player.getBoundingBox().getCenter();
        Vec3 to = Vec3.atCenterOf(anchorPos);
        AABB playerAABB = this.mc.player.getBoundingBox();

        // Raycast from player to anchor
        net.minecraft.world.phys.BlockHitResult result = this.mc.level.clip(
            new net.minecraft.world.level.ClipContext(
                from, to, 
                net.minecraft.world.level.ClipContext.Block.COLLIDER, 
                net.minecraft.world.level.ClipContext.Fluid.NONE, 
                this.mc.player
            )
        );
        
        return result.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && !result.getBlockPos().equals(anchorPos);
    }

    private boolean isBehindWall(BlockPos pos) {
        if (this.mc.player == null || this.mc.level == null) return false;
        
        Vec3 from = this.mc.player.getEyePosition();
        Vec3 to = Vec3.atCenterOf(pos);
        
        net.minecraft.world.phys.BlockHitResult result = this.mc.level.clip(
            new net.minecraft.world.level.ClipContext(
                from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                this.mc.player
            )
        );
        
        return result.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && !result.getBlockPos().equals(pos);
    }

    private void placeShield(BlockPos shieldPos) {
        if (this.mc.player == null || this.mc.player.connection == null) return;

        FindItemResult glowstone = InvUtils.findInHotbar(Items.GLOWSTONE);
        if (!glowstone.found()) return;

        Direction face = null;
        BlockPos placementPos = null;
        for (Direction d : Direction.values()) {
            BlockPos neighbor = shieldPos.relative(d);
            BlockState state = this.mc.level.getBlockState(neighbor);
            if (state.is(Blocks.RESPAWN_ANCHOR)) continue;
            if (!state.isFaceSturdy(this.mc.level, neighbor, d)) continue;
            if (d == Direction.DOWN) {
                placementPos = neighbor;
                face = Direction.UP;
                break;
            }
            if (placementPos == null) {
                placementPos = neighbor;
                face = d.getOpposite();
            }
        }
        if (placementPos == null || face == null) return;

        Vec3 targetVec = Vec3.atCenterOf(placementPos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);

        final Vec3 finalTargetVec = targetVec;
        final Direction finalFace = face;
        final BlockPos finalPlacementPos = placementPos;

        PreRotate.rotateThen(this,
            Rotations.getYaw(targetVec),
            Rotations.getPitch(targetVec),
            () -> {
                BlockHitResult hit = new BlockHitResult(finalTargetVec, finalFace, finalPlacementPos, false);
                
                if (this.autoSwitch.get()) {
                    InvUtils.swap(glowstone.slot(), true);
                }
                
                this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, hit);
                this.mc.player.swing(InteractionHand.MAIN_HAND);
                
                if (this.autoSwitch.get()) {
                    InvUtils.swapBack();
                }
            });
    }

    private void rotateToBlock(BlockPos pos) {
        if (this.mc.player == null) return;
        Vec3 target = Vec3.atCenterOf(pos);
        double yaw = Rotations.getYaw(target);
        double pitch = Rotations.getPitch(target);
        
        PreRotate.rotateThen(this, yaw, pitch, () -> {});
    }

    private record BlockPlacement(BlockPos targetPos, BlockPos placementPos, Direction face) {}

    public enum RotationMode {
        Off,
        Silent,
        Normal;
    }
}
