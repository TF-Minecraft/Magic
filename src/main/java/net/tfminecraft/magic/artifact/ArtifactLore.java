package net.tfminecraft.magic.artifact;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.artifact.config.ArtifactRarityDef;
import net.tfminecraft.magic.artifact.config.ArtifactRarityRegistry;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeElementDef;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeImprint;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeImprintStore;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeRegistry;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.model.ElementVisibility;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.util.MagicNumbers;
import net.tfminecraft.magic.util.MagicText;

public final class ArtifactLore {

    /** Color-code prefix only. No letters or unicode, so the font never draws a missing-glyph box. */
    private static final String BEGIN = "\u00A70\u00A71\u00A72\u00A7r";
    private static final String LEGACY_BEGIN = "magic.aura.begin";
    private static final String LEGACY_END = "magic.aura.end";

    private ArtifactLore() {}

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public static void apply(ItemStack stack) {
        Artifact artifact = Artifact.fromItem(stack);
        if (artifact == null) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore = stripMagicBlocks(lore);
        List<String> block = new ArrayList<>();
        String rarityLine = rarityLine(stack);
        if (rarityLine != null) {
            block.add(rarityLine);
        }
        block.add(MagicText.format("{color:label_muted}Aura"));
        double muffle = ArtifactCareStore.readMuffle(stack);
        for (ElementDef element : orderedAuraElements(stack, artifact)) {
            double cap = artifact.getCap(element.getId());
            double fill = artifact.getFill(element.getId());
            double shown = ArtifactCareStore.usableFill(fill, cap, muffle);
            block.add(MagicText.elementName(element)
                    + MagicText.format(
                            " {color:label_muted}"
                                    + formatAmount(shown)
                                    + " / "
                                    + formatAmount(cap)));
        }
        int attuneOffset = block.size();
        List<String> attuneLines = buildAttuneLines(stack, artifact);
        block.addAll(attuneLines);
        for (SacrificeImprint imprint : SacrificeImprintStore.read(stack)) {
            String line = formatImprint(imprint);
            if (line != null && !line.isBlank()) {
                block.add(MagicText.format("{color:label_muted}" + line));
            }
        }
        if (block.isEmpty()) {
            return;
        }
        int loreStart = lore.size();
        block.set(0, BEGIN + block.get(0));
        lore.addAll(block);
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(Cache.artifactGlint ? Boolean.TRUE : Boolean.FALSE);
        stack.setItemMeta(meta);
        artifact.persistPdc(stack);
        writeAttuneIndex(stack, loreStart + attuneOffset, attuneLines.size());
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public static void refreshAttune(ItemStack stack) {
        if (!ArtifactIds.hasKey(stack)) {
            return;
        }
        Artifact artifact = Artifact.fromItem(stack);
        if (artifact == null) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.getLore() == null) {
            apply(stack);
            return;
        }
        Integer start = meta.getPersistentDataContainer().get(
                ArtifactKeys.attuneStart(), PersistentDataType.INTEGER);
        Integer count = meta.getPersistentDataContainer().get(
                ArtifactKeys.attuneCount(), PersistentDataType.INTEGER);
        if (start == null || count == null || start < 0) {
            apply(stack);
            return;
        }
        List<String> lore = new ArrayList<>(meta.getLore());
        if (start > lore.size()) {
            apply(stack);
            return;
        }
        int end = Math.min(start + count, lore.size());
        List<String> current = new ArrayList<>(lore.subList(start, end));
        List<String> attuneLines = buildAttuneLines(stack, artifact);
        if (current.equals(attuneLines) && count == attuneLines.size()) {
            return;
        }
        for (int i = end - 1; i >= start; i--) {
            lore.remove(i);
        }
        lore.addAll(start, attuneLines);
        meta.setLore(lore);
        stack.setItemMeta(meta);
        writeAttuneIndex(stack, start, attuneLines.size());
    }

    static boolean careVisibleWouldChange(ItemStack stack, double newMuffle, double dtHours) {
        double oldMuffle = ArtifactCareStore.readMuffle(stack);
        if (!muffleLine(oldMuffle).equals(muffleLine(newMuffle))) {
            return true;
        }
        Artifact artifact = Artifact.fromItem(stack);
        if (artifact == null) {
            return false;
        }
        for (ElementDef element : orderedAuraElements(stack, artifact)) {
            String id = element.getId();
            double cap = artifact.getCap(id);
            double fill = artifact.getFill(id);
            double nextFill = fillAfterDecay(id, fill, dtHours);
            String oldShown = formatAmount(ArtifactCareStore.usableFill(fill, cap, oldMuffle));
            String newShown = formatAmount(ArtifactCareStore.usableFill(nextFill, cap, newMuffle));
            if (!oldShown.equals(newShown)) {
                return true;
            }
        }
        return false;
    }

    private static String muffleLine(double muffle) {
        if (muffle <= 0.0001) {
            return "";
        }
        return MagicText.format(
                "{color:label_muted}Muffled: " + MagicNumbers.format(muffle * 100.0) + "%");
    }

    private static double fillAfterDecay(String elementId, double fill, double dtHours) {
        if (dtHours <= 0.0) {
            return fill;
        }
        ElementDef element = ElementRegistry.getById(elementId);
        if (element == null) {
            return fill;
        }
        double rate = element.getAuraDecayPerHour();
        if (rate == 0.0) {
            return fill;
        }
        if (rate < 0.0 && fill <= 0.0001) {
            return fill;
        }
        double next = fill + rate * dtHours;
        return next < 0.0 ? 0.0 : next;
    }

    /**
     * Rebuild aura from visible lore when nested PDC was dropped (pickup / furniture meta).
     */
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public static Artifact readAura(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.getLore() == null) {
            return null;
        }
        Artifact artifact = Artifact.create();
        double loreMuffle = 0.0;
        for (String line : meta.getLore()) {
            String p = plain(line);
            if (p.regionMatches(true, 0, "Muffled:", 0, 8)) {
                Double percent = parseAmount(p.substring(8).replace("%", "").trim());
                if (percent != null) {
                    loreMuffle = Math.max(0.0, Math.min(1.0, percent / 100.0));
                }
            }
        }
        for (String line : meta.getLore()) {
            String p = plain(line);
            int slash = p.indexOf('/');
            if (slash < 0) {
                continue;
            }
            String before = p.substring(0, slash).trim();
            String after = p.substring(slash + 1).trim();
            ElementDef element = matchElement(before);
            if (element == null) {
                continue;
            }
            Double shown = parseAmount(leadingNumber(before));
            Double cap = parseAmount(after);
            if (cap == null || cap <= 0) {
                continue;
            }
            double fill = shown != null ? shown : 0;
            if (loreMuffle > 0.0001 && loreMuffle < 0.9999) {
                fill = Math.min(cap, fill / (1.0 - loreMuffle));
            }
            artifact.setCap(element.getId(), cap);
            artifact.setFill(element.getId(), fill);
        }
        if (artifact.getCappedElementIds().isEmpty()) {
            return null;
        }
        return artifact;
    }

    private static List<String> buildAttuneLines(ItemStack stack, Artifact artifact) {
        return buildAttuneLines(stack, artifact, ArtifactCareStore.readMuffle(stack));
    }

    private static List<String> buildAttuneLines(ItemStack stack, Artifact artifact, double muffle) {
        List<String> lines = new ArrayList<>();
        if (muffle > 0.0001) {
            lines.add(muffleLine(muffle));
        }
        return lines;
    }

    private static void writeAttuneIndex(ItemStack stack, int start, int count) {
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        Integer oldStart = meta.getPersistentDataContainer().get(
                ArtifactKeys.attuneStart(), PersistentDataType.INTEGER);
        Integer oldCount = meta.getPersistentDataContainer().get(
                ArtifactKeys.attuneCount(), PersistentDataType.INTEGER);
        if (oldStart != null && oldCount != null && oldStart == start && oldCount == count) {
            return;
        }
        meta.getPersistentDataContainer().set(
                ArtifactKeys.attuneStart(), PersistentDataType.INTEGER, start);
        meta.getPersistentDataContainer().set(
                ArtifactKeys.attuneCount(), PersistentDataType.INTEGER, count);
        stack.setItemMeta(meta);
    }

    private static ElementDef matchElement(String before) {
        String lower = before.toLowerCase(Locale.ROOT);
        ElementDef best = null;
        int bestLen = 0;
        for (ElementDef element : ElementRegistry.getAll()) {
            String name = loreName(element);
            if (!auraLabel(lower, name)) {
                continue;
            }
            if (name.length() > bestLen) {
                best = element;
                bestLen = name.length();
            }
        }
        return best;
    }

    private static String leadingNumber(String before) {
        int i = before.length();
        while (i > 0) {
            char c = before.charAt(i - 1);
            if (Character.isDigit(c) || c == '.' || Character.isWhitespace(c)) {
                i--;
                continue;
            }
            break;
        }
        return before.substring(i).trim();
    }

    private static Double parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static ItemStack updateItem(ItemStack stack) {
        apply(stack);
        return stack;
    }

    private static String formatImprint(SacrificeImprint imprint) {
        if (imprint == null) {
            return "";
        }
        SacrificeElementDef def = SacrificeRegistry.getById(imprint.getElementId());
        if (def == null) {
            List<SacrificeElementDef> all = SacrificeRegistry.getAll();
            def = all.isEmpty() ? null : all.get(0);
        }
        return SacrificeImprintStore.formatLine(imprint, def);
    }

    private static String rarityLine(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        String rarityId = meta.getPersistentDataContainer().get(
                ArtifactKeys.artifactRarity(), PersistentDataType.STRING);
        if (rarityId == null || rarityId.isBlank()) {
            return null;
        }
        ArtifactRarityDef rarity = ArtifactRarityRegistry.getById(rarityId);
        if (rarity == null) {
            return MagicText.format("{color:label_muted}" + rarityId);
        }
        String color = rarity.getColor() != null && !rarity.getColor().isBlank()
                ? rarity.getColor()
                : "{color:label_muted}";
        return MagicText.format(color + rarity.getName());
    }

    /**
     * Drop our Aura block (and any doubled copies). Paper/MMOItems often prefix lore with
     * italic color codes, so the hidden begin marker is no longer at index 0.
     */
    private static List<String> stripMagicBlocks(List<String> lore) {
        int start = findMagicStart(lore);
        if (start < 0) {
            return lore;
        }
        return new ArrayList<>(lore.subList(0, start));
    }

    private static int findMagicStart(List<String> lore) {
        int start = -1;
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            int candidate = -1;
            if (hasHiddenMarker(line)) {
                candidate = i;
            } else if (isAuraHeader(line)) {
                candidate = i;
                if (i > 0 && isRarityOnlyLine(lore.get(i - 1))) {
                    candidate = i - 1;
                }
            } else if (isFillLine(line)) {
                candidate = i;
                if (i > 0 && isAuraHeader(lore.get(i - 1))) {
                    candidate = i - 1;
                }
                if (candidate > 0 && isRarityOnlyLine(lore.get(candidate - 1))) {
                    candidate--;
                }
            }
            if (candidate >= 0 && (start < 0 || candidate < start)) {
                start = candidate;
            }
        }
        return start;
    }

    private static boolean hasHiddenMarker(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }
        if (line.contains(BEGIN) || line.contains("\u00A70\u00A71\u00A72")) {
            return true;
        }
        if (line.contains(LEGACY_BEGIN) || line.contains(LEGACY_END)) {
            return true;
        }
        return line.indexOf('\u2063') >= 0 || line.indexOf('\u200b') >= 0;
    }

    private static boolean isAuraHeader(String line) {
        return "aura".equals(plain(line).toLowerCase(Locale.ROOT));
    }

    private static boolean isRarityOnlyLine(String line) {
        String p = plain(line).toLowerCase(Locale.ROOT);
        if (p.isEmpty()) {
            return false;
        }
        for (ArtifactRarityDef rarity : ArtifactRarityRegistry.getAll()) {
            if (rarity != null && p.equals(rarity.getName().toLowerCase(Locale.ROOT))) {
                return true;
            }
            if (rarity != null && p.equals(rarity.getId().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFillLine(String line) {
        String p = plain(line);
        int slash = p.indexOf('/');
        if (slash < 0) {
            return false;
        }
        String before = p.substring(0, slash).trim().toLowerCase(Locale.ROOT);
        for (ElementDef element : ElementRegistry.getAll()) {
            String name = loreName(element);
            if (auraLabel(before, name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when {@code before} is the element label followed only by its amount.
     * A short id such as {@code fire} must not match {@code fire resistance 5}.
     */
    private static boolean auraLabel(String before, String name) {
        if (before == null || name == null || name.isEmpty() || !before.startsWith(name)) {
            return false;
        }
        String rest = before.substring(name.length()).trim();
        if (rest.isEmpty()) {
            return false;
        }
        boolean digit = false;
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (Character.isDigit(c)) {
                digit = true;
                continue;
            }
            if (c == '.' && digit) {
                continue;
            }
            return false;
        }
        return digit;
    }

    /** Same label {@link MagicText#elementName} writes, so a blank name still matches its id. */
    private static String loreName(ElementDef element) {
        String name = plain(element.getName()).toLowerCase(Locale.ROOT);
        if (!name.isEmpty()) {
            return name;
        }
        return element.getId() == null ? "" : element.getId().toLowerCase(Locale.ROOT);
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private static String plain(String line) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        String stripped = ChatColor.stripColor(line);
        stripped = stripped.replaceAll("(?i)#[0-9a-f]{6}", "");
        return stripped.replaceAll("\\s+", " ").trim();
    }

    private static String formatAmount(double value) {
        double rounded = MagicNumbers.round(value, 2);
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001) {
            return String.valueOf((long) Math.rint(rounded));
        }
        return MagicNumbers.format(rounded);
    }

    private static List<ElementDef> orderedAuraElements(ItemStack stack, Artifact artifact) {
        String primary = primaryId(stack, artifact);
        List<ElementDef> listed = new ArrayList<>();
        for (ElementDef element : ElementRegistry.getAll()) {
            if (artifact.getCap(element.getId()) > 0
                    && ElementVisibility.shownOnArtifact(element.getId(), primary)) {
                listed.add(element);
            }
        }
        listed.sort((a, b) -> {
            boolean aPrimary = a.getId().equalsIgnoreCase(primary);
            boolean bPrimary = b.getId().equalsIgnoreCase(primary);
            if (aPrimary != bPrimary) {
                return aPrimary ? -1 : 1;
            }
            int byFill = Double.compare(artifact.getFill(b.getId()), artifact.getFill(a.getId()));
            if (byFill != 0) {
                return byFill;
            }
            int byCap = Double.compare(artifact.getCap(b.getId()), artifact.getCap(a.getId()));
            if (byCap != 0) {
                return byCap;
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });
        return listed;
    }

    private static String primaryId(ItemStack stack, Artifact artifact) {
        if (stack != null && stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                String stored = meta.getPersistentDataContainer().get(
                        ArtifactKeys.artifactPrimary(), PersistentDataType.STRING);
                if (stored != null && !stored.isBlank()) {
                    return stored.trim().toLowerCase(Locale.ROOT);
                }
            }
        }
        if (artifact == null) {
            return "";
        }
        String best = "";
        double bestCap = -1;
        for (String id : artifact.getCappedElementIds()) {
            double cap = artifact.getCap(id);
            if (cap > bestCap) {
                bestCap = cap;
                best = id;
            }
        }
        return best;
    }
}
