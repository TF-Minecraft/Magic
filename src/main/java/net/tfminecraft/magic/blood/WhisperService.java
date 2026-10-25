package net.tfminecraft.magic.blood;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.ToDoubleFunction;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.magic.Magic;
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
    private static final long FIRST_VOICE_DELAY = MINUTE;
    private static final Map<UUID, Long> NEXT_VOICE = new HashMap<>();

    static LongSupplier clock = System::currentTimeMillis;
    static Random random = new Random();
    /** The player's equilibrium, NaN without a resonance session. */
    static ToDoubleFunction<Player> equilibrium = WhisperService::sessionEquilibrium;

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
        if (!BloodConfig.isEnabled() || amount <= 0) {
            return false;
        }
        BloodRecord record = BloodRecords.getOrCreate(character, owner);
        if (record.getStage() != BloodStage.NONE) {
            return false;
        }
        double gain = amount * (fastTrait(character) ? WhisperConfig.fastMultiplier() : 1);
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

    /** Whisper weight for killing a listed animal with a blade, more when alone or at night. */
    public static double animalKillWeight(Player killer, Entity animal) {
        if (!WhisperConfig.isAnimal(animal.getType()) || !blade(killer.getInventory().getItemInMainHand())) {
            return 0;
        }
        long time = animal.getWorld().getTime();
        boolean night = time >= 13_000 && time < 23_000;
        return WhisperConfig.animalKill() * (night || alone(killer) ? WhisperConfig.aloneOrNightMultiplier() : 1);
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
        if (record != null && record.getStage() == BloodStage.NONE && record.getWhisper() > 0) {
            record.setWhisper(record.getWhisper() - WhisperConfig.decayPerHour() / 6.0);
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
        if (now < due || (WhisperConfig.requireAlone() && !alone(player))) {
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
        if (!lines.isEmpty()) {
            speak(player, speaker, lines.get(random.nextInt(lines.size())));
        }
    }

    public static WhisperConfig.Speaker speaker(BloodRecord record) {
        return record.getPath() == BloodPath.TRUE ? WhisperConfig.Speaker.SELF : WhisperConfig.Speaker.GOD;
    }

    /** One line to this player alone, in the speaker's style and sound. */
    public static void speak(Player player, WhisperConfig.Speaker speaker, String line) {
        WhisperConfig.Voice voice = WhisperConfig.voice(speaker);
        player.sendMessage(MagicText.format(voice.style() + line));
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
    }

    private static double sessionEquilibrium(Player player) {
        ResonanceSession session = Magic.plugin.getResonanceGuiManager().getSessionManager().get(player);
        return session != null ? session.getEquilibrium() : Double.NaN;
    }
}
