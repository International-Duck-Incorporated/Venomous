package net.venomous.meteoraddon.safety;

/**
 * How risky a module is to use on a server.
 *
 * <p>Ordering matters: {@link #disableAllAtOrAbove(SafetyLevel)} uses the declaration order, so
 * disabling {@link #RISKY} also disables {@link #BAN}.</p>
 */
public enum SafetyLevel {
    /** No realistic ban risk; always allowed. */
    SAFE,

    /** May violate server rules or trigger anticheat under some configurations. */
    RISKY,

    /** Known to be detectable or explicitly disallowed on most servers. */
    BAN
}
