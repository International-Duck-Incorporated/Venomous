package net.venomous.meteoraddon.modules.misc;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.venomous.meteoraddon.VenomousAddon;

/**
 * Diagnostic recorder for movement and camera state.
 *
 * <p>The recorder writes CSV outside the event callbacks' memory, which makes long
 * captures practical and avoids changing packet timing with a large in-memory queue.
 * It records client tick state plus the state observed immediately before and after
 * Meteor's movement-packet event.
 */
public class MovementDebug extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> flushEvery = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("flush-every")
            .description("Flush the debug file after this many rows.")
            .defaultValue(20)
            .range(1, 200)
            .sliderMax(100)
            .build()
    );

    private BufferedWriter writer;
    private Path outputFile;
    private long sequence;
    private int rowsSinceFlush;
    private long tick;

    public MovementDebug() {
        super(VenomousAddon.CATEGORY, "movement-debug", "Record movement packets and camera angles to a CSV file.");
    }

    @Override
    public void onActivate() {
        this.sequence = 0L;
        this.rowsSinceFlush = 0;
        this.tick = 0L;

        Path directory = Path.of("venom-output");
        this.outputFile = directory.resolve("movement_" + System.currentTimeMillis() + ".csv");

        try {
            Files.createDirectories(directory);
            this.writer = Files.newBufferedWriter(
                this.outputFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            );
            this.writer.write("seq,event,tick,time_nanos,x,y,z,dx,dy,dz,yaw,pitch,server_yaw,server_pitch,yaw_delta,pitch_delta,on_ground,sneaking,sprinting,forward,back,left,right");
            this.writer.newLine();
            this.writer.flush();
            this.info("Recording movement to " + this.outputFile, new Object[0]);
        } catch (IOException e) {
            this.writer = null;
            this.error("Could not open movement debug file: " + e.getMessage(), new Object[0]);

            // Deferred: toggling from inside onActivate re-enters Meteor's toggle logic while
            // activation is still unwinding. isActive() guards against a race where the user
            // switched the module off before the deferred call runs.
            this.mc.execute(() -> {
                if (this.isActive()) this.toggle();
            });
        }
    }

    @Override
    public void onDeactivate() {
        if (this.writer == null) {
            return;
        }

        try {
            this.writer.flush();
            this.writer.close();
            this.info("Saved movement debug to " + this.outputFile, new Object[0]);
        } catch (IOException e) {
            this.error("Could not close movement debug file: " + e.getMessage(), new Object[0]);
        } finally {
            this.writer = null;
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.writer == null || this.mc.player == null) {
            return;
        }

        this.tick++;
        this.writeRow("tick");
    }

    @EventHandler
    private void onMovementPre(SendMovementPacketsEvent.Pre event) {
        this.writeRow("movement_pre");
    }

    @EventHandler
    private void onMovementPost(SendMovementPacketsEvent.Post event) {
        this.writeRow("movement_post");
    }

    private void writeRow(String event) {
        LocalPlayer player = this.mc.player;
        if (this.writer == null || player == null) {
            return;
        }

        try {
            double dx = player.getX() - player.xo;
            double dy = player.getY() - player.yo;
            double dz = player.getZ() - player.zo;
            float yaw = player.getYRot();
            float pitch = player.getXRot();
            float serverYaw = Rotations.serverYaw;
            float serverPitch = Rotations.serverPitch;

            this.writer.write(Long.toString(++this.sequence));
            this.writer.write(',');
            this.writer.write(event);
            this.writer.write(',');
            this.writer.write(Long.toString(this.tick));
            this.writer.write(',');
            this.writer.write(Long.toString(System.nanoTime()));
            this.writer.write(',');
            this.writer.write(Double.toString(player.getX()));
            this.writer.write(',');
            this.writer.write(Double.toString(player.getY()));
            this.writer.write(',');
            this.writer.write(Double.toString(player.getZ()));
            this.writer.write(',');
            this.writer.write(Double.toString(dx));
            this.writer.write(',');
            this.writer.write(Double.toString(dy));
            this.writer.write(',');
            this.writer.write(Double.toString(dz));
            this.writer.write(',');
            this.writer.write(Float.toString(yaw));
            this.writer.write(',');
            this.writer.write(Float.toString(pitch));
            this.writer.write(',');
            this.writer.write(Float.toString(serverYaw));
            this.writer.write(',');
            this.writer.write(Float.toString(serverPitch));
            this.writer.write(',');
            this.writer.write(Float.toString(wrapDegrees(serverYaw - yaw)));
            this.writer.write(',');
            this.writer.write(Float.toString(serverPitch - pitch));
            this.writer.write(',');
            this.writer.write(Boolean.toString(player.onGround()));
            this.writer.write(',');
            this.writer.write(Boolean.toString(player.isShiftKeyDown()));
            this.writer.write(',');
            this.writer.write(Boolean.toString(player.isSprinting()));
            this.writer.write(',');
            this.writer.write(Boolean.toString(this.mc.options.keyUp.isDown()));
            this.writer.write(',');
            this.writer.write(Boolean.toString(this.mc.options.keyDown.isDown()));
            this.writer.write(',');
            this.writer.write(Boolean.toString(this.mc.options.keyLeft.isDown()));
            this.writer.write(',');
            this.writer.write(Boolean.toString(this.mc.options.keyRight.isDown()));
            this.writer.newLine();

            if (++this.rowsSinceFlush >= this.flushEvery.get()) {
                this.writer.flush();
                this.rowsSinceFlush = 0;
            }
        } catch (IOException e) {
            this.error("Movement debug write failed: " + e.getMessage(), new Object[0]);
            this.closeAfterError();
        }
    }

    private void closeAfterError() {
        try {
            if (this.writer != null) {
                this.writer.close();
            }
        } catch (IOException ignored) {
            // The original write error is the useful diagnostic.
        } finally {
            this.writer = null;
        }
    }

    private static float wrapDegrees(float value) {
        float wrapped = value % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }
}
