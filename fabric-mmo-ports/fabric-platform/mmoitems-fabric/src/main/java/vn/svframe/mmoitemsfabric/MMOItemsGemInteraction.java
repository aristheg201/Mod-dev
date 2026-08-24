package vn.svframe.mmoitemsfabric;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Inventory cursor gemstone application matching MMOItems GemStone interaction semantics. */
public final class MMOItemsGemInteraction {
    public enum Result { NONE, FAILURE, SUCCESS }

    private static final Set<String> WEAPON_TYPES = Set.of(
            "SWORD", "DAGGER", "SPEAR", "HAMMER", "GAUNTLET", "WHIP", "STAFF", "GREATSTAFF",
            "BOW", "CROSSBOW", "MUSKET", "LUTE", "GREATSWORD", "LONG_SWORD", "KATANA", "HALBERD",
            "AXE", "GREATAXE", "GREATHAMMER");

    private static final Set<String> NON_MERGEABLE = Set.of(
            "custom-model-data", "required-level", "success-rate", "max-durability", "max-item-damage",
            "gem-sockets", "gem-color", "item-type-restriction", "upgrade", "revision-id", "item-damage",
            "will-break", "unbreakable", "hide-durability-bar", "disable-crafting", "disable-smelting",
            "disable-smithing", "disable-enchanting", "disable-repairing", "disable-interaction",
            "disable-drop", "disable-death-drop", "item-cooldown", "soulbound-level", "soulbinding-chance",
            "soulbound-break-chance", "unstackable", "gem-upgrade-scaling");

    private MMOItemsGemInteraction() {}

    public static Result apply(ServerPlayerEntity player, ItemStack gemStack, ItemStack targetStack) {
        if (player == null || gemStack == null || targetStack == null || gemStack.isEmpty() || targetStack.isEmpty()) return Result.NONE;
        MMOItemsGameplayMod.Template gem = MMOItemsGameplayMod.template(gemStack);
        MMOItemsGameplayMod.Template target = MMOItemsGameplayMod.template(targetStack);
        if (gem == null || target == null || !isGem(gem)) return Result.NONE;

        MMOItemsGameplayMod.hydrate(targetStack);
        List<String> sockets = new ArrayList<>(MMOItemsGameplayMod.emptySockets(targetStack));
        int socket = findSocket(sockets, gem.gemColor());
        if (socket < 0) return Result.NONE;
        if (!supportsTarget(gem, target)) return Result.NONE;

        double success = gem.successRate() == 0.0 ? 100.0 : gem.successRate();
        if (ThreadLocalRandom.current().nextDouble() > success / 100.0) return Result.FAILURE;

        String consumedSocket = sockets.remove(socket);
        MMOItemsGameplayMod.setEmptySockets(targetStack, sockets);
        MMOItemsGameplayMod.addGemStats(targetStack, mergeableStats(gem.numericStats()), 1.0);

        // Preserve the socket color consumed in state so a later unsocket implementation can restore it.
        var data = MMOItemsGameplayMod.customData(targetStack);
        String history = data.getString("mmoitems_gem_socket_history");
        data.putString("mmoitems_gem_socket_history", history.isEmpty() ? consumedSocket : history + "\u001f" + consumedSocket);
        MMOItemsGameplayMod.writeCustomData(targetStack, data);
        return Result.SUCCESS;
    }

    private static boolean isGem(MMOItemsGameplayMod.Template template) {
        return template.type().equalsIgnoreCase("GEM_STONE");
    }

    private static int findSocket(List<String> sockets, String gemColor) {
        String wanted = normalizeColor(gemColor);
        for (int i = 0; i < sockets.size(); i++) if (normalizeColor(sockets.get(i)).equals(wanted)) return i;
        for (int i = 0; i < sockets.size(); i++) if (normalizeColor(sockets.get(i)).equals("uncolored")) return i;
        return -1;
    }

    private static boolean supportsTarget(MMOItemsGameplayMod.Template gem, MMOItemsGameplayMod.Template target) {
        if (gem.itemTypeRestriction().isEmpty()) return true;
        for (String raw : gem.itemTypeRestriction()) {
            String allowed = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            if (allowed.equals(target.type())) return true;
            if (allowed.equals("WEAPON") && WEAPON_TYPES.contains(target.type())) return true;
        }
        return false;
    }

    private static Map<String, Double> mergeableStats(Map<String, Double> source) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT).replace('_', '-');
            if (!NON_MERGEABLE.contains(key)) out.put(key, entry.getValue());
        }
        return Map.copyOf(out);
    }

    private static String normalizeColor(String value) {
        return value == null || value.isBlank() ? "uncolored" : value.trim().toLowerCase(Locale.ROOT);
    }
}
