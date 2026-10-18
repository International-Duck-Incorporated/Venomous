package net.venomous.meteoraddon.modules.combat;

import java.util.Random;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.McCompat;

public class KillAura extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgVulcan = this.settings.createGroup("Vulcan");
    private final SettingGroup sgAnticheat = this.settings.createGroup("GCD / Anticheat");

    private final Setting<Double> range = this.sgGeneral.add(
        new DoubleSetting.Builder()
            .name("range")
            .description("Attack range.")
            .defaultValue(3.8)
            .range(1.0, 6.0)
            .sliderMax(6.0)
            .build()
    );

    private final Setting<Boolean> players = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("players")
            .description("Target players.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> mobs = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("mobs")
            .description("Target mobs.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> animals = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("animals")
            .description("Target animals.")
            .defaultValue(false)
            .build()
    );

    private final Setting<KillAura.Technique> technique = this.sgGeneral.add(
        new EnumSetting.Builder<KillAura.Technique>()
            .name("technique")
            .description("Click timing pattern.")
            .defaultValue(KillAura.Technique.Stabilized)
            .build()
    );

    private final Setting<Integer> minCps = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("min-cps")
            .description("Min CPS.")
            .defaultValue(8)
            .range(1, 20)
            .sliderMax(15)
            .build()
    );

    private final Setting<Integer> maxCps = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("max-cps")
            .description("Max CPS.")
            .defaultValue(11)
            .range(1, 20)
            .sliderMax(15)
            .build()
    );

    private final Setting<Boolean> respectCooldown = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("respect-cooldown")
            .description("Respect attack cooldown (REQUIRED for Grim/NCP).")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> rotate = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("rotate")
            .description("Rotate to target.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> gcdFix = this.sgAnticheat.add(
        new BoolSetting.Builder()
            .name("gcd-fix")
            .description("Quantize rotation steps to match mouse sensitivity GCD (bypasses Vulcan/Polar/Grim).")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> rotationSpeed = this.sgGeneral.add(
        new DoubleSetting.Builder()
            .name("rotation-speed")
            .description("Rotation speed (deg/tick).")
            .defaultValue(120.0)
            .range(10.0, 360.0)
            .sliderMax(360.0)
            .visible(this.rotate::get)
            .build()
    );

    private final Setting<Double> rotationThreshold = this.sgGeneral.add(
        new DoubleSetting.Builder()
            .name("rotation-threshold")
            .description("Angle threshold to attack.")
            .defaultValue(30.0)
            .range(1.0, 180.0)
            .sliderMax(90.0)
            .visible(this.rotate::get)
            .build()
    );

    private final Setting<Boolean> autoBlock = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("auto-block")
            .description("Block while not attacking.")
            .defaultValue(false)
            .build()
    );

    private final Setting<KillAura.ACMode> acMode = this.sgGeneral.add(
        new EnumSetting.Builder<KillAura.ACMode>()
            .name("ac-mode")
            .description("Anticheat mode — tunes settings automatically.")
            .defaultValue(KillAura.ACMode.Grim)
            .build()
    );

    private final Setting<Boolean> vulcanSpeedRandomization = this.sgVulcan.add(
        new BoolSetting.Builder()
            .name("speed-randomization")
            .description("Add ±100ms randomization to attack timing (bypasses Vulcan AutoClicker).")
            .defaultValue(true)
            .visible(() -> this.acMode.get() == KillAura.ACMode.Vulcan)
            .build()
    );

    private final Setting<Integer> vulcanRandomizationMs = this.sgVulcan.add(
        new IntSetting.Builder()
            .name("randomization-ms")
            .description("±Milliseconds of attack timing randomization.")
            .defaultValue(100)
            .range(20, 300)
            .visible(() -> this.acMode.get() == KillAura.ACMode.Vulcan && this.vulcanSpeedRandomization.get())
            .build()
    );

    private int ticksUntilAttack = 0;
    private Entity target = null;
    private final Random random = new Random();

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
        if (this.mc.options != null && this.mc.options.keyUse != null && this.autoBlock.get()) {
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

        if (this.acMode.get() == KillAura.ACMode.Grim && !this.isBoatNearby()) {
            this.placeBoat();
        }

        this.target = this.findTarget();
        if (this.target == null) {
            if (this.autoBlock.get()) {
                this.mc.options.keyUse.setDown(false);
            }
        } else {
            if (this.rotate.get()) {
                this.rotateTo(this.target);
            }

            if (this.rotate.get()) {
                double angle = this.getAngleTo(this.target);
                double thresh = this.acMode.get() == KillAura.ACMode.Grim
                    ? Math.min(this.rotationThreshold.get(), 15.0)
                    : this.rotationThreshold.get();
                if (angle > thresh) {
                    if (this.autoBlock.get()) {
                        this.mc.options.keyUse.setDown(true);
                    }
                    return;
                }
            }

            this.ticksUntilAttack--;
            if (this.ticksUntilAttack <= 0) {
                this.doAttack();
                this.ticksUntilAttack = this.getNextAttackDelay();
            } else if (this.autoBlock.get()) {
                this.mc.options.keyUse.setDown(true);
            }
        }
    }

    private void doAttack() {
        if (this.mc.player != null && this.target != null && this.mc.gameMode != null) {
            float minStrength = this.acMode.get() != KillAura.ACMode.Grim && this.acMode.get() != KillAura.ACMode.NCP ? 0.9F : 1.0F;
            if (!this.respectCooldown.get() || !(this.mc.player.getAttackStrengthScale(0.0F) < minStrength)) {
                if (this.autoBlock.get()) {
                    this.mc.options.keyUse.setDown(false);
                }

                this.mc.gameMode.attack(this.mc.player, this.target);
                this.mc.player.swing(InteractionHand.MAIN_HAND);
                this.mc.player.resetAttackStrengthTicker();
            }
        }
    }

    private int getNextAttackDelay() {
        int min = this.minCps.get();
        int max = this.maxCps.get();
        int avg = (min + max) / 2;
        double base = 20.0 / Math.max(1, avg);
        if (this.acMode.get() == KillAura.ACMode.Vulcan) {
            base = 20.0 / Math.max(9, Math.min(12, avg));
        }

        double var = (this.random.nextDouble() - 0.5) * 2.0 * (max - min) / Math.max(1.0, avg);

        double interval = switch (this.technique.get()) {
            case Stabilized -> base + var;
            case Butterfly -> this.random.nextBoolean() ? 20.0 / Math.max(1, avg + 2) : 20.0 / Math.max(1, avg - 2);
            case NormalDistribution -> base + this.random.nextGaussian() * base * 0.25;
            case Efficient -> base;
            case Switch -> base + (this.random.nextDouble() - 0.5) * 2.0;
        };

        if (this.acMode.get() == KillAura.ACMode.Vulcan && this.vulcanSpeedRandomization.get()) {
            double msVariation = (this.random.nextDouble() - 0.5) * 2.0 * this.vulcanRandomizationMs.get();
            interval += msVariation / 50.0;
        }

        return Math.max(1, (int)Math.round(interval));
    }

    private Entity findTarget() {
        double effectiveRange = this.range.get();
        if (this.acMode.get() == KillAura.ACMode.Grim) {
            effectiveRange = Math.min(this.range.get(), 3.0 + (this.isBoatNearby() ? 0.2 : 0.0));
        } else if (this.acMode.get() == KillAura.ACMode.Vulcan) {
            effectiveRange = Math.min(this.range.get(), 3.2);
        } else if (this.acMode.get() == KillAura.ACMode.NCP) {
            effectiveRange = Math.min(this.range.get(), 3.1);
        }

        Entity closest = null;
        double closestDist = effectiveRange * effectiveRange;

        for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity != this.mc.player && entity.isAlive()) {
                if (entity instanceof Player p) {
                    if (p.isSpectator() || !Friends.get().shouldAttack(p) || McCompat.isTeammate(p) || !this.players.get()) {
                        continue;
                    }
                } else if (entity instanceof Animal) {
                    if (!this.animals.get()) {
                        continue;
                    }
                } else if (entity instanceof LivingEntity) {
                    if (!this.mobs.get()) {
                        continue;
                    }
                } else {
                    continue;
                }

                double dist = this.mc.player.distanceToSqr(entity);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = entity;
                }
            }
        }

        return closest;
    }

    private void rotateTo(Entity target) {
        double dx = target.getX() - this.mc.player.getX();
        double dy = target.getY() + target.getEyeHeight() * 0.8 - (this.mc.player.getY() + this.mc.player.getEyeHeight());
        double dz = target.getZ() - this.mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, dist)));
        float yawDiff = this.wrapDegrees(targetYaw - this.mc.player.getYRot());
        float pitchDiff = this.wrapDegrees(targetPitch - this.mc.player.getXRot());
        float maxTurn = this.acMode.get() == KillAura.ACMode.Grim ? 80.0F : this.rotationSpeed.get().floatValue();

        if (Math.abs(yawDiff) > maxTurn) {
            yawDiff = Math.signum(yawDiff) * maxTurn;
        }

        if (Math.abs(pitchDiff) > maxTurn) {
            pitchDiff = Math.signum(pitchDiff) * maxTurn;
        }

        if (this.gcdFix.get()) {
            yawDiff = this.applyGcd(yawDiff);
            pitchDiff = this.applyGcd(pitchDiff);
        }

        this.mc.player.setYRot(this.mc.player.getYRot() + yawDiff);
        this.mc.player.setXRot(this.mc.player.getXRot() + pitchDiff);
    }

    private float applyGcd(float delta) {
        if (this.mc.options == null) return delta;
        double sens = this.mc.options.sensitivity().get();
        double f = sens * 0.6 + 0.2;
        double gcd = f * f * f * 1.2;
        if (gcd <= 0.0001) return delta;
        return (float)(Math.round(delta / gcd) * gcd);
    }

    private double getAngleTo(Entity target) {
        double dx = target.getX() - this.mc.player.getX();
        double dy = target.getY() + target.getEyeHeight() - (this.mc.player.getY() + this.mc.player.getEyeHeight());
        double dz = target.getZ() - this.mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yawDiff = Math.abs(this.wrapDegrees((float)Math.toDegrees(Math.atan2(-dx, dz)) - this.mc.player.getYRot()));
        float pitchDiff = Math.abs(this.wrapDegrees((float)(-Math.toDegrees(Math.atan2(dy, dist))) - this.mc.player.getXRot()));
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

    private boolean isBoatNearby() {
        if (this.mc.level == null) {
            return false;
        }

        for (Entity e : this.mc.level.entitiesForRendering()) {
            if (e instanceof AbstractBoat && e != this.mc.player.getVehicle() && this.mc.player.distanceTo(e) < 2.5) {
                return true;
            }
        }

        return false;
    }

    private void placeBoat() {
        int slot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack s = this.mc.player.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem().toString().toLowerCase().contains("boat")) {
                slot = i;
                break;
            }
        }

        if (slot != -1) {
            int prev = this.mc.player.getInventory().getSelectedSlot();
            this.mc.player.getInventory().setSelectedSlot(slot);
            float pp = this.mc.player.getXRot();
            this.mc.player.setXRot(80.0F);
            this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
            this.mc.player.setXRot(pp);
            this.mc.player.getInventory().setSelectedSlot(prev);
        }
    }

    @Override
    public String getInfoString() {
        String extra = switch (this.acMode.get()) {
            case Grim -> this.isBoatNearby() ? " [Grim+Boat]" : " [Grim]";
            case Vulcan -> " [Vulcan]";
            case NCP -> " [NCP]";
            case WeakAC -> " [Weak]";
            default -> "";
        };
        return (this.target != null ? this.target.getName().getString() : null) + extra;
    }

    public enum ACMode {
        Grim,
        Vulcan,
        NCP,
        WeakAC,
        None;
    }

    public enum Technique {
        Stabilized,
        Butterfly,
        NormalDistribution,
        Efficient,
        Switch;
    }
}
