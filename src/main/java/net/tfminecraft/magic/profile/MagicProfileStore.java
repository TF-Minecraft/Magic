package net.tfminecraft.magic.profile;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.tfminecraft.magic.Magic;

public final class MagicProfileStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File folder;

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
            return profile;
        } catch (IOException | RuntimeException ex) {
            Magic.plugin.getLogger().severe("[Magic] Failed to load profile " + characterId + ": " + ex.getMessage());
            return null;
        }
    }

    public void save(MagicProfile profile) {
        if (profile == null || profile.getCharacterId() == null || profile.getCharacterId().isBlank()) {
            return;
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
            return;
        }
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            Magic.plugin.getLogger().severe("[Magic] Failed to replace profile "
                    + profile.getCharacterId() + ": " + ex.getMessage());
        }
    }

    private File fileFor(String characterId) {
        return new File(folder, characterId + ".json");
    }
}
