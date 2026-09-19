package net.tfminecraft.magic.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.artifact.aura.AuraVessel;
import net.tfminecraft.magic.artifact.aura.AuraVessels;
import net.tfminecraft.magic.artifact.ArtifactIds;
import net.tfminecraft.magic.artifact.fillchest.ArtifactFillChestService;
import net.tfminecraft.magic.artifact.config.ArtifactRarityDef;
import net.tfminecraft.magic.artifact.config.ArtifactRarityRegistry;
import net.tfminecraft.magic.artifact.config.ArtifactTypeDef;
import net.tfminecraft.magic.artifact.config.ArtifactTypeRegistry;
import net.tfminecraft.magic.artifact.path.ArtifactPathParser;
import net.tfminecraft.magic.artifact.path.ArtifactPathSpec;
import net.tfminecraft.magic.artifact.generate.ArtifactAuraSlot;
import net.tfminecraft.magic.artifact.generate.ArtifactItemBuilder;
import net.tfminecraft.magic.artifact.generate.ArtifactRoll;
import net.tfminecraft.magic.artifact.generate.ArtifactRoller;
import net.tfminecraft.magic.artifact.shrine.ShrineChargeService;
import net.tfminecraft.magic.attunement.AuraLog;
import net.tfminecraft.magic.gear.GearRefresher;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.profile.MagicProfileService;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.session.ResonanceSession;
import net.tfminecraft.magic.util.MagicNumbers;

public final class MagicCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "reload", "open", "artifact", "fillchest", "resonance", "shrine", "refresh");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!hasAdmin(sender)) {
            sender.sendMessage(Messages.get("admin.no_permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Messages.get("admin.usage"));
            return true;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            boolean ok = Magic.plugin.reloadAll();
            sender.sendMessage(ok ? Messages.get("reload.success") : Messages.get("reload.failed"));
            return true;
        }

        if ("open".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Messages.get("open.players_only"));
                return true;
            }
            Magic.plugin.getResonanceGuiManager().tryOpen(player, true);
            return true;
        }

        if ("artifact".equalsIgnoreCase(args[0])) {
            return handleArtifact(sender, args);
        }

        if ("fillchest".equalsIgnoreCase(args[0])) {
            return handleFillchest(sender, args);
        }

        if ("resonance".equalsIgnoreCase(args[0])) {
            return handleResonance(sender, args);
        }

        if ("shrine".equalsIgnoreCase(args[0])) {
            return handleShrine(sender, args);
        }

        if ("refresh".equalsIgnoreCase(args[0])) {
            return handleRefresh(sender);
        }

        sender.sendMessage(Messages.get("admin.usage"));
        return true;
    }

    private static boolean handleRefresh(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("gear.refresh.players_only"));
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!GearRefresher.isManaged(held)) {
            sender.sendMessage(Messages.get("gear.refresh.not_gear"));
            return true;
        }
        ItemStack rebuilt = GearRefresher.refresh(held, player, true);
        if (rebuilt == null) {
            sender.sendMessage(Messages.get("gear.refresh.failed"));
            return true;
        }
        player.getInventory().setItemInMainHand(rebuilt);
        Magic.plugin.syncSpellModifiers(
                player, Magic.plugin.getResonanceGuiManager().getSessionManager().get(player));
        sender.sendMessage(Messages.get("gear.refresh.ok"));
        return true;
    }

    private static boolean handleArtifact(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.get("artifact.usage"));
            return true;
        }
        if ("roll".equalsIgnoreCase(args[1])) {
            return handleArtifactRoll(sender, args);
        }
        if ("give".equalsIgnoreCase(args[1])) {
            return handleArtifactGive(sender, args);
        }
        if ("create".equalsIgnoreCase(args[1])) {
            return handleArtifactCreate(sender, args);
        }
        if ("path".equalsIgnoreCase(args[1])) {
            return handleArtifactPath(sender, args);
        }
        if ("setfill".equalsIgnoreCase(args[1])) {
            return handleArtifactSetFill(sender, args);
        }
        sender.sendMessage(Messages.get("artifact.usage"));
        return true;
    }

    private static boolean handleFillchest(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("fillchest.players_only"));
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(Messages.get("fillchest.usage"));
            return true;
        }
        ArtifactFillChestService.Mode mode = ArtifactFillChestService.parseMode(args[1]);
        if (mode == null) {
            sender.sendMessage(Messages.get("fillchest.unknown"));
            return true;
        }
        String typeId = mode == ArtifactFillChestService.Mode.TYPE
                ? args[1].toLowerCase(Locale.ROOT)
                : null;
        ArtifactFillChestService.arm(player.getUniqueId(), mode, typeId);
        sender.sendMessage(Messages.get("fillchest.armed"));
        return true;
    }

    private static boolean handleResonance(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.get("resonance.admin.usage"));
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(Messages.get("artifact.give.unknown_player", "player", args[2]));
            return true;
        }
        String characterId = Magic.plugin.getResonanceGuiManager().getSessionManager().getLoadedCharacterId(target);
        ResonanceSession session = Magic.plugin.getResonanceGuiManager().getSessionManager().get(target);
        if (characterId == null || characterId.isBlank() || session == null) {
            sender.sendMessage(Messages.get("resonance.admin.no_character", "player", target.getName()));
            return true;
        }
        if ("get".equals(action)) {
            sender.sendMessage(Messages.get(
                    "resonance.admin.get_header",
                    "player", target.getName(),
                    "character", characterId));
            for (ElementDef element : ElementRegistry.getAll()) {
                sender.sendMessage(Messages.get(
                        "resonance.admin.get_line",
                        "element", element.getId(),
                        "value", MagicNumbers.format(session.getResonance(element.getId())),
                        "max", MagicNumbers.format(element.getMaxResonance())));
            }
            return true;
        }
        if ("reset".equals(action)) {
            String elementArg = args.length >= 4 ? args[3] : "all";
            if (!applyResonance(session, elementArg, Cache.defaultResonance, false, sender)) {
                return true;
            }
            persistResonance(target, session);
            sender.sendMessage(Messages.get(
                    "resonance.admin.reset_ok",
                    "player", target.getName(),
                    "element", elementArg.toLowerCase(Locale.ROOT)));
            return true;
        }
        if (args.length < 5) {
            sender.sendMessage(Messages.get("resonance.admin.usage"));
            return true;
        }
        String elementArg = args[3];
        Double amount = parseAmount(args[4]);
        if (amount == null) {
            sender.sendMessage(Messages.get("resonance.admin.invalid_amount"));
            return true;
        }
        if ("set".equals(action)) {
            if (!applyResonance(session, elementArg, amount, false, sender)) {
                return true;
            }
            persistResonance(target, session);
            sender.sendMessage(Messages.get(
                    "resonance.admin.set_ok",
                    "player", target.getName(),
                    "element", elementArg.toLowerCase(Locale.ROOT),
                    "value", MagicNumbers.format(amount)));
            return true;
        }
        if ("add".equals(action)) {
            if (!applyResonance(session, elementArg, amount, true, sender)) {
                return true;
            }
            persistResonance(target, session);
            sender.sendMessage(Messages.get(
                    "resonance.admin.add_ok",
                    "player", target.getName(),
                    "element", elementArg.toLowerCase(Locale.ROOT),
                    "delta", MagicNumbers.format(amount)));
            return true;
        }
        sender.sendMessage(Messages.get("resonance.admin.usage"));
        return true;
    }

    private static boolean handleShrine(CommandSender sender, String[] args) {
        if (args.length < 2 || !"fill".equalsIgnoreCase(args[1])) {
            sender.sendMessage(Messages.get("shrine.fill.usage"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("shrine.fill.players_only"));
            return true;
        }
        if (args.length != 4) {
            sender.sendMessage(Messages.get("shrine.fill.usage"));
            return true;
        }
        String elementId = args[2].toLowerCase(Locale.ROOT);
        if (!ElementRegistry.contains(elementId)) {
            sender.sendMessage(Messages.get("shrine.fill.unknown_element"));
            return true;
        }
        Double amount = parseAmount(args[3]);
        if (amount == null || amount <= 0) {
            sender.sendMessage(Messages.get("shrine.fill.invalid_amount"));
            return true;
        }
        ShrineChargeService.AdminStart result = ShrineChargeService.startAdmin(player, elementId, amount);
        switch (result) {
            case NO_PEDESTAL -> sender.sendMessage(Messages.get("shrine.fill.no_pedestal"));
            case NO_ARTIFACT -> sender.sendMessage(Messages.get("shrine.fill.no_artifact"));
            case NO_CAP -> sender.sendMessage(Messages.get("shrine.fill.no_cap", "element", elementId));
            case ALREADY_FULL -> sender.sendMessage(Messages.get(
                    "shrine.fill.already_full",
                    "element", elementId,
                    "amount", MagicNumbers.format(amount)));
            case STARTED -> sender.sendMessage(Messages.get(
                    "shrine.fill.started",
                    "element", elementId,
                    "amount", MagicNumbers.format(amount)));
        }
        return true;
    }

    private static boolean applyResonance(
            ResonanceSession session,
            String elementArg,
            double amount,
            boolean add,
            CommandSender sender) {
        if (elementArg == null) {
            sender.sendMessage(Messages.get("resonance.admin.unknown_element"));
            return false;
        }
        if ("all".equalsIgnoreCase(elementArg)) {
            for (ElementDef element : ElementRegistry.getAll()) {
                double next = add ? session.getResonance(element.getId()) + amount : amount;
                session.setResonance(element.getId(), next);
            }
            return true;
        }
        ElementDef element = ElementRegistry.getById(elementArg.toLowerCase(Locale.ROOT));
        if (element == null) {
            sender.sendMessage(Messages.get("resonance.admin.unknown_element"));
            return false;
        }
        double next = add ? session.getResonance(element.getId()) + amount : amount;
        session.setResonance(element.getId(), next);
        return true;
    }

    private static void persistResonance(Player target, ResonanceSession session) {
        MagicProfileService profiles = Magic.plugin.getProfileService();
        if (profiles != null) {
            profiles.savePlayer(target);
        }
        Magic.plugin.syncSpellModifiers(target, session);
    }

    private static Double parseAmount(String raw) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean handleArtifactCreate(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(Messages.get("artifact.create.usage"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("artifact.create.players_only"));
            return true;
        }
        Magic.plugin.getArtifactCreateGuiManager().open(player);
        return true;
    }

    private static boolean handleArtifactPath(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(Messages.get("artifact.path.usage"));
            return true;
        }
        ArtifactPathSpec spec = ArtifactPathParser.parse(args[2]);
        if (spec == null) {
            sender.sendMessage(Messages.get("artifact.path.invalid"));
            return true;
        }
        sender.sendMessage(Messages.get(
                "artifact.path.spec",
                "primary", dash(spec.getPrimaryId()),
                "rarity", dash(spec.getRarityId()),
                "extras", formatExtras(spec.getExtras())));
        return true;
    }

    private static boolean handleArtifactSetFill(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage(Messages.get("artifact.setfill.usage"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("artifact.setfill.players_only"));
            return true;
        }
        ItemStack item = heldArtifactItem(player);
        if (item == null) {
            sender.sendMessage(Messages.get("artifact.setfill.no_item"));
            return true;
        }
        if (ArtifactIds.read(item) == null) {
            sender.sendMessage(Messages.get("artifact.setfill.no_id"));
            return true;
        }
        String elementId = args[2].toLowerCase(Locale.ROOT);
        if (!ElementRegistry.contains(elementId)) {
            sender.sendMessage(Messages.get("artifact.setfill.unknown_element"));
            return true;
        }
        AuraVessel artifact = AuraVessels.fromItem(item);
        if (artifact == null || artifact.getCap(elementId) <= 0) {
            sender.sendMessage(Messages.get("artifact.setfill.no_cap", "element", elementId));
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Messages.get("artifact.setfill.invalid_amount"));
            return true;
        }
        double fillBefore = artifact.getFill(elementId);
        artifact.setFill(elementId, amount);
        artifact.write(item);
        player.getInventory().setItemInMainHand(item);
        UUID id = ArtifactIds.read(item);
        AuraLog.append(
                "setfill player=%s artifact=%s element=%s fill=%s->%s requested=%s",
                player.getName(),
                id != null ? id.toString() : "-",
                elementId,
                AuraLog.n(fillBefore),
                AuraLog.n(artifact.getFill(elementId)),
                AuraLog.n(amount));
        sender.sendMessage(Messages.get(
                "artifact.setfill.ok",
                "element", elementId,
                "fill", MagicNumbers.format(artifact.getFill(elementId))));
        return true;
    }

    private static ItemStack heldArtifactItem(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            return null;
        }
        return item;
    }

    private static String dash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String formatExtras(Map<String, Double> extras) {
        if (extras == null || extras.isEmpty()) {
            return "-";
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Double> entry : extras.entrySet()) {
            if (out.length() > 0) {
                out.append(" ");
            }
            out.append(entry.getKey());
            if (entry.getValue() != null) {
                out.append("=").append(MagicNumbers.format(entry.getValue()));
            }
        }
        return out.toString();
    }

    private static boolean handleArtifactRoll(CommandSender sender, String[] args) {
        String elementArg = args.length >= 3 ? args[2] : "random";
        String rarityArg = args.length >= 4 ? args[3] : "roll";
        ArtifactRoll roll = new ArtifactRoller().roll(elementArg, rarityArg);
        describeRoll(sender, roll);
        return true;
    }

    private static boolean handleArtifactGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.get("artifact.give.usage"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(Messages.get("artifact.give.unknown_player", "player", args[2]));
            return true;
        }
        String elementArg = args.length >= 4 ? args[3] : "random";
        String rarityArg = args.length >= 5 ? args[4] : "roll";
        ArtifactRoll roll = new ArtifactRoller().roll(elementArg, rarityArg);
        if (roll.isError()) {
            String key = roll.getErrorKey() != null ? roll.getErrorKey() : "artifact.roll.usage";
            sender.sendMessage(Messages.get(
                    key,
                    "element", roll.getPrimaryId() != null ? roll.getPrimaryId() : "",
                    "rarity", roll.getRarityId() != null ? roll.getRarityId() : ""));
            return true;
        }
        ItemStack stack = new ArtifactItemBuilder().build(roll);
        if (stack == null) {
            sender.sendMessage(Messages.get("artifact.give.failed"));
            return true;
        }
        giveOrDrop(target, stack);
        describeRoll(sender, roll);
        sender.sendMessage(Messages.get("artifact.give.ok", "player", target.getName()));
        return true;
    }

    private static void describeRoll(CommandSender sender, ArtifactRoll roll) {
        if (roll.isError()) {
            String key = roll.getErrorKey() != null ? roll.getErrorKey() : "artifact.roll.usage";
            sender.sendMessage(Messages.get(
                    key,
                    "element", roll.getPrimaryId() != null ? roll.getPrimaryId() : "",
                    "rarity", roll.getRarityId() != null ? roll.getRarityId() : ""));
            return;
        }
        List<ArtifactAuraSlot> slots = roll.getSlots();
        if (slots.isEmpty()) {
            sender.sendMessage(Messages.get("artifact.roll.usage"));
            return;
        }
        ArtifactAuraSlot primary = slots.get(0);
        ArtifactRarityDef rarity = ArtifactRarityRegistry.getById(roll.getRarityId());
        String rarityName = rarity != null ? rarity.getName() : roll.getRarityId();
        String rarityColor = rarity != null ? rarity.getColor() : "#aaaaaa";
        sender.sendMessage(Messages.get(
                "artifact.roll.header",
                "color", rarityColor,
                "rarity", rarityName,
                "element", primary.getElementId(),
                "cap", MagicNumbers.format(primary.getCap())));
        for (int i = 1; i < slots.size(); i++) {
            ArtifactAuraSlot slot = slots.get(i);
            sender.sendMessage(Messages.get(
                    "artifact.roll.secondary",
                    "element", slot.getElementId(),
                    "cap", MagicNumbers.format(slot.getCap())));
        }
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (leftover.isEmpty() || player.getWorld() == null) {
            return;
        }
        for (ItemStack extra : leftover.values()) {
            if (extra != null && !extra.getType().isAir()) {
                player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
        }
    }

    private static boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission("magic.admin") || sender.hasPermission("magic.admin.reload");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!hasAdmin(sender) || args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterPrefix(SUBCOMMANDS, args[0]);
        }
        if ("fillchest".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                List<String> options = new ArrayList<>();
                options.add("random");
                options.add("all");
                for (ArtifactTypeDef type : ArtifactTypeRegistry.getAll()) {
                    if (type.isEnabled()) {
                        options.add(type.getElementId());
                    }
                }
                return filterPrefix(options, args[1]);
            }
            return Collections.emptyList();
        }
        if ("resonance".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                return filterPrefix(List.of("get", "set", "add", "reset"), args[1]);
            }
            if (args.length == 3) {
                List<String> names = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    names.add(player.getName());
                }
                return filterPrefix(names, args[2]);
            }
            if (args.length == 4 && !"get".equalsIgnoreCase(args[1])) {
                List<String> options = new ArrayList<>(ElementRegistry.getAllIds());
                options.add("all");
                return filterPrefix(options, args[3]);
            }
            return Collections.emptyList();
        }
        if ("shrine".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                return filterPrefix(List.of("fill"), args[1]);
            }
            if (args.length == 3 && "fill".equalsIgnoreCase(args[1])) {
                return filterPrefix(ElementRegistry.getAllIds(), args[2]);
            }
            return Collections.emptyList();
        }
        if (!"artifact".equalsIgnoreCase(args[0])) {
            return Collections.emptyList();
        }
        if (args.length == 2) {
            return filterPrefix(List.of("roll", "give", "create", "path", "setfill"), args[1]);
        }
        if ("setfill".equalsIgnoreCase(args[1])) {
            if (args.length == 3) {
                return filterPrefix(ElementRegistry.getAllIds(), args[2]);
            }
            return Collections.emptyList();
        }
        if ("roll".equalsIgnoreCase(args[1])) {
            return completeRollArgs(args, 3);
        }
        if ("give".equalsIgnoreCase(args[1])) {
            if (args.length == 3) {
                List<String> names = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    names.add(player.getName());
                }
                return filterPrefix(names, args[2]);
            }
            return completeRollArgs(args, 4);
        }
        return Collections.emptyList();
    }

    private static List<String> completeRollArgs(String[] args, int elementIndex) {
        if (args.length == elementIndex) {
            List<String> options = new ArrayList<>();
            options.add("random");
            for (ArtifactTypeDef type : ArtifactTypeRegistry.getAll()) {
                if (type.isEnabled()) {
                    options.add(type.getElementId());
                }
            }
            return filterPrefix(options, args[elementIndex - 1]);
        }
        if (args.length == elementIndex + 1) {
            List<String> options = new ArrayList<>();
            options.add("roll");
            for (ArtifactRarityDef rarity : ArtifactRarityRegistry.getAll()) {
                options.add(rarity.getId());
            }
            return filterPrefix(options, args[elementIndex]);
        }
        return Collections.emptyList();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
