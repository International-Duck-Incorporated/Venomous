package net.venomous.meteoraddon.utils.combat;

/**
 * Auto-shoot controller shared across aimbot and triggerbot.
 */
public final class AutoShootController {

    private long lastAttackTick = 0;
    private long attackDelay = 50; // default 50ms cooldown

    /**
     * Sets the attack delay in milliseconds.
     */
    public void setDelay(long delayMs) {
        this.attackDelay = Math.max(0, delayMs);
    }

    /**
     * Ticks the auto-shoot state; returns true if ready to attack this tick.
     *
     * @param currentTickMs current time in milliseconds
     * @return true if ready to shoot
     */
    public boolean tick(long currentTickMs) {
        if (lastAttackTick == 0) lastAttackTick = currentTickMs;
        long elapsed = currentTickMs - lastAttackTick;
        boolean ready = elapsed >= attackDelay;
        if (ready) lastAttackTick = currentTickMs;
        return ready;
    }

    /**
     * Resets the auto-shoot state.
     */
    public void release() {
        lastAttackTick = 0;
    }
}