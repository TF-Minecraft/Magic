package net.tfminecraft.magic.artifact.path;

import java.util.LinkedHashMap;
import java.util.Locale;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.artifact.config.ArtifactTypeDef;
import net.tfminecraft.magic.artifact.config.ArtifactTypeRegistry;

public final class ArtifactPathParser {

    private ArtifactPathParser() {}

    public static ArtifactPathSpec parse(String raw) {
        if (raw == null || raw.isBlank()) {
            log("Empty artifact path.");
            return null;
        }
        String trimmed = raw.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("magic.")) {
            log("Path must start with magic.: " + trimmed);
            return null;
        }
        String rest = trimmed.substring("magic.".length());
        if (rest.isEmpty()) {
            log("Empty magic. path.");
            return null;
        }
        if ("artifact".equalsIgnoreCase(rest)) {
            return new ArtifactPathSpec(null, null, new LinkedHashMap<>());
        }
        if (!rest.startsWith("(") || !rest.endsWith(")") || rest.length() < 2) {
            log("Expected magic.artifact or magic.(...): " + trimmed);
            return null;
        }
        String inner = rest.substring(1, rest.length() - 1);
        String primaryId = null;
        String rarityId = null;
        LinkedHashMap<String, Double> extras = new LinkedHashMap<>();
        for (String token : inner.split(";")) {
            if (token == null) {
                continue;
            }
            String part = token.trim();
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            String key = (eq < 0 ? part : part.substring(0, eq)).trim().toLowerCase(Locale.ROOT);
            String value = eq < 0 ? null : part.substring(eq + 1).trim();
            if (key.isEmpty()) {
                continue;
            }
            if ("primary".equals(key) || "element".equals(key)) {
                if (value == null || value.isBlank()) {
                    log("Missing value for " + key + " in " + trimmed);
                    return null;
                }
                String canonical = canonicalTypeId(value);
                primaryId = canonical != null ? canonical : value.toLowerCase(Locale.ROOT);
                continue;
            }
            if ("rarity".equals(key)) {
                if (value == null || value.isBlank()) {
                    log("Missing rarity value in " + trimmed);
                    return null;
                }
                rarityId = value.toLowerCase(Locale.ROOT);
                continue;
            }
            String typeId = canonicalTypeId(key);
            if (typeId == null) {
                log("Unknown artifact path key '" + key + "'");
                continue;
            }
            if (value == null || value.isBlank()) {
                extras.put(typeId, null);
                continue;
            }
            try {
                extras.put(typeId, Double.parseDouble(value));
            } catch (NumberFormatException ex) {
                log("Bad cap number for " + key + " in " + trimmed);
                return null;
            }
        }
        return new ArtifactPathSpec(primaryId, rarityId, extras);
    }

    private static String canonicalTypeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ArtifactTypeDef exact = ArtifactTypeRegistry.getById(raw);
        if (exact != null) {
            return exact.getElementId();
        }
        for (ArtifactTypeDef type : ArtifactTypeRegistry.getAll()) {
            if (type.getElementId().equalsIgnoreCase(raw)) {
                return type.getElementId();
            }
        }
        return null;
    }

    private static void log(String message) {
        if (Magic.plugin != null) {
            Magic.plugin.getLogger().warning("[Magic] " + message);
        }
    }
}
