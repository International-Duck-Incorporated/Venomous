package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.level.block.state.BlockState;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.AimEase;
import net.venomous.meteoraddon.utils.RotationGcd;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;

/**
 * Mines blocks from the freecam viewpoint rather than the player's.
 *
 * <p>Freecam moves the camera without moving the player, so the client keeps reporting the
 * player's real position in movement packets and the server will reject any break that arrives
 * from somewhere else. The break is therefore walked to the target in the same increments
 * {@code v-click-tp} uses, each within the server's per-packet limit.</p>
 */
public class FreecamMining extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Double> range = this.sgGeneral
        .add(
            new DoubleSetting.Builder().name("range").description("Maximum range to mine blocks from freecam position.")
                .defaultValue(64.0)
                .min(4.0)
                .max(256.0)
                .sliderMax(128.0)
                .build()
        );
    private final Setting<Boolean> rotate = this.sgGeneral
        .add(
            new BoolSetting.Builder()
                .name("rotate")
                .description("Rotate towards the block before mining.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Integer> packetRepeats = this.sgGeneral
        .add(
            new IntSetting.Builder()
                .name("packet-repeats")
                .description("Break packets sent per block mined.")
                .defaultValue(1)
                .min(1)
                .max(5)
                .sliderMax(3)
                .build()
        );
    private final Setting<Integer> maxStep = this.sgGeneral
        .add(
            new IntSetting.Builder()
                .name("max-step-blocks")
                .description("Largest movement sent in one position packet. Vanilla rejects more than ~10.")
                .defaultValue(8)
                .min(1)
                .max(10)
                .sliderMax(10)
                .build()
        );
    private static final float AIM_STEP = 6.0F;
    private float aimVelocity = 0.0F;
    private BlockPos target = null;
    private boolean mining = false;

    public FreecamMining() {
        super(VenomousAddon.CATEGORY, "freecam-mining", "Allows mining blocks while freecam is active.");
    }

    @Override
    public void onActivate() {
        this.aimVelocity = 0.0F;
        this.target = null;
        this.mining = false;
    }

    @Override
    public void onDeactivate() {
        this.aimVelocity = 0.0F;
        this.target = null;
        this.mining = false;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null || this.mc.level == null) return;

        Freecam freecam = Modules.get() != null ? Modules.get().get(Freecam.class) : null;
        if (freecam == null || !freecam.isActive()) {
            this.target = null;
            this.mining = false;
            this.aimVelocity = 0.0F;
            return;
        }

        BlockPos aimed = this.aimedBlock(freecam);
        if (aimed == null) {
            this.target = null;
            this.mining = false;
            this.aimVelocity = 0.0F;
            return;
        }

        this.target = aimed;

        if ((Boolean)this.rotate.get()) {
            this.faceTowards(aimed);
        }

        if (!BlockUtils.canPlace(aimed) && !this.mc.level.getBlockState(aimed).isAir()) {
            this.breakBlock(aimed);
        }
    }

    /** The block the freecam is looking at, within range, or null. */
    private BlockPos aimedBlock(Freecam freecam) {
        double reach = this.isActive() ? (Double)this.range.get() : 4.5;
        double ex = freecam.pos.x;
        double ey = freecam.pos.y;
        double ez = freecam.pos.z;

        double dx = -Math.sin(Math.toRadians(freecam.yaw)) * Math.cos(Math.toRadians(freecam.pitch));
        double dy = -Math.sin(Math.toRadians(freecam.pitch));
        double dz = Math.cos(Math.toRadians(freecam.yaw)) * Math.cos(Math.toRadians(freecam.pitch));

        // March along the view ray in half-block steps and take the first solid block. Cheaper
        // and far more predictable than a full ray trace, and reach here is user-controlled.
        double step = 0.5;
        for (double distance = 0.0; distance <= reach; distance += step) {
            BlockPos pos = BlockPos.containing(ex + dx * distance, ey + dy * distance, ez + dz * distance);
            BlockState state = this.mc.level.getBlockState(pos);

            if (!state.isAir() && !state.getCollisionShape(this.mc.level, pos).isEmpty()) {
                return pos;
            }
        }

        return null;
    }

    /**
     * Eases the client camera towards the target so the break has a plausible view angle.
     *
     * <p>Uses the same shared easing and GCD helpers as scaffold, so the sweep is not a
     * constant-rate step and the deltas stay on the sensitivity grid.</p>
     */
    private void faceTowards(BlockPos pos) {
        double dx = pos.getX() + 0.5 - this.mc.player.getX();
        double dy = pos.getY() + 0.5 - (this.mc.player.getY() + this.mc.player.getEyeHeight());
        double dz = pos.getZ() + 0.5 - this.mc.player.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float)-Math.toDegrees(Math.atan2(dy, horizontal));

        float yawStep = AimEase.step(targetYaw - this.mc.player.getYRot(), AIM_STEP, this.aimVelocity, 5.0F, 0.0F);
        this.aimVelocity = AimEase.nextVelocity(targetYaw - this.mc.player.getYRot(), AIM_STEP, this.aimVelocity, 5.0F, 0.0F);
        float pitchStep = AimEase.step(targetPitch - this.mc.player.getXRot(), AIM_STEP, this.aimVelocity, 5.0F, 0.0F);

        double sens = this.mc.options.sensitivity().get();
        this.mc.player.setYRot(this.mc.player.getYRot()
            + RotationGcd.quantize(sens, yawStep));
        this.mc.player.setXRot(this.mc.player.getXRot()
            + RotationGcd.quantize(sens, pitchStep));
    }

    /**
     * Walks a break to the target in server-acceptable steps, then sends start/stop destroy.
     *
     * <p>A single position packet straight to a block 64 blocks away is rejected outright as
     * "moved too quickly", so the distance is covered in increments and each one is followed by
     * the break.</p>
     */
    private void breakBlock(BlockPos pos) {
        if (this.mc.player.connection == null) return;

        double startX = this.mc.player.getX();
        double startY = this.mc.player.getY();
        double startZ = this.mc.player.getZ();

        double endX = pos.getX() + 0.5;
        double endY = pos.getY() + 0.5;
        double endZ = pos.getZ() + 0.5;

        double dx = endX - startX;
        double dy = endY - startY;
        double dz = endZ - startZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        int hops = distance <= 0.0 ? 1 : (int)Math.ceil(distance / (Integer)this.maxStep.get());
        double sx = dx / hops;
        double sy = dy / hops;
        double sz = dz / hops;

        double x = startX;
        double y = startY;
        double z = startZ;

        for (int i = 1; i <= hops; i++) {
            x += sx;
            y += sy;
            z += sz;
            this.mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(
                x, y, z, this.mc.player.onGround(), false));
        }

        for (int repeat = 0; repeat < (Integer)this.packetRepeats.get(); repeat++) {
            this.mc.player.connection.send(new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, pos, Direction.UP));
            this.mc.player.connection.send(new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, pos, Direction.UP));
        }

        // Return to where we actually are, so the server's view of the player is not left
        // parked at the block we just mined.
        this.mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(
            startX, startY, startZ, this.mc.player.onGround(), false));
    }

    public boolean isActiveAndFreecamOn() {
        return this.isActive() && Modules.get() != null && Modules.get().isActive(Freecam.class);
    }

    public double getExtendedRange() {
        return this.isActive() ? (Double)this.range.get() : 4.5;
    }

    @Override
    public String getInfoString() {
        return this.target != null ? this.target.toShortString() : null;
    }
}
