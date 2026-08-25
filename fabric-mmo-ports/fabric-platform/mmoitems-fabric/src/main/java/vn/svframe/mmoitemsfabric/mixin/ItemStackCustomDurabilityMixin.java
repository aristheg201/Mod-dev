package vn.svframe.mmoitemsfabric.mixin;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mmoitemsfabric.MMOItemsGameplayMod;
import vn.svframe.mmoitemsfabric.MMOItemsLegacyItemOptions;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

@Mixin(ItemStack.class)
public abstract class ItemStackCustomDurabilityMixin {
    @Inject(
            method = "damage(ILnet/minecraft/server/world/ServerWorld;Lnet/minecraft/server/network/ServerPlayerEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void mmoitems$customDurability(int amount, ServerWorld world, ServerPlayerEntity player,
                                           Consumer<Item> breakCallback, CallbackInfo ci) {
        ItemStack stack = (ItemStack) (Object) this;
        MMOItemsGameplayMod.Template template = MMOItemsGameplayMod.template(stack);
        if (template == null || template.maxDurability() <= 0) return;

        ci.cancel();
        MMOItemsGameplayMod.hydrate(stack);
        if (amount <= 0 || (player != null && player.isInCreativeMode())) return;

        int unbreaking = unbreaking(world, stack);
        if (unbreaking > 0 && ThreadLocalRandom.current().nextInt(unbreaking + 1) != 0) return;

        int current = MMOItemsGameplayMod.durability(stack);
        int next = Math.max(0, current - amount);
        MMOItemsGameplayMod.setDurability(stack, next);
        updateBar(stack, next, template.maxDurability());
        if (next > 0) return;

        if (MMOItemsGameplayMod.lostWhenBroken(stack)) {
            breakStack(stack, breakCallback);
            return;
        }

        if (!MMOItemsLegacyItemOptions.bool(stack, "break-downgrade", false)) return;
        int level = MMOItemsGameplayMod.upgradeLevel(stack);
        if (level <= template.upgradeMin()) {
            breakStack(stack, breakCallback);
            return;
        }

        MMOItemsGameplayMod.setUpgradeLevel(stack, level - 1);
        MMOItemsGameplayMod.setDurability(stack, template.maxDurability());
        updateBar(stack, template.maxDurability(), template.maxDurability());
    }

    private static void breakStack(ItemStack stack, Consumer<Item> breakCallback) {
        Item broken = stack.getItem();
        stack.decrement(1);
        if (breakCallback != null) breakCallback.accept(broken);
    }

    private static int unbreaking(ServerWorld world, ItemStack stack) {
        try {
            var registry = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
            var enchantment = registry.getEntry(Enchantments.UNBREAKING).orElse(null);
            return enchantment == null ? 0 : EnchantmentHelper.getLevel(enchantment, stack);
        } catch (RuntimeException ignored) {
            return 0;
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
