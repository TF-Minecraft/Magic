package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.util.ItemRef;
import net.tfminecraft.magic.util.MagicText;

/**
 * The path's artefacts: bound to one character, set out on its altar's pedestals when the path's
 * class is given (and, on the true path, one more in place of an older one at every final), and only
 * of use once sunk into the altar's pool of blood. Each holds a place; its id names the place and what
 * fills it (devotee:3, true:hunt:3, true:final:3), so an artefact whose place was filled again since
 * dissolves instead of sinking. They are not Magic artifacts: no aura, no wear, no use in mage gear.
 */
public final class BloodArtefacts {

    static final String KIND = "artefact";

    /** What sinking an artefact into the pool came to. */
    public enum Sink {
        SUNK,
        ALL_SUNK,
        NOT_YOURS,
        STALE,
        ALREADY
    }

    private BloodArtefacts() {}

    static NamespacedKey idKey() {
        return new NamespacedKey(Magic.plugin, "blood_artefact");
    }

    /**
     * The artefacts the character holds a place for now, one id per place: none before the path's class
     * is given. On the true path the Nth final sacrifice fills the Nth place anew.
     */
    static List<String> ids(BloodRecord record) {
        List<String> out = new ArrayList<>();
        if (record == null || !record.getStage().atLeast(BloodStage.FINAL)) {
            return out;
        }
        if (record.getPath() == BloodPath.DEVOTEE) {
            for (int i = 0; i < ArtefactConfig.devotee().size(); i++) {
                out.add("devotee:" + i);
            }
        } else if (record.getPath() == BloodPath.TRUE) {
            int finals = Math.min(record.getFinalsDone(), ArtefactConfig.finals().size());
            for (int i = 0; i < ArtefactConfig.hunt().size(); i++) {
                out.add(i < finals ? "true:final:" + i : "true:hunt:" + i);
            }
        }
        return out;
    }

    /** The config piece an id stands for, or null. */
    static ArtefactConfig.Piece piece(String id) {
        String[] parts = id == null ? new String[0] : id.split(":");
        List<ArtefactConfig.Piece> pieces;
        if (parts.length == 2 && parts[0].equals("devotee")) {
            pieces = ArtefactConfig.devotee();
        } else if (parts.length == 3 && parts[0].equals("true") && parts[1].equals("hunt")) {
            pieces = ArtefactConfig.hunt();
        } else if (parts.length == 3 && parts[0].equals("true") && parts[1].equals("final")) {
            pieces = ArtefactConfig.finals();
        } else {
            return null;
        }
        try {
            int index = Integer.parseInt(parts[parts.length - 1]);
            return index >= 0 && index < pieces.size() ? pieces.get(index) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** How many of the character's artefacts are in the pool now. */
    static int sunk(BloodRecord record) {
        int count = 0;
        for (String id : ids(record)) {
            if (record.getSunk().contains(id)) {
                count++;
            }
        }
        return count;
    }

    /** A new artefact for this place, bound to the record's character; null for an unknown id. */
    @SuppressWarnings("deprecation")
    static ItemStack create(BloodRecord record, String id) {
        ArtefactConfig.Piece piece = piece(id);
        if (piece == null) {
            return null;
        }
        ItemStack item = ItemRef.buildOrFallback(piece.item(), Material.FERMENTED_SPIDER_EYE);
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        String name = piece.name();
        if (name.contains("{victim}")) {
            String victim = victim(record, Integer.parseInt(id.substring(id.lastIndexOf(':') + 1)));
            name = name.replace("{victim}", victim.replace("§", ""));
        }
        if (!name.isBlank()) {
            meta.setDisplayName(MagicText.format(name));
        }
        if (!piece.lore().isEmpty()) {
            List<String> lore = new ArrayList<>();
            for (String line : piece.lore()) {
                lore.add(MagicText.format(line));
            }
            meta.setLore(lore);
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(BloodItems.kindKey(), PersistentDataType.STRING, KIND);
        pdc.set(BloodItems.ownerKey(), PersistentDataType.STRING, record.getCharacterId());
        pdc.set(idKey(), PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        BloodLooks.protect(item);
        return item;
    }

    /** The name of the Nth final sacrifice's victim; "someone" when the final was skipped by staff. */
    static String victim(BloodRecord record, int index) {
        int seen = 0;
        for (BloodRecord.Sacrifice sacrifice : record.getSacrifices()) {
            if (BloodStage.FINAL.name().equals(sacrifice.stage()) && seen++ == index) {
                String name = FinalService.nameOf(sacrifice.character());
                return "?".equals(name) ? "someone" : name;
            }
        }
        return "someone";
    }

    public static boolean is(ItemStack item) {
        return KIND.equals(BloodItems.read(item, BloodItems.kindKey()));
    }

    /** The artefact's id, or null for any other item. */
    static String id(ItemStack item) {
        return is(item) ? BloodItems.read(item, idKey()) : null;
    }

    /**
     * Hands out every artefact the character holds a place for but was not given yet: onto the altar's
     * pedestals (in place of the one it follows, else on a free one), or into the inventory when none is
     * free or loaded. One that follows an artefact in the pool takes that one's place there: the old one
     * stops counting. Returns how many were handed out.
     */
    public static int sync(Player player) {
        String character = AltarService.currentCharacter(player);
        BloodRecord record = BloodRecords.get(character);
        List<String> wanted = ids(record);
        if (wanted.isEmpty() || wanted.equals(record.getArtefacts())) {
            return 0;
        }
        BloodAltar altar = BloodAltars.ofCharacter(character);
        List<String> before = List.copyOf(record.getArtefacts());
        int given = 0;
        for (int i = 0; i < wanted.size(); i++) {
            String id = wanted.get(i);
            String old = i < before.size() ? before.get(i) : null;
            if (id.equals(old)) {
                continue;
            }
            ItemStack item = create(record, id);
            if (altar == null || !AltarPedestals.offer(altar, item, old)) {
                WhisperService.give(player, item);
            }
            given++;
        }
        record.getArtefacts().clear();
        record.getArtefacts().addAll(wanted);
        record.getSunk().retainAll(wanted);
        BloodRecords.save(record);
        if (given > 0) {
            WhisperService.tell(player, given > 1 ? "blood.artefact.given" : "blood.artefact.replaced");
        }
        Magic.plugin.getLogger().info("[Magic] Character " + character + " was given " + given + " blood artefact(s)");
        return given;
    }

    /** Runs {@link #sync} on the next tick, if the player is still online then. */
    public static void syncSoon(Player player) {
        Bukkit.getScheduler().runTask(Magic.plugin, () -> {
            if (player.isOnline()) {
                sync(player);
            }
        });
    }

    /**
     * The player sinks the held artefact into their own altar's pool. Anything but NOT_YOURS uses the
     * item up: an artefact whose place was filled anew, or a copy of one already in the pool, crumbles.
     */
    public static Sink sink(Player player, BloodAltar altar, ItemStack held) {
        String character = AltarService.currentCharacter(player);
        BloodRecord record = BloodRecords.get(character);
        if (record == null || !character.equalsIgnoreCase(altar.getOwnerCharacter())
                || !character.equalsIgnoreCase(BloodItems.owner(held))) {
            return Sink.NOT_YOURS;
        }
        String id = id(held);
        held.setAmount(held.getAmount() - 1);
        if (!ids(record).contains(id)) {
            return Sink.STALE;
        }
        if (record.getSunk().contains(id)) {
            return Sink.ALREADY;
        }
        record.getSunk().add(id);
        BloodRecords.save(record);
        return sunk(record) >= ids(record).size() ? Sink.ALL_SUNK : Sink.SUNK;
    }

    /** Staff: gives the player every one of their current artefacts that is not in the pool. */
    public static int regive(Player player) {
        BloodRecord record = BloodRecords.get(AltarService.currentCharacter(player));
        int given = 0;
        for (String id : ids(record)) {
            if (!record.getSunk().contains(id)) {
                WhisperService.give(player, create(record, id));
                given++;
            }
        }
        return given;
    }
}
