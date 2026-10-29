package net.venomous.meteoraddon.modules.combat;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.EntityPredict;
import net.venomous.meteoraddon.utils.PreRotate;
import net.venomous.meteoraddon.utils.RodAimMath;
import net.venomous.meteoraddon.utils.RotationGcd;
import net.venomous.meteoraddon.utils.ScaffoldAim;

public class BowAimbot extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgTarget = this.settings.createGroup("Targeting");
    private final SettingGroup sgRotation = this.settings.createGroup("Rotation");

    private final Setting<Double> fov = this.sgGeneral
        .add(new DoubleSetting.Builder()
            .name("fov")
            .description("The whole cone, in degrees, inside which a target is considered. 360 aims at anything.")
            .defaultValue(180.0)
            .range(30.0, 360.0)
            .sliderMax(360.0)
            .build());

    private final Setting<Double> distance = this.sgGeneral
        .add(new DoubleSetting.Builder()
            .name("distance")
            .description("How far away a target may be, in blocks.")
            .defaultValue(6.0)
            .range(3.0, 30.0)
            .sliderMax(30.0)
            .build());

    private final Setting<Integer> predictedTicks = this.sgGeneral
        .add(new IntSetting.Builder()
            .name("predicted-ticks")
            .description("How many ticks of the target's current motion to aim ahead of. Higher leads a running target more; 0 aims at where they stand.")
            .defaultValue(5)
            .range(0, 20)
            .sliderMax(20)
            .build());

    private final Setting<Boolean> aimInvis = this.sgGeneral
        .add(new BoolSetting.Builder()
            .name("aim-invis")
            .description("Also aim at invisible players.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> ignoreTeammates = this.sgGeneral
        .add(new BoolSetting.Builder()
            .name("ignore-teammates")
            .description("Never aim at players on your own team, judged by vanilla's ally check.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> aimPlayers = this.sgTarget
        .add(new BoolSetting.Builder()
            .name("aim-players")
            .description("Target players.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> aimAnimals = this.sgTarget
        .add(new BoolSetting.Builder()
            .name("aim-animals")
            .description("Target animals and mobs.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> aimMonsters = this.sgTarget
        .add(new BoolSetting.Builder()
            .name("aim-monsters")
            .description("Target hostile monsters.")
            .defaultValue(false)
            .build());

    private final Setting<BodyPart> bodyPart = this.sgTarget
        .add(new EnumSetting.Builder<BodyPart>()
            .name("body-part")
            .description("The part of the target's body to aim at.")
            .defaultValue(BodyPart.Chest)
            .build());

    private final Setting<TargetPriority> targetPriority = this.sgTarget
        .add(new EnumSetting.Builder<TargetPriority>()
            .name("target-priority")
            .description("How to pick which target to aim at.")
            .defaultValue(TargetPriority.Closest)
            .build());

    private final Setting<Boolean> fakeRotation = this.sgRotation
        .add(new BoolSetting.Builder()
            .name("fake-rotation")
            .description("Spoofs the client's rotation so that the player appears rotated on the server.")
            .defaultValue(false)
            .build());

    private LivingEntity target = null;
    private int currentTick = 0;

    public BowAimbot() {
        super(VenomousAddon.CATEGORY, "bow-aimbot", "Aims your bow/crossbow shot at the closest valid entity, leading their motion.");
    }

    @Override
    public void onActivate() {
        this.target = null;
        this.currentTick = 0;
    }

    @Override
    public void onDeactivate() {
        this.target = null;
        this.currentTick = 0;
        PreRotate.cancelAll();
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null || this.mc.level == null || this.mc.gui.screen() != null) {
            this.target = null;
            PreRotate.cancelAll();
            return;
        }

        ItemStack stack = this.mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem)) {
            this.target = null;
            PreRotate.cancelAll();
            return;
        }

        if (stack.getItem() instanceof BowItem) {
            if (!this.mc.options.keyUse.isDown() && !this.mc.player.isUsingItem()) {
                this.target = null;
                PreRotate.cancelAll();
                return;
            }
        } else if (stack.getItem() instanceof CrossbowItem) {
            if (!CrossbowItem.isCharged(stack)) {
                this.target = null;
                PreRotate.cancelAll();
                return;
            }
        }

        this.currentTick++;
        if (this.currentTick < 1) return; // Update every tick for now

        LivingEntity picked = this.findTarget();
        if (picked == null) {
            this.target = null;
            PreRotate.cancelAll();
            return;
        }

        this.target = picked;
        this.stageCast();
    }

    private void stageCast() {
        if (this.target == null) return;

        double[] predicted = EntityPredict.predict(
            this.target.getX(), this.target.getY(), this.target.getZ(),
            this.target.getDeltaMovement().x, this.target.getDeltaMovement().y, this.target.getDeltaMovement().z,
            this.target.onGround(), this.predictedTicks.get()
        );

        double aimY;
        if (this.bodyPart.get() == BodyPart.Eyes) {
            aimY = predicted[1] + this.target.getEyeHeight() - 0.1;
        } else if (this.bodyPart.get() == BodyPart.Head) {
            aimY = predicted[1] + this.target.getBbHeight() - 0.1;
        } else if (this.bodyPart.get() == BodyPart.Feet) {
            aimY = predicted[1];
        } else { // Chest
            aimY = predicted[1] + this.target.getBbHeight() * 0.5;
        }

        float yaw = RodAimMath.yawTo(predicted[0] - this.mc.player.getX(), predicted[2] - this.mc.player.getZ());
        float pitch = RodAimMath.pitchTo(
            this.mc.player.getX(), this.mc.player.getEyeY(), this.mc.player.getZ(),
            predicted[0], aimY, predicted[2]
        );

        double sens = this.mouseSensitivity();
        float serverYaw = Rotations.serverYaw;
        float serverPitch = Rotations.serverPitch;

        float wireYaw, wirePitch;
        if (this.fakeRotation.get()) {
            wireYaw = serverYaw + RotationGcd.quantize(sens, (float) RodAimMath.wrapDegrees(yaw - serverYaw));
            wirePitch = RotationGcd.quantizeFrom(sens, serverPitch, pitch);
        } else {
            wireYaw = yaw;
            wirePitch = pitch;
        }

        PreRotate.rotateThen(this, wireYaw, wirePitch, this::cast);
    }

    private void cast() {
        if (this.mc.player == null) return;
        if (!(this.mc.player.getMainHandItem().getItem() instanceof BowItem || this.mc.player.getMainHandItem().getItem() instanceof CrossbowItem)) return;
        
        // The item use is already being held down, just ensure we're still aiming correctly
        // The rotation has been sent via PreRotate, the actual shot is handled by vanilla
    }

    private LivingEntity findTarget() {
        double maxDistanceSq = this.distance.get() * this.distance.get();
        float cameraYaw = this.mc.player.getYRot();
        double eyeX = this.mc.player.getX();
        double eyeY = this.mc.player.getEyeY();
        double eyeZ = this.mc.player.getZ();

        LivingEntity best = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (LivingEntity candidate : this.mc.level.getEntitiesOfClass(LivingEntity.class, this.mc.player.getBoundingBox().inflate(this.distance.get()))) {
            if (candidate == this.mc.player) continue;
            if (candidate.isRemoved() || candidate.isDeadOrDying()) continue;

            // Check entity type filters
            if (!this.isValidTargetType(candidate)) continue;
            if (!this.aimInvis.get() && candidate.isInvisible()) continue;
            if (this.ignoreTeammates.get() && candidate instanceof Player player && this.mc.player.isAlliedTo(player)) continue;

            double distanceSq = candidate.distanceToSqr(this.mc.player);
            if (distanceSq > maxDistanceSq) continue;

            // Check FOV
            float targetYaw = RodAimMath.yawTo(candidate.getX() - eyeX, candidate.getZ() - eyeZ);
            if (!RodAimMath.withinFov(cameraYaw, targetYaw, this.fov.get())) continue;

            // Priority selection
            if (best == null || this.isBetterTarget(candidate, best, distanceSq)) {
                best = candidate;
                bestDistanceSq = distanceSq;
            }
        }

        return best;
    }

    private boolean isValidTargetType(LivingEntity entity) {
        if (entity instanceof Player) {
            return this.aimPlayers.get();
        }
        
        // Check if it's a monster (hostile) by checking if it's aggressive
        EntityType<?> type = entity.getType();
        String typeName = type.getDescriptionId().toLowerCase();
        
        // Check for common hostile mobs
        boolean isMonster = typeName.contains("zombie") || typeName.contains("skeleton") || 
            typeName.contains("creeper") || typeName.contains("spider") ||
            typeName.contains("enderman") || typeName.contains("witch") ||
            typeName.contains("pillager") || typeName.contains("vindicator") ||
            typeName.contains("ravager") || typeName.contains("hoglin") ||
            typeName.contains("zoglin") || typeName.contains("blaze") ||
            typeName.contains("ghast") || typeName.contains("slime") ||
            typeName.contains("magma_cube") || typeName.contains("phantom") ||
            typeName.contains("warden") || typeName.contains("breeze") ||
            typeName.contains("husk") || typeName.contains("stray") ||
            typeName.contains("drowned") || typeName.contains("vex") ||
            typeName.contains("evoker") || typeName.contains("illusioner") ||
            typeName.contains("shulker") || typeName.contains("silverfish") ||
            typeName.contains("endermite") || typeName.contains("guardian") ||
            typeName.contains("elder_guardian") || typeName.contains("wither") ||
            typeName.contains("dragon");
        
        if (isMonster) {
            return this.aimMonsters.get();
        }
        
        // Check for animals/peaceful mobs
        boolean isAnimal = typeName.contains("cow") || typeName.contains("pig") || 
            typeName.contains("sheep") || typeName.contains("chicken") ||
            typeName.contains("horse") || typeName.contains("donkey") ||
            typeName.contains("mule") || typeName.contains("llama") ||
            typeName.contains("trader_llama") || typeName.contains("mooshroom") ||
            typeName.contains("rabbit") || typeName.contains("fox") ||
            typeName.contains("wolf") || typeName.contains("cat") ||
            typeName.contains("ocelot") || typeName.contains("panda") ||
            typeName.contains("polar_bear") || typeName.contains("bee") ||
            typeName.contains("goat") || typeName.contains("camel") ||
            typeName.contains("sniffer") || typeName.contains("frog") ||
            typeName.contains("tadpole") || typeName.contains("axolotl") ||
            typeName.contains("dolphin") || typeName.contains("turtle") ||
            typeName.contains("cod") || typeName.contains("salmon") ||
            typeName.contains("tropical_fish") || typeName.contains("pufferfish") ||
            typeName.contains("squid") || typeName.contains("glow_squid");
        
        if (isAnimal) {
            return this.aimAnimals.get();
        }
        
        return false;
    }

    private boolean isBetterTarget(LivingEntity candidate, LivingEntity current, double candidateDistSq) {
        switch (this.targetPriority.get()) {
            case Closest:
                return candidateDistSq < current.distanceToSqr(this.mc.player);
            case LowestHealth:
                return candidate.getHealth() < current.getHealth();
            case HighestHealth:
                return candidate.getHealth() > current.getHealth();
            default:
                return candidateDistSq < current.distanceToSqr(this.mc.player);
        }
    }

    private double mouseSensitivity() {
        return this.mc.options == null
            ? 0.0D
            : ((Double) this.mc.options.sensitivity().get()).doubleValue();
    }

    public enum BodyPart {
        Eyes,
        Head,
        Chest,
        Feet;
    }

    public enum TargetPriority {
        Closest,
        LowestHealth,
        HighestHealth;
    }

    public enum RotationMode {
        Off,
        Silent,
        Normal;
    }

    @Override
    public String getInfoString() {
        return this.target != null ? this.target.getName().getString() : null;
    }
}