# Changelog

All notable changes to Venomous are documented in this file.

## [Unreleased]

## [7.18.6] - 2026-10-23

- New combat module `rod-aimbot`: a right click with an uncast rod is intercepted, the closest
  valid player inside the cone is picked, and the cast is staged against where they will be when
  the hook arrives - `predicted-ticks` projects their observed motion through vanilla's own
  per-tick constants (air drag 0.91, gravity 0.08 with 0.98 drag, ground friction 0.546).
  Ported from Raven B4's `RodAimbot` with the same gates (cone, distance, invisibility, bots,
  teammates) and its deviations stated: the camera is never taken - the cast rotation is
  spoofed server-side through `PreRotate`, so the interaction departs before the movement
  packet from a rotation the server acknowledged, and the aim keeps refreshing every tick until
  the cast lands rather than only when the original's motion event fires. Both rotation axes are
  snapped onto the sensitivity grid with the 7.18.4 machinery - a cast angle no mouse could
  produce is exactly what the rotation checks read. The reel-in is deliberately left to the
  player: the hook is where it is, and a second interception would fight the hand that cast it.
  The projection and the angle conventions live in `EntityPredict` and `RodAimMath`, unit
  tested in `RodAimMathTest` (fall and friction shapes, yaw/pitch conventions, cone wrap).

## [7.18.5] - 2026-10-23

- Fixed the quit hang. Clicking Quit used to freeze the client for twenty seconds and end in a
  `ClientShutdownWatchdog` crash report with exit code -8. The cause: Baritone's cache workers
  are non-daemon threads parked forever in a `LinkedBlockingQueue.take()` inside a loop that
  catches `InterruptedException` and `Throwable` and keeps parking - so neither an interrupt nor
  a `shutdownNow()` can release a worker that is already parked there, and once `main()` returns
  the JVM waits on them until vanilla's watchdog force-kills the process. The fix is a shutdown
  reaper: a daemon thread started at addon init that watches the main thread, and the moment
  `main()` has returned invokes `System.exit(0)` - shutdown hooks run, the halt takes the parked
  threads with it, and quitting is clean and immediate. When every thread is well behaved the
  natural shutdown wins the race and the forced exit is a no-op on an already-halting JVM.
  Covered by `ShutdownReaperTest` (fires on death, stays quiet while watched, daemon by
  construction).
- Removed fourteen dead modules after grading every one of them against the 26.2 server model.
  Twelve were 1.8-era packet relics that cannot function against a server-side simulation and
  only ever produced flags: `more-carry` (crafting-slot carry - servers repopulate containers on
  close), `bungeecord-spoof` (forwarding data is proxy-side; a client injection just fingerprints),
  `kick` (ESC menu exists), `portal-exploits` (with its two serving mixins,
  `NetherPortalBlockMixin` and `ScreenMixin`), `reset-vl` (modern anticheats do not keep
  reloggable violation levels), `sleep-walker` (the server ejects a moving sleeper), `chorus-exploit`
  (the teleport is server-authoritative), `v-click-tp` (client setPos rubber-bands without
  permission), `vehicle-one-hit` (vehicle health is server-side), `v-ghost-hand` (server
  raycasts), `time-shift` and `v-anti-hunger`. The other two were worse than dead: the repo's
  own `OrphanModuleTest` audit had already established that `text-field-protect`,
  `extended-firework` and `v-ghost-hand` were wired to nothing - their advertised features never
  ran because the mixins they waited on were never written - so those settings took space in the
  GUI while doing literally nothing. The orphan and inert allowlists are empty now, and the
  drift assertions keep them that way.

## [7.18.4] - 2026-10-23

- Fixed V Scaffold sending pitch steps no mouse can produce. The shared `RotationGcd` grid was
  verified against the 26.2 client bytecode and is correct: `MouseHandler.turnPlayer` multiplies
  mouse counts by `f³ × 8` and `Entity.turn` scales that by `0.15`, so the real grid step is
  `f³ × 1.2` - exactly what the utility computes, and exactly what the network interceptor's
  duplicate of the formula computes. The scaffold was the fault: it imported the utility and
  never called it, so the staged pitch went out as the raw `smoothed + wobble` float, and the
  delta between consecutive rotations - the thing Grim's `RotationPlace` reachability test
  actually validates - landed between the grid lines (the session's two `RotationPlace` flags).
  The pitch handed to the server is now snapped onto the grid at both staging sites, measured
  from the rotation the server already holds, since what is validated is the step, not the
  absolute angle. The yaw is deliberately untouched: it is the camera's own, so its step is a
  real mouse turn by construction.
- The snap never rounds a requested movement away. The pitch wobble is smaller than one grid
  step at most sensitivities, and a naive round-to-nearest would erase it, leaving the rotation
  repeating exactly - the fingerprint the wobble exists to avoid (`AimDuplicateLook`). A real
  but sub-grid step now becomes one whole grid step in the requested direction, the smallest
  movement a real mouse can make; a genuinely zero step still invents nothing. The smoothing and
  wobble constructs are untouched - the grid is applied where the aim is handed to Meteor, in
  the new `RotationGcd.quantizeFrom`, covered by four new `RotationGcdTest` cases: relative
  snapping, the sub-grid bump, two hundred ticks of hold oscillation staying on-grid and near
  the aim, and degenerate sensitivities passing the target through unchanged.
- The Simulation flag wall from the same session is not explained by anything this codebase
  writes: the scaffold's whole path was checked and there is not a single velocity write in it -
  the module stages rotations and clicks, and applies no motion of its own. The steady
  `.054600` repeat is one 0.1/tick step of velocity re-injected and ground-frictioned
  (0.6 × 0.91) every tick, which is a physics-versus-prediction mismatch, not a motion hack.
  Retest on Grim after this build; if the rotation-side flags are gone and Simulation remains,
  the next suspect is the just-placed-block confirmation window, isolable with a live A/B -
  module on while standing still, then while walking, then while bridging.

## [7.18.3] - 2026-10-23

- Debug round: a head-to-head reread of the 7.18.x additions against their sources - the pause
  masks, the damage and knockback bookkeeping, the state resets, the registration and the version
  sync - shipping the fixes the reread found. Three real ones:
- Fixed `latency-alerts` limbo carve-out losing half of the original's test: Raven's limbo is The
  End dimension AND an empty scoreboard sidebar, and the port had kept only the dimension half, so
  the module went silent inside any The End world - actually hiding genuine packet-loss alerts in
  minigames played there. Now the exact conjunction in `inLimbo()`: the `DisplaySlot.SIDEBAR`
  objective absent, or present with zero scored rows, and the dimension The End.
- Fixed `latency-alerts` first-alert timing: the original zeroes its last-alert stamp on disable
  and stamps only the packet clock on enable, so the first stall raises at the earliest legal
  moment; the port's reset-both-on-activate delayed every first alert by a full alert interval
  after toggling. Enable and disable mirror the original now, and the disable and no-player legs
  clear the last-packet name the way the original's branches do.
- Fixed two `hit-select` parity breaks: the tick clock is cleared on enable (the original does;
  the wait-first window measures exchange age, not addon uptime), and `item-blacklist` is now
  hidden until `filter-blacklisted-items` is on - the original's guiUpdate behavior, riding the
  addon's existing `.visible` precedent. `missed-swings-cancel-rate`'s description now tells the
  truth about the packet level: it filters attack packets on non-player entities, and air swings
  are out of reach there because vanilla never sends an attack packet for an empty crosshair -
  Raven's 1.8 build could intercept them, a packet filter cannot.
- Reread, no fault found everywhere else: `hit-select`'s pause/mask/criticals logic matches the
  original step-for-step (including the strict pause release clock and the raw-mask write on
  server confirmation), `jump-reset`, `no-jump-delay` and `auto-requeue` hold their shapes and
  their state resets, the `noJumpDelay` accessor targets the javap-verified field, and
  `ItemListSetting`'s builder seeds a non-null empty-list default (bytecode-checked), so building
  `item-blacklist` without `defaultValue` was already safe. The skipped suite member is
  `BuildScriptsTest`'s `@DisabledOnOs(WINDOWS)` case, idle on this machine on purpose.

## [7.18.2] - 2026-10-23

- New combat module `hit-select`: filters clicks the server would ignore anyway - a hit that
  lands inside a target's invulnerability window is voided by every server, while the click still
  spends the attacker's weapon cooldown and the momentum of the exchange. Ported from Raven B4's
  `HitSelect` with its decision extracted into `HitSelectRules` (bursts predicted from the
  attacker's own hit, the optional server-timing gate, the sticky pause holding any block reason
  for its duration, the wait-for-first-hit hold, the criticals gate, the three-block reach, the
  percent cancel rates), with unit tests in `HitSelectRulesTest`. Two ports findings shipped with
  the port: Raven's hurt-window sync and its `maxHurtTime` fallback are unwired in the original -
  the live path only ever starts ten-tick windows from a passed hit, which is what the port keeps
  and every one of them is exclusive-end-tested; and 26.2 gives attacks their own
  `ServerboundAttackPacket`, so the filter runs there and the rejected click's vanilla swing packet
  still leaves the client - the visible behaviour Raven's "fake swing" option bought, without the
  option. The mouse-over-first/else-closest target precedence and the eye-to-nearest-box-face
  reach are Raven's own; Raven's friends list and bot list carve-outs have no equivalent here and
  are dropped; `ignore-teammates` rides on vanilla `isAlliedTo`; weapon detection rides on the
  repo's `McCompat.isWeapon` (the same check the other combat modules hold to), since 26.2 swords
  carry a weapon data component instead of a `SwordItem` class and there is no sword class to
  instanceof.
- New misc module `latency-alerts`: the gap since the last packet the server sent is measured
  every tick, and once it crosses the `high-latency` threshold an alert prints with the gap and
  the last packet seen - at most once per `alert-interval` so a stall does not flood the chat.
  Ported from Raven B4's `LatencyAlerts` with the same measurement, interval guard and its limbo
  carve-out (measured the same way, on The End dimension); the hover component it hung the
  details on is replaced by the details printed inline.
- Graded-and-rejected for this increment: larpclient's `HoverTotem` (already inside
  `auto-inv-totem` - it ships the "merged with HoverTotem" description) and Raven's `HitBox`
  (needs a pick-interception mixin at the current `GameRendererMixin`'s cost, for a reach that
  Grim's player-attack-range check refuses to honour beyond three blocks anyway).

## [7.18.1] - 2026-10-23

- New movement module `no-jump-delay`: the vanilla jump cooldown (`LivingEntity.noJumpDelay`, ticked
  down from ten after every jump) is clamped each tick to the `jump-delay` setting, so the default
  of 0 lets the jump be taken again the grounded tick after the last one. The port clamps instead
  of writing zero a decision the 26.2-era source it was transcribed from got right: a constant zero
  would defeat the countdown itself, and a jump would never be allowed again. The field is reached
  through a new `LivingEntityAccessor` mixin (the addon's first accessor into an entity class,
  registered in `venomous.mixins.json`).
- New misc module `auto-requeue`: runs the command behind a game's play-again chat button, held
  until the `delay` has passed. Ported from Raven B4's `AutoRequeue` with its shape kept - a
  message whose text matches the `trigger-text` is scanned for a command click event, the command
  is held until it is run, and a fresh world join discards what was held, because requeueing
  belongs to the game the message was sent in. Two deliberate deviations: Raven matched the
  server's own button wording ("Click here"), where the port takes the first run-command click
  event wherever it sits in the component tree and matches the trigger text somewhat; and Raven
  routed the click value through 1.8.9's single send method, where the port splits on the leading
  slash - `sendCommand` for a `/queue`, `sendChat` otherwise.
- Surveyed further archive ports for this increment and rejected them for cause: Raven's
  `AntiDebuff` is 1.8.9 render plumbing already covered by `anti-blind`; `InstantStop` and
  `NullMove` need an input-injection event Meteor does not expose (`PrePlayerInputEvent` is
  Raven-internal) for a small payoff; `StopMotion` zeroes vertical velocity mid-air, which is a
  predictable gravity mismatch on the servers this addon is tuned for; `KeepSprint`'s 1.8.9 logic
  is attack-slowdown math that does not exist in modern code, and its modern descendant is
  partially covered by `super-kb`'s W-TAP; larpclient's `PanicSlot` force-selects a hotbar slot.
  The bigger ports (`HitSelect`, the lag-queue trio, `Clutch`, `HitBox`) stay queued for their own
  increments.

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
