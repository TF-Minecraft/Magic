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
 * The path's artefacts: bound to one character and set out on its altar's pedestals when the path's
 * class is given (and, on the true path, one more in place of an older one at every final). Each
 * holds a charge, filled in the altar's pool of blood (see {@link BloodCharging}) and drawn into blood
 * resonance by meditating among them (see {@link BloodMeditation}); a charged one glows. Each holds a
 * place; its id names the place and what fills it (devotee:3, true:hunt:3, true:final:3), so one whose
 * place was filled again since is of no more use. They are not Magic artifacts: no aura, no wear, no
 * use in mage gear.
 */
public final class BloodArtefacts {

    static final String KIND = "artefact";

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

    /** The most charge one artefact holds: an equal share of what the path's artefacts are worth. */
    static double maxCharge(BloodRecord record) {
        int places = ids(record).size();
        int reached = record.getStage() == BloodStage.ASCENDED ? Integer.MAX_VALUE : record.getFinalsDone();
        return places == 0 ? 0 : PoolConfig.worth(record.getPath(), reached) / places;
    }

    /** How many of the character's current artefacts hold any charge. */
    static int charged(BloodRecord record) {
        int count = 0;
        for (String id : ids(record)) {
            if (record.getCharge(id) > 0) {
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
        return styled(record, item);
    }

    /** The artefact glowing while its place holds charge, plain once drained. */
    static ItemStack styled(BloodRecord record, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(record.getCharge(id(item)) > 0);
        item.setItemMeta(meta);
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
     * free or loaded. One that takes another's place starts without charge, and the old one's charge
     * goes. Returns how many were handed out.
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
        record.keepCharges(wanted);
        record.getPending().retainAll(wanted);
        BloodRecords.save(record);
        if (given > 0) {
            WhisperService.tell(player, given > 1 ? "blood.artefact.given" : "blood.artefact.replaced");
        }
        Magic.plugin.getLogger().info("[Magic] Character " + character + " was given " + given + " blood artefact(s)");
        return given;
    }

    /**
     * Runs {@link #sync} and {@link #deliverPending} on the next tick, if the player is still online
     * then.
     */
    public static void syncSoon(Player player) {
        Bukkit.getScheduler().runTask(Magic.plugin, () -> {
            if (player.isOnline()) {
                sync(player);
                deliverPending(player);
            }
        });
    }

    /**
     * Gives back the artefacts that finished charging while their owner was away (or the server
     * stopped), and returns how many.
     */
    public static int deliverPending(Player player) {
        BloodRecord record = BloodRecords.get(AltarService.currentCharacter(player));
        if (record == null || record.getPending().isEmpty()) {
            return 0;
        }
        int given = 0;
        for (String id : List.copyOf(record.getPending())) {
            if (BloodCharging.isCharging(record.getCharacterId(), id)) {
                continue;
            }
            record.getPending().remove(id);
            if (ids(record).contains(id)) {
                WhisperService.give(player, create(record, id));
                given++;
            }
        }
        BloodRecords.save(record);
        if (given > 0) {
            WhisperService.tell(player, "blood.artefact.returned");
        }
        return given;
    }

    /** Staff: gives the player a fresh copy of each of their current artefacts. */
    public static int regive(Player player) {
        BloodRecord record = BloodRecords.get(AltarService.currentCharacter(player));
        List<String> ids = ids(record);
        for (String id : ids) {
            WhisperService.give(player, create(record, id));
        }
        return ids.size();
    }
}
