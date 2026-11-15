package net.tfminecraft.magic.blood;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import net.tfminecraft.magic.Magic;

/**
 * The permissions a path's character holds while it is played (permissions: in the wildmagic/bloodmagic config), e.g.
 * to see that path's ArmourShop skins. It follows the character, not the account: switching to another
 * character takes it away.
 */
public final class BloodPerms {

    private static final Map<UUID, PermissionAttachment> ATTACHED = new HashMap<>();

    private BloodPerms() {}

    /** Gives the player their current character's path permissions, replacing any earlier ones. */
    public static void refresh(Player player) {
        clear(player);
        BloodRecord record = BloodRecords.get(AltarService.currentCharacter(player));
        List<String> permissions = record != null ? ChoiceConfig.permissionsFor(record.getPath()) : List.of();
        if (permissions.isEmpty()) {
            return;
        }
        PermissionAttachment attachment = player.addAttachment(Magic.plugin);
        for (String permission : permissions) {
            attachment.setPermission(permission, true);
        }
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
