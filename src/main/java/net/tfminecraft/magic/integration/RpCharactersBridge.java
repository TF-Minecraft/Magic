package net.tfminecraft.magic.integration;

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
