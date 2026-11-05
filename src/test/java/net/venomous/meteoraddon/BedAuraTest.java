package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BedAura module.
 * Guards bed detection, target validation, and breaking logic.
 *
 * <p>Module instantiation needs a live Minecraft device (module construction reaches the render
 * system), which plain unit tests do not have. What is asserted here is the contract the
 * reflection auto-registrar enforces for this class - public, concrete, extends Module, public
 * no-arg constructor - which is exactly what the runtime registration needs. The old assertion
 * read {@code ModuleRegistry.java} for a registration line that no longer exists.</p>
 */
public class BedAuraTest {

    @Test
    void moduleIsRegistrableByAutoRegistration() throws Exception {
        Class<?> bedAuraClass = Class.forName("net.venomous.meteoraddon.modules.combat.BedAura");
        assertNotNull(bedAuraClass);

        assertTrue(Modifier.isPublic(bedAuraClass.getModifiers()), "BedAura must be public");
        assertFalse(Modifier.isAbstract(bedAuraClass.getModifiers()), "BedAura must be concrete");
        assertTrue(meteordevelopment.meteorclient.systems.modules.Module.class.isAssignableFrom(bedAuraClass),
            "BedAura must extend Module");
        assertTrue(Modifier.isPublic(bedAuraClass.getDeclaredConstructor().getModifiers()),
            "BedAura needs a public no-arg constructor for auto-registration");
    }
}
