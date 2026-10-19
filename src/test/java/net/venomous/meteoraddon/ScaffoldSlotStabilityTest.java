package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the hotbar-swap behaviour of {@code v-scaffold}.
 *
 * <p>Grim's {@code PacketOrderE} is described as "Changed held item slot during another conflicting
 * action" and fires on {@code HELD_ITEM_CHANGE} whenever any action is outstanding:</p>
 *
 * <pre>{@code
 *   if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
 *       int currentFlags = currentFlags();
 *       if (currentFlags != 0) { ...flag... }
 *   }
 * }</pre>
 *
 * <p>A block placement counts as an outstanding action, so scanning the hotbar every tick produced a
 * slot change arriving while the previous placement was still in flight:</p>
 *
 * <pre>{@code
 *   Grim > PacketOrderE x0.0 attacking=false, rightClicking=true,
 *            openingInventory=false, releasing=false, sneaking=false, sprinting=false, ...
 * }</pre>
 *
 * <p>There is nothing to gain by swapping when the slot already held holds a usable block, so
 * {@code findBlock} must check the held item before it ever reaches {@code InvUtils.findInHotbar}.
 */
public class ScaffoldSlotStabilityTest {
    private static String scaffoldSource() throws IOException {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        String rel = "src/main/java/net/venomous/meteoraddon/modules/world/Scaffold.java";

        while (dir != null) {
            Path candidate = dir.resolve(rel);
            if (Files.isRegularFile(candidate)) return Files.readString(candidate);
            dir = dir.getParent();
        }

        throw new AssertionError("could not find " + rel);
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "method not found: " + signature);

        int end = source.indexOf("\n    }", start);
        return source.substring(start, end);
    }

    @Test
    void theHeldItemIsCheckedBeforeAnythingCanSwapSlots() throws IOException {
        String body = method(scaffoldSource(), "private FindItemResult findBlock() {");

        int heldCheck = body.indexOf("isPlaceableBlock(held)");
        int firstFind = body.indexOf("findInHotbar");

        assertTrue(heldCheck >= 0,
            "findBlock must check whether the slot already held holds a usable block, before any "
                + "hotbar scan that can change the selected slot");
        assertTrue(heldCheck < firstFind,
            "the held-item check must come before findInHotbar; otherwise the swap happens first and "
                + "HELD_ITEM_CHANGE is sent with a placement already outstanding");
    }

    /**
     * The regression that produced the flags: an unconditional hotbar scan per placement. The
     * early return must not be merely present but must dominate the rest of the method.
     */
    @Test
    void aPlaceableHeldItemReturnsWithoutScanningTheHotbar() throws IOException {
        String body = method(scaffoldSource(), "private FindItemResult findBlock() {");
        String code = body.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");

        assertTrue(code.contains("return new FindItemResult(this.mc.player.getInventory().getSelectedSlot()"),
            "holding a usable block must return the current slot directly, emitting no "
                + "HELD_ITEM_CHANGE at all");
        assertFalse(code.contains("InvUtils.findInHotbar(held.getItem())"),
            "findInHotbar(held.getItem()) still performs a slot operation; the current slot must be "
                + "returned as-is instead");
    }
}