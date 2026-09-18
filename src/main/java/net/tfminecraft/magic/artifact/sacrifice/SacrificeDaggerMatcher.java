package net.tfminecraft.magic.artifact.sacrifice;

import org.bukkit.inventory.ItemStack;

import me.Plugins.TLibs.TLibs;

public final class SacrificeDaggerMatcher {

    private SacrificeDaggerMatcher() {}

    public static boolean matches(ItemStack item) {
        SacrificeDaggerDef def = SacrificeRegistry.getDagger();
        if (def == null) {
            return false;
        }
        return TLibs.getItemAPI().getChecker().checkItemWithPath(item, def.getPath());
    }
}
