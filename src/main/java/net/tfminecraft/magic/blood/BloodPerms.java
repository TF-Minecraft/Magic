package net.tfminecraft.magic.blood;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import net.tfminecraft.magic.Magic;

/**
 * The permission a path's character holds while it is played (permissions: in bloodmagic.yml), e.g.
 * to see that path's ArmourShop skins. It follows the character, not the account: switching to another
 * character takes it away.
 */
public final class BloodPerms {

    private static final Map<UUID, PermissionAttachment> ATTACHED = new HashMap<>();

    private BloodPerms() {}

    /** Gives the player their current character's path permission, replacing any earlier one. */
    public static void refresh(Player player) {
        clear(player);
        BloodRecord record = BloodRecords.get(AltarService.currentCharacter(player));
        String permission = record != null ? ChoiceConfig.permissionFor(record.getPath()) : null;
        if (permission == null) {
            return;
        }
        PermissionAttachment attachment = player.addAttachment(Magic.plugin);
        attachment.setPermission(permission, true);
        ATTACHED.put(player.getUniqueId(), attachment);
    }

    /** Takes the path permission away, e.g. when the player leaves or switches character. */
    public static void clear(Player player) {
        PermissionAttachment attachment = ATTACHED.remove(player.getUniqueId());
        if (attachment != null) {
            try {
                player.removeAttachment(attachment);
            } catch (IllegalArgumentException ex) {
                // Already gone with the player's old session.
            }
        }
    }

    /** After a config reload: every online player's permission from the new names. */
    public static void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    public static void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
        ATTACHED.clear();
    }

    static boolean holds(UUID player) {
        return ATTACHED.containsKey(player);
    }
}
