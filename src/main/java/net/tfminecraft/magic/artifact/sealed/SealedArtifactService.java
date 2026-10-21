package net.tfminecraft.magic.artifact.sealed;

import java.util.Map;
import java.util.Random;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.artifact.ArtifactIds;
import net.tfminecraft.magic.artifact.generate.ArtifactItemBuilder;
import net.tfminecraft.magic.artifact.generate.ArtifactRoll;
import net.tfminecraft.magic.artifact.generate.ArtifactRoller;

/**
 * Breaks a sealed artifact: picks a rarity by the item's weights, then rolls and builds the
 * artifact the same way a Dowsing Artifact Mine drop is made.
 */
public final class SealedArtifactService {

    private final Random rng;
    private final ArtifactRoller roller;
    private final Function<ArtifactRoll, ItemStack> builder;

    public SealedArtifactService() {
        this(new Random(), new ArtifactRoller(), roll -> new ArtifactItemBuilder().build(roll));
    }

    SealedArtifactService(Random rng, ArtifactRoller roller, Function<ArtifactRoll, ItemStack> builder) {
        this.rng = rng;
        this.roller = roller;
        this.builder = builder;
    }

    /** Picks a rarity id by weight. Weights must be above zero, as the loader keeps them. */
    String pickRarity(Map<String, Double> weights) {
        // Each weight is divided by the largest, so huge weights cannot overflow the sum.
        double maximum = 0.0;
        for (double weight : weights.values()) {
            maximum = Math.max(maximum, weight);
        }
        double total = 0.0;
        for (double weight : weights.values()) {
            total += weight / maximum;
        }
        double pick = rng.nextDouble() * total;
        String last = null;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            last = entry.getKey();
            pick -= entry.getValue() / maximum;
            if (pick < 0.0) {
                return last;
            }
        }
        return last;
    }

    /**
     * Breaks the seal on the item in {@code hand}. The sealed item is only used up when the
     * artifact was made.
     *
     * @return true when an artifact was given
     */
    public boolean open(Player player, EquipmentSlot hand, SealedArtifactDef def) {
        String rarity = pickRarity(def.getRarities());
        String element = def.getElementId() == null ? SealedArtifactConfigLoader.RANDOM : def.getElementId();
        ArtifactRoll roll = roller.roll(element, rarity);
        ItemStack artifact = roll.isError() ? null : builder.apply(roll);
        if (artifact == null) {
            Magic.plugin.getLogger().warning("[Magic] Sealed artifact '" + def.getId() + "' (" + element + ", "
                    + rarity + ") could not be made: " + (roll.isError() ? roll.getErrorKey() : "build failed"));
            player.sendMessage(Messages.get("artifact.sealed.failed"));
            return false;
        }
        ItemStack sealed = player.getInventory().getItem(hand);
        if (sealed.getAmount() > 1) {
            sealed.setAmount(sealed.getAmount() - 1);
            player.getInventory().setItem(hand, sealed);
        } else {
            player.getInventory().setItem(hand, null);
        }
        for (ItemStack extra : player.getInventory().addItem(artifact).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
        player.sendMessage(Messages.get("artifact.sealed.opened"));
        String sound = SealedArtifactRegistry.getSound();
        if (!sound.isEmpty()) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
        // Console, not aura.log: aura.log is wiped on every start, and these are counted over weeks.
        Magic.plugin.getLogger().info("[Magic] Sealed artifact opened: player=" + player.getName()
                + " seal=" + def.getId() + " element=" + roll.getPrimaryId() + " rarity=" + roll.getRarityId()
                + " artifact=" + ArtifactIds.read(artifact));
        return true;
    }
}
