package net.tfminecraft.magic.command;

import java.util.List;
import java.util.Locale;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.artifact.Artifact;
import net.tfminecraft.magic.artifact.ArtifactCareStore;
import net.tfminecraft.magic.artifact.ArtifactIds;
import net.tfminecraft.magic.artifact.ArtifactKeys;
import net.tfminecraft.magic.artifact.ArtifactLore;
import net.tfminecraft.magic.artifact.ArtifactWear;
import net.tfminecraft.magic.attunement.AuraLog;
import net.tfminecraft.magic.gear.GearProvenance;
import net.tfminecraft.magic.gear.WeaponLore;
import net.tfminecraft.magic.gear.WeaponRequirement;
import net.tfminecraft.magic.gear.WeaponRift;
import net.tfminecraft.magic.gear.WeaponWear;
import net.tfminecraft.magic.gear.orb.OrbCache;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.util.MagicNumbers;

final class HeldValuesCommand {
    private HeldValuesCommand() {}

    static final List<String> ARTIFACT = List.of("inspect", "fill", "muffle", "wear");
    static final List<String> GEAR = List.of("inspect", "rift", "durability");

    static boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("values.players_only"));
            return true;
        }
        boolean gear = args[0].equalsIgnoreCase("gear");
        String field = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        boolean inspect = field.equals("inspect");
        ValueAction action = args.length > 2 ? ValueAction.parse(args[2]) : null;
        boolean fill = field.equals("fill");
        int amountIndex = fill ? 4 : 3;
        if (!(gear ? GEAR : ARTIFACT).contains(field)
                || (inspect ? args.length != 2 : action == null
                    || args.length != amountIndex + (action.needsAmount() ? 1 : 0))) {
            sender.sendMessage(Messages.get("values.item_usage"));
            return true;
        }
        ItemStack item = player.getInventory().getItemInMainHand().clone();
        Artifact artifact = gear ? null : Artifact.fromItem(item);
        if (gear ? !GearProvenance.isGear(item) : artifact == null || ArtifactIds.read(item) == null) {
            sender.sendMessage(Messages.get("values.wrong_item"));
            return true;
        }
        if (item.getAmount() != 1 && !inspect && action != ValueAction.GET) {
            sender.sendMessage(Messages.get("values.single_item"));
            return true;
        }
        if (inspect) {
            if (gear) {
                report(sender, "rift", WeaponRift.get(item));
                report(sender, "durability", WeaponWear.remaining(item));
                var aura = WeaponRequirement.fromItem(item).aura();
                for (String element : aura.getCappedElementIds()) {
                    report(sender, "attunement." + element, aura.getFill(element));
                }
            } else {
                sender.sendMessage(Messages.get("values.item_id", "id", ArtifactIds.read(item).toString()));
                for (String element : artifact.getCappedElementIds()) reportFill(sender, artifact, element);
                report(sender, "muffle", ArtifactCareStore.readMuffle(item) * 100);
                report(sender, "wear", ArtifactWear.readWear(item));
                report(sender, "wear_budget", ArtifactWear.budget(artifact));
                report(sender, "active_users", ArtifactCareStore.activeUserCount(item, System.currentTimeMillis()));
            }
            return true;
        }
        Double amount = action.needsAmount() ? ValueAction.amount(args[amountIndex]) : Double.valueOf(0.0);
        if (amount == null || (action == ValueAction.REMOVE && amount < 0)
                || (gear && (amount != Math.rint(amount) || Math.abs(amount) > Integer.MAX_VALUE))) {
            sender.sendMessage(Messages.get("values.invalid_amount"));
            return true;
        }
        if (fill) {
            String element = args[3].toLowerCase(Locale.ROOT);
            if (!element.equals("all") && (!ElementRegistry.contains(element) || artifact.getCap(element) <= 0)) {
                sender.sendMessage(Messages.get("artifact.setfill.no_cap", "element", element));
                return true;
            }
            for (String id : element.equals("all") ? artifact.getCappedElementIds() : List.of(element)) {
                double before = artifact.getFill(id);
                if (action != ValueAction.GET) artifact.setFill(id, action.apply(before, amount, 0));
                reportFill(sender, artifact, id);
            }
            if (action != ValueAction.GET) artifact.write(item);
        } else {
            double before;
            double max;
            double reset = 0;
            switch (field) {
                case "muffle" -> { before = ArtifactCareStore.readMuffle(item) * 100; max = 100; }
                case "wear" -> {
                    if (!ArtifactWear.enabled()) {
                        sender.sendMessage(Messages.get("values.wear_disabled"));
                        return true;
                    }
                    before = ArtifactWear.readWear(item);
                    max = ArtifactWear.budget(artifact);
                }
                case "rift" -> { before = WeaponRift.get(item); max = OrbCache.riftCap; }
                default -> {
                    before = WeaponWear.remaining(item);
                    if (before < 0) {
                        sender.sendMessage(Messages.get("values.no_durability"));
                        return true;
                    }
                    Damageable meta = (Damageable) item.getItemMeta();
                    max = meta.hasMaxDamage() ? meta.getMaxDamage() : item.getType().getMaxDurability();
                    reset = max;
                }
            }
            // Durability stops at one: MMOItems may delete a zero-durability weapon.
            double next = action == ValueAction.GET ? before : MagicNumbers.clamp(
                    action.apply(before, amount, reset), field.equals("durability") ? 1 : 0, max);
            if (action != ValueAction.GET) {
                switch (field) {
                    case "muffle" -> ArtifactCareStore.setMuffle(item, next / 100, System.currentTimeMillis());
                    case "wear" -> {
                        if (next >= max) ArtifactWear.crumble(item);
                        else {
                            var meta = item.getItemMeta();
                            meta.getPersistentDataContainer().set(ArtifactKeys.artifactWear(), PersistentDataType.DOUBLE, next);
                            item.setItemMeta(meta);
                            ArtifactLore.refreshAttune(item);
                        }
                    }
                    case "rift" -> { WeaponRift.set(item, (int) next); WeaponLore.apply(item); }
                    default -> {
                        Damageable meta = (Damageable) item.getItemMeta();
                        meta.setDamage((int) (max - next));
                        item.setItemMeta(meta);
                    }
                }
            }
            report(sender, field, next);
        }
        if (action != ValueAction.GET) {
            player.getInventory().setItemInMainHand(item);
            AuraLog.append("admin-item player=%s field=%s action=%s amount=%s", player.getName(), field, action, AuraLog.n(amount));
            if (gear) Magic.plugin.syncSpellModifiers(player,
                    Magic.plugin.getResonanceGuiManager().getSessionManager().get(player));
        }
        return true;
    }

    private static void report(CommandSender sender, String field, double value) {
        sender.sendMessage(Messages.get("values.item", "field", field, "value", MagicNumbers.format(value)));
    }

    private static void reportFill(CommandSender sender, Artifact artifact, String element) {
        sender.sendMessage(Messages.get("values.fill", "element", element,
                "value", MagicNumbers.format(artifact.getFill(element)), "max", MagicNumbers.format(artifact.getCap(element))));
    }

    static List<String> complete(String[] args) {
        if (args.length == 2) return GEAR;
        if (args.length == 3 && !args[1].equalsIgnoreCase("inspect")) return ValueAction.NAMES;
        if (args.length == 4 && args[1].equalsIgnoreCase("fill")) {
            var options = new java.util.ArrayList<>(ElementRegistry.getAllIds());
            options.add("all");
            return options;
        }
        return List.of();
    }
}
