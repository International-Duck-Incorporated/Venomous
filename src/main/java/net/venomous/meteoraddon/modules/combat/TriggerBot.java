package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
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
import net.venomous.meteoraddon.utils.combat.CombatContext;
import net.venomous.meteoraddon.utils.combat.CrosshairScanner;
import net.venomous.meteoraddon.utils.combat.config.TriggerBotConfig;

import java.util.Set;
import java.util.HashSet;

/**
 * TriggerBot using shared CombatContext config and CrosshairScanner for raycasting.
 */
public class TriggerBot extends Module {

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgTarget = this.settings.createGroup("Targeting");
    private final SettingGroup sgBehavior = this.settings.createGroup("Behavior");

    private final TriggerBotConfig config = new TriggerBotConfig(sgGeneral, sgBehavior, sgTarget);

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
        if (this.config.triggerOnClick.get() && !this.mc.options.keyAttack.isDown()) {
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

        // Find target in crosshair using CrosshairScanner
        Entity ent = CrosshairScanner.raycastEntities(this.mc.player, this.mc.level, this.config.radius.get());

        if (ent != null) {
            if (!(ent instanceof LivingEntity living)) return;

            // Filter entity types
            if (!this.isValidTargetType(ent)) return;
            if (ent instanceof Player player) {
                if (!this.config.targetFriends.get() && Friends.get().isFriend(player)) return;
                if (this.config.ignoreNPCs.get() && this.isNPC(player)) return;
            }

            if (this.config.ignoreDead.get() && !living.isAlive()) return;
            if (this.config.ignoreInvisible.get() && living.isInvisible()) return;
            if (this.config.ignoreSleeping.get() && living.isSleeping()) return;

            // Check distance from hitbox
            AABB box = ent.getBoundingBox();
            double cx = Math.max(box.minX, Math.min(this.mc.player.getEyePosition().x, box.maxX));
            double cy = Math.max(box.minY, Math.min(this.mc.player.getEyePosition().y, box.maxY));
            double cz = Math.max(box.minZ, Math.min(this.mc.player.getEyePosition().z, box.maxZ));
            double dx = cx - this.mc.player.getEyePosition().x, dy = cy - this.mc.player.getEyePosition().y, dz = cz - this.mc.player.getEyePosition().z;
            double distSqr = dx * dx + dy * dy + dz * dz;

            if (distSqr > this.config.radius.get() * this.config.radius.get()) return;

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

        switch (this.config.targetEntities.get()) {
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
        int min = this.config.attackDelayMin.get();
        int max = this.config.attackDelayMax.get();
        return min + (long) (Math.random() * (max - min + 1));
    }

    public enum TargetEntities {
        Players,
        Living,
        All
    }

    @Override
    public String getInfoString() {
        return this.pendingAttackTarget != null ? this.pendingAttackTarget.getName().getString() : null;
    }
}