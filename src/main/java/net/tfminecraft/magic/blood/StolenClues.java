package net.tfminecraft.magic.blood;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.util.ItemRef;

/**
 * A stolen animal leaves small clues: a trace where it was led away from, for a few hours, and a
 * vague note to each of its keepers saying which way the drag marks lead.
 */
public final class StolenClues {

    private static final long LEASH_MEMORY = 3_600_000L;
    private static final String[] DIRECTIONS = {
        "south", "southwest", "west", "northwest", "north", "northeast", "east", "southeast"
    };

    private record Origin(Location location, long at) {}

    private static final Map<UUID, Origin> LEASHED = new HashMap<>();
    static LongSupplier clock = System::currentTimeMillis;

    private StolenClues() {}

    public static NamespacedKey traceKey() {
        return new NamespacedKey(Magic.plugin, "blood_trace");
    }

    /** Remembers where an animal stood when someone put it on a lead. */
    public static void leashed(Entity animal) {
        LEASHED.put(animal.getUniqueId(), new Origin(animal.getLocation().clone(), clock.getAsLong()));
    }

    /** Leaves the trace and the keepers' notes for an animal taken to the altar. */
    public static void leave(LivingEntity animal, List<HusbandryOwners.Owner> owners, Location altar) {
        long now = clock.getAsLong();
        Origin origin = LEASHED.remove(animal.getUniqueId());
        String name = animal.getCustomName() != null ? animal.getCustomName()
                : "your " + animal.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String text;
        if (origin != null && origin.location().getWorld() == altar.getWorld()) {
            trace(origin.location(), now);
            String direction = Messages.get("blood.direction." + direction(origin.location(), altar));
            text = Messages.get("blood.animal.note", "animal", name, "direction", direction);
        } else {
            text = Messages.get("blood.animal.note_lost", "animal", name);
        }
        for (HusbandryOwners.Owner owner : owners) {
            BloodNotes.add(new BloodNotes.Note(owner.account().toString(),
                    owner.character() != null ? owner.character().toString() : null, text,
                    now + AnimalConfig.noteDelayMillis()));
        }
    }

    /** Eight-point compass direction from one place to another. */
    static String direction(Location from, Location to) {
        double angle = Math.toDegrees(Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ()));
        int index = (int) Math.round((angle + 360) % 360 / 45.0) % 8;
        return DIRECTIONS[index];
    }

    private static void trace(Location at, long now) {
        Location spot = at.getBlock().getLocation().clone().add(0.5, 0.02, 0.5);
        long expires = now + AnimalConfig.traceMillis();
        spot.getWorld().spawn(spot, ItemDisplay.class, display -> {
            display.setItemStack(ItemRef.buildOrFallback(AnimalConfig.traceModel(), Material.REDSTONE));
            display.setPersistent(true);
            display.setTransformation(new Transformation(new Vector3f(),
                    new AxisAngle4f((float) Math.toRadians(-90), 1f, 0f, 0f),
                    new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
            display.getPersistentDataContainer().set(traceKey(), PersistentDataType.LONG, expires);
        });
    }

    /** Removes traces whose time is up among the given entities. */
    public static void removeExpired(Collection<? extends Entity> entities) {
        long now = clock.getAsLong();
        for (Entity entity : entities) {
            Long expires = entity.getPersistentDataContainer().get(traceKey(), PersistentDataType.LONG);
            if (expires != null && now >= expires) {
                entity.remove();
            }
        }
    }

    /** Minute tick: forget old leads and clear expired traces in loaded worlds. */
    static void tick() {
        long now = clock.getAsLong();
        for (Iterator<Origin> it = LEASHED.values().iterator(); it.hasNext();) {
            if (now - it.next().at() > LEASH_MEMORY) {
                it.remove();
            }
        }
        for (World world : Bukkit.getWorlds()) {
            removeExpired(world.getEntitiesByClass(ItemDisplay.class));
        }
    }

    static boolean remembers(UUID animal) {
        return LEASHED.containsKey(animal);
    }

    public static void clear() {
        LEASHED.clear();
    }
}
