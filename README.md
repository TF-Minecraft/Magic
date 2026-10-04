# Magic

> Elemental resonance, artifacts, and mage equipment for TF-Minecraft.

Magic connects a character's relationship with the elements to meditation, artifacts, and spellcasting. Players develop elemental resonance, manage the balance between Flow and Surge, and prepare mage weapons whose attunement determines the spells they can support.

## Features

- **Elemental resonance** — track affinities across TF-Minecraft's magic traditions, with resonance influencing spell mana cost, damage, and cooldowns.
- **Flow and Surge** — casting modes interact with equilibrium, tranquility, and corruption to shape a character's magical state over time.
- **Interactive meditation** — meditate around artifacts and engage with orbiting orbs, spending mental focus to develop resonance.
- **Artifacts and attunement** — artifacts carry elemental aura, with display, storage, and care affecting how that aura remains available.
- **Crafted mage equipment** — assemble staffs, wands, and swords from parts, then work with charges and orbs to shape their elemental capabilities.
- **Casting consequences** — insufficient attunement can prevent a cast, while unstable equipment or carrying multiple staffs can cause a spell to fumble.

Magic keeps resonance profiles tied to roleplay characters through RPCharacters. Its menus make current affinities and modifiers visible, while its equipment systems connect preparation at the crafting station with the character's experience when casting spells.

## Staff weapon commands

`/magic weapon give <player> <staff|wand|sword> <element> <aura> <part> [part...]`

Gives one completed mage weapon to an online player. Use part IDs from `gear/parts.yml`
and an element ID from the loaded element configuration. Supply exactly one part for
each required category, respecting the core's part limit. Aura is the raw attunement
amount and must reach a configured tier band. The command applies attunement, finalizes
sockets and records no material cost. It does not change the recipient's resonance.
The recipient needs an empty inventory slot.

`give-permission` in `config.yml` defaults to `magic.weapon.give` (operators).
Set it to your staff permission; a blank value disables giving. This permission is
independent of `magic.admin`. Reload configuration with `/magic reload`.
Tab completion suggests recipients, archetypes, elements and enabled part IDs.

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/Magic/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

## Tests and coverage

Use Java 21 and the pinned dependencies installed by `.github/scripts/prepare-release.sh`, then run:

```sh
mvn -B --no-transfer-progress clean verify
```

JUnit exercises domain calculations, configuration loading, item persistence, artifact generation,
character sessions, and plugin lifecycle through MockBukkit and isolated external integration mocks.
JaCoCo measures every production class with no coverage exclusions. Maven `verify` requires
100% line, branch, and instruction coverage. The HTML report is
`target/site/jacoco/index.html`; XML and CSV reports are alongside it. CI uploads coverage reports
for builds and releases. Test dependencies are not bundled into the plugin JAR.

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and bundled material
retain their own licenses.
