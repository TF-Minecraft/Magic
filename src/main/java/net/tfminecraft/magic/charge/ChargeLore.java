package net.tfminecraft.magic.charge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.util.MagicText;

/**
 * Charge lore: one band line per imprinted element, never a raw aura number.
 *
 * <p>Bands are derived from the stored fill on every render, so nothing extra is
 * persisted and a config change to the thresholds re-reads correctly.
 */
public final class ChargeLore {

    /** Color-code prefix only, so the font never draws a missing-glyph box. */
    private static final String BEGIN = "\u00A70\u00A71\u00A73\u00A7r";

    private ChargeLore() {}

    public static ItemStack updateItem(ItemStack stack) {
        apply(stack);
        return stack;
    }

    public static void apply(ItemStack stack) {
        Charge charge = Charge.fromItem(stack);
        if (charge == null) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore = stripChargeBlock(lore);
        List<String> block = new ArrayList<>();
        if (charge.isBlank()) {
            block.add(MagicText.format("{color:label_muted}Uncharged"));
            block.add(MagicText.format("{color:label_muted}Place on a shrine pedestal"));
        } else {
            block.add(MagicText.format("{color:label_muted}Charge"));
            for (ElementDef element : orderedElements(charge)) {
                String numeral = TierBands.numeralFor(element.getId(), charge.getFill(element.getId()));
                // An imprinted element below the first band still lists, so the player can
                // see which elements the charge took on before it has gathered anything.
                block.add(MagicText.format(element.getColor() + element.getName()
                        + " {color:label_muted}" + (numeral.isEmpty() ? "-" : numeral)));
            }
        }
        block.set(0, BEGIN + block.get(0));
        lore.addAll(block);
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(
                Cache.artifactGlint && charge.hasStoredAura() ? Boolean.TRUE : Boolean.FALSE);
        stack.setItemMeta(meta);
        charge.persistPdc(stack);
    }

    private static List<ElementDef> orderedElements(Charge charge) {
        String primary = charge.primaryElementId();
        List<ElementDef> listed = new ArrayList<>();
        for (ElementDef element : ElementRegistry.getAll()) {
            if (charge.getCap(element.getId()) > 0) {
                listed.add(element);
            }
        }
        listed.sort((a, b) -> {
            boolean aPrimary = a.getId().equalsIgnoreCase(primary);
            boolean bPrimary = b.getId().equalsIgnoreCase(primary);
            if (aPrimary != bPrimary) {
                return aPrimary ? -1 : 1;
            }
            int byFill = Double.compare(charge.getFill(b.getId()), charge.getFill(a.getId()));
            if (byFill != 0) {
                return byFill;
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });
        return listed;
    }

    private static List<String> stripChargeBlock(List<String> lore) {
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            if (line != null && (line.contains(BEGIN) || line.contains("\u00A70\u00A71\u00A73"))) {
                return new ArrayList<>(lore.subList(0, i));
            }
        }
        int header = indexOfHeader(lore);
        return header < 0 ? lore : new ArrayList<>(lore.subList(0, header));
    }

    private static int indexOfHeader(List<String> lore) {
        for (int i = 0; i < lore.size(); i++) {
            String p = plain(lore.get(i)).toLowerCase(Locale.ROOT);
            if ("charge".equals(p) || "uncharged".equals(p)) {
                return i;
            }
        }
        return -1;
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
