package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.block.Block;

/** Placed altars, indexed by id, block and owner. All lookups stay in memory. */
public final class BloodAltars {

    private static final Map<String, BloodAltar> BY_ID = new LinkedHashMap<>();
    private static final Map<String, BloodAltar> BY_BLOCK = new LinkedHashMap<>();
    private static final Map<String, BloodAltar> BY_OWNER = new LinkedHashMap<>();
    private static BloodStore store;

    private BloodAltars() {}

    public static void init(BloodStore source) {
        clearIndex();
        store = source;
        for (BloodAltar altar : source.loadAltars()) {
            index(altar);
        }
    }

    public static void clear() {
        clearIndex();
        store = null;
    }

    private static void clearIndex() {
        BY_ID.clear();
        BY_BLOCK.clear();
        BY_OWNER.clear();
    }

    private static void index(BloodAltar altar) {
        BY_ID.put(altar.getId(), altar);
        BY_BLOCK.put(altar.blockKey(), altar);
        BY_OWNER.put(altar.getOwnerCharacter().toLowerCase(Locale.ROOT), altar);
    }

    public static BloodAltar at(Block block) {
        return block != null ? BY_BLOCK.get(BloodAltar.key(block)) : null;
    }

    public static BloodAltar byId(String id) {
        return id != null ? BY_ID.get(id) : null;
    }

    public static BloodAltar ofCharacter(String characterId) {
        return characterId != null ? BY_OWNER.get(characterId.toLowerCase(Locale.ROOT)) : null;
    }

    public static List<BloodAltar> inChunk(String world, int chunkX, int chunkZ) {
        String chunk = BloodAltar.chunk(world, chunkX, chunkZ);
        List<BloodAltar> out = new ArrayList<>();
        for (BloodAltar altar : BY_ID.values()) {
            if (altar.chunkKey().equals(chunk)) {
                out.add(altar);
            }
        }
        return out;
    }

    public static List<BloodAltar> all() {
        return new ArrayList<>(BY_ID.values());
    }

    public static boolean add(BloodAltar altar) {
        index(altar);
        return save();
    }

    public static boolean remove(BloodAltar altar) {
        BY_ID.remove(altar.getId());
        BY_BLOCK.remove(altar.blockKey());
        BY_OWNER.remove(altar.getOwnerCharacter().toLowerCase(Locale.ROOT));
        return save();
    }

    /** Writes every altar; called after any change to one of them. */
    public static boolean save() {
        return store == null || store.saveAltars(new ArrayList<>(BY_ID.values()));
    }
}
