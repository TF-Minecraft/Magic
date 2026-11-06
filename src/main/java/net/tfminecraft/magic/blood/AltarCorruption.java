package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.Set;
import java.util.function.LongSupplier;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Ageable;

/**
 * The room around an altar slowly turns. Only listed blocks change: exposed stone-like blocks
 * are converted, empty floor and ceiling spots are decorated. It grows outward from the altar in
 * connected patches: of a handful of random candidates, the one nearest the altar that touches what
 * it already turned wins, so the room reddens in veins rather than speckles. Floor and ceiling
 * decorations grow only on blocks it has already turned, and candles stand only on the spots right in
 * front of the pedestals. Every change is journalled on the altar with the block
 * it replaced, and purification puts back any block still as the altar left it.
 */
public final class AltarCorruption {

    private static final int ATTEMPTS = 24;
    private static final int MAX_HEIGHT_REACH = 3;
    private static final BlockFace[] FACES = {
        BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    static Random random = new Random();
    static LongSupplier clock = System::currentTimeMillis;

    private AltarCorruption() {}

    /** Changes up to count blocks within the altar level's reach and budget; returns how many changed. */
    public static int spread(BloodAltar altar, int count) {
        Block center = altar.block();
        if (center == null || !center.getWorld().isChunkLoaded(altar.getX() >> 4, altar.getZ() >> 4)) {
            return 0;
        }
        CorruptionConfig.Reach reach = CorruptionConfig.reach(altar.getLevel());
        List<CorruptionConfig.Rule> rules = CorruptionConfig.rules(altar.getLevel());
        if (rules.isEmpty()) {
            return 0;
        }
        int made = 0;
        for (int i = 0; i < count && altar.getCorrupted().size() < reach.maxBlocks(); i++) {
            if (changeOne(altar, center, reach.radius(), rules)) {
                made++;
            }
        }
        return made;
    }

    private static boolean changeOne(BloodAltar altar, Block center, int radius, List<CorruptionConfig.Rule> rules) {
        int height = Math.min(radius, MAX_HEIGHT_REACH);
        Block above = center.getRelative(BlockFace.UP);
        Set<Long> turned = new HashSet<>();
        for (BloodAltar.CorruptedBlock entry : altar.getCorrupted()) {
            turned.add(key(entry.x(), entry.y(), entry.z()));
        }
        Block best = null;
        Material bestTo = null;
        double bestScore = 0;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            Block block = center.getRelative(random.nextInt(2 * radius + 1) - radius,
                    random.nextInt(2 * height + 1) - height, random.nextInt(2 * radius + 1) - radius);
            if (block.equals(center) || block.equals(above)
                    || !block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
                    || AltarRegions.blocked(block.getLocation())) {
                continue;
            }
            CorruptionConfig.Rule rule = rules.get(random.nextInt(rules.size()));
            Material to = fits(rule, block, turned) ? material(rule, block, center) : null;
            if (to == null) {
                continue;
            }
            double score = score(block, center, turned);
            if (best == null || score > bestScore) {
                best = block;
                bestTo = to;
                bestScore = score;
            }
        }
        if (best == null) {
            return false;
        }
        change(altar, best, bestTo);
        return true;
    }

    /** Nearer the altar is better, touching what it already turned much better, with a little chance. */
    static double score(Block block, Block center, Set<Long> turned) {
        double score = -0.6 * Math.sqrt(block.getLocation().distanceSquared(center.getLocation()));
        for (BlockFace face : FACES) {
            if (turned(turned, block.getRelative(face))) {
                score += 4;
                break;
            }
        }
        return score + random.nextDouble() * 1.5;
    }

    /** What the rule makes of the block; candles only on a spot right in front of a pedestal. */
    static Material material(CorruptionConfig.Rule rule, Block block, Block center) {
        List<Material> allowed = new ArrayList<>();
        boolean candleSpot = candleSpot(block, center);
        for (Material to : rule.to()) {
            if (!Tag.CANDLES.isTagged(to) || candleSpot) {
                allowed.add(to);
            }
        }
        return allowed.isEmpty() ? null : allowed.get(random.nextInt(allowed.size()));
    }

    /** One step from each pedestal toward the altar, on the altar's floor. */
    static boolean candleSpot(Block block, Block center) {
        if (block.getY() != center.getY()) {
            return false;
        }
        int dx = block.getX() - center.getX();
        int dz = block.getZ() - center.getZ();
        for (int[] pedestal : AltarPedestals.ring()) {
            if (dx == pedestal[0] - Integer.signum(pedestal[0]) && dz == pedestal[1] - Integer.signum(pedestal[1])) {
                return true;
            }
        }
        return false;
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
    }

    static boolean fits(CorruptionConfig.Rule rule, Block block) {
        return fits(rule, block, null);
    }

    /** With turned set, decorations only grow on (or hang from) a block the altar has turned already. */
    static boolean fits(CorruptionConfig.Rule rule, Block block, Set<Long> turned) {
        return switch (rule.on()) {
            case CONVERT -> rule.from().contains(block.getType()) && exposed(block);
            case FLOOR -> block.isEmpty() && rests(block.getRelative(BlockFace.DOWN))
                    && (turned == null || turned(turned, block.getRelative(BlockFace.DOWN)));
            case CEILING -> block.isEmpty() && rests(block.getRelative(BlockFace.UP))
                    && (turned == null || turned(turned, block.getRelative(BlockFace.UP)));
        };
    }

    private static boolean turned(Set<Long> turned, Block block) {
        return turned.contains(key(block.getX(), block.getY(), block.getZ()));
    }

    /** Solid stone to sit on or hang from; a barrier is furniture (a pedestal) or the altar, never decorated. */
    private static boolean rests(Block block) {
        return block.getType().isSolid() && block.getType() != Material.BARRIER;
    }

    private static boolean exposed(Block block) {
        for (BlockFace face : FACES) {
            if (block.getRelative(face).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void change(BloodAltar altar, Block block, Material to) {
        String original = block.getBlockData().getAsString();
        List<BloodAltar.CorruptedBlock> journal = altar.getCorrupted();
        for (ListIterator<BloodAltar.CorruptedBlock> it = journal.listIterator(); it.hasNext();) {
            BloodAltar.CorruptedBlock entry = it.next();
            if (entry.x() == block.getX() && entry.y() == block.getY() && entry.z() == block.getZ()) {
                original = entry.original();
                it.remove();
                break;
            }
        }
        block.setType(to, false);
        BlockData data = block.getBlockData();
        if (data instanceof Lightable lightable) {
            lightable.setLit(true);
            block.setBlockData(lightable, false);
        } else if (data instanceof Ageable ageable) {
            // Fully grown: vines and the like never spread beyond what the journal can undo.
            ageable.setAge(ageable.getMaximumAge());
            block.setBlockData(ageable, false);
        }
        journal.add(new BloodAltar.CorruptedBlock(block.getX(), block.getY(), block.getZ(), original, to.name()));
        BloodFx.play("corruption", block.getLocation().clone().add(0.5, 0.5, 0.5));
    }

    /** Whether an altar changed this block and it still is what the altar made it. */
    public static boolean isCorrupted(Block block) {
        String world = block.getWorld().getName();
        for (BloodAltar altar : BloodAltars.all()) {
            if (!world.equals(altar.getWorld())) {
                continue;
            }
            for (BloodAltar.CorruptedBlock entry : altar.getCorrupted()) {
                if (entry.x() == block.getX() && entry.y() == block.getY() && entry.z() == block.getZ()) {
                    return block.getType().name().equals(entry.placed());
                }
            }
        }
        return false;
    }

    /** Puts back every journalled block that still is what the altar made it, newest first. */
    public static void restore(BloodAltar altar) {
        List<BloodAltar.CorruptedBlock> journal = altar.getCorrupted();
        World world = Bukkit.getWorld(altar.getWorld());
        if (world != null) {
            for (int i = journal.size() - 1; i >= 0; i--) {
                BloodAltar.CorruptedBlock entry = journal.get(i);
                Block block = world.getBlockAt(entry.x(), entry.y(), entry.z());
                if (!block.getType().name().equals(entry.placed())) {
                    continue;
                }
                try {
                    block.setBlockData(Bukkit.createBlockData(entry.original()), false);
                } catch (IllegalArgumentException ex) {
                    block.setType(Material.AIR, false);
                }
            }
        }
        journal.clear();
    }

    /** The slow spread between sacrifices: a block (ambient_blocks) per altar every few minutes. */
    static void ambient() {
        long now = clock.getAsLong();
        long every = CorruptionConfig.ambientEveryMinutes() * 60_000L;
        boolean changed = false;
        for (BloodAltar altar : BloodAltars.all()) {
            if (now - altar.getLastSpreadAt() < every) {
                continue;
            }
            if (spread(altar, CorruptionConfig.ambientBlocks()) > 0) {
                altar.setLastSpreadAt(now);
                changed = true;
            }
        }
        if (changed) {
            BloodAltars.save();
        }
    }
}
