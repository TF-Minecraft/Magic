# Magic administration

These commands require `magic.admin` (the existing `magic.admin.reload` alias
also grants administration). Character targets must be online and have a loaded
active character. Commands affect that character only, save immediately, and
resync spell modifiers. If saving fails, the edit is rolled back and the command
reports failure. Console may use character commands; held-item commands
must be used by a player.

```text
/magic player get <player>
/magic player reset <player>
/magic resonance get <player>
/magic resonance <set|add|remove> <player> <element|all> <amount>
/magic resonance reset <player> [element|all]
/magic equilibrium <get|set|add|remove|reset> <player> [amount]
/magic castmode <get|set|reset> <player> [flow|surge]
```

Amounts are required for set/add/remove and omitted for get/reset. `add` accepts
signed deltas; `remove` takes a nonnegative magnitude. Resonance is clamped to
zero through the configured school maximum; equilibrium uses the configured
minimum and maximum. Both keep two decimals. Positive equilibrium is tranquility,
negative is corruption, and zero is neutral. For example, `equilibrium set Alice
-30` gives 30 corruption; `equilibrium add Alice 10` leaves 20 corruption.

`player get` shows character ID, casting mode, equilibrium, Focus and all school
resonances. `player reset` resets **only Magic's character values** to configured
defaults; Focus is unchanged. Resetting a single value also restores its configured
default. Fractional passive drift is cleared when its value is edited.

Focus belongs to RPCharacters; use `/focus get`, `/focus set`, `/focus add`,
`/focus remove`, `/focus reset` or `/focus restore` with `rpchar.focus.admin`.
Mana and cooldowns belong to their provider plugins. Spell modifiers are derived
from resonance, equilibrium and gear, so are recalculated rather than edited.
School unlocks remain permissions.

## Held artifact and weapon values

```text
/magic artifact inspect
/magic artifact fill <get|set|add|remove|reset> <element|all> [amount]
/magic artifact muffle <get|set|add|remove|reset> [percent]
/magic artifact wear <get|set|add|remove|reset> [amount]
/magic gear inspect
/magic gear rift <get|set|add|remove|reset> [percent]
/magic gear durability <get|set|add|remove|reset> [points]
```

Edits require exactly one appropriate item in the main hand. Artifacts must have
an ID. Fill targets existing aura slots and clamps to each slot's capacity;
reset empties it. Muffle is a percentage from zero to 100; reset clears it and
editing re-anchors the care clock. Wear is accumulated lifetime wear, not remaining
integrity: reset repairs a still-existing artifact; reaching its wear budget
crumbles it into the normal spent husk. A husk cannot be repaired by these commands.
Wear edits are unavailable while wear is disabled. Artifact inspection also shows
its ID, aura capacities, wear budget and active sharing count.

Rift is the weapon's permanent Rift, clamped to the configured cap. Temporary
casting mismatch is derived from equipment and character state. Durability means
remaining points, uses custom max durability when present, and stops at one point
to preserve MMOItems weapons. Reset repairs to maximum. Rift/durability require
whole numbers; unbreakable or nondamageable weapons have no editable durability.
Gear inspection also shows elemental attunement. Edits preserve item identity,
parts and sockets and update affected lore. They are logged to Magic's aura log
when logging is enabled. Artifact fill edits preserve capacity, rarity, sharing
history and identity.

Existing `/magic artifact setfill`, `setmuffle`, `unmuffle`, `/magic weapon give`,
`/magic shrine fill`, `/magic artifact create` and `/magic refresh` still work.
Tab completion lists the new routes and arguments for permitted senders.

## Validation

Run `mvn -B --no-transfer-progress clean verify` with Java 21 and the pinned
dependencies. The existing durable gear-storage tests require Linux filesystem
semantics. `tools/dev-admin-values.cjs` exercises both this plugin and the
RPCharacters Focus commands on TFMCDev01 through its existing Dev bot helpers.
It uses only the dedicated `AdminValueProbe` character, removes its temporary
operator status, resets its test values and clears its test inventory afterward.
