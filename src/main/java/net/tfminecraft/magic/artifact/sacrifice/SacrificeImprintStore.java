package net.tfminecraft.magic.artifact.sacrifice;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.magic.artifact.ArtifactKeys;

public final class SacrificeImprintStore {

    private SacrificeImprintStore() {}

    public static List<SacrificeImprint> read(ItemStack stack) {
        List<SacrificeImprint> out = new ArrayList<>();
        if (stack == null || !stack.hasItemMeta()) {
            return out;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return out;
        }
        String raw = meta.getPersistentDataContainer().get(
                ArtifactKeys.sacrificeImprints(), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.split(";")) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String[] bits = part.split("\\|", 4);
            if (bits.length < 3 || bits[0].isBlank()) {
                continue;
            }
            String elementId = bits.length > 3 ? bits[3] : "";
            out.add(new SacrificeImprint(bits[0], bits[1], bits[2], elementId));
        }
        return out;
    }

    public static void write(ItemStack stack, List<SacrificeImprint> imprints) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        if (imprints == null || imprints.isEmpty()) {
            meta.getPersistentDataContainer().remove(ArtifactKeys.sacrificeImprints());
            stack.setItemMeta(meta);
            return;
        }
        StringBuilder blob = new StringBuilder();
        for (SacrificeImprint imprint : imprints) {
            if (imprint == null || imprint.getCharacterId().isBlank()) {
                continue;
            }
            if (blob.length() > 0) {
                blob.append(';');
            }
            blob.append(sanitize(imprint.getCharacterId()))
                    .append('|')
                    .append(sanitize(imprint.getCharacterName()))
                    .append('|')
                    .append(imprint.getStage())
                    .append('|')
                    .append(sanitize(imprint.getElementId()));
        }
        if (blob.length() == 0) {
            meta.getPersistentDataContainer().remove(ArtifactKeys.sacrificeImprints());
        } else {
            meta.getPersistentDataContainer().set(
                    ArtifactKeys.sacrificeImprints(), PersistentDataType.STRING, blob.toString());
        }
        stack.setItemMeta(meta);
    }

    public static String formatLine(SacrificeImprint imprint, SacrificeElementDef element) {
        if (imprint == null || element == null) {
            return "";
        }
        String template = switch (imprint.getStage()) {
            case SacrificeImprint.SOUL -> element.getLoreSoul();
            case SacrificeImprint.SCREAMS -> element.getLoreScreams();
            default -> element.getLorePain();
        };
        if (template == null || template.isBlank()) {
            return "";
        }
        return template.replace("{character}", imprint.getCharacterName() != null ? imprint.getCharacterName() : "");
    }

    private static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('|', ' ').replace(';', ' ').trim();
    }
}
