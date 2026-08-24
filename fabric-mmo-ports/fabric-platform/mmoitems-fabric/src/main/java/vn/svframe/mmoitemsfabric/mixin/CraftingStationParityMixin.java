package vn.svframe.mmoitemsfabric.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import vn.svframe.mmoitemsfabric.MMOItemsCraftingStationMod;
import vn.svframe.mmoitemsfabric.MMOItemsEconomyBridge;

import java.util.Map;

/** Repairs legacy 6.10.1 crafting-station grammar and money transaction semantics. */
@Mixin(value = MMOItemsCraftingStationMod.class, remap = false)
public abstract class CraftingStationParityMixin {
    private static final ThreadLocal<Boolean> MMOITEMS_CRAFTING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Double> MMOITEMS_MONEY = ThreadLocal.withInitial(() -> 0.0);

    @ModifyArgs(
            method = "parseStation",
            at = @At(value = "INVOKE", target = "Lvn/svframe/mmoitemsfabric/MMOItemsCraftingStationMod$Ingredient;<init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;II)V"),
            remap = false)
    private static void mmoitems$legacyVanillaIngredient(Args args) {
        String kind = String.valueOf(args.get(0));
        String itemType = String.valueOf(args.get(1));
        String id = String.valueOf(args.get(2));
        if (kind.equalsIgnoreCase("vanilla") && (id == null || id.isBlank()) && itemType != null && !itemType.isBlank()) {
            args.set(1, "");
            args.set(2, itemType);
        }
    }

    @Redirect(
            method = "conditionsMet",
            at = @At(value = "INVOKE", target = "Lvn/svframe/mmoitemsfabric/MMOItemsCraftingStationMod;first(Ljava/util/Map;[Ljava/lang/String;)Ljava/lang/String;"),
            remap = false)
    private static String mmoitems$legacyConditionArgument(Map<String, String> params, String[] keys) {
        boolean permission = false;
        for (String key : keys) if (key.equalsIgnoreCase("permission") || key.equalsIgnoreCase("perm") || key.equalsIgnoreCase("node")) permission = true;
        if (permission) {
            String list = params.get("list");
            if (list != null && !list.isBlank()) return list.trim();
        }
        for (String key : keys) {
            String value = params.get(key);
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    @Redirect(
            method = "conditionsMet",
            at = @At(value = "INVOKE", target = "Lvn/svframe/mmoitemsfabric/MMOItemsCraftingStationMod$EconomyAccess;hasAndWithdraw(Lnet/minecraft/server/network/ServerPlayerEntity;DZ)Z"),
            remap = false)
    private static boolean mmoitems$realEconomy(ServerPlayerEntity player, double amount, boolean ignoredWithdraw) {
        if (Boolean.TRUE.equals(MMOITEMS_CRAFTING.get())) MMOITEMS_MONEY.set(Math.max(MMOITEMS_MONEY.get(), amount));
        return MMOItemsEconomyBridge.canAfford(player, amount);
    }

    @Inject(method = "craft", at = @At("HEAD"), remap = false)
    private static void mmoitems$beginCraft(ServerPlayerEntity player, @Coerce Object station, String recipeId, CallbackInfo ci) {
        MMOITEMS_CRAFTING.set(true);
        MMOITEMS_MONEY.set(0.0);
    }

    @Inject(
            method = "craft",
            at = @At(value = "INVOKE", target = "Lvn/svframe/mmoitemsfabric/MMOItemsCraftingStationMod;consumeIngredients(Lnet/minecraft/server/network/ServerPlayerEntity;Ljava/util/List;)V"),
            cancellable = true,
            remap = false)
    private static void mmoitems$chargeBeforeConsume(ServerPlayerEntity player, @Coerce Object station, String recipeId, CallbackInfo ci) {
        double amount = MMOITEMS_MONEY.get();
        if (amount > 0.0 && !MMOItemsEconomyBridge.withdraw(player, amount)) ci.cancel();
    }

    @Inject(method = "craft", at = @At("RETURN"), remap = false)
    private static void mmoitems$finishCraft(ServerPlayerEntity player, @Coerce Object station, String recipeId, CallbackInfo ci) {
        MMOITEMS_CRAFTING.remove();
        MMOITEMS_MONEY.remove();
    }
}
