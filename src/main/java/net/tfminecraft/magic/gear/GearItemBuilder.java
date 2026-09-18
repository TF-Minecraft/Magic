package net.tfminecraft.magic.gear;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.data.GemSocketsData;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.util.CostFormatter;
import net.tfminecraft.magic.util.ItemRef;

public final class GearItemBuilder {

    private GearItemBuilder() {}

    public static boolean mmoItemsPresent() {
        return Bukkit.getPluginManager().isPluginEnabled("MMOItems")
                && Bukkit.getPluginManager().isPluginEnabled("MythicLib");
    }

    public static ItemStack preview(GearType type, Collection<PartDef> parts) {
        return build(type, parts, true, 0);
    }

    public static ItemStack prepare(GearType type, Collection<PartDef> parts) {
        return build(type, parts, false, 0);
    }

    public static ItemStack rewriteSockets(ItemStack stack, int band) {
        if (stack == null) {
            return null;
        }
        GearType type = GearProvenance.archetypeOf(stack);
        ArchetypeDef archetype = ArchetypeRegistry.get(type);
        if (archetype == null || GearProvenance.socketsLocked(stack)) {
            return stack;
        }
        List<PartDef> parts = GearProvenance.resolveParts(stack);
        List<String> colours = SocketLayout.colours(archetype, parts, band);
        ItemStack rewritten = applyMmoData(stack, colours, parts);
        if (rewritten == null) {
            return stack;
        }
        rewritten = GearModelResolver.apply(rewritten, type, parts);
        copyGearPdc(stack, rewritten);
        GearProvenance.lockSockets(rewritten);
        WeaponRequirement.fromItem(stack).persist(rewritten);
        WeaponRift.copy(stack, rewritten);
        return WeaponLore.updateItem(rewritten);
    }

    private static ItemStack build(GearType type, Collection<PartDef> parts, boolean gui, int band) {
        ArchetypeDef archetype = ArchetypeRegistry.get(type);
        ItemStack barrier = barrier("Cannot craft");
        if (archetype == null) {
            return barrier("Unknown archetype");
        }
        if (!requiredPresent(archetype, parts)) {
            return barrier("Missing parts");
        }
        ItemStack base = ItemRef.build(archetype.getTemplate());
        if (base == null || base.getType().isAir()) {
            if (gui) {
                return barrier("Missing template: " + archetype.getTemplate());
            }
            Magic.plugin.getLogger().warning("[Magic] Missing gear template: " + archetype.getTemplate());
            return barrier;
        }
        if (gui) {
            return decoratePreview(base, type, parts);
        }
        List<String> colours = SocketLayout.colours(archetype, parts, band);
        ItemStack withMmo = applyMmoData(base, colours, parts);
        if (withMmo == null) {
            withMmo = base;
        }
        withMmo = GearModelResolver.apply(withMmo, type, parts);
        GearProvenance.stamp(withMmo, type, parts);
        return WeaponLore.updateItem(withMmo);
    }

    private static boolean requiredPresent(ArchetypeDef archetype, Collection<PartDef> parts) {
        if (archetype.getRequired().isEmpty()) {
            return parts != null && !parts.isEmpty();
        }
        PartDef core = null;
        if (parts != null) {
            for (PartDef part : parts) {
                if (part != null && PartSlots.CORE.equalsIgnoreCase(part.getPartType())) {
                    core = part;
                    break;
                }
            }
        }
        for (String required : PartSlots.open(archetype, core)) {
            boolean found = false;
            if (parts != null) {
                for (PartDef part : parts) {
                    if (part != null && part.getPartType().equalsIgnoreCase(required)) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static ItemStack applyMmoData(ItemStack stack, List<String> colours, Collection<PartDef> parts) {
        if (stack == null || !mmoItemsPresent()) {
            return stack;
        }
        try {
            LiveMMOItem mmo = new LiveMMOItem(NBTItem.get(stack));
            if (colours != null && !colours.isEmpty()) {
                mmo.setData(ItemStats.GEM_SOCKETS, new GemSocketsData(new ArrayList<>(colours)));
            }
            GearStatApplicator.apply(mmo, parts);
            ItemStack built = mmo.newBuilder().build();
            return built == null || built.getType().isAir() ? stack : built;
        } catch (Exception ex) {
            Magic.plugin.getLogger().warning("[Magic] Failed to write gear MMO data: " + ex.getMessage());
            return stack;
        }
    }

    private static void copyGearPdc(ItemStack from, ItemStack to) {
        if (from == null || to == null || !from.hasItemMeta() || !to.hasItemMeta()) {
            return;
        }
        GearType type = GearProvenance.archetypeOf(from);
        if (type == null) {
            return;
        }
        ItemMeta fromMeta = from.getItemMeta();
        String parts = fromMeta.getPersistentDataContainer().get(
                GearKeys.parts(), org.bukkit.persistence.PersistentDataType.STRING);
        Integer archetypeRevision = fromMeta.getPersistentDataContainer().get(
                GearKeys.archetypeRevision(), org.bukkit.persistence.PersistentDataType.INTEGER);
        ItemMeta toMeta = to.getItemMeta();
        if (parts != null) {
            toMeta.getPersistentDataContainer().set(
                    GearKeys.parts(), org.bukkit.persistence.PersistentDataType.STRING, parts);
        }
        toMeta.getPersistentDataContainer().set(
                GearKeys.archetype(),
                org.bukkit.persistence.PersistentDataType.STRING,
                type.name().toLowerCase());
        if (archetypeRevision != null) {
            toMeta.getPersistentDataContainer().set(
                    GearKeys.archetypeRevision(),
                    org.bukkit.persistence.PersistentDataType.INTEGER,
                    archetypeRevision);
        }
        Integer majority = fromMeta.getPersistentDataContainer().get(
                GearKeys.majorityTier(), org.bukkit.persistence.PersistentDataType.INTEGER);
        if (majority != null) {
            toMeta.getPersistentDataContainer().set(
                    GearKeys.majorityTier(),
                    org.bukkit.persistence.PersistentDataType.INTEGER,
                    majority);
        }
        to.setItemMeta(toMeta);
    }

    private static ItemStack decoratePreview(
            ItemStack stack, GearType type, Collection<PartDef> parts) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        int majority = MajorityTierResolver.resolve(parts);
        if (majority > 0) {
            lore.add("§eTier " + MajorityTierResolver.toRoman(majority));
        }
        lore.add("§6Sockets");
        lore.addAll(SocketLayout.previewLines(ArchetypeRegistry.get(type), parts));
        CostFormatter.appendInput(lore, GearCosts.total(parts));
        lore.add("");
        lore.add("§eClick to prepare on the station");
        if (!mmoItemsPresent()) {
            lore.add("§cMMOItems is not loaded");
        }
        meta.setLore(lore);
        if (meta.getDisplayName() == null || meta.getDisplayName().isBlank()) {
            meta.setDisplayName("§6" + type.getDisplayName());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack barrier(String reason) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c" + reason);
            item.setItemMeta(meta);
        }
        return item;
    }
}
