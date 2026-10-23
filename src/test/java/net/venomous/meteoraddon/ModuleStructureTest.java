package net.venomous.meteoraddon;

import meteordevelopment.orbit.EventHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ModuleStructureTest {
    private static final List<String> ALL_MODULE_CLASSES = List.of(
            "net.venomous.meteoraddon.modules.combat.Aimbot",
            "net.venomous.meteoraddon.modules.combat.AntiProjectile",
            "net.venomous.meteoraddon.modules.combat.AutoClickerPlus",
            "net.venomous.meteoraddon.modules.combat.AutoPotion",
            "net.venomous.meteoraddon.modules.combat.AutoShield",
            "net.venomous.meteoraddon.modules.combat.CriticalsPlus",
            "net.venomous.meteoraddon.modules.combat.CrystalPvP",
            "net.venomous.meteoraddon.modules.combat.HitSelect",
            "net.venomous.meteoraddon.modules.combat.Hunt",
            "net.venomous.meteoraddon.modules.combat.JumpReset",
            "net.venomous.meteoraddon.modules.combat.MaceKill",
            "net.venomous.meteoraddon.modules.combat.ProjectilePuncher",
            "net.venomous.meteoraddon.modules.combat.SuperKnockback",
            "net.venomous.meteoraddon.modules.combat.SwordBlock",
            "net.venomous.meteoraddon.modules.combat.TargetObsidian",
            "net.venomous.meteoraddon.modules.combat.TickBase",
            "net.venomous.meteoraddon.modules.combat.TimerRange",
            "net.venomous.meteoraddon.modules.combat.TpAura",
            "net.venomous.meteoraddon.modules.combat.AnchorMacro",
            "net.venomous.meteoraddon.modules.combat.AntiTrap",
            "net.venomous.meteoraddon.modules.combat.VAutoCity",
            "net.venomous.meteoraddon.modules.combat.AutoDoubleHand",
            "net.venomous.meteoraddon.modules.combat.AutoInvTotem",
            "net.venomous.meteoraddon.modules.combat.BreachSwap",
            "net.venomous.meteoraddon.modules.combat.KillAura",
            "net.venomous.meteoraddon.modules.combat.PopCounter",
            "net.venomous.meteoraddon.modules.combat.ShieldBreaker",
            "net.venomous.meteoraddon.modules.combat.SwordPlaceObsidian",
            "net.venomous.meteoraddon.modules.combat.WindPearlMacro",
            "net.venomous.meteoraddon.modules.combat.VelocityPlus",
            "net.venomous.meteoraddon.modules.detection.AntiCheatDetect",
            "net.venomous.meteoraddon.modules.detection.AntiStaff",
            "net.venomous.meteoraddon.modules.detection.SpectatorAlert",
            "net.venomous.meteoraddon.modules.detection.AntiVanish",
            "net.venomous.meteoraddon.modules.detection.PlayerDetection",
            "net.venomous.meteoraddon.modules.esp.InvisESP",
            "net.venomous.meteoraddon.modules.esp.LightESP",
            "net.venomous.meteoraddon.modules.esp.SkeletonESP",
            "net.venomous.meteoraddon.modules.exploit.AbortBreaking",
            "net.venomous.meteoraddon.modules.exploit.AntiReducedDebugInfo",
            "net.venomous.meteoraddon.modules.exploit.ClickTp",
            "net.venomous.meteoraddon.modules.exploit.Damage",
            "net.venomous.meteoraddon.modules.exploit.Disabler",
            "net.venomous.meteoraddon.modules.exploit.ExtendedFirework",
            "net.venomous.meteoraddon.modules.exploit.Kick",
            "net.venomous.meteoraddon.modules.exploit.MoreCarry",
            "net.venomous.meteoraddon.modules.exploit.NameCollector",
            "net.venomous.meteoraddon.modules.exploit.NoPitchLimit",
            "net.venomous.meteoraddon.modules.exploit.PingSpoof",
            "net.venomous.meteoraddon.modules.exploit.PortalExploits",
            "net.venomous.meteoraddon.modules.exploit.ResetVL",
            "net.venomous.meteoraddon.modules.exploit.SleepWalker",
            "net.venomous.meteoraddon.modules.exploit.TimeShift",
            "net.venomous.meteoraddon.modules.exploit.VAntiHunger",
            "net.venomous.meteoraddon.modules.exploit.BungeeCordSpoof",
            "net.venomous.meteoraddon.modules.exploit.ChorusExploit",
            "net.venomous.meteoraddon.modules.exploit.VGhostHand",
            "net.venomous.meteoraddon.modules.exploit.VehicleOneHit",
            "net.venomous.meteoraddon.modules.fun.DankBobbing",
            "net.venomous.meteoraddon.modules.fun.Derp",
            "net.venomous.meteoraddon.modules.fun.Twerk",
            "net.venomous.meteoraddon.modules.fun.Vomit",
            "net.venomous.meteoraddon.modules.misc.ArmorWarning",
            "net.venomous.meteoraddon.modules.misc.AutoChatGame",
            "net.venomous.meteoraddon.modules.misc.AutoPearl",
            "net.venomous.meteoraddon.modules.misc.Chat",
            "net.venomous.meteoraddon.modules.misc.ElytraSwap",
            "net.venomous.meteoraddon.modules.misc.FarmlandTremble",
            "net.venomous.meteoraddon.modules.misc.FlagCheck",
            "net.venomous.meteoraddon.modules.misc.GUICloser",
            "net.venomous.meteoraddon.modules.misc.InventoryTracker",
            "net.venomous.meteoraddon.modules.misc.ItemOrganizer",
            "net.venomous.meteoraddon.modules.misc.LatencyAlerts",
            "net.venomous.meteoraddon.modules.misc.MiddleClickAction",
            "net.venomous.meteoraddon.modules.misc.Notifier",
            "net.venomous.meteoraddon.modules.misc.PearlTools",
            "net.venomous.meteoraddon.modules.misc.SafeLogout",
            "net.venomous.meteoraddon.modules.misc.SoundESP",
            "net.venomous.meteoraddon.modules.misc.Spammer",
            "net.venomous.meteoraddon.modules.misc.TNTTimer",
            "net.venomous.meteoraddon.modules.misc.TargetLock",
            "net.venomous.meteoraddon.modules.misc.Teams",
            "net.venomous.meteoraddon.modules.misc.TextFieldProtect",
            "net.venomous.meteoraddon.modules.misc.TrueSight",
            "net.venomous.meteoraddon.modules.misc.AntiBot",
            "net.venomous.meteoraddon.modules.misc.AutoEZ",
            "net.venomous.meteoraddon.modules.misc.AutoRequeue",
            "net.venomous.meteoraddon.modules.misc.BookBot",
            "net.venomous.meteoraddon.modules.misc.Choir",
            "net.venomous.meteoraddon.modules.misc.CoordSnapper",
            "net.venomous.meteoraddon.modules.misc.FreecamMining",
            "net.venomous.meteoraddon.modules.misc.HideScoreboard",
            "net.venomous.meteoraddon.modules.misc.NameProtect",
            "net.venomous.meteoraddon.modules.misc.NoBlockInteract",
            "net.venomous.meteoraddon.modules.misc.MovementDebug",
            "net.venomous.meteoraddon.modules.misc.PacketLogger",
            "net.venomous.meteoraddon.modules.movement.AntiBounce",
            "net.venomous.meteoraddon.modules.movement.AssertDominance",
            "net.venomous.meteoraddon.modules.movement.Clip",
            "net.venomous.meteoraddon.modules.movement.Fly",
            "net.venomous.meteoraddon.modules.movement.NoJumpDelay",
            "net.venomous.meteoraddon.modules.movement.NoPush",
            "net.venomous.meteoraddon.modules.movement.Phase",
            "net.venomous.meteoraddon.modules.movement.SnapTap",
            "net.venomous.meteoraddon.modules.movement.Speed",
            "net.venomous.meteoraddon.modules.movement.VBlink",
            "net.venomous.meteoraddon.modules.movement.BoatPhase",
            "net.venomous.meteoraddon.modules.movement.HoleSnap",
            "net.venomous.meteoraddon.modules.movement.RoboWalk",
            "net.venomous.meteoraddon.modules.network.GlobalNetworkInterceptor",
            "net.venomous.meteoraddon.modules.player.NoFallPlus",
            "net.venomous.meteoraddon.modules.render.AntiBlind",
            "net.venomous.meteoraddon.modules.render.NewChunks",
            "net.venomous.meteoraddon.modules.utility.AntiVoid",
            "net.venomous.meteoraddon.modules.utility.AutoBreakSelected",
            "net.venomous.meteoraddon.modules.utility.AutoToolPlus",
            "net.venomous.meteoraddon.modules.world.NoSignScreen",
            "net.venomous.meteoraddon.modules.world.Scaffold",
            "net.venomous.meteoraddon.modules.world.TimerPlus",
            "net.venomous.meteoraddon.modules.world.VAirPlace",
            "net.venomous.meteoraddon.modules.world.AutoFarm",
            "net.venomous.meteoraddon.modules.world.BambooPlanter"
    );

    /**
     * Guards against the list above silently drifting from the source tree. The expected set is
     * derived from disk rather than a hardcoded count.
     */
    @Test
    void testModuleListMatchesSourceTree() throws Exception {
        java.nio.file.Path root = java.nio.file.Path.of("src/main/java/net/venomous/meteoraddon/modules");
        java.util.Set<String> ignored = java.util.Set.of("ModuleRegistry");

        java.util.Set<String> onDisk;
        try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(root)) {
            onDisk = paths
                .filter(p -> p.getFileName().toString().endsWith(".java"))
                .map(p -> p.getFileName().toString().replace(".java", ""))
                .filter(n -> !ignored.contains(n))
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        }

        java.util.Set<String> listed = ALL_MODULE_CLASSES.stream()
            .map(c -> c.substring(c.lastIndexOf('.') + 1))
            .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));

        java.util.Set<String> missing = new java.util.TreeSet<>(onDisk);
        missing.removeAll(listed);
        assertTrue(missing.isEmpty(), "Modules on disk missing from ALL_MODULE_CLASSES: " + missing);

        java.util.Set<String> stale = new java.util.TreeSet<>(listed);
        stale.removeAll(onDisk);
        assertTrue(stale.isEmpty(), "ALL_MODULE_CLASSES lists modules with no source file: " + stale);
    }

    @Test
    void testModuleClassesStructureAndAnnotations() throws Exception {
        for (String className : ALL_MODULE_CLASSES) {
            Class<?> clazz = Class.forName(className);

            // Must be public class
            assertTrue(Modifier.isPublic(clazz.getModifiers()), className + " must be public");

            // Must have a public no-arg constructor
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            assertTrue(Modifier.isPublic(ctor.getModifiers()), className + " constructor must be public");

            // Check event handler methods
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(EventHandler.class)) {
                    assertEquals(1, method.getParameterCount(), "EventHandler method " + method.getName() + " in " + className + " must have exactly 1 parameter");
                    Class<?> paramType = method.getParameterTypes()[0];
                    assertNotNull(paramType, "Event parameter type must not be null");
                }
            }

            // Check Setting fields
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getName().startsWith("sg")) {
                    assertTrue(Modifier.isPrivate(field.getModifiers()) || Modifier.isProtected(field.getModifiers()) || Modifier.isPublic(field.getModifiers()));
                }
            }
        }
    }
}
