package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;

/**
 * The path's artefacts (artefacts.yml): the devotee's, handed out at the kneel, and the true path's,
 * handed out when the hunt is done, with the ones its final sacrifices put in their place.
 */
public final class ArtefactConfig {

    /** One artefact: the item (a TLibs path), its name ({victim} for a final's) and lore. */
    public record Piece(String item, String name, List<String> lore) {}

    private static final List<Piece> DEVOTEE = new ArrayList<>();
    private static final List<Piece> HUNT = new ArrayList<>();
    private static final List<Piece> FINALS = new ArrayList<>();

    private ArtefactConfig() {}

    static void reset() {
        DEVOTEE.clear();
        HUNT.clear();
        FINALS.clear();
    }

    static void load(ConfigurationSection artefacts, Logger log) {
        reset();
        if (artefacts == null) {
            return;
        }
        read(artefacts.getMapList("devotee"), artefacts.getStringList("devotee_lore"), DEVOTEE, "devotee", log);
        read(artefacts.getMapList("true.hunt"), artefacts.getStringList("true.lore"), HUNT, "true.hunt", log);
        read(artefacts.getMapList("true.finals"), artefacts.getStringList("true.lore"), FINALS, "true.finals", log);
    }

    private static void read(List<Map<?, ?>> entries, List<String> sharedLore, List<Piece> out, String path,
            Logger log) {
        for (Map<?, ?> entry : entries) {
            if (!(entry.get("item") instanceof String item) || item.isBlank()) {
                log.warning("[Magic] blood magic config artefacts." + path + " entry needs an item");
                continue;
            }
            String name = entry.get("name") instanceof String raw ? raw : "";
            List<String> lore = new ArrayList<>();
            if (entry.get("lore") instanceof List<?> lines) {
                for (Object line : lines) {
                    lore.add(String.valueOf(line));
                }
            } else {
                lore.addAll(sharedLore);
            }
            out.add(new Piece(item.trim(), name, List.copyOf(lore)));
        }
    }

    /** The artefacts the devotee path hands out at the kneel. */
    public static List<Piece> devotee() {
        return List.copyOf(DEVOTEE);
    }

    /** The artefacts the true path hands out when the hunt is done. */
    public static List<Piece> hunt() {
        return List.copyOf(HUNT);
    }

    /** The true path's final sacrifices' artefacts: the Nth takes the place of the Nth hunt artefact. */
    public static List<Piece> finals() {
        return List.copyOf(FINALS);
    }
}
