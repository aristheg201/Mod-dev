package vn.svframe.mythiclibfabric.runtime;

import java.util.Arrays;
import java.util.Map;

/** Exact raw-slot mappings used by MythicLib 1.7.1 for vanilla crafting stations. */
public final class MythicLibStationMappings {
    public enum Kind { PLAYER_CRAFTING, WORKBENCH, FURNACE, SMITHING_LEGACY, SMITHING_MODERN }

    public record Mapping(
            Kind kind,
            MythicLibCraftingRuntime.Station station,
            int resultSlot,
            int[] mainSlots,
            Map<String, int[]> sideSlots,
            int width,
            int height
    ) {
        public Mapping {
            mainSlots = mainSlots.clone();
            sideSlots = Map.copyOf(sideSlots);
        }
        @Override public int[] mainSlots() { return mainSlots.clone(); }
        @Override public Map<String, int[]> sideSlots() {
            java.util.LinkedHashMap<String, int[]> copy = new java.util.LinkedHashMap<>();
            sideSlots.forEach((key, value) -> copy.put(key, value.clone()));
            return java.util.Collections.unmodifiableMap(copy);
        }
        public int rawMainSlot(int logicalSlot) {
            return logicalSlot < 0 || logicalSlot >= mainSlots.length ? -1 : mainSlots[logicalSlot];
        }
        public int rawSideSlot(String side, int logicalSlot) {
            int[] slots = sideSlots.get(side);
            return slots == null || logicalSlot < 0 || logicalSlot >= slots.length ? -1 : slots[logicalSlot];
        }
        public boolean isInputSlot(int rawSlot) {
            if (Arrays.binarySearch(sorted(mainSlots), rawSlot) >= 0) return true;
            for (int[] slots : sideSlots.values()) for (int slot : slots) if (slot == rawSlot) return true;
            return false;
        }
        private static int[] sorted(int[] values) { int[] copy = values.clone(); Arrays.sort(copy); return copy; }
    }

    private static final Mapping PLAYER_CRAFTING = new Mapping(
            Kind.PLAYER_CRAFTING, MythicLibCraftingRuntime.Station.WORKBENCH, 0,
            new int[]{1, 2, 3, 4}, Map.of(), 2, 2);
    private static final Mapping WORKBENCH = new Mapping(
            Kind.WORKBENCH, MythicLibCraftingRuntime.Station.WORKBENCH, 0,
            new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, Map.of(), 3, 3);
    private static final Mapping FURNACE = new Mapping(
            Kind.FURNACE, MythicLibCraftingRuntime.Station.FURNACE, 2,
            new int[]{0}, Map.of("fuel", new int[]{1}), 1, 1);
    private static final Mapping SMITHING_LEGACY = new Mapping(
            Kind.SMITHING_LEGACY, MythicLibCraftingRuntime.Station.SMITHING, 2,
            new int[]{0}, Map.of("ingot", new int[]{1}), 1, 1);
    private static final Mapping SMITHING_MODERN = new Mapping(
            Kind.SMITHING_MODERN, MythicLibCraftingRuntime.Station.SMITHING, 3,
            new int[]{1}, Map.of("template", new int[]{0}, "ingot", new int[]{2}), 1, 1);

    private MythicLibStationMappings() {}
    public static Mapping playerCrafting() { return PLAYER_CRAFTING; }
    public static Mapping workbench() { return WORKBENCH; }
    public static Mapping furnace() { return FURNACE; }
    public static Mapping smithingLegacy() { return SMITHING_LEGACY; }
    public static Mapping smithingModern() { return SMITHING_MODERN; }
}
