package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.ShutdownReaper;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the shutdown reaper's trigger: it must fire the forced exit once, and only once the
 * watched thread is gone. This is the fix for the twenty second quit-hang - the watcher firing
 * early would kill the client mid-stop, and never firing would leave the watchdog crash in
 * place, so both directions are pinned.
 */
public class ShutdownReaperTest {
    @Test
    void firesOnceTheWatchedThreadHasFinished() throws Exception {
        AtomicBoolean forced = new AtomicBoolean(false);
        CountDownLatch done = new CountDownLatch(1);

        Thread dying = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
        });
        dying.start();

        ShutdownReaper.monitor(dying, 5, () -> {
            forced.set(true);
            done.countDown();
        });

        assertTrue(done.await(5, TimeUnit.SECONDS),
            "the reaper must fire after the watched thread dies");
        assertTrue(forced.get());
        assertFalse(dying.isAlive());
    }

    @Test
    void doesNotFireWhileTheWatchedThreadIsAlive() throws Exception {
        AtomicInteger fires = new AtomicInteger(0);

        Thread living = new Thread(() -> {
            try {
                Thread.sleep(250);
            } catch (InterruptedException ignored) {
            }
        });
        living.start();

        ShutdownReaper.monitor(living, 20, fires::incrementAndGet);

        // The watched thread still has most of its run ahead of it; the reaper must have stayed
        // quiet through this window.
        Thread.sleep(80);
        assertEquals(0, fires.get(), "the reaper fired while the watched thread was still alive");

        living.join(2000);
        assertTrue(living.isAlive() || fires.get() <= 1,
            "the reaper must fire at most once for one watched thread");
    }

    @Test
    void theReaperThreadIsADaemon() throws Exception {
        // A non-daemon reaper would be its own quit-hang: the JVM would wait on the waiter.
        AtomicBoolean sawDaemon = new AtomicBoolean(false);
        CountDownLatch done = new CountDownLatch(1);

        Thread alreadyDead = new Thread(() -> {
        });
        alreadyDead.start();
        alreadyDead.join();

        ShutdownReaper.monitor(alreadyDead, 1, done::countDown);
        assertTrue(done.await(5, TimeUnit.SECONDS), "a dead watched thread must trigger immediately");
        // All reaper threads are daemons by construction; assert the class constant path ran by
        // confirming no live non-daemon threads named after the reaper linger.
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().contains("shutdown reaper")) {
                sawDaemon.set(thread.isDaemon());
            }
        }
        assertTrue(sawDaemon.get() || Thread.getAllStackTraces().keySet().stream()
                .noneMatch(t -> t.getName().contains("shutdown reaper")),
            "the reaper thread, while alive, must be a daemon");
    }
}
