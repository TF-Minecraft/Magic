package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The chamber every altar lays around itself (placement.room.template), the same for everyone:
 * the floor, walls and ceiling in these blocks (an entry repeated weighs more) and hidden light
 * in the four top corners. An empty list leaves that part of the room as it was.
 */
public final class RoomTemplate {

    private static List<Material> floor = List.of();
    private static List<Material> walls = List.of();
    private static List<Material> ceiling = List.of();
    private static int light = 0;

    private RoomTemplate() {}

    static void reset() {
        floor = List.of();
        walls = List.of();
        ceiling = List.of();
        light = 0;
    }

    static void load(ConfigurationSection section, Logger log) {
        reset();
        if (section == null) {
            return;
        }
        floor = solids(section.getStringList("floor"), "floor", log);
        walls = solids(section.getStringList("walls"), "walls", log);
        ceiling = solids(section.getStringList("ceiling"), "ceiling", log);
        light = Math.max(0, Math.min(15, section.getInt("light", 0)));
    }

    private static List<Material> solids(List<String> raw, String part, Logger log) {
        List<Material> out = new ArrayList<>();
        for (String name : raw) {
            Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (material == null || !material.isBlock() || !material.isSolid()) {
                log.warning("[Magic] blood magic config placement.room.template." + part + ": '" + name
                        + "' is not a solid block");
                continue;
            }
            out.add(material);
        }
        return List.copyOf(out);
    }

    static boolean hasFloor() {
        return !floor.isEmpty();
    }

    /** A floor block, or null when the template has no floor. */
    static Material floor(Random random) {
        return pick(floor, random);
    }

    static Material wall(Random random) {
        return pick(walls, random);
    }

    static Material ceiling(Random random) {
        return pick(ceiling, random);
    }

    /** Light level of the hidden lights in the top corners; 0 for none. */
    static int light() {
        return light;
    }

    private static Material pick(List<Material> from, Random random) {
        return from.isEmpty() ? null : from.get(random.nextInt(from.size()));
    }
}
