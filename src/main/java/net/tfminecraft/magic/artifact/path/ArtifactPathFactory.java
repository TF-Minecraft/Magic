package net.tfminecraft.magic.artifact.path;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import me.Plugins.TLibs.Objects.API.SubAPI.ItemPathHandler;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.artifact.Artifact;
import net.tfminecraft.magic.artifact.generate.ArtifactItemBuilder;
import net.tfminecraft.magic.artifact.generate.ArtifactRoll;
import net.tfminecraft.magic.artifact.generate.ArtifactRoller;

public final class ArtifactPathFactory implements ItemPathHandler {

    public static final ArtifactPathFactory INSTANCE = new ArtifactPathFactory();

    private ArtifactPathFactory() {}

    @Override
    public ItemStack create(String path) {
        ArtifactPathSpec spec = ArtifactPathParser.parse(path);
        if (spec == null) {
            return null;
        }
        ArtifactRoll roll = new ArtifactRoller().roll(spec);
        if (roll == null || roll.isError()) {
            if (Magic.plugin != null && roll != null && roll.getErrorKey() != null) {
                Magic.plugin.getLogger().warning("[Magic] Artifact path failed: " + roll.getErrorKey());
            }
            return null;
        }
        ItemStack stack = new ArtifactItemBuilder().build(roll);
        if (stack == null || stack.getType() == Material.AIR) {
            return null;
        }
        stack.setAmount(1);
        return stack;
    }

    @Override
    public boolean matches(ItemStack item, String fullPath) {
        Artifact artifact = Artifact.fromItem(item);
        return artifact != null && artifact.hasStoredAura();
    }
}
