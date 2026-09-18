# Magic - System Overview

Elemental resonance, casting modes (Surge / Flow), and the Resonance GUI. Batch 1 ships config shells only; the GUI renders from Batch 3 onward.

## Config folder layout

All paths under `plugins/Magic/` on the server.

| Path | Purpose |
|------|---------|
| `config.yml` | `debug`, `default_cast_mode`, equilibrium rates, tick interval |
| `gui.yml` | Title, slot map, filler icons, cast mode definitions, bar templates, hex color tokens |
| `messages.yml` | Player-facing chat strings |
| `elements/*.yml` | Element catalog (name, icon, color, skills) |
| `data/characters/<characterId>.json` | Per-character MagicProfile (cast mode, equilibrium, resonance) |

## Resonance GUI layout (54 slots)

Row 0 is the top row. Row 5 is the bottom.

```
Row 0:  [ ][ ][ ][ ][HEAD][ ][ ][ ][ ]     slot 4
Row 1:  [ ][ ][ ][SURGE][ ][FLOW][ ][ ][ ]  slots 12, 14
Row 2-3: filler / future meters
Row 4:  [E1][E2][E3][E4][E5][E6][E7][E8]  slots 36-44 (second-to-last row)
Row 5:  filler
```

Eight elements center in row 4 when `element_center: true` in gui.yml.

| Zone | Default slot(s) | Config key |
|------|-----------------|------------|
| Character head | 4 | `slots.character_head` |
| Cast mode (Surge) | 12 | `slots.cast_mode_left` |
| Cast mode (Flow) | 14 | `slots.cast_mode_right` |
| Element icons | 36-44 | `slots.element_row` + auto layout |

## Color tokens (gui.yml)

| Token | Hex | Use |
|-------|-----|-----|
| `title` | `#c9a0ff` | Inventory title |
| `label_muted` | `#8b7fa8` | Secondary lore |
| `label_body` | `#d4c8e8` | Body text |
| `label_accent` | `#e8d4ff` | Highlights |
| `mode_surge` | `#e85d4a` | Surge cast mode |
| `mode_flow` | `#5bc4d4` | Flow cast mode |
| `corruption` | `#c45749` | Corruption side of equilibrium bar |
| `tranquility` | `#6ec9a0` | Tranquility side of equilibrium bar |
| `resonance_low` | `#575150` | Low resonance |
| `resonance_high` | `#82d461` | High resonance |

Applied via TLibs `StringFormatter.formatHex` in Batch 2 (`GuiText`).

## Cast modes

Stable config IDs (do not rename without migration):

| ID | GUI position | Role |
|----|--------------|------|
| `surge` | Left (slot 12) | Fast power, pushes corruption |
| `flow` | Right (slot 14) | Slow ramp, builds tranquility |

Display names and lore live in `gui.yml` only.

## Equilibrium (session)

Single axis from **-100** (corruption) to **+100** (tranquility). Stored as `double` with 2 decimal precision; display trims trailing zeros (`23.5`, not `23.50`).

| Value | Behavior |
|-------|----------|
| `< 0` | Compounding passive drift deeper into corruption (`passive_corrupt_per_hour` in config) |
| `0` | Stable (no passive drift) |
| `> 0` | Linear decay toward `0` (`tranquility_decay_per_hour`); stops at `0` |

Config rates are **per hour**; `MagicTickService` applies `rate / 3600` each second.

The character head shows **Corruption** or **Tranquility** (colored label) plus bar and absolute magnitude, live drift spell percents when not zero, and previews at 60 corruption and 100 tranquility.

Element resonance values are also `double` (default `0.0`, max `100.0` per element).

Each element has a passive drift rate in **per hour** (`resonance.decay_per_hour` in `config.yml`, optional `decay_per_hour` override per element). Negative declines resonance; positive gains it. The element icon lore shows the bar plus a drift line (`-0.02/h`, same style as equilibrium), live combined mana/damage/cooldown percents, and an At 100 resonance preview.

Bound skills in `skills.yml` get MythicLib RELATIVE skill modifiers (`mana`, `cooldown`, `damage`) synced from the Resonance session so mana and cooldown checks match GUI Now.

Open Resonance GUIs refresh every second while viewed (same pattern as SimpleFactions `InventoryUpdater`).

## Meditation

Sit with GSit on the circle center (must be in a vehicle). Eight InteractibleFurniture `pedestal` pieces are required:

- Cardinals: three empty blocks then pedestal (`C---P`, offset 4)
- Diagonals: two empty steps then pedestal (offset 3)

No chat or action bar. A white starter orb appears in front of you; left-click hitscan (VehicleFramework-style) begins the orbit field. White orbs are Flow, red are Surge. Mix follows Equilibrium (`whiteChance = 0.5 * (1 + eq/max)`). Hitting red starts a 10s all-Surge lock; another red hit refreshes it.

Each hit costs 1 Focus (TFMCCore, character-keyed). Flow nudges Equilibrium up; Surge nudges it down. Resonance toward cap `sum(element power on sockets) / 8`. Stub artifact: blaze powder = Oseni power 20. Missing item on a pedestal is 0 power; missing furniture means no session.

## Persistence (MagicProfile)

Keyed by **character id** (`RPCharacter.getId()`), not player UUID. Files live under `plugins/Magic/data/characters/<characterId>.json`.

| Event | Action |
|-------|--------|
| Character activated (login or switch) | Load profile, or create defaults and write the file |
| Character switch | Save previous character, then load the new one |
| Player quit / plugin disable | Save the loaded character profile and unload the session |

Admin `/magic open` without a character stays ephemeral (nothing saved). Drift still ticks in memory while online and is flushed on save.

## Dependencies

- **TLibs** (required) - item refs, formatHex
- **ItemsAdder** (required) - `ia.` icons in gui.yml / elements
- **TFMCCore** (required) - character Focus pool
- **InteractibleFurniture** (required) - pedestals
- **RPCharacters** (soft) - character head and per-character MagicProfile persistence

## Commands

| Command | Permission | Batch |
|---------|------------|-------|
| `/resonance` | `magic.use` | Opens resonance profile GUI |
| `/magic reload` | `magic.admin` | Reload configs |
| `/magic open` | `magic.admin` | Open GUI (bypasses character check) |
