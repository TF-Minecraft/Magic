package net.tfminecraft.magic.blood;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * The altar's pool of blood and the blood resonance it holds up. The artefacts sunk in the pool give
 * the resonance (their share of what the path's artefacts are worth); the pool caps it at its level.
 * It stays full while the path goes on. Once the path is complete, the sunk artefacts drink it by the
 * hour the character is played, and only people sacrificed on the altar fill it again. Resonance never
 * sinks below the floor, so the lowest spells always work.
 */
public final class BloodPool {

    private BloodPool() {}

    /** The pool's blood: full until the path is complete, and full when first measured after that. */
    static double level(BloodRecord record) {
        Double pool = record.getStage() == BloodStage.ASCENDED ? record.getPool() : null;
        return pool != null ? Math.min(pool, PoolConfig.capacity()) : PoolConfig.capacity();
    }

    /** The character's blood resonance now; the floor without a path's class. */
    public static double resonance(BloodRecord record) {
        if (record == null) {
            return PoolConfig.floor();
        }
        int places = BloodArtefacts.ids(record).size();
        int reached = record.getStage() == BloodStage.ASCENDED ? Integer.MAX_VALUE : record.getFinalsDone();
        double artefacts = places == 0 ? 0
                : PoolConfig.worth(record.getPath(), reached) * BloodArtefacts.sunk(record) / places;
        return Math.max(PoolConfig.floor(), Math.min(artefacts, level(record)));
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

    /** Every minute: the pools of mages online whose path is complete drain; they hear it thin. */
    static void tick() {
        double drain = PoolConfig.drainPerHour() / 60.0;
        if (drain <= 0) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            BloodRecord record = BloodRecords.get(AltarService.currentCharacter(player));
            if (record == null || record.getStage() != BloodStage.ASCENDED || BloodArtefacts.sunk(record) == 0) {
                continue;
            }
            double level = level(record);
            if (level <= 0) {
                continue;
            }
            int before = PoolConfig.band(resonance(record));
            record.setPool(Math.max(0, level - drain));
            BloodRecords.save(record);
            if (record.getPool() <= 0) {
                WhisperService.tell(player, "blood.pool.dry");
            } else if (PoolConfig.band(resonance(record)) < before) {
                WhisperService.tell(player, "blood.pool.thinned");
            }
        }
    }

    /** A person died on the altar after the path was complete: their blood fills the pool by closeness. */
    static void fedPerson(Player mage, BloodRecord record, int rank) {
        record.setPool(Math.min(PoolConfig.capacity(), level(record) + PoolConfig.refill(rank)));
        BloodRecords.save(record);
        WhisperService.tell(mage, "blood.pool.fed");
    }
}
