package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.EnumSetting;

public class AntiProjectile extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<AntiProjectile.Mode> mode = this.sgGeneral
        .add(
            new EnumSetting.Builder<AntiProjectile.Mode>().name("mode").description("Walk dodges by moving WASD, Velocity flings you out of the way.")
                .defaultValue(AntiProjectile.Mode.Walk)
                .build()
        );
    private final Setting<Double> range = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("range")
                .description("Projectile detection range.")
                .defaultValue(14.0)
                .min(2.0)
                .max(64.0)
                .build()
        );
    private final Setting<Double> dodgePower = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("dodge-power")
                .description("Dodge strength.")
                .defaultValue(0.45)
                .min(0.05)
                .max(5.0)
                .build()
        );
    private int walkTimer;
    private boolean managedKeys;
    private boolean previousLeftDown;
    private boolean previousRightDown;

    public AntiProjectile() {
        super(VenomousAddon.CATEGORY, "anti-projectile", "Detects incoming projectiles and dodges them.");
    }

    public void onDeactivate() {
        this.stopDodge();
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null && this.mc.level != null) {
            Entity projectile = this.nearestIncomingProjectile();
            if (projectile != null) {
                if (this.mode.get() == AntiProjectile.Mode.Velocity) {
                    this.velocityDodge(projectile);
                } else if (this.mode.get() == AntiProjectile.Mode.Walk && this.walkTimer <= 0) {
                    this.startWalkDodge(projectile);
                }
            }

            if (this.walkTimer > 0) {
                this.walkTimer--;
                if (this.walkTimer <= 0) {
                    this.stopDodge();
                }
            }
        }
    }

    private void velocityDodge(Entity projectile) {
        Vec3 velocity = projectile.getDeltaMovement();
        Vec3 side = new Vec3(-velocity.z, 0.0, velocity.x).normalize().scale((Double)this.dodgePower.get());
        this.mc.player.addDeltaMovement(new Vec3(side.x, 0.0, side.z));
    }

    private void startWalkDodge(Entity projectile) {
        if (!this.managedKeys) {
            this.previousLeftDown = this.mc.options.keyLeft.isDown();
            this.previousRightDown = this.mc.options.keyRight.isDown();
            this.managedKeys = true;
        }

        Vec3 velocity = projectile.getDeltaMovement();
        Vec3 toPlayer = this.mc.player.position().subtract(projectile.position());
        double dot = -velocity.z * toPlayer.x + velocity.x * toPlayer.z;
        if (dot > 0.0) {
            this.mc.options.keyLeft.setDown(true);
        } else {
            this.mc.options.keyRight.setDown(true);
        }

        this.walkTimer = 10;
    }

    private void stopDodge() {
        if (this.managedKeys && this.mc.options != null) {
            this.mc.options.keyLeft.setDown(this.previousLeftDown);
            this.mc.options.keyRight.setDown(this.previousRightDown);
        }
        this.managedKeys = false;
        this.previousLeftDown = false;
        this.previousRightDown = false;
        this.walkTimer = 0;
    }

    private Entity nearestIncomingProjectile() {
        double maxSq = (Double)this.range.get() * (Double)this.range.get();
        Entity best = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof Projectile p && p.getOwner() != this.mc.player) {
                double dist = this.mc.player.distanceToSqr(entity);
                if (dist <= maxSq && dist < bestDist && this.isIncoming(entity)) {
                    best = entity;
                    bestDist = dist;
                }
            }
        }

        return best;
    }

    private boolean isIncoming(Entity projectile) {
        Vec3 velocity = projectile.getDeltaMovement();
        if (velocity.lengthSqr() < 1.0E-4) {
            return false;
        }

        Vec3 toPlayer = this.mc.player.position().add(0.0, 0.9, 0.0).subtract(projectile.position()).normalize();
        return velocity.normalize().dot(toPlayer) > 0.65;
    }

    public enum Mode {
        Walk,
        Velocity;
    }
}
