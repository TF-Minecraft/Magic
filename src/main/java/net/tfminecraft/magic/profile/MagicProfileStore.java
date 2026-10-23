package net.tfminecraft.magic.profile;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.tfminecraft.magic.Magic;

public final class MagicProfileStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File folder;
    private final Set<String> unreadableProfiles = new HashSet<>();

    public MagicProfileStore(File folder) {
        this.folder = folder;
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    public boolean exists(String characterId) {
        if (characterId == null || characterId.isBlank()) {
            return false;
        }
        return fileFor(characterId).exists();
    }

    public MagicProfile load(String characterId) {
        if (characterId == null || characterId.isBlank()) {
            return null;
        }
        File file = fileFor(characterId);
        if (!file.exists()) {
            return null;
        }
        try (Reader reader = new FileReader(file)) {
            MagicProfile profile = GSON.fromJson(reader, MagicProfile.class);
            if (profile == null) {
                return null;
            }
            if (profile.getCharacterId() == null || profile.getCharacterId().isBlank()) {
                profile.setCharacterId(characterId);
            }
            profile.migrateClaims();
            unreadableProfiles.remove(characterId);
            return profile;
        } catch (IOException | RuntimeException ex) {
            Magic.plugin.getLogger().severe("[Magic] Failed to load profile " + characterId + ": " + ex.getMessage());
            unreadableProfiles.add(characterId);
            if (file.isFile()) {
                File rejected = new File(folder, file.getName() + ".rejected-" + UUID.randomUUID());
                try {
                    Files.move(file.toPath(), rejected.toPath());
                    unreadableProfiles.remove(characterId);
                    Magic.plugin.getLogger().warning("[Magic] Preserved unreadable profile at " + rejected.getName());
                } catch (IOException preserveFailure) {
                    Magic.plugin.getLogger().severe("[Magic] Could not preserve unreadable profile "
                            + characterId + ": " + preserveFailure.getMessage());
                }
            }
            return null;
        }
    }

    public void save(MagicProfile profile) {
        saveChecked(profile);
    }

    /** Reports whether the profile reached disk, for synchronous administrative edits. */
    public boolean saveChecked(MagicProfile profile) {
        if (profile == null || profile.getCharacterId() == null || profile.getCharacterId().isBlank()) {
            return false;
        }
        if (unreadableProfiles.contains(profile.getCharacterId())) {
            Magic.plugin.getLogger().severe("[Magic] Refusing to overwrite unreadable profile "
                    + profile.getCharacterId());
            return false;
        }
        if (!folder.exists()) {
            folder.mkdirs();
        }
        profile.migrateClaims();
        File target = fileFor(profile.getCharacterId());
        File temp = new File(folder, profile.getCharacterId() + ".json.tmp");
        try (Writer writer = new FileWriter(temp)) {
            GSON.toJson(profile, writer);
        } catch (IOException ex) {
            Magic.plugin.getLogger().severe("[Magic] Failed to write profile "
                    + profile.getCharacterId() + ": " + ex.getMessage());
            return false;
        }
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            Magic.plugin.getLogger().severe("[Magic] Failed to replace profile "
                    + profile.getCharacterId() + ": " + ex.getMessage());
            return false;
        }
        return true;
    }

    private File fileFor(String characterId) {
        File exact = new File(folder, characterId + ".json");
        if (exact.exists()) {
            return exact;
        }
        // Earlier versions used RPCharacters' UUID spelling verbatim. Keep that file on disk.
        File[] equivalents = folder.listFiles((dir, name) -> name.equalsIgnoreCase(exact.getName()));
        if (equivalents == null || equivalents.length == 0) {
            return exact;
        }
        if (equivalents.length > 1) {
            throw new IllegalStateException("Multiple case-equivalent Magic profiles for " + characterId);
        }
        return equivalents[0];
    }
}
