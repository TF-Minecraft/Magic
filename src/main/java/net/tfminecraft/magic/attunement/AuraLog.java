package net.tfminecraft.magic.attunement;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;

import net.tfminecraft.magic.util.MagicNumbers;

/**
 * Immediate debug log at {@code logs/aura.log} in the plugin data folder.
 * Enable via {@code logging: true} in config.yml.
 */
public final class AuraLog {

    private static final Logger LOGGER = Logger.getLogger(AuraLog.class.getName());
    private static final DateTimeFormatter SESSION_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    static final String LOG_DIRECTORY = "logs";
    static final String LOG_FILE_NAME = "aura.log";

    private static volatile boolean enabled;
    private static volatile Path logFile;
    private static final Object LOCK = new Object();

    private AuraLog() {}

    public static void configure(boolean loggingEnabled, boolean wipeLog, File dataFolder) {
        enabled = loggingEnabled;
        if (dataFolder != null) {
            logFile = dataFolder.toPath().resolve(LOG_DIRECTORY).resolve(LOG_FILE_NAME);
        }
        if (wipeLog) {
            wipeLogFile();
        }
    }

    private static void wipeLogFile() {
        if (logFile == null) {
            return;
        }
        synchronized (LOCK) {
            try {
                Files.deleteIfExists(logFile);
            } catch (IOException exception) {
                LOGGER.warning("Failed to wipe aura.log: " + exception.getMessage());
            }
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static String n(double value) {
        return MagicNumbers.format(value, MagicNumbers.CLAIM_DECIMALS);
    }

    public static void append(String message) {
        if (!enabled || message == null || logFile == null) {
            return;
        }
        writeLines(List.of(SESSION_TIME.format(Instant.now()) + " " + message));
    }

    public static void append(String format, Object... args) {
        if (!enabled) {
            return;
        }
        append(String.format(format, args));
    }

    private static void writeLines(List<String> lines) {
        synchronized (LOCK) {
            try {
                Files.createDirectories(logFile.getParent());
                try (BufferedWriter writer = Files.newBufferedWriter(
                        logFile,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND)) {
                    for (String line : lines) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
            } catch (IOException exception) {
                LOGGER.warning("Failed to write aura.log: " + exception.getMessage());
            }
        }
    }
}
