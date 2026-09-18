package net.tfminecraft.magic.gear;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SocketLayout {

    public static final int MAX_SOCKETS = 4;

    private static final Map<String, String> DEFAULT_LABELS = Map.of(
            "projectile", "Projectile",
            "support", "Support",
            "minor_spell", "Minor Spell",
            "major_spell", "Major Spell",
            "spell", "Spell");

    private static final Map<String, String> LABELS = new LinkedHashMap<>(DEFAULT_LABELS);

    private SocketLayout() {}

    public static void clearLabels() {
        LABELS.clear();
        LABELS.putAll(DEFAULT_LABELS);
    }

    public static void putLabel(String slotId, String display) {
        if (slotId == null || slotId.isBlank() || display == null || display.isBlank()) {
            return;
        }
        LABELS.put(slotId.trim().toLowerCase(Locale.ROOT), display.trim());
    }

    public static String label(String slotId) {
        if (slotId == null || slotId.isBlank()) {
            return "";
        }
        String key = slotId.trim().toLowerCase(Locale.ROOT);
        String mapped = LABELS.get(key);
        if (mapped != null && !mapped.isBlank()) {
            return mapped;
        }
        return prettyId(key);
    }

    public static Map<String, String> labels() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(LABELS));
    }

    public static String prettyId(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        String[] parts = id.trim().replace('-', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return out.toString();
    }

    public static List<String> colours(ArchetypeDef archetype, Collection<PartDef> parts, int band) {
        List<String> colours = new ArrayList<>();
        if (archetype == null) {
            return colours;
        }
        for (String slotId : archetype.slotIds()) {
            int count = 0;
            if (parts != null) {
                for (PartDef part : parts) {
                    if (part != null) {
                        count += part.socketCount(slotId);
                    }
                }
            }
            String suffix = archetype.slotSuffix(slotId);
            if (suffix.isEmpty() || count <= 0) {
                continue;
            }
            String colour = SocketColourRegistry.colour(band, suffix);
            for (int i = 0; i < count; i++) {
                if (colours.size() >= MAX_SOCKETS) {
                    return colours;
                }
                colours.add(colour);
            }
        }
        return colours;
    }

    public static int rawTotal(ArchetypeDef archetype, Collection<PartDef> parts) {
        if (archetype == null || parts == null) {
            return 0;
        }
        int total = 0;
        for (String slotId : archetype.slotIds()) {
            for (PartDef part : parts) {
                if (part != null) {
                    total += part.socketCount(slotId);
                }
            }
        }
        return total;
    }

    public static List<String> previewLines(ArchetypeDef archetype, Collection<PartDef> parts) {
        List<String> lines = new ArrayList<>();
        if (archetype == null) {
            lines.add("§8No rune sockets");
            return lines;
        }
        int listed = 0;
        int raw = rawTotal(archetype, parts);
        for (String slotId : archetype.slotIds()) {
            int count = 0;
            if (parts != null) {
                for (PartDef part : parts) {
                    if (part != null) {
                        count += part.socketCount(slotId);
                    }
                }
            }
            String suffix = archetype.slotSuffix(slotId);
            if (suffix.isEmpty() || count <= 0) {
                continue;
            }
            for (int i = 0; i < count; i++) {
                if (listed >= MAX_SOCKETS) {
                    break;
                }
                lines.add("§7- §f" + suffix);
                listed++;
            }
            if (listed >= MAX_SOCKETS) {
                break;
            }
        }
        if (lines.isEmpty()) {
            lines.add("§8No rune sockets");
            return lines;
        }
        if (raw > MAX_SOCKETS) {
            lines.add("§8Clamped to " + MAX_SOCKETS + " (was " + raw + ")");
        }
        return lines;
    }
}
