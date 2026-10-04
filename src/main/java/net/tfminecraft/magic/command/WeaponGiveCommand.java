package net.tfminecraft.magic.command;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.charge.TierBands;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.model.ElementVisibility;
import net.tfminecraft.magic.registry.ElementRegistry;

/** Staff spawning uses the same builder and socket finalization as station crafting. */
public final class WeaponGiveCommand {
    private WeaponGiveCommand() {}

    static boolean allowed(CommandSender sender) {
        return !Cache.givePermission.isBlank() && sender.hasPermission(Cache.givePermission);
    }

    static boolean execute(CommandSender sender, String[] args) {
        if (!allowed(sender)) {
            sender.sendMessage("§cYou do not have permission to give mage weapons.");
            return true;
        }
        if (args.length < 7 || !"give".equalsIgnoreCase(args[1])) {
            sender.sendMessage("§eUsage: /magic weapon give <player> <staff|wand|sword> <element> <aura> <part> [part...]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("§cPlayer must be online.");
            return true;
        }
        GearType type = GearType.fromId(args[3]);
        ArchetypeDef archetype = ArchetypeRegistry.get(type);
        if (archetype == null) {
            sender.sendMessage("§cUnknown weapon archetype.");
            return true;
        }
        String element = args[4].toLowerCase(Locale.ROOT);
        if (ElementRegistry.getById(element) == null || !ElementVisibility.shownOnCharge(element)) {
            sender.sendMessage("§cUnknown or disabled attunement element.");
            return true;
        }
        double aura;
        try {
            aura = Double.parseDouble(args[5]);
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cAura must be a finite positive amount reaching an attunement band.");
            return true;
        }
        if (!Double.isFinite(aura) || aura <= 0 || TierBands.bandOf(element, aura) == 0) {
            sender.sendMessage("§cAura must be a finite positive amount reaching an attunement band.");
            return true;
        }
        Map<String, PartDef> parts = new LinkedHashMap<>();
        for (int i = 6; i < args.length; i++) {
            PartDef part = PartRegistry.get(args[i]);
            if (part == null || part.isDisabled() || !part.supports(type)) {
                sender.sendMessage("§cUnknown, disabled or incompatible part: " + args[i]);
                return true;
            }
            if (parts.putIfAbsent(part.getPartType(), part) != null) {
                sender.sendMessage("§cOnly one part per category is allowed.");
                return true;
            }
        }
        List<String> required = PartSlots.open(archetype, parts.get(PartSlots.CORE));
        if (!parts.keySet().equals(new HashSet<>(required))) {
            sender.sendMessage("§cSupply exactly these part categories: " + String.join(", ", required));
            return true;
        }
        if (Arrays.stream(target.getInventory().getStorageContents()).noneMatch(slot -> slot == null || slot.getType().isAir())) {
            sender.sendMessage("§cRecipient needs an empty inventory slot.");
            return true;
        }
        ItemStack item = GearItemBuilder.prepare(type, parts.values());
        if (item == null || item.getType().isAir() || item.getType() == Material.BARRIER) {
            sender.sendMessage("§cWeapon could not be built; check its template and parts.");
            return true;
        }
        GearProvenance.stampInputs(item, Map.of());
        WeaponRequirement requirement = WeaponRequirement.fromItem(item);
        requirement.mergeAmounts(Map.of(element, aura));
        requirement.persist(item);
        item = GearItemBuilder.rewriteSockets(item, requirement.highestBand());
        target.getInventory().addItem(item);
        sender.sendMessage("§aGave completed " + type.getDisplayName() + " to " + target.getName() + ".");
        return true;
    }

    static List<String> complete(CommandSender sender, String[] args) {
        if (!allowed(sender) || args.length == 1) return List.of();
        List<String> options = new ArrayList<>();
        if (args.length == 2) options.add("give");
        else if (!"give".equalsIgnoreCase(args[1])) return List.of();
        else if (args.length == 3) Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
        else if (args.length == 4) ArchetypeRegistry.getAll().keySet().forEach(t -> options.add(t.name().toLowerCase(Locale.ROOT)));
        else if (args.length == 5) options.addAll(ElementRegistry.getAllIds());
        else if (args.length >= 7) {
            GearType type = GearType.fromId(args[3]);
            PartRegistry.matching(null, type).forEach(p -> options.add(p.getId()));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
