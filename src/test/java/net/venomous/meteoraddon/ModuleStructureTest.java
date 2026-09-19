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
            "net.venomous.meteoraddon.modules.combat.Hunt",
            "net.venomous.meteoraddon.modules.combat.MaceKill",
            "net.venomous.meteoraddon.modules.combat.ProjectilePuncher",
            "net.venomous.meteoraddon.modules.combat.SuperKnockback",
            "net.venomous.meteoraddon.modules.combat.SwordBlock",
            "net.venomous.meteoraddon.modules.combat.TargetObsidian",
            "net.venomous.meteoraddon.modules.combat.TickBase",
            "net.venomous.meteoraddon.modules.combat.TimerRange",
            "net.venomous.meteoraddon.modules.combat.TpAura",
            "net.venomous.meteoraddon.modules.combat.VAnchorMacro",
            "net.venomous.meteoraddon.modules.combat.VAntiTrap",
            "net.venomous.meteoraddon.modules.combat.VAutoCity",
            "net.venomous.meteoraddon.modules.combat.VAutoDoubleHand",
            "net.venomous.meteoraddon.modules.combat.VAutoInvTotem",
            "net.venomous.meteoraddon.modules.combat.VBreachSwap",
            "net.venomous.meteoraddon.modules.combat.VKeyPearl",
            "net.venomous.meteoraddon.modules.combat.VKillAura",
            "net.venomous.meteoraddon.modules.combat.VMaceKill",
            "net.venomous.meteoraddon.modules.combat.VPopCounter",
            "net.venomous.meteoraddon.modules.combat.VShieldBreaker",
            "net.venomous.meteoraddon.modules.combat.VShieldBypass",
            "net.venomous.meteoraddon.modules.combat.VSwordPlaceObsidian",
            "net.venomous.meteoraddon.modules.combat.VWindPearlMacro",
            "net.venomous.meteoraddon.modules.combat.VelocityPlus",
            "net.venomous.meteoraddon.modules.detection.AntiCheatDetect",
            "net.venomous.meteoraddon.modules.detection.AntiStaff",
            "net.venomous.meteoraddon.modules.detection.SpectatorAlert",
            "net.venomous.meteoraddon.modules.detection.VAntiVanish",
            "net.venomous.meteoraddon.modules.detection.VPlayerDetection",
            "net.venomous.meteoraddon.modules.esp.VInvisESP",
            "net.venomous.meteoraddon.modules.esp.VLightESP",
            "net.venomous.meteoraddon.modules.esp.VSkeletonESP",
            "net.venomous.meteoraddon.modules.exploit.AbortBreaking",
            "net.venomous.meteoraddon.modules.exploit.AntiReducedDebugInfo",
            "net.venomous.meteoraddon.modules.exploit.ClickTp",
            "net.venomous.meteoraddon.modules.exploit.Damage",
            "net.venomous.meteoraddon.modules.exploit.Disabler",
            "net.venomous.meteoraddon.modules.exploit.Dupe",
            "net.venomous.meteoraddon.modules.exploit.ExtendedFirework",
            "net.venomous.meteoraddon.modules.exploit.Kick",
            "net.venomous.meteoraddon.modules.exploit.LbClip",
            "net.venomous.meteoraddon.modules.exploit.MoreCarry",
            "net.venomous.meteoraddon.modules.exploit.NameCollector",
            "net.venomous.meteoraddon.modules.exploit.NoPitchLimit",
            "net.venomous.meteoraddon.modules.exploit.PingSpoof",
            "net.venomous.meteoraddon.modules.exploit.PortalMenu",
            "net.venomous.meteoraddon.modules.exploit.ResetVL",
            "net.venomous.meteoraddon.modules.exploit.SleepWalker",
            "net.venomous.meteoraddon.modules.exploit.TimeShift",
            "net.venomous.meteoraddon.modules.exploit.VAntiHunger",
            "net.venomous.meteoraddon.modules.exploit.VBungeeCordSpoof",
            "net.venomous.meteoraddon.modules.exploit.VChorusExploit",
            "net.venomous.meteoraddon.modules.exploit.VGhostHand",
            "net.venomous.meteoraddon.modules.exploit.VNoPortalHitbox",
            "net.venomous.meteoraddon.modules.exploit.VPortalGodMode",
            "net.venomous.meteoraddon.modules.exploit.VVehicleOneHit",
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
            "net.venomous.meteoraddon.modules.misc.LagNotifier",
            "net.venomous.meteoraddon.modules.misc.MiddleClickAction",
            "net.venomous.meteoraddon.modules.misc.PearlTools",
            "net.venomous.meteoraddon.modules.misc.ProphuntESP",
            "net.venomous.meteoraddon.modules.misc.SafeLogout",
            "net.venomous.meteoraddon.modules.misc.SoundESP",
            "net.venomous.meteoraddon.modules.misc.Spammer",
            "net.venomous.meteoraddon.modules.misc.TNTTimer",
            "net.venomous.meteoraddon.modules.misc.TargetLock",
            "net.venomous.meteoraddon.modules.misc.Teams",
            "net.venomous.meteoraddon.modules.misc.TextFieldProtect",
            "net.venomous.meteoraddon.modules.misc.TrueSight",
            "net.venomous.meteoraddon.modules.misc.VAntiBot",
            "net.venomous.meteoraddon.modules.misc.VAutoEZ",
            "net.venomous.meteoraddon.modules.misc.VBookBot",
            "net.venomous.meteoraddon.modules.misc.VChoir",
            "net.venomous.meteoraddon.modules.misc.VCoordSnapper",
            "net.venomous.meteoraddon.modules.misc.VFreecamMining",
            "net.venomous.meteoraddon.modules.misc.VHideScoreboard",
            "net.venomous.meteoraddon.modules.misc.VNameProtect",
            "net.venomous.meteoraddon.modules.misc.VNoBlockInteract",
            "net.venomous.meteoraddon.modules.misc.VNotifier",
            "net.venomous.meteoraddon.modules.misc.VPacketLogger",
            "net.venomous.meteoraddon.modules.movement.AntiBounce",
            "net.venomous.meteoraddon.modules.movement.AssertDominance",
            "net.venomous.meteoraddon.modules.movement.Clip",
            "net.venomous.meteoraddon.modules.movement.Fly",
            "net.venomous.meteoraddon.modules.movement.NoPush",
            "net.venomous.meteoraddon.modules.movement.Phase",
            "net.venomous.meteoraddon.modules.movement.SnapTap",
            "net.venomous.meteoraddon.modules.movement.Speed",
            "net.venomous.meteoraddon.modules.movement.VBlink",
            "net.venomous.meteoraddon.modules.movement.VBoatPhase",
            "net.venomous.meteoraddon.modules.movement.VHoleSnap",
            "net.venomous.meteoraddon.modules.movement.VRoboWalk",
            "net.venomous.meteoraddon.modules.network.GlobalNetworkInterceptor",
            "net.venomous.meteoraddon.modules.player.NoFallPlus",
            "net.venomous.meteoraddon.modules.render.AntiBlind",
            "net.venomous.meteoraddon.modules.render.VNewChunks",
            "net.venomous.meteoraddon.modules.utility.AntiVoid",
            "net.venomous.meteoraddon.modules.utility.AutoBreakSelected",
            "net.venomous.meteoraddon.modules.utility.AutoToolPlus",
            "net.venomous.meteoraddon.modules.world.NoSignScreen",
            "net.venomous.meteoraddon.modules.world.Scaffold",
            "net.venomous.meteoraddon.modules.world.TimerPlus",
            "net.venomous.meteoraddon.modules.world.VAirPlace",
            "net.venomous.meteoraddon.modules.world.VAutoFarm"
    );

    @Test
    void testTotalModuleCount() {
        assertEquals(124, ALL_MODULE_CLASSES.size());
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
