package vn.svframe.mmoitemsfabric;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Locale;

/** Applies legacy repair/repair-percent consumables to custom or vanilla durability. */
public final class MMOItemsRepairInteraction {
    public enum Result { NONE, SUCCESS }

    private MMOItemsRepairInteraction() {}

    public static Result apply(ServerPlayerEntity player, ItemStack consumable, ItemStack target) {
        if (player == null || consumable == null || target == null || consumable.isEmpty() || target.isEmpty()) return Result.NONE;
        MMOItemsGameplayMod.Template source = MMOItemsGameplayMod.template(consumable);
        if (source == null || !MMOItemsTypeRegistry.isA(source.type(), "CONSUMABLE")) return Result.NONE;
        if (!MMOItemsRequirementGate.canUse(player, consumable)) return Result.NONE;

        double flat = numeric(source, "repair");
        double percent = numeric(source, "repair-percent");
        if (flat <= 0.0 && percent <= 0.0) return Result.NONE;
        if (!referencesMatch(MMOItemsLegacyItemOptions.string(consumable, "repair-type", ""),
                MMOItemsLegacyItemOptions.string(target, "repair-type", ""))) return Result.NONE;

        MMOItemsGameplayMod.Template targetTemplate = MMOItemsGameplayMod.template(target);
        if (targetTemplate != null) {
            if (!MMOItemsRequirementGate.canUse(player, target)) return Result.NONE;
            if (MMOItemsLegacyItemOptions.bool(target, "disable-repairing", false)) return Result.NONE;
        }
        if (targetTemplate != null && targetTemplate.maxDurability() > 0) {
            MMOItemsGameplayMod.hydrate(target);
            int current = MMOItemsGameplayMod.durability(target);
            int maximum = MMOItemsGameplayMod.maxDurability(target);
            if (maximum <= 0 || current >= maximum) return Result.NONE;
            int power = repairAmount(flat, percent, maximum);
            if (power <= 0) return Result.NONE;
            MMOItemsGameplayMod.setDurability(target, Math.min(maximum, current + power));
            syncVanillaBar(target, MMOItemsGameplayMod.durability(target), maximum);
            return Result.SUCCESS;
        }

        if (!target.isDamageable() || target.getDamage() <= 0) return Result.NONE;
        int power = repairAmount(flat, percent, target.getMaxDamage());
        if (power <= 0) return Result.NONE;
        target.setDamage(Math.max(0, target.getDamage() - power));
        return Result.SUCCESS;
    }

    private static double numeric(MMOItemsGameplayMod.Template template, String key) {
        Double value = template.numericStats().get(key);
        if (value == null) value = template.numericStats().get(key.replace('-', '_'));
        return value == null ? 0.0 : value;
    }

    private static int repairAmount(double flat, double percent, int maximum) {
        if (flat > 0.0) return Math.max(1, (int) Math.floor(flat));
        return percent <= 0.0 ? 0 : Math.max(1, (int) Math.floor(maximum * percent / 100.0));
    }

    private static boolean referencesMatch(String first, String second) {
        String a = normalize(first);
        String b = normalize(second);
        return a.equals("all") || b.equals("all") || a.equals(b);
    }

    private static void syncVanillaBar(ItemStack stack, int current, int maximum) {
        if (MMOItemsLegacyItemOptions.bool(stack, "hide-durability-bar", false)) {
            if (stack.isDamageable()) stack.setDamage(0);
            return;
        }
        if (!stack.isDamageable() || maximum <= 0) return;
        int vanillaMax = stack.getMaxDamage();
        int damage = current >= maximum ? 0 : Math.max(1, (int) ((1.0 - (double) current / maximum) * vanillaMax));
        stack.setDamage(Math.min(vanillaMax, damage));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
