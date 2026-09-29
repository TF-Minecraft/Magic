package net.tfminecraft.magic.gear;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

public final class GearProvenance {

    private static final Gson GSON = new Gson();
    private static final Type INPUTS_TYPE = new TypeToken<LinkedHashMap<String, Integer>>() {}.getType();

    private GearProvenance() {}

    public static void stamp(ItemStack stack, GearType type, Collection<PartDef> parts) {
        if (stack == null || !stack.hasItemMeta() || type == null) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        List<String> ids = new ArrayList<>();
        if (parts != null) {
            for (PartDef part : parts) {
                if (part != null) {
                    ids.add(part.getId() + ":" + part.getRevision());
                }
            }
        }
        ArchetypeDef archetype = ArchetypeRegistry.get(type);
        meta.getPersistentDataContainer().set(
                GearKeys.parts(), PersistentDataType.STRING, String.join(",", ids));
        meta.getPersistentDataContainer().set(
                GearKeys.archetype(), PersistentDataType.STRING, type.name().toLowerCase(Locale.ROOT));
        meta.getPersistentDataContainer().set(
                GearKeys.archetypeRevision(), PersistentDataType.INTEGER,
                archetype == null ? 1 : archetype.getRevision());
        writeMajority(meta, parts);
        stack.setItemMeta(meta);
    }

    /** Records what the craft actually charged. Empty when staff bypassed the cost. */
    public static void stampInputs(ItemStack stack, Map<String, Integer> charged) {
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(
                GearKeys.craftInputs(), PersistentDataType.STRING, GSON.toJson(charged == null ? Map.of() : charged));
        stack.setItemMeta(meta);
    }

    /** Materials charged at craft time, or null for weapons crafted before this was recorded. */
    public static Map<String, Integer> readInputs(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        String json = meta.getPersistentDataContainer().get(GearKeys.craftInputs(), PersistentDataType.STRING);
        if (json == null) {
            return null;
        }
        try {
            return GSON.fromJson(json, INPUTS_TYPE);
        } catch (JsonParseException ex) {
            return null;
        }
    }

    public static int majorityOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return 0;
        }
        ItemMeta meta = stack.getItemMeta();
        Integer stored = meta.getPersistentDataContainer().get(
                GearKeys.majorityTier(), PersistentDataType.INTEGER);
        return stored == null ? 0 : stored;
    }

    public static void applyMajority(ItemStack stack, Collection<PartDef> parts) {
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        writeMajority(meta, parts);
        stack.setItemMeta(meta);
    }

    private static void writeMajority(ItemMeta meta, Collection<PartDef> parts) {
        int majority = MajorityTierResolver.resolve(parts);
        if (majority > 0) {
            meta.getPersistentDataContainer().set(
                    GearKeys.majorityTier(), PersistentDataType.INTEGER, majority);
        } else {
            meta.getPersistentDataContainer().remove(GearKeys.majorityTier());
        }
    }

    /** Stamped part ids that no longer exist in the live registry. */
    public static List<String> missingPartIds(ItemStack stack) {
        List<String> missing = new ArrayList<>();
        for (String token : rawTokens(stack)) {
            String id = idOf(token);
            if (PartRegistry.get(id) == null) {
                missing.add(id);
            }
        }
        return missing;
    }

    /**
     * True when any stamped part or the archetype has been edited since this item was
     * built. A missing part id does not count as outdated; that is the broken path.
     */
    public static boolean isOutdated(ItemStack stack) {
        if (!isGear(stack)) {
            return false;
        }
        for (String token : rawTokens(stack)) {
            String id = idOf(token);
            PartDef live = PartRegistry.get(id);
            if (live != null && live.getRevision() > revisionOf(token)) {
                return true;
            }
        }
        GearType type = archetypeOf(stack);
        ArchetypeDef archetype = ArchetypeRegistry.get(type);
        return archetype != null && archetype.getRevision() > storedArchetypeRevision(stack);
    }

    /** Rewrites every stamped revision to the live value. Call after a successful rebuild. */
    public static void syncRevisions(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        List<String> tokens = new ArrayList<>();
        for (String token : rawTokens(stack)) {
            String id = idOf(token);
            PartDef live = PartRegistry.get(id);
            tokens.add(id + ":" + (live == null ? revisionOf(token) : live.getRevision()));
        }
        meta.getPersistentDataContainer().set(
                GearKeys.parts(), PersistentDataType.STRING, String.join(",", tokens));
        ArchetypeDef archetype = ArchetypeRegistry.get(archetypeOf(stack));
        if (archetype != null) {
            meta.getPersistentDataContainer().set(
                    GearKeys.archetypeRevision(), PersistentDataType.INTEGER, archetype.getRevision());
        }
        stack.setItemMeta(meta);
    }

    private static int storedArchetypeRevision(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        Integer stored = meta.getPersistentDataContainer().get(
                GearKeys.archetypeRevision(), PersistentDataType.INTEGER);
        return stored == null ? 0 : stored;
    }

    private static List<String> rawTokens(ItemStack stack) {
        String raw = partsRaw(stack);
        if (raw.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : raw.split(",")) {
            if (!token.isBlank()) {
                tokens.add(token.trim());
            }
        }
        return tokens;
    }

    private static String idOf(String token) {
        int colon = token.indexOf(':');
        return colon > 0 ? token.substring(0, colon) : token;
    }

    private static int revisionOf(String token) {
        int colon = token.indexOf(':');
        if (colon <= 0) {
            return 1;
        }
        try {
            return Integer.parseInt(token.substring(colon + 1).trim());
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    /** Raw stored part list, used as a cheap identity for rate-limiting per weapon. */
    public static String partsRaw(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return "";
        }
        ItemMeta meta = stack.getItemMeta();
        String raw = meta.getPersistentDataContainer().get(GearKeys.parts(), PersistentDataType.STRING);
        return raw == null ? "" : raw;
    }

    public static List<PartDef> resolveParts(ItemStack stack) {
        List<PartDef> parts = new ArrayList<>();
        if (stack == null || !stack.hasItemMeta()) {
            return parts;
        }
        ItemMeta meta = stack.getItemMeta();
        String raw = meta.getPersistentDataContainer().get(GearKeys.parts(), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return parts;
        }
        for (String token : raw.split(",")) {
            String id = token;
            int colon = token.indexOf(':');
            if (colon > 0) {
                id = token.substring(0, colon);
            }
            PartDef def = PartRegistry.get(id);
            if (def != null) {
                parts.add(def);
            }
        }
        return parts;
    }

    public static GearType archetypeOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        String stored = meta.getPersistentDataContainer().get(
                GearKeys.archetype(), PersistentDataType.STRING);
        return GearType.fromId(stored);
    }

    public static boolean isGear(ItemStack stack) {
        return archetypeOf(stack) != null;
    }

    public static boolean socketsLocked(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        Byte locked = meta.getPersistentDataContainer().get(
                GearKeys.socketsLocked(), PersistentDataType.BYTE);
        return locked != null && locked != 0;
    }

    public static void lockSockets(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(
                GearKeys.socketsLocked(), PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
    }
}
