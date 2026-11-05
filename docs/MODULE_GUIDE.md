# Module Guide

Adding a module takes **one new Java file**. There is no registry to edit: `ModuleRegistry`
scans `net.venomous.meteoraddon.modules` at startup and registers every concrete `Module`
subclass it finds.

## 1. Choose the package

Put the class in the category package that matches its behaviour:

```
src/main/java/net/venomous/meteoraddon/modules/
├── combat/      PvP, aim, aura, macros
├── detection/   staff/anticheat/player detection
├── esp/         visual entity overlays
├── exploit/     game-mechanic abuse
├── fun/         cosmetic/troll modules
├── misc/        chat, inventory, automation, alerts
├── movement/    flight, speed, phase, clip
├── network/     lag, backtrack, packet interception
├── player/      player-state helpers
├── render/      world/render effects
├── utility/     building/mining helpers
└── world/       farming, scaffolding, world interaction
```

Subpackages of `modules` are scanned recursively, so deeper grouping is allowed. Keep the class
name equal to the file name, as Java requires.

## 2. Write the class

```java
package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.venomous.meteoraddon.VenomousAddon;

public class AutoExample extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> announce = sgGeneral.add(new BoolSetting.Builder()
        .name("announce")
        .description("Send a chat message when the module acts.")
        .defaultValue(true)
        .build()
    );

    public AutoExample() {
        super(VenomousAddon.CATEGORY, "auto-example", "Does one thing well.");
    }

    @Override
    public void onActivate() {
        // Reset per-session state here. No world access: modules are constructed at startup.
    }

    @Override
    public void onDeactivate() {
        // Cancel queued work, release keys, clear references.
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (!announce.get()) return;
        // Per-tick behaviour.
    }
}
```

Requirements enforced by the registry and the structure tests:

- `public` class with a `public` no-argument constructor.
- `super(category, id, description)` — the id must be unique, lowercase, and kebab-cased.
- No heavy work, world access, or packet sending in the constructor; the registry builds every
  module during addon initialisation.
- `@EventHandler` methods take exactly one event parameter.
- Every setting you declare must actually be read somewhere; `DeadSettingTest` fails otherwise.

## 3. Do not edit `ModuleRegistry`

The old per-module `Modules.get().add(new X());` list is gone. A new module is picked up because
its class is under the scanned package and can be constructed. If a module does **not** appear
in game, check in this order:

1. Is the class concrete (not `abstract`) and non-anonymous?
2. Is the constructor `public` and no-argument?
3. Is the source under `net.venomous.meteoraddon.modules`?
4. Did startup throw `IllegalStateException("Cannot register ...")`? The cause is the constructor
   failure.

## 4. Update the generated module index

```bash
./gradlew generateModulesDoc        # Windows: .\gradlew.bat generateModulesDoc
```

This rewrites `docs/MODULES.md` with the current package/module counts and descriptions. Commit
the result. CI regenerates the file and fails if the committed copy is stale.

## 5. Verify

```bash
./gradlew compileJava
./gradlew test
./gradlew generateModulesDoc
git diff --exit-code -- docs/MODULES.md
```

Add unit tests for any logic that can run without a live client (math, gates, rules, decision
helpers). See [TESTING.md](TESTING.md). The full client behaviour still needs a manual
`./gradlew runClient` check, because the unit-test classpath does not start Minecraft.

## 6. Safety levels

`net.venomous.meteoraddon.safety.SafetyLevel` classifies modules as `SAFE`, `RISKY`, or `BAN`,
and `SafetyManager` decides whether a level is allowed. The default for a module that declares
nothing is `SAFE`. Track A provides the policy API; per-module overrides and enforcement are
Track B work, so do not add ad-hoc bypasses or hard-code a level check in a module.
