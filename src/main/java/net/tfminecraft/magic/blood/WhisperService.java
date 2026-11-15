package net.tfminecraft.magic.blood;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.session.ResonanceSession;
import net.tfminecraft.magic.tick.MagicTickContext;
import net.tfminecraft.magic.util.MagicText;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.Characters;

/**
 * The whisper meter and the voices. Dark deeds fill a hidden meter; past its threshold the god
 * starts to speak, teaches the rite in order, then hands over the knife and the altar. Later
 * stages hear a line now and then. A mage who refused the god only hears their own dark self.
 */
public final class WhisperService {

    private static final long MINUTE = 60_000L;
    private static final long DAY = 86_400_000L;
    private static final long FIRST_VOICE_DELAY = MINUTE;
    private static final Map<UUID, Long> NEXT_VOICE = new HashMap<>();
    /** Keepers of animals being killed, read before Cooking's death handler deletes them (by animal). */
    private static final Map<UUID, List<HusbandryOwners.Owner>> KEEPERS_AT_DEATH = new HashMap<>();

    static LongSupplier clock = System::currentTimeMillis;
    static Random random = new Random();
    /** The player's equilibrium, NaN without a resonance session. */
    static ToDoubleFunction<Player> equilibrium = WhisperService::sessionEquilibrium;
    /** Whether a character has a fast trait (the evil fast route). */
    static Predicate<String> fastTraitOf = WhisperService::fastTrait;

    /** Who keeps a killed animal, seen from its killer. */
    enum Keeper {
        /** Nobody keeps it. */
        WILD,
        /** The killer's account is one of its keepers. */
        OWN,
        /** Other players keep it, the killer doesn't. */
        OTHERS
    }

    private WhisperService() {}

    /** Adds whisper weight to the player's current character. */
    public static void add(Player player, double amount) {
        String character = AltarService.currentCharacter(player);
        if (character != null) {
            addTo(character, player.getUniqueId().toString(), amount, player);
        }
    }

    /**
     * Adds whisper weight to a character, raised for fast-trait characters. The meter only fills
     * before the voices start. Returns true when this woke them.
     */
    public static boolean addTo(String character, String owner, double amount, Player online) {
        return addTo(character, owner, amount, online, true);
    }

    private static boolean addTo(String character, String owner, double amount, Player online, boolean multiply) {
        if (!BloodConfig.isEnabled() || amount <= 0) {
            return false;
        }
        BloodRecord record = BloodRecords.getOrCreate(character, owner);
        if (record.getStage() != BloodStage.NONE) {
            return false;
        }
        double gain = amount * (multiply && fastTraitOf.test(character) ? WhisperConfig.fastMultiplier() : 1);
        record.setWhisper(record.getWhisper() + gain);
        boolean woke = record.getWhisper() >= WhisperConfig.threshold();
        if (woke) {
            awaken(record, online);
        }
        BloodRecords.save(record);
        return woke;
    }

    /** Starts the voices for the record; the first line comes a minute later. */
    public static void awaken(BloodRecord record, Player online) {
        record.setStage(BloodStage.WHISPERED);
        if (online != null) {
            NEXT_VOICE.put(online.getUniqueId(), clock.getAsLong() + FIRST_VOICE_DELAY);
        }
        Magic.plugin.getLogger().info("[Magic] The voices woke for character " + record.getCharacterId());
    }

    static boolean fastTrait(String character) {
        try {
            return Characters.info(UUID.fromString(character)).map(CharacterInfo::traits)
                    .map(WhisperConfig::fastTrait).orElse(false);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * Killing a listed animal with a blade. Another player's animal feeds anyone's meter (fast traits
     * multiply it as usual); the killer's own only feeds a fast-trait character's, unmultiplied and up
     * to a daily cap; a wild one never does.
     */
    public static void animalKill(Player killer, Entity animal) {
        if (!BloodConfig.isEnabled() || !WhisperConfig.isAnimal(animal.getType())
                || !blade(killer.getInventory().getItemInMainHand())) {
            return;
        }
        switch (keeper(killer, animal)) {
            case OTHERS -> add(killer, WhisperConfig.animalOthers());
            case OWN -> ownAnimal(killer);
            default -> { }
        }
    }

    /**
     * Remembers the keepers of a listed animal killed with a blade, as it dies. Cooking deletes them in
     * its own death handler, which runs before Magic's at the same priority, so this runs at LOWEST.
     */
    public static void noteKeepers(Player killer, Entity animal) {
        if (BloodConfig.isEnabled() && WhisperConfig.isAnimal(animal.getType())
                && blade(killer.getInventory().getItemInMainHand())) {
            KEEPERS_AT_DEATH.put(animal.getUniqueId(), HusbandryOwners.of(animal.getUniqueId()));
        }
    }

    /** Drops the keepers noted for a death once it is over. */
    public static void forgetKeepers(Entity animal) {
        KEEPERS_AT_DEATH.remove(animal.getUniqueId());
    }

    /**
     * Cooking's husbandry keepers (as noted at the death, else read now), and a vanilla tamer,
     * decide whose animal it was.
     */
    static Keeper keeper(Player killer, Entity animal) {
        boolean kept = false;
        List<HusbandryOwners.Owner> owners = KEEPERS_AT_DEATH.get(animal.getUniqueId());
        for (HusbandryOwners.Owner owner : owners != null ? owners : HusbandryOwners.of(animal.getUniqueId())) {
            if (owner.account().equals(killer.getUniqueId())) {
                return Keeper.OWN;
            }
            kept = true;
        }
        if (animal instanceof Tameable tameable && tameable.getOwnerUniqueId() != null) {
            if (tameable.getOwnerUniqueId().equals(killer.getUniqueId())) {
                return Keeper.OWN;
            }
            kept = true;
        }
        return kept ? Keeper.OTHERS : Keeper.WILD;
    }

    /** An own animal: fast-trait characters only, never multiplied, no more than the daily cap. */
    static void ownAnimal(Player killer) {
        String character = AltarService.currentCharacter(killer);
        double amount = WhisperConfig.animalOwn();
        if (character == null || amount <= 0 || !fastTraitOf.test(character)) {
            return;
        }
        String owner = killer.getUniqueId().toString();
        BloodRecord record = BloodRecords.getOrCreate(character, owner);
        if (record.getStage() != BloodStage.NONE) {
            return;
        }
        long day = Math.floorDiv(clock.getAsLong(), DAY);
        double cap = WhisperConfig.animalOwnDailyCap();
        double gain = cap > 0 ? Math.min(amount, cap - record.getOwnAnimalWhisper(day)) : amount;
        if (gain <= 0) {
            return;
        }
        record.addOwnAnimalWhisper(day, gain);
        addTo(character, owner, gain, killer, false);
    }

    static boolean blade(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        String name = item.getType().name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE") || BloodItems.kind(item) != null;
    }

    /** No other player within the configured radius. */
    static boolean alone(Player player) {
        double radius = WhisperConfig.aloneRadius();
        for (Player other : player.getWorld().getPlayers()) {
            if (other != player && other.getLocation().distanceSquared(player.getLocation()) <= radius * radius) {
                return false;
            }
        }
        return true;
    }

    /** Runs every second from the Magic tick; real work happens once a minute and every ten. */
    static void tick(MagicTickContext context) {
        if (!BloodConfig.isEnabled() || !context.everyMinutes(1)) {
            return;
        }
        boolean tenth = context.everyMinutes(10);
        for (Player player : Bukkit.getOnlinePlayers()) {
            String character = AltarService.currentCharacter(player);
            if (character == null) {
                continue;
            }
            if (tenth) {
                tenMinutes(player, character);
            }
            BloodRecord record = BloodRecords.get(character);
            if (record != null && record.getStage() != BloodStage.NONE) {
                voiceIfDue(player, record);
            }
        }
    }

    private static void tenMinutes(Player player, String character) {
        double eq = equilibrium.applyAsDouble(player);
        if (!Double.isNaN(eq) && eq <= WhisperConfig.corruptionBelow()) {
            addTo(character, player.getUniqueId().toString(), WhisperConfig.corruption(), player);
        }
        BloodRecord record = BloodRecords.get(character);
        if (record == null) {
            return;
        }
        // The daily cap's count is kept until its day is over, even with the meter drained to 0.
        boolean changed = record.forgetOwnAnimalWhisperBefore(Math.floorDiv(clock.getAsLong(), DAY));
        if (record.getStage() == BloodStage.NONE && record.getWhisper() > 0) {
            record.setWhisper(record.getWhisper() - WhisperConfig.decayPerHour() / 6.0);
            changed = true;
        }
        if (changed) {
            BloodRecords.save(record);
        }
    }

    private static void voiceIfDue(Player player, BloodRecord record) {
        long now = clock.getAsLong();
        Long due = NEXT_VOICE.get(player.getUniqueId());
        if (due == null) {
            schedule(player, now);
            return;
        }
        if (now < due || !mayHear(player, record)) {
            return;
        }
        speakNext(player, record);
        schedule(player, now);
    }

    private static void schedule(Player player, long now) {
        int span = WhisperConfig.maxInterval() - WhisperConfig.minInterval();
        int minutes = WhisperConfig.minInterval() + (span > 0 ? random.nextInt(span + 1) : 0);
        NEXT_VOICE.put(player.getUniqueId(), now + minutes * MINUTE);
    }

    /** Says the next line for the record's stage. The teaching lines end in the gift. */
    public static void speakNext(Player player, BloodRecord record) {
        WhisperConfig.Speaker speaker = speaker(record);
        // From the knife on, the mage's own thoughts answer the god half the time.
        if ((record.getStage() == BloodStage.ANIMAL || record.getStage() == BloodStage.CHOICE)
                && !WhisperConfig.voice(WhisperConfig.Speaker.SELF).lines(record.getStage()).isEmpty()
                && random.nextBoolean()) {
            speaker = WhisperConfig.Speaker.SELF;
        }
        List<String> lines = WhisperConfig.voice(speaker).lines(record.getStage());
        if (record.getStage() == BloodStage.WHISPERED) {
            int index = record.getWhisperLine();
            if (index < lines.size()) {
                speak(player, speaker, lines.get(index));
                record.setWhisperLine(index + 1);
            }
            if (record.getWhisperLine() >= lines.size()) {
                gift(player, record);
            }
            BloodRecords.save(record);
            return;
        }
        WhisperConfig.Voice voice = WhisperConfig.voice(speaker);
        if (voice.tiered(record.getStage())) {
            lines = voice.lines(record.getStage(), record.getSpecies().size());
            if (!lines.isEmpty()) {
                speak(player, speaker, lines.get(random.nextInt(lines.size())));
            }
            return;
        }
        if (!lines.isEmpty()) {
            speak(player, speaker, lines.get(pick(lines.size(), stageProgress(record))));
        }
    }

    /**
     * Where the voices reach the mage. Once their altar stands, only in its chamber (the animal stage
     * also waits for the stone to be ready for the next kind, with no rite under way). Before that,
     * only when nobody else is near (require_alone).
     */
    static boolean mayHear(Player player, BloodRecord record) {
        BloodAltar altar = BloodAltars.ofCharacter(record.getCharacterId());
        if (altar == null) {
            return !WhisperConfig.requireAlone() || alone(player);
        }
        if (AltarRoom.lockedBy(player.getLocation().getBlock()) != altar) {
            return false;
        }
        if (record.getStage() != BloodStage.ANIMAL) {
            return true;
        }
        boolean ready = record.getLastAnimalAt() <= 0
                || clock.getAsLong() - record.getLastAnimalAt() >= AnimalConfig.cooldownMillis();
        return ready && AnimalRiteService.bindingOf(player.getUniqueId()) == null;
    }

    /**
     * A line index. Lines are written from early to late: with a stage progress (0 to 1) the pick
     * comes from a third of the list that slides along with it; without (below 0), from all of it.
     */
    static int pick(int size, double stageProgress) {
        if (stageProgress < 0) {
            return random.nextInt(size);
        }
        int window = Math.max(1, (size + 2) / 3);
        int start = (int) Math.round(Math.min(1.0, stageProgress) * (size - window));
        return start + random.nextInt(window);
    }

    /** How far through its stage the record is; -1 for stages whose lines come in any order. */
    static double stageProgress(BloodRecord record) {
        if (record.getStage() == BloodStage.ANIMAL) {
            return (double) record.getSpecies().size() / Math.max(1, AnimalConfig.speciesRequired());
        }
        return -1;
    }

    /** How far along the whole path the record is, 0 to 1: what the voices' colours follow. */
    static double pathProgress(BloodRecord record) {
        if (record == null) {
            return 0;
        }
        if (record.getStage() == BloodStage.ASCENDED) {
            return 1;
        }
        int whole = AnimalConfig.speciesRequired() + 1 + BloodConfig.huntPointsRequired() + 2 * HuntConfig.finalCount();
        return Math.min(1.0, (double) BloodProgress.of(record) / Math.max(1, whole));
    }

    /** The line in the speaker's style, coloured for how far the player's character has come. */
    static String voiced(Player player, WhisperConfig.Speaker speaker, String line) {
        WhisperConfig.Voice voice = WhisperConfig.voice(speaker);
        if (!voice.coloured()) {
            return MagicText.format(voice.style() + line);
        }
        String character = AltarService.currentCharacter(player);
        double progress = pathProgress(character != null ? BloodRecords.get(character) : null);
        return VoiceColours.gradient(line, VoiceColours.at(voice.start(), voice.end(), progress),
                MagicText.format(voice.style()));
    }

    /**
     * A blood message; one written as {god}... or {self}... is said in that voice's colours, and
     * {stone}... (narration of the stone) in the colours of whoever speaks to the mage now: the god,
     * or after refusing him, their own self.
     */
    public static void tell(Player player, String key) {
        String raw = Messages.getRaw(key);
        if (raw != null && raw.startsWith("{stone}")) {
            String character = AltarService.currentCharacter(player);
            BloodRecord record = character != null ? BloodRecords.get(character) : null;
            WhisperConfig.Speaker speaker = record != null ? speaker(record) : WhisperConfig.Speaker.GOD;
            player.sendMessage(voiced(player, speaker, raw.substring("{stone}".length())));
            return;
        }
        for (WhisperConfig.Speaker speaker : WhisperConfig.Speaker.values()) {
            String tag = "{" + speaker.name().toLowerCase(Locale.ROOT) + "}";
            if (raw != null && raw.startsWith(tag)) {
                player.sendMessage(voiced(player, speaker, raw.substring(tag.length())));
                return;
            }
        }
        player.sendMessage(Messages.get(key));
    }

    public static WhisperConfig.Speaker speaker(BloodRecord record) {
        return record.getPath() == BloodPath.TRUE ? WhisperConfig.Speaker.SELF : WhisperConfig.Speaker.GOD;
    }

    /** One line to this player alone, in the speaker's style and sound. */
    public static void speak(Player player, WhisperConfig.Speaker speaker, String line) {
        WhisperConfig.Voice voice = WhisperConfig.voice(speaker);
        player.sendMessage(voiced(player, speaker, line));
        if (!voice.sound().isBlank()) {
            player.playSound(player.getLocation(), voice.sound(), 0.6f, voice.pitch());
        }
        BloodFx.play(speaker == WhisperConfig.Speaker.SELF ? "voice_self" : "voice_god", player.getLocation(), player);
    }

    /** The voices hand over the ritual knife and, unless one stands already, the altar. */
    static void gift(Player player, BloodRecord record) {
        give(player, BloodItems.create(BloodItemKind.RITUAL_KNIFE, record.getCharacterId()));
        if (BloodAltars.ofCharacter(record.getCharacterId()) == null) {
            give(player, BloodItems.create(BloodItemKind.ALTAR, record.getCharacterId()));
        }
        record.setStage(BloodStage.ANIMAL);
        BloodFx.play("gift", player.getLocation(), player);
        Magic.plugin.getLogger().info("[Magic] Character " + record.getCharacterId()
                + " was given the ritual knife and altar");
    }

    static void give(Player player, ItemStack item) {
        for (ItemStack left : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    /** Forget the player's voice timer, e.g. after a quit or a character switch. */
    public static void forget(UUID player) {
        NEXT_VOICE.remove(player);
    }

    /** Lets the next minute tick speak to the player straight away. */
    public static void voiceSoon(Player player) {
        NEXT_VOICE.put(player.getUniqueId(), clock.getAsLong());
    }

    static Long nextVoice(UUID player) {
        return NEXT_VOICE.get(player);
    }

    public static void clear() {
        NEXT_VOICE.clear();
        KEEPERS_AT_DEATH.clear();
    }

    private static double sessionEquilibrium(Player player) {
        ResonanceSession session = Magic.plugin.getResonanceGuiManager().getSessionManager().get(player);
        return session != null ? session.getEquilibrium() : Double.NaN;
    }
}
