package net.tfminecraft.magic.artifact.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ArtifactModelScheme {

    private final String id;
    private final List<ArtifactModelEntry> models;

    public ArtifactModelScheme(String id, List<ArtifactModelEntry> models) {
        this.id = id;
        this.models = Collections.unmodifiableList(new ArrayList<>(models));
    }

    public String getId() {
        return id;
    }

    public List<ArtifactModelEntry> getModels() {
        return models;
    }
}
