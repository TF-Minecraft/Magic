package net.tfminecraft.magic.listener;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;

import net.Indyuce.mmoitems.api.event.item.UntargetedWeaponUseEvent;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.gear.GearProvenance;
import net.tfminecraft.magic.gear.WeaponWear;

/**
 * Keeps mage weapon wear to spells and wand shots.
 *
 * <p>An ArmourShop skin tags the weapon as an ItemsAdder item, and ItemsAdder then wears it
 * on every melee hit through a {@link PlayerItemDamageEvent}. Vanilla never damages a stick
 * or paper base, so on those that event is cancelled. A sword base keeps its vanilla wear,
 * but wear that would reach the last point is cancelled outright: ItemsAdder only checks
 * whether its event was cancelled and then applies its own amount, so lowering the amount
 * would not protect the weapon.
 *
 * <p>MMOItems wears a wand by one per shot and deletes it when the durability runs out, so
 * a worn-out wand refuses the shot instead.
 */
public final class WeaponWearListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (!GearProvenance.isGear(item)) {
            return;
        }
        if (item.getType().getMaxDurability() <= 0 || event.getDamage() >= WeaponWear.remaining(item)) {
            event.setCancelled(true);
        }
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWeaponShot(UntargetedWeaponUseEvent event) {
        ItemStack item = event.getWeapon().getItem();
        if (!GearProvenance.isGear(item) || !WeaponWear.isWorn(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        player.sendTitle(Messages.get("cast.worn_title"), Messages.get("cast.worn_sub"), 0, 25, 10);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.7f, 0.6f);
    }
}
