package net.tfminecraft.magic.blood;

import org.bukkit.entity.Player;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.integration.RpCharactersBridge;

/**
 * The answer to the god's question. A devotee gives part of themself (permanent injuries), skips
 * the hunt and is put into the devotee class (bound to it) with its armour at once. A mage who refuses keeps their class and
 * hunts alone; from now on only their own dark self speaks to them.
 */
public final class BloodChoice {

    private BloodChoice() {}

    /** Whether the player may answer now: their character waits at the question. */
    public static boolean canAnswer(Player player) {
        BloodRecord record = BloodRecords.get(AltarService.currentCharacter(player));
        return record != null && record.getStage() == BloodStage.CHOICE
                && BloodAltars.ofCharacter(record.getCharacterId()) != null;
    }

    /** Applies the answer; false when the player may no longer answer. */
    public static boolean answer(Player player, BloodPath path) {
        if (path == BloodPath.NONE || !canAnswer(player)) {
            return false;
        }
        BloodRecord record = BloodRecords.get(AltarService.currentCharacter(player));
        String character = record.getCharacterId();
        record.setPath(path);
        if (path == BloodPath.DEVOTEE) {
            String mark = ChoiceConfig.devoteeTrait();
            boolean marked = !mark.isBlank() && RpCharactersBridge.applyTrait(player, mark);
            ClassUnlockBridge.assign(player, ChoiceConfig.classFor(BloodPath.DEVOTEE));
            BloodArmour.give(player, BloodPath.DEVOTEE, character);
            WhisperService.tell(player, "blood.choice.devoted");
            BloodFx.play("devoted", player.getLocation(), player);
            FinalService.begin(player, record);
            BloodBoons.apply(player);
            Magic.plugin.getLogger().info("[Magic] Character " + character + " gave themself to the god ("
                    + (marked ? "marked " + mark : "no mark") + ")");
        } else {
            record.setStage(BloodStage.HUNT);
            BloodRecords.save(record);
            WhisperService.tell(player, "blood.choice.refused");
            BloodFx.play("refused", player.getLocation(), player);
            Magic.plugin.getLogger().info("[Magic] Character " + character + " refused the god");
        }
        BloodPerms.refresh(player);
        WhisperService.voiceSoon(player);
        BloodAltar altar = BloodAltars.ofCharacter(character);
        AltarService.refresh(altar);
        AltarCorruption.spread(altar, CorruptionConfig.blocksPerSacrifice());
        BloodAltars.save();
        return true;
    }
}
