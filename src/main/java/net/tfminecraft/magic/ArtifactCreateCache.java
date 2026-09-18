package net.tfminecraft.magic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.tfminecraft.magic.model.CastModeDef;

/**
 * Artifact create-GUI slots and button defs from gui.yml artifact_create.
 */
public final class ArtifactCreateCache {

    private ArtifactCreateCache() {}

    public static String title = "{color:title}Create artifact";
    public static int previewSlot = 4;
    public static int capMinusSlot = 12;
    public static int capPlusSlot = 14;
    public static int confirmSlot = 48;
    public static int cancelSlot = 50;
    public static List<Integer> raritySlots = List.of(10, 11, 13, 15, 16);

    public static CastModeDef capMinus = new CastModeDef(
            "cap_minus",
            "vanilla.GRAY_STAINED_GLASS_PANE",
            "{color:label_muted}Lower cap",
            List.of("{color:label_body}Click -1", "{color:label_muted}Shift click -5"));
    public static CastModeDef capPlus = new CastModeDef(
            "cap_plus",
            "vanilla.GRAY_STAINED_GLASS_PANE",
            "{color:label_accent}Raise cap",
            List.of("{color:label_body}Click +1", "{color:label_muted}Shift click +5"));
    public static CastModeDef confirm = new CastModeDef(
            "confirm",
            "vanilla.LIME_STAINED_GLASS_PANE",
            "{color:resonance_high}Confirm",
            List.of("{color:label_muted}Give this artifact to yourself."));
    public static CastModeDef cancel = new CastModeDef(
            "cancel",
            "vanilla.RED_STAINED_GLASS_PANE",
            "{color:corruption}Cancel",
            List.of("{color:label_muted}Close without giving an item."));

    public static List<Integer> reservedSlots() {
        List<Integer> slots = new ArrayList<>();
        slots.add(previewSlot);
        slots.add(capMinusSlot);
        slots.add(capPlusSlot);
        slots.add(confirmSlot);
        slots.add(cancelSlot);
        slots.addAll(raritySlots);
        return Collections.unmodifiableList(slots);
    }
}
