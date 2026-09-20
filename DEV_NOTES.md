# Venomous — dev session notes

## Environment (this workspace)

The sandbox ships JDK 11, which cannot build this project. A JDK 25 + Gradle 9.6.1
toolchain was installed under `~/.cache/toolchain/`. Note that `.cache/` is **not**
persisted in workspace snapshots, so after a cold start re-run:

```bash
curl -sSL -o jdk.tar.gz "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse"
```

For day-to-day work you only need a JDK 25 on PATH — Gradle itself now comes from
the committed wrapper:

```bash
source ~/env.sh     # sets JAVA_HOME + PATH
cd Venomous && ./gradlew build
```

Full build (compile + 10 test classes + jar) takes ~1m45s cold, ~10s warm.
Output: `build/libs/venomous-7.0.0.jar` (~576 KB).

## What changed this session

| Commit | Change |
|---|---|
| `2a4fbd1` | Committed the Gradle wrapper (9.6.1) |
| `3ffb4ef` | Untracked `logs/latest.log`; ignore `logs/` and `*.patch` |
| `f0cd8ee` | CI uses `./gradlew`; bumped checkout v7, setup-java v6, download-artifact v8 |
| `b43cc58` | `v-coord-snapper`: https-only webhooks, deferred auto-toggle |
| `c44c392` | `v-auto-ez`: null-guard `mc.player` |
| `86b49a4` | Tests derive module list from source tree instead of hardcoded `124` |

### Why these

- **Wrapper was missing.** The README documented `./gradlew build`, but neither
  `gradlew` nor `gradle/wrapper/` was committed — a fresh clone could not build.
  `.gitignore` already had the `!gradle/wrapper/gradle-wrapper.jar` negation
  waiting for it. CI papered over this by pinning `gradle-version: 9.6.1` in
  `setup-gradle`; that pin is now redundant and was removed.
- **`v-coord-snapper` leaked coords over plaintext.** `URI.create(url)` accepted
  any scheme, so a typo'd `http://` webhook sent your base coordinates
  unencrypted. Now rejected before the request is built. Separately, `toggle()`
  ran from a `finally` inside `onActivate()` — re-entering Meteor's toggle logic
  during activation. It is now deferred via `mc.execute()` and guarded on
  `isActive()`.
- **Hardcoded `124`.** Two suites asserted an exact module count, so adding a
  module failed the build with an unhelpful count mismatch. They now diff the
  source tree against `ModuleRegistry` and name the forgotten class. Verified by
  adding a dummy module: both tests fail with the class name, and pass once
  removed.

## Session 2 — behaviour fixes, de-prefixing, merges

| Commit | Change |
|---|---|
| `2a97530` | `scaffold`: place on PRE instead of POST; real per-technique profiles |
| `7bc22e8` | `hunt`: works without Baritone installed |
| `bc1ace8` | `v-click-tp`: actually teleports (sends position packets) |
| `d20cc77` | `no-push`: multi-select sources + mixins so they work at all |
| `7a642a6` | Dropped the `v-` prefix from 37 modules |
| `3f032ad` | Merged 6 duplicate modules into 3 |

### The POST placement flag

Meteor's `Rotations.rotate(yaw, pitch, ticks, callback)` runs its callback from
`SendMovementPacketsEvent.Post` — *after* the tick's movement packet is flushed.
Scaffold placed from inside that callback, so every placement packet landed after
the position packet. That ordering is precisely what anticheats fingerprint as
"placing on POST"; a vanilla client always interacts *before* moving.

Placements are now staged during the tick (`pendingNeighbor` + friends) and flushed
from `SendMovementPacketsEvent.Pre`, gated on `Rotations.serverYaw/serverPitch`
matching the staged aim within 1.5°. Stale placements expire after 4 ticks so a
target that never becomes flushable cannot wedge the module.

**The same bug pattern exists elsewhere.** Any module that passes a callback to
`Rotations.rotate` inherits it. Worth auditing `AutoCity`, `AirPlace`,
`SwordPlaceObsidian` and `AutoFarm` next.

### Why the Scaffold presets all felt identical

Every technique resolved to the same centre-of-face aim and the same delay, so the
only difference between `GrimScaffold`, `GodBridge`, `NCPNormal` etc. was which
settings were *visible*. They now differ where it matters: `techniquePitch()` gives
each a distinct placement pitch (76.5° GodBridge, 89.5° NCP, 68° Vulcan…) and
`techniqueDelay()` varies cadence. Grim's jitter is applied at stage time to defeat
`DuplicateRotPlace`.

### V-prefix audit

Extracted all 178 module names from `meteor-client-26.2-SNAPSHOT.jar` and compared.
Only **11** of 48 prefixed modules genuinely collided; 37 were prefixed for no reason
and were renamed in both class and display name. Kept: `v-air-place`, `v-anti-hunger`,
`v-anti-void`, `v-auto-city`, `v-blink`, `v-click-tp`, `v-ghost-hand`, `v-notifier`,
`v-scaffold`, `v-speed`, `v-mace-kill`→ now merged away.

Note `Modules.add` replaces by name, so reusing a Meteor name doesn't just shadow
the module — it unregisters Meteor's class and can NPE their mixins. Hence the
remaining 11 must keep the prefix.

### Merges (124 → 119 modules)

- `v-mace-kill` → `mace-kill` as `mode=FakeFall`
- `key-pearl` → `autopearl` as `mode=Instant`
- `portal-menu` + `no-portal-hitbox` + `portal-god-mode` → `portal-exploits`

### Further merge candidates — now done

| Merged | Into | As |
|---|---|---|
| `shield-bypass` | `shield-breaker` | `mode=Behind` |
| `lb-clip` | `clip` | `mode=Combined` |
| `lag-notifier` | `v-notifier` | `lag-alerts` toggle |
| `prophunt-esp` | `invis-esp` | `block-sized-boxes` toggle |

Left alone deliberately: `light-esp`, `skeleton-esp` and `sound-esp` render
genuinely different things (block light levels, player pose, sound origins) and
share no logic with the entity-hitbox ESPs.

## Session 3 — the POST bug was systemic

The Scaffold fix in session 2 turned out to be one instance of a codebase-wide
pattern. **Every** module that passed an action callback to `Rotations.rotate`
had the same defect, because the callback always runs from
`SendMovementPacketsEvent.Post`.

Added `utils/PreRotate`, which keeps Meteor's rotation handling but defers the
*action* to `SendMovementPacketsEvent.Pre`, gated on `Rotations.serverYaw/Pitch`
matching the requested aim within 1.5° and expiring after 5 ticks.

Converted: `auto-potion`, `v-auto-city`, `pearl-wind-macro` (×2),
`auto-break-selected`, `auto-farm` (×2), and `ContainerAutomator`.
`Hunt` uses the two-arg aim-only overload and was already fine.

`PostPlacementRegressionTest` guards this. It is a brace-matching scanner rather
than a regex — lambda bodies contain both `;` and `)`, which defeats any
reasonable pattern. **The first version of this test was a regex and silently
passed against a deliberately reintroduced bug**; the scanner version was verified
to fail correctly, and carries a self-test asserting it can still detect one.

### Module count

124 → 116 across both sessions, with no behaviour removed.

## Audit findings (no action taken)

- **Module-name collisions: none.** Checked all 124 registered display names
  against the 178 module names extracted from `meteor-client-26.2-SNAPSHOT.jar`
  — zero clashes, so the `v` prefix work in `b0128d9` is complete. Three *class*
  names still shadow Meteor (`AntiVoid`, `Scaffold`, `Speed`) but they are in
  separate packages and register distinct display names, so this is harmless.
- **`ServerAnalyzer`** lives under `modules/detection/` but is a helper owned by
  `AntiCheatDetect`, not a `Module`. It is excluded via `NON_MODULE_CLASSES` in
  the tests. Consider moving it to `utils/` so the package only holds modules.
- **7 empty catch blocks** (4 in `AutoChatGame`, 1 each in `VBreachSwap`,
  `ServerAnalyzer`, `ArmorWarning`) — silent failure swallowing, worth revisiting.
- **6 modules define `onActivate` without `onDeactivate`** while touching
  position/velocity/timer state: `TargetObsidian`, `VAnchorMacro`,
  `SpectatorAlert`, `LbClip`, `AssertDominance`, `AntiVoid`. Each needs a
  per-module look at whether state survives toggle-off.
- **127 of 139 source files use 3-space indentation**, the signature of
  decompiler output (Vineflower is referenced in `.gitignore`). Worth a one-shot
  reformat to 4-space to match the hand-written files — best done as an isolated
  commit so it does not pollute future diffs.
- **No LICENSE file.** Until one exists the code is all-rights-reserved by
  default, which blocks contributions and redistribution.

## Suggested next steps

1. Decide a license and add it.
2. Squash the indentation inconsistency in one dedicated commit.
3. Work through the 6 missing-`onDeactivate` modules.
4. Adopt shorter commit subjects — several existing commits embed an entire
   `git status` dump as the message, which makes `git log` unreadable.
