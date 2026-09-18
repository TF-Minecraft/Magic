package net.tfminecraft.magic.artifact.sacrifice;

public final class SacrificeDaggerDef {

    public static final String DEFAULT_PATH = "v.golden_sword";

    private final String path;

    public SacrificeDaggerDef(String path) {
        this.path = path != null && !path.isBlank() ? path.trim() : DEFAULT_PATH;
    }

    public String getPath() {
        return path;
    }
}
