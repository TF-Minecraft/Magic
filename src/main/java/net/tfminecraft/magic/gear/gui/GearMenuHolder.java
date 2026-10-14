package net.tfminecraft.magic.gear.gui;

import net.tfminecraft.magic.gear.GearStationStore;
import net.tfminecraft.magic.gear.ownership.GearIdentity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** A rendered menu retains its initiating session even if the account opens another menu. */
public abstract class GearMenuHolder implements InventoryHolder {
    private final GearIdentity.Token token;
    protected GearMenuHolder(Player player) { token = GearStationStore.property().identity().current(player).orElse(null); }
    public boolean current(Player player) { return GearStationStore.property().identity().current(token, player); }
    public GearIdentity.Token token() { return token; }
    @Override public Inventory getInventory() { return null; }
}
