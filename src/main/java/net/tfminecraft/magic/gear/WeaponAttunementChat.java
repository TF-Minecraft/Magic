package net.tfminecraft.magic.gear;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.magic.Messages;

public final class WeaponAttunementChat {

    private WeaponAttunementChat() {}

    public static void sendPostChargeSummary(Player player, ItemStack weapon) {
        if (player == null || weapon == null) {
            return;
        }
        WeaponRequirement requirement = WeaponRequirement.fromItem(weapon);
        int rift = WeaponRift.get(weapon);

        player.sendMessage("");
        player.sendMessage(Messages.get("gear.orbs.summary_title"));
        if (!requirement.hasStored()) {
            player.sendMessage(Messages.get("gear.orbs.summary_unattuned"));
        } else {
            String prefix = Messages.get("gear.orbs.summary_line_prefix");
            List<String> lines = new ArrayList<>();
            WeaponResonanceDisplay.appendElementLines(lines, requirement, prefix);
            for (String line : lines) {
                player.sendMessage(line);
            }
        }
        if (rift > 0) {
            player.sendMessage(Messages.get("gear.orbs.summary_rift", "rift", String.valueOf(rift)));
        }
        player.sendMessage("");
        if (rift > 0) {
            player.sendMessage(Messages.get("gear.orbs.summary_hint_resonance_and_clean"));
        } else {
            player.sendMessage(Messages.get("gear.orbs.summary_hint_resonance"));
        }
    }
}
