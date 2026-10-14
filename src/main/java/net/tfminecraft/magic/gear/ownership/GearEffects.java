package net.tfminecraft.magic.gear.ownership;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.tfminecraft.magic.gear.GearStationStore;
import net.tfminecraft.magic.gear.orb.GearOrbService;
import net.tfminecraft.magic.util.ItemRef;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Paper inventory saves and these files are separate durability domains; ambiguity is held. */
public final class GearEffects {
    private GearEffects() {}
    public static List<String> frame(ItemStack[] contents) {
        List<String> frame = new ArrayList<>(); for (ItemStack item : contents) frame.add(item == null || item.getType().isAir() ? null : GearPropertyStore.b64(net.tfminecraft.magic.gear.ownership.GearItems.bytes(item))); return frame;
    }
    public static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] clone = new ItemStack[contents.length]; for (int i = 0; i < contents.length; i++) clone[i] = contents[i] == null ? null : contents[i].clone(); return clone;
    }
    public static GearStationStore.Occupancy deposit(Player player, GearIdentity.Token token, Location location, ItemStack item,
            Map<String, Integer> charged, boolean rested, ItemStack[] inventoryAfter, List<String> before) {
        GearPropertyStore store = GearStationStore.property();
        if (!store.identity().current(token, player) || location == null || location.getWorld() == null || item == null) return null;
        if (!before.equals(frame(player.getInventory().getContents()))) return null;
        UUID operation = UUID.randomUUID();
        Map<Object, Object> row = new LinkedHashMap<>(); row.put("world", location.getWorld().getName()); row.put("x", location.getBlockX()); row.put("y", location.getBlockY()); row.put("z", location.getBlockZ());
        row.put("owner", token.owner().account().toString()); row.put("occupancy-id", operation.toString()); row.put("ownership", GearPropertyStore.ownership(token.owner())); row.put("effect-state", "depositing");
        row.put("item-bytes", GearPropertyStore.b64(net.tfminecraft.magic.gear.ownership.GearItems.bytes(item))); row.put("rested", rested);
        if (charged != null) { Map<Object, Object> costs = new LinkedHashMap<>(); charged.forEach((path, amount) -> costs.put(Integer.toString(costs.size()), Map.of("path", path, "amount", amount))); row.put("charged", costs); }
        row.put("deposit-intent", Map.of("operation", operation.toString(), "session", token.session(), "inventory-before", before, "inventory-after", frame(inventoryAfter)));
        try {
            store.requireUnlocked(token.owner());
            if (!store.identity().current(token, player) || !before.equals(frame(player.getInventory().getContents()))) return null;
            store.insert(row);
            if (!store.identity().current(token, player) || !before.equals(frame(player.getInventory().getContents()))) {
                var expected = store.station(operation); var cancelled = GearYaml.copy(expected.raw()); cancelled.put("effect-state", "released"); cancelled.put("effect-result", "cancelled-before-removal"); store.patch(expected, cancelled); return null;
            }
            player.getInventory().setContents(cloneContents(inventoryAfter)); player.updateInventory(); InventoryCheckpoint.save(player);
            var expected = store.station(operation); var after = GearYaml.copy(expected.raw()); after.put("effect-state", "active"); store.patch(expected, after);
            return GearStationStore.install(location, item, after);
        } catch (IOException | RuntimeException ex) { hold(operation); GearStationStore.warning(ex); return null; }
    }
    public static boolean charge(Player player, GearIdentity.Token token, Location station, ItemStack hand,
            Map<String, Double> snapshot, String element, int tier) {
        GearPropertyStore store = GearStationStore.property(); var occupancy = GearStationStore.get(station);
        if (occupancy == null || !store.identity().current(token, player)) return false;
        UUID operation = UUID.randomUUID(); List<String> before = frame(player.getInventory().getContents());
        ItemStack[] afterInventory = cloneContents(player.getInventory().getContents());
        int slot = player.getInventory().getHeldItemSlot();
        if (!java.util.Arrays.equals(net.tfminecraft.magic.gear.ownership.GearItems.bytes(hand), net.tfminecraft.magic.gear.ownership.GearItems.bytes(afterInventory[slot]))) return false;
        if (afterInventory[slot].getAmount() <= 1) afterInventory[slot] = null; else afterInventory[slot].setAmount(afterInventory[slot].getAmount() - 1);
        try {
            store.requireUnlocked(token.owner());
            if (occupancy.characterOwner() != null) store.requireUnlocked(occupancy.characterOwner());
            var expected = GearStationStore.expected(station, occupancy); if (!"active".equals(GearPropertyStore.state(expected.raw()))) return false;
            var after = GearYaml.copy(expected.raw());
            ItemStack spent = hand.clone(); spent.setAmount(1);
            Map<Object, Object> intent = new LinkedHashMap<>(); intent.put("operation", operation.toString()); intent.put("actor-account", token.owner().account().toString()); intent.put("actor-character", token.owner().character().toString()); intent.put("session", token.session()); intent.put("occupancy-id", occupancy.generation().toString()); intent.put("item-hash", GearPropertyStore.hash(net.tfminecraft.magic.gear.ownership.GearItems.bytes(occupancy.getItem()))); intent.put("owner", expected.raw().get("ownership")); intent.put("charge", GearPropertyStore.b64(net.tfminecraft.magic.gear.ownership.GearItems.bytes(spent))); intent.put("snapshot", snapshot); intent.put("element", element); intent.put("tier", tier); intent.put("inventory-before", before); intent.put("inventory-after", frame(afterInventory));
            after.put("charge-intent", intent); after.put("effect-state", "charging"); store.patch(expected, after);
            if (!store.identity().current(token, player) || !before.equals(frame(player.getInventory().getContents()))) { var cancelled = store.station(occupancy.generation()); var restored = GearYaml.copy(cancelled.raw()); restored.put("effect-state", "active"); restored.put("charge-result", "cancelled-before-removal"); store.patch(cancelled, restored); return false; }
            player.getInventory().setContents(afterInventory); player.updateInventory(); InventoryCheckpoint.save(player);
            occupancy.setOrbSessionActive(true);
            if (!GearOrbService.begin(player, station, snapshot, element, tier, token)) { occupancy.setOrbSessionActive(false); hold(occupancy.generation()); return false; }
            return true;
        } catch (IOException | RuntimeException ex) { hold(occupancy.generation()); GearStationStore.warning(ex); return false; }
    }
    public static boolean authorize(Player player, GearStationStore.Occupancy occupancy) {
        var token = GearStationStore.property().identity().current(player);
        return token.isPresent() && (player.hasPermission("magic.admin") || occupancy.characterOwner() == null || token.orElseThrow().owner().equals(occupancy.characterOwner()));
    }
    public static boolean authorize(Player player, Location station, GearStationStore.Occupancy occupancy) {
        if (!authorize(player, occupancy)) return false;
        if (occupancy.characterOwner() != null || player.hasPermission("magic.admin")) return true;
        GearIdentity.Token actor = GearOrbService.actor(station);
        return actor == null || GearStationStore.property().identity().current(player).filter(token -> token.owner().equals(actor.owner())).isPresent();
    }
    public static boolean abortReturn(Player player, Location station, GearStationStore.Occupancy occupancy, Map<String, Integer> charged) {
        GearPropertyStore store = GearStationStore.property(); var token = store.identity().current(player);
        if (token.isEmpty() || !authorize(player, station, occupancy)) return false;
        GearIdentity.Owner recipient = occupancy.characterOwner();
        if (recipient == null) return false;
        GearIdentity.Token actor = token.orElseThrow();
        if (!occupancy.isRested() && charged == null) return false;
        List<String> items = new ArrayList<>();
        if (occupancy.isRested()) items.add(GearPropertyStore.b64(net.tfminecraft.magic.gear.ownership.GearItems.bytes(occupancy.getItem())));
        else for (var cost : charged.entrySet()) {
            int remaining = cost.getValue();
            while (remaining > 0) {
                ItemStack item = ItemRef.build(cost.getKey()); if (item == null || item.getType().isAir()) return false;
                int amount = Math.min(remaining, item.getMaxStackSize()); item.setAmount(amount); remaining -= amount; items.add(GearPropertyStore.b64(net.tfminecraft.magic.gear.ownership.GearItems.bytes(item)));
            }
        }
        UUID operation = UUID.nameUUIDFromBytes((occupancy.generation() + ":abort").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            store.requireUnlocked(actor.owner());
            if (!recipient.equals(actor.owner())) store.requireUnlocked(recipient);
            if (!store.identity().current(actor, player) || GearStationStore.get(station) != occupancy) return false;
            GearStationStore.expected(station, occupancy);
            GearOrbService.abort(station);
            if (GearStationStore.takeForAbort(station) == null) return false;
            store.createReturn(recipient, operation, items, "held");
            if (!GearStationStore.acknowledgeRelease(station, occupancy.generation())) return false;
            JsonObject expected = GearPropertyStore.read(store.returnFile(recipient, operation)); JsonObject pending = expected.deepCopy(); pending.addProperty("state", items.isEmpty() ? "delivered" : "pending"); store.saveReturn(expected, pending);
            if (recipient.equals(actor.owner())) deliver(player, actor); return true;
        } catch (IOException | RuntimeException ex) { GearStationStore.warning(ex); return false; }
    }
    public static void deliver(Player player, GearIdentity.Token token) {
        GearPropertyStore store = GearStationStore.property(); if (!store.identity().current(token, player)) return;
        try {
            store.requireUnlocked(token.owner());
            for (JsonObject row : store.returns(token.owner())) {
                if (!"pending".equals(GearPropertyStore.text(row, "state")) || !store.identity().current(token, player)) continue;
                if (row.getAsJsonArray("items").isEmpty()) {
                    JsonObject completed = row.deepCopy(); completed.addProperty("state", "delivered"); store.saveReturn(row, completed); continue;
                }
                ItemStack[] after = cloneContents(player.getInventory().getStorageContents()); List<String> remaining = new ArrayList<>();
                for (var value : row.getAsJsonArray("items")) {
                    ItemStack item = net.tfminecraft.magic.gear.ownership.GearItems.item(GearPropertyStore.bytes(value.getAsString())); int amount = item.getAmount();
                    for (int i = 0; i < after.length && amount > 0; i++) if (after[i] == null || after[i].getType().isAir()) { ItemStack part = item.clone(); int count = Math.min(amount, item.getMaxStackSize()); part.setAmount(count); after[i] = part; amount -= count; }
                    if (amount > 0) { item.setAmount(amount); remaining.add(GearPropertyStore.b64(net.tfminecraft.magic.gear.ownership.GearItems.bytes(item))); }
                }
                List<String> before = frame(player.getInventory().getStorageContents()); if (before.equals(frame(after))) continue;
                JsonObject intent = row.deepCopy(); intent.addProperty("state", "delivering"); intent.addProperty("attempt", UUID.randomUUID().toString()); intent.add("inventory-before", strings(before)); intent.add("inventory-after", strings(frame(after))); intent.add("remaining", strings(remaining)); store.saveReturn(row, intent);
                if (!store.identity().current(token, player) || !before.equals(frame(player.getInventory().getStorageContents()))) { JsonObject pending = intent.deepCopy(); pending.addProperty("state", "pending"); store.saveReturn(intent, pending); continue; }
                player.getInventory().setStorageContents(after); player.updateInventory(); InventoryCheckpoint.save(player);
                JsonObject acknowledged = intent.deepCopy(); acknowledged.add("items", strings(remaining)); acknowledged.addProperty("state", remaining.isEmpty() ? "delivered" : "pending"); store.saveReturn(intent, acknowledged);
            }
        } catch (IOException | RuntimeException ex) { GearStationStore.warning(ex); }
    }
    private static JsonArray strings(List<String> values) { JsonArray array = new JsonArray(); values.forEach(array::add); return array; }
    public static void hold(UUID id) {
        try {
            var store = GearStationStore.property(); var row = store.station(id);
            if (!java.util.Arrays.asList("depositing", "charging", "releasing").contains(row.raw().get("effect-state"))) return;
            var held = GearYaml.copy(row.raw()); held.put("effect-state", "held"); store.patch(row, held);
        } catch (IOException ex) { GearStationStore.warning(ex); }
    }
}
