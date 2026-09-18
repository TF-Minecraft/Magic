"""Generate tfmc_magic ItemsAdder pack + Magic model/naming schemes from groups.txt."""
from __future__ import annotations

from pathlib import Path

SRC = Path(r"D:\Dokumenter\My Plugins\TFMC\Magic\textures")
PACK = Path(r"D:\Dokumenter\My Plugins\VSCode Workspace\magic\ItemsAdder\tfmc_magic")
ART = Path(r"D:\Dokumenter\My Plugins\VSCode Workspace\magic\src\main\resources\artifacts")

ORDER = [
    "oseni",
    "cerrith",
    "seithr",
    "mitlan",
    "arcanum",
    "bloodmagic",
    "necromancy",
    "shadowmancy",
    "spirit",
    "illusion",
]

# kind -> list of (span, numbers). Gated extras only, not a repeat of the all-tier set.
GROUPS: dict[str, dict[str, list[tuple[str, list[int]]]]] = {
    "oseni": {
        "orb": [("all", [0]), ("epic-legendary", [11])],
        "brooch": [("all", [13, 15])],
        "book": [("all", [20, 35, 40])],
        "ring": [("all", [147, 166, 167, 171, 172])],
        "bracelet": [("all", [151, 152])],
        "amulet": [("all", [156, 159, 177]), ("legendary", [195])],
        "necklace": [("all", [197])],
        "gem": [("all", [164])],
        "jar": [("all", [113])],
    },
    "cerrith": {
        "orb": [("all", [1])],
        "brooch": [("rare-legendary", [14, 16])],
        "book": [("all", [23, 43]), ("epic-legendary", [56])],
        "ring": [("all", [149]), ("epic-legendary", [174])],
        "bracelet": [("all", [154])],
        "amulet": [("all", [158])],
        "jar": [("all", [116])],
        "gem": [("all", [165]), ("epic-legendary", [185])],
    },
    "seithr": {
        "orb": [("all", [2, 10]), ("epic-legendary", [5, 9])],
        "book": [("all", [21, 24, 26, 46, 44])],
        "ring": [("all", [148, 168]), ("epic-legendary", [173])],
        "bracelet": [("all", [153])],
        "amulet": [("all", [157])],
        "necklace": [("all", [198])],
        "gem": [("all", [163])],
    },
    "mitlan": {
        "orb": [("all", [10]), ("epic-legendary", [9])],
        "book": [("all", [21, 41]), ("epic-legendary", [55])],
        "ring": [("all", [148, 168]), ("epic-legendary", [173])],
        "bracelet": [("all", [153])],
        "amulet": [("all", [157])],
        "necklace": [("all", [198])],
    },
    "arcanum": {
        "orb": [("all", [2, 3, 4, 7, 8]), ("legendary", [11])],
        "book": [("all", [22, 42]), ("epic-legendary", [29, 30])],
        "ring": [("all", [150, 170, 175])],
        "bracelet": [("all", [151, 155])],
        "amulet": [("all", [159, 177]), ("epic-legendary", [178])],
        "gem": [("all", [181, 180, 141, 162]), ("legendary", [140])],
    },
    "bloodmagic": {
        "orb": [("all", [0])],
        "brooch": [("all", [13, 15])],
        "eye_orb": [("all", [18]), ("legendary", [19])],
        "book": [("all", [20, 35, 37]), ("legendary", [27])],
        "ring": [("all", [147, 172])],
        "bracelet": [("all", [152])],
        "amulet": [("all", [156, 159]), ("legendary", [195])],
        "necklace": [("all", [197])],
        "gem": [("all", [164])],
    },
    "necromancy": {
        "orb": [("all", [12])],
        "brooch": [("all", [15])],
        "eye_orb": [("all", [18, 19])],
        "skull": [("all", [142])],
        "jar": [("all", [117])],
        "book": [("all", [24, 23, 26, 37, 44, 45]), ("epic-legendary", [28])],
        "ring": [("all", [166, 171])],
        "necklace": [("epic-legendary", [196])],
    },
    "shadowmancy": {
        "orb": [("all", [12, 3]), ("epic-legendary", [4])],
        "eye_orb": [("all", [18, 19])],
        "book": [("all", [23, 25, 37, 39, 45]), ("epic-legendary", [57])],
        "jar": [("all", [115, 114])],
        "ring": [("all", [148])],
        "amulet": [("all", [195])],
        "necklace": [("epic-legendary", [196])],
    },
    "spirit": {
        "orb": [("all", [9])],
        "book": [("all", [36, 38, 41])],
        "jar": [("all", [106])],
        "ring": [("all", [173])],
        "amulet": [("all", [157, 176]), ("epic-legendary", [178])],
        "necklace": [("all", [199])],
        "gem": [("all", [163])],
    },
    "illusion": {
        "orb": [("all", [2]), ("epic-legendary", [4])],
        "book": [("all", [26, 39, 46]), ("epic-legendary", [59])],
        "jar": [("all", [106])],
        "ring": [("all", [173])],
        "bracelet": [("all", [155])],
        "amulet": [("all", [159])],
        "necklace": [("all", [198])],
    },
}

SHARED_SPANS: dict[tuple[str, int], str] = {
    ("brooch", 6): "all",
    ("brooch", 17): "all",
    ("book", 37): "common-uncommon",
}

MANUSCRIPTS = [47, 48, 49, 50, 51]


def title_case(name: str) -> str:
    """Capitalize after start, space, and hyphen."""
    out: list[str] = []
    cap_next = True
    for ch in name:
        if cap_next and ch.isalpha():
            out.append(ch.upper())
            cap_next = False
        else:
            out.append(ch)
            if ch in " -":
                cap_next = True
    return "".join(out)


NAMES: dict[str, dict[str, list[str]]] = {
    "oseni": {
        "orb": [
            "Kilnheart",
            "Clinker",
            "Brand-seed",
            "Cinderbead",
            "Hearthknot",
            "Slagpearl",
            "Coal-tear",
            "Bellows-eye",
            "Ash-pip",
            "Forge-dew",
        ],
        "book": [
            "Soot Canticle",
            "Kiln Psalter",
            "Brand Ordinances",
            "Cinder Ledger",
            "Hearth Hours",
            "Slag Hymnal",
            "Ash Ordinary",
            "Bellows Primer",
            "Ember Gloss",
            "Furnace Rubric",
        ],
        "ring": [
            "Brand-ring",
            "Bellows hoop",
            "Kiln band",
            "Cinder ring",
            "Coal-band",
        ],
        "bracelet": [
            "Slag torque",
            "Cinder clasp",
            "Hearth bangle",
        ],
        "amulet": [
            "Hearth pendant",
            "Ashen amulet",
            "Cinder latch",
            "Coal-link",
        ],
        "necklace": [
            "Forge cord",
            "Brand necklace",
        ],
        "brooch": [
            "Forge-brooch",
            "Kiln pin",
            "Ashen fibula",
            "Hearth pin",
        ],
        "gem": [
            "Fire-chip",
            "Kiln splinter",
            "Clinker facet",
            "Brand-glass",
            "Slag spark",
            "Hearth glint",
            "Ash crystal",
            "Forge fleck",
        ],
        "jar": [
            "Cinder phial",
            "Kiln crock",
            "Ash pot",
            "Brand vial",
            "Slag flask",
            "Hearth pot",
            "Soot bottle",
            "Forge crock",
        ],
        "manuscript": [
            "A Primer On Oseni",
            "Notes On The Kiln",
            "A Study On The Hearth",
            "Notes On Brand And Cinder",
            "A Primer On The Furnace",
            "Study Of Slag And Soot",
            "Notes On The Bellows",
            "A Study On Ember",
            "Primer Of The Ash",
            "Notes On The Forge",
        ],
    },
    "cerrith": {
        "orb": [
            "Sunpip",
            "Grove-seed",
            "Leafheart",
            "Verdant bead",
            "Canopy knot",
            "Pollen tear",
            "Root-pearl",
            "Dawn pip",
            "Moss-eye",
            "Thorn-dew",
        ],
        "book": [
            "Leaf-canticle",
            "Grove psalter",
            "Sun ordinary",
            "Root gloss",
            "Canopy hours",
            "Pollen primer",
            "Thorn rubric",
            "Moss hymnal",
            "Verdant ledger",
            "Dawn offices",
        ],
        "ring": [
            "Canopy ring",
            "Thorn hoop",
            "Leaf band",
        ],
        "bracelet": [
            "Root torque",
            "Grove clasp",
        ],
        "amulet": [
            "Dawn link",
            "Pollen latch",
            "Sun pendant",
        ],
        "brooch": [
            "Verdant brooch",
            "Leaf pin",
            "Moss fibula",
            "Sun pin",
        ],
        "gem": [
            "Sun-chip",
            "Leaf splinter",
            "Grove facet",
            "Moss-glass",
            "Pollen spark",
            "Root glint",
            "Thorn crystal",
            "Canopy fleck",
        ],
        "jar": [
            "Grove phial",
            "Sap crock",
            "Pollen pot",
            "Leaf vial",
            "Moss flask",
            "Root pot",
            "Dew bottle",
            "Canopy crock",
        ],
        "manuscript": [
            "A Primer On Cerrith",
            "Notes On The Grove",
            "A Study On Order",
            "Notes On Leaf And Root",
            "A Primer On Charm",
            "Study Of The Canopy",
            "Notes On Healing",
            "A Study On Dawn",
            "Primer Of The Thorn",
            "Notes On The Sun Ordinary",
        ],
    },
    "seithr": {
        "orb": [
            "Rimebead",
            "Pale knot",
            "Frost-pip",
            "Winter tear",
            "Hoarfrost eye",
            "Ice-seed",
            "Stillglass",
            "Snow-pearl",
            "Brine-pip",
            "Cold dew",
        ],
        "book": [
            "Rime hours",
            "Pale ordinary",
            "Winter gloss",
            "Hoar primer",
            "Ice rubric",
            "Still hymnal",
            "Frost ledger",
            "Snow canticle",
            "Brine psalter",
            "Cold offices",
        ],
        "ring": [
            "Winter ring",
            "Frost hoop",
            "Ice band",
        ],
        "bracelet": [
            "Ice torque",
            "Rime clasp",
        ],
        "amulet": [
            "Cold link",
            "Brine latch",
            "Pale pendant",
        ],
        "necklace": [
            "Snow cord",
            "Hoar necklace",
        ],
        "brooch": [
            "Still brooch",
            "Pale pin",
            "Snow fibula",
            "Hoar pin",
        ],
        "gem": [
            "Rime-chip",
            "Ice splinter",
            "Pale facet",
            "Frost-glass",
            "Hoar spark",
            "Snow glint",
            "Winter crystal",
            "Brine fleck",
        ],
        "manuscript": [
            "A Primer On Seithr",
            "Notes On Rime",
            "A Study On Inverted Heat",
            "Notes On Hoar And Ice",
            "A Primer On Winter",
            "Study Of Stillglass",
            "Notes On The Pale",
            "A Study On Frost",
            "Primer Of The Cold",
            "Notes On Snow Offices",
        ],
    },
    "mitlan": {
        "orb": [
            "Tidebead",
            "Current-knot",
            "Deep pip",
            "Brine tear",
            "Undertow eye",
            "Shoal-seed",
            "Azure pearl",
            "Wake-pip",
            "Eddy dew",
            "Leeward bead",
        ],
        "book": [
            "Tide hours",
            "Deep ordinary",
            "Current gloss",
            "Shoal primer",
            "Wake rubric",
            "Brine hymnal",
            "Eddy ledger",
            "Azure canticle",
            "Leeward psalter",
            "Undertow offices",
        ],
        "ring": [
            "Eddy ring",
            "Deep hoop",
            "Tide band",
        ],
        "bracelet": [
            "Shoal torque",
            "Tide clasp",
        ],
        "amulet": [
            "Undertow link",
            "Azure latch",
            "Wake pendant",
        ],
        "necklace": [
            "Brine cord",
            "Current necklace",
        ],
        "brooch": [
            "Leeward brooch",
            "Current pin",
            "Brine fibula",
            "Wake pin",
        ],
        "manuscript": [
            "A Study On Mitlan",
            "A Primer On Mitlan",
            "Notes On The Deep",
            "Notes On The Current",
            "A Primer On The Tide",
            "Study Of The Undertow",
            "Notes On The Shoal",
            "A Study On The Wake",
            "Primer Of Brine",
            "Notes On Eddy And Azure",
        ],
    },
    "arcanum": {
        "orb": [
            "Cipherbead",
            "Ley-knot",
            "Sigil pip",
            "Occult tear",
            "Glyph-eye",
            "Uncounted bead",
            "Ward-seed",
            "Hex-pearl",
            "Mute dew",
            "Axiom pip",
        ],
        "book": [
            "Cipher hours",
            "Ley ordinary",
            "Sigil gloss",
            "Glyph primer",
            "Ward rubric",
            "Hex hymnal",
            "Axiom ledger",
            "Mute canticle",
            "Occult psalter",
            "Uncounted offices",
        ],
        "ring": [
            "Axiom ring",
            "Sigil hoop",
            "Ley band",
        ],
        "bracelet": [
            "Glyph torque",
            "Cipher clasp",
        ],
        "amulet": [
            "Ley link",
            "Mute latch",
            "Ward pendant",
        ],
        "brooch": [
            "Occult brooch",
            "Ley pin",
            "Hex fibula",
            "Ward pin",
        ],
        "gem": [
            "Cipher-chip",
            "Ley splinter",
            "Sigil facet",
            "Glyph-glass",
            "Ward spark",
            "Hex glint",
            "Axiom crystal",
            "Mute fleck",
        ],
        "manuscript": [
            "A Primer On Arcanum",
            "Notes On The Source",
            "A Study On The Ley",
            "Notes On Sigil And Glyph",
            "A Primer On Wards",
            "Study Of The Axiom",
            "Notes On The Cipher",
            "A Study On The Occult",
            "Primer Of The Hex",
            "Notes On The Uncounted",
        ],
    },
    "bloodmagic": {
        "orb": [
            "Vitae-bead",
            "Pulse-knot",
            "Crimson pip",
            "Sanguine tear",
            "Heart-dew",
            "Vein-seed",
            "Clot-pearl",
            "Warm pip",
            "Offering bead",
            "Quiet pulse",
        ],
        "eye_orb": [
            "Watching vitae",
            "Lidless pulse",
            "Sanguine stare",
            "Vein-eye",
            "Offering gaze",
            "Warm iris",
            "Clot-look",
            "Heart's witness",
        ],
        "book": [
            "Vitae hours",
            "Pulse ordinary",
            "Vein gloss",
            "Offering primer",
            "Clot rubric",
            "Sanguine hymnal",
            "Heart ledger",
            "Warm canticle",
            "Quiet psalter",
            "Crimson offices",
        ],
        "ring": [
            "Warm ring",
            "Vein hoop",
            "Pulse band",
        ],
        "bracelet": [
            "Heart torque",
            "Vitae clasp",
        ],
        "amulet": [
            "Crimson link",
            "Sanguine latch",
            "Offering pendant",
        ],
        "necklace": [
            "Vein cord",
            "Pulse necklace",
        ],
        "brooch": [
            "Quiet brooch",
            "Pulse pin",
            "Clot fibula",
            "Heart pin",
        ],
        "gem": [
            "Vitae-chip",
            "Pulse splinter",
            "Vein facet",
            "Heart-glass",
            "Offering spark",
            "Clot glint",
            "Warm crystal",
            "Sanguine fleck",
        ],
        "manuscript": [
            "A Primer On Bloodmagic",
            "Notes On Vitae",
            "A Study On The Pulse",
            "Notes On Vein And Heart",
            "A Primer On The Offering",
            "Study Of The Clot",
            "Notes On Sanguine Work",
            "A Study On Warm Blood",
            "Primer Of The Quiet Pulse",
            "Notes On Crimson Offices",
        ],
    },
    "necromancy": {
        "orb": [
            "Wake-bead",
            "Bone-knot",
            "Ash pip",
            "Grave tear",
            "Still-pearl",
            "Marrow-seed",
            "Wight dew",
            "Dirge pip",
            "Barrow bead",
            "Quiet ash",
        ],
        "eye_orb": [
            "Lidless wake",
            "Grave stare",
            "Ash iris",
            "Barrow gaze",
            "Wight's look",
            "Marrow witness",
            "Dirge-eye",
            "Still watch",
        ],
        "skull": [
            "Barrow pate",
            "Quiet calva",
            "Ash crown",
            "Wake-jaw",
            "Marrow bowl",
            "Dirge-head",
            "Grave visor",
            "Wight's cup",
        ],
        "jar": [
            "Ash phial",
            "Marrow crock",
            "Grave pot",
            "Wake vial",
            "Dirge flask",
            "Barrow pot",
            "Quiet bottle",
            "Wight crock",
        ],
        "book": [
            "Wake hours",
            "Grave ordinary",
            "Ash gloss",
            "Marrow primer",
            "Dirge rubric",
            "Barrow hymnal",
            "Quiet ledger",
            "Wight canticle",
            "Still psalter",
            "Bone offices",
        ],
        "ring": [
            "Barrow ring",
            "Ash hoop",
            "Bone band",
        ],
        "necklace": [
            "Grave cord",
            "Dirge necklace",
            "Wake chain",
        ],
        "brooch": [
            "Wight brooch",
            "Bone pin",
            "Dirge fibula",
            "Marrow pin",
        ],
        "manuscript": [
            "A Primer On Necromancy",
            "Notes On The Wake",
            "A Study On The Grave",
            "Notes On Marrow And Bone",
            "A Primer On The Dirge",
            "Study Of The Barrow",
            "Notes On Ash And Still",
            "A Study On The Wight",
            "Primer Of Quiet Ash",
            "Notes On Bone Offices",
        ],
    },
    "shadowmancy": {
        "orb": [
            "Veilbead",
            "Dusk-knot",
            "Shade pip",
            "Umbral tear",
            "Gloom-eye",
            "Night-seed",
            "Mute pearl",
            "Penumbra pip",
            "Soft dark",
            "Threshold dew",
        ],
        "eye_orb": [
            "Lidless dusk",
            "Veil stare",
            "Shade iris",
            "Umbral gaze",
            "Gloom witness",
            "Night look",
            "Penumbra watch",
            "Threshold eye",
        ],
        "book": [
            "Veil hours",
            "Dusk ordinary",
            "Shade gloss",
            "Umbral primer",
            "Gloom rubric",
            "Night hymnal",
            "Mute ledger",
            "Penumbra canticle",
            "Soft psalter",
            "Threshold offices",
        ],
        "jar": [
            "Veil phial",
            "Dusk crock",
            "Shade pot",
            "Umbral vial",
            "Gloom flask",
            "Night pot",
            "Mute bottle",
            "Penumbra crock",
        ],
        "ring": [
            "Mute ring",
            "Shade hoop",
            "Dusk band",
        ],
        "amulet": [
            "Threshold link",
            "Penumbra latch",
            "Veil pendant",
        ],
        "necklace": [
            "Night cord",
            "Gloom necklace",
        ],
        "brooch": [
            "Soft brooch",
            "Dusk pin",
            "Night fibula",
            "Gloom pin",
        ],
        "manuscript": [
            "A Primer On Shadowmancy",
            "Notes On The Veil",
            "A Study On Dusk",
            "Notes On Shade And Gloom",
            "A Primer On The Night",
            "Study Of Penumbra",
            "Notes On The Threshold",
            "A Study On The Umbral",
            "Primer Of Soft Dark",
            "Notes On Mute Hours",
        ],
    },
    "spirit": {
        "orb": [
            "Breath-bead",
            "Wake-knot",
            "Quiet pip",
            "Pale tear",
            "Hush-eye",
            "Drift-seed",
            "Thin pearl",
            "Exhale pip",
            "Still breath",
            "Liminal dew",
        ],
        "book": [
            "Breath hours",
            "Quiet ordinary",
            "Wake gloss",
            "Hush primer",
            "Drift rubric",
            "Pale hymnal",
            "Thin ledger",
            "Exhale canticle",
            "Still psalter",
            "Liminal offices",
        ],
        "jar": [
            "Breath phial",
            "Quiet crock",
            "Wake pot",
            "Hush vial",
            "Drift flask",
            "Pale pot",
            "Thin bottle",
            "Exhale crock",
        ],
        "ring": [
            "Thin ring",
            "Wake hoop",
            "Breath band",
        ],
        "amulet": [
            "Liminal link",
            "Exhale latch",
            "Hush pendant",
        ],
        "necklace": [
            "Quiet cord",
            "Drift necklace",
        ],
        "brooch": [
            "Still brooch",
            "Quiet pin",
            "Pale fibula",
            "Hush pin",
        ],
        "gem": [
            "Breath-chip",
            "Quiet splinter",
            "Wake facet",
            "Hush-glass",
            "Drift spark",
            "Pale glint",
            "Thin crystal",
            "Exhale fleck",
        ],
        "manuscript": [
            "A Primer On Spirit",
            "Notes On Breath",
            "A Study On The Hush",
            "Notes On Drift And Wake",
            "A Primer On The Thin",
            "Study Of The Liminal",
            "Notes On Still Breath",
            "A Study On The Exhale",
            "Primer Of Pale Quiet",
            "Notes On Liminal Offices",
        ],
    },
    "illusion": {
        "orb": [
            "Mirror-bead",
            "False knot",
            "Glint pip",
            "Trick tear",
            "Doubled eye",
            "Mirage-seed",
            "Slip pearl",
            "Afterimage pip",
            "Soft lie",
            "Folded dew",
        ],
        "book": [
            "Mirror hours",
            "False ordinary",
            "Glint gloss",
            "Trick primer",
            "Doubled rubric",
            "Mirage hymnal",
            "Slip ledger",
            "Afterimage canticle",
            "Soft psalter",
            "Folded offices",
        ],
        "jar": [
            "Mirror phial",
            "False crock",
            "Glint pot",
            "Trick vial",
            "Doubled flask",
            "Mirage pot",
            "Slip bottle",
            "Afterimage crock",
        ],
        "ring": [
            "Slip ring",
            "Glint hoop",
            "Mirror band",
        ],
        "bracelet": [
            "Trick torque",
            "Mirror clasp",
        ],
        "amulet": [
            "Folded link",
            "Afterimage latch",
            "Mirage pendant",
        ],
        "necklace": [
            "False cord",
            "Glint necklace",
        ],
        "brooch": [
            "Soft brooch",
            "False pin",
            "Mirage fibula",
            "Trick pin",
        ],
        "manuscript": [
            "A Primer On Illusion",
            "Notes On The Mirror",
            "A Study On The Glint",
            "Notes On Trick And Mirage",
            "A Primer On The Fold",
            "Study Of Afterimage",
            "Notes On The Slip",
            "A Study On Doubled Sight",
            "Primer Of Soft Lie",
            "Notes On Folded Offices",
        ],
    },
}

NAMES = {
    element: {kind: [title_case(n) for n in names] for kind, names in kinds.items()}
    for element, kinds in NAMES.items()
}


def first_owners() -> dict[tuple[str, int], str]:
    owners: dict[tuple[str, int], str] = {}
    for element in ORDER:
        for kind, spans in GROUPS[element].items():
            for _span, nums in spans:
                for n in nums:
                    key = (kind, n)
                    if key in SHARED_SPANS:
                        continue
                    owners.setdefault(key, element)
    return owners


def item_id(kind: str, n: int, owners: dict[tuple[str, int], str]) -> str:
    if kind == "manuscript":
        return f"manuscript_{n}"
    if (kind, n) in SHARED_SPANS:
        return f"shared_{kind}_{n}"
    owner = owners[(kind, n)]
    return f"{owner}_{kind}_{n}"


def collect_needed(owners: dict[tuple[str, int], str]) -> dict[str, int]:
    """item_id -> texture number"""
    needed: dict[str, int] = {}
    for element in ORDER:
        for kind, spans in GROUPS[element].items():
            for _span, nums in spans:
                for n in nums:
                    needed[item_id(kind, n, owners)] = n
    for (kind, n) in SHARED_SPANS:
        needed[item_id(kind, n, owners)] = n
    for n in MANUSCRIPTS:
        needed[item_id("manuscript", n, owners)] = n
    return needed


def scheme_lines(element: str, owners: dict[tuple[str, int], str], have: set[str]) -> list[str]:
    lines: list[str] = []
    seen: set[str] = set()

    def add(kind: str, n: int, span: str) -> None:
        iid = item_id(kind, n, owners)
        if iid in seen:
            return
        if iid not in have:
            return
        seen.add(iid)
        if (kind, n) in SHARED_SPANS:
            span = SHARED_SPANS[(kind, n)]
        lines.append(f"    - {kind}(ia.tfmc_magic:{iid}) {span}")

    for kind, spans in GROUPS[element].items():
        for span, nums in spans:
            for n in nums:
                add(kind, n, span)
    for (kind, n), span in SHARED_SPANS.items():
        add(kind, n, span)
    for n in MANUSCRIPTS:
        add("manuscript", n, "all")
    return lines


def yaml_quote(name: str) -> str:
    if any(ch in name for ch in ":#{}[],&*!|>'%@`"):
        return '"' + name.replace("\\", "\\\\").replace('"', '\\"') + '"'
    return name


def main() -> None:
    owners = first_owners()
    needed = collect_needed(owners)
    tex_dir = PACK / "resourcepack" / "assets" / "tfmc_magic" / "textures" / "item" / "artifacts"
    tex_dir.mkdir(parents=True, exist_ok=True)
    (PACK / "contents").mkdir(parents=True, exist_ok=True)

    have: set[str] = set()
    missing: list[str] = []
    for iid, n in sorted(needed.items()):
        src = SRC / f"item_{n}.png"
        dest = tex_dir / f"{iid}.png"
        if not src.is_file():
            missing.append(f"{iid} (item_{n}.png)")
            continue
        dest.write_bytes(src.read_bytes())
        have.add(iid)

    for leftover in tex_dir.glob("*.png"):
        if leftover.stem not in have:
            leftover.unlink()

    if missing:
        print("Missing textures:")
        for line in missing:
            print(" ", line)

    items: list[str] = ["info:", "  namespace: tfmc_magic", "items:"]
    cat_items: list[str] = []
    for iid in sorted(have):
        items.append(f"  {iid}:")
        items.append(f'    display_name: "{iid}"')
        items.append("    resource:")
        items.append("      material: PAPER")
        items.append("      generate: true")
        items.append("      textures:")
        items.append(f"      - item/artifacts/{iid}")
        cat_items.append(f"      - tfmc_magic:{iid}")
    (PACK / "contents" / "items.yml").write_text("\n".join(items) + "\n", encoding="utf-8")

    cats = [
        "info:",
        "  namespace: tfmc_magic",
        "categories:",
        "  tfmc_magic_artifacts:",
        '    name: "TFMC Magic artifacts"',
        "    icon: PAPER",
        "    items:",
        *cat_items,
    ]
    (PACK / "contents" / "categories.yml").write_text("\n".join(cats) + "\n", encoding="utf-8")

    model: list[str] = []
    for element in ORDER:
        model.append(f"{element}:")
        model.append("  models:")
        model.extend(scheme_lines(element, owners, have))
        model.append("")
    (ART / "model-schemes.yml").write_text("\n".join(model).rstrip() + "\n", encoding="utf-8")

    naming: list[str] = []
    for element in ORDER:
        naming.append(f"{element}:")
        kinds = list(GROUPS[element].keys()) + ["manuscript"]
        for shared_kind, _n in SHARED_SPANS:
            if shared_kind not in kinds:
                kinds.append(shared_kind)
        seen_kind: set[str] = set()
        for kind in kinds:
            if kind in seen_kind:
                continue
            seen_kind.add(kind)
            pool = NAMES[element].get(kind)
            if not pool:
                continue
            naming.append(f"  {kind}:")
            for name in pool:
                naming.append(f"    - {yaml_quote(name)}")
        naming.append("")
    (ART / "naming-schemes.yml").write_text("\n".join(naming).rstrip() + "\n", encoding="utf-8")

    print(f"Wrote {len(have)} IA items, skipped {len(missing)} missing.")


if __name__ == "__main__":
    main()
