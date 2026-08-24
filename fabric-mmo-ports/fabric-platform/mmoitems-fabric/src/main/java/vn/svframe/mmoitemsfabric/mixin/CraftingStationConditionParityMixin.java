package vn.svframe.mmoitemsfabric.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.mmoitemsfabric.MMOItemsCraftingStationMod;
import vn.svframe.mmoitemsfabric.MMOItemsEconomyBridge;
import vn.svframe.mmoitemsfabric.MMOItemsPermissionBridge;
import vn.svframe.mythiclibfabric.runtime.RpgProfileRegistry;
import vn.svframe.mythiclibfabric.runtime.RpgResourceRegistry;

import java.util.Map;

/** Exact 6.10.1 crafting condition checks and whenCrafting consumption for native stations. */
@Mixin(value = MMOItemsCraftingStationMod.class, remap = false)
public abstract class CraftingStationConditionParityMixin {
    @Inject(method = "conditionsMet", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mmoitems$conditions(ServerPlayerEntity player, @Coerce Object recipe,
                                             CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(allMet(player, recipe));
    }

    @Inject(
            method = "craft",
            at = @At(value = "INVOKE", target = "Lvn/svframe/mmoitemsfabric/MMOItemsCraftingStationMod;consumeIngredients(Lnet/minecraft/server/network/ServerPlayerEntity;Ljava/util/List;)V"),
            cancellable = true,
            remap = false)
    private static void mmoitems$consumeConditions(ServerPlayerEntity player, @Coerce Object station, String recipeId,
                                                    CallbackInfo ci) {
        Object recipe = ((CraftingStationStationAccessor) station).mmoitems$getRecipes().get(recipeId);
        if (recipe == null || !consume(player, recipe)) ci.cancel();
    }

    private static boolean allMet(ServerPlayerEntity player, Object recipe) {
        RpgProfileRegistry.Snapshot profile = RpgProfileRegistry.mergeOrDefault(player.getUuid());
        for (Object raw : ((CraftingStationRecipeAccessor) recipe).mmoitems$getConditions()) {
            CraftingStationConditionAccessor condition = (CraftingStationConditionAccessor) raw;
            Map<String, String> params = condition.mmoitems$getParams();
            String type = normalize(condition.mmoitems$getType());
            switch (type) {
                case "level" -> {
                    if (profile.level() < integer(params.get("level"), 0)) return false;
                }
                case "class" -> {
                    String list = params.getOrDefault("list", "");
                    boolean match = false;
                    for (String value : list.split(",")) if (value.trim().equals(profile.playerClass())) { match = true; break; }
                    if (!match) return false;
                }
                case "permission" -> {
                    String list = params.getOrDefault("list", "");
                    for (String value : list.split(",")) {
                        String permission = value.trim();
                        if (!permission.isEmpty() && !MMOItemsPermissionBridge.has(player, permission)) return false;
                    }
                }
                case "food" -> {
                    if (player.getHungerManager().getFoodLevel() < integer(params.get("amount"), 0)) return false;
                }
                case "mana", "stamina" -> {
                    if (!RpgResourceRegistry.has(player.getUuid(), type, decimal(params.get("amount"), 0.0))) return false;
                }
                case "money" -> {
                    if (!MMOItemsEconomyBridge.canAfford(player, decimal(params.get("amount"), 0.0))) return false;
                }
                case "placeholder" -> {
                    // Placeholder expressions are handled by the dedicated Text Placeholder API bridge.
                    if (!MMOItemsPlaceholderConditionBridge.test(player, params.get("placeholder"))) return false;
                }
                default -> {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean consume(ServerPlayerEntity player, Object recipe) {
        if (!allMet(player, recipe)) return false;
        for (Object raw : ((CraftingStationRecipeAccessor) recipe).mmoitems$getConditions()) {
            CraftingStationConditionAccessor condition = (CraftingStationConditionAccessor) raw;
            Map<String, String> params = condition.mmoitems$getParams();
            String type = normalize(condition.mmoitems$getType());
            switch (type) {
                case "level" -> {
                    if (bool(params.get("consume"), false)) {
                        int amount = Math.max(0, integer(params.get("level"), 0));
                        player.experienceLevel = Math.max(0, player.experienceLevel - amount);
                    }
                }
                case "food" -> {
                    int amount = Math.max(0, integer(params.get("amount"), 0));
                    player.getHungerManager().setFoodLevel(Math.max(0, player.getHungerManager().getFoodLevel() - amount));
                }
                case "mana", "stamina" -> {
                    if (!RpgResourceRegistry.consume(player.getUuid(), type, decimal(params.get("amount"), 0.0))) return false;
                }
                case "money" -> {
                    if (!MMOItemsEconomyBridge.withdraw(player, decimal(params.get("amount"), 0.0))) return false;
                }
                default -> { }
            }
        }
        return true;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT).replace("-", "").replace("_", "");
    }

    private static int integer(String value, int fallback) {
        try { return value == null ? fallback : Integer.parseInt(value.trim()); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static double decimal(String value, double fallback) {
        try { return value == null ? fallback : Double.parseDouble(value.trim()); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static boolean bool(String value, boolean fallback) {
        if (value == null) return fallback;
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "true", "yes", "on", "1" -> true;
            case "false", "no", "off", "0" -> false;
            default -> fallback;
        };
    }
}
