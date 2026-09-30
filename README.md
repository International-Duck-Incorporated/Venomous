<p align="center">
  <img src="https://gitlab.com/international-duck-incorporated/Venomous/-/raw/main/src/main/resources/assets/venomous/icon.png" alt="Venomous logo" width="140">
</p>

<h1 align="center">Venomous</h1>
<p align="center">A feature-heavy Meteor Client addon for Minecraft 26.2.</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-26.2-3C8527?logo=minecraft&logoColor=white" alt="Minecraft 26.2">
  <img src="https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white" alt="Java 25">
  <img src="https://img.shields.io/badge/status-WIP-yellow" alt="Work in progress">
</p>

## About

Venomous is an open-source Fabric addon that extends [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) with combat, movement, rendering, detection, automation, exploit, and utility modules.

Venomous is a work in progress. Expect bugs and test features only where you have permission.

## Highlights

- Combat and movement modules
- Scaffold and bridging profiles
- Anticheat and player detection
- ESP and rendering tools
- Inventory and world automation
- Packet and utility experiments
- Meteor settings and module integration

## Requirements

| Component | Version |
|---|---|
| Minecraft | 26.2 |
| Meteor Client | 26.2 |
| Java | 25 |
| Mod loader | Fabric |

Venomous is an addon, not a standalone client. Meteor Client is required.

## Install

1. Install Fabric and Meteor Client for Minecraft 26.2.
2. Download or build Venomous.
3. Put the Venomous JAR in your `mods` folder.
4. Start Minecraft and open Meteor's module menu.

Binary releases are not available yet.

## Build

```bash
./gradlew build
```

The JAR will be written to `build/libs/`.

## Contributing

Issues and merge requests are welcome. Keep changes focused, explain how they were tested, and do not commit generated or IDE-specific files.

- [Issues](https://gitlab.com/international-duck-incorporated/Venomous/-/issues)
- [Repository](https://gitlab.com/international-duck-incorporated/Venomous)
- [Changelog](CHANGELOG.md)

## License

See [LICENSE](LICENSE) for the project license and redistribution terms.
