package net.tfminecraft.magic.blood;

import java.util.LinkedHashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * The altar's pool of blood and the blood resonance it feeds. Artefacts charge in the pool, and
 * meditating among the charged ones draws their charge into blood resonance, up to what the path's
 * artefacts are worth. While the path goes on, charging is free and resonance keeps. Once the path is
 * complete, charging costs pool blood, resonance fades by the hour the character is played, and only
 * people sacrificed on the altar fill the pool again. Resonance never sinks below the floor, so the
 * lowest spells always work.
 */
public final class BloodPool {

    /** Records whose resonance faded since they were last written; saved with {@link #flush}. */
    private static final Set<BloodRecord> FADED = new LinkedHashSet<>();

    private BloodPool() {}

    /** The pool's blood: full until the path is complete, and full when first measured after that. */
    static double level(BloodRecord record) {
        Double pool = record.getStage() == BloodStage.ASCENDED ? record.getPool() : null;
        return pool != null ? Math.min(pool, PoolConfig.capacity()) : PoolConfig.capacity();
    }

    /** The most blood resonance the path's artefacts can give now. */
    static double cap(BloodRecord record) {
        return BloodArtefacts.maxCharge(record) * BloodArtefacts.ids(record).size();
    }

    /** The character's blood resonance now, never below the floor; the floor without a path's class. */
    public static double resonance(BloodRecord record) {
        if (record == null) {
            return PoolConfig.floor();
        }
        return Math.max(PoolConfig.floor(), Math.min(record.getResonance(), cap(record)));
    }

    /** How the pool looks to its owner: full, half, low or dry. */
    static String state(BloodRecord record) {
        double level = level(record);
        double capacity = PoolConfig.capacity();
        if (level <= 0) {
            return "dry";
        }
        return level >= capacity * 0.75 ? "full" : level >= capacity * 0.25 ? "half" : "low";
    }

    /**
     * Takes the blood for this much charge from the pool and returns the charge it paid for: all of it
     * while the path goes on, else as much as the pool holds.
     */
    static double pay(BloodRecord record, double charge) {
        double price = PoolConfig.bloodPerPoint();
        if (record.getStage() != BloodStage.ASCENDED || price <= 0) {
            return charge;
        }
        double level = level(record);
        double paid = Math.min(charge, level / price);
        record.setPool(level - paid * price);
        return paid;
    }

    /**
     * Every minute: mages online whose path is complete lose a share of their blood resonance (never
     * below the floor) and hear it when a tier slips away.
     */
    static void tick() {
        double share = PoolConfig.decayPercent() / 100.0 / 60.0;
        if (share <= 0) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            BloodRecord record = BloodRecords.get(AltarService.currentCharacter(player));
            if (record == null || record.getStage() != BloodStage.ASCENDED
                    || record.getResonance() <= PoolConfig.floor()) {
                continue;
            }
            int before = PoolConfig.band(resonance(record));
            record.setResonance(Math.max(PoolConfig.floor(), record.getResonance() * (1 - share)));
            if (PoolConfig.band(resonance(record)) < before) {
                FADED.remove(record);
                BloodRecords.save(record);
                WhisperService.tell(player, "blood.pool.thinned");
            } else {
                FADED.add(record);
            }
        }
    }

    /**
     * Writes the records whose resonance only faded a little since they were saved (every ten minutes
     * and on shutdown, so the minute tick never touches the disk unless a tier slipped).
     */
    static void flush() {
        for (BloodRecord record : FADED) {
            // A record reset or forgotten since is not written back.
            if (BloodRecords.get(record.getCharacterId()) == record) {
                BloodRecords.save(record);
            }
        }
        FADED.clear();
    }

    /** A person died on the altar after the path was complete: their blood fills the pool by closeness. */
    static void fedPerson(Player mage, BloodRecord record, int rank) {
        record.setPool(Math.min(PoolConfig.capacity(), level(record) + PoolConfig.refill(rank)));
        BloodRecords.save(record);
        WhisperService.tell(mage, "blood.pool.fed");
    }
}
