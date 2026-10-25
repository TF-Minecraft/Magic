package net.tfminecraft.magic.blood;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.integration.RpCharactersBridge;

/**
 * Character sacrifices. A knocked-out character near the altar is dragged onto it with the mage's
 * blood knife; once the short rite ends the character dies for good, whatever happens to the
 * knockout meanwhile. In the hunt it earns points by how much the victim mattered (the closest
 * are kept for the finals); in the finals only the named target counts.
 */
public final class CharacterRiteService {

    public enum Bind {
        OK,
        NOT_STAGE,
        NOT_KNIFE,
        NO_ALTAR,
        NO_CHARACTER,
        NOT_DOWN,
        TOO_FAR,
        TOO_NEW,
        REPEAT,
        RESERVED,
        WAITING,
        NOT_TARGET,
        BUSY
    }

    private record Rite(UUID victim, String mageCharacter, String victimCharacter, String victimAccount,
            int points, long endsAt) {}

    private static final Map<UUID, Rite> RITES = new HashMap<>();
    private static final Set<String> SACRIFICING = new HashSet<>();
    private static final double LEAVE_SLACK = 4;
    private static final double ESCAPE = 2.5;
    static LongSupplier clock = System::currentTimeMillis;

    private CharacterRiteService() {}

    /** Tries to drag the knocked-out victim onto the mage's altar. */
    public static Bind bind(Player mage, Player victim) {
        String character = AltarService.currentCharacter(mage);
        BloodRecord record = BloodRecords.get(character);
        Bind ready = readiness(mage, character, record);
        if (ready != null) {
            return ready;
        }
        BloodAltar altar = BloodAltars.ofCharacter(character);
        Location top = altar != null ? altar.center() : null;
        if (top == null) {
            return Bind.NO_ALTAR;
        }
        String victimCharacter = AltarService.currentCharacter(victim);
        if (victimCharacter == null) {
            return Bind.NO_CHARACTER;
        }
        if (!RpCharactersBridge.isDown(victim)) {
            return Bind.NOT_DOWN;
        }
        if (victim.getWorld() != top.getWorld() || victim.getLocation().distance(top) > HuntConfig.dragRange()) {
            return Bind.TOO_FAR;
        }
        String victimAccount = victim.getUniqueId().toString();
        if (RpCharactersBridge.playtimeSeconds(victim) < HuntConfig.minPlaytimeHours() * 3600
                || RpCharactersBridge.characterAgeSeconds(victim) < HuntConfig.minAgeDays() * 86_400) {
            return Bind.TOO_NEW;
        }
        if (HuntConfig.noRepeatPlayer() && victimAccount.equalsIgnoreCase(record.getLastVictimAccount())) {
            return Bind.REPEAT;
        }
        int points = 0;
        if (record.getStage() == BloodStage.HUNT) {
            int rank = BondService.rank(mage, victimCharacter);
            if (rank >= 1 && rank <= HuntConfig.finalCount()) {
                return Bind.RESERVED;
            }
            points = HuntConfig.points(rank);
        } else {
            if (clock.getAsLong() < record.getNextFinalAt()) {
                return Bind.WAITING;
            }
            if (!victimCharacter.equalsIgnoreCase(record.currentTarget())) {
                return Bind.NOT_TARGET;
            }
        }
        if (RITES.containsKey(mage.getUniqueId()) || isVictim(victim.getUniqueId())) {
            return Bind.BUSY;
        }
        CarryService.release(mage, false);
        RpCharactersBridge.moveDowned(victim, top.clone().add(0, 0.5, 0));
        RITES.put(mage.getUniqueId(), new Rite(victim.getUniqueId(), character, victimCharacter, victimAccount,
                points, clock.getAsLong() + HuntConfig.riteSeconds() * 1000L));
        victim.sendMessage(Messages.get("blood.rite.dread"));
        BloodFx.play("rite_bound", top, victim);
        return Bind.OK;
    }

    /** NOT_STAGE or NOT_KNIFE when the mage may not take anyone now, else null. */
    static Bind readiness(Player mage, String character, BloodRecord record) {
        if (record == null || (record.getStage() != BloodStage.HUNT && record.getStage() != BloodStage.FINAL)) {
            return Bind.NOT_STAGE;
        }
        ItemStack knife = mage.getInventory().getItemInMainHand();
        if (!BloodItems.is(knife, BloodItemKind.BLOOD_KNIFE) || !character.equalsIgnoreCase(BloodItems.owner(knife))) {
            return Bind.NOT_KNIFE;
        }
        return null;
    }

    static boolean isVictim(UUID player) {
        for (Rite rite : RITES.values()) {
            if (rite.victim().equals(player)) {
                return true;
            }
        }
        return false;
    }

    /** Whether this character is dying on an altar right now (its death is not a failed final). */
    public static boolean isBeingSacrificed(String character) {
        return character != null && SACRIFICING.contains(character.toLowerCase(Locale.ROOT));
    }

    /** Every second: the rite beats on, breaks when the mage walks off, and ends in death. */
    static void tick() {
        long now = clock.getAsLong();
        for (Iterator<Map.Entry<UUID, Rite>> it = RITES.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Rite> entry = it.next();
            Rite rite = entry.getValue();
            Player mage = Bukkit.getPlayer(entry.getKey());
            Player victim = Bukkit.getPlayer(rite.victim());
            BloodAltar altar = BloodAltars.ofCharacter(rite.mageCharacter());
            Location top = altar != null ? altar.center() : null;
            if (mage == null || victim == null || top == null || mage.getWorld() != top.getWorld()
                    || mage.getLocation().distance(top) > HuntConfig.dragRange() + LEAVE_SLACK
                    || victim.getWorld() != top.getWorld() || victim.getLocation().distance(top) > ESCAPE
                    || !rite.mageCharacter().equalsIgnoreCase(AltarService.currentCharacter(mage))
                    || !rite.victimCharacter().equalsIgnoreCase(AltarService.currentCharacter(victim))) {
                it.remove();
                if (mage != null) {
                    mage.sendMessage(Messages.get("blood.rite.broken"));
                }
                if (top != null) {
                    BloodFx.play("rite_broken", top);
                }
                continue;
            }
            if (now < rite.endsAt()) {
                BloodFx.play("rite_beat", top, victim);
                continue;
            }
            it.remove();
            complete(mage, victim, rite, altar);
        }
    }

    private static void complete(Player mage, Player victim, Rite rite, BloodAltar altar) {
        String key = rite.victimCharacter().toLowerCase(Locale.ROOT);
        SACRIFICING.add(key);
        boolean died;
        try {
            died = RpCharactersBridge.sacrifice(victim, mage);
        } finally {
            SACRIFICING.remove(key);
        }
        if (!died) {
            mage.sendMessage(Messages.get("blood.rite.refused"));
            return;
        }
        BloodRecord record = BloodRecords.get(rite.mageCharacter());
        if (record == null) {
            return;
        }
        BloodStage stage = record.getStage();
        record.getSacrifices().add(new BloodRecord.Sacrifice(key, rite.victimAccount(), stage.name(),
                rite.points(), clock.getAsLong()));
        record.setLastVictimAccount(rite.victimAccount());
        BloodFx.play("rite_complete", altar.center(), mage);
        Magic.plugin.getLogger().info("[Magic] Character " + rite.mageCharacter() + " sacrificed character "
                + key + " in stage " + stage + " for " + rite.points() + " point(s)");
        if (stage == BloodStage.HUNT) {
            record.setHuntPoints(record.getHuntPoints() + rite.points());
            if (record.getHuntPoints() >= BloodConfig.huntPointsRequired()) {
                FinalService.begin(mage, record);
            } else {
                mage.sendMessage(Messages.get("blood.rite.taken"));
                BloodRecords.save(record);
            }
        } else if (stage == BloodStage.FINAL && key.equalsIgnoreCase(record.currentTarget())) {
            FinalService.completed(mage, record);
        } else {
            BloodRecords.save(record);
        }
        AltarService.refresh(altar);
        AltarCorruption.spread(altar, CorruptionConfig.blocksPerSacrifice());
        BloodAltars.save();
    }

    /** Ends every rite without a death, e.g. on shutdown. */
    public static void clear() {
        RITES.clear();
        SACRIFICING.clear();
    }

    static boolean hasRite(UUID mage) {
        return RITES.containsKey(mage);
    }
}
