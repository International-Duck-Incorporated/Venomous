package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BowAimbot module.
 *
 * <p>The module itself cannot be instantiated in a plain unit test - module construction reaches
 * the Minecraft render system - so what these tests pin are the contracts that are unit-testable:
 * the module class satisfies the reflection auto-registrar's requirements (public, concrete,
 * extends Module, public no-arg constructor). The old assertion read
 * {@code ModuleRegistry.java} for a registration line that no longer exists.</p>
 */
public class BowAimbotTest {

    @Test
    void moduleIsRegistrableByAutoRegistration() throws Exception {
        Class<?> bowAimbotClass = Class.forName("net.venomous.meteoraddon.modules.combat.BowAimbot");
        assertNotNull(bowAimbotClass);

        assertTrue(Modifier.isPublic(bowAimbotClass.getModifiers()), "BowAimbot must be public");
        assertFalse(Modifier.isAbstract(bowAimbotClass.getModifiers()), "BowAimbot must be concrete");
        assertTrue(meteordevelopment.meteorclient.systems.modules.Module.class.isAssignableFrom(bowAimbotClass),
            "BowAimbot must extend Module");
        assertTrue(Modifier.isPublic(bowAimbotClass.getDeclaredConstructor().getModifiers()),
            "BowAimbot needs a public no-arg constructor for auto-registration");
    }
}
