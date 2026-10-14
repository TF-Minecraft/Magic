package net.tfminecraft.magic.gear.ownership;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/** Safe raw mappings retain nulls, extension fields and the original scalar row keys. */
public final class GearYaml {
    private GearYaml() {}
    @SuppressWarnings("unchecked")
    public static Map<Object, Object> read(byte[] bytes) throws IOException {
        LoaderOptions options = new LoaderOptions(); options.setAllowDuplicateKeys(false);
        options.setCodePointLimit(16 * 1024 * 1024);
        try {
            Object value = new Yaml(new SafeConstructor(options)).load(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
            if (!(value instanceof Map<?, ?>)) throw new IOException("Gear YAML is not a mapping");
            return (Map<Object, Object>) value;
        } catch (YAMLException ex) { throw new IOException("Unreadable gear YAML", ex); }
    }
    public static byte[] bytes(Map<?, ?> raw) { return new Yaml().dump(raw).getBytes(StandardCharsets.UTF_8); }
    public static Map<Object, Object> copy(Map<?, ?> raw) throws IOException { return new LinkedHashMap<>(read(bytes(raw))); }
    public static boolean equal(Map<?, ?> a, Map<?, ?> b) { return java.util.Arrays.equals(bytes(a), bytes(b)); }
}
