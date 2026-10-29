package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.venomous.meteoraddon.VenomousAddon;

import java.util.Set;
import java.util.HashSet;

public class TriggerBot extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgTarget = this.settings.createGroup("Targeting");
    private final SettingGroup sgBehavior = this.settings.createGroup("Behavior");

    private final Setting<Double> radius = this.sgGeneral
        .add(new DoubleSetting.Builder()
            .name("radius")
            .description("Radius that TriggerBot will trigger.")
            .defaultValue(3.0)
            .min(0.1)
            .max(10.0)
            .sliderMax(10.0)
            .build());

    private final Setting<Boolean> triggerOnClick = this.sgBehavior
        .add(new BoolSetting.Builder()
            .name("trigger-on-click")
            .description("Only attack while the attack key is held down.")
            .defaultValue(false)
            .build());

    private final Setting<Integer> attackDelayMin = this.sgBehavior
        .add(new IntSetting.Builder()
            .name("attack-delay-min")
            .description("Minimum delay between attacks in milliseconds.")
            .defaultValue(50)
            .min(0)
            .max(1000)
            .sliderMax(1000)
            .build());

    private final Setting<Integer> attackDelayMax = this.sgBehavior
        .add(new IntSetting.Builder()
            .name("attack-delay-max")
            .description("Maximum delay between attacks in milliseconds.")
            .defaultValue(150)
            .min(0)
            .max(1000)
            .sliderMax(1000)
            .build());

    private final Setting<TargetEntities> targetEntities = this.sgTarget
        .add(new EnumSetting.Builder<TargetEntities>()
            .name("target-entities")
            .description("Entity types that TriggerBot will target.")
            .defaultValue(TargetEntities.Players)
            .build());

    private final Setting<Boolean> targetFriends = this.sgTarget
        .add(new BoolSetting.Builder()
            .name("target-friends")
            .description("Target friends.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> ignoreDead = this.sgTarget
        .add(new BoolSetting.Builder()
            .name("ignore-dead")
            .description("Skip entities that are dead or dying.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> ignoreInvisible = this.sgTarget
        .add(new BoolSetting.Builder()
            .name("ignore-invisible")
            .description("Skip entities that are invisible.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> ignoreSleeping = this.sgTarget
        .add(new BoolSetting.Builder()
            .name("ignore-sleeping")
            .description("Skip players that are sleeping.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> ignoreNPCs = this.sgTarget
        .add(new BoolSetting.Builder()
            .name("ignore-npcs")
            .description("Attempt to ignore NPCs based on entity UUID.")
            .defaultValue(true)
            .build());

    private long timeSinceAttackMs = 0;
    private long nextAttackDelayMs = 0;
    private Entity pendingAttackTarget = null;

    public TriggerBot() {
        super(VenomousAddon.CATEGORY, "trigger-bot", "Attacks anything you are looking at.");
    }

    @Override
    public void onActivate() {
        this.timeSinceAttackMs = 0;
        this.nextAttackDelayMs = this.getRandomDelay();
        this.pendingAttackTarget = null;
    }

    @Override
    public void onDeactivate() {
        this.pendingAttackTarget = null;
        this.timeSinceAttackMs = 0;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null || this.mc.level == null) {
            return;
        }

        // Update attack timer
        this.timeSinceAttackMs += 50; // ~50ms per tick

        // Handle trigger on click
        if (this.triggerOnClick.get() && !this.mc.options.keyAttack.isDown()) {
            this.pendingAttackTarget = null;
            return;
        }

        // Check attack cooldown
        if (this.timeSinceAttackMs < this.nextAttackDelayMs) {
            return;
        }

        // Check attack strength (full charge)
        if (this.mc.player.getAttackStrengthScale(1.0f) < 1.0f) {
            return;
        }

        // Find target in crosshair
        double reach = this.radius.get();
        Vec3 eyePos = this.mc.player.getEyePosition();
        Vec3 lookVector = this.mc.player.getViewVector(1.0F);
        Vec3 lookEndPos = eyePos.add(lookVector.x * reach, lookVector.y * reach, lookVector.z * reach);

        // Check for block in the way
        BlockHitResult blockHit = this.mc.level.clip(
            new ClipContext(
                eyePos, lookEndPos, 
                ClipContext.Block.COLLIDER, 
                ClipContext.Fluid.NONE, 
                this.mc.player
            )
        );
        if (blockHit.getType() != HitResult.Type.MISS) {
            lookEndPos = blockHit.getLocation();
        }

        // Raycast for entities
        double rayDistSqr = eyePos.distanceToSqr(lookEndPos);
        AABB hitcastSearchBox = this.mc.player.getBoundingBox().expandTowards(lookVector.scale(reach)).inflate(1.0, 1.0, 1.0);
        
        EntityHitResult entityResult = ProjectileUtil.getEntityHitResult(
            this.mc.player, eyePos, lookEndPos, hitcastSearchBox,
            e -> !e.isSpectator() && e.isPickable() && e != this.mc.player, rayDistSqr
        );

        if (entityResult != null) {
            Entity ent = entityResult.getEntity();
            if (!(ent instanceof LivingEntity living)) return;

            // Filter entity types
            if (!this.isValidTargetType(ent)) return;
            if (ent instanceof Player player) {
                if (!this.targetFriends.get() && Friends.get().isFriend(player)) return;
                if (this.ignoreNPCs.get() && this.isNPC(player)) return;
            }

            if (this.ignoreDead.get() && !living.isAlive()) return;
            if (this.ignoreInvisible.get() && living.isInvisible()) return;
            if (this.ignoreSleeping.get() && living.isSleeping()) return;

            // Check distance from hitbox
            AABB box = ent.getBoundingBox();
            double cx = Math.max(box.minX, Math.min(eyePos.x, box.maxX));
            double cy = Math.max(box.minY, Math.min(eyePos.y, box.maxY));
            double cz = Math.max(box.minZ, Math.min(eyePos.z, box.maxZ));
            double dx = cx - eyePos.x, dy = cy - eyePos.y, dz = cz - eyePos.z;
            double distSqr = dx * dx + dy * dy + dz * dz;

            if (distSqr > this.radius.get() * this.radius.get()) return;

            this.pendingAttackTarget = ent;
        }

        // Execute attack if we have a pending target
        if (this.pendingAttackTarget != null) {
            Entity target = this.pendingAttackTarget;
            this.pendingAttackTarget = null;
            
            if (target == null || target.isRemoved() || !target.isAlive()) return;

            this.attack(target);
            this.timeSinceAttackMs = 0;
            this.nextAttackDelayMs = this.getRandomDelay();
        }
    }

    private boolean isValidTargetType(Entity entity) {
        EntityType<?> type = entity.getType();
        
        switch (this.targetEntities.get()) {
            case Players:
                return entity instanceof Player;
            case Living:
                return entity instanceof LivingEntity;
            case All:
                return entity.isPickable();
            default:
                return false;
        }
    }

    private boolean isNPC(Player player) {
        // Simple NPC detection based on UUID
        // NPCs often have fixed/nil UUIDs
        return player.getUUID().toString().equals("00000000-0000-0000-0000-000000000000");
    }

    private void attack(Entity target) {
        if (this.mc.player == null || this.mc.level == null) return;
        
        // Use vanilla attack logic
        this.mc.gameMode.attack(this.mc.player, target);
        this.mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    private long getRandomDelay() {
        int min = this.attackDelayMin.get();
        int max = this.attackDelayMax.get();
        return min + (long) (Math.random() * (max - min + 1));
    }

    public enum TargetEntities {
        Players,
        Living,
        All;
    }

    @Override
    public String getInfoString() {
        return this.pendingAttackTarget != null ? this.pendingAttackTarget.getName().getString() : null;
    }
}