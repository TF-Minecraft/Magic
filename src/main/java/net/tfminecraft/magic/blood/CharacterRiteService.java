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
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

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
        BUSY,
        BLOCKED
    }

    private record Rite(UUID victim, String mageCharacter, String victimCharacter, String victimAccount,
            int points, int rank, long endsAt) {}

    private static final Map<UUID, Rite> RITES = new HashMap<>();
    private static final Set<String> SACRIFICING = new HashSet<>();
    private static final double LEAVE_SLACK = 4;
    private static final double ESCAPE = 2.5;
    private static final double BODY_HEIGHT = 1.2;
    private static final double LOW_HEIGHT = 0.7;
    private static final double ALTAR_TOP = 0.6;
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
        if (!clearPath(victim.getLocation(), top)) {
            return Bind.BLOCKED;
        }
        Bind eligible = eligibility(mage, record, victim, victimCharacter);
        if (eligible != null) {
            return eligible;
        }
        UUID carrier = CarryService.carrier(victim.getUniqueId());
        if (RITES.containsKey(mage.getUniqueId()) || isVictim(victim.getUniqueId())
                || (carrier != null && !carrier.equals(mage.getUniqueId()))) {
            return Bind.BUSY;
        }
        String victimAccount = victim.getUniqueId().toString();
        int rank = record.getStage() == BloodStage.HUNT ? BondService.rank(mage, victimCharacter) : 0;
        int points = record.getStage() == BloodStage.HUNT ? HuntConfig.points(rank) : 0;
        if (!RpCharactersBridge.moveDowned(victim, top.clone().add(0, 0.5, 0))) {
            return Bind.BLOCKED;
        }
        CarryService.release(mage, false);
        RITES.put(mage.getUniqueId(), new Rite(victim.getUniqueId(), character, victimCharacter, victimAccount,
                points, rank, clock.getAsLong() + HuntConfig.riteSeconds() * 1000L));
        victim.sendMessage(Messages.get("blood.rite.dread"));
        BloodFx.play("rite_bound", top, victim);
        return Bind.OK;
    }

    /**
     * Whether this victim may be taken now: TOO_NEW, REPEAT (hunt only; a named final target must stay
     * takeable), RESERVED (the closest are kept for the finals), WAITING, NOT_TARGET; null when they may.
     */
    static Bind eligibility(Player mage, BloodRecord record, Player victim, String victimCharacter) {
        if (RpCharactersBridge.playtimeSeconds(victim) < HuntConfig.minPlaytimeHours() * 3600
                || RpCharactersBridge.characterAgeSeconds(victim) < HuntConfig.minAgeDays() * 86_400) {
            return Bind.TOO_NEW;
        }
        if (record.getStage() == BloodStage.HUNT) {
            if (HuntConfig.noRepeatPlayer()
                    && victim.getUniqueId().toString().equalsIgnoreCase(record.getLastVictimAccount())) {
                return Bind.REPEAT;
            }
            int rank = BondService.rank(mage, victimCharacter);
            return rank >= 1 && rank <= HuntConfig.finalCount() ? Bind.RESERVED : null;
        }
        if (clock.getAsLong() < record.getNextFinalAt()) {
            return Bind.WAITING;
        }
        return victimCharacter.equalsIgnoreCase(record.currentTarget()) ? null : Bind.NOT_TARGET;
    }

    /**
     * No wall between the body and the altar: the rite never pulls anyone through stone. Two lines run
     * to the altar top: one at chest height above the floor, where any solid block counts, and one just
     * above slab and bed height, where only full blocks count. Slabs, stairs, pews and beds do not stop
     * it; walls, windows, full blocks and ceilings do. Doors count as solid even when open, so a body goes
     * through a door, or around a block in the way, by being dragged.
     */
    static boolean clearPath(Location from, Location altarCenter) {
        return clearLine(from.clone().add(0, BODY_HEIGHT, 0), altarCenter, false)
                && clearLine(from.clone().add(0, LOW_HEIGHT, 0), altarCenter, true);
    }

    private static boolean clearLine(Location start, Location altarCenter, boolean fullBlocksOnly) {
        Vector line = altarCenter.clone().add(0, ALTAR_TOP, 0).toVector().subtract(start.toVector());
        int samples = (int) Math.ceil(line.length() / 0.25);
        Block altarBlock = altarCenter.getBlock();
        for (int i = 1; i < samples; i++) {
            Block block = start.clone().add(line.clone().multiply((double) i / samples)).getBlock();
            Material type = block.getType();
            if (!block.equals(altarBlock) && (fullBlocksOnly ? type.isOccluding() : type.isSolid())) {
                return false;
            }
        }
        return true;
    }

    /**
     * A victim who logs out on the altar does not escape: the rite finishes as they leave (their
     * character session is still open at this point). A mage who logs out simply breaks the rite.
     */
    static void onQuit(Player player) {
        UUID id = player.getUniqueId();
        RITES.remove(id);
        for (Iterator<Map.Entry<UUID, Rite>> it = RITES.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Rite> entry = it.next();
            if (!entry.getValue().victim().equals(id)) {
                continue;
            }
            it.remove();
            Player mage = Bukkit.getPlayer(entry.getKey());
            BloodAltar altar = BloodAltars.ofCharacter(entry.getValue().mageCharacter());
            if (BloodConfig.isEnabled() && altar != null && holds(mage, player, entry.getValue(), altar.center())) {
                complete(mage, player, entry.getValue(), altar);
            }
            return;
        }
    }

    /** Mage near the altar, body on it, both still the same characters. */
    private static boolean holds(Player mage, Player victim, Rite rite, Location top) {
        return mage != null && victim != null && top != null && mage.getWorld() == top.getWorld()
                && mage.getLocation().distance(top) <= HuntConfig.dragRange() + LEAVE_SLACK
                && victim.getWorld() == top.getWorld() && victim.getLocation().distance(top) <= ESCAPE
                && rite.mageCharacter().equalsIgnoreCase(AltarService.currentCharacter(mage))
                && rite.victimCharacter().equalsIgnoreCase(AltarService.currentCharacter(victim));
    }

    /** NOT_STAGE or NOT_KNIFE when the mage may not take anyone now, else null. */
    static Bind readiness(Player mage, String character, BloodRecord record) {
        if (!BloodConfig.isEnabled() || record == null
                || (record.getStage() != BloodStage.HUNT && record.getStage() != BloodStage.FINAL)) {
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
            if (!holds(mage, victim, rite, top)) {
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
                rite.points(), clock.getAsLong(), rite.rank()));
        record.setLastVictimAccount(rite.victimAccount());
        BloodFx.play("rite_complete", altar.center(), mage);
        Magic.plugin.getLogger().info("[Magic] Character " + rite.mageCharacter() + " sacrificed character "
                + key + " in stage " + stage + " for " + rite.points() + " point(s)");
        if (stage == BloodStage.HUNT) {
            record.setHuntPoints(record.getHuntPoints() + rite.points());
            if (record.getHuntPoints() >= BloodConfig.huntPointsRequired()) {
                FinalService.begin(mage, record);
            } else {
                WhisperService.tell(mage, "blood.rite.taken");
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
