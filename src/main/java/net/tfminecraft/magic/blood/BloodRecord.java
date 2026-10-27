package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One character's blood path, saved as data/bloodmagic/characters/&lt;characterId&gt;.json. */
public final class BloodRecord {

    private String characterId;
    private String ownerUuid;
    private BloodStage stage = BloodStage.NONE;
    private BloodPath path = BloodPath.NONE;
    /** Hidden whisper meter, 0 and up. */
    private double whisper;
    /** Index of the next voice line for the current stage. */
    private int whisperLine;
    /** Animal species already sacrificed, lower-case entity type keys. */
    private List<String> species = new ArrayList<>();
    private long lastAnimalAt;
    private String altarId;
    private int huntPoints;
    private int finalsDone;
    /** Account of the last character sacrificed, so the same player is never taken twice in a row. */
    private String lastVictimAccount;
    /** Every character sacrificed at the altar, oldest first. */
    private List<Sacrifice> sacrifices = new ArrayList<>();
    /** Final targets still to come, least close first; the first is the one named now. */
    private List<String> finalTargets = new ArrayList<>();
    /** No final target is named before this time (after a failed sacrifice). */
    private long nextFinalAt;
    /** A failed sacrifice whose backlash waits for the mage to come online. */
    private boolean pendingBacklash;
    /** The path's artefacts handed out so far, one id per place (see {@link BloodArtefacts}). */
    private List<String> artefacts = new ArrayList<>();
    /** Ids of the artefacts sunk into the altar's pool of blood. */
    private List<String> sunk = new ArrayList<>();
    /** Blood in the altar's pool once the path is complete; null until then (a full pool). */
    private Double pool;

    /**
     * One character sacrificed at the altar. rank: the victim's closeness rank when taken (1 = closest),
     * 0 for a stranger or a record from before ranks were kept.
     */
    public record Sacrifice(String character, String account, String stage, int points, long at, int rank) {
        public Sacrifice(String character, String account, String stage, int points, long at) {
            this(character, account, stage, points, at, 0);
        }
    }

    public BloodRecord() {}

    public static BloodRecord create(String characterId, String ownerUuid) {
        BloodRecord record = new BloodRecord();
        record.characterId = characterId;
        record.ownerUuid = ownerUuid;
        return record;
    }

    /** Repairs fields a hand edit or an older file may have left empty. */
    public void normalize() {
        if (stage == null) {
            stage = BloodStage.NONE;
        }
        if (path == null) {
            path = BloodPath.NONE;
        }
        if (species == null) {
            species = new ArrayList<>();
        }
        if (sacrifices == null) {
            sacrifices = new ArrayList<>();
        }
        if (finalTargets == null) {
            finalTargets = new ArrayList<>();
        }
        if (artefacts == null) {
            artefacts = new ArrayList<>();
        }
        if (sunk == null) {
            sunk = new ArrayList<>();
        }
        if (pool != null && (pool.isNaN() || pool < 0)) {
            pool = 0.0;
        }
        if (whisper < 0 || Double.isNaN(whisper)) {
            whisper = 0;
        }
        whisperLine = Math.max(0, whisperLine);
        huntPoints = Math.max(0, huntPoints);
        finalsDone = Math.max(0, finalsDone);
        if (altarId != null && altarId.isBlank()) {
            altarId = null;
        }
    }

    public String getCharacterId() {
        return characterId;
    }

    public void setCharacterId(String characterId) {
        this.characterId = characterId;
    }

    public String getOwnerUuid() {
        return ownerUuid;
    }

    public BloodStage getStage() {
        return stage;
    }

    /** Moves to a stage and restarts its voice lines. */
    public void setStage(BloodStage stage) {
        if (this.stage != stage) {
            whisperLine = 0;
        }
        this.stage = stage;
    }

    public BloodPath getPath() {
        return path;
    }

    public void setPath(BloodPath path) {
        this.path = path;
    }

    public double getWhisper() {
        return whisper;
    }

    public void setWhisper(double whisper) {
        this.whisper = Math.max(0, whisper);
    }

    public int getWhisperLine() {
        return whisperLine;
    }

    public void setWhisperLine(int whisperLine) {
        this.whisperLine = Math.max(0, whisperLine);
    }

    public List<String> getSpecies() {
        return species;
    }

    public boolean hasSpecies(String key) {
        return key != null && species.contains(key.toLowerCase(Locale.ROOT));
    }

    public void addSpecies(String key) {
        if (key != null && !hasSpecies(key)) {
            species.add(key.toLowerCase(Locale.ROOT));
        }
    }

    public long getLastAnimalAt() {
        return lastAnimalAt;
    }

    public void setLastAnimalAt(long lastAnimalAt) {
        this.lastAnimalAt = lastAnimalAt;
    }

    public String getAltarId() {
        return altarId;
    }

    public void setAltarId(String altarId) {
        this.altarId = altarId;
    }

    public int getHuntPoints() {
        return huntPoints;
    }

    public void setHuntPoints(int huntPoints) {
        this.huntPoints = Math.max(0, huntPoints);
    }

    public int getFinalsDone() {
        return finalsDone;
    }

    public void setFinalsDone(int finalsDone) {
        this.finalsDone = Math.max(0, finalsDone);
    }

    public String getLastVictimAccount() {
        return lastVictimAccount;
    }

    public void setLastVictimAccount(String lastVictimAccount) {
        this.lastVictimAccount = lastVictimAccount;
    }

    public List<Sacrifice> getSacrifices() {
        return sacrifices;
    }

    public List<String> getFinalTargets() {
        return finalTargets;
    }

    /** The final target named now, or null. */
    public String currentTarget() {
        return finalTargets.isEmpty() ? null : finalTargets.get(0);
    }

    public long getNextFinalAt() {
        return nextFinalAt;
    }

    public void setNextFinalAt(long nextFinalAt) {
        this.nextFinalAt = nextFinalAt;
    }

    public boolean isPendingBacklash() {
        return pendingBacklash;
    }

    public void setPendingBacklash(boolean pendingBacklash) {
        this.pendingBacklash = pendingBacklash;
    }

    public List<String> getArtefacts() {
        return artefacts;
    }

    public List<String> getSunk() {
        return sunk;
    }

    /** The pool's blood, or null while it was never measured (full). */
    public Double getPool() {
        return pool;
    }

    public void setPool(Double pool) {
        this.pool = pool == null ? null : Math.max(0, pool);
    }

    /** True when the record carries nothing worth keeping on disk. */
    public boolean isEmpty() {
        return stage == BloodStage.NONE && path == BloodPath.NONE && whisper <= 0
                && species.isEmpty() && altarId == null && huntPoints == 0 && finalsDone == 0
                && sacrifices.isEmpty() && finalTargets.isEmpty() && !pendingBacklash
                && artefacts.isEmpty() && sunk.isEmpty() && pool == null;
    }
}
