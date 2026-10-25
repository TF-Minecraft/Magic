package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.util.ItemRef;
import net.tfminecraft.magic.util.MagicText;

/**
 * Armour the blood path grants: unbreakable and bound to one character. Nobody else can wear it,
 * and once its owner is dead it crumbles the moment anyone handles it.
 */
public final class BloodArmour {

    static final String KIND = "armour";
    private static final EquipmentSlot[] ARMOUR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private BloodArmour() {}

    /** Builds the path's armour pieces bound to the character. Unknown paths in config are skipped. */
    public static List<ItemStack> create(BloodPath path, String characterId) {
        return create(path, characterId, List.of());
    }

    /**
     * As above, and every piece with a name is named after a victim (character ids, most important
     * first): the first named piece takes the first victim and so on down the body, wrapping round
     * to the first again when there were fewer victims than pieces.
     */
    public static List<ItemStack> create(BloodPath path, String characterId, List<String> victims) {
        List<ItemStack> out = new ArrayList<>();
        int named = 0;
        for (ChoiceConfig.Piece piece : ChoiceConfig.armourFor(path)) {
            ItemStack item = ItemRef.build(piece.item());
            if (item == null) {
                continue;
            }
            item = BloodLooks.apply(item, piece.look());
            if (piece.name() != null && !victims.isEmpty()) {
                rename(item, piece.name(), victims.get(named++ % victims.size()));
            }
            out.add(bind(item, characterId));
        }
        return out;
    }

    /**
     * The hunt's victims, most important first: the most points, then the closest rank (strangers and
     * older records without a rank last), then the earliest taken.
     */
    static List<String> victims(BloodRecord record) {
        return record.getSacrifices().stream()
                .filter(sacrifice -> BloodStage.HUNT.name().equals(sacrifice.stage()))
                .sorted(Comparator.comparingInt(BloodRecord.Sacrifice::points).reversed()
                        .thenComparingInt(sacrifice -> sacrifice.rank() > 0 ? sacrifice.rank() : Integer.MAX_VALUE)
                        .thenComparingLong(BloodRecord.Sacrifice::at))
                .map(BloodRecord.Sacrifice::character)
                .toList();
    }

    /** Names the piece after the victim; keeps the item's own name when the victim is unknown. */
    private static void rename(ItemStack item, String format, String victim) {
        String name = FinalService.nameOf(victim);
        if ("?".equals(name)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(MagicText.format(format.replace("{victim}", name.replace("\u00a7", ""))));
        item.setItemMeta(meta);
    }

    static ItemStack bind(ItemStack item, String characterId) {
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(BloodItems.kindKey(), PersistentDataType.STRING, KIND);
        meta.getPersistentDataContainer().set(BloodItems.ownerKey(), PersistentDataType.STRING, characterId);
        item.setItemMeta(meta);
        BloodLooks.protect(item);
        return item;
    }

    public static boolean is(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return KIND.equals(item.getItemMeta().getPersistentDataContainer().get(BloodItems.kindKey(),
                PersistentDataType.STRING));
    }

    /** Hands the path's armour to the player; whatever does not fit drops at their feet. */
    public static void give(Player player, BloodPath path, String characterId) {
        give(player, path, characterId, List.of());
    }

    /** As above, with the pieces named after these victims (see {@link #create(BloodPath, String, List)}). */
    public static void give(Player player, BloodPath path, String characterId, List<String> victims) {
        for (ItemStack piece : create(path, characterId, victims)) {
            WhisperService.give(player, piece);
        }
        BloodFx.play("armour_given", player.getLocation(), player);
    }

    /**
     * Takes off any blood armour the player may not wear: another character's, or a dead owner's,
     * which crumbles. Returns how many pieces came off.
     */
    public static int enforce(Player player) {
        String character = AltarService.currentCharacter(player);
        PlayerInventory inventory = player.getInventory();
        int removed = 0;
        for (EquipmentSlot slot : ARMOUR_SLOTS) {
            ItemStack worn = inventory.getItem(slot);
            if (!is(worn)) {
                continue;
            }
            String owner = BloodItems.owner(worn);
            if (AltarService.isDead(owner)) {
                inventory.setItem(slot, null);
                player.sendMessage(Messages.get("blood.armour.crumbles"));
                BloodFx.play("armour_crumbles", player.getLocation(), player);
                removed++;
            } else if (character == null || !character.equalsIgnoreCase(owner)) {
                inventory.setItem(slot, null);
                WhisperService.give(player, worn);
                player.sendMessage(Messages.get("blood.armour.refuses"));
                removed++;
            }
        }
        return removed;
    }

    /** True when the item is blood armour whose owner died; the caller removes it. */
    public static boolean crumbles(ItemStack item) {
        return is(item) && AltarService.isDead(BloodItems.owner(item));
    }
}
