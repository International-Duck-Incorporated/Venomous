package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.meteor.MouseClickEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.modules.misc.AntiBot;
import net.venomous.meteoraddon.utils.EntityPredict;
import net.venomous.meteoraddon.utils.PreRotate;
import net.venomous.meteoraddon.utils.RodAimMath;
import net.venomous.meteoraddon.utils.RotationGcd;

/**
 * Aims the fishing rod cast for you.
 *
 * <p>A right click with an uncast rod is intercepted, the closest valid target inside the cone
 * is picked, and the cast is staged against where they will <em>be</em> when the hook arrives -
 * the predicted-ticks slider decides how far ahead that is. The rotation leaves through
 * {@link PreRotate}, so the interaction departs before the movement packet, from a rotation the
 * server has actually acknowledged, and both axes are snapped onto the sensitivity grid with
 * {@link RotationGcd#quantizeFrom} - the same machinery the scaffold runs on, because a cast
 * rotation no mouse could have produced is the exact signature the rotation checks read.</p>
 *
 * <p>The camera is never taken. The player keeps their view and their mouse; only the rotation
 * the server is told changes, and it is released the tick the cast lands.</p>
 */
public class RodAimbot extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> fov = this.sgGeneral.add(
        new DoubleSetting.Builder()
            .name("fov")
            .description("The whole cone, in degrees, inside which a target is considered. 360 aims at anything.")
            .defaultValue(180.0)
            .range(30.0, 360.0)
            .sliderMax(360.0)
            .build()
    );

    private final Setting<Integer> predictedTicks = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("predicted-ticks")
            .description("How many ticks of the target's current motion to aim ahead of. Higher leads a running target more; 0 aims at where they stand.")
            .defaultValue(5)
            .range(0, 20)
            .sliderMax(20)
            .build()
    );

    private final Setting<Double> distance = this.sgGeneral.add(
        new DoubleSetting.Builder()
            .name("distance")
            .description("How far away a target may be, in blocks.")
            .defaultValue(6.0)
            .range(3.0, 30.0)
            .sliderMax(30.0)
            .build()
    );

    private final Setting<Boolean> aimInvis = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("aim-invis")
            .description("Also aim at invisible players.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> ignoreTeammates = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("ignore-teammates")
            .description("Never aim at players on your own team, judged by vanilla's ally check.")
            .defaultValue(false)
            .build()
    );

    /** The target the current staged cast is for; null whenever nothing is being aimed at. */
    private Player target;

    public RodAimbot() {
        super(VenomousAddon.CATEGORY, "rod-aimbot", "Aims your fishing rod cast at the closest valid player, leading their motion.");
    }

    @Override
    public void onActivate() {
        this.target = null;
    }

    @Override
    public void onDeactivate() {
        this.target = null;
    }

    /**
     * Intercepts the right click that would cast the rod.
     *
     * <p>The click is cancelled only when a target is actually picked - otherwise the cast is
     * the player's own, exactly as they made it. An already-cast rod is left alone too: the
     * second click reels in, and reeling in is aimed by where the hook already is, not by where
     * the target will be.</p>
     */
    @EventHandler
    private void onMouseClick(MouseClickEvent event) {
        if (event.action != KeyAction.Press || event.button() != 1) return;
        if (this.mc.player == null || this.mc.level == null || this.mc.gui.screen() != null) return;
        if (!(this.mc.player.getMainHandItem().getItem() instanceof FishingRodItem)) return;
        if (this.mc.player.fishing != null) return;

        Player picked = this.findTarget();
        if (picked == null) return;

        this.target = picked;
        event.cancel();
        this.stageCast();
    }

    /**
     * Keeps the staged cast fresh while the rod is out of the water.
     *
     * <p>A moving target moves the aim: staging once and waiting would cast at an angle computed
     * for a position the target has already left. So every tick between the click and the cast,
     * the rotation is re-staged against a fresh prediction - and because a re-stage supersedes
     * the previous one on {@link PreRotate}, only the newest aim can ever fire.</p>
     */
    @EventHandler
    private void onTick(Pre event) {
        if (this.target == null) return;

        if (this.mc.player == null || this.mc.level == null
            || this.mc.player.fishing != null
            || this.target.isRemoved() || this.target.isDeadOrDying()) {
            this.target = null;
            return;
        }

        this.stageCast();
    }

    /** Computes the predicted, grid-snapped rotation and hands it to Meteor for the cast. */
    private void stageCast() {
        Player player = this.mc.player;
        double[] predicted = EntityPredict.predict(
            this.target.getX(), this.target.getY(), this.target.getZ(),
            this.target.getDeltaMovement().x, this.target.getDeltaMovement().y, this.target.getDeltaMovement().z,
            this.target.onGround(), this.predictedTicks.get());

        // The chest, not the feet: the hook needs to overlap the body to register the punch.
        double aimY = predicted[1] + 0.9;

        float yaw = RodAimMath.yawTo(predicted[0] - player.getX(), predicted[2] - player.getZ());
        float pitch = RodAimMath.pitchTo(player.getX(), player.getEyeY(), player.getZ(),
            predicted[0], aimY, predicted[2]);

        // Both axes are ours here - not the camera's - so both need the grid. The step from
        // whatever the server holds to the angle we want is snapped to a whole number of
        // sensitivity steps, the way a real mouse turn would be.
        double sens = this.mouseSensitivity();
        float serverYaw = Rotations.serverYaw;
        float serverPitch = Rotations.serverPitch;
        float wireYaw = serverYaw + RotationGcd.quantize(sens, (float) RodAimMath.wrapDegrees(yaw - serverYaw));
        float wirePitch = RotationGcd.quantizeFrom(sens, serverPitch, pitch);

        PreRotate.rotateThen(this, wireYaw, wirePitch, this::cast);
    }

    /** The cast itself. Runs in the movement packet's PRE phase, from a landed rotation. */
    private void cast() {
        if (this.mc.player == null || this.mc.player.fishing != null) return;
        if (!(this.mc.player.getMainHandItem().getItem() instanceof FishingRodItem)) return;

        this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
    }

    /**
     * The closest player that passes every gate: alive, not you, not invisible (unless asked),
     * not a bot, not a teammate (unless asked), inside the distance and inside the cone.
     */
    private Player findTarget() {
        AntiBot antiBot = Modules.get().get(AntiBot.class);
        double maxDistanceSq = this.distance.get() * this.distance.get();
        float cameraYaw = this.mc.player.getYRot();
        double eyeX = this.mc.player.getX();
        double eyeY = this.mc.player.getEyeY();
        double eyeZ = this.mc.player.getZ();

        Player best = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (Player candidate : this.mc.level.players()) {
            if (candidate == this.mc.player || candidate.isRemoved() || candidate.isDeadOrDying()) continue;
            if (!this.aimInvis.get() && candidate.isInvisible()) continue;
            if (this.ignoreTeammates.get() && this.mc.player.isAlliedTo(candidate)) continue;
            if (antiBot != null && antiBot.isBotEntity(candidate)) continue;

            double distanceSq = candidate.distanceToSqr(this.mc.player);
            if (distanceSq > maxDistanceSq) continue;

            float targetYaw = RodAimMath.yawTo(candidate.getX() - eyeX, candidate.getZ() - eyeZ);
            if (!RodAimMath.withinFov(cameraYaw, targetYaw, this.fov.get())) continue;

            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                best = candidate;
            }
        }

        return best;
    }

    private double mouseSensitivity() {
        return this.mc.options == null
            ? 0.0D
            : ((Double) this.mc.options.sensitivity().get()).doubleValue();
    }

    @Override
    public String getInfoString() {
        return this.target != null ? this.target.getName().getString() : null;
    }
}
