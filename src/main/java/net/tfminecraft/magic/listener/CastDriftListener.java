package net.tfminecraft.magic.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.lumine.mythic.lib.api.event.skill.SkillCastEvent;
import io.lumine.mythic.lib.skill.Skill;
import io.lumine.mythic.lib.skill.SkillMetadata;
import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.GuiCache;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.integration.SkillIdResolver;
import net.tfminecraft.magic.session.ResonanceSession;
import net.tfminecraft.magic.util.MagicNumbers;

public final class CastDriftListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSkillCast(SkillCastEvent event) {
        if (Magic.plugin == null) {
            return;
        }
        Skill cast = event.getCast();
        if (!SkillIdResolver.isActiveCast(cast)) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        double mana = manaSpent(event.getMetadata(), cast);
        if (mana <= 0.0) {
            return;
        }
        ResonanceSession session = Magic.plugin.getResonanceGuiManager().getSessionManager().get(player);
        if (session == null) {
            return;
        }
        double drift = MagicNumbers.round(mana / Cache.castDriftManaDivisor, 2);
        if (drift < Cache.castDriftMin) {
            drift = Cache.castDriftMin;
        }
        boolean surge = isSurge(session.getCastModeId());
        double next = session.getEquilibrium() + (surge ? -drift : drift);
        session.setEquilibrium(next);
        if (Magic.plugin.getProfileService() != null) {
            Magic.plugin.getProfileService().savePlayer(player);
        }
        Magic.plugin.syncSpellModifiers(player, session);
    }

    private static boolean isSurge(String castModeId) {
        if (castModeId == null || castModeId.isBlank()) {
            return false;
        }
        return castModeId.equalsIgnoreCase(GuiCache.castModeLeft.getId())
                || "surge".equalsIgnoreCase(castModeId);
    }

    private static double manaSpent(SkillMetadata metadata, Skill cast) {
        if (metadata != null) {
            double fromMeta = metadata.getParameter("mana");
            if (fromMeta > 0.0) {
                return fromMeta;
            }
        }
        if (cast != null) {
            double fromSkill = cast.getParameter("mana");
            if (fromSkill > 0.0) {
                return fromSkill;
            }
        }
        return 0.0;
    }
}
