package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TriggerBot module. Asserts the reflection auto-registrar's contract for the
 * class (public, concrete, extends Module, public no-arg constructor) - the registry itself no
 * longer contains per-module lines to grep for.
 */
public class TriggerBotTest {

    @Test
    void moduleIsRegistrableByAutoRegistration() throws Exception {
        Class<?> triggerBotClass = Class.forName("net.venomous.meteoraddon.modules.combat.TriggerBot");
        assertNotNull(triggerBotClass);

        assertTrue(Modifier.isPublic(triggerBotClass.getModifiers()), "TriggerBot must be public");
        assertFalse(Modifier.isAbstract(triggerBotClass.getModifiers()), "TriggerBot must be concrete");
        assertTrue(meteordevelopment.meteorclient.systems.modules.Module.class.isAssignableFrom(triggerBotClass),
            "TriggerBot must extend Module");
        assertTrue(Modifier.isPublic(triggerBotClass.getDeclaredConstructor().getModifiers()),
            "TriggerBot needs a public no-arg constructor for auto-registration");
    }
}
