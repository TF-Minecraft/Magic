package net.tfminecraft.magic.blood;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.integration.RpCharactersBridge;

/**
 * Dragging a knocked-out character to the altar (hunt and finals). Sneak and right-click them with
 * your blood knife: the body trails behind you along the path you walked. Right-click the altar or
 * the body with the knife to lay it on the altar, sneak and right-click the body to let go. The drag
 * breaks when either of you leaves or switches character, the body wakes up or you go down.
 */
public final class CarryService {

    private static final class Carry {
        private final UUID victim;
        private final String mageCharacter;
        private final String victimCharacter;
        private final Deque<Location> trail = new ArrayDeque<>();

        private Carry(UUID victim, String mageCharacter, String victimCharacter) {
            this.victim = victim;
            this.mageCharacter = mageCharacter;
            this.victimCharacter = victimCharacter;
        }
    }

    private static final Map<UUID, Carry> CARRIES = new HashMap<>();
    private static final int TRAIL_LIMIT = 40;
    private static final double STEP = 0.3;
    private static BukkitTask task;

    private CarryService() {}

    /** The body follows every other tick; the one-second blood tick is too coarse for walking. */
    static void startTicking(Plugin plugin) {
        if (task != null) {
            task.cancel();
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, CarryService::tick, 2L, 2L);
    }

    /** Takes hold of a knocked-out character. Answers with the rite's bind results. */
    public static CharacterRiteService.Bind start(Player mage, Player victim) {
        String character = AltarService.currentCharacter(mage);
        CharacterRiteService.Bind ready = CharacterRiteService.readiness(mage, character,
                BloodRecords.get(character));
        if (ready != null) {
            return ready;
        }
        String victimCharacter = AltarService.currentCharacter(victim);
        if (victimCharacter == null) {
            return CharacterRiteService.Bind.NO_CHARACTER;
        }
        if (!RpCharactersBridge.isDown(victim)) {
            return CharacterRiteService.Bind.NOT_DOWN;
        }
        if (CARRIES.containsKey(mage.getUniqueId()) || carrier(victim.getUniqueId()) != null
                || CharacterRiteService.isVictim(victim.getUniqueId())) {
            return CharacterRiteService.Bind.BUSY;
        }
        CARRIES.put(mage.getUniqueId(), new Carry(victim.getUniqueId(), character, victimCharacter));
        mage.sendMessage(Messages.get("blood.carry.start"));
        victim.sendMessage(Messages.get("blood.carry.dragged"));
        return CharacterRiteService.Bind.OK;
    }

    /** The body this mage drags, if it is online. */
    public static Player carried(Player mage) {
        Carry carry = CARRIES.get(mage.getUniqueId());
        return carry != null ? Bukkit.getPlayer(carry.victim) : null;
    }

    /** The mage dragging this player, or null. */
    static UUID carrier(UUID victim) {
        for (Map.Entry<UUID, Carry> entry : CARRIES.entrySet()) {
            if (entry.getValue().victim.equals(victim)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Lets go of the body; told tells the mage. */
    public static void release(Player mage, boolean told) {
        if (CARRIES.remove(mage.getUniqueId()) != null && told) {
            mage.sendMessage(Messages.get("blood.carry.released"));
        }
    }

    /** A player left: whatever they dragged, or whoever dragged them, is let go. */
    static void drop(UUID player) {
        CARRIES.remove(player);
        UUID mage = carrier(player);
        if (mage != null) {
            CARRIES.remove(mage);
            Player holder = Bukkit.getPlayer(mage);
            if (holder != null) {
                holder.sendMessage(Messages.get("blood.carry.broken"));
            }
        }
    }

    static void tick() {
        for (Iterator<Map.Entry<UUID, Carry>> it = CARRIES.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Carry> entry = it.next();
            Carry carry = entry.getValue();
            Player mage = Bukkit.getPlayer(entry.getKey());
            Player victim = Bukkit.getPlayer(carry.victim);
            if (!holds(mage, victim, carry)) {
                it.remove();
                if (mage != null) {
                    mage.sendMessage(Messages.get("blood.carry.broken"));
                }
                continue;
            }
            follow(mage, victim, carry);
            if (HuntConfig.carrySlowness() > 0) {
                mage.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30,
                        HuntConfig.carrySlowness() - 1, false, false, false));
            }
        }
    }

    private static boolean holds(Player mage, Player victim, Carry carry) {
        return mage != null && victim != null
                && carry.mageCharacter.equalsIgnoreCase(AltarService.currentCharacter(mage))
                && carry.victimCharacter.equalsIgnoreCase(AltarService.currentCharacter(victim))
                && RpCharactersBridge.isDown(victim) && !RpCharactersBridge.isDown(mage)
                && mage.getWorld() == victim.getWorld()
                && mage.getLocation().distance(victim.getLocation()) <= HuntConfig.carryBreakDistance();
    }

    /** Puts the body on the trail point that lies the drag distance behind the mage. */
    private static void follow(Player mage, Player victim, Carry carry) {
        Location here = mage.getLocation();
        Location last = carry.trail.peekLast();
        if (last != null && last.getWorld() != here.getWorld()) {
            carry.trail.clear();
            last = null;
        }
        if (last == null || last.distanceSquared(here) >= STEP * STEP) {
            carry.trail.addLast(here.clone());
            if (carry.trail.size() > TRAIL_LIMIT) {
                carry.trail.removeFirst();
            }
        }
        Location spot = null;
        for (Iterator<Location> back = carry.trail.descendingIterator(); back.hasNext();) {
            Location point = back.next();
            if (point.distance(here) >= HuntConfig.carryDistance()) {
                spot = point;
                break;
            }
        }
        Location body = victim.getLocation();
        if (spot == null || spot.distanceSquared(body) < 0.01) {
            return;
        }
        Location to = spot.clone();
        to.setYaw(body.getYaw());
        to.setPitch(body.getPitch());
        RpCharactersBridge.moveDowned(victim, to);
    }

    public static void clear() {
        CARRIES.clear();
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
