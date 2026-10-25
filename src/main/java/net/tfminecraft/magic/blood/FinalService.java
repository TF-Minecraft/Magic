package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.integration.RpCharactersBridge;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterStatus;
import net.tfminecraft.tlibs.character.Characters;

/**
 * The final sacrifices: the mage's closest characters, one named at a time from the least close
 * to the closest. A named target that dies any other way is a failed sacrifice: backlash, a wait,
 * then the next. Dead, vanished or long-absent targets are replaced so the stage never sticks.
 */
public final class FinalService {

    static LongSupplier clock = System::currentTimeMillis;

    private FinalService() {}

    /** Starts the finals: the path's class and armour (true path), then the first target. */
    public static void begin(Player mage, BloodRecord record) {
        record.setStage(BloodStage.FINAL);
        if (record.getPath() == BloodPath.TRUE) {
            ClassUnlockBridge.unlock(mage, ChoiceConfig.classFor(BloodPath.TRUE));
            BloodArmour.give(mage, BloodPath.TRUE, record.getCharacterId());
            mage.sendMessage(Messages.get("blood.hunt.complete"));
            BloodFx.play("hunt_complete", mage.getLocation(), mage);
        }
        refill(mage, record);
        BloodRecords.save(record);
        name(mage, record);
        Magic.plugin.getLogger().info("[Magic] Character " + record.getCharacterId() + " began the final sacrifices: "
                + record.getFinalTargets());
    }

    /**
     * Tops the target list up from the current closeness ranking, skipping anyone already
     * sacrificed, and keeps it least close first so the last sacrifice is the closest.
     */
    static boolean refill(Player mage, BloodRecord record) {
        return refill(mage, record, Set.of());
    }

    private static boolean refill(Player mage, BloodRecord record, Set<String> exclude) {
        int wanted = HuntConfig.finalCount() - record.getFinalsDone();
        List<BondService.Ranked> ranking = BondService.ranking(mage);
        Set<String> taken = new HashSet<>(exclude);
        for (BloodRecord.Sacrifice sacrifice : record.getSacrifices()) {
            taken.add(sacrifice.character().toLowerCase(Locale.ROOT));
        }
        List<String> targets = record.getFinalTargets();
        boolean changed = false;
        for (BondService.Ranked ranked : ranking) {
            if (targets.size() >= wanted) {
                break;
            }
            if (!taken.contains(ranked.character()) && !targets.contains(ranked.character())) {
                targets.add(ranked.character());
                changed = true;
            }
        }
        List<String> order = new ArrayList<>();
        for (BondService.Ranked ranked : ranking) {
            order.add(ranked.character());
        }
        targets.sort(Comparator.comparingInt((String id) -> order.contains(id) ? order.indexOf(id) : order.size())
                .reversed());
        return changed;
    }

    /** Tells the mage, in their voice, who is named now; or that there is nobody yet. */
    static void name(Player mage, BloodRecord record) {
        String target = record.currentTarget();
        WhisperConfig.Speaker speaker = WhisperService.speaker(record);
        if (target == null) {
            WhisperService.speak(mage, speaker, Messages.getRaw("blood.final.nobody"));
            return;
        }
        WhisperService.speak(mage, speaker, Messages.getRaw("blood.final.named").replace("{target}", nameOf(target)));
        BloodFx.play("target_named", mage.getLocation(), mage);
    }

    static String nameOf(String character) {
        try {
            return Characters.info(UUID.fromString(character)).map(CharacterInfo::name).orElse("?");
        } catch (IllegalArgumentException ex) {
            return "?";
        }
    }

    /** The named target was sacrificed at the altar. */
    static void completed(Player mage, BloodRecord record) {
        if (!record.getFinalTargets().isEmpty()) {
            record.getFinalTargets().remove(0);
        }
        record.setFinalsDone(record.getFinalsDone() + 1);
        if (record.getFinalsDone() >= HuntConfig.finalCount()) {
            record.setStage(BloodStage.ASCENDED);
            record.getFinalTargets().clear();
            mage.sendMessage(Messages.get("blood.final.ascended"));
            BloodFx.play("ascended", mage.getLocation(), mage);
        } else {
            refill(mage, record);
            mage.sendMessage(Messages.get("blood.final.taken"));
            BloodFx.play("final_taken", mage.getLocation(), mage);
        }
        BloodRecords.save(record);
        BloodBoons.apply(mage);
        if (record.getStage() == BloodStage.FINAL) {
            name(mage, record);
        }
    }

    /**
     * A character died, not on the mage's altar. For every mage naming them now, it is a failed
     * sacrifice; later targets just leave the list.
     */
    public static void onDeath(String character) {
        for (BloodRecord record : BloodRecords.all()) {
            if (record.getStage() != BloodStage.FINAL || !containsIgnoreCase(record.getFinalTargets(), character)) {
                continue;
            }
            boolean current = character.equalsIgnoreCase(record.currentTarget());
            record.getFinalTargets().removeIf(id -> id.equalsIgnoreCase(character));
            if (current) {
                fail(record);
            }
            BloodRecords.save(record);
        }
    }

    private static void fail(BloodRecord record) {
        record.setNextFinalAt(clock.getAsLong() + HuntConfig.failCooldownMillis());
        record.setPendingBacklash(true);
        Magic.plugin.getLogger().info("[Magic] Character " + record.getCharacterId() + " lost a final target");
        Player mage = online(record);
        if (mage != null) {
            backlash(mage, record);
        }
    }

    /** The failed sacrifice strikes back: a healable injury and the voice's scorn. */
    static void backlash(Player mage, BloodRecord record) {
        if (!record.isPendingBacklash()) {
            return;
        }
        record.setPendingBacklash(false);
        RpCharactersBridge.applyHealingInjury(mage);
        BloodFx.play("backlash", mage.getLocation(), mage);
        WhisperService.speak(mage, WhisperService.speaker(record), Messages.getRaw("blood.final.failed"));
        BloodRecords.save(record);
    }

    /**
     * Ten-minute check for an online mage in the finals: pending backlash, targets that died or
     * vanished while nobody watched, long-absent targets, refills, and naming once the wait is over.
     */
    public static void check(Player mage) {
        BloodRecord record = BloodRecords.get(AltarService.currentCharacter(mage));
        if (record == null || record.getStage() != BloodStage.FINAL) {
            return;
        }
        backlash(mage, record);
        String before = record.currentTarget();
        long now = clock.getAsLong();
        boolean changed = false;
        for (String target : new ArrayList<>(record.getFinalTargets())) {
            Optional<CharacterInfo> info = info(target);
            boolean gone = info.isEmpty() || info.get().status() != CharacterStatus.ALIVE;
            boolean absent = info.isPresent() && info.get().lastPlayedAt() != null
                    && now - info.get().lastPlayedAt().toEpochMilli() > HuntConfig.inactiveMillis();
            if (gone || absent) {
                record.getFinalTargets().remove(target);
                changed = true;
                if (gone && target.equals(before) && info.isPresent()) {
                    record.setNextFinalAt(now + HuntConfig.failCooldownMillis());
                    record.setPendingBacklash(true);
                    backlash(mage, record);
                }
            }
        }
        if (now >= record.getNextFinalAt()) {
            changed |= refill(mage, record);
        }
        if (changed) {
            BloodRecords.save(record);
        }
        String after = record.currentTarget();
        if (after != null && !after.equals(before) && now >= record.getNextFinalAt()) {
            name(mage, record);
        }
    }

    private static Optional<CharacterInfo> info(String character) {
        try {
            return Characters.info(UUID.fromString(character));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static Player online(BloodRecord record) {
        if (record.getOwnerUuid() == null) {
            return null;
        }
        Player player = Bukkit.getPlayer(UUID.fromString(record.getOwnerUuid()));
        return player != null && record.getCharacterId().equalsIgnoreCase(AltarService.currentCharacter(player))
                ? player : null;
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        for (String entry : list) {
            if (entry.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    /** Staff: replaces the named target with a fresh pick, or with a given character. */
    public static void reroll(Player mage, BloodRecord record) {
        Set<String> exclude = new HashSet<>();
        if (!record.getFinalTargets().isEmpty()) {
            exclude.add(record.getFinalTargets().remove(0));
        }
        refill(mage, record, exclude);
        BloodRecords.save(record);
        name(mage, record);
    }

    public static void set(Player mage, BloodRecord record, String character) {
        record.getFinalTargets().removeIf(id -> id.equalsIgnoreCase(character));
        record.getFinalTargets().add(0, character.toLowerCase(Locale.ROOT));
        record.setNextFinalAt(0);
        BloodRecords.save(record);
        name(mage, record);
    }

    /** Staff: counts the named target as done without a death, e.g. when a bug blocked it. */
    public static void skip(Player mage, BloodRecord record) {
        String current = record.currentTarget();
        if (current != null) {
            record.getSacrifices().add(new BloodRecord.Sacrifice(current, null, "SKIPPED", 0, clock.getAsLong()));
        }
        completed(mage, record);
    }
}
