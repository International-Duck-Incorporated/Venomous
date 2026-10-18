package net.venomous.meteoraddon.modules.detection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.venomous.meteoraddon.VenomousAddon;

public class AntiStaff extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<List<String>> staffNames = this.sgGeneral.add(
        new StringListSetting.Builder()
            .name("staff-names")
            .description("Names to treat as staff. Matching is case-insensitive.")
            .defaultValue(new ArrayList<>())
            .build()
    );

    private final Setting<Boolean> detectKeywords = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("detect-keywords")
            .description("Detect common staff rank tags in display names ([Admin], [Mod], [Owner], [Staff], [Helper], [Dev]).")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> alertJoin = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("alert-join")
            .description("Warn when a configured staff name appears in tab/player info.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> autoDisconnect = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("auto-disconnect")
            .description("Instantly disconnect when staff joins or is detected.")
            .defaultValue(false)
            .build()
    );

    private static final Pattern STAFF_PREFIX_PATTERN = Pattern.compile(
        "(?i).*(\\[(admin|mod|moderator|owner|staff|helper|dev|developer|builder|trainee|operator|gm|guard)\\]|\\b(admin|moderator|staff)\\b).*"
    );

    private final Set<String> alerted = new HashSet<>();

    public AntiStaff() {
        super(VenomousAddon.CATEGORY, "anti-staff", "Warns or disconnects when staff members or staff-tagged profiles join the server.");
    }

    @Override
    public void onActivate() {
        this.alerted.clear();
    }

    @Override
    public void onDeactivate() {
        this.alerted.clear();
    }

    @EventHandler
    private void onPacket(Receive event) {
        if (!this.alertJoin.get()) {
            return;
        }

        if (event.packet instanceof ClientboundPlayerInfoUpdatePacket packet) {
            for (Entry entry : packet.newEntries()) {
                if (entry.profile() == null) {
                    continue;
                }

                String name = entry.profile().name();
                String lowerName = name.toLowerCase(Locale.ROOT);

                boolean isStaffMember = this.isStaff(name);

                if (!isStaffMember && this.detectKeywords.get()) {
                    Component displayName = entry.displayName();
                    String fullDisplay = (displayName != null) ? displayName.getString() : name;
                    if (STAFF_PREFIX_PATTERN.matcher(fullDisplay).matches() || STAFF_PREFIX_PATTERN.matcher(name).matches()) {
                        isStaffMember = true;
                    }
                }

                if (isStaffMember && this.alerted.add(lowerName)) {
                    this.warning("Staff detected on server: %s", name);
                    if (this.autoDisconnect.get() && this.mc.getConnection() != null) {
                        this.mc.getConnection().getConnection().disconnect(Component.literal("[Venomous] Staff member detected: " + name));
                    }
                }
            }
        }
    }

    private boolean isStaff(String name) {
        for (String staff : this.staffNames.get()) {
            if (staff.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
