package net.tfminecraft.magic.blood;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.lumine.mythic.lib.api.event.skill.PlayerCastSkillEvent;
import io.lumine.mythic.lib.skill.Skill;
import net.tfminecraft.magic.Messages;

/**
 * Blood spells (pool.spells) only work while the caster's blood resonance reaches their tier. A
 * refused spell is cancelled before it costs health or starts its cooldown. Characters without a
 * blood path are never refused, so staff tests of the classes keep working. Registered only with
 * MythicLib.
 */
public final class BloodSpellGate implements Listener {

    private static final long SAY_EVERY_MILLIS = 3000;
    private static final Map<UUID, Long> SAID = new HashMap<>();
    static LongSupplier clock = System::currentTimeMillis;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCast(PlayerCastSkillEvent event) {
        Player player = event.getPlayer();
        int tier = PoolConfig.tierOf(spell(event.getCast()));
        if (tier <= 0 || player == null || !BloodConfig.isEnabled()) {
            return;
        }
        BloodRecord record = BloodRecords.get(AltarService.currentCharacter(player));
        if (record == null || record.getPath() == BloodPath.NONE
                || BloodPool.resonance(record) >= PoolConfig.need(tier)) {
            return;
        }
        event.setCancelled(true);
        long now = clock.getAsLong();
        Long last = SAID.get(player.getUniqueId());
        if (last == null || now - last >= SAY_EVERY_MILLIS) {
            SAID.put(player.getUniqueId(), now);
            player.sendMessage(Messages.get("blood.pool.too_thin"));
        }
    }

    /** The cast's MythicLib skill id, or null. */
    static String spell(Skill cast) {
        return cast != null && cast.getHandler() != null ? cast.getHandler().getId() : null;
    }

    static void forget(UUID player) {
        SAID.remove(player);
    }
}
