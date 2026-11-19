# Prehistoric Tribes — Android Port (M1)

Native Android reimplementation of Prehistoric Tribes (THQ / Gear Games, 2008),
touch-first (Clash of Clans / Art of War style — no D-pad cursor at all) and
moddable without repacking. Research, mod format, and the full plan live in the
sibling repo `Keitaro0104/blueberry-prehistribes_code`
(`ANDROID-PORT-PLAN.md`, `mods/format/MOD-FORMAT.md`).

This repo contains only our own code. It does **not** bundle the original game:
no JAR, no original maps, sprites, MIDI, or decompiled code. The bundled
Green Valley map is user-created example content (mod format v0.1).

## M1 — what this milestone is

- libGDX 1.12.1 + Kotlin 2.0 skeleton (`core` / `android` / `desktop` modules).
- Loads the bundled Green Valley map (`core/assets/mods/green-valley.json`)
  and renders it with placeholder coloured tiles (proven tile classes only).
- Fixed-timestep loop at 20 ticks/second (50 ms/tick).
- Touch camera: one-finger drag pans, pinch zooms 0.5x–3x, double-tap
  centres on the tribe. Default zoom shows ~11 tiles across.
- HUD shell: resource bar (no live values yet).

### M1 done-criterion

The app opens to Green Valley rendered from its JSON; one-finger drag pans,
pinch zooms 0.5x–3x, double-tap centres on the tribe; no crash on
rotate/background; GitHub Actions build green.

### Explicitly not in M1

Villagers, buildings, combat, real terrain art, audio, saves, mods folder.

## Build

Requires JDK 17 and the Android SDK (API 35).

```sh
./gradlew android:assembleDebug
```

The debug APK lands in `android/build/outputs/apk/debug/`.

## Run the desktop check

The desktop module runs the same game code on the JVM — no phone needed:

```sh
./gradlew :desktop:run
```

Headless smoke test (boots the game, exits after ~6 seconds — this is what
CI runs under `xvfb-run`):

```sh
./gradlew :desktop:run --args="--smoke"
```

## Known M1 limitations

- **Placeholder art only.** Tiles are coloured by proven class (green =
  open ground, grey-brown = blocked, gold = spawn). The tile-ID → sprite
  mapping is still unproven, so real terrain art waits for M2 prep.
- **No fog of war yet.** All tiles are shown; the original only draws terrain
  the tribe has seen. The reveal radius is unproven and is an M2 task.
- **No simulation yet.** The 20 Hz loop runs, but there are no villagers,
  buildings, economy, or combat — those are M2–M4.
- **No audio, no saves, no mods folder.** Those are M5 work.
