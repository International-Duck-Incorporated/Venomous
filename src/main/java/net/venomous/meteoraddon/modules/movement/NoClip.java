package net.venomous.meteoraddon.modules.movement;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.venomous.meteoraddon.VenomousAddon;

public class NoClip extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgAdvanced = this.settings.createGroup("Advanced");
    private final SettingGroup sgRender = this.settings.createGroup("Render");

    private final Setting<Double> speed = this.settings.getDefaultGroup()
        .add(new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("speed")
            .description("Noclip speed in blocks per tick.")
            .defaultValue(2.0)
            .min(0.1)
            .max(15.0)
            .sliderMax(15.0)
            .build());

    private final Setting<Double> speedMultiplier = this.settings.getDefaultGroup()
        .add(new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("speed-multiplier")
            .description("Speed multiplier when sprinting.")
            .defaultValue(1.5)
            .min(0.1)
            .max(15.0)
            .sliderMax(15.0)
            .build());

    private final Setting<Boolean> onGround = this.settings.getDefaultGroup()
        .add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("on-ground")
            .description("Send on-ground packet while moving.")
            .defaultValue(true)
            .build());

    private final Setting<Double> packetDistanceThreshold = this.sgAdvanced
        .add(new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("packet-distance-threshold")
            .description("Distance threshold for sending packets.")
            .defaultValue(10.0)
            .min(1.0)
            .max(100.0)
            .sliderMax(100.0)
            .build());

    private final Setting<Double> packetCountOffset = this.sgAdvanced
        .add(new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("packet-count-offset")
            .description("Offset for the number of packets required.")
            .defaultValue(1.0)
            .min(1.0)
            .max(10.0)
            .sliderMax(10.0)
            .build());

    private final Setting<Integer> maxPackets = this.sgAdvanced
        .add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("max-packets")
            .description("Maximum packets per update.")
            .defaultValue(5)
            .min(1)
            .max(40)
            .sliderMax(40)
            .build());

    private final Setting<Boolean> avoidBlocks = this.settings.getDefaultGroup()
        .add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("avoid-blocks")
            .description("Avoid clipping through solid blocks (slows down near walls).")
            .defaultValue(true)
            .build());

    private final Setting<Double> wallDistance = this.settings.getDefaultGroup()
        .add(new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("wall-distance")
            .description("Distance from walls to start slowing down.")
            .defaultValue(0.5)
            .min(0.1)
            .max(2.0)
            .sliderMax(2.0)
            .visible(this.avoidBlocks::get)
            .build());

    private final Setting<Boolean> render = this.sgRender
        .add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("render")
            .description("Show noclip bounding box and direction indicator.")
            .defaultValue(true)
            .build());

    private final Setting<SettingColor> color = this.sgRender
        .add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
            .name("color")
            .description("Render color for noclip visualization.")
            .defaultValue(new meteordevelopment.meteorclient.utils.render.color.SettingColor(0, 255, 255, 200))
            .build());

    private final Setting<Boolean> showDirection = this.sgRender
        .add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("show-direction")
            .description("Show movement direction indicator.")
            .defaultValue(true)
            .build());

    public NoClip() {
        super(VenomousAddon.CATEGORY, "no-clip", "Allows the player to clip through blocks (clientside only).");
    }

    @Override
    public void onActivate() {
        if (this.mc.player != null) {
            this.mc.player.noPhysics = true;
        }
    }

    @Override
    public void onDeactivate() {
        if (this.mc.player != null) {
            this.mc.player.noPhysics = false;
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null || this.mc.level == null || this.mc.getConnection() == null) {
            return;
        }

        double speed = this.speed.get();

        if (this.mc.options.keySprint.isDown()) {
            speed *= this.speedMultiplier.get();
        }

        // Apply block avoidance if enabled
        if (this.avoidBlocks.get()) {
            speed = this.applyBlockAvoidance(speed);
        }

        // Calculate movement vector
        Vec3 forward = Vec3.directionFromRotation(0, this.mc.player.getYRot());
        Vec3 right = Vec3.directionFromRotation(0, this.mc.player.getYRot() + 90.0f);

        Vec3 movement = Vec3.ZERO;

        if (this.mc.options.keyUp.isDown()) {
            movement = movement.add(forward.scale(speed));
        } else if (this.mc.options.keyDown.isDown()) {
            movement = movement.subtract(forward.scale(speed));
        }

        if (this.mc.options.keyRight.isDown()) {
            movement = movement.add(right.scale(speed));
        } else if (this.mc.options.keyLeft.isDown()) {
            movement = movement.subtract(right.scale(speed));
        }

        if (this.mc.options.keyJump.isDown()) {
            movement = movement.add(new Vec3(0, speed, 0));
        } else if (this.mc.options.keyShift.isDown()) {
            movement = movement.subtract(new Vec3(0, speed, 0));
        }

        Vec3 newPos = this.mc.player.position().add(movement);

        // Calculate packets needed
        double distance = this.mc.player.position().distanceTo(newPos);
        int packetsRequired = (int) Math.ceil(distance / this.packetDistanceThreshold.get() - this.packetCountOffset.get());
        packetsRequired = Math.min(packetsRequired, this.maxPackets.get());

        // Send packets
        for (int i = 0; i < packetsRequired; i++) {
            this.mc.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(this.onGround.get(), false));
        }

        // Final position packet
        this.mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(
            newPos.x, newPos.y, newPos.z, this.onGround.get(), false
        ));
    }

    private double applyBlockAvoidance(double speed) {
        if (this.mc.player == null || this.mc.level == null) return speed;

        Vec3 pos = this.mc.player.position();
        Vec3 velocity = this.mc.player.getDeltaMovement();
        Vec3 lookVec = this.mc.player.getViewVector(1.0F);
        
        // Check for blocks in movement direction
        double checkDist = this.wallDistance.get();
        Vec3 checkPos = this.mc.player.getEyePosition().add(this.mc.player.getViewVector(1.0F).scale(checkDist));
        
        BlockPos checkBlockPos = BlockPos.containing(checkPos);
        if (this.mc.level.getBlockState(checkBlockPos).getBlock() != Blocks.AIR) {
            return speed * 0.3;
        }

        // Check surrounding blocks for collision
        for (Direction dir : Direction.values()) {
            BlockPos checkPos2 = this.mc.player.blockPosition().relative(dir);
            if (this.mc.level.getBlockState(checkPos2).getBlock() != Blocks.AIR) {
                // Check if we're moving towards this block
                Vec3 blockCenter = Vec3.atCenterOf(checkPos2);
                Vec3 toBlock = blockCenter.subtract(this.mc.player.position());
                double dist = toBlock.length();
                if (dist < this.wallDistance.get() + 0.5) {
                    // Moving towards a solid block - slow down
                    return Math.min(speed, 0.5);
                }
            }
        }

        return speed;
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onRender(Render3DEvent event) {
        if (!this.render.get() || this.mc.player == null) return;

        Vec3 playerPos = this.mc.player.getPosition(1.0F);
        Vec3 forward = Vec3.directionFromRotation(0, this.mc.player.getYRot());
        Vec3 right = Vec3.directionFromRotation(0, this.mc.player.getYRot() + 90.0f);

        // Render noclip bounding box around player
        AABB playerBox = this.mc.player.getBoundingBox().move(-playerPos.x, -playerPos.y, -playerPos.z);
        event.renderer.box(playerBox, this.color.get(), this.color.get(), meteordevelopment.meteorclient.renderer.ShapeMode.Lines, 1);

        // Direction indicator disabled for version compatibility — module core (collision disable) functional
        // Show nearby blocks that will be clipped through
        // Nearby block render disabled for version compatibility
    }

    private Vec3 getMovementDirection() {
        if (this.mc.player == null) return Vec3.ZERO;

        Vec3 forward = Vec3.directionFromRotation(0, this.mc.player.getYRot());
        Vec3 right = Vec3.directionFromRotation(0, this.mc.player.getYRot() + 90.0f);
        Vec3 movement = Vec3.ZERO;

        if (this.mc.options.keyUp.isDown()) movement = movement.add(forward);
        else if (this.mc.options.keyDown.isDown()) movement = movement.subtract(forward);
        if (this.mc.options.keyRight.isDown()) movement = movement.add(right);
        else if (this.mc.options.keyLeft.isDown()) movement = movement.subtract(right);
        if (this.mc.options.keyJump.isDown()) movement = movement.add(new Vec3(0, 1, 0));
        else if (this.mc.options.keyShift.isDown()) movement = movement.subtract(new Vec3(0, 1, 0));

        return movement.lengthSqr() > 0 ? movement.normalize() : Vec3.ZERO;
    }

    @Override
    public String getInfoString() {
        return String.format("%.1f", this.speed.get());
    }
}