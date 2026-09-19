# Venomous

[![Build Status](https://github.com/International-Duck-Incorporated/Venomous/actions/workflows/build.yml/badge.svg)](https://github.com/International-Duck-Incorporated/Venomous/actions/workflows/build.yml)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-blue.svg)](https://www.minecraft.net/)
[![Meteor Client](https://img.shields.io/badge/Meteor%20Client-26.2--SNAPSHOT-purple.svg)](https://meteorclient.com/)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://adoptium.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric%20%3E%3D0.19.3-black.svg)](https://fabricmc.net/)

**Venomous** is an optimized, feature-rich Meteor Client addon designed for modern Minecraft anarchy, combat, and utility environments, ported to **Minecraft 26.2** with Java 25.

---

## Features

### Combat & PvP
- **CrystalPvP**: Optimized End Crystal placement and break sequences with anti-suicide checks and custom delays.
- **VAutoCity**: Automatically detects, supports, and destroys enemy surround blocks (Obsidian, Crying Obsidian, Ender Chest, Netherite) to expose targets.
- **VPopCounter**: Counts and announces totem pops and kills with customizable client-side and public chat formatting.
- **VKillAura & TpAura**: Highly configurable combat auras supporting custom targeting algorithms and weak anticheat bypasses.
- **VMaceKill & MaceKill**: Specialized attack optimization and height-damage scaling for the Mace.
- **VAnchorMacro**: Automated Respawn Anchor charging (Glowstone) and detonation routines.
- **VAutoInvTotem & VAutoDoubleHand**: Automated off-hand totem replenishment, inventory opening/closing macros, and dual-hand item usage.
- **VBreachSwap**: Automated hot-swapping to Breach weapons when opponents raise shields.
- **VShieldBreaker & VShieldBypass**: Bypass or disable opponent shields during melee engagements.
- **CriticalsPlus, SuperKnockback, VelocityPlus**: Combat hit enhancers, jump critical modifiers, and customizable velocity reduction.
- **AutoPotion & AutoShield**: Automatic potion splash timing and reactive shield blocking.
- **Aimbot**: Smooth and projectile-predicted target tracking.

### Exploits & Anticheat Bypasses
- **VNoPortalHitbox**: Injects into portal collision shapes to attack entities and break blocks through Nether portals.
- **Disabler**: Suppresses specific anticheat checks across multiple server engines.
- **PingSpoof**: Injects artificial round-trip latency to desync server-side lag compensation.
- **TimeShift**: Packet and tick modulation to manipulate client-server simulation time.
- **VBungeeCordSpoof**: Injects BungeeCord proxy headers into client handshakes.
- **VChorusExploit & VPortalGodMode**: Chorus fruit teleportation desyncs and portal transition invulnerability.
- **VAntiHunger**: Suppresses player sprint and movement exhaustion packets to preserve hunger.
- **VGhostHand**: Allows raycasting and interacting with containers and tile entities through solid walls.
- **GlobalNetworkInterceptor**: Low-level packet queue serializer with kinematic limits and rotation quantization.
- **NoPitchLimit**: Unlocks pitch rotation beyond standard -90 deg to 90 deg limits.

### Movement & Physics
- **VHoleSnap**: Automatically detects the nearest 1x1 blast-resistant safe hole and smoothly pulls the player into center alignment.
- **Fly & Speed**: Versatile movement engines tuned for bypasses (Grim boat flight, vanilla speed, step assistance).
- **VBlink & Phase**: Movement packet caching and collision phased passage through blocks.
- **VBoatPhase**: Phasing and clip mechanics specialized for boat mounting.
- **SnapTap**: Rapid directional strafing input cancellation for keyboard movement.
- **AssertDominance**: Target orbiting and position lock.
- **AntiBounce**: Prevents bounce effects from slime blocks and beds.
- **NoPush & NoFallPlus**: Push prevention and fall damage packet nullification.

### World, Automation & Building
- **VAirPlace**: Enables placing blocks directly in mid-air on empty raycast vectors.
- **Scaffold & VAutoFarm**: Block bridge placement and automated crop harvest/replant.
- **ItemOrganizer & ContainerAutomator**: Automatic chest sorting and categorized container deposit.
- **VFreecamMining**: Allows mining and interacting with distant blocks directly from the freecam perspective.
- **FarmlandTremble**: Protects farmland from destruction when stepping or landing.
- **NoSignScreen**: Suppresses the sign text editing GUI when placing signs.

### ESP & Render
- **VNewChunks**: Visualizes newly generated chunks versus cached chunks using palette detection.
- **VSkeletonESP**: Skeletal entity bone rendering with custom joint colors.
- **VLightESP & VInvisESP**: Identifies block light levels and renders bounding boxes for invisible entities.
- **AntiBlind**: Suppresses blindness, darkness, and nausea post-processing effects.
- **DankBobbing**: Configurable view-bobbing motion scaling.

### Detection & Alerts
- **AntiCheatDetect**: Real-time packet transaction analysis to identify server anticheat software (Grim, Vulcan, Matrix, Intave, Polar, Verus, Watchdog).
- **AntiStaff & SpectatorAlert**: Notifies when vanished administrators or GameMode 3 spectators enter tracking range.
- **VAntiVanish & VPlayerDetection**: Detects hidden players and provides proximity warnings.
- **FlagCheck**: Detects anticheat movement flags and server-side rubberband setbacks.

### Misc & Chat
- **VAutoEZ**: Sends customizable taunt and GG messages with kill streak tracking upon eliminating targets.
- **ElytraSwap**: Fast single-key swap between chestplate armor and Elytra wings.
- **MiddleClickAction**: Quick-add or remove friends by clicking on players with the middle mouse button.
- **InventoryTracker**: Tracks nearby player inventory states through container network packets.
- **PearlTools & AutoPearl**: Ender pearl trajectory simulation, landing spot projection, and danger alerts.
- **VBookBot & VNotifier**: Automated book authoring and rich game event notifications.
- **VNameProtect & Chat**: In-game identity masking, custom chat formatting, and timestamping.
- **TextFieldProtect**: Protects sensitive input fields against paste and text leaks.

---

## Installation

1. Install **Minecraft 26.2** with **Fabric Loader** (`>= 0.19.3`).
2. Download the latest **[Meteor Client](https://meteorclient.com/)** for `26.2-SNAPSHOT`.
3. Download the latest `venomous-*.jar` from the **[Releases](https://github.com/International-Duck-Incorporated/Venomous/releases)** page (or build from source).
4. Place both JARs into your `.minecraft/mods` directory.
5. Launch the game using Java 25.

---

## Building from Source

### Prerequisites
- **JDK 25** or higher installed and configured on your `PATH`.
- Git.

### Compilation
Clone the repository and run the Gradle wrapper:

```bash
git clone https://github.com/International-Duck-Incorporated/Venomous.git
cd Venomous
gradle build --no-daemon
```

The built JAR file will be located at:
```
build/libs/venomous-7.0.0.jar
```

---

## License & Disclaimer

Venomous is distributed for educational and testing purposes on private and authorized servers. Respect server rules and terms of service.
