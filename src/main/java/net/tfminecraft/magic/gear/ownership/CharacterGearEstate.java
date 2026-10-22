package net.tfminecraft.magic.gear.ownership;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.gear.GearStationStore;
import net.tfminecraft.magic.gear.orb.GearOrbService;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.Characters;
import net.tfminecraft.tlibs.character.estate.EstateDecision;
import net.tfminecraft.tlibs.character.estate.EstateParticipant;
import net.tfminecraft.tlibs.character.estate.Estates;
import net.tfminecraft.tlibs.character.estate.Holdings;
import net.tfminecraft.tlibs.character.estate.Outcome;
import org.bukkit.plugin.ServicePriority;

/** Only deposited gear and queued returns participate; profiles and focus never do. */
public final class CharacterGearEstate implements EstateParticipant {
    private final Magic plugin;
    private final Map<UUID, Map.Entry<Long, Outcome>> reported = new java.util.HashMap<>();
    public CharacterGearEstate(Magic plugin) { this.plugin = plugin; }
    public static void start(Magic plugin) {
        CharacterGearEstate participant = new CharacterGearEstate(plugin);
        plugin.getServer().getServicesManager().register(EstateParticipant.class, participant, plugin, ServicePriority.Normal);
        plugin.getServer().getScheduler().runTaskTimer(plugin, participant::reconcile, 1L, 72_000L);
    }
    public String id() { return "magic-gear"; }
    public Outcome settle(EstateDecision decision) {
        if (!Estates.guardHolds(decision)) return Outcome.retry("Current Magic gear estate decision does not hold");
        GearIdentity.Owner from = owner(decision.testator());
        // Public runs can be initiated by an entirely different character/account.
        if (GearOrbService.touches(from)) return Outcome.retry("An orb run still touches the deposited gear");
        GearPropertyStore store = GearStationStore.property();
        try {
            store.validateEstate(decision.deathId(), decision.toString(), from);
            store.requireReady(from);
            List<String> moved;
            if (decision.property().character().isEmpty()) moved = store.retained(decision.deathId(), decision.toString(), from, () -> Estates.guardHolds(decision));
            else {
                GearIdentity.Owner to = owner(decision.property().character().orElseThrow());
                if (GearOrbService.touches(to)) return Outcome.retry("Recipient gear is still being charged");
                moved = store.transfer(decision.deathId(), decision.toString(), from, to, () -> Estates.guardHolds(decision));
            }
            GearStationStore.loadViews();
            return Outcome.done(Map.of("gear", moved.size()), moved);
        } catch (GearPropertyStore.Conflict ex) { return Outcome.blocked(ex.getMessage()); }
        catch (IOException ex) { return Outcome.retry("Could not persist Magic gear estate: " + ex.getMessage()); }
    }
    public Holdings describe(UUID character) {
        try {
            var info = Characters.info(character).filter(value -> value.ref().characterId().equals(character)).orElseThrow(); GearIdentity.Owner owner = owner(info.ref());
            List<String> ids = new ArrayList<>(); var store = GearStationStore.property();
            for (var station : store.stations()) if (station.raw().get("ownership") instanceof Map<?, ?> raw && "CHARACTER".equals(raw.get("kind")) && owner.equals(GearPropertyStore.owner(station.raw())) && !"released".equals(GearPropertyStore.state(station.raw()))) ids.add("station:" + GearPropertyStore.location(station.raw()) + "@" + GearPropertyStore.id(station.raw()));
            for (var row : store.returns(owner)) if (!"delivered".equals(GearPropertyStore.text(row, "state"))) ids.add("return:" + GearPropertyStore.text(row, "id"));
            return ids.isEmpty() ? Holdings.NONE : new Holdings(Map.of("gear", ids.size()), ids);
        } catch (IOException | RuntimeException ex) { return new Holdings(Map.of("heldGear", 1), List.of("Magic gear needs review")); }
    }
    /**
     * Decided estates stay decided for good, and each settle reads the station file several times,
     * so a revision this process already reported DONE is not settled again until the next restart.
     * RETRY and BLOCKED answers, and new revisions, are still settled every hour.
     */
    void reconcile() {
        try { GearStationStore.property().migrate(); GearStationStore.loadViews(); }
        catch (IOException ex) { GearStationStore.warning(ex); }
        for (EstateDecision decision : Estates.decided()) {
            try {
                if (settled(decision) || !Estates.guardHolds(decision)) continue;
                Outcome outcome = settle(decision); var result = Map.entry(decision.revision(), outcome);
                if (!result.equals(reported.get(decision.deathId())) && Estates.report(decision.deathId(), id(), decision.revision(), outcome)) reported.put(decision.deathId(), result);
            } catch (RuntimeException ex) { plugin.getLogger().warning("[Magic] Gear estate held: " + decision.deathId() + " " + ex); }
        }
    }
    private boolean settled(EstateDecision decision) {
        var last = reported.get(decision.deathId());
        return last != null && last.getKey() == decision.revision() && last.getValue() instanceof Outcome.Done;
    }
    private static GearIdentity.Owner owner(CharacterRef ref) { return new GearIdentity.Owner(ref.ownerId(), ref.characterId()); }
}
