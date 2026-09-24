package net.tfminecraft.magic.gear;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.stat.data.DoubleData;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import net.Indyuce.mmoitems.stat.type.StatHistory;
import net.tfminecraft.magic.Magic;

public final class GearStatApplicator {

    private static final Set<String> UNKNOWN_LOGGED = new HashSet<>();

    private GearStatApplicator() {}

    public static Map<String, Double> sum(Collection<PartDef> parts) {
        Map<String, Double> totals = new LinkedHashMap<>();
        if (parts == null) {
            return totals;
        }
        for (PartDef part : parts) {
            if (part == null || part.getStats().isEmpty()) {
                continue;
            }
            for (Map.Entry<String, Double> entry : part.getStats().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                totals.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }
        return totals;
    }

    public static Set<String> managedIds() {
        Set<String> ids = new HashSet<>();
        for (PartDef part : PartRegistry.getAll()) {
            ids.addAll(part.getStats().keySet());
        }
        return ids;
    }

    public static void apply(MMOItem mmo, Collection<PartDef> parts) {
        if (mmo == null || !GearItemBuilder.mmoItemsPresent()) {
            return;
        }
        Set<String> managed = managedIds();
        if (managed.isEmpty()) {
            return;
        }
        Map<String, Double> totals = sum(parts);
        for (String statId : managed) {
            ItemStat<?, ?> itemStat = resolve(statId);
            if (itemStat == null) {
                continue;
            }
            StatHistory hist = StatHistory.from(mmo, itemStat);
            if (hist != null) {
                hist.clearExternalData();
                Object og = hist.getOriginalData();
                if (og instanceof DoubleData doubleOg) {
                    doubleOg.setValue(0);
                }
                mmo.setStatHistory(itemStat, hist);
            }
            mmo.setData(itemStat, new DoubleData(0));
        }
        for (String statId : managed) {
            applyDouble(mmo, statId, totals.getOrDefault(statId, 0.0));
        }
    }

    @SuppressWarnings("deprecation")
    private static void applyDouble(MMOItem mmo, String statId, double value) {
        ItemStat<?, ?> itemStat = resolve(statId);
        if (itemStat == null) {
            return;
        }
        DoubleData data = new DoubleData(value);
        mmo.setData(itemStat, data);
        StatHistory hist = StatHistory.from(mmo, itemStat);
        if (hist != null) {
            hist.registerExternalData(data);
            mmo.setStatHistory(itemStat, hist);
        }
    }

    private static ItemStat<?, ?> resolve(String statId) {
        if (statId == null || statId.isBlank()) {
            return null;
        }
        ItemStat<?, ?> itemStat = MMOItems.plugin.getStats().get(statId.toUpperCase(Locale.ROOT));
        if (itemStat == null && UNKNOWN_LOGGED.add(statId.toLowerCase(Locale.ROOT))) {
            Magic.plugin.getLogger().warning("[Magic] Unknown MMOItems stat: " + statId);
        }
        return itemStat;
    }
}
