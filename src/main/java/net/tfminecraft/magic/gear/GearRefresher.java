package net.tfminecraft.magic.gear;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.data.GemSocketsData;
import net.Indyuce.mmoitems.stat.data.GemstoneData;
import net.Indyuce.mmoitems.stat.data.type.StatData;
import net.tfminecraft.magic.Magic;

/**
 * Brings a crafted weapon back in line with the live gear config.
 *
 * Socket colours and part stats are rewritten from the stamped parts. Runes already in
 * the weapon are merged into the new layout; any that no longer fit are held by
 * {@link GearBrokenMarker} instead of being dropped.
 */
public final class GearRefresher {

    private GearRefresher() {}

    public static boolean isManaged(ItemStack stack) {
        return GearProvenance.isGear(stack) && !GearProvenance.partsRaw(stack).isBlank();
    }

    /** Returns the rebuilt stack, or null when nothing needed doing. */
    public static ItemStack refreshIfOutdated(ItemStack stack, Player holder) {
        return refresh(stack, holder, false);
    }

    public static ItemStack refresh(ItemStack stack, Player holder, boolean force) {
        if (!isManaged(stack) || !GearItemBuilder.mmoItemsPresent()) {
            return null;
        }
        if (GearBrokenMarker.isBroken(stack)) {
            return null;
        }

        List<String> missing = GearProvenance.missingPartIds(stack);
        if (!missing.isEmpty()) {
            ItemStack broken = stack.clone();
            GearBrokenMarker.mark(broken);
            GearBrokenMarker.notifyOnce(holder, broken, missing);
            return broken;
        }

        if (!force && !GearProvenance.isOutdated(stack)) {
            int live = MajorityTierResolver.resolve(GearProvenance.resolveParts(stack));
            if (live > 0 && GearProvenance.majorityOf(stack) != live) {
                ItemStack clone = stack.clone();
                GearProvenance.applyMajority(clone, GearProvenance.resolveParts(clone));
                return WeaponLore.updateItem(clone);
            }
            return null;
        }

        GearType type = GearProvenance.archetypeOf(stack);
        ArchetypeDef archetype = ArchetypeRegistry.get(type);
        if (archetype == null) {
            return null;
        }
        int band = WeaponRequirement.fromItem(stack).highestBand();
        List<String> colours = SocketLayout.colours(archetype, GearProvenance.resolveParts(stack), band);

        List<GemstoneData> orphaned = new ArrayList<>();
        ItemStack rebuilt = rewrite(stack, colours, orphaned);
        if (rebuilt == null) {
            return null;
        }

        copyGearPdc(stack, rebuilt);
        WeaponRequirement.fromItem(stack).persist(rebuilt);
        WeaponRift.copy(stack, rebuilt);
        GearProvenance.syncRevisions(rebuilt);
        GearProvenance.applyMajority(rebuilt, GearProvenance.resolveParts(rebuilt));
        rebuilt.setAmount(stack.getAmount());

        if (!orphaned.isEmpty()) {
            List<GearBrokenMarker.Orphan> payload = new ArrayList<>();
            for (GemstoneData gem : orphaned) {
                payload.add(new GearBrokenMarker.Orphan(gem.getMMOItemType(), gem.getMMOItemID()));
            }
            GearBrokenMarker.addOrphans(rebuilt, payload);
            GearBrokenMarker.mark(rebuilt);
            GearBrokenMarker.notifyOnce(holder, rebuilt, List.of());
            return rebuilt;
        }
        return WeaponLore.updateItem(rebuilt);
    }

    /**
     * Writes the target socket colours while carrying existing runes across. Unlike
     * {@code GearItemBuilder} craft, which builds fresh socket data at craft time,
     * this puts every gem it can back into a matching empty socket first.
     */
    private static ItemStack rewrite(ItemStack stack, List<String> colours, List<GemstoneData> orphaned) {
        try {
            LiveMMOItem mmo = new LiveMMOItem(NBTItem.get(stack));
            List<GemstoneData> existing = new ArrayList<>();
            if (mmo.hasData(ItemStats.GEM_SOCKETS)) {
                StatData data = mmo.getData(ItemStats.GEM_SOCKETS);
                if (data instanceof GemSocketsData sockets) {
                    existing.addAll(sockets.getGems());
                }
            }
            GemSocketsData next = new GemSocketsData(new ArrayList<>(colours));
            for (GemstoneData gem : existing) {
                String colour = gem.getSocketColor();
                if (colour != null && next.canReceive(colour) && next.apply(colour, gem)) {
                    continue;
                }
                orphaned.add(gem);
            }
            mmo.setData(ItemStats.GEM_SOCKETS, next);
            GearStatApplicator.apply(mmo, GearProvenance.resolveParts(stack));
            ItemStack built = mmo.newBuilder().build();
            if (built == null || built.getType().isAir()) {
                return null;
            }
            GearType type = GearProvenance.archetypeOf(stack);
            return GearModelResolver.apply(built, type, GearProvenance.resolveParts(stack));
        } catch (Exception ex) {
            Magic.plugin.getLogger().warning("[Magic] Gear refresh failed: " + ex.getMessage());
            return null;
        }
    }

    private static void copyGearPdc(ItemStack from, ItemStack to) {
        if (from == null || to == null || !from.hasItemMeta() || !to.hasItemMeta()) {
            return;
        }
        ItemMeta fromMeta = from.getItemMeta();
        ItemMeta toMeta = to.getItemMeta();
        if (fromMeta == null || toMeta == null) {
            return;
        }
        copyString(fromMeta, toMeta, GearKeys.parts());
        copyString(fromMeta, toMeta, GearKeys.archetype());
        copyString(fromMeta, toMeta, GearKeys.orphans());
        Integer archetypeRevision = fromMeta.getPersistentDataContainer().get(
                GearKeys.archetypeRevision(), PersistentDataType.INTEGER);
        if (archetypeRevision != null) {
            toMeta.getPersistentDataContainer().set(
                    GearKeys.archetypeRevision(), PersistentDataType.INTEGER, archetypeRevision);
        }
        Integer majority = fromMeta.getPersistentDataContainer().get(
                GearKeys.majorityTier(), PersistentDataType.INTEGER);
        if (majority != null) {
            toMeta.getPersistentDataContainer().set(
                    GearKeys.majorityTier(), PersistentDataType.INTEGER, majority);
        }
        Byte locked = fromMeta.getPersistentDataContainer().get(
                GearKeys.socketsLocked(), PersistentDataType.BYTE);
        if (locked != null) {
            toMeta.getPersistentDataContainer().set(
                    GearKeys.socketsLocked(), PersistentDataType.BYTE, locked);
        }
        to.setItemMeta(toMeta);
    }

    private static void copyString(ItemMeta from, ItemMeta to, org.bukkit.NamespacedKey key) {
        String value = from.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (value != null) {
            to.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        }
    }
}
