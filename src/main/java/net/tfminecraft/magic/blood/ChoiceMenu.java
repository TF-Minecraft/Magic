package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.magic.util.ItemRef;
import net.tfminecraft.magic.util.MagicText;

/** The god's question as a small menu: two answers, then a confirmation, since there is no undo. */
public final class ChoiceMenu implements InventoryHolder {

    static final int SIZE = 27;
    static final int QUESTION_SLOT = 4;
    static final int LEFT_SLOT = 11;
    static final int MIDDLE_SLOT = 13;
    static final int RIGHT_SLOT = 15;

    /** NONE while the player still picks; the picked path while they confirm. */
    private final BloodPath pending;
    private Inventory inventory;

    private ChoiceMenu(BloodPath pending) {
        this.pending = pending;
    }

    public BloodPath pending() {
        return pending;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Opens the question for the player. */
    public static void open(Player player) {
        BloodFx.play("question", player.getLocation(), player);
        show(player, BloodPath.NONE);
    }

    private static void show(Player player, BloodPath pending) {
        ChoiceMenu menu = new ChoiceMenu(pending);
        Inventory inventory = Bukkit.createInventory(menu, SIZE, MagicText.format(ChoiceConfig.title()));
        menu.inventory = inventory;
        inventory.setItem(QUESTION_SLOT, button(ChoiceConfig.Slot.QUESTION));
        if (pending == BloodPath.NONE) {
            inventory.setItem(LEFT_SLOT, button(ChoiceConfig.Slot.DEVOTEE));
            inventory.setItem(RIGHT_SLOT, button(ChoiceConfig.Slot.TRUE));
        } else {
            inventory.setItem(LEFT_SLOT, button(ChoiceConfig.Slot.CONFIRM));
            inventory.setItem(MIDDLE_SLOT, button(pending == BloodPath.DEVOTEE
                    ? ChoiceConfig.Slot.DEVOTEE : ChoiceConfig.Slot.TRUE));
            inventory.setItem(RIGHT_SLOT, button(ChoiceConfig.Slot.BACK));
        }
        player.openInventory(inventory);
    }

    @SuppressWarnings("deprecation")
    static ItemStack button(ChoiceConfig.Slot slot) {
        ChoiceConfig.Button def = ChoiceConfig.button(slot);
        ItemStack item = ItemRef.buildOrFallback(def.item(), Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(def.name().isBlank() ? " " : MagicText.format(def.name()));
        List<String> lore = new ArrayList<>();
        for (String line : def.lore()) {
            lore.add(MagicText.format(line));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Clicks inside the menu never move items; the two answer slots lead on. */
    public static void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ChoiceMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory()) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (menu.pending == BloodPath.NONE) {
            if (slot == LEFT_SLOT || slot == RIGHT_SLOT) {
                show(player, slot == LEFT_SLOT ? BloodPath.DEVOTEE : BloodPath.TRUE);
            }
            return;
        }
        if (slot == RIGHT_SLOT) {
            show(player, BloodPath.NONE);
        } else if (slot == LEFT_SLOT) {
            player.closeInventory();
            BloodChoice.answer(player, menu.pending);
        }
    }

    public static void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ChoiceMenu) {
            event.setCancelled(true);
        }
    }
}
