package net.tfminecraft.magic.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.rpcharacters.managers.PlayerManager;
import net.tfminecraft.rpcharacters.objects.PlayerData;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.rpcharacters.identity.DisplayIdentityService;
import net.tfminecraft.rpcharacters.mail.CharacterMailTarget;
import net.tfminecraft.rpcharacters.permadeath.PermadeathService;
import net.tfminecraft.rpcharacters.permadeath.PermakillCause;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeRegistry;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeTierDef;

public final class RpCharactersBridge {

    private RpCharactersBridge() {}

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("RPCharacters");
    }

    public static RPCharacter getActiveCharacter(Player player) {
        if (!isAvailable() || player == null) {
            return null;
        }
        return RPCharacters.getActiveCharacter(player);
    }

    public static String resolveDisplayTab(Player player) {
        if (!isAvailable() || player == null) {
            return "";
        }
        return DisplayIdentityService.ensureDisplayTabWhite(DisplayIdentityService.resolveDisplayTab(player));
    }

    /** Plain character name for lore. Empty when RPCharacters is down or the id is unknown. */
    public static String resolveCharacterName(String characterId) {
        if (!isAvailable() || characterId == null || characterId.isBlank()) {
            return "";
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = PlayerManager.get(player);
            if (data == null) {
                continue;
            }
            RPCharacter character = data.getCharacterById(characterId);
            if (character != null && character.getName() != null && !character.getName().isBlank()) {
                return character.getName();
            }
        }
        for (CharacterMailTarget target : RPCharacters.listMailTargets()) {
            if (target != null
                    && characterId.equals(target.getCharacterId())
                    && target.getDisplayPlain() != null
                    && !target.getDisplayPlain().isBlank()) {
                return target.getDisplayPlain();
            }
        }
        return "";
    }

    /**
     * @return false when consequences could not run and {@code require_rpcharacters} is set
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
            PermadeathService.killCharacter(victim, character, PermakillCause.SACRIFICE, caster);
            return true;
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
