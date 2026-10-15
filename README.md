# Starcatcher FishBot

[![Release](https://img.shields.io/github/v/release/TargetedEntropy/starcatcher-fishbot)](https://github.com/TargetedEntropy/starcatcher-fishbot/releases/latest)
[![Build](https://github.com/TargetedEntropy/starcatcher-fishbot/actions/workflows/build.yml/badge.svg)](https://github.com/TargetedEntropy/starcatcher-fishbot/actions/workflows/build.yml)
![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-62B47A)
![NeoForge](https://img.shields.io/badge/NeoForge-26.1.2.99%2B-E04E14)
![Side](https://img.shields.io/badge/side-client%20only-blue)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A **client-side only** NeoForge mod for **Minecraft 26.1.2** that fishes for you with
[Starcatcher](https://github.com/wdiscute/starcatcher) (built against Starcatcher `3.3.8.1-NEOFORGE-26.1.2`).

It plays the reel minigame frame-perfectly, hooks bites, recasts, keeps your rod safe and
tracks what you caught. Nothing is needed on the server.

## Requirements

| | Version |
|---|---|
| Minecraft | 26.1.2 |
| NeoForge | 26.1.2.99 or newer |
| Starcatcher | 3.3.8 or newer for 26.1.2 |
| Java | 25 |

Download the jar from the [latest release](https://github.com/TargetedEntropy/starcatcher-fishbot/releases/latest)
and put it in your `mods` folder next to Starcatcher. Development builds are on the
[Actions page](https://github.com/TargetedEntropy/starcatcher-fishbot/actions/workflows/build.yml)
(artifact `scfishbot-jar`), or you can build it yourself.

## Modes

| Mode | What it does | Default key |
|---|---|---|
| **OFF** | Nothing. | |
| **ASSIST** | Plays only the reel minigame. You cast and hook yourself. | `F7` |
| **FULL AUTO** | Casts, waits, hooks the bite, plays the minigame, then recasts. | `F8` |

Pressing the key for the active mode turns the bot off. Keys can be rebound under
*Options > Controls > Key Binds > Starcatcher FishBot*.

## Features

### Minigame solver
- Rebuilds Starcatcher's own hit test (same handle position, leeway, spot order and modifier
  vetoes) every frame and every tick. It presses only when that test lands on a spot, so a
  press never counts as a miss and catches come out **perfect**.
- Collects **treasure**. With *treasure priority* on, it holds fish progress below a set
  fraction of the goal until the treasure bar is full.
- Never hits **negative-reward** spots (optional).
- Handles every sweet-spot type: normal, mushroom, freeze, treasure, TNT, deepslate, leaf,
  dripstone, cloud, glowing, sculk and mirage. It never presses on mirage spots or on spots
  that cannot be hit.
- Handles **multi-layer (A/D) minigames**: it switches the handle to the layer of the next
  spot it will reach.
- Humanised timing you can tune: a start delay, a minimum interval between presses plus random
  jitter, *aim toward centre* (a random press depth inside each spot) and an optional *skip
  chance* that lets a spot pass with no miss penalty.

### Full-auto fishing
- Casts the rod, detects bites from the bobber's synced state, and hooks after a random
  reaction delay (always inside Starcatcher's 80-tick bite window).
- Recasts after a random delay. Reels in and retries if the bobber never lands in liquid, nothing
  bites within the timeout, or a hooked bobber never opens a minigame.
- Waits for the server to confirm a cast before doing anything else, so a slow connection
  cannot make it reel in by mistake.
- Pauses while any other screen (inventory, chat, menu) is open.

### Rod and safety
- **Auto rod select**: if no Starcatcher rod is in hand, or the held rod is too damaged, it
  switches to the hotbar rod with the most durability left.
- Stops when rod durability is low, when you are out of bait (optional), when your inventory
  is full, when you take damage, when your health drops below a threshold, or when you die.
- Pauses casting while a hostile mob is within range.
- Session limits: stop after N catches or N minutes.

### Stats, alerts and logging
- On-screen overlay: mode, current activity, catches and success rate, perfect catches,
  treasures, catches per hour, session time, casts/bites/hooks, rare catches, and your most
  caught items. You can move it to any corner.
- A chat alert and sound for catches at or above a chosen rarity (default: Epic).
- Optional one-line chat summary after every minigame.
- A CSV log of every minigame at `<game dir>/scfishbot/catches.csv`
  (timestamp, result, item, rarity, perfect, treasure, duration, hits, bot presses, mode).

### Commands (client-side)
```
/fishbot              show status
/fishbot full | on    full auto
/fishbot assist       minigame only
/fishbot off          stop
/fishbot stats        detailed session statistics
/fishbot reset        reset session statistics
/fishbot hud          show/hide the overlay
/fishbot config       open the settings screen
/fishbot help
```

### Settings
All settings are in `config/scfishbot-client.toml`. You can also edit them in game under
*Mods > Starcatcher FishBot > Config*, through `/fishbot config`, or with the optional
"Open FishBot Settings" key. Every option has a tooltip.

## Building

```bash
./gradlew build          # jar in build/libs/
./gradlew runClient      # dev client with Starcatcher loaded
./gradlew runE2e         # automated in-game test (see below)
```

`runE2e` starts a dev client (it works headless under `xvfb-run`). It creates a flat test world,
digs a pool, gives a Starcatcher rod, switches the bot to FULL and waits for 5 catches. Then it
logs `[E2E] RESULT PASS|FAIL ...` and quits. Change the target with `-Pe2eCatches=N`. The harness
is in `src/devtest` and is never packaged into the mod jar.

Starcatcher is a `compileOnly` dependency (from the Modrinth maven) and is **not** bundled.

## Notes

- The mod id is `scfishbot`. Starcatcher's minigame refuses to start when a mod with the id
  `scauto` is loaded, so don't rename the mod to that.
- The mod is marked client-only (`displayTest = IGNORE_ALL_VERSION`), so servers never need it.
  Using automation on a multiplayer server may break that server's rules. Check before you use
  it there.

## License

[MIT](LICENSE). Not affiliated with Starcatcher or its authors.
