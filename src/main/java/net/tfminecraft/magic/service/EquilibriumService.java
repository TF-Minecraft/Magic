package net.tfminecraft.magic.service;

import org.bukkit.entity.Player;

import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.GuiCache;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.session.ResonanceSession;
import net.tfminecraft.magic.session.ResonanceSessionManager;
import net.tfminecraft.magic.util.MagicNumbers;

public final class EquilibriumService {

    private EquilibriumService() {}

    public static void tickOnlineSessions(ResonanceSessionManager sessionManager) {
        if (sessionManager == null) {
            return;
        }
        sessionManager.forEachOnlineSession(EquilibriumService::tickSession);
    }

    private static void tickSession(Player player, ResonanceSession session) {
        double eq = session.getEquilibrium();
        if (eq == 0.0) {
            session.clearEquilibriumDrift();
            return;
        }

        double dtHours = Cache.tickHours();

        if (eq < 0) {
            double ratePerHour = EquilibriumRates.corruptionDriftPerHour(eq);
            session.driftEquilibrium(-ratePerHour * dtHours, GuiCache.equilibriumMin);
        } else {
            session.driftEquilibrium(-EquilibriumRates.tranquilityDecayPerHour(eq) * dtHours, 0.0);
        }

        if (Cache.debug) {
            Magic.plugin.getLogger().fine("[Magic] Equilibrium tick for "
                    + player.getName() + ": " + MagicNumbers.format(session.getEquilibrium()));
        }
    }
}
