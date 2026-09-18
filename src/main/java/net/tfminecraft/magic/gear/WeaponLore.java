package net.tfminecraft.magic.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.magic.util.MagicText;

public final class WeaponLore {

    private static final String BEGIN = "\u00A70\u00A71\u00A74\u00A7r";

    private WeaponLore() {}

    public static ItemStack updateItem(ItemStack stack) {
        apply(stack);
        return stack;
    }

    public static void apply(ItemStack stack) {
        if (!GearProvenance.isGear(stack)) {
            return;
        }
        GearProvenance.applyMajority(stack, GearProvenance.resolveParts(stack));
        WeaponRequirement requirement = WeaponRequirement.fromItem(stack);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore = stripBlock(lore);
        List<String> block = new ArrayList<>();
        int majority = GearProvenance.majorityOf(stack);
        if (majority > 0) {
            block.add(MagicText.format("{color:label_muted}Tier "
                    + MajorityTierResolver.toRoman(majority)));
        }
        WeaponResonanceDisplay.appendLoreResonanceBlock(block, requirement);
        int rift = WeaponRift.get(stack);
        if (rift > 0) {
            block.add(MagicText.format("{color:corruption}Rift {color:label_muted}" + rift + "%"));
        }
        if (GearBrokenMarker.isBroken(stack)) {
            block.add("");
            block.add(MagicText.format("{color:corruption}Damaged"));
            List<String> missing = GearProvenance.missingPartIds(stack);
            if (!missing.isEmpty()) {
                block.add(MagicText.format("{color:label_muted}Missing parts: " + String.join(", ", missing)));
            }
            int held = GearBrokenMarker.orphans(stack).size();
            if (held > 0) {
                block.add(MagicText.format("{color:label_muted}Holding " + held
                        + (held == 1 ? " loose rune" : " loose runes")));
            }
            block.add(MagicText.format("{color:label_muted}Right-click an empty station to reclaim"));
        }
        block.set(0, BEGIN + block.get(0));
        lore.addAll(block);
        meta.setLore(lore);
        stack.setItemMeta(meta);
        requirement.persist(stack);
    }

    private static List<String> stripBlock(List<String> lore) {
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            if (line != null && (line.contains(BEGIN) || line.contains("\u00A70\u00A71\u00A74"))) {
                return new ArrayList<>(lore.subList(0, i));
            }
        }
        for (int i = 0; i < lore.size(); i++) {
            String p = plain(lore.get(i)).toLowerCase(Locale.ROOT);
            if ("resonance".equals(p) || "unattuned".equals(p) || p.startsWith("tier ")) {
                return new ArrayList<>(lore.subList(0, i));
            }
        }
        return lore;
    }

    private static String plain(String line) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        String stripped = ChatColor.stripColor(line);
        stripped = stripped.replaceAll("(?i)#[0-9a-f]{6}", "");
        return stripped.replaceAll("\\s+", " ").trim();
    }
}
