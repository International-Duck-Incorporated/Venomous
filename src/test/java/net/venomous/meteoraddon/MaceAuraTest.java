package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MaceAura module. Asserts the reflection auto-registrar's contract for the
 * class (public, concrete, extends Module, public no-arg constructor) - the registry itself no
 * longer contains per-module lines to grep for.
 */
public class MaceAuraTest {

    @Test
    void moduleIsRegistrableByAutoRegistration() throws Exception {
        Class<?> maceAuraClass = Class.forName("net.venomous.meteoraddon.modules.combat.MaceAura");
        assertNotNull(maceAuraClass);

        assertTrue(Modifier.isPublic(maceAuraClass.getModifiers()), "MaceAura must be public");
        assertFalse(Modifier.isAbstract(maceAuraClass.getModifiers()), "MaceAura must be concrete");
        assertTrue(meteordevelopment.meteorclient.systems.modules.Module.class.isAssignableFrom(maceAuraClass),
            "MaceAura must extend Module");
        assertTrue(Modifier.isPublic(maceAuraClass.getDeclaredConstructor().getModifiers()),
            "MaceAura needs a public no-arg constructor for auto-registration");
    }
}
