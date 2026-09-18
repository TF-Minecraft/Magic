# Magic - Implementation batches

Work in order. Each batch should compile and be testable before the next.

---

## Batch 1 - Scaffold

- [x] Maven project, plugin.yml, config/gui/messages/elements yaml
- [x] Bootstrap class, stub loaders, Cache, Messages
- [x] `/resonance reload` (no-args silent until Batch 4)
- [x] Documentation (`SYSTEM.md`, this file)

**Test:** `mvn package`, plugin enables, `/resonance reload` succeeds.

---

## Batch 2 - Config loaders + layout constants

- [x] `GuiLoader` populates `GuiCache`
- [x] `ElementsLoader` populates element registry
- [x] `GridLayout` with slot helpers
- [x] `GuiText` (TLibs formatHex)

**Test:** `/resonance reload` logs element count; slot constants match gui.yml.

---

## Batch 3 - GUI builder (static render)

- [x] `ResonanceGuiHolder`, `ResonanceGuiBuilder`
- [x] 54-slot layout: filler, head placeholder, cast modes, element row
- [x] Temporary `/resonance open` admin command for layout testing (player open moves to Batch 4)

**Test:** `/resonance open` shows correct frame.

---

## Batch 4 - Command + open/close wiring

- [x] `/resonance` opens GUI
- [x] `ResonanceGuiManager` cancels clicks
- [x] RPCharacters active-character guard (admin `/resonance open` bypasses)

**Test:** GUI opens, items cannot be taken.

---

## Batch 5 - RPCharacters identity on head

- [x] `RpCharactersBridge`, wardrobe texture head, character lore placeholders

**Test:** Active character skin and name in GUI.

---

## Batch 6 - Cast mode toggle (session only)

- [x] `ResonanceSession`, click cast mode slots, re-render

**Test:** Toggle Surge/Flow visually; no persistence.

---

## Batch 7 - Polish

- [x] Complete messages.yml, border filler, manual test matrix

**Test:** Black border frame + purple inner filler; all chat from messages.yml.

See [TEST_MATRIX.md](TEST_MATRIX.md) for full manual checklist.

---

## Future batches

| Batch | Scope |
|-------|--------|
| 9 | Real resonance % on element row |
| 10 | Corruption / tranquility meters |
| 11 | MythicLib cast modifiers |
| 12 | Skill-to-element map |
| 13 | Artifacts + pedestals (full system; stub blaze powder is in 14) |
| 14 | Meditation minigame (stub artifacts) |
| 15 | TFMCCore stats (optional) |

---

## Batch status

| Batch | Status |
|-------|--------|
| 1 Scaffold | Done |
| 2 Loaders + GridLayout | Done |
| 3 GUI builder | Done |
| 4 Command + GUI | Done |
| 5 RPCharacters head | Done |
| 6 Cast mode toggle | Done |
| 7 Polish | Done |
| 8 MagicProfile persistence | Done |
| 14 Meditation minigame | Done (stub artifacts) |
