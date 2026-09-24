# Changelog

All notable changes to Venomous are documented in this file.

## [Unreleased]

- Continued development and maintenance following the migration from GitHub to GitLab.
- Improved project documentation and clarified the project's work-in-progress status.

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

[Unreleased]: https://gitlab.com/international-duck-incorporated/Venomous/-/compare/v7.0.0...main
[7.0.0]: https://gitlab.com/international-duck-incorporated/Venomous/-/releases/v7.0.0
[6.2.6]: https://gitlab.com/international-duck-incorporated/Venomous/-/releases/v6.2.6
[6.1.6]: https://gitlab.com/international-duck-incorporated/Venomous/-/releases/v6.1.6
[0.1.0]: https://gitlab.com/international-duck-incorporated/Venomous/-/commits/main
