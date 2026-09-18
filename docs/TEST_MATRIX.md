# Magic - Manual test matrix (Batches 1-8, 14)

Run after each release or before merging core GUI work. Check off each item when verified in-game.

## Build

- [ ] `mvn -f magic/pom.xml package` succeeds
- [ ] Plugin enables on server start without errors
- [ ] JAR deploys to server plugins folder

## Reload (Batch 1-2)

- [ ] `/magic reload` with `magic.admin` shows success message
- [ ] Console logs element count after reload
- [ ] Console logs `shrines=10` (or shrine element count) after artifact generator load
- [ ] Invalid config shows reload failed message (optional negative test)

## Commands and permissions (Batch 4, 7)

- [ ] `/resonance` opens GUI for player with `magic.use`
- [ ] `/resonance` without `magic.use` shows `open.no_permission`
- [ ] `/magic reload` without `magic.admin` shows `admin.no_permission`
- [ ] `/magic open` without `magic.admin` shows `admin.no_permission`
- [ ] Console sender for `/magic open` shows `open.players_only`

## Open guards (Batch 4)

- [ ] Player without active character gets `open.no_character` (when RPCharacters loaded)
- [ ] `/magic open` bypasses character check (admin layout test)

## GUI layout (Batch 3, 7)

- [ ] 54-slot inventory with arcane purple title
- [ ] Black border frame on perimeter filler slots
- [ ] Purple inner filler on non-border filler slots
- [ ] Character head in slot 4
- [ ] Surge in slot 12, Flow in slot 14
- [ ] Ten element icons: spirit 20, arcanum 22, illusion 24; row 3 Cerrith/Seithr/Oseni/Mitlan (28, 30, 32, 34); row 4 Necromancy/Shadowmancy/Bloodmagic (38, 40, 42)

## RPCharacters head (Batch 5)

- [ ] Active character shows wardrobe skin on head
- [ ] Display tab name shown as head title
- [ ] Fallback head when no character (admin `/magic open`)

## Cast mode toggle (Batch 6)

- [ ] Default cast mode from `config.yml` selected on first open (glint + green bold Selected lore)
- [ ] Unselected cast mode shows Click to Select
- [ ] Click Flow: right glints, left does not; head lore shows `Casting: Flow`; chat + chime feedback
- [ ] Click Surge: switches back; chat + chime feedback
- [ ] Close and reopen GUI: session mode retained
- [ ] Relog with active character: cast mode restored from profile

## Persistence (Batch 8)

- [ ] Join with active character: `plugins/Magic/data/characters/<characterId>.json` is created if missing
- [ ] Change cast mode, wait for equilibrium/resonance drift, relog: values restored
- [ ] Switch character: each character keeps its own cast mode / equilibrium / resonance
- [ ] Quit: profile file updated on disk
- [ ] `/magic open` with no character: GUI works, no character file written
- [ ] `/magic reload`: online session values stay; new elements get default resonance on next load

## Spell modifier lore

- [ ] Element icon at 0 resonance / 0 equilibrium: `Now` is Mana +20% Damage -20% Cooldown +20%; `At 100 resonance` is Mana -20% Damage +20% Cooldown -20%
- [ ] Character head at 0 equilibrium: no `Now` modifier block; always shows `At 60 corruption` and `At 100 tranquility`
- [ ] `/magic reload` still loads; GUI lore uses Java defaults if live `gui.yml` has no `modifiers:` section

## Spell modifiers on cast

- [ ] Add one `skills.yml` binding (live file; jar does not overwrite), `/magic reload`
- [ ] At 0 resonance / 0 equilibrium, that skill's mana check and spend are +20% cost, damage param -20%, cooldown +20% (matches element Now lore)
- [ ] Unbound skills are unchanged
- [ ] Without MythicLib or MMOCore, Magic still enables and skips apply (console note once)

## Artifact generator (Batches 1-6)

### Server setup

- [ ] TLibs, then InteractibleFurniture, then Magic
- [ ] MMOItems item `m.artifacts.template_artifact` exists
- [ ] Scrap path `m.crafting.scrap` exists
- [ ] Copy `magic/ItemsAdder/tfmc_magic` into ItemsAdder `contents/`, zip/reload IA
- [ ] Copy live `plugins/Magic/artifacts/` `model-schemes.yml`, `naming-schemes.yml`, `generator.yml`, and `shrines.yml` from the jar (existing servers are not overwritten)
- [ ] IA pack copy only if `tfmc_magic` item ids or textures changed (naming/affinity YAML is enough for this pass)
- [ ] `/magic artifact give <self> cerrith legendary` uses an `ia.tfmc_magic:` skin (not `iaalchemy`)
- [ ] `plugins/Magic/artifacts/` present (copy from jar if the data folder already existed)
- [ ] Live `messages.yml` includes `artifact.roll` and `artifact.give` keys
- [ ] Live `config.yml` has `meditation.pedestal_slot: "*"` and `artifacts.aura_cap: 80` (not overwritten on existing servers)
- [ ] IF pedestal whitelist includes `item` (reload IF if YAML was already on disk)

### Affinity companions (generator.yml groups)

Secondaries are the union of every group that contains the primary. Names: Title Case after space and hyphen; manuscripts are generic copies. Mitlan is the only water school name.

- [ ] Cerrith secondaries can include oseni, seithr, mitlan, arcanum, bloodmagic, illusion, shadowmancy, spirit (planar / source / vitae / veil / animus)
- [ ] Spirit secondaries are seithr and/or cerrith only (never arcanum)
- [ ] Necromancy secondaries are shadowmancy and/or bloodmagic only (never cerrith)
- [ ] Mitlan secondaries can include oseni, seithr, cerrith, bloodmagic; titles never say Atlan
- [ ] Arcanum secondaries are cerrith only
- [ ] Illusion secondaries are cerrith and/or shadowmancy
- [ ] `magic.(primary=cerrith;rarity=rare)` still has no affinity extras (path lock)

### Shrines (Batch 2-4)

Scoring is loaded from `artifacts/shrines.yml`. Charge runs when an artifact is placed on a `pedestal` IF slot.

- [ ] Copy live `plugins/Magic/artifacts/shrines.yml` (not overwritten on existing servers)
- [ ] `/magic reload` logs shrine element count; missing file warns and Magic still enables
- [ ] Empty Cerrith on a mixed grove pedestal: fill rises, lore `fill / 80`, `hasStoredAura` becomes true
- [ ] Moss-only shrine: little or no fill (`min_families`)
- [ ] Cerrith item on a lava shrine: Oseni slot appears and fills
- [ ] Necromancy on a grove: no Cerrith fill
- [ ] Take off the pedestal stops charging; partial fill is kept
- [ ] Sitting on the ring does not spend Focus from charging; circle still needs fill greater than 0 to start
- [ ] Grove Cerrith: green dust and enchant glyphs pull from moss/leaves into the jar while fill rises
- [ ] Cerrith on lava: orange Oseni pull; when a slot caps, burst uses the school with the highest fill
- [ ] Take: particles stop immediately
- [ ] Two slots on one pedestal: both stream without one cancelling the other

### Give / roll

- [ ] `/magic artifact roll arcanum common` reports scrap
- [ ] `/magic artifact roll necromancy legendary` secondaries are only shadowmancy and/or bloodmagic (not cerrith)
- [ ] `/magic artifact give <self> cerrith legendary` gives a named artifact with Aura lore, fill 0 / `aura_cap` (80), slots present, and an `ia.tfmc_magic:` model
- [ ] `/magic artifact roll` chat still prints roller share caps; those are not the stored item cap
- [ ] `/magic artifact give <self> arcanum common` gives scrap with no Aura lore
- [ ] `/magic artifact give <self> spirit rare` and `illusion rare` succeed (not unknown element)

### Meditation with generated artifacts

Empty generated items have cap PDC but `hasStoredAura` is false until shrine charge (or an older pre-empty item).

- [ ] Newly given empty artifact on a pedestal does not start a session; sit shows `meditation.no_artifacts`
- [ ] Charged artifact (fill > 0) on a pedestal slot counts for the circle
- [ ] Older filled artifacts whose lore still shows Aura (even if PDC was dropped on pickup) still start a session
- [ ] Scrap on a pedestal does not count (same no-artifacts message if nothing else is charged)
- [ ] Sit on a complete ring with charged artifacts already attuned: `meditation.nothing`
- [ ] Copy live `messages.yml` `meditation` keys (not overwritten on existing servers)
- [ ] Multi-element artifact: hits credit the dominant fill; other fills still feed power-by-element
- [ ] Eight pedestals + sit: session starts when at least one charged artifact is present; orbs spawn from those pedestals

## Meditation (Batch 14)

- [ ] Eight pedestals on the ring + GSit at center: white starter orb, no text
- [ ] Missing a pedestal: no session
- [ ] Hit starter: pedestals flash, orbs orbit
- [ ] White hits raise Equilibrium; red lower it and lock; another red refreshes lock
- [ ] Corrupted Equilibrium: mostly red orbs
- [ ] Charged generated artifact on a pedestal: that element's resonance rises toward `fill / 8`
- [ ] Each hit spends 1 Focus; 0 Focus does nothing
- [ ] Research experiment spends the same Focus bar (character-keyed)

## Click lock (Batch 4)

- [ ] Cannot take items from GUI
- [ ] Shift-click blocked
- [ ] Drag into/out of GUI cancelled
- [ ] Element icons have no click effect

## Artifact TLibs path (path batches 1-4)

### Server setup

- [ ] TLibs jar includes `registerPathHandler` / `ItemPathHandler.matches`
- [ ] Load order: TLibs, then Magic, then TrialRooms
- [ ] Magic enables without "No handler for path prefix magic"

### Path create

- [ ] Loot or any `getItemFromPath("magic.artifact")` gives an empty generated artifact (cap PDC, fill 0, `hasStoredAura` false; affinity secondaries allowed)
- [ ] `magic.(primary=cerrith;rarity=rare)` is Cerrith-only (no affinity extras), still fill 0 / `aura_cap`
- [ ] Invalid primary+rarity bracket gives scrap (no Aura PDC)
- [ ] `/magic artifact give` still uses companion-dice (unchanged grammar)
- [ ] `/magic artifact path <token>` still dumps spec only (does not give the item)

### TrialRooms

- [ ] Jar `loot-tables.yml` has unused `artifact_test` with `magic.artifact` and `magic.(primary=cerrith;rarity=rare)`
- [ ] Live `plugins/TrialRooms/loot-tables.yml` is not auto-overwritten; copy a line into a real table, reload TrialRooms
- [ ] Opening that chest drops a Magic artifact (not a blank `m.` template)

### ItemChecker

- [ ] `checkItemWithPath` on a **filled** artifact is true for `magic.artifact`
- [ ] Empty new artifacts (fill 0) do not match `magic.artifact` yet (`hasStoredAura` is false)
- [ ] Same filled item matches `magic.(primary=cerrith;rarity=rare)` (aura only, not a reverse recipe)
- [ ] Scrap / blank template does not match `magic.artifact`
- [ ] With Magic disabled, `magic.artifact` create is null and check is false (no ItemsAdder dirt)

## Fillchest

- [ ] Copy live `messages.yml` `fillchest` keys (not overwritten on existing servers)
- [ ] `/magic fillchest cerrith` then right-click a chest: contents wiped and filled with empty Cerrith artifacts (rarity rolled, fill 0 / `aura_cap`)
- [ ] `/magic fillchest random`: one element for the whole chest
- [ ] `/magic fillchest all`: mixed elements per slot
- [ ] Running fillchest again before click replaces the pending mode
- [ ] Wait 30s then click: expired message, chest not opened-overwritten

## Artifact care

- [ ] Player inventory / hotbar: muffle falls; no chat
- [ ] Chest or barrel (open to apply elapsed): muffle rises; lore shows `Muffled`; aura amount is usable; stored fill also drops at `aura_decay_per_hour` (`-0.05`/h); no `Usable` line; no chat
- [ ] Pedestal, `artifact_display`, or item frame: muffle falls; stored fill is still there
- [ ] Ground pickup: time on the ground muffles, then inventory recovers
- [ ] Test clock (set `seconds_per_hour: 1`, restore `3600` after): ~24s in a chest 0% to 100%; ~168s in inventory 100% to 0%
- [ ] Hold in hotbar: no hand jitter; no `Users` line; lore `%` / aura amount only when the formatted number changes
- [ ] Click or shift-click into a chest: last_tick stamps; chest time does not include pocket time; no chat
- [ ] Two characters sit the same artifact within 7 days: second sit is `1/n` yield; first character's resonance is unchanged
- [ ] After user TTL: one user again, full yield
- [ ] Character resonance only moves from meditation, `/magic resonance`, and element `decay_per_hour`
- [ ] `aura_decay_per_hour` (`-0.05`/h on every element): fill drops; recovering muffle does not restore it


