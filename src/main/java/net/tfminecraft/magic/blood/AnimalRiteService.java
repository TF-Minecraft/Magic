package net.tfminecraft.magic.blood;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.Messages;

/**
 * Animal sacrifices. The mage binds a living animal onto their altar with their ritual knife;
 * it hangs still on the stone until the same knife kills it or it breaks free. Seven different
 * kinds, one per rolling day, taken from other keepers, finish the stage.
 */
public final class AnimalRiteService {

    public enum Bind {
        OK,
        NOT_STAGE,
        NOT_KNIFE,
        NO_ALTAR,
        NOT_SPECIES,
        KNOWN,
        COOLDOWN,
        NOT_STOLEN,
        TOO_FAR,
        BUSY
    }

    private record Bound(UUID mage, String character, String altarId, long expiresAt,
            List<HusbandryOwners.Owner> owners, boolean stolen) {}

    private static final Map<UUID, Bound> BOUND = new HashMap<>();
    static LongSupplier clock = System::currentTimeMillis;

    private AnimalRiteService() {}

    /** Tries to bind the animal onto the player's altar. */
    public static Bind bind(Player player, LivingEntity animal) {
        String character = AltarService.currentCharacter(player);
        BloodRecord record = BloodRecords.get(character);
        if (record == null || record.getStage() != BloodStage.ANIMAL) {
            return Bind.NOT_STAGE;
        }
        ItemStack knife = player.getInventory().getItemInMainHand();
        if (!BloodItems.is(knife, BloodItemKind.RITUAL_KNIFE) || !character.equalsIgnoreCase(BloodItems.owner(knife))) {
            return Bind.NOT_KNIFE;
        }
        BloodAltar altar = BloodAltars.ofCharacter(character);
        Location top = altar != null ? altar.center() : null;
        if (top == null) {
            return Bind.NO_ALTAR;
        }
        if (!AnimalConfig.isSpecies(animal.getType())) {
            return Bind.NOT_SPECIES;
        }
        if (record.hasSpecies(speciesKey(animal))) {
            return Bind.KNOWN;
        }
        long now = clock.getAsLong();
        if (record.getLastAnimalAt() > 0 && now - record.getLastAnimalAt() < AnimalConfig.cooldownMillis()) {
            return Bind.COOLDOWN;
        }
        List<HusbandryOwners.Owner> owners = HusbandryOwners.of(animal.getUniqueId());
        boolean stolen = !owners.isEmpty()
                && owners.stream().noneMatch(owner -> owner.account().equals(player.getUniqueId()));
        if (AnimalConfig.requireStolen() && !stolen) {
            return Bind.NOT_STOLEN;
        }
        if (animal.getWorld() != top.getWorld()
                || animal.getLocation().distance(top) > AnimalConfig.bindRange()) {
            return Bind.TOO_FAR;
        }
        if (bindingOf(player.getUniqueId()) != null) {
            return Bind.BUSY;
        }
        animal.setAI(false);
        animal.teleport(top.clone().add(0, 0.5, 0));
        BOUND.put(animal.getUniqueId(), new Bound(player.getUniqueId(), character, altar.getId(),
                now + AnimalConfig.bindSeconds() * 1000L, owners, stolen));
        return Bind.OK;
    }

    static String speciesKey(Entity animal) {
        return animal.getType().name().toLowerCase(Locale.ROOT);
    }

    /** The animal this mage holds on their altar right now, if any. */
    static UUID bindingOf(UUID mage) {
        for (Map.Entry<UUID, Bound> entry : BOUND.entrySet()) {
            if (entry.getValue().mage().equals(mage)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static boolean isBound(UUID animal) {
        return BOUND.containsKey(animal);
    }

    /** A bound animal died: the sacrifice counts only by the binding mage's own knife. */
    public static void onDeath(EntityDeathEvent event) {
        LivingEntity animal = event.getEntity();
        Bound bound = BOUND.remove(animal.getUniqueId());
        if (bound == null) {
            return;
        }
        Player killer = animal.getKiller();
        ItemStack knife = killer != null ? killer.getInventory().getItemInMainHand() : null;
        if (killer == null || !killer.getUniqueId().equals(bound.mage())
                || !BloodItems.is(knife, BloodItemKind.RITUAL_KNIFE)
                || !bound.character().equalsIgnoreCase(BloodItems.owner(knife))
                || !bound.character().equalsIgnoreCase(AltarService.currentCharacter(killer))) {
            Player mage = Bukkit.getPlayer(bound.mage());
            if (mage != null) {
                mage.sendMessage(Messages.get("blood.animal.spoiled"));
            }
            return;
        }
        if (AnimalConfig.clearDrops()) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
        complete(killer, bound, animal);
    }

    private static void complete(Player mage, Bound bound, LivingEntity animal) {
        BloodRecord record = BloodRecords.get(bound.character());
        BloodAltar altar = BloodAltars.byId(bound.altarId());
        if (record == null || altar == null || record.getStage() != BloodStage.ANIMAL) {
            return;
        }
        record.addSpecies(speciesKey(animal));
        record.setLastAnimalAt(clock.getAsLong());
        boolean done = record.getSpecies().size() >= AnimalConfig.speciesRequired();
        if (done) {
            record.setStage(BloodStage.CHOICE);
            WhisperService.give(mage, BloodItems.create(BloodItemKind.BLOOD_KNIFE, bound.character()));
            WhisperService.voiceSoon(mage);
        }
        BloodRecords.save(record);
        AltarService.refresh(altar);
        AltarCorruption.spread(altar, CorruptionConfig.blocksPerSacrifice());
        BloodAltars.save();
        Location center = altar.center();
        center.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, center.clone().add(0, 0.6, 0), 14, 0.3, 0.2, 0.3, 0.05);
        center.getWorld().playSound(center, "minecraft:entity.warden.heartbeat", 1f, 0.6f);
        if (bound.stolen()) {
            StolenClues.leave(animal, bound.owners(), center);
        }
        mage.sendMessage(Messages.get(done ? "blood.animal.complete" : "blood.animal.accepted"));
        Magic.plugin.getLogger().info("[Magic] Character " + bound.character() + " sacrificed a "
                + speciesKey(animal) + " (" + record.getSpecies().size() + "/" + AnimalConfig.speciesRequired() + ")");
    }

    /** Every second: a bound animal twitches on the stone, and breaks free when its time is up. */
    static void tick() {
        long now = clock.getAsLong();
        for (Iterator<Map.Entry<UUID, Bound>> it = BOUND.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Bound> entry = it.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity animal) || animal.isDead()) {
                it.remove();
                continue;
            }
            if (now >= entry.getValue().expiresAt()) {
                it.remove();
                animal.setAI(true);
                Player mage = Bukkit.getPlayer(entry.getValue().mage());
                if (mage != null) {
                    mage.sendMessage(Messages.get("blood.animal.escaped"));
                }
                continue;
            }
            animal.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, animal.getLocation().clone().add(0, 0.5, 0), 2, 0.2, 0.2, 0.2, 0);
        }
    }

    /** Lets every bound animal go, e.g. on shutdown. */
    public static void releaseAll() {
        for (UUID id : BOUND.keySet()) {
            if (Bukkit.getEntity(id) instanceof LivingEntity animal) {
                animal.setAI(true);
            }
        }
        BOUND.clear();
    }

    /** Lets this mage's animal go, e.g. when they leave. */
    public static void releaseFor(UUID mage) {
        UUID animal = bindingOf(mage);
        if (animal == null) {
            return;
        }
        BOUND.remove(animal);
        if (Bukkit.getEntity(animal) instanceof LivingEntity living) {
            living.setAI(true);
        }
    }
}
