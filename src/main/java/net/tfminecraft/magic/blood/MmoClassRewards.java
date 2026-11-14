package net.tfminecraft.magic.blood;

import java.util.Collection;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import net.Indyuce.mmocore.api.event.PlayerChangeClassEvent;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.profess.PlayerClass;
import net.Indyuce.mmocore.player.Unlockable;
import net.tfminecraft.magic.Magic;

/** MMOCore side of {@link ClassRewards}; only loaded once MMOCore is known to be enabled. */
final class MmoClassRewards implements Listener {

    /**
     * Opens the slots and unlocks the spells while the player plays this class. MMOCore keeps them
     * with that class's data. Returns how many were new.
     */
    static int unlock(Player player, String classId, Collection<Integer> slots, Collection<String> spells) {
        if (!PlayerData.has(player)) {
            return 0;
        }
        PlayerData data = PlayerData.get(player);
        PlayerClass profess = data.getProfess();
        if (profess == null || !profess.getId().equalsIgnoreCase(classId)) {
            return 0;
        }
        int opened = 0;
        for (int number : slots) {
            opened += open(data, profess.getSkillSlot(number), classId, "slot " + number);
        }
        for (String spell : spells) {
            opened += open(data, profess.getSkill(spell), classId, "spell " + spell);
        }
        return opened;
    }

    private static int open(PlayerData data, Unlockable unlockable, String classId, String what) {
        if (unlockable == null) {
            Magic.plugin.getLogger().warning("[Magic] blood magic config finals.class_rewards: class " + classId
                    + " has no " + what);
            return 0;
        }
        return !unlockable.isUnlockedByDefault() && data.unlock(unlockable) ? 1 : 0;
    }

    /** A picked class gets what the character has earned for it. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClassChange(PlayerChangeClassEvent event) {
        ClassRewards.applySoon(event.getPlayer());
    }
}
