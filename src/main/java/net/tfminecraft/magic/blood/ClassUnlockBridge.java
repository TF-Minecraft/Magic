package net.tfminecraft.magic.blood;

import org.bukkit.entity.Player;

import net.tfminecraft.magic.Magic;

/**
 * Puts the player's active character into a hidden class through RPCharacters' ClassUnlocks (the
 * character is bound to it; players never pick these classes themselves), and
 * sets the class's own skill-point pool through ClassSkillPoints. Reached by reflection so Magic also
 * runs against RPCharacters versions without them; then staff do it by hand, as the log says.
 */
public final class ClassUnlockBridge {

    static String unlocksClass = "net.tfminecraft.rpcharacters.classpick.ClassUnlocks";
    static String skillPointsClass = "net.tfminecraft.rpcharacters.classpick.ClassSkillPoints";

    private ClassUnlockBridge() {}

    public static boolean assign(Player player, String classId) {
        if (classId == null) {
            return false;
        }
        try {
            Object result = Class.forName(unlocksClass).getMethod("assign", Player.class, String.class)
                    .invoke(null, player, classId);
            if (Boolean.TRUE.equals(result)) {
                return true;
            }
        } catch (ReflectiveOperationException | LinkageError ex) {
            // Falls through to the warning below.
        }
        Magic.plugin.getLogger().warning("[Magic] Could not assign class " + classId + " to "
                + player.getName() + "; assign it by hand (/rpcharacter admin assignclass)");
        return false;
    }

    /** The class's own skill-point total on the active character; -1 when it has none or RPCharacters can't say. */
    static int skillPoints(Player player, String classId) {
        try {
            Object result = Class.forName(skillPointsClass).getMethod("get", Player.class, String.class)
                    .invoke(null, player, classId);
            return result instanceof Integer total ? total : -1;
        } catch (ReflectiveOperationException | LinkageError ex) {
            return -1;
        }
    }

    /** Gives the class its own skill-point pool with this lifetime total on the active character. */
    static boolean setSkillPoints(Player player, String classId, int total) {
        try {
            Object result = Class.forName(skillPointsClass).getMethod("set", Player.class, String.class, int.class)
                    .invoke(null, player, classId, total);
            if (Boolean.TRUE.equals(result)) {
                return true;
            }
        } catch (ReflectiveOperationException | LinkageError ex) {
            // Falls through to the warning below.
        }
        Magic.plugin.getLogger().warning("[Magic] Could not set the " + classId + " skill points of "
                + player.getName() + " to " + total + "; RPCharacters needs ClassSkillPoints");
        return false;
    }
}
