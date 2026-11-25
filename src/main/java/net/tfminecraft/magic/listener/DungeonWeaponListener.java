package net.tfminecraft.magic.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.Indyuce.mmoitems.api.event.MMOItemReforgeEvent;
import net.Indyuce.mmoitems.api.event.item.UnsocketGemStoneEvent;
import net.Indyuce.mmoitems.api.item.mmoitem.ReadMMOItem;
import net.tfminecraft.magic.gear.GearProvenance;

/** Dungeon weapons keep their runes, and MMOItems never rebuilds one from its template. */
public final class DungeonWeaponListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUnsocket(UnsocketGemStoneEvent event) {
        if (event.getTargetItem() instanceof ReadMMOItem read
                && GearProvenance.isDungeonWeapon(read.getNBT().getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onReforge(MMOItemReforgeEvent event) {
        if (GearProvenance.isDungeonWeapon(event.getReforger().getStack())) {
            event.setCancelled(true);
        }
    }
}
