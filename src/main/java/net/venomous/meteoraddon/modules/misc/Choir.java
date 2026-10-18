package net.venomous.meteoraddon.modules.misc;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.venomous.meteoraddon.VenomousAddon;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import meteordevelopment.meteorclient.settings.FileSetting;

public class Choir extends Module {
    private static final int MS_PER_TICK = 50;
    private static final int MAX_CHAT_LENGTH = 256;
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgAdvanced = this.settings.createGroup("Advanced");
    private final File DEFAULT_FILE = new File(MeteorClient.FOLDER, "vchoir.txt");
    private final PointerBuffer filters = BufferUtils.createPointerBuffer(1);
    private final Setting<File> file;
    private final Setting<Integer> defaultDelay;
    private final Setting<Boolean> loop;
    private final Setting<Boolean> disableWhenDone;
    private final Setting<Boolean> keepBlankLines;
    private final Setting<Boolean> trimLines;
    private final Setting<Boolean> truncateLong;
    private final Setting<Boolean> notify;
    private final List<Choir.Entry> script;
    private int index;
    private int timer;
    private boolean failed;

    public Choir() {
        super(VenomousAddon.CATEGORY, "choir", "Sings a .txt file into chat one line at a time, with .wait <ms> directives controlling the timing.");
        ByteBuffer var1 = MemoryUtil.memASCII("*.txt");
        this.filters.put(var1);
        this.filters.rewind();
        this.file = this.sgGeneral
            .add(
                new FileSetting.Builder().name("file").description("The .txt file to read lines from.").defaultValue(this.DEFAULT_FILE)
                    .filter(this.filters)
                    .build()
            );
        this.defaultDelay = this.sgGeneral
            .add(
                new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                    .name("default-delay")
                    .description("Milliseconds between lines when the file doesn't specify a .wait.")
                    .defaultValue(1000)
                    .min(0)
                    .sliderRange(0, 10000)
                    .build()
            );
        this.loop = this.sgGeneral
            .add(
                new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                    .name("loop")
                    .description("Start again from the top once the file has finished.")
                    .defaultValue(false)
                    .build()
            );
        this.disableWhenDone = this.sgGeneral
            .add(
                new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                    .name("disable-when-done")
                    .description("Automatically toggle the module off after the last line. Ignored while looping.")
                    .defaultValue(true)
                    .visible(() -> !(Boolean)this.loop.get())
                    .build()
            );
        this.keepBlankLines = this.sgAdvanced
            .add(
                new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                    .name("keep-blank-lines")
                    .description("Send blank lines too. Most servers reject empty messages, so this is off by default.")
                    .defaultValue(false)
                    .build()
            );
        this.trimLines = this.sgAdvanced
            .add(
                new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                    .name("trim-lines")
                    .description("Strip leading/trailing whitespace from each line before sending.")
                    .defaultValue(true)
                    .build()
            );
        this.truncateLong = this.sgAdvanced
            .add(
                new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                    .name("truncate-long")
                    .description("Cut lines longer than 256 characters instead of skipping them. Sending an over-length message gets you kicked.")
                    .defaultValue(true)
                    .build()
            );
        this.notify = this.sgAdvanced
            .add(
                new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                    .name("notify")
                    .description("Report loading errors and completion in chat.")
                    .defaultValue(true)
                    .build()
            );
        this.script = new ArrayList<>();
    }

    @Override
    public void onActivate() {
        this.script.clear();
        this.index = 0;
        this.timer = 0;
        this.failed = false;
        if (!this.load()) {
            this.failed = true;
            this.mc.execute(() -> {
                if (this.isActive()) this.toggle();
            });
        } else if (this.script.isEmpty()) {
            if ((Boolean)this.notify.get()) {
                this.warning("Nothing to sing - the file has no sendable lines.", new Object[0]);
            }

            this.failed = true;
            this.mc.execute(() -> {
                if (this.isActive()) this.toggle();
            });
        } else {
            this.timer = this.script.getFirst().delayTicks;
        }
    }

    @Override
    public void onDeactivate() {
        this.script.clear();
        this.index = 0;
        this.timer = 0;
        this.failed = false;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent var1) {
        if (this.isActive()) {
            this.toggle();
        }
    }

    @EventHandler
    private void onTick(Pre var1) {
        if (!this.failed && this.mc.player != null && !this.script.isEmpty()) {
            if (this.timer > 0) {
                this.timer--;
            } else if (this.index >= this.script.size()) {
                this.finish();
            } else {
                Choir.Entry var2 = this.script.get(this.index++);
                ChatUtils.sendPlayerMsg(var2.text);
                if (this.index < this.script.size()) {
                    this.timer = this.script.get(this.index).delayTicks;
                } else {
                    this.finish();
                }
            }
        }
    }

    private void finish() {
        if ((Boolean)this.loop.get()) {
            this.index = 0;
            this.timer = this.script.getFirst().delayTicks;
        } else {
            if ((Boolean)this.notify.get()) {
                this.info("Finished singing (highlight)%d(default) line(s).", new Object[]{this.script.size()});
            }

            if ((Boolean)this.disableWhenDone.get()) {
                this.toggle();
            } else {
                this.index = this.script.size();
            }
        }
    }

    private boolean load() {
        File var1 = (File)this.file.get();
        if (var1 != null && var1.exists() && var1.isFile()) {
            List<String> var2;
            try {
                var2 = Files.readAllLines(var1.toPath(), StandardCharsets.UTF_8);
            } catch (IOException var10) {
                if ((Boolean)this.notify.get()) {
                    this.error("Couldn't read (highlight)%s(default): %s", new Object[]{var1.getName(), var10.getMessage()});
                }

                return false;
            }

            int var3 = -1;
            int var4 = 0;

            for (String var6 : var2) {
                String var7 = this.trimLines.get() ? var6.strip() : this.stripLineEnding(var6);
                Integer var8 = this.parseWait(var7);
                if (var8 != null) {
                    var3 = (var3 < 0 ? 0 : var3) + var8;
                } else if (!var7.isEmpty() || (Boolean)this.keepBlankLines.get()) {
                    if (var7.length() > 256) {
                        if (!(Boolean)this.truncateLong.get()) {
                            var4++;
                            continue;
                        }

                        var7 = var7.substring(0, 256);
                    }

                    int var9 = var3 < 0 ? (Integer)this.defaultDelay.get() : var3;
                    this.script.add(new Choir.Entry(var7, this.msToTicks(var9)));
                    var3 = -1;
                }
            }

            if (var4 > 0 && (Boolean)this.notify.get()) {
                this.warning("Skipped (highlight)%d(default) line(s) over %d characters.", new Object[]{var4, 256});
            }

            return true;
        } else {
            if ((Boolean)this.notify.get()) {
                this.error("No file selected, or it doesn't exist.", new Object[0]);
            }

            return false;
        }
    }

    private Integer parseWait(String var1) {
        String var2 = var1.strip();
        if (var2.length() < 5) {
            return null;
        }

        if (!var2.regionMatches(true, 0, ".wait", 0, 5)) {
            return null;
        }

        String var3 = var2.substring(5).strip();
        if (var3.isEmpty()) {
            return 0;
        }

        try {
            int var4 = Integer.parseInt(var3);
            return Math.max(0, var4);
        } catch (NumberFormatException var5) {
            return null;
        }
    }

    private int msToTicks(int var1) {
        return var1 <= 0 ? 0 : Math.max(1, Math.round(var1 / 50.0F));
    }

    private String stripLineEnding(String var1) {
        return var1.endsWith("\r") ? var1.substring(0, var1.length() - 1) : var1;
    }

    public WWidget getWidget(GuiTheme var1) {
        WVerticalList var2 = var1.verticalList();
        WButton var3 = (WButton)var2.add(var1.button("Reload File")).expandX().widget();
        var3.action = () -> {
            if (!this.isActive()) {
                this.info("Enable the module to start singing.", new Object[0]);
            } else {
                this.onActivate();
            }
        };
        WButton var4 = (WButton)var2.add(var1.button("Restart From Top")).expandX().widget();
        var4.action = () -> {
            if (!this.script.isEmpty()) {
                this.index = 0;
                this.timer = this.script.getFirst().delayTicks;
            }
        };
        return var2;
    }

    public String getInfoString() {
        return this.script.isEmpty() ? null : this.index + "/" + this.script.size();
    }

    private record Entry(String text, int delayTicks) {
    }
}
