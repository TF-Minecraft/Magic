package net.tfminecraft.magic.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.Messages;

public final class ResonanceCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("magic.use")) {
            sender.sendMessage(Messages.get("open.no_permission"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("open.players_only"));
            return true;
        }
        Magic.plugin.getResonanceGuiManager().tryOpen(player, false);
        return true;
    }
}
