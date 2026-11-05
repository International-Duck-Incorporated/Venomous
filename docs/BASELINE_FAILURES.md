# Baseline test failures

Recorded: **2026-11-05**
Command: `./gradlew test --no-daemon`
Code state: Track A commits `399c305` (A1), `f9cefdc` (A2), `985de1b` (A3–A8) plus the
`SafetyManager` empty-allow-list fix.

```
360 tests completed, 10 failed, 1 skipped
```

This file is the hand-off to **Track B (LLM 2)** for **B6**. Track A does not modify
`src/test/**` or module sources, so none of these are fixed here.

## Summary

| # | Test class | Test method | Introduced by | Owner |
| --- | --- | --- | --- | --- |
| 1 | `OrphanModuleTest` | `everyModuleEitherRunsSomethingOrIsCalledFromElsewhere` | pre-existing | Track B |
| 2 | `OrphanModuleTest` | `theInertAllowlistHasNotDrifted` | pre-existing | Track B |
| 3 | `ModuleStructureTest` | `testModuleListMatchesSourceTree` | pre-existing | Track B |
| 4 | `PacketGatesTest` | `packetGatesModuleExistsAndRegistered` | pre-existing | Track B |
| 5 | `ModuleRegistryIntegrityTest` | `testRegistryHasNoDuplicates` | A1 registry migration | Track B |
| 6 | `ModuleRegistryIntegrityTest` | `testEveryModuleSourceFileIsRegistered` | A1 registry migration | Track B |
| 7 | `BedAuraTest` | `moduleIsRegisteredAndLoadable` | A1 registry migration | Track B |
| 8 | `BowAimbotTest` | `moduleIsRegisteredAndLoadable` | A1 registry migration | Track B |
| 9 | `MaceAuraTest` | `moduleIsRegisteredAndLoadable` | A1 registry migration | Track B |
| 10 | `TriggerBotTest` | `moduleIsRegisteredAndLoadable` | A1 registry migration | Track B |

Pre-refactor baseline, for comparison: **4 failed** (rows 1–4). Rows 5–10 are the expected
consequence of replacing the hand-maintained registry text with a reflection scanner; those
tests parse `ModuleRegistry.java` for `Modules.get().add(new X());` lines and must be updated to
test behaviour (the scanner) instead of source text.

## DeadSettingTest

**Result: PASS.** The scanner reports no module with a declared-and-never-read setting, and
`STUB_MODULES` is empty. Nothing to hand to B6 from this test.

## OrphanModuleTest

Two failures, both naming the same module:

| Module | Failure | Why |
| --- | --- | --- |
| `Chams` | `everyModuleEitherRunsSomethingOrIsCalledFromElsewhere` | No `@EventHandler`, no non-empty `onActivate()`, and nothing outside the registry references it. It is currently inert. |
| `Chams` | `theInertAllowlistHasNotDrifted` | The inert allowlist is empty but the detector found `Chams.java`, so the allowlist and reality disagree. |

`noModuleExposesAnApiThatNothingCalls` and `theOrphanAllowlistHasNotDrifted` **pass**
(`KNOWN_ORPHANED_API` is empty). A transient failure in these two was caused by a deprecated
`ModuleRegistry.register()` alias; that alias was removed, so the remaining orphan failures are
only the pre-existing `Chams` ones.

**B6 decision for Chams:** either wire it to a real hook (an event handler or activation work)
or add `Chams.java` to `KNOWN_INERT_MODULES` deliberately. Track A recommends wiring it, since
the module exists and has settings.

## ModuleStructureTest

```
testModuleListMatchesSourceTree:
Modules on disk missing from ALL_MODULE_CLASSES: [Chams]
```

`ALL_MODULE_CLASSES` in `ModuleStructureTest` lists 123 fully-qualified classes but omits
`net.venomous.meteoraddon.modules.render.Chams` (124 module files exist on disk). Add it
(Track B owns `src/test/`).

## PacketGatesTest

```
packetGatesModuleExistsAndRegistered:
ModuleRegistry references PacketGates ==> expected: <true> but was: <false>
```

This test asserts that the registry source mentions `PacketGates`, a utility. It is unrelated to
registration and was already failing before A1. With the registry now reflection-based, the
assertion can never be meaningful: `PacketGates` is not a module and should not appear in
`ModuleRegistry.java`. B6 should rewrite the check to assert the utility exists and is referenced
by the modules that use it.

## ModuleRegistryIntegrityTest (A1-related)

| Test | Message |
| --- | --- |
| `testRegistryHasNoDuplicates` | `Registry should register at least one module` — `registeredModules()` returns an empty set because the regex finds no `Modules.get().add(new X());` lines. |
| `testEveryModuleSourceFileIsRegistered` | Lists all 124 module classes on disk as "never registered". |

`testRegistryImportsMatchRegistrations` passes vacuously (both sets are empty).

B6 should replace the text parser with a behavioural check. Two workable options:

1. Assert that every `.java` file under `modules/` (excluding `ModuleRegistry`) declares
   `extends Module`, is concrete, and has a public no-arg constructor. This matches what the
   runtime scanner requires without starting Meteor.
2. Load the compiled classes and run the same `Reflections` predicate. This needs the Minecraft
   and Meteor classpath, which the test runtime already has, but may be heavier than necessary.

Keeping a hardcoded list would reintroduce the merge-conflict problem the migration removed.

## Per-module registration tests (A1-related)

These four tests assert their module name appears in `ModuleRegistry.java`:

| Test | Assertion |
| --- | --- |
| `BedAuraTest.moduleIsRegisteredAndLoadable` | `BedAura must be registered in ModuleRegistry` |
| `BowAimbotTest.moduleIsRegisteredAndLoadable` | `BowAimbot must be registered in ModuleRegistry` |
| `MaceAuraTest.moduleIsRegisteredAndLoadable` | `MaceAura must be registered in ModuleRegistry` |
| `TriggerBotTest.moduleIsRegisteredAndLoadable` | `TriggerBot must be registered in ModuleRegistry` |

They all passed before A1 because the class names appeared as `new BedAura()` etc. With
auto-registration they fail even though the modules are discovered and constructed at runtime
(verified: 124/124 classes discovered, every one has a no-arg constructor).

B6 should replace these with a source-tree/reflection check: assert the module class exists in
the expected `modules.<package>` directory, extends `Module`, is concrete, and has a public
no-arg constructor. That is the actual contract the scanner enforces.

## What Track A verified instead

- Manual registry before migration: **124** entries.
- Reflection scanner after migration: **124** discovered types, **124** registrable concrete
  classes, all with a no-arg constructor (run against the dev runtime classpath).
- `./gradlew build -x test` succeeds and produces the jar with bundled Reflections/Javassist.
- `./gradlew test jacocoTestReport` generates coverage; the test failures above are the only
  blockers to a fully green build.
