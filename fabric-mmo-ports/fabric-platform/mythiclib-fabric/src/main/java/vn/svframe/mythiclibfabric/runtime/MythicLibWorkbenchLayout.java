package vn.svframe.mythiclibfabric.runtime;

import java.util.Arrays;

/** Exact slot topology used by MythicLib 1.7.1 Super/Mega Workbench mappings. */
public final class MythicLibWorkbenchLayout {
    public enum Kind { SUPER, MEGA }

    public static final int RESULT_SLOT = 25;
    private static final int[] SUPER_INPUT = {
            1, 2, 3, 4, 5,
            10, 11, 12, 13, 14,
            19, 20, 21, 22, 23,
            28, 29, 30, 31, 32,
            37, 38, 39, 40, 41
    };
    private static final int[] SUPER_EDGE = {
            0, 9, 18, 27, 36,
            6, 15, 24, 33, 42,
            7, 16, 34, 43,
            8, 17, 26, 35, 44
    };
    private static final int[] MEGA_INPUT = {
            0, 1, 2, 3, 4, 5,
            9, 10, 11, 12, 13, 14,
            18, 19, 20, 21, 22, 23,
            27, 28, 29, 30, 31, 32,
            36, 37, 38, 39, 40, 41,
            45, 46, 47, 48, 49, 50
    };
    private static final int[] MEGA_EDGE = {
            6, 15, 24, 33, 42, 51,
            7, 16, 34, 43, 52,
            8, 17, 26, 35, 44, 53
    };

    private final Kind kind;
    private final int size;
    private final int width;
    private final int height;
    private final int[] inputSlots;
    private final int[] edgeSlots;

    private MythicLibWorkbenchLayout(Kind kind, int size, int width, int height, int[] inputSlots, int[] edgeSlots) {
        this.kind = kind;
        this.size = size;
        this.width = width;
        this.height = height;
        this.inputSlots = inputSlots;
        this.edgeSlots = edgeSlots;
    }

    public static MythicLibWorkbenchLayout superWorkbench() {
        return new MythicLibWorkbenchLayout(Kind.SUPER, 45, 5, 5, SUPER_INPUT, SUPER_EDGE);
    }

    public static MythicLibWorkbenchLayout megaWorkbench() {
        return new MythicLibWorkbenchLayout(Kind.MEGA, 54, 6, 6, MEGA_INPUT, MEGA_EDGE);
    }

    public Kind kind() { return kind; }
    public int size() { return size; }
    public int width() { return width; }
    public int height() { return height; }
    public int resultSlot() { return RESULT_SLOT; }
    public int[] inputSlots() { return inputSlots.clone(); }
    public int[] edgeSlots() { return edgeSlots.clone(); }

    public boolean isInputSlot(int rawSlot) { return Arrays.binarySearch(inputSlots, rawSlot) >= 0; }
    public boolean isEdgeSlot(int rawSlot) {
        for (int slot : edgeSlots) if (slot == rawSlot) return true;
        return false;
    }
    public boolean isResultSlot(int rawSlot) { return rawSlot == RESULT_SLOT; }

    /** X coordinate in the logical recipe grid or -1 for non-input slots. */
    public int gridX(int rawSlot) {
        int index = Arrays.binarySearch(inputSlots, rawSlot);
        return index < 0 ? -1 : index % width;
    }

    /** Y coordinate in the logical recipe grid or -1 for non-input slots. */
    public int gridY(int rawSlot) {
        int index = Arrays.binarySearch(inputSlots, rawSlot);
        return index < 0 ? -1 : -(index / width);
    }
}
