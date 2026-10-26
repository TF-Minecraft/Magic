package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.magic.Messages;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.Characters;

/**
 * Staff tools: /magic blood &lt;list|info|stage|path|give|whisper|voice|target|bonds|altar|reset&gt;.
 * Player targets must be online on the character being edited; altars can also be named by id.
 */
public final class BloodCommand {

    static final List<String> SUBCOMMANDS = List.of(
            "list", "info", "stage", "path", "give", "whisper", "voice", "target", "bonds", "altar", "reset");
    private static final List<String> TARGET_ACTIONS = List.of("list", "reroll", "set", "skip");
    private static final List<String> ALTAR_ACTIONS = List.of("info", "tp", "refresh", "purify");

    private BloodCommand() {}

    public static boolean execute(CommandSender sender, String[] args) {
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "list" -> list(sender);
            case "info" -> withTarget(sender, args, 3, (player, character) -> info(sender, player, character));
            case "stage" -> withTarget(sender, args, 4, (player, character) -> stage(sender, player, character, args[3]));
            case "path" -> withTarget(sender, args, 4, (player, character) -> path(sender, player, character, args[3]));
            case "give" -> withTarget(sender, args, 4, (player, character) -> give(sender, player, character, args[3]));
            case "whisper" -> whisper(sender, args);
            case "voice" -> withTarget(sender, args, 3, (player, character) -> voice(sender, player, character));
            case "target" -> target(sender, args);
            case "bonds" -> withTarget(sender, args, 3, (player, character) -> bonds(sender, player));
            case "altar" -> altar(sender, args);
            case "reset" -> withTarget(sender, args, 3, (player, character) -> reset(sender, player, character));
            default -> sender.sendMessage(Messages.get("blood.admin.usage"));
        }
        return true;
    }

    private interface TargetAction {
        void run(Player player, String character);
    }

    private static void withTarget(CommandSender sender, String[] args, int length, TargetAction action) {
        if (args.length != length) {
            sender.sendMessage(Messages.get("blood.admin.usage"));
            return;
        }
        Player player = Bukkit.getPlayerExact(args[2]);
        if (player == null) {
            sender.sendMessage(Messages.get("artifact.give.unknown_player", "player", args[2]));
            return;
        }
        String character = AltarService.currentCharacter(player);
        if (character == null) {
            sender.sendMessage(Messages.get("resonance.admin.no_character", "player", player.getName()));
            return;
        }
        action.run(player, character);
    }

    private static void list(CommandSender sender) {
        List<BloodAltar> altars = BloodAltars.all();
        sender.sendMessage(Messages.get("blood.admin.list_header", "count", String.valueOf(altars.size())));
        for (BloodAltar altar : altars) {
            sender.sendMessage(Messages.get("blood.admin.list_line", "id", altar.getId(),
                    "character", altar.getOwnerCharacter(), "where", altar.blockKey(),
                    "level", String.valueOf(altar.getLevel())));
        }
    }

    private static void info(CommandSender sender, Player player, String character) {
        BloodRecord record = BloodRecords.get(character);
        if (record == null) {
            sender.sendMessage(Messages.get("blood.admin.no_record", "player", player.getName(), "character", character));
            return;
        }
        BloodAltar altar = BloodAltars.ofCharacter(character);
        sender.sendMessage(Messages.get("blood.admin.info", "player", player.getName(), "character", character,
                "stage", record.getStage().name(), "path", record.getPath().name(),
                "whisper", String.format(Locale.ROOT, "%.1f", record.getWhisper()),
                "species", String.join(", ", record.getSpecies()),
                "species_count", String.valueOf(record.getSpecies().size()),
                "hunt", String.valueOf(record.getHuntPoints()),
                "finals", String.valueOf(record.getFinalsDone()),
                "progress", String.valueOf(BloodProgress.of(record)),
                "altar", altar != null ? altar.blockKey() + " L" + altar.getLevel() : "-"));
    }

    private static void stage(CommandSender sender, Player player, String character, String raw) {
        BloodStage stage = BloodStage.parse(raw);
        if (stage == null) {
            sender.sendMessage(Messages.get("blood.admin.bad_stage", "stages", names(BloodStage.values())));
            return;
        }
        BloodRecord record = BloodRecords.getOrCreate(character, player.getUniqueId().toString());
        record.setStage(stage);
        saved(sender, record);
        AltarService.refreshFor(character);
        sender.sendMessage(Messages.get("blood.admin.stage_set", "player", player.getName(), "stage", stage.name()));
    }

    private static void path(CommandSender sender, Player player, String character, String raw) {
        BloodPath path = BloodPath.parse(raw);
        if (path == null) {
            sender.sendMessage(Messages.get("blood.admin.bad_path", "paths", names(BloodPath.values())));
            return;
        }
        BloodRecord record = BloodRecords.getOrCreate(character, player.getUniqueId().toString());
        record.setPath(path);
        saved(sender, record);
        BloodPerms.refresh(player);
        AltarService.refreshFor(character);
        sender.sendMessage(Messages.get("blood.admin.path_set", "player", player.getName(), "path", path.name()));
    }

    private static void give(CommandSender sender, Player player, String character, String raw) {
        BloodItemKind kind = BloodItemKind.parse(raw);
        if (kind == null) {
            sender.sendMessage(Messages.get("blood.admin.bad_item"));
            return;
        }
        ItemStack item = BloodItems.create(kind, character);
        for (ItemStack left : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
        sender.sendMessage(Messages.get("blood.admin.given", "player", player.getName(), "item", kind.key()));
    }

    /** whisper &lt;player&gt; wakes the voices now; whisper &lt;player&gt; &lt;amount&gt; adds to the meter. */
    private static void whisper(CommandSender sender, String[] args) {
        if (args.length != 3 && args.length != 4) {
            sender.sendMessage(Messages.get("blood.admin.usage"));
            return;
        }
        withTarget(sender, Arrays.copyOf(args, 3), 3, (player, character) -> {
            if (args.length == 4) {
                addWhisper(sender, player, character, args[3]);
                return;
            }
            BloodRecord record = BloodRecords.getOrCreate(character, player.getUniqueId().toString());
            if (record.getStage() != BloodStage.NONE) {
                sender.sendMessage(Messages.get("blood.admin.already_whispered", "player", player.getName()));
                return;
            }
            WhisperService.awaken(record, player);
            saved(sender, record);
            sender.sendMessage(Messages.get("blood.admin.whispered", "player", player.getName()));
        });
    }

    private static void addWhisper(CommandSender sender, Player player, String character, String raw) {
        double amount;
        try {
            amount = Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Messages.get("values.invalid_amount"));
            return;
        }
        boolean woke = WhisperService.addTo(character, player.getUniqueId().toString(), amount, player);
        BloodRecord record = BloodRecords.get(character);
        sender.sendMessage(Messages.get("blood.admin.whisper_added", "player", player.getName(),
                "value", String.format(Locale.ROOT, "%.1f", record != null ? record.getWhisper() : 0),
                "woke", String.valueOf(woke)));
    }

    private static void voice(CommandSender sender, Player player, String character) {
        BloodRecord record = BloodRecords.get(character);
        if (record == null || record.getStage() == BloodStage.NONE) {
            sender.sendMessage(Messages.get("blood.admin.no_voices", "player", player.getName()));
            return;
        }
        WhisperService.speakNext(player, record);
        sender.sendMessage(Messages.get("blood.admin.voiced", "player", player.getName()));
    }

    /** target &lt;player&gt; list|reroll|skip, or target &lt;player&gt; set &lt;character name&gt;. */
    private static void target(CommandSender sender, String[] args) {
        String action = args.length >= 4 ? args[3].toLowerCase(Locale.ROOT) : "";
        boolean set = action.equals("set");
        if (!TARGET_ACTIONS.contains(action) || args.length != (set ? 5 : 4)) {
            sender.sendMessage(Messages.get("blood.admin.usage"));
            return;
        }
        withTarget(sender, Arrays.copyOf(args, 3), 3, (player, character) -> {
            BloodRecord record = BloodRecords.get(character);
            if (record == null || record.getStage() != BloodStage.FINAL) {
                sender.sendMessage(Messages.get("blood.admin.not_final", "player", player.getName()));
                return;
            }
            switch (action) {
                case "reroll" -> FinalService.reroll(player, record);
                case "skip" -> FinalService.skip(player, record);
                case "set" -> {
                    String id = livingCharacter(args[4]);
                    if (id == null) {
                        sender.sendMessage(Messages.get("blood.admin.no_character_named", "name", args[4]));
                        return;
                    }
                    FinalService.set(player, record, id);
                }
                default -> { }
            }
            List<String> names = new ArrayList<>();
            for (String id : record.getFinalTargets()) {
                names.add(FinalService.nameOf(id));
            }
            sender.sendMessage(Messages.get("blood.admin.targets", "player", player.getName(),
                    "done", String.valueOf(record.getFinalsDone()), "targets", String.join(" > ", names)));
        });
    }

    private static String livingCharacter(String name) {
        for (CharacterInfo info : Characters.findByName(name)) {
            if (info.alive()) {
                return info.ref().characterId().toString();
            }
        }
        return null;
    }

    private static void bonds(CommandSender sender, Player player) {
        List<BondService.Ranked> ranking = BondService.ranking(player);
        sender.sendMessage(Messages.get("blood.admin.bonds_header", "player", player.getName(),
                "count", String.valueOf(ranking.size())));
        for (int i = 0; i < Math.min(10, ranking.size()); i++) {
            sender.sendMessage(Messages.get("blood.admin.bonds_line", "rank", String.valueOf(i + 1),
                    "name", FinalService.nameOf(ranking.get(i).character()),
                    "score", String.format(Locale.ROOT, "%.1f", ranking.get(i).score())));
        }
    }

    private static void reset(CommandSender sender, Player player, String character) {
        BloodAltar altar = BloodAltars.ofCharacter(character);
        if (altar != null) {
            AltarService.purify(altar, "reset by " + sender.getName());
        }
        BloodRecords.delete(character);
        BloodPerms.refresh(player);
        sender.sendMessage(Messages.get("blood.admin.reset", "player", player.getName()));
    }

    private static void altar(CommandSender sender, String[] args) {
        if (args.length != 4 || !ALTAR_ACTIONS.contains(args[2].toLowerCase(Locale.ROOT))) {
            sender.sendMessage(Messages.get("blood.admin.usage"));
            return;
        }
        BloodAltar altar = BloodAltars.byId(args[3]);
        if (altar == null) {
            Player player = Bukkit.getPlayerExact(args[3]);
            String character = player != null ? AltarService.currentCharacter(player) : null;
            altar = BloodAltars.ofCharacter(character);
        }
        if (altar == null) {
            sender.sendMessage(Messages.get("blood.admin.no_altar", "target", args[3]));
            return;
        }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "info" -> sender.sendMessage(Messages.get("blood.admin.list_line", "id", altar.getId(),
                    "character", altar.getOwnerCharacter(), "where", altar.blockKey(),
                    "level", String.valueOf(altar.getLevel())));
            case "tp" -> teleport(sender, altar);
            case "refresh" -> {
                AltarService.refresh(altar);
                sender.sendMessage(Messages.get("blood.admin.altar_refreshed", "level", String.valueOf(altar.getLevel())));
            }
            default -> {
                AltarService.purify(altar, "purified by " + sender.getName());
                sender.sendMessage(Messages.get("blood.admin.altar_purified", "where", altar.blockKey()));
            }
        }
    }

    private static void teleport(CommandSender sender, BloodAltar altar) {
        Location center = altar.center();
        if (!(sender instanceof Player staff) || center == null) {
            sender.sendMessage(Messages.get("blood.admin.tp_unavailable"));
            return;
        }
        staff.teleport(center.add(0, 1, 0));
    }

    private static void saved(CommandSender sender, BloodRecord record) {
        if (!BloodRecords.save(record)) {
            sender.sendMessage(Messages.get("values.save_failed"));
        }
    }

    private static String names(Enum<?>[] values) {
        return String.join(", ", Arrays.stream(values).map(value -> value.name().toLowerCase(Locale.ROOT)).toList());
    }

    public static List<String> complete(String[] args) {
        if (args.length == 2) {
            return SUBCOMMANDS;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3) {
            return sub.equals("altar") ? ALTAR_ACTIONS
                    : sub.equals("list") ? List.of() : onlineNames();
        }
        if (args.length == 4) {
            return switch (sub) {
                case "stage" -> lower(BloodStage.values());
                case "path" -> lower(BloodPath.values());
                case "give" -> Arrays.stream(BloodItemKind.values()).map(BloodItemKind::key).toList();
                case "altar" -> altarTargets();
                case "target" -> TARGET_ACTIONS;
                default -> List.of();
            };
        }
        return List.of();
    }

    private static List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    private static List<String> lower(Enum<?>[] values) {
        return Arrays.stream(values).map(value -> value.name().toLowerCase(Locale.ROOT)).toList();
    }

    private static List<String> altarTargets() {
        List<String> out = new ArrayList<>(onlineNames());
        for (BloodAltar altar : BloodAltars.all()) {
            out.add(altar.getId());
        }
        return out;
    }
}
