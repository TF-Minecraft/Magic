package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.util.ItemRef;

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
        List<ItemStack> out = new ArrayList<>();
        for (ChoiceConfig.Piece piece : ChoiceConfig.armourFor(path)) {
            ItemStack item = ItemRef.build(piece.item());
            if (item != null) {
                out.add(bind(BloodLooks.apply(item, piece.look()), characterId));
            }
        }
        return out;
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
        for (ItemStack piece : create(path, characterId)) {
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
