package net.venomous.meteoraddon.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

public class NoClip extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgAdvanced = this.settings.createGroup("Advanced");

    private final Setting<Double> speed = this.sgGeneral
        .add(new DoubleSetting.Builder()
            .name("speed")
            .description("Noclip speed in blocks per tick.")
            .defaultValue(2.0)
            .min(0.1)
            .max(15.0)
            .sliderMax(15.0)
            .build());

    private final Setting<Double> speedMultiplier = this.sgGeneral
        .add(new DoubleSetting.Builder()
            .name("speed-multiplier")
            .description("Speed multiplier when sprinting.")
            .defaultValue(1.5)
            .min(0.1)
            .max(15.0)
            .sliderMax(15.0)
            .build());

    private final Setting<Boolean> onGround = this.sgGeneral
        .add(new BoolSetting.Builder()
            .name("on-ground")
            .description("Send on-ground packet while moving.")
            .defaultValue(true)
            .build());

    private final Setting<Double> packetDistanceThreshold = this.sgAdvanced
        .add(new DoubleSetting.Builder()
            .name("packet-distance-threshold")
            .description("Distance threshold for sending packets.")
            .defaultValue(10.0)
            .min(1.0)
            .max(100.0)
            .sliderMax(100.0)
            .build());

    private final Setting<Double> packetCountOffset = this.sgAdvanced
        .add(new DoubleSetting.Builder()
            .name("packet-count-offset")
            .description("Offset for the number of packets required.")
            .defaultValue(1.0)
            .min(1.0)
            .max(10.0)
            .sliderMax(10.0)
            .build());

    private final Setting<Integer> maxPackets = this.sgAdvanced
        .add(new IntSetting.Builder()
            .name("max-packets")
            .description("Maximum packets per update.")
            .defaultValue(5)
            .min(1)
            .max(40)
            .sliderMax(40)
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
        this.mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
            newPos.x, newPos.y, newPos.z, this.onGround.get(), false
        ));
    }

    @Override
    public String getInfoString() {
        return String.format("%.1f", this.speed.get());
    }
}