package net.tfminecraft.magic.gear;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;

import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.skill.trigger.TriggerType;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.data.AbilityData;
import net.Indyuce.mmoitems.stat.data.AbilityListData;
import net.tfminecraft.magic.Cache;

public final class RuneKeybind {

    public enum Result {
        NOT_RUNE,
        NO_ABILITIES,
        FAILED,
        OK
    }

    public record Outcome(Result result, ItemStack item) {
        public static Outcome of(Result result) {
            return new Outcome(result, null);
        }

        public static Outcome ok(ItemStack item) {
            return new Outcome(Result.OK, item);
        }
    }

    private RuneKeybind() {}

    public static boolean isRune(ItemStack item) {
        if (item == null || item.getType().isAir() || Cache.runeTypes.isEmpty()) {
            return false;
        }
        if (!GearItemBuilder.mmoItemsPresent()) {
            return false;
        }
        NBTItem nbt = NBTItem.get(item);
        if (!nbt.hasType()) {
            return false;
        }
        return Cache.runeTypes.contains(nbt.getType().toLowerCase());
    }

    public static Outcome apply(ItemStack held, TriggerType trigger) {
        if (!isRune(held) || trigger == null) {
            return Outcome.of(Result.NOT_RUNE);
        }
        try {
            LiveMMOItem mmo = new LiveMMOItem(NBTItem.get(held));
            if (!mmo.hasData(ItemStats.ABILITIES)) {
                return Outcome.of(Result.NO_ABILITIES);
            }
            AbilityListData current = (AbilityListData) mmo.getData(ItemStats.ABILITIES);
            if (current == null || current.isEmpty()) {
                return Outcome.of(Result.NO_ABILITIES);
            }
            List<AbilityData> copies = new ArrayList<>();
            for (AbilityData old : current.getAbilities()) {
                if (old == null || old.getAbility() == null) {
                    continue;
                }
                AbilityData copy = new AbilityData(old.getAbility(), trigger);
                for (String modifier : old.getModifiers()) {
                    copy.setModifier(modifier, old.getParameter(modifier));
                }
                copies.add(copy);
            }
            if (copies.isEmpty()) {
                return Outcome.of(Result.NO_ABILITIES);
            }
            mmo.setData(ItemStats.ABILITIES, new AbilityListData(copies));
            ItemStack rebuilt = mmo.newBuilder().build();
            if (rebuilt == null || rebuilt.getType().isAir()) {
                return Outcome.of(Result.FAILED);
            }
            rebuilt.setAmount(held.getAmount());
            return Outcome.ok(rebuilt);
        } catch (Exception ex) {
            return Outcome.of(Result.FAILED);
        }
    }
}
