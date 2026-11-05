package net.venomous.meteoraddon.safety;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Central allow-list for module safety levels.
 *
 * <p>Track A provides the policy API only. Enforcement belongs in the module base class
 * (Track B): before a module toggles on, it should ask {@link #isAllowed(SafetyLevel)} with its
 * {@link SafetyLevel}. Until a module overrides its level, {@link #defaultLevel()} is
 * {@link SafetyLevel#SAFE}, so existing behaviour is unchanged.</p>
 */
public final class SafetyManager {
    private static final Set<SafetyLevel> ENABLED = EnumSet.of(
        SafetyLevel.SAFE,
        SafetyLevel.RISKY,
        SafetyLevel.BAN
    );

    private SafetyManager() {}

    /**
     * @return the level assumed for any module that does not declare one
     */
    public static SafetyLevel defaultLevel() {
        return SafetyLevel.SAFE;
    }

    public static boolean isAllowed(SafetyLevel level) {
        Objects.requireNonNull(level, "level");
        synchronized (ENABLED) {
            return ENABLED.contains(level);
        }
    }

    public static void setEnabled(SafetyLevel level, boolean on) {
        Objects.requireNonNull(level, "level");
        synchronized (ENABLED) {
            if (on) {
                ENABLED.add(level);
            } else {
                ENABLED.remove(level);
            }
        }
    }

    /**
     * Kill switch: disables the given level and every level above it.
     *
     * <p>For example, {@code disableAllAtOrAbove(RISKY)} leaves only {@link SafetyLevel#SAFE}
     * enabled.</p>
     */
    public static void disableAllAtOrAbove(SafetyLevel level) {
        Objects.requireNonNull(level, "level");
        synchronized (ENABLED) {
            for (SafetyLevel candidate : SafetyLevel.values()) {
                if (candidate.ordinal() >= level.ordinal()) {
                    ENABLED.remove(candidate);
                }
            }
        }
    }

    /**
     * @return an immutable snapshot of the levels currently allowed
     */
    public static Set<SafetyLevel> enabledLevels() {
        synchronized (ENABLED) {
            return Collections.unmodifiableSet(EnumSet.copyOf(ENABLED));
        }
    }
}
