package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.magic.Magic;

/**
 * Sounds and particles for the blood path, by moment (fx.moments in the wildmagic/bloodmagic config). Each moment is a
 * list of layers played together or after a delay: a sound, or particles in a shape (point, ring,
 * column, spiral). Private layers reach only the player the moment is about; an effect never breaks
 * the rite that plays it.
 */
public final class BloodFx {

    enum Shape { POINT, RING, COLUMN, SPIRAL }

    record Layer(String sound, float volume, float pitch, Particle particle, Object data, int count,
            double spreadX, double spreadY, double spreadZ, double speed, double y, Shape shape, double radius,
            int points, double height, long delay, boolean personal) {}

    private static final Map<String, List<Layer>> MOMENTS = new HashMap<>();
    private static boolean enabled;

    static {
        reset();
    }

    private BloodFx() {}

    static void reset() {
        enabled = true;
        MOMENTS.clear();
    }

    static void load(ConfigurationSection fx, Logger log) {
        reset();
        if (fx == null) {
            return;
        }
        enabled = fx.getBoolean("enabled", true);
        ConfigurationSection moments = fx.getConfigurationSection("moments");
        if (moments == null) {
            return;
        }
        for (String moment : moments.getKeys(false)) {
            List<Layer> layers = new ArrayList<>();
            for (Map<?, ?> raw : moments.getMapList(moment)) {
                Layer layer = layer(raw);
                if (layer != null) {
                    layers.add(layer);
                } else {
                    log.warning("[Magic] blood magic config fx." + moment + " has a layer without a valid sound or particle: " + raw);
                }
            }
            MOMENTS.put(moment.toLowerCase(Locale.ROOT), List.copyOf(layers));
        }
    }

    /** The moment's layers; empty when it is not configured. */
    static List<Layer> moment(String name) {
        return MOMENTS.getOrDefault(name, List.of());
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** Plays the moment at the spot for everyone around; private layers are skipped. */
    public static void play(String moment, Location at) {
        play(moment, at, null);
    }

    /** Plays the moment at the spot; private layers reach only the viewer. */
    public static void play(String moment, Location at, Player viewer) {
        if (!enabled || at == null || at.getWorld() == null) {
            return;
        }
        for (Layer layer : moment(moment)) {
            if (layer.personal() && viewer == null) {
                continue;
            }
            Location spot = at.clone();
            if (layer.delay() > 0) {
                Magic.plugin.getServer().getScheduler().runTaskLater(Magic.plugin,
                        () -> run(moment, layer, spot, viewer), layer.delay());
            } else {
                run(moment, layer, spot, viewer);
            }
        }
    }

    private static void run(String moment, Layer layer, Location at, Player viewer) {
        try {
            if (layer.sound() != null) {
                if (layer.personal()) {
                    viewer.playSound(at, layer.sound(), layer.volume(), layer.pitch());
                } else {
                    at.getWorld().playSound(at, layer.sound(), layer.volume(), layer.pitch());
                }
                return;
            }
            Object data = data(layer);
            for (Location point : points(layer, at)) {
                if (layer.personal()) {
                    viewer.spawnParticle(layer.particle(), point, layer.count(), layer.spreadX(), layer.spreadY(),
                            layer.spreadZ(), layer.speed(), data);
                } else {
                    at.getWorld().spawnParticle(layer.particle(), point, layer.count(), layer.spreadX(),
                            layer.spreadY(), layer.spreadZ(), layer.speed(), data);
                }
            }
        } catch (RuntimeException ex) {
            Magic.plugin.getLogger().fine("[Magic] Blood effect " + moment + " failed: " + ex.getMessage());
        }
    }

    /** Block and item data are built when played; config loading must not need a running server. */
    static Object data(Layer layer) {
        if (layer.data() instanceof Material material) {
            return layer.particle().getDataType() == BlockData.class ? material.createBlockData() : new ItemStack(material);
        }
        return layer.data();
    }

    static List<Location> points(Layer layer, Location at) {
        Location base = at.clone().add(0, layer.y(), 0);
        List<Location> out = new ArrayList<>();
        int points = layer.points();
        switch (layer.shape()) {
            case RING -> {
                for (int i = 0; i < points; i++) {
                    double angle = 2 * Math.PI * i / points;
                    out.add(base.clone().add(Math.cos(angle) * layer.radius(), 0, Math.sin(angle) * layer.radius()));
                }
            }
            case COLUMN -> {
                for (int i = 0; i < points; i++) {
                    out.add(base.clone().add(0, layer.height() * i / Math.max(1, points - 1), 0));
                }
            }
            case SPIRAL -> {
                for (int i = 0; i < points; i++) {
                    double angle = 4 * Math.PI * i / points;
                    out.add(base.clone().add(Math.cos(angle) * layer.radius(), layer.height() * i / points,
                            Math.sin(angle) * layer.radius()));
                }
            }
            default -> out.add(base);
        }
        return out;
    }

    static Layer layer(Map<?, ?> raw) {
        String sound = text(raw.get("sound"));
        Particle particle = null;
        Object data = null;
        if (sound == null) {
            String name = text(raw.get("particle"));
            if (name == null) {
                return null;
            }
            try {
                particle = Particle.valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return null;
            }
            data = data(particle, raw);
            if (data == UNSUPPORTED) {
                return null;
            }
        } else if (!sound.contains(":")) {
            sound = "minecraft:" + sound;
        }
        double[] spread = spread(raw.get("spread"));
        Shape shape;
        try {
            shape = Shape.valueOf(String.valueOf(raw.containsKey("shape") ? raw.get("shape") : "point").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            shape = Shape.POINT;
        }
        return new Layer(sound, (float) number(raw, "volume", 1), (float) number(raw, "pitch", 1), particle, data,
                (int) Math.max(0, number(raw, "count", 1)), spread[0], spread[1], spread[2], number(raw, "speed", 0),
                number(raw, "y", 0), shape, number(raw, "radius", 1), (int) Math.max(1, number(raw, "points", 16)),
                number(raw, "height", 1), (long) Math.max(0, number(raw, "delay", 0)),
                Boolean.TRUE.equals(raw.get("private")));
    }

    private static final Object UNSUPPORTED = new Object();

    /** The particle's extra data from the layer, null when it takes none, or UNSUPPORTED. */
    private static Object data(Particle particle, Map<?, ?> raw) {
        Class<?> type = particle.getDataType();
        Color color = color(raw.get("color"), Color.fromRGB(0x8a0303));
        float size = (float) number(raw, "size", 1);
        if (type == Void.class) {
            return null;
        }
        if (type == Particle.DustOptions.class) {
            return new Particle.DustOptions(color, size);
        }
        if (type == Particle.DustTransition.class) {
            return new Particle.DustTransition(color, color(raw.get("to"), Color.fromRGB(0x1a0000)), size);
        }
        if (type == BlockData.class) {
            Material block = Material.matchMaterial(String.valueOf(raw.get("block")));
            return block != null && block.isBlock() ? block : UNSUPPORTED;
        }
        if (type == ItemStack.class) {
            Material item = Material.matchMaterial(String.valueOf(raw.get("item")));
            return item != null && item.isItem() ? item : UNSUPPORTED;
        }
        if (type == Color.class) {
            return color;
        }
        if (type == Float.class) {
            return (float) number(raw, "value", 0);
        }
        if (type == Integer.class) {
            return (int) number(raw, "value", 0);
        }
        return UNSUPPORTED;
    }

    private static String text(Object value) {
        return value instanceof String s && !s.isBlank() ? s.trim() : null;
    }

    private static double number(Map<?, ?> raw, String key, double fallback) {
        return raw.get(key) instanceof Number n ? n.doubleValue() : fallback;
    }

    private static double[] spread(Object value) {
        if (value instanceof Number n) {
            return new double[] {n.doubleValue(), n.doubleValue(), n.doubleValue()};
        }
        if (value instanceof List<?> list && list.size() == 3 && list.get(0) instanceof Number x
                && list.get(1) instanceof Number y && list.get(2) instanceof Number z) {
            return new double[] {x.doubleValue(), y.doubleValue(), z.doubleValue()};
        }
        return new double[] {0, 0, 0};
    }

    private static Color color(Object value, Color fallback) {
        String hex = text(value);
        if (hex == null) {
            return fallback;
        }
        try {
            return Color.fromRGB(Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16) & 0xFFFFFF);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
