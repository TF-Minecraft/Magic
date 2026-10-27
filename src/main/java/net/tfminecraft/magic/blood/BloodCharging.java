package net.tfminecraft.magic.blood;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Charging an artefact in the altar's pool of blood. Its owner right-clicks the altar with it: the
 * artefact sinks into the pool, turns in the blood while the stone's heart beats, and rises again
 * glowing, back into its owner's hands to set on a pedestal. The charge is paid when it goes in (free
 * while the path goes on, from the pool once it is complete) and kept in the record as pending, so a
 * restart or a quit mid-charge still gives it back at the next session. One at a time per altar.
 */
public final class BloodCharging {

    /** What right-clicking the altar with an artefact came to. */
    public enum Start {
        STARTED,
        NOT_YOURS,
        STALE,
        FULL,
        DRY,
        BUSY
    }

    private record Charging(UUID player, String character, String id, ItemDisplay display, Location base,
            long startedAt) {}

    private static final double ABOVE = 0.6;
    private static final double SUNK = -0.05;
    private static final double RISEN = 0.95;
    private static final Map<String, Charging> BY_ALTAR = new HashMap<>();
    private static long ticks;
    private static BukkitTask task;

    private BloodCharging() {}

    static void startTicking(Plugin plugin) {
        if (task != null) {
            task.cancel();
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, BloodCharging::tick, 1L, 1L);
    }

    /** The owner sets the held artefact in their altar's pool to charge. */
    public static Start start(Player player, BloodAltar altar, ItemStack held) {
        String character = AltarService.currentCharacter(player);
        BloodRecord record = BloodRecords.get(character);
        if (record == null || !character.equalsIgnoreCase(altar.getOwnerCharacter())
                || !character.equalsIgnoreCase(BloodItems.owner(held))) {
            return Start.NOT_YOURS;
        }
        String id = BloodArtefacts.id(held);
        if (!BloodArtefacts.ids(record).contains(id)) {
            held.setAmount(held.getAmount() - 1);
            return Start.STALE;
        }
        if (BY_ALTAR.containsKey(altar.getId())) {
            return Start.BUSY;
        }
        double missing = BloodArtefacts.maxCharge(record) - record.getCharge(id);
        if (missing <= 0.0001) {
            return Start.FULL;
        }
        double paid = BloodPool.pay(record, missing);
        if (paid <= 0) {
            return Start.DRY;
        }
        ItemStack shown = held.clone();
        shown.setAmount(1);
        held.setAmount(held.getAmount() - 1);
        record.setCharge(id, record.getCharge(id) + paid);
        if (!record.getPending().contains(id)) {
            record.getPending().add(id);
        }
        BloodRecords.save(record);
        Location base = altar.center().add(0, 0.5, 0);
        ItemDisplay display = base.getWorld().spawn(base.clone().add(0, ABOVE, 0), ItemDisplay.class, spawned -> {
            spawned.setItemStack(shown);
            spawned.setPersistent(false);
            spawned.setTeleportDuration(2);
            spawned.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(0.5f, 0.5f, 0.5f), new AxisAngle4f()));
        });
        BY_ALTAR.put(altar.getId(), new Charging(player.getUniqueId(), record.getCharacterId(), id, display, base, ticks));
        MeditationConfig.charge().play(player);
        BloodFx.play("artefact_charge", base, player);
        return Start.STARTED;
    }

    /** Whether this character's artefact is in a pool right now. */
    static boolean isCharging(String character, String id) {
        for (Charging charging : BY_ALTAR.values()) {
            if (charging.character().equalsIgnoreCase(character) && charging.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** Every tick: each artefact in a pool sinks, turns in the blood, then rises and goes back to its owner. */
    static void tick() {
        ticks++;
        int total = MeditationConfig.chargeTicks();
        for (Iterator<Charging> it = BY_ALTAR.values().iterator(); it.hasNext();) {
            Charging charging = it.next();
            long elapsed = ticks - charging.startedAt();
            if (elapsed >= total) {
                it.remove();
                finish(charging);
                continue;
            }
            double t = (double) elapsed / total;
            double height = t < 0.3 ? ABOVE + (SUNK - ABOVE) * (t / 0.3)
                    : t < 0.75 ? SUNK : SUNK + (RISEN - SUNK) * ((t - 0.75) / 0.25);
            Location at = charging.base().clone().add(0, height, 0);
            at.setYaw((float) (elapsed * 6 % 360));
            charging.display().teleport(at);
            if (elapsed % 4 == 0) {
                at.getWorld().spawnParticle(Particle.DUST, charging.base(), 6, 0.25, 0.05, 0.25, 0,
                        new Particle.DustOptions(Color.fromRGB(0x8a0303), 1.2f));
            }
            if (elapsed % 20 == 10 && t < 0.75) {
                BloodFx.play("artefact_charge_beat", charging.base());
            }
        }
    }

    private static void finish(Charging charging) {
        charging.display().remove();
        BloodRecord record = BloodRecords.get(charging.character());
        Player player = Bukkit.getPlayer(charging.player());
        if (record == null || player == null
                || !charging.character().equalsIgnoreCase(AltarService.currentCharacter(player))) {
            return;
        }
        record.getPending().remove(charging.id());
        BloodRecords.save(record);
        if (BloodArtefacts.ids(record).contains(charging.id())) {
            WhisperService.give(player, BloodArtefacts.create(record, charging.id()));
            WhisperService.tell(player, "blood.artefact.charge.done");
            BloodFx.play("artefact_charged", charging.base(), player);
        }
    }

    /** Ends every charge without handing anything back (it waits as pending), e.g. on shutdown. */
    static void clear() {
        for (Charging charging : BY_ALTAR.values()) {
            charging.display().remove();
        }
        BY_ALTAR.clear();
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
