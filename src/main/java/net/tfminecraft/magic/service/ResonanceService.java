package net.tfminecraft.magic.service;

import org.bukkit.entity.Player;

import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.session.ResonanceSession;
import net.tfminecraft.magic.session.ResonanceSessionManager;
import net.tfminecraft.magic.util.MagicNumbers;

public final class ResonanceService {

    private static final double EPSILON = 0.0001;

    private ResonanceService() {}

    public static void tickOnlineSessions(ResonanceSessionManager sessionManager) {
        if (sessionManager == null) {
            return;
        }
        sessionManager.forEachOnlineSession(ResonanceService::tickSession);
    }

    private static void tickSession(Player player, ResonanceSession session) {
        double dtHours = Cache.tickHours();
        boolean dirty = false;
        for (ElementDef element : ElementRegistry.getAll()) {
            double ratePerHour = element.getDecayPerHour();
            if (ratePerHour == 0.0) {
                continue;
            }
            double current = session.getResonance(element.getId());
            if (ratePerHour < 0.0 && current <= EPSILON) {
                continue;
            }
            if (ratePerHour > 0.0 && !element.isUnlocked(player)) {
                continue;
            }
            session.addResonance(element.getId(), ratePerHour * dtHours);
            dirty = true;
            if (Cache.debug) {
                Magic.plugin.getLogger().fine("[Magic] Resonance tick for "
                        + player.getName() + " " + element.getId() + ": "
                        + MagicNumbers.format(session.getResonance(element.getId())));
            }
        }
        if (dirty && player != null && Magic.plugin != null && Magic.plugin.getProfileService() != null) {
            Magic.plugin.getProfileService().savePlayer(player);
        }
    }
}
