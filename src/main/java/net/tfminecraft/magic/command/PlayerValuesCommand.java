package net.tfminecraft.magic.command;

import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.GuiCache;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.profile.MagicProfile;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.session.ResonanceSession;
import net.tfminecraft.magic.util.MagicNumbers;
import net.tfminecraft.rpcharacters.RPCharacters;

final class PlayerValuesCommand {
    private PlayerValuesCommand() {}

    static boolean handles(String root) {
        return List.of("player", "equilibrium", "castmode").contains(root.toLowerCase(Locale.ROOT));
    }

    static boolean execute(CommandSender sender, String[] args) {
        String root = args[0].toLowerCase(Locale.ROOT);
        ValueAction action = args.length >= 2 ? ValueAction.parse(args[1]) : null;
        boolean numeric = root.equals("equilibrium");
        boolean mode = root.equals("castmode");
        if (action == null || (!numeric && !List.of(ValueAction.GET, ValueAction.RESET, ValueAction.SET).contains(action))
                || (root.equals("player") && action == ValueAction.SET)
                || args.length != (action.needsAmount() ? 4 : 3)) {
            sender.sendMessage(Messages.get("values.player_usage"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(Messages.get("artifact.give.unknown_player", "player", args[2]));
            return true;
        }
        var sessions = Magic.plugin.getResonanceGuiManager().getSessionManager();
        ResonanceSession session = sessions.get(target);
        String character = sessions.getLoadedCharacterId(target);
        if (session == null || character == null) {
            sender.sendMessage(Messages.get("resonance.admin.no_character", "player", target.getName()));
            return true;
        }
        if (action != ValueAction.GET) {
            MagicProfile before = MagicProfile.fromSession(character, target.getUniqueId(), session);
            if (numeric) {
                Double amount = action.needsAmount() ? ValueAction.amount(args[3]) : Double.valueOf(0.0);
                if (amount == null || (action == ValueAction.REMOVE && amount < 0)) {
                    sender.sendMessage(Messages.get("values.invalid_amount"));
                    return true;
                }
                session.setEquilibrium(action.apply(session.getEquilibrium(), amount, Cache.defaultEquilibrium));
                session.clearEquilibriumDrift();
            } else if (mode) {
                String value = action == ValueAction.RESET ? Cache.defaultCastMode : args[3];
                if (!castModes().contains(value.toLowerCase(Locale.ROOT))) {
                    sender.sendMessage(Messages.get("values.invalid_mode"));
                    return true;
                }
                session.setCastModeId(value);
            } else {
                session.setCastModeId(Cache.defaultCastMode);
                session.setEquilibrium(Cache.defaultEquilibrium);
                session.clearEquilibriumDrift();
                for (String element : ElementRegistry.getAllIds()) {
                    session.setResonance(element, Cache.defaultResonance);
                    session.clearResonanceDrift(element);
                }
            }
            if (!persist(sender, target, session, before)) return true;
        }
        sender.sendMessage(Messages.get("values.player", "player", target.getName(), "character", character,
                "equilibrium", MagicNumbers.format(session.getEquilibrium()), "mode", session.getCastModeId()));
        if (root.equals("player")) {
            var focus = RPCharacters.getFocusService();
            sender.sendMessage(Messages.get("values.focus", "value", focus == null ? "unavailable"
                    : focus.getPoints(target) + "/" + focus.getMax()));
            for (String element : ElementRegistry.getAllIds()) {
                sender.sendMessage(Messages.get("resonance.admin.get_line", "element", element,
                        "value", MagicNumbers.format(session.getResonance(element)),
                        "max", MagicNumbers.format(ElementRegistry.getById(element).getMaxResonance())));
            }
        }
        return true;
    }

    static boolean persist(CommandSender sender, Player target, ResonanceSession session, MagicProfile before) {
        var profiles = Magic.plugin.getProfileService();
        if (profiles == null || !profiles.savePlayerChecked(target)) {
            before.applyTo(session);
            sender.sendMessage(Messages.get("values.save_failed"));
            return false;
        }
        Magic.plugin.syncSpellModifiers(target, session);
        return true;
    }

    static List<String> castModes() {
        return List.of(GuiCache.castModeLeft.getId(), GuiCache.castModeRight.getId());
    }

    static List<String> complete(String[] args) {
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "player" -> List.of("get", "reset");
                case "castmode" -> List.of("get", "set", "reset");
                default -> ValueAction.NAMES;
            };
        }
        if (args.length == 3) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("castmode") && args[1].equalsIgnoreCase("set")) {
            return castModes();
        }
        return List.of();
    }
}
