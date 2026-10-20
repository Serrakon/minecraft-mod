# Particle Studio

A client-side Fabric mod for Minecraft Java 26.3: a visual editor for effects built from `/particle` commands. Instead of typing a long command in chat, you put the effect together in a panel and see it in the world right away.

- Layers: each layer is its own `/particle` command, and all layers run together.
- Any particle, with search. Its options get proper fields: color (HEX and RGB), scale, the block under the crosshair, the item in your hand, the target for `trail` and `vibration`.
- Position: `~` from the player, `^` from the view, the point under the crosshair, or fixed coordinates. Spread, speed, count, `force`.
- Shapes: circle, sphere, line, helix. Particles can fly outward, inward or up.
- "Preview" shows the effect only to you and needs no permissions. "Run" sends real commands (needs cheats or `/op`). The effect can repeat every N ticks.
- Copy commands for chat and command blocks, export to `.mcfunction`, save presets.

## Installation

You need Fabric Loader for 26.3 and [Fabric API](https://modrinth.com/mod/fabric-api). Download the mod jar from [Releases](../../releases).

**Modrinth App:** create a Fabric 26.3 profile → Add content → install Fabric API → Add content → From file → pick `particlestudio-<version>.jar`.

**Official launcher:** run the [Fabric Installer](https://fabricmc.net/use/installer/) for 26.3, put Fabric API and the mod jar into the `mods` folder (on Windows: `%APPDATA%\.minecraft\mods`), and start the Fabric profile.

In game, press **J** to open the panel. The key can be changed in the Controls settings.

Presets and exported functions are stored in the game folder: `config/particlestudio/`.

## Development

Build, run and test through `scripts/*.sh` (macOS, JDK 25). Details are in [CLAUDE.md](CLAUDE.md).

```bash
./scripts/setup.sh     # JDK, dependencies, Minecraft sources
./scripts/run.sh       # game with the mod
./scripts/test.sh      # game tests with screenshots
./scripts/release.sh   # GitHub Release with the jar (version from gradle.properties)
```

License: MIT.
