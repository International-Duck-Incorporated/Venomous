package net.venomous.meteoraddon.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.reflections.Reflections;

import java.lang.reflect.Modifier;
import java.util.Comparator;

/**
 * Discovers every concrete {@link Module} subclass under the addon's module package and
 * registers it with Meteor.
 *
 * <p>Adding a module no longer requires editing this file: create the class with a public
 * no-argument constructor and it is picked up automatically. The scan is deliberately limited
 * to {@code net.venomous.meteoraddon.modules}, so Meteor's own modules and helper classes
 * elsewhere in the addon are never touched.</p>
 */
public final class ModuleRegistry {
    private static final String BASE_PACKAGE = "net.venomous.meteoraddon.modules";

    private ModuleRegistry() {}

    /**
     * Registers every concrete, non-anonymous module class found on the classpath.
     *
     * @throws IllegalStateException if a discovered module cannot be constructed
     */
    public static void registerAll() {
        new Reflections(BASE_PACKAGE)
            .getSubTypesOf(Module.class)
            .stream()
            .filter(cls -> !Modifier.isAbstract(cls.getModifiers()))
            .filter(cls -> !cls.isAnonymousClass())
            .sorted(Comparator.comparing(Class::getName))
            .forEach(ModuleRegistry::register);
    }

    private static void register(Class<? extends Module> moduleClass) {
        try {
            Modules.get().add(moduleClass.getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot register " + moduleClass.getName(), e);
        }
    }

    /**
     * @deprecated kept so older callers keep working; use {@link #registerAll()}.
     */
    @Deprecated
    public static void register() {
        registerAll();
    }
}
