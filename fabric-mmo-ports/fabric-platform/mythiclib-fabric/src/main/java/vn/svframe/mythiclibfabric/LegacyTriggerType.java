package vn.svframe.mythiclibfabric;

import java.util.Locale;

/**
 * Fabric-side counterpart of MythicLib's legacy TriggerType registry.
 *
 * The IDs deliberately match the Bukkit implementation so existing MMOItems/
 * MMOCore configuration and bridges do not need a second naming scheme.
 */
public enum LegacyTriggerType {
    KILL_ENTITY,
    KILL_PLAYER,
    ATTACK,
    DAMAGED,
    DAMAGED_BY_ENTITY,
    DEATH,
    PLACE_BLOCK,
    BREAK_BLOCK,
    SHOOT_BOW,
    ARROW_TICK,
    ARROW_HIT,
    ARROW_LAND,
    SHOOT_TRIDENT,
    TRIDENT_TICK,
    TRIDENT_HIT,
    TRIDENT_LAND,
    RIGHT_CLICK,
    LEFT_CLICK,
    SHIFT_RIGHT_CLICK,
    SHIFT_LEFT_CLICK,
    DROP_ITEM,
    SHIFT_DROP_ITEM,
    SWAP_ITEMS,
    SHIFT_SWAP_ITEMS,
    LOGIN,
    SNEAK,
    TELEPORT,
    TIMER,
    CAST,
    COMMAND,
    API,
    EQUIP_ARMOR,
    UNEQUIP_ARMOR,
    PLUGIN;

    public static LegacyTriggerType parse(String raw) {
        if (raw == null) throw new IllegalArgumentException("Trigger type cannot be null");
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return LegacyTriggerType.valueOf(normalized);
    }

    public String lowerCaseId() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public boolean actionHandSpecific() {
        return switch (this) {
            case RIGHT_CLICK, LEFT_CLICK, SHIFT_RIGHT_CLICK, SHIFT_LEFT_CLICK,
                    SHOOT_BOW, SHOOT_TRIDENT, SWAP_ITEMS, SHIFT_SWAP_ITEMS -> true;
            default -> false;
        };
    }

    public boolean passive() {
        return this != CAST && this != COMMAND && this != API && this != PLUGIN;
    }
}
