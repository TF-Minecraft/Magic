package net.tfminecraft.magic.integration;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.rpcharacters.identity.DisplayIdentityService;
import net.tfminecraft.rpcharacters.permadeath.PermadeathService;
import net.tfminecraft.rpcharacters.permadeath.PermakillCause;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeRegistry;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeTierDef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.Characters;

public final class RpCharactersBridge {

    static Supplier<Object> knockoutManager = RPCharacters::getPvpKnockoutManager;

    private RpCharactersBridge() {}

    public static boolean isAvailable() {
        return Characters.available();
    }

    public static RPCharacter getActiveCharacter(Player player) {
        if (player == null) return null;
        CharacterSession session = Characters.currentSession(player).orElse(null);
        if (session == null) return null;
        RPCharacter character = RPCharacters.getActiveCharacter(player);
        return character != null && session.ref().characterId().toString().equalsIgnoreCase(character.getId())
                ? character : null;
    }

    /** The character's real name in its name colour, without masks or aliases. */
    public static String resolveRealName(RPCharacter character) {
        if (character == null) {
            return "";
        }
        return DisplayIdentityService.colourPlain(character.getName(), character.getNameColour());
    }

    /** Counted RP exchanges of the player's active character with each other character id. */
    public static Map<String, Integer> conversationCounts(Player player) {
        RPCharacter character = getActiveCharacter(player);
        return character != null ? new HashMap<>(character.getConversationCounts()) : Map.of();
    }

    /** The character id the active character's will names as heir, or null. */
    public static String willHeir(Player player) {
        RPCharacter character = getActiveCharacter(player);
        return character != null && character.getWill().named() ? character.getWill().heir().toString() : null;
    }

    /** Whether the player lies knocked out. */
    public static boolean isDown(Player player) {
        var knockouts = RPCharacters.getPvpKnockoutManager();
        return knockouts != null && knockouts.isDown(player);
    }

    /**
     * Moves a knocked-out player and keeps them down there. RPCharacters without moveDowned holds a
     * knockout where it happened; there the player is only teleported and its freeze pulls them back.
     */
    public static boolean moveDowned(Player player, Location to) {
        Object knockouts = knockoutManager.get();
        if (knockouts != null) {
            try {
                Object moved = knockouts.getClass().getMethod("moveDowned", Player.class, Location.class)
                        .invoke(knockouts, player, to);
                return Boolean.TRUE.equals(moved);
            } catch (ReflectiveOperationException ex) {
                // Older RPCharacters: a plain teleport below.
            }
        }
        return player.teleport(to);
    }

    /** Seconds since the active character was created, and its online playtime; 0 without one. */
    public static long characterAgeSeconds(Player player) {
        RPCharacter character = getActiveCharacter(player);
        return character != null ? RPCharacters.getCharacterAgeSeconds(character) : 0;
    }

    public static long playtimeSeconds(Player player) {
        RPCharacter character = getActiveCharacter(player);
        return character != null ? character.getOnlinePlaytimeSeconds() : 0;
    }

    /** Kills the victim's active character for good as a sacrifice by the mage. */
    public static boolean sacrifice(Player victim, Player mage) {
        RPCharacter character = getActiveCharacter(victim);
        return character != null && PermadeathService.killCharacter(victim, character, PermakillCause.SACRIFICE, mage);
    }

    /** Gives the player's active character a random healable injury; false without one. */
    public static boolean applyHealingInjury(Player player) {
        RPCharacter character = getActiveCharacter(player);
        return character != null && PermadeathService.applyRandomInjury(player, character);
    }

    /** Gives the player's active character a random permanent injury; false without one. */
    public static boolean applyPermanentInjury(Player player) {
        RPCharacter character = getActiveCharacter(player);
        return character != null && PermadeathService.applyRandomPermanentInjury(player, character);
    }

    /**
     * @return false when required consequences are unavailable or permanent death was refused
     */
    public static boolean applySacrificeTier(Player victim, Player caster, SacrificeTierDef tier) {
        if (tier == null) {
            return !SacrificeRegistry.isRequireRpCharacters();
        }
        if (!isAvailable()) {
            return !SacrificeRegistry.isRequireRpCharacters();
        }
        RPCharacter character = getActiveCharacter(victim);
        if (character == null) {
            return !SacrificeRegistry.isRequireRpCharacters();
        }
        if (tier.isPermadeath()) {
            return PermadeathService.killCharacter(victim, character, PermakillCause.SACRIFICE, caster);
        }
        String injury = tier.getInjury();
        if ("healing".equals(injury)) {
            PermadeathService.applyRandomInjury(victim, character);
        } else if ("permanent".equals(injury)) {
            PermadeathService.applyRandomPermanentInjury(victim, character);
        }
        return true;
    }
}
