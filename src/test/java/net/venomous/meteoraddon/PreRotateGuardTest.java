package net.venomous.meteoraddon;

import meteordevelopment.meteorclient.systems.modules.Module;
import net.venomous.meteoraddon.utils.PreRotate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the lifetime contract of {@link PreRotate}.
 *
 * <p>Every queued action outlives the tick that requested it, so each one used to keep running
 * after its module was switched off. {@code auto-break-selected} went further and dereferenced
 * a target its own teardown had just nulled. The fix centralises the check in PreRotate by
 * removing the overloads that had no way to express a guard, which makes this a compile-time
 * invariant - this test exists so that a future unguarded overload cannot be reintroduced.</p>
 */
public class PreRotateGuardTest {
    @Test
    void everyRotateThenOverloadTakesAGuard() throws Exception {
        int checked = 0;

        for (Method method : PreRotate.class.getDeclaredMethods()) {
            if (!method.getName().equals("rotateThen")) continue;
            checked++;

            Class<?>[] params = method.getParameterTypes();
            boolean hasGuard = Arrays.stream(params)
                .anyMatch(t -> t == PreRotate.Guard.class || Module.class.isAssignableFrom(t));

            assertTrue(hasGuard,
                "PreRotate.rotateThen overload accepts no guard, so callers cannot tie an action "
                    + "to a lifetime: " + Arrays.toString(params));
        }

        assertTrue(checked > 0, "No rotateThen overloads found - the reflection is not looking where it thinks");
    }

    @Test
    void everyRotateThenOverloadTakesAnAction() throws Exception {
        for (Method method : PreRotate.class.getDeclaredMethods()) {
            if (!method.getName().equals("rotateThen")) continue;

            Class<?>[] params = method.getParameterTypes();
            assertEquals(Runnable.class, params[params.length - 1],
                "rotateThen should take its Runnable last, found: " + Arrays.toString(params));
        }
    }
}
