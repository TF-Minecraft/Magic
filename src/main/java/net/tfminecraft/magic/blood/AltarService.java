package net.tfminecraft.magic.blood;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterStatus;
import net.tfminecraft.tlibs.character.Characters;

/** Placing, refreshing and purifying altars. */
public final class AltarService {

    private AltarService() {}

    /** The id of the player's current character, or null without one. */
    public static String currentCharacter(Player player) {
        CharacterSession session = Characters.currentSession(player).orElse(null);
        return session != null ? session.ref().characterId().toString() : null;
    }

    /**
     * Tries to set the altar item in the player's hand down on the target block.
     * Returns the message path that tells the player the outcome.
     */
    public static String place(Player player, ItemStack hand, Block target) {
        if (!BloodConfig.isEnabled()) {
            return "blood.altar.inert";
        }
        String character = currentCharacter(player);
        if (character == null || !character.equalsIgnoreCase(BloodItems.owner(hand))) {
            return "blood.altar.not_yours";
        }
        BloodRecord record = BloodRecords.get(character);
        if (record == null || !record.getStage().atLeast(BloodStage.ANIMAL)) {
            return "blood.altar.inert";
        }
        if (BloodAltars.ofCharacter(character) != null) {
            return "blood.altar.already";
        }
        AltarPlacement.Result result = AltarPlacement.check(target);
        if (result != AltarPlacement.Result.OK) {
            return "blood.altar.place." + result.name().toLowerCase(Locale.ROOT);
        }
        // Claims, factions and other protections get their say, and loggers see the altar block placed.
        BlockPlaceEvent probe = new BlockPlaceEvent(target, target.getState(), target.getRelative(BlockFace.DOWN),
                new ItemStack(BloodConfig.altarBlock()), player, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(probe);
        if (probe.isCancelled() || !probe.canBuild()) {
            return "blood.altar.place.region";
        }
        // The altar is the middle of a meditation circle: its room is shaped to hold the pedestals.
        AltarRoom.Plan room = AltarRoom.plan(target, player.getLocation().getBlock().getRelative(BlockFace.DOWN));
        if (room.verdict() != AltarRoom.Verdict.OK) {
            return "blood.altar.place.room_" + room.verdict().name().toLowerCase(Locale.ROOT);
        }
        // The chamber reaches past the altar's block: claims and installations at its corners get their say too.
        for (Block corner : AltarRoom.corners(target)) {
            BlockPlaceEvent edge = new BlockPlaceEvent(corner, corner.getState(), corner.getRelative(BlockFace.DOWN),
                    new ItemStack(Material.STONE), player, true, EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(edge);
            if (edge.isCancelled() || !edge.canBuild()) {
                return "blood.altar.place.region";
            }
        }
        room.apply();
        BloodAltar altar = BloodAltar.create(character, player.getUniqueId().toString(), target,
                player.getLocation().getYaw() + 180f, System.currentTimeMillis());
        altar.setLevel(BloodProgress.level(record));
        AltarDisplays.ensure(altar);
        AltarPedestals.place(altar, target);
        BloodAltars.add(altar);
        record.setAltarId(altar.getId());
        BloodRecords.save(record);
        hand.setAmount(hand.getAmount() - 1);
        BloodFx.play("altar_placed", altar.center(), player);
        Magic.plugin.getLogger().info("[Magic] Blood altar " + altar.getId() + " placed by character "
                + character + " at " + altar.blockKey());
        return "blood.altar.placed";
    }

    /** Re-reads the owner's progress into the altar's level and look. */
    public static void refresh(BloodAltar altar) {
        int level = BloodProgress.level(BloodRecords.get(altar.getOwnerCharacter()));
        boolean changed = level != altar.getLevel();
        if (level > altar.getLevel()) {
            BloodFx.play("altar_awakens", altar.center());
        }
        altar.setLevel(level);
        changed |= AltarDisplays.ensure(altar);
        if (changed) {
            BloodAltars.save();
        }
    }

    /** Refreshes the character's altar, if it has one. */
    public static void refreshFor(String characterId) {
        BloodAltar altar = BloodAltars.ofCharacter(characterId);
        if (altar != null) {
            refresh(altar);
        }
    }

    /** Removes the altar from the world and from its owner's record. */
    public static void purify(BloodAltar altar, String reason) {
        BloodFx.play("altar_purified", altar.center());
        AltarCorruption.restore(altar);
        AltarDisplays.remove(altar);
        AltarPedestals.remove(altar);
        // The glow above the altar goes with it.
        Block stone = altar.block();
        if (stone != null && stone.getRelative(BlockFace.UP).getType() == Material.LIGHT) {
            stone.getRelative(BlockFace.UP).setType(Material.AIR, false);
        }
        BloodAltars.remove(altar);
        BloodRecord record = BloodRecords.get(altar.getOwnerCharacter());
        if (record != null && altar.getId().equals(record.getAltarId())) {
            record.setAltarId(null);
            BloodRecords.save(record);
        }
        Magic.plugin.getLogger().info("[Magic] Blood altar " + altar.getId() + " of character "
                + altar.getOwnerCharacter() + " at " + altar.blockKey() + " purified (" + reason + ")");
    }

    /** A dead character keeps nothing: the altar purifies itself and the path is forgotten. */
    public static void onCharacterDeath(String characterId) {
        BloodAltar altar = BloodAltars.ofCharacter(characterId);
        if (altar != null) {
            purify(altar, "owner died");
        }
        if (BloodRecords.get(characterId) != null) {
            BloodRecords.delete(characterId);
            Magic.plugin.getLogger().info("[Magic] Blood path of dead character " + characterId + " cleared");
        }
    }

    /** Catches deaths missed while Magic was not listening, e.g. during a restart. */
    public static void sweepDead() {
        if (!Characters.available()) {
            return;
        }
        for (BloodAltar altar : BloodAltars.all()) {
            if (isDead(altar.getOwnerCharacter())) {
                onCharacterDeath(altar.getOwnerCharacter());
            }
        }
        for (BloodRecord record : BloodRecords.all()) {
            if (isDead(record.getCharacterId())) {
                onCharacterDeath(record.getCharacterId());
            }
        }
    }

    static boolean isDead(String characterId) {
        if (characterId == null) {
            return false;
        }
        UUID id;
        try {
            id = UUID.fromString(characterId);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        Optional<CharacterInfo> info = Characters.info(id);
        return info.isPresent() && info.get().status() == CharacterStatus.DEAD;
    }

    /** Tells a player who touched an altar what it feels like: to its owner, as far as they have come. */
    public static void touch(Player player, BloodAltar altar) {
        String character = currentCharacter(player);
        boolean owner = character != null && character.equalsIgnoreCase(altar.getOwnerCharacter());
        BloodFx.play(owner ? "altar_touch_owner" : "altar_touch_stranger", altar.center(), player);
        BloodRecord record = owner ? BloodRecords.get(character) : null;
        String key = stageKey(record);
        if (record != null && record.getStage() == BloodStage.ASCENDED) {
            key += "_" + BloodPool.state(record);
        }
        WhisperService.tell(player, owner ? "blood.altar.touch." + key : "blood.altar.stranger_touch");
    }

    /** The stage (and, once chosen, the path) a line is written for: animal, choice, hunt, final_true... */
    static String stageKey(BloodRecord record) {
        if (record == null || !record.getStage().atLeast(BloodStage.CHOICE)) {
            return "animal";
        }
        return switch (record.getStage()) {
            case CHOICE -> "choice";
            case HUNT -> "hunt";
            default -> record.getStage().name().toLowerCase(Locale.ROOT) + "_"
                    + (record.getPath() == BloodPath.DEVOTEE ? "devotee" : "true");
        };
    }

    /**
     * A player sat down (GSit's /sit or any other seat) on top of an altar: the middle of its
     * meditation circle. The stone answers its owner and chills anyone else.
     */
    public static void sat(Player player, Entity seat) {
        Block block = seat.getLocation().getBlock();
        BloodAltar altar = BloodAltars.at(block);
        if (altar == null) {
            altar = BloodAltars.at(block.getRelative(0, -1, 0));
        }
        if (altar == null) {
            return;
        }
        String character = currentCharacter(player);
        boolean owner = character != null && character.equalsIgnoreCase(altar.getOwnerCharacter());
        WhisperService.tell(player, owner ? "blood.altar.sit." + stageKey(BloodRecords.get(character))
                : "blood.altar.sit_stranger");
        BloodFx.play(owner ? "altar_sit" : "altar_touch_stranger", seat.getLocation(), player);
    }
}
