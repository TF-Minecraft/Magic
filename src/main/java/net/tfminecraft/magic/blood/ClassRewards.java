package net.tfminecraft.magic.blood;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.magic.Magic;

/**
 * What the path's class earns from the final sacrifices (finals.class_rewards): skill points for the
 * class's own pool in RPCharacters, and MMOCore skill slots and spells. Everything is worked out again
 * from the record each time, so it is safe to repeat. Points follow the character even while it plays
 * another class; slots and spells only open while it plays the path's class, so a class picked later
 * gets everything earned so far when it is picked. Ascended characters get every listed reward.
 */
public final class ClassRewards {

    private ClassRewards() {}

    /** Brings the current character's class rewards up to date; says so only when something new arrived. */
    public static void apply(Player player) {
        BloodRecord record = BloodRecords.get(AltarService.currentCharacter(player));
        if (record == null || record.getPath() == BloodPath.NONE || !record.getStage().atLeast(BloodStage.FINAL)) {
            return;
        }
        String classId = ChoiceConfig.classFor(record.getPath());
        if (classId == null) {
            return;
        }
        int reached = record.getStage() == BloodStage.ASCENDED ? Integer.MAX_VALUE : record.getFinalsDone();
        HuntConfig.ClassReward reward = HuntConfig.classRewardsUpTo(record.getPath(), reached);
        int before = ClassUnlockBridge.skillPoints(player, classId);
        boolean gained = before != reward.skillPoints()
                && ClassUnlockBridge.setSkillPoints(player, classId, reward.skillPoints())
                && reward.skillPoints() > Math.max(0, before);
        if (Bukkit.getPluginManager().isPluginEnabled("MMOCore")) {
            gained |= MmoClassRewards.unlock(player, classId, reward.slots(), reward.spells()) > 0;
        }
        if (gained) {
            WhisperService.tell(player, "blood.class.rewarded");
        }
    }

    /** A tick later, once MMOCore and RPCharacters are done switching class or character. */
    public static void applySoon(Player player) {
        Bukkit.getScheduler().runTask(Magic.plugin, () -> {
            if (player.isOnline()) {
                apply(player);
            }
        });
    }
}
