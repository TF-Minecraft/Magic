package net.tfminecraft.magic.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.tfminecraft.magic.GuiCache;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.registry.ElementRegistry;

/**
 * Slot map for the 54-slot (9x6) Resonance GUI. Slot indices read from GuiCache.
 */
public final class GridLayout {

    public static final int COLUMNS = 9;
    public static final int ROWS = 6;
    public static final int SIZE = COLUMNS * ROWS;

    private GridLayout() {}

    public static int slot(int row, int col) {
        return row * COLUMNS + col;
    }

    public static int characterHeadSlot() {
        return GuiCache.characterHeadSlot;
    }

    public static int castModeLeftSlot() {
        return GuiCache.castModeLeftSlot;
    }

    public static int castModeRightSlot() {
        return GuiCache.castModeRightSlot;
    }

    public static List<Integer> elementSlots() {
        return ElementRegistry.getOccupiedSlots();
    }

    public static boolean isElementSlot(int slotIndex) {
        return elementSlots().contains(slotIndex);
    }

    public static Set<Integer> reservedSlots() {
        Set<Integer> reserved = new HashSet<>();
        reserved.add(characterHeadSlot());
        reserved.add(castModeLeftSlot());
        reserved.add(castModeRightSlot());
        reserved.addAll(elementSlots());
        return Collections.unmodifiableSet(reserved);
    }

    public static List<Integer> fillerSlots() {
        Set<Integer> reserved = reservedSlots();
        List<Integer> fillers = new ArrayList<>(SIZE - reserved.size());
        for (int slotIndex = 0; slotIndex < SIZE; slotIndex++) {
            if (!reserved.contains(slotIndex)) {
                fillers.add(slotIndex);
            }
        }
        return Collections.unmodifiableList(fillers);
    }

    public static boolean isFillerSlot(int slotIndex) {
        return !reservedSlots().contains(slotIndex);
    }

    public static List<Integer> borderFillerSlots() {
        List<Integer> borderFillers = new ArrayList<>();
        for (int slotIndex : fillerSlots()) {
            if (isBorderSlot(slotIndex)) {
                borderFillers.add(slotIndex);
            }
        }
        return Collections.unmodifiableList(borderFillers);
    }

    public static List<Integer> innerFillerSlots() {
        List<Integer> innerFillers = new ArrayList<>();
        for (int slotIndex : fillerSlots()) {
            if (!isBorderSlot(slotIndex)) {
                innerFillers.add(slotIndex);
            }
        }
        return Collections.unmodifiableList(innerFillers);
    }

    public static boolean isCharacterHeadSlot(int slotIndex) {
        return slotIndex == characterHeadSlot();
    }

    public static boolean isCastModeSlot(int slotIndex) {
        return slotIndex == castModeLeftSlot() || slotIndex == castModeRightSlot();
    }

    public static boolean isBorderSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= SIZE) {
            return false;
        }
        int row = slotIndex / COLUMNS;
        int col = slotIndex % COLUMNS;
        return row == 0 || row == ROWS - 1 || col == 0 || col == COLUMNS - 1;
    }

    public static boolean validateSlots() {
        boolean ok = true;
        ok &= validateSlot("character_head", GuiCache.characterHeadSlot);
        ok &= validateSlot("cast_mode_left", GuiCache.castModeLeftSlot);
        ok &= validateSlot("cast_mode_right", GuiCache.castModeRightSlot);
        if (GuiCache.elementRow < 0 || GuiCache.elementRow >= ROWS) {
            Magic.plugin.getLogger().severe("[Magic] Invalid element_row " + GuiCache.elementRow
                    + " (must be 0-" + (ROWS - 1) + ")");
            ok = false;
        }
        return ok;
    }

    private static boolean validateSlot(String name, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= SIZE) {
            Magic.plugin.getLogger().severe("[Magic] Invalid slot " + name + ": " + slotIndex
                    + " (must be 0-" + (SIZE - 1) + ")");
            return false;
        }
        return true;
    }
}
