# Particle Studio — Fabric mod for Minecraft Java 26.3

The main feature is **Particle Studio** (client only): pressing J opens a panel for building effects out of `/particle` commands visually — layers, particle options, shapes, preview, running commands, presets, `.mcfunction` export.

Claude writes most of the code (vibe coding); the user works in VSCode. Build, run and clean go only through `scripts/*.sh`. No IntelliJ, no IDE run configurations, no Gradle extensions for VSCode.

Project docs (README, CLAUDE.md, release notes) are written in English. The in-game UI is translated into both `en_us` and `ru_ru`.

## Stack

| | Version | Where to change |
|---|---|---|
| Minecraft | 26.3 | `gradle.properties` |
| Fabric Loader | 0.19.5 | `gradle.properties` |
| Fabric API | 0.164.0+26.3 | `gradle.properties` |
| Loom (`net.fabricmc.fabric-loom`) | 1.18.4 | `gradle.properties` |
| Gradle | 9.7.1 | `gradle/wrapper/gradle-wrapper.properties` |
| Java | 25 (Temurin, `~/Library/Java/JavaVirtualMachines`) | — |

Mod id: `particlestudio`, package: `com.sergej.particlestudio`. Current versions: https://fabricmc.net/develop.

## Commands

```bash
./scripts/setup.sh        # JDK 25, VSCode extensions, dependencies, MC sources — idempotent
./scripts/build.sh        # compile + jar in build/libs/ — the main check that the code is correct
./scripts/run.sh          # dev client (game window; no license needed)
./scripts/run.sh server   # dev server
./scripts/test.sh         # client game tests (src/gametest): world, screens, screenshots
./scripts/datagen.sh      # generate JSON into src/main/generated/
./scripts/install.sh      # jar -> ~/Library/Application Support/minecraft/mods
./scripts/release.sh      # GitHub Release v<version> with the jar — how the mod gets to other PCs and friends
./scripts/mc-sources.sh   # Minecraft sources into .mc-sources/
./scripts/disk.sh         # what takes how much disk space
./scripts/clean.sh [--deep] [--worlds]
```

The scripts are bash: call them as `./scripts/x.sh`, never `source` them from zsh.

After every code change run `./scripts/build.sh` and fix errors until the build passes. `run.sh` opens a game window on the user's screen — run it only when in-game behavior must be checked, and warn the user first.

Check changes to the studio UI or logic with `./scripts/test.sh` (a game window opens for ~20 seconds). The test `src/gametest/.../ParticleStudioClientTest.java`:
- generates a command for **every** particle type and parses it with the server's `/particle` parser;
- checks every shape and position mode;
- clicks studio buttons and takes screenshots into `build/run/clientGameTest/screenshots/` — **open and look at them**, it is the only way to see the UI;
- checks that without cheats "Run" sends no commands, and with cheats the server's replies stay out of chat.

Add new scenarios to this test. Buttons are clicked by text (`clickScreenButton("particlestudio.run")` — a translation key or a literal); buttons inside `EditorList` cannot be found this way.

## Minecraft 26.x is not obfuscated — important

- The code uses **official Mojang names** (`net.minecraft.world.item.Item`, `ResourceKey`, `BuiltInRegistries`, `Identifier`). Yarn mappings (`net.minecraft.item.Item`, `Registries.ITEM`, `Identifier.of`) and examples from 1.20/1.21 tutorials **do not apply** — never copy them without checking.
- The API changes between versions. Before using a Minecraft class or method, find it in the sources:
  `grep -rn "class BlockItem" .mc-sources/net/minecraft` (the folder is gitignored, so pass the path explicitly). If `.mc-sources/` is missing, run `./scripts/mc-sources.sh`.
- Vanilla classes are the reference implementations (`Items.java`, `Blocks.java`, `BlockBehaviour.Properties`, `Item.Properties`).
- Fabric docs: https://docs.fabricmc.net/develop/ (pick version 26.x).
- Fabric API classes (no sources in `.mc-sources`) can be inspected with javap on the jars in `~/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/`.

Already established for 26.3 (old tutorials say otherwise):
- GUI: `Screen.extractRenderState(GuiGraphicsExtractor, …)` instead of `render(GuiGraphics, …)`, widgets use `extractWidgetRenderState`; open a screen with `minecraft.gui.setScreen(...)`, the current one is `minecraft.gui.screen()`. Text colors must include alpha (`0xFFRRGGBB`), otherwise the text is invisible.
- Block in particle options: `{block_state:{id:"minecraft:stone"}}` — the key is `id`, not `Name`.
- A player's command is sent with `player.connection.sendCommand("particle …")` (no slash). The server sends the client a tree of only the commands it may run — that is how permissions are checked (`EffectRunner.canRunCommands`).

## Layout

```
src/main/java/com/sergej/particlestudio/   common code (server + client)
  ParticleStudio.java                      ModInitializer, MOD_ID, ParticleStudio.id("path")
  item/ModItems.java                       item registration: ModItems.register(name, factory, props)
  block/ModBlocks.java                     block registration (+ BlockItem): ModBlocks.register(name, factory, props, withItem)
  mixin/                                   mixins -> list them in particlestudio.mixins.json
src/main/resources/
  fabric.mod.json                          manifest (${version}, ${mod_id} are filled in by Gradle)
  assets/particlestudio/                   lang/, items/, models/, blockstates/, textures/
  data/particlestudio/                     recipe/, loot_table/, tags/
src/main/generated/                        datagen output — do not edit by hand
src/client/java/com/sergej/particlestudio/client/
  ParticleStudioClient.java                ClientModInitializer: J key, repeat ticks, chat filter
  studio/                                  Particle Studio — model and logic, no GUI
    ParticleEffect, ParticleLayer          effect = layers; layer = one /particle (+ shape). POJOs -> Gson presets
    ParticleOptionsHelper                  particle option templates (SNBT), validation through the type's codec
    EffectCompiler                         layer -> /particle commands and local particles for preview
    EffectRunner                           current effect, preview/run, repeat, permission check
    PresetStore                            config/particlestudio/presets/*.json, functions/*.mcfunction, last_session.json
  studio/gui/                              studio screens and widgets
    ParticleStudioScreen                   panel on the left; the options editor is built from the particle's SNBT
    EditorList, NumberField, ChannelSlider, ColorSwatch, PickListScreen
  datagen/ParticleStudioDataGenerator.java datagen providers
  mixin/                                   client mixins -> particlestudio.client.mixins.json
src/gametest/                              client game tests (a separate mod, particlestudio-test)
```

## Rules

- Client classes (`net.minecraft.client.*`, rendering, screens) belong only in `src/client`, otherwise the mod crashes on a dedicated server. Split source sets catch this at compile time.
- New items and blocks are `static final` fields in `ModItems` / `ModBlocks`, created through their `register` helpers. Blocks first, then items (the order in `ParticleStudio.onInitialize`).
- Ids and resource paths are `snake_case`, lowercase Latin only.
- Every new UI string, item or block goes into both `lang/en_us.json` and `lang/ru_ru.json`.
- Prefer datagen for models, recipes, loot and tags over hand-written JSON.
- Mixins are the last resort: look for a Fabric API event first (`net.fabricmc.fabric.api.*`).
- Textures: 16×16 PNG in `assets/particlestudio/textures/item|block/`.

## Git

- Repository: https://github.com/Serrakon/minecraft-mod (public, MIT, the user's personal account Serrakon). Releases with the jar live on its Releases tab.
- Work directly in `main`: no branches or merge requests; commit and `git push` right away.
- `./scripts/build.sh` must pass before every commit.
- The commit author is set locally in the repo (`88548243+Serrakon@users.noreply.github.com`). Never use the work email from the global git config here.

## Disk (little free space)

- Do not install new programs, JDKs, extensions, Gradle plugins or Minecraft versions without asking the user and stating the size.
- The official launcher is not needed for development: `run.sh` uses its own cache.
- After changing the Minecraft or Gradle version, or when space runs low: `./scripts/clean.sh --deep`.
- Caches of other Gradle versions in `~/.gradle` may be used by other projects — never delete them without the user's consent.
