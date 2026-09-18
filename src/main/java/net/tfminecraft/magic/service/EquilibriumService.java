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
            return;
        }

        double dtHours = Cache.tickHours();

        if (eq < 0) {
            double ratePerHour = EquilibriumRates.corruptionDriftPerHour(eq);
            eq -= ratePerHour * dtHours;
            eq = Math.max(GuiCache.equilibriumMin, eq);
        } else {
            eq -= EquilibriumRates.tranquilityDecayPerHour(eq) * dtHours;
            eq = Math.max(0.0, eq);
        }

        session.setEquilibrium(eq);
        if (Cache.debug) {
            Magic.plugin.getLogger().fine("[Magic] Equilibrium tick for "
                    + player.getName() + ": " + MagicNumbers.format(session.getEquilibrium()));
        }
    }
}
