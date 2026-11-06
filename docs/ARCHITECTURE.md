# Architecture

Venomous is a client-only Fabric addon for [Meteor Client](https://meteorclient.com/). This
document describes how the addon boots, how modules are discovered, and how module actions reach
the server. It intentionally stays at the level a contributor needs before changing code.

## Runtime shape

| Piece | Value |
| --- | --- |
| Mod id | `venomous` |
| Environment | client only |
| Entrypoint | `meteor` → `net.venomous.meteoraddon.VenomousAddon` |
| Minecraft | 26.2 |
| Meteor Client | 26.2-SNAPSHOT |
| Fabric Loader | 0.19.3+ |
| Java | 25 |
| License | GPL-3.0-or-later |

`VenomousAddon` extends Meteor's `MeteorAddon` and declares two categories:

- `Venomous` — the normal module list.
- `Venomous OP` — modules that are deliberately separated because of risk or destructive
  potential.

Fabric loads the mod, Meteor discovers the `meteor` entrypoint, and then calls the addon hooks.

## Startup sequence

Meteor calls the addon hooks:

- `onRegisterCategories()` registers both categories with `Modules.registerCategory(...)`.
- `onInitialize()` runs the addon startup:
  - `ShutdownReaper.init()` starts the daemon watcher that forces a clean JVM exit once
    `main()` returns (see its class Javadoc for why).
  - `PreRotate.init()` subscribes the deferred-rotation dispatcher to Meteor's event bus.
  - `ModuleRegistry.registerAll()` discovers and constructs every module (see below).
  - `SafetyManager.setEnabled(SafetyLevel.RISKY, false)` records the default safety policy.

Meteor then owns the resulting `Module` instances: it persists enabled state and settings and
dispatches events to their `@EventHandler` methods.

## Reflection-based module registry

`ModuleRegistry` no longer contains a list of module classes. It scans
`net.venomous.meteoraddon.modules` with
[Reflections](https://github.com/ronmamo/reflections) 0.10.2:

```java
new Reflections(BASE_PACKAGE)
    .getSubTypesOf(Module.class)
    .stream()
    .filter(cls -> !Modifier.isAbstract(cls.getModifiers()))
    .filter(cls -> !cls.isAnonymousClass())
    .sorted(Comparator.comparing(Class::getName))
    .forEach(ModuleRegistry::register);
```

`register` calls `cls.getDeclaredConstructor().newInstance()` and passes the result to
`Modules.get().add(...)`. Registration order is deterministic because classes are sorted by
fully-qualified name.

Consequences:

- Adding a module is a single new source file; there is no registry edit and therefore no
  registry merge conflict.
- The class must be concrete, non-anonymous, and have a public no-argument constructor.
- Discovery is limited to the `modules` package and its subpackages, so Meteor's own modules and
  the addon's `utils`/`mixin`/`safety` classes are never considered.
- A module that cannot be constructed fails startup with
  `IllegalStateException("Cannot register <class>", cause)` instead of silently disappearing.

Reflections needs Javassist to read class files. Both libraries are bundled into the mod jar as
Fabric jar-in-jar entries (`META-INF/jars/reflections-0.10.2.jar` and
`META-INF/jars/javassist-3.28.0-GA.jar`), so users do not install them separately.

Verification at the time of the registry migration: **124** manual registrations before,
**124** discovered/registrable classes after. The tree now holds **123** modules following the
dead-code prune.

## Module lifecycle

A module is a Meteor `Module` subclass with a no-argument constructor:

```java
public MyModule() {
    super(VenomousAddon.CATEGORY, "my-module", "One-line description.");
}
```

Settings are declared as `SettingGroup`/`Setting` fields and registered through the group builder.
Meteor serialises them per module.

Lifecycle hooks used by this addon:

| Hook | When it runs | Typical use |
| --- | --- | --- |
| constructor | once, at addon startup | `super(...)`, setting declarations only |
| `onActivate()` | module toggled on | reset counters, subscribe helpers, send one-shot packets |
| `onDeactivate()` | module toggled off | clear state, release keys, cancel queued actions |
| `@EventHandler` methods | when the event is posted on Meteor's bus | tick/render/packet-adjacent logic |

Because the registry constructs every module at startup, constructors must not perform world
access, send packets, or assume a player exists.

## Event and packet flow

Meteor uses the Orbit event bus. Modules subscribe implicitly through `@EventHandler`; static
helpers such as `PreRotate` subscribe explicitly with `MeteorClient.EVENT_BUS.subscribe(...)`.

The important events for combat/movement code are:

- `TickEvent.Pre` / `TickEvent.Post` — before and after the client tick.
- `SendMovementPacketsEvent.Pre` / `.Post` — around the player-position/rotation packet flush.
- render events (`Render2DEvent`, `Render3DEvent`, …) for ESP/HUD work.

### Rotation ordering (`PreRotate`)

Meteor's `Rotations.rotate(..., Runnable)` runs the callback from
`SendMovementPacketsEvent.Post`, i.e. after the tick's movement packet has already been sent.
Vanilla sends the interaction first and the movement packet second; anticheats treat the inverted
order as suspicious.

`PreRotate.rotateThen(module, yaw, pitch, action)` keeps Meteor's rotation handling but queues the
action until a later `SendMovementPacketsEvent.Pre`, once the server has observed the rotation.
Queued actions are guarded by the owning module's active state (cancelled if the module is
disabled), become stale after 5 ticks, and require the spoofed rotation to be within 1.5° of the
requested one.

### Delay gates (`PacketGates`)

Modules that delay packets (for example lag/backtrack features) must not hold packets that keep
the connection alive or the world streaming. `PacketGates.isRealtimePassthrough(packet)` returns
true for:

- the entire `net.minecraft.network.protocol.common` family (keep-alives, pings, resource packs),
- `BundlePacket` / `BundleDelimiterPacket`,
- chunk-batch start/finish/acknowledgement packets,
- `null` (so a missing reference can never be swallowed).

### Mixins

`src/main/resources/venomous.mixins.json` registers nine client mixins/accessors:

- `ServerboundMovePlayerPacketAccessor` — read/write position and rotation fields.
- `LivingEntityAccessor` — access to living-entity internals.
- `NoPitchLimitMixin` — removes the vanilla pitch clamp.
- `MinecraftMixin`, `HudMixin` — client/HUD hooks.
- `NoPushEntityMixin`, `NoPushFishingHookMixin` — push cancellation.
- `LocalPlayerUseSlowMixin` — use-item slowdown overrides.
- `PlayerAttackMixin` — attack-path hooks.

Mixin compatibility level is `JAVA_25`, matching the toolchain.

## Safety levels

`net.venomous.meteoraddon.safety` provides the policy layer:

- `SafetyLevel` — `SAFE`, `RISKY`, `BAN`, declared in increasing risk order.
- `SafetyManager` — an allow-list initialised to all three levels, with
  `isAllowed(level)`, `setEnabled(level, on)`, and `disableAllAtOrAbove(level)` as a kill switch.
- `defaultLevel()` returns `SAFE`, so a module that declares nothing is treated as safe.

Track A provides the API only. Enforcement (consulting the module's level before it toggles on)
lives in the module base class and is Track B's responsibility. `VenomousAddon` currently records
the default policy and logs the enabled set.

## Build and packaging

- Gradle 9.6.1 with Kotlin DSL, Fabric Loom 1.17, Java 25 toolchain.
- `./gradlew build` compiles, tests, and produces `build/libs/venomous-<version>.jar`.
- `./gradlew test jacocoTestReport` runs tests and writes JaCoCo XML/HTML reports.
- `./gradlew generateModulesDoc` regenerates `docs/MODULES.md` from the source tree.
- The jar embeds `LICENSE_venomous` (GPL-3.0 §4) and the two runtime libraries listed above.
