package net.tfminecraft.magic.artifact.sacrifice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SacrificeRegistry {

    private static boolean enabled = false;
    private static boolean sceneryCharge = true;
    private static double chargeSeconds = 10.0;
    private static double victimRange = 4.0;
    private static SacrificeWordMatch match = SacrificeWordMatch.STARTS_WITH;
    private static boolean caseInsensitive = true;
    private static boolean requireMinScore = true;
    private static double minSceneryAura = 50.0;
    private static double hintMinAura = 20.0;
    private static boolean requireDaggerInHand = true;
    private static boolean requireRpCharacters = true;
    private static boolean oneRitePerCaster = true;
    private static boolean refuseNewWordsWhileActive = true;
    private static boolean hideIncantation = false;
    private static String rpChannel = "rp";
    private static String pedestalId = "pedestal";
    private static String pedestalSlot = "*";
    private static SacrificeDaggerDef dagger = new SacrificeDaggerDef(SacrificeDaggerDef.DEFAULT_PATH);
    private static SacrificeFxDef fx = SacrificeFxDef.empty();
    private static final Map<String, SacrificeTierDef> tiers = new LinkedHashMap<>();
    private static final Map<String, SacrificeElementDef> elements = new LinkedHashMap<>();

    private SacrificeRegistry() {}

    public static void clear() {
        enabled = false;
        sceneryCharge = true;
        chargeSeconds = 10.0;
        victimRange = 4.0;
        match = SacrificeWordMatch.STARTS_WITH;
        caseInsensitive = true;
        requireMinScore = true;
        minSceneryAura = 50.0;
        hintMinAura = 20.0;
        requireDaggerInHand = true;
        requireRpCharacters = true;
        oneRitePerCaster = true;
        refuseNewWordsWhileActive = true;
        hideIncantation = false;
        rpChannel = "rp";
        pedestalId = "pedestal";
        pedestalSlot = "*";
        dagger = new SacrificeDaggerDef(SacrificeDaggerDef.DEFAULT_PATH);
        fx = SacrificeFxDef.empty();
        tiers.clear();
        elements.clear();
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void setSceneryCharge(boolean value) {
        sceneryCharge = value;
    }

    public static void setHideIncantation(boolean value) {
        hideIncantation = value;
    }

    public static void setRequireRpCharacters(boolean value) {
        requireRpCharacters = value;
    }

    public static void setRpChannel(String value) {
        rpChannel = value != null && !value.isBlank() ? value.trim().toLowerCase(Locale.ROOT) : "rp";
    }

    public static void setGlobals(
            double chargeSecondsValue,
            double victimRangeValue,
            SacrificeWordMatch matchValue,
            boolean caseInsensitiveValue,
            boolean requireMinScoreValue,
            double minSceneryAuraValue,
            double hintMinAuraValue,
            boolean requireDaggerInHandValue,
            boolean oneRitePerCasterValue,
            boolean refuseNewWordsWhileActiveValue,
            String pedestalIdValue,
            String pedestalSlotValue,
            SacrificeDaggerDef daggerValue) {
        chargeSeconds = chargeSecondsValue > 0 ? chargeSecondsValue : 10.0;
        victimRange = victimRangeValue > 0 ? victimRangeValue : 4.0;
        match = matchValue != null ? matchValue : SacrificeWordMatch.STARTS_WITH;
        caseInsensitive = caseInsensitiveValue;
        requireMinScore = requireMinScoreValue;
        minSceneryAura = minSceneryAuraValue > 0 ? minSceneryAuraValue : 0;
        hintMinAura = Math.max(0, hintMinAuraValue);
        requireDaggerInHand = requireDaggerInHandValue;
        oneRitePerCaster = oneRitePerCasterValue;
        refuseNewWordsWhileActive = refuseNewWordsWhileActiveValue;
        pedestalId = pedestalIdValue != null && !pedestalIdValue.isBlank()
                ? pedestalIdValue.trim()
                : "pedestal";
        pedestalSlot = pedestalSlotValue != null && !pedestalSlotValue.isBlank()
                ? pedestalSlotValue.trim()
                : "*";
        dagger = daggerValue != null ? daggerValue : new SacrificeDaggerDef(SacrificeDaggerDef.DEFAULT_PATH);
    }

    public static void registerTier(SacrificeTierDef def) {
        if (def == null || def.getId().isBlank()) {
            return;
        }
        tiers.put(def.getId(), def);
    }

    public static void register(SacrificeElementDef def) {
        if (def == null || def.getElementId().isBlank()) {
            return;
        }
        elements.put(def.getElementId(), def);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isSceneryCharge() {
        return sceneryCharge;
    }

    public static boolean blocksSceneryCharge(String elementId) {
        if (!enabled || sceneryCharge || elementId == null || elementId.isBlank()) {
            return false;
        }
        return elements.containsKey(elementId.trim().toLowerCase(Locale.ROOT));
    }

    public static double getChargeSeconds() {
        return chargeSeconds;
    }

    public static double getVictimRange() {
        return victimRange;
    }

    public static SacrificeWordMatch getMatch() {
        return match;
    }

    public static boolean isCaseInsensitive() {
        return caseInsensitive;
    }

    public static boolean isRequireMinScore() {
        return requireMinScore;
    }

    public static double getMinSceneryAura() {
        return minSceneryAura;
    }

    public static double getHintMinAura() {
        return hintMinAura;
    }

    public static double minSceneryAura(String elementId) {
        SacrificeElementDef def = getById(elementId);
        if (def != null && def.getMinSceneryAura() >= 0) {
            return def.getMinSceneryAura();
        }
        return minSceneryAura;
    }

    public static boolean isRequireDaggerInHand() {
        return requireDaggerInHand;
    }

    public static boolean isRequireRpCharacters() {
        return requireRpCharacters;
    }

    public static boolean isOneRitePerCaster() {
        return oneRitePerCaster;
    }

    public static boolean isRefuseNewWordsWhileActive() {
        return refuseNewWordsWhileActive;
    }

    public static boolean isHideIncantation() {
        return hideIncantation;
    }

    public static String getRpChannel() {
        return rpChannel;
    }

    public static String getPedestalId() {
        return pedestalId;
    }

    public static String getPedestalSlot() {
        return pedestalSlot;
    }

    public static SacrificeDaggerDef getDagger() {
        return dagger;
    }

    public static void setFx(SacrificeFxDef value) {
        fx = value != null ? value : SacrificeFxDef.empty();
    }

    public static SacrificeFxDef getFx() {
        return fx;
    }

    public static SacrificeTierDef getTier(String id) {
        if (id == null) {
            return null;
        }
        return tiers.get(id.trim().toLowerCase(Locale.ROOT));
    }

    public static List<SacrificeTierDef> getTiers() {
        return Collections.unmodifiableList(new ArrayList<>(tiers.values()));
    }

    public static SacrificeTierDef tierForCharge(double charge) {
        for (SacrificeTierDef def : tiers.values()) {
            if (def.matches(charge)) {
                return def;
            }
        }
        SacrificeTierDef none = tiers.get("none");
        if (none != null) {
            return none;
        }
        return new SacrificeTierDef("none", 0.0, 0.0, 0.0, "none", false);
    }

    public static SacrificeElementDef getById(String elementId) {
        if (elementId == null) {
            return null;
        }
        return elements.get(elementId.trim().toLowerCase(Locale.ROOT));
    }

    public static List<SacrificeElementDef> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(elements.values()));
    }

    public static int size() {
        return elements.size();
    }
}
