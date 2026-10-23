package net.venomous.meteoraddon.utils;

/**
 * Forces the JVM to exit once the main thread has finished.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Quitting the game used to hang for twenty seconds and end in a crash. The sequence: the
 * player clicks Quit, {@code Minecraft.stop()} completes, {@code main()} returns, and the JVM
 * launcher settles into {@code DestroyJavaVM} - which then waits for every non-daemon thread
 * still alive. Baritone's cache workers are exactly that: a shared {@code ThreadPoolExecutor}
 * whose packer threads park forever in {@code LinkedBlockingQueue.take()}, and whose loop
 * catches {@code InterruptedException} <em>and</em> {@code Throwable}, prints the stack, and
 * goes back to parking. Neither an interrupt nor a {@code shutdownNow()} can end a worker that
 * is already parked inside that loop, so nothing the client does at shutdown can release them.
 * After twenty seconds vanilla's {@code ClientShutdownWatchdog} gives up and force-kills the
 * process with a crash report and exit code -8.</p>
 *
 * <p>The reaper changes the ending. A daemon thread watches the main thread; the moment
 * {@code main()} has returned, {@code System.exit(0)} is invoked from the reaper. Shutdown
 * hooks run, and the halt that follows takes the parked Baritone threads with it - no twenty
 * second wait, no watchdog crash, a clean exit code instead of -8. If every thread happens to
 * be well behaved, the natural shutdown wins the race and the forced exit is a harmless
 * no-op on an already-halting JVM.</p>
 *
 * <p>The watcher is a daemon deliberately: it must never be the thing keeping the JVM alive.</p>
 */
public final class ShutdownReaper {
    /** How often the reaper checks whether the main thread has finished. */
    static final long POLL_MS = 200;

    private ShutdownReaper() {
    }

    /**
     * Starts watching. Call once during addon init, on the render thread - the thread that runs
     * {@code main()}, and therefore the one whose death means the client is done stopping.
     */
    public static void init() {
        monitor(Thread.currentThread(), POLL_MS, () -> System.exit(0));
    }

    /** The watching itself, separated so the trigger condition is testable without a game. */
    public static void monitor(Thread main, long pollMs, Runnable forceExit) {
        Thread reaper = new Thread(() -> {
            while (main.isAlive()) {
                try {
                    Thread.sleep(pollMs);
                } catch (InterruptedException interrupted) {
                    return;
                }
            }

            forceExit.run();
        }, "Venomous shutdown reaper");

        reaper.setDaemon(true);
        reaper.start();
    }
}
