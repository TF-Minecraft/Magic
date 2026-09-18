package net.tfminecraft.magic.attunement;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.session.ResonanceSession;

public final class AttunementCaptureService {

    private static final double EPSILON = 0.0001;

    private AttunementCaptureService() {}

    public static void credit(
            Player meditator,
            ResonanceSession session,
            String artifactId,
            String elementId,
            double gain) {
        if (session == null || elementId == null || elementId.isBlank() || gain <= EPSILON) {
            return;
        }
        ElementDef element = ElementRegistry.getById(elementId.toLowerCase(java.util.Locale.ROOT));
        if (element != null && !element.isUnlocked(meditator)) {
            return;
        }
        double before = session.getResonance(elementId);
        session.addResonance(elementId, gain);
        AuraLog.append(
                "credit player=%s artifact=%s element=%s gain=%s resonance=%s->%s",
                meditator != null ? meditator.getName() : "-",
                artifactId != null ? artifactId : "-",
                elementId,
                AuraLog.n(gain),
                AuraLog.n(before),
                AuraLog.n(session.getResonance(elementId)));
    }

    public static String displayNameOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return "";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return "";
        }
        String raw = meta.getDisplayName();
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return ChatColor.stripColor(raw);
    }
}
