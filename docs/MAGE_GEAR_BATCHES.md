# Mage Gear - Phase plan

Mage weapon crafting: charges, station, orb minigame, cast gating, lifecycle.

Work one **phase** at a time. Each phase is a set of **batches**. Every batch should
compile and be testable before the next. Do not start a phase before the previous
phase is signed off.

All design decisions below are **locked**. Anything not listed here is an open item
at the bottom, not an implementer's choice.

---

## Locked decisions

### Ownership and scope

| Decision | Locked value |
|---|---|
| Host plugin | `magic`, new package `net.tfminecraft.magic.gear` |
| Runes granting spells | Already handled by MMOItems gemstones. We build **none** of it |
| Skill system | **MMOItems key combinations, not MMOCore.** A maxed weapon holds about **4 spells**, so 4 is the socket ceiling |
| MMOItems configs (gem colours, base templates, rune items) | Fran's. We ship a proof of concept he tunes |
| Armor runes | Passive only (mana, protection). Never grant spells, never gate casts |
| Socket tier groups | Use TLibs `socket-tier-groups` as they exist today. No new groups |
| Enabled elements at launch | Planar 4 (cerrith, seithr, oseni, mitlan) + bloodmagic |
| Configured but disabled | arcanum, spirit, illusion, necromancy, shadowmancy |

### Enchanted Charge

- A charge is a **separate consumable vessel type**, not an artifact. Both share one
  aura layer (`AuraData` plus the `AuraVessel` interface) so the shrine, sacrifice and
  admin fill paths work on either. No new charging system is written.
- Charge **tier comes from the charge item**, crafted in another plugin. We take one
  input item path and one aura cap per tier from config and nothing else.
- A charge starts blank and imprints **every element the shrine scores** on placement,
  exactly like an artifact sitting on a pedestal. Caps come from its tier.
- Displayed tier is derived from **aura gathered before removal**, not stored.
- Charges are excluded from meditation and from the care/muffle system.
- Fill sources are **unchanged** from what already ships:

| Element | Scenery shrine | Sacrifice rite | `/magic shrine fill` |
|---|---|---|---|
| cerrith, seithr, oseni, mitlan | yes | no | yes |
| bloodmagic | blocked | yes | yes |
| necromancy, shadowmancy | blocked | disabled | yes |
| arcanum, spirit, illusion | blocked | no | yes |

- Charges display as **tier bands** (`Cerrith II`), never raw aura numbers.
- **No trance state and no station locking.** The rite happens to the charge at the
  shrine, before the station is involved. Every element uses one identical flow and
  only the fill source differs.

### Station and craft

- Standalone block. **Not** required to be at a shrine.
- GunsAndGadgets-style GUI: pick archetype, pick parts, live preview, click to craft.
- **Socket count** comes from the parts chosen.
- **Socket tier** comes from the charge tier.
- **Weapon resonance** comes from the orb minigame.

| Archetype | Socket groups | Melee |
|---|---|---|
| staff | projectile, support, minor spell, major spell | no |
| wand | minor spell, major spell | no |
| sword | support, spell | yes |

### Weapon resonance

- Stored **per element** on the weapon, same PDC container shape as `Artifact`.
- Applying a charge uses **highest only**, never additive. `max(existing, incoming)`.
- A different element **adds a key**. Multi-element weapons are supported and are the
  reason the gate is per-spell rather than per-weapon.
- One-way ratchet: a requirement can never be lowered. The only escape is the
  recycler. The craft UI **must warn** before committing a charge above the player's
  current resonance.
- Displayed as tier bands (`Cerrith IV`), never raw numbers.

### Cast gating - two separate failures

**Refusal** is a locked door. Deterministic, costs nothing.

```
element  = SkillElementRegistry.elementOf(skillId)
required = weaponRequirement(element)
actual   = session.getResonance(element)
if (actual < required) -> setCancelled(true), NO whenCast(), no cost
```

**Rift** is a fumble. Flat percentage, costs mana and cooldown.

```
if (ThreadLocalRandom.nextDouble() < rift / 100.0)
    -> cast.whenCast(meta), then setCancelled(true)
```

- **Refusal is checked first.** Never roll Rift on a spell that was never going to fire.
- Refusal is **not** a whiff and must never use the word. Only Rift is a whiff.
- Only weapons gate. Armor never gates.
- Nothing derived is stored. Refusal and alignment are computed live from
  `ResonanceSession` plus weapon PDC. **No JSON, no disk sync, no temporary lore.**

### Removals

Skill-level `min_resonance` is superseded by the weapon requirement and is deleted:

- `cast.min_resonance_curve` in `config.yml`
- `Cache.minResonanceCurve` and `ConfigLoader.normalizeCurve`
- `skillMinResonance` map and `minResonance()` in `SkillElementRegistry`
- The curve branch in `ResonanceCastListener`

**Keep** the `skills.yml` element binding and the nested-section parsing in
`SkillsLoader`. The binding is now the key the entire gate looks up and is also
iterated by `SpellModifierApplyService`. Only the `min_resonance` field goes.

### Alignment bonus

- **One-sided.** A bonus scaled by the weapon's tier in the spell's element. There is
  no penalty side, because refusal already replaces it.
- Folded into `SpellModifierApplyService` as a third `ModifierTriple.combine`.
- Behind a config flag, default small. This is the tuning knob for power creep.
- Resync on `PlayerItemHeldEvent`.

### Lifecycle

- Recycler: hardcode a provider into `RecycleProviderChain.rebuild()`, matching the
  AC and GG pattern. Resonance is lost, materials return.
- Revision: `RevisionTracker` plus provenance PDC plus lazy refresh, copied from AC/GG.
  Refresh only touches the slot the player clicked, dropped, or hotbar-selected.
- Orphaned runes follow `GunBrokenMarker`: mark, block, never delete config ids. Use a
  disabled flag instead. Staff are told never to change rune counts from crafting.

---

## Phase 1 - Enchanted Charges

Unblocks every later phase. Charges become a second vessel type over the existing
aura layer, so all three fill paths work on them with no new charging code.

### Batch 1.1 - Extract the vessel abstraction

Pure refactor. No behaviour change, no config change.

- [ ] `AuraData`: cap/fill maps, PDC read/write, `aura_data` blob, extracted from `Artifact`
- [ ] `AuraVessel` interface + `VesselKind` enum
- [ ] `Artifact implements AuraVessel`, delegating storage to `AuraData`
- [ ] `AuraVessels.fromItem(ItemStack)` resolver (artifacts only for now)
- [ ] Migrate shrine, sacrifice and command call sites to the interface. Artifact-only
      sites (`ArtifactLore`, `ArtifactCareStore`, `MeditationCircle`, `ArtifactListener`,
      `ArtifactPathFactory`, `ArtifactItemBuilder`) stay on the concrete type

**Test:** artifacts charge, meditate, sacrifice and admin-fill exactly as before.

### Batch 1.2 - Charge type and registry

- [ ] `charges.yml`: one input item path and one `aura_cap` per tier
- [ ] `ChargeDef`, `ChargeRegistry`, `ChargesLoader` in the reload chain
- [ ] `ChargeKeys.chargeTier()`; charges never receive `artifact_id`
- [ ] `Charge implements AuraVessel`; resolver matches charges before artifacts
- [ ] A blank charge resolves as a vessel with zero caps

**Test:** `/magic reload` logs the tier count; a charge resolves as a Charge and an
artifact still resolves as an Artifact.

### Batch 1.3 - Imprint, charge, and display

- [ ] Imprint every shrine-scored element on placement, caps from the charge's tier
- [ ] `allowedElements` branches by kind: artifacts use primary + affinity, charges use
      all capped elements
- [ ] Admin fill imprints a cap on a blank charge so disabled elements work by command
- [ ] `tiers` config block per element (I-IV fill thresholds) + `TierBands.bandOf`
- [ ] Charge lore renders `Cerrith II` bands; artifact lore untouched
- [ ] Charges excluded from meditation circle power and from care/muffle
- [ ] `ShrineChargeFx` reused unchanged

**Test:** One charge per row of the fill-source table above behaves as the table says,
and displayed tier tracks aura gathered.

---

## Phase 2 - Station and craft

Produces a prepared weapon on the station (not in inventory). Charges apply there.
Shift-right-click ejects. Socket count comes from parts (ceiling 4). Socket colours
come from a config map of resonance band to TLibs colour prefix.

### Flow

1. Empty station, right-click: assembly GUI
2. Click craft: consume part costs, station holds the prepared item
3. Right-click with a charged charge: merge requirements; first charge locks socket colours
4. Shift-right-click empty hand: drop the item (blocked if orbs are in the air)

### Batch 2.1 - Station block and GUI scaffold

- [x] Station block path in config, right-click opens GUI when empty
- [x] Archetype selection (staff, wand, sword), session state per player
- [x] Selecting an archetype clears part selections
- [x] Occupied station does not open the GUI

**Test:** GUI opens at the block, archetype switches, clicks cannot take items.

### Batch 2.2 - Parts and socket count

- [x] `gear/parts.yml`: part id, archetype compatibility, socket contributions, cost
- [x] Part picker per category, filtered by archetype
- [x] Live preview showing the exact socket layout that will be produced (clamped to 4)

**Test:** Changing parts changes the previewed socket count and groups immediately.

### Batch 2.3 - Prepare (not give)

- [x] Build MMOItems base from the archetype template
- [x] Write empty `GEM_SOCKETS` using `prefix.default` + archetype slot suffixes
- [x] Stamp craft provenance PDC (part ids, revision 1)
- [x] Consume inputs; store the item on the station; persist across restart

**Test:** Craft consumes materials, station shows the weapon, inventory does not gain it.

### Batch 2.4 - Charge, warn, eject

- [x] Consume the charge, write fill into the weapon requirement container
- [x] Highest-only merge; second element adds a key
- [x] First charge rewrites empty socket colours from `prefix[highestBand]` and locks them
- [x] Requirement lore renders as tier bands
- [x] Warn and confirm when the charge's band exceeds the player's band in that element
- [x] Shift-right-click empty hand ejects; no tool; refuse while `orbSessionActive`

**Test:** Charging twice with Cerrith I then Cerrith II yields Cerrith II, not III.
Charging Cerrith then Oseni yields both.

---

## Phase 3 - Orb minigame

Ported from `meditation/`, which is already particle-only with no entities.

Phase 2 merged the charge the moment it was applied. This phase **delays the write**
until the minigame ends. Warn/confirm, blank-charge refusal, and the eject lock are
unchanged. The charge is consumed at the start, so a disconnect or shutdown finishes
the session with whatever was captured rather than refunding.

Tuning lives in `gear/orbs.yml`. Difficulty keys off the **charge item tier** (1-4),
not the displayed band, so a low tier charge is the easy game.

### Batch 3.1 - Orbs around the station

- [x] Port `MeditationOrb` shape; anchor `orbit()` to the **station**, not player eye
- [x] Port hitscan (left-click plus `PlayerAnimationEvent`, dot-product ray test)
- [x] Session starts when a charge is used on a weapon at the station
- [x] `GearChargeService` no longer merges on apply; it snapshots the fill and starts
      the session. One session per station, a second charge is refused

**Test:** Orbs orbit the station and can be hit from any angle; blocks occlude them.

### Batch 3.2 - Good and bad orbs

- [x] Fixed element-independent colour pair for good and bad
- [x] Differentiate on **three** axes: colour, particle type, motion. Good gets
      `END_ROD` sparkle and smooth motion, bad gets `SMOKE` and faster jitter
- [x] Beam FX from hit orb back to the station, coloured by the **charge's element**

**Test:** Good and bad are distinguishable at distance and in greyscale. No bad orb is
ever black.

### Batch 3.3 - Capture and Rift

- [x] Good hits accumulate toward `good_target`; capture is a **fraction** of the
      charge's fill, `clamp(goodHits / goodTarget - misses * miss_penalty, 0, 1)`
- [x] Bad hits add permanent Rift to the weapon (`rift.per_bad`, capped at `rift.cap`)
- [x] Timed window; a good orb that expires unhit costs `miss_penalty`. Bad orbs that
      expire cost nothing
- [x] Result written to weapon PDC on completion: highest-only merge of the captured
      fill, Rift integer, and socket colours from the **captured** band on first charge

**Test:** A perfect run yields the charge's full tier; a poor run yields a lower band.

### Batch 3.4 - Difficulty by tier

- [x] Orb count, speed, and window scale with charge tier. Lower tier is easier
- [x] Re-running with another charge can reduce existing Rift by `rift.per_recharge`,
      applied once on completion and never on the first charge

**Test:** Tier I is comfortably clearable; tier IV is not, without practice.

---

## Phase 4 - Cast gating

The weapon is the gate. Only the held item matters: main hand when it is mage gear,
otherwise offhand. Anything that is not mage gear casts exactly as it does today, so a
non-mage holding a bound skill is never touched.

An element the weapon never took a charge in reads as a requirement of nothing, and
that **refuses**. A Cerrith staff cannot fire Oseni spells at all. A staff holding
Oseni IV and Cerrith I refuses only the element the caster is short on.

### Batch 4.1 - Remove min_resonance

- [x] Delete the curve config, `Cache` field, `normalizeCurve`, `skillMinResonance`
- [x] Keep the `skills.yml` element binding and nested-section parsing
- [x] `ResonanceCastListener` reduced to a shell ready for the two new branches

**Test:** Plugin loads with no curve config present; skills still resolve to elements.

### Batch 4.2 - Refusal

- [x] Recheck `SkillIdResolver` against **MMOItems** abilities. It now tries the
      MythicLib handler id first and falls back to MMOCore `RegisteredSkill.getName()`
- [x] Read the weapon's requirement for the **spell's** element
- [x] Below requirement, or no requirement at all: cancel **without** `whenCast()`, so
      nothing is spent
- [x] Title and subtitle that never say "whiff"; `BLOCK_BEACON_DEACTIVATE`
- [x] Chat states the requirement and the player's current value, rate-limited to once
      per 30s per player, weapon and element

**Test:** A weapon needing Oseni IV and Cerrith I lets Cerrith spells through and
refuses Oseni ones. Mana is unchanged after a refusal. Spamming does not flood chat.

### Batch 4.3 - Rift

- [x] Flat percentage roll, no curve
- [x] On fail: `whenCast()` then cancel, so mana and cooldown burn
- [x] `*Whiff*` title with the current Rift percentage, lava extinguish sound
- [x] Rolled only after refusal has passed

**Test:** 100 Rift always whiffs and always costs. 0 Rift never whiffs.

### Batch 4.4 - Alignment bonus

- [x] One-sided bonus scaled by the weapon's band in the spell's element. A missing
      element contributes nothing and never subtracts
- [x] Third `ModifierTriple.combine` in `SpellModifierApplyService`
- [x] Resync on `PlayerItemHeldEvent`, next tick so the swapped item is the one read
- [x] Behind `gear.alignment.enabled`, default off until tuned

**Test:** With the flag off, modifiers match today's values exactly.

---

## Phase 5 - Lifecycle

Parts carry no stats today, only cost and socket counts, so a refresh is a surgical
socket-layout rewrite rather than a full item rebuild. Applied runes are merged into the
new socket data, never overwritten. A rune that no longer fits is held on the weapon,
not dropped, and the weapon is dead until the player reclaims it.

### Batch 5.1 - Revision and refresh

- [x] `RevisionTracker` over parts and archetypes, persisted to `data/revisions.json`
- [x] Revision content covers gameplay fields only. Part: type, gear types, item path,
      cost, sockets, disabled. Archetype: template, melee, required, slots. Name and
      lore are excluded so cosmetic edits do not churn revisions
- [x] Provenance stamps the live revision per part plus an archetype revision, and gains
      `missingPartIds()`, `isOutdated()` and `syncRevisions()`
- [x] `GearRefresher` recomputes the socket layout for the weapon's own band and merges
      existing gems back in. It deliberately bypasses `sockets_locked`, which only exists
      to stop a second charge from re-colouring
- [x] Refresh listener on drop, inventory click, hotbar select, at MONITOR, next tick
- [x] `/magic refresh` forces a refresh on the held item

**Test:** Editing a part's sockets and reloading refreshes a crafted weapon on next
click, and its runes survive. Reloading twice with no yaml change refreshes nothing.

### Batch 5.2 - Orphaned runes

- [x] Broken marker when a socketed rune no longer fits, or when a stamped part id no
      longer exists
- [x] Orphaned runes are pulled into a `gear_orphans` payload on the weapon as
      `mmoType:mmoId` pairs. Nothing is ever dropped
- [x] A broken weapon is blocked **whole**, GunsAndGadgets style: every cast is refused
      with a damaged message, charges are refused, and it is excluded from recycling
- [x] Marker keeps the display name and renders through `WeaponLore` instead, since the
      name cannot be regenerated the way a gun's can
- [x] Player reclaims deliberately: right-click an **empty** station holding the marked
      weapon. Runes go to the inventory, the marker clears, sockets rewrite to the
      current layout, the weapon stays in hand
- [x] A weapon broken by a **deleted** part id stays broken after reclaim. Only restoring
      the id fixes it, so prefer `disabled: true` over deletion

**Test:** Shrinking a layout below the socketed rune count marks the weapon rather than
dropping anything. Nothing is ever lost to the ground.

### Batch 5.3 - Recycler

- [x] `MagicGearProvider implements RecycleProvider`, reads provenance PDC, priority 15
- [x] Register in `RecycleProviderChain.rebuild()` behind a `Magic` enabled check
- [x] Resonance lost, materials returned, charge not returned
- [x] A weapon with runes still socketed is **not** recyclable. There is no unsocket
      flow, so recycling one would destroy them

**Test:** Recycling a crafted weapon returns its part materials at the configured rate.

---

## Open items

Not implementer's choices. Decide before the phase that needs them.

| Item | Needed by | Note |
|---|---|---|
| Resonance thresholds per tier per element | Phase 1.2 | Same table (`charges.yml` bands) gates displayed charge tier, socket colour band, and the apply-warn vs player resonance |
| Socket contribution per part | Phase 2.2 | YAML `sockets:` map on each part. Sum per slot, clamp total empty sockets to 4. Fran tunes counts |
| Socket colour vs resonance band | Phase 2.3 | `gear/socket-colours.yml` prefix map. Colour = `prefix[band] + " " + archetype slot suffix`. First charge only. Zero charges uses `prefix.default` |
| Rift per bad orb, and Rift removed per re-charge | Phase 3.3 | Closed. `gear/orbs.yml` `rift:` block. PoC `+5` per bad hit, cap `100`, `-10` per later completed charge. Fran retunes |
| Alignment bonus magnitude per tier | Phase 4.4 | Closed. `config.yml` `gear.alignment` band rows, one `ModifierTriple` each. Ships with `enabled: false`. The power creep knob |
| Orphaned rune blast radius and reclaim point | Phase 5.2 | Closed. The whole weapon is blocked, not just the socket. Reclaim is right-clicking an empty station with the marked weapon in hand, no new GUI |

---

## Phase status

| Phase | Status |
|---|---|
| 1 Enchanted Charges | Done |
| 2 Station and craft | Done |
| 3 Orb minigame | Done |
| 4 Cast gating | Done |
| 5 Lifecycle | Done |
