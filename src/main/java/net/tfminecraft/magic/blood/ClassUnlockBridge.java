package net.tfminecraft.magic.blood;

import org.bukkit.entity.Player;

import net.tfminecraft.magic.Magic;

/**
 * Unlocks a hidden class for the player's active character through RPCharacters' ClassUnlocks.
 * Reached by reflection so Magic also runs against RPCharacters versions without it; then staff
 * unlock the class by hand, as the log says.
 */
public final class ClassUnlockBridge {

    static String unlocksClass = "net.tfminecraft.rpcharacters.classpick.ClassUnlocks";

    private ClassUnlockBridge() {}

    public static boolean unlock(Player player, String classId) {
        if (classId == null) {
            return false;
        }
        try {
            Object result = Class.forName(unlocksClass).getMethod("unlock", Player.class, String.class)
                    .invoke(null, player, classId);
            if (Boolean.TRUE.equals(result)) {
                return true;
            }
        } catch (ReflectiveOperationException | LinkageError ex) {
            // Falls through to the warning below.
        }
        Magic.plugin.getLogger().warning("[Magic] Could not unlock class " + classId + " for "
                + player.getName() + "; unlock it by hand (/rpcharacter admin unlockclass)");
        return false;
    }
}
