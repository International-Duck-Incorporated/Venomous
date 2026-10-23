# Changelog

All notable changes to Venomous are documented in this file.

## [Unreleased]

## [7.18.0] - 2026-10-23

- New combat module `jump-reset`: pressing the jump key on the tick a hit lands, so the knockback
  multiplies into the jump and lands a fraction of its range. Ported from Raven B4's
  `JumpReset` module and its gate list, one boolean at a time: only a fresh growth of the hurt
  window counts as a hit (a drained window mid-combo is the same hit, not a new one), the player
  must be grounded ("a jump can only start from the ground"), the window may not be burning or
  jump-boost/poison/wither-tinted (`MobEffects` reads, Raven's own three), the movement direction
  must line up with the facing, and the three optional gates ship with Raven's defaults - attack
  key waived, walking-forward required, crosshair-on-player required.
- The port keeps Raven's landing guard: hurt time that arrives while standing on ground after
  falling further than three blocks is the fall's own damage, not a hit, and is suppressed once.
  The one-tick press is Meteor-native: the module presses `keyJump` in
  `TickEvent.Pre` only when the player was not already holding it, and releases its own press
  before the next tick's decision, so the tap covers exactly one input window. The module only
  ever releases a press it made itself - the key mapping is shared with the game's own events, so
  an unconditional release would clobber a jump the player is holding.
- Raven's knockback arc check is shipped honestly rather than copied: its `inFov(GetDirection,
  330, ...)` halves its argument to a ±165 degree tolerance and therefore passes everything except
  a movement within fifteen degrees of exactly backwards - a no-op in practice. `JumpResetRules`
  carries the wrap-aware `withinArc` with the same full-arc semantics and the same 330 default
  behind the `motion-arc` setting (documented as such), so lowering it actually rejects sideways
  and rear hits; standing still on the hurt tick - before the knockback is applied - waives the
  gate instead of being judged against yaw zero. The decision lives in `JumpResetRules`
  (`motionYawOf` follows Minecraft's yaw convention: +Z is 0, +X is -90) with unit tests in
  `JumpResetRulesTest`; every new setting is read.

## [7.17.2] - 2026-10-23

- `v-scaffold` keeps the rotation the server is told on the camera's own yaw across every tick of
  a bridging run, not only on the ticks the staging loop had a target to aim at. Meteor consumes
  each rotation it is handed in the same tick, but it also keeps the last one and re-sends it for
  up to `rotation-hold` ticks (default 4) whenever no fresh rotation is staged - writing the
  player's own rotation to that staged angle for the length of each packet, while the position
  delta in those packets was integrated by physics that ran on the live camera. Every degree the
  mouse moved since the staging is therefore a walk the server did not predict, and Grim's
  `Simulation` reports it: the 7.17.1 run collected 119 one-millimetre-scale flags in exactly
  that shape - decaying clusters that restarted with each staging gap. The gap came from the
  ticks that early-return out of every loop (no supporting face for the cell mid-jump, no block
  in the hotbar, no cell underfoot), which are precisely the ticks that skipped
  `Rotations.rotate()`. `PreRotate.followCameraYaw` now re-stages the rotation on those ticks -
  from each no-target return in `tickGrim`, `tickStandardMode` and `aimAndPlace` - with the
  camera's yaw read fresh that tick, the held aim pitch (`stagedPitch`) and its wobble, pending
  an aim within a one-second linger of the last staging. The queue is untouched, the age-out
  clock keeps running, nothing is written to the camera, there is no new setting, and the crouch
  is unchanged.
- `PreRotate.isServerReady` now honours the `yawMatters` flag it already records, whose documented
  contract ("the action waits only for the pitch to be sent") was silently ignored - a moving
  mouse held every placement back a tick, and a staged rotation was judged against a frozen
  staging yaw with a 1.5 degree tolerance, which is exactly the mouse being measured rather than
  the rotation. The readiness decision now lives in `RotationReadiness` (yaw comparison wraps
  around the 0/360 seam, tolerance inclusive on both axes) with unit tests
  (`RotationReadinessTest`, `CameraFollowTest`, and `find` coverage in `DeferredActionsTest`).

## [7.17.1] - 2026-10-22

- Ported two timing ideas from Raven B4's scaffold, with the derivations behind each, to `v-scaffold`:
  - **Sneak re-press delay** (`SneakRepress`): a crouch that is not already down now waits 90ms of
    wall-clock time since the last placement before it presses, instead of squeezing the edge crouch
    back on the tick after a placement leaves. Holding is not pressing — an already-down key is left
    alone — and releases are never delayed, because a crouch held past its point is a walking-into-air
    bug. Together with `SneakTiming` this turns the sneak-toggle cadence from one toggle per
    placement into bursts at the places a real hand would squeeze the crouch.
  - **Aim hold after a placement** (`AimHold`): the smoothed pitch now rests on the angle that
    performed the last placement — 710ms while sprinting, 110ms while walking, 460ms idle, Raven's
    numbers — instead of drifting along with the walk between placements. A staged placement turns
    the hold off entirely, because that is when the server's ray has to reach the face; the pitch
    wobble keeps running either way so the rotation never repeats exactly for `AimDuplicateLook`.
- Both decisions are pure static functions with unit tests (`SneakRepressTest`, `AimHoldTest`), no
  new mixins, no camera writes, and no change to packet ordering.

## [7.1.0] - 2026-10-18

- Fixed `v-scaffold` placing on POST — the anticheat named it directly:
  `Looks like you are currently attacking/placing on POST, this is a very common check on most
  anti-cheats`. Meteor's `BlockUtils.place(..., rotate = true, ...)` calls
  `Rotations.rotate(yaw, pitch, runnable)`, whose callback Meteor runs from
  `SendMovementPacketsEvent.Post`, so the interaction left *after* the movement packet. Only
  GrimScaffold and NCP were routed through `PreRotate`; every other path passed `rotate = true`
  straight to `BlockUtils`. Rotation-and-place is now always `aimAndPlace`, and the rotating
  `place(pos, shouldRotate)` overload has been deleted so no call site can reintroduce it.
- Removed the `expand`, `vulcan-expand` and `vulcan-sprint` techniques from `v-scaffold`, along with
  the `Expand` and `Vulcan` setting groups and every setting that belonged to them. They placed
  extra blocks per tick, which no placement cadence survives and which the anticheat's
  straight-line minimum punishes hardest. Nine techniques remain: GrimScaffold, AutoSneak,
  BridgeMacro, VapeBridge, NCPNormal and Normal.
- Added activity guards across the combat modules, ported from a decompile of Vape v4.10. Each one
  stops the client doing something a person would not: `aimbot` gained `click-aim` (only aim while
  the attack key is held), `check-block-break` (do not aim while mining) and `limit-to-items` — the
  first two close a real inconsistency, because `auto-clicker-plus` already refused to click unless
  the attack key was down while the aimer tracked anyone unconditionally. `auto-clicker-plus` gained
  `require-entity`, since nothing stopped it firing on empty air. `velocity-plus` gained `chance`,
  `water-check` and `only-when-targeting`; reducing every knockback in every situation is itself a
  pattern. `v-scaffold` gained `block-whitelist`/`block-blacklist` and `pitch-check`/
  `pitch-threshold`. All default to off or permissive so existing behaviour is unchanged.
- Removed the `god-bridge` and `breezily` techniques from `v-scaffold`. Both drove the camera
  directly, which is the behaviour this release exists to undo: `god-bridge` jumped, looked back and
  down, placed beneath the jump and eased the view forward again, while `breezily` pulsed the player
  sideways at the edge and placed on each pulse. Neither survived a real bridge. Removing them took
  out the last callers of the camera slew, so `smoothAimAt`, `smoothViewTo`, `applyLook`, `aimNoise`,
  the per-axis aim velocities, `CAPTURE_DEGREES`, and the `AimEase`/`RotationGcd` imports went with
  them — all of which existed only to write a GCD-quantised look change to the camera, and none of
  which is reachable now that nothing drives the view. `v-scaffold` is down 127 lines.
- `v-scaffold` no longer snaps out of its crouch the instant a bridge edge clears. All four
  auto-sneak sites were a bare `if (edge) crouch(); else stand();`, so the crouch came up 50ms after
  it went down. That is the exact shape AntiCheat-Addition's `Scaffold.parts.Safewalk.Timing` flags
  for 20 VL — moved within 355ms, stopped within 175ms, and not held sneaking for more than 148ms —
  and it is also simply how a human does not move, since nobody lands on a new block and stands
  straight back up. The crouch is now held for a configurable `sneak-delay` (default 200ms, the
  middle of Vape's 100/200/500 presets) with a 30ms floor so a one-tick edge blip cannot produce a
  zero-length crouch. The decision lives in the pure, unit-tested `SneakTiming`. GodBridge and
  Breezily manage their own crouch phase-by-phase and were left alone deliberately.
- Removed the last GitHub references now that the project lives on GitLab. `fabric.mod.json`'s
  `contact.sources` pointed at a dead `github.com` repository, and `VenomousAddon.getRepo()` still
  declared one — Meteor's addon API can only describe GitHub (`GithubRepo` is a `final record` with
  no host field), so the honest value is `null`, which is Meteor's own default and is null-checked
  before use. The cost is that the title screen no longer offers a commit link; `contact.sources`
  is now the only source pointer and points at the GitLab group. The credited author is derived
  from that URL rather than hardcoded, so the two cannot drift apart again.
- Corrected the mod's credited author. `fabric.mod.json` still named a personal handle while the
  repository and the changelog had both moved to International Duck Incorporated, so anything
  reading the mod listing was told the wrong owner. The two are now asserted to agree, against the
  packaged resource rather than the source file, so the shipped jar is what gets checked.
- Fixed `v-scaffold`'s default placement cadence being detectable on its own, independent of
  rotation. AntiCheat-Addition's `ScaffoldAverageBatchProcessor` averages the real gaps between
  placements on one straight line and flags anything faster than its `placement_delay` default of
  238ms, and a straight bridge is the *strict* case — diagonal building is allowed 138ms. The
  default `place-delay` of 3 produced `3 + random(0..2)` ticks, a 200ms mean, so it was under the
  floor by construction. Observed on AntiCheat-Addition as `failed scaffold x15` then `x20`;
  decoding its `1.1 * (238 - actual) + 5` puts the measured average at ~229ms and ~224ms, which is
  the 200ms nominal plus tick scheduling. The default is now 5 and the invariant lives in the pure,
  unit-tested `ScaffoldCadence` so the setting and its guard cannot drift apart.
- `v-scaffold` no longer drags the player's view onto the server's rotation. Both the GrimScaffold
  and the NCP placement paths snapped the camera to whatever the block needed, so bridging overrode
  where you were looking and the mouse fought the client. They now hand the rotation to Meteor and
  defer the placement to `SendMovementPacketsEvent.Pre` through `PreRotate`, so the server is
  aimed correctly while your camera stays exactly where you put it. The `rotate` setting gates both
  paths; with it off, placement happens immediately as before. GodBridge still drives the camera
  deliberately — looking back and down is the whole point of that mode.
- Fixed `v-scaffold` sweeping absurdly slowly, and only in one direction. `AimEase.step` treats
  velocity as a magnitude, but `nextVelocity` handed back the *signed* step. A leftward sweep
  therefore compared a negative velocity against a positive target and the acceleration ramp
  collapsed, crawling toward the ceiling instead of reaching it. Rightward sweeps looked correct,
  which is why this read as a general sluggishness rather than a sign bug. `nextVelocity` now
  returns `Math.abs(step(...))`.
- Fixed the startup banner advertising the wrong version. `VenomousAddon.VERSION` was maintained
  by hand while the build numbered the jar from `libs.versions.toml`, so 7.0.1 shipped printing
  `V7.0.0`. The two are now asserted equal, the version may be written down exactly once in the
  addon class, and the banner reads the constant. `AddonMetadataTest` fails the build otherwise.

- Continued development and maintenance following the migration from GitHub to GitLab.
- Improved project documentation and clarified the project's work-in-progress status.
- Reindented the recovered sources from 3-space to 4-space, matching the hand-written files.
  The recovered tree carried Vineflower's 3-space output, so 122 of 150 Java files were
  formatted differently from the rest; the change is whitespace-only and leaves compiled
  output byte-identical.
- Moved `ServerAnalyzer` from `modules/detection` to `utils`. It is a helper owned by
  `AntiCheatDetect` rather than a registrable module, so `modules/` now contains only
  modules and the registry tests no longer need to special-case it.
- Narrowed `ServerAnalyzer.getServerIp`'s undocumented `catch (Throwable)` to
  `catch (LinkageError)` and documented why: `MeteorClient`'s static initialiser throws
  `ExceptionInInitializerError` outside a running game, which is an `Error`, so a
  `RuntimeException` catch silently stops covering it. Genuine `Error`s now propagate.
- `v-ghost-hand` no longer leaves Meteor's `StorageESP` running forever after it is
  switched off, and now only disables an ESP it enabled itself.
- `pearl-wind-macro` no longer fires its queued rotation actions after being disabled,
  which threw items at stale hotbar slots from an inactive module.
- Implemented the advertised humanlike timing in `middle-click-action`. Its `technique`,
  `min-cps` and `max-cps` settings were decorative — the module acted instantly on the
  middle-click down-edge and never read them. Timing now lives in the pure, unit-tested
  `ClickTiming` helper, and the target is resolved on the press so the delay cannot act on
  a different entity than the one clicked.
- Every `PreRotate` consumer kept running its deferred action after being switched off,
  because nothing re-checked that the action was still wanted. `PreRotate` now takes a
  mandatory guard — `rotateThen(this, …)` for modules, or any `Guard` for non-modules —
  and the unguarded overloads are gone, so the compiler enforces it.
- `v-auto-city` and `auto-break-selected` captured a mutable field in their deferred action,
  so the rotation was computed for one block while the action ran against another. Both now
  capture the target by value. `auto-break-selected` additionally dereferenced a target that
  its own `resetMining()` teardown had just nulled, which could throw.
- Deferred actions no longer pile up. A module that re-requested its action every tick while the
  rotation was settling queued one entry per tick, and every copy fired in the same tick once the
  rotation landed — repeated `START`/`STOP` destroy packets for a single block. Queueing now
  supersedes the caller's own earlier entry, expressing current intent instead of a backlog.
- Fixed ready actions running in reverse queue order. They were collected back-to-front so
  entries could be removed while iterating, and were never put back in order. A macro queuing
  two actions on one aim would have run them backwards.
- Added `PreRotate.rotateThenSequence` for macros that must run every step, such as
  `pearl-wind-macro` throwing an item and then using a charge on the same aim.
- Made `v-scaffold` actually human-shaped. Its camera moved at a fixed 7°/tick for the whole
  sweep and stopped dead on the target, which no hand produces; edge thresholds fired at
  identical distances every time; and placements landed exactly `place-delay` ticks apart,
  forever. Aim now eases in, decelerates into the target and carries per-tick noise, thresholds
  and cadence are jittered, and — most importantly — rotation deltas are quantised to the
  sensitivity GCD grid. A real mouse emits only whole multiples of that grid, so the old
  unquantised `setYRot` sweep produced angles no human could generate. That is what
  `Aimbot` and `KillAura` already did via their `gcd-fix` settings; scaffold had neither.
- Extracted the sensitivity GCD formula into `RotationGcd` and the easing curve into `AimEase`,
  both free of Minecraft types so they unit-test on a plain JVM. The GCD formula had been
  copy-pasted into `Aimbot`, `KillAura` and `GlobalNetworkInterceptor`.
- Implemented fifteen settings that were registered in the GUI but never read, so they silently
  did nothing. `invis-esp` passed a hardcoded `0` for line width; `no-fall-plus` never consulted
  `grim-save-ticks`; `new-chunks` only ever rendered the new-chunk colour; `auto-ez` tracked a
  streak it never reported; `teams` ignored `armor-color`; `tickbase` banked a burst while idle
  regardless of `only-on-attack`; `targetlock` never checked `combat-only`; `lethality` ignored
  `hurt-time-check`; `autochatgame` had no unscramble solver at all despite detecting the game
  type; `v-blink`'s `dummy` setting had no renderer behind it.
- Removed `skeleton-esp`'s `line-width` setting instead of implementing it: `Renderer3D.line`
  takes no width argument, so there is no call that could honour it.
- Removed dead code in `auto-farm`: `rotateTo`, `isLookingAt` and `wrapDegrees` had no callers.
  `rotateTo` was a verbatim copy of the constant-rate, un-quantised rotation logic just removed
  from scaffold, so leaving it in place invited that bug being reintroduced.
- Added `DeadSettingTest`, which fails the build if a module registers a setting it never reads.
  It carries a self-test asserting the detector can still fire.
- Wrote the four modules that were settings-only shells, and gave each real behaviour.
  - `flagcheck` now detects anticheat setbacks: a position packet arriving with no matching
    teleport request, distinguished from ordinary position packets by a grace window around
    outbound accept-teleport packets. Reports session and recent counts.
  - `freecam-mining` now mines from the freecam viewpoint, walking the break to the target in
    server-acceptable position increments and returning afterwards, with optional eased,
    GCD-quantised camera rotation.
  - `inventorytracker` now records container contents and slot changes, attributing them to the
    nearest player in range.
- Deferred every remaining `toggle()` called synchronously from `onActivate` (11 sites across
  `Damage`, `Kick`, `NameCollector`, `WindPearlMacro`, `AutoPearl`, `MovementDebug`) via
  `mc.execute()` with an `isActive()` guard, so activation is never interleaved with its own
  teardown.
- `tnt-timer` draws a fuse marker whose height scales with the remaining fuse, so the danger is
  readable in world space. The old `text-scale` setting was removed rather than implemented: the
  numeric counters cannot be drawn on this Minecraft version's renderer, and a control that
  still did nothing was the original problem.
- Removed `hide-scoreboard`'s `hide-numbers` setting and its uncalled `shouldHideNumbers()`.
  `HudMixin` cancels `extractScoreboardSidebar` outright whenever the module is on, so the
  sidebar was already hidden in full and the setting could not change anything.
- Added `OrphanModuleTest`, which fails the build when a module exposes a public API that
  nothing calls, or defines neither an event handler nor activation work while being referenced
  nowhere. Four such cases are recorded in an allowlist with a drift check: `TextFieldProtect`
  and `ExtendedFirework` are inert shells, and `VGhostHand.isTargeted` is the unhooked
  interact-through-walls feature. All three await a mixin that was never written.

## [7.0.0] - 2026-09-19

### Added

- Added a Gradle-based Fabric build system with the Gradle wrapper.
- Added automated build and release workflows for stable and nightly builds.
- Added a comprehensive test suite covering:
  - Addon metadata
  - Compatibility helpers
  - Mixin configuration
  - Module registry integrity
  - Module structure
  - Scavenged module logic
  - Server analysis
  - Anti-vanish behaviour
  - Anti-cheat bypass logic
  - Movement mathematics
  - Placement regressions
  - Build scripts
- Added new rendering, HUD, screen, portal, and movement mixins.
- Added movement debugging and supporting movement mathematics utilities.
- Added `MovementMath`, `PreRotate`, and other utility improvements.
- Added a GPL-3.0-or-later license and included it in built JAR files.

### Changed

- Recovered and migrated the addon from decompiled output into a maintainable Java source tree.
- Cleaned the recovered source tree and moved resources into the standard Fabric layout.
- Updated the project for Minecraft 26.2, Meteor Client 26.2, and Java 25.
- Renamed modules where necessary to avoid collisions with Meteor Client module names.
- Added the `v-` prefix to modules that conflict with Meteor's global module registry.
- Refined module registration and reorganized modules across combat, detection, ESP, exploit, fun, miscellaneous, movement, player, render, utility, and world categories.
- Improved combat, movement, automation, rendering, networking, and anti-cheat-related modules across the codebase.
- Added lifecycle cleanup and state-reset handling to multiple modules.
- Improved Aimbot, KillAura, Speed, Fly, Disabler, and related modules.
- Improved packet, rotation, portal, and placement handling.
- Updated README documentation with compatibility, installation, build, licensing, and issue-reporting information.

### Fixed

- Fixed recovered Java source compilation errors across several modules.
- Fixed runtime issues in combat, movement, detection, exploit, utility, and rendering modules.
- Fixed module-name collisions that could replace Meteor Client modules.
- Fixed several module registration and structure issues.
- Fixed workflow and build configuration issues.
- Fixed executable permissions for `gradlew`.
- Fixed compatibility and mixin configuration problems.

## [6.2.6] - 2026-09-18

### Added

- Added decompiled Java source for the recovered Venomous addon.
- Added the initial Gradle and Fabric project structure.
- Added module implementations covering combat, detection, ESP, exploits, fun, miscellaneous utilities, movement, rendering, and world interaction.
- Added Venomous resources, including the icon, splashes, metadata, and mixin configuration.

### Changed

- Cleaned the recovered source tree and converted it into a conventional source layout.
- Added an initial automated build workflow.
- Started migrating the project from a distributed JAR into a source-based development project.

### Fixed

- Fixed initial compilation errors in the recovered source.
- Fixed formatting issues in the original README.

## [6.1.6] - 2026-09-18

### Added

- Added the first recovered Venomous JAR contents, including:
  - Fabric metadata
  - Mixins
  - Venomous addon entrypoint
  - Combat modules
  - Detection modules
  - ESP modules
  - Exploit modules
  - Fun modules
  - Miscellaneous modules
  - Movement modules
  - Utility modules
  - World modules
  - Assets and splashes
- Added the initial README.

## [0.1.0] - 2026-09-18

### Added

- Created the Venomous project.
- Added the initial project README and project documentation.

[Unreleased]: https://gitlab.com/international-duck-incorporated/Venomous/-/compare/v7.1.0...main
[7.1.0]: https://gitlab.com/international-duck-incorporated/Venomous/-/releases/v7.1.0
[7.0.0]: https://gitlab.com/international-duck-incorporated/Venomous/-/releases/v7.0.0
[6.2.6]: https://gitlab.com/international-duck-incorporated/Venomous/-/releases/v6.2.6
[6.1.6]: https://gitlab.com/international-duck-incorporated/Venomous/-/releases/v6.1.6
[0.1.0]: https://gitlab.com/international-duck-incorporated/Venomous/-/commits/main
