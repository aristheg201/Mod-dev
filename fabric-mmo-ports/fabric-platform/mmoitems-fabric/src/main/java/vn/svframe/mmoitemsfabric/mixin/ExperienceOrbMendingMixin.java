package vn.svframe.mmoitemsfabric.mixin;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.mmoitemsfabric.MMOItemsGameplayMod;
import vn.svframe.mmoitemsfabric.MMOItemsLegacyItemOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Extends vanilla Mending so custom MMOItems durability participates in the same equipment pool. */
@Mixin(ExperienceOrbEntity.class)
public abstract class ExperienceOrbMendingMixin {
    @Inject(method = "repairPlayerGears", at = @At("HEAD"), cancellable = true)
    private void mmoitems$repairCustomDurability(ServerPlayerEntity player, int amount,
                                                  CallbackInfoReturnable<Integer> cir) {
        if (amount <= 0) return;
        List<ItemStack> initial = eligible(player);
        boolean hasCustom = initial.stream().anyMatch(ExperienceOrbMendingMixin::customDamaged);
        if (!hasCustom) return;

        int remaining = amount;
        while (remaining > 0) {
            List<ItemStack> candidates = eligible(player);
            if (candidates.isEmpty()) break;
            ItemStack stack = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            int repaired;
            if (customDamaged(stack)) {
                MMOItemsGameplayMod.hydrate(stack);
                int max = MMOItemsGameplayMod.maxDurability(stack);
                int current = MMOItemsGameplayMod.durability(stack);
                repaired = Math.min(remaining * 2, Math.max(0, max - current));
                if (repaired <= 0) continue;
                int next = Math.min(max, current + repaired);
                MMOItemsGameplayMod.setDurability(stack, next);
                updateBar(stack, next, max);
            } else {
                repaired = Math.min(remaining * 2, stack.getDamage());
                if (repaired <= 0) continue;
                stack.setDamage(Math.max(0, stack.getDamage() - repaired));
            }
            remaining -= Math.max(1, (repaired + 1) / 2);
        }
        cir.setReturnValue(Math.max(0, remaining));
    }

    private static List<ItemStack> eligible(ServerPlayerEntity player) {
        List<ItemStack> out = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getEquippedStack(slot);
            if (stack == null || stack.isEmpty() || !hasMending(player, stack)) continue;
            MMOItemsGameplayMod.Template template = MMOItemsGameplayMod.template(stack);
            if (template != null && template.maxDurability() > 0) MMOItemsGameplayMod.hydrate(stack);
            if (customDamaged(stack) || stack.isDamaged()) out.add(stack);
        }
        return out;
    }

    private static boolean customDamaged(ItemStack stack) {
        MMOItemsGameplayMod.Template template = MMOItemsGameplayMod.template(stack);
        if (template == null || template.maxDurability() <= 0) return false;
        MMOItemsGameplayMod.hydrate(stack);
        int max = MMOItemsGameplayMod.maxDurability(stack);
        return max > 0 && MMOItemsGameplayMod.durability(stack) < max;
    }

    private static boolean hasMending(ServerPlayerEntity player, ItemStack stack) {
        try {
            var registry = player.getServerWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
            var enchantment = registry.getEntry(Enchantments.MENDING).orElse(null);
            return enchantment != null && EnchantmentHelper.getLevel(enchantment, stack) > 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void updateBar(ItemStack stack, int current, int customMax) {
        if (MMOItemsLegacyItemOptions.bool(stack, "hide-durability-bar", false)) {
            if (stack.isDamageable()) stack.setDamage(0);
            return;
        }
        int vanillaMax = stack.getMaxDamage();
        if (vanillaMax <= 0 || customMax <= 0) return;
        int damage = current >= customMax ? 0 : Math.max(1, (int) ((1.0 - (double) current / customMax) * vanillaMax));
        stack.setDamage(Math.min(vanillaMax, damage));
    }
}
