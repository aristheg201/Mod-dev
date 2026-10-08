package io.github.aristheg201.cobblemonworld.shop;

import com.google.gson.Gson;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import io.github.aristheg201.cobblemonworld.network.ShopSnapshotPayload;
import io.github.aristheg201.cobblemonworld.network.ShopBuyPayload;
import io.github.aristheg201.cobblemonworld.npc.NpcDefinitionRegistry;
import io.github.aristheg201.cobblemonworld.progression.ProgressionStore;
import io.github.aristheg201.cobblemonworld.story.ObjectiveBridge;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.math.BigDecimal;
import java.util.*;

public final class ShopService {
    private static final Gson GSON = new Gson();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> LAST_PURCHASE = new HashMap<>();
    private static boolean loggedEconomyFailure;
    private static boolean disabled;
    private static boolean failNextGrant;
    public static void qaFailNextGrant() {
        if (!Boolean.getBoolean("cworld.qa.production")) throw new IllegalStateException("QA fault injection disabled");
        failNextGrant = true;
    }
    private record Session(String shop, UUID npc, long expires) {}
    private ShopService() {}
    public static void register() {
        ShopRegistry.register();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SESSIONS.remove(handler.player.getUUID()); LAST_PURCHASE.remove(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { invalidateSessions(); loggedEconomyFailure = false; disabled = false; });
    }
    public static void invalidateSessions() { SESSIONS.clear(); LAST_PURCHASE.clear(); }
    public static boolean open(ServerPlayer player, NPCEntity npc, String npcId) {
        var d = ShopRegistry.INSTANCE.forNpc(npcId);
        if (d == null) return false;
        if (!access(player, d.requiredFlags()) || !npc.level().equals(player.level()) || player.distanceToSqr(npc) > 64) return false;
        SESSIONS.put(player.getUUID(), new Session(d.id(), npc.getUUID(), player.serverLevel().getGameTime() + 2400));
        send(player, d, "", true);
        return true;
    }
    public static boolean access(ServerPlayer player, List<String> flags) {
        return flags == null || ProgressionStore.INSTANCE.getOrCreate(player.getUUID()).storyFlags.containsAll(flags);
    }
    private static BeastCoinAccount account() {
        if (disabled) return null;
        try {
            if (!FabricLoader.getInstance().isModLoaded("beconomy")) throw new IllegalStateException("BEconomy mod missing");
            return new BEconomyAccount();
        } catch (RuntimeException | LinkageError e) {
            if (!loggedEconomyFailure) { loggedEconomyFailure = true; CobblemonWorldMod.LOGGER.warn("BeastCoin shops unavailable: {}", e.toString()); }
            return null;
        }
    }
    public static void buy(ServerPlayer player, ShopBuyPayload request) {
        var session = SESSIONS.get(player.getUUID());
        var definition = ShopRegistry.INSTANCE.get(request.shop());
        long now = player.serverLevel().getGameTime();
        if (definition == null) return;
        if (session == null || !session.shop().equals(request.shop())) {
            if (access(player, definition.requiredFlags())) send(player, definition, "access", false);
            return;
        }
        var npc = player.serverLevel().getEntity(session.npc());
        if (!(npc instanceof NPCEntity) || !npc.isAlive() || player.distanceToSqr(npc) > 64 || now > session.expires() || !player.isAlive()
                || !access(player, definition.requiredFlags())) { send(player, definition, "access", false); SESSIONS.remove(player.getUUID()); return; }
        Long previous = LAST_PURCHASE.get(player.getUUID());
        if (previous != null && now - previous < 8) { send(player, definition, "busy", false); return; }
        LAST_PURCHASE.put(player.getUUID(), now);
        SESSIONS.put(player.getUUID(), new Session(session.shop(), session.npc(), now + 2400));
        if (request.quantity() < 1 || request.quantity() > 16) { send(player, definition, "invalid", false); return; }
        var entry = ShopRegistry.INSTANCE.entries(definition.id()).stream().filter(e -> e.id().equals(request.entry())).findFirst().orElse(null);
        if (entry == null || !access(player, entry.requiredFlags())) { send(player, definition, "entry", false); return; }
        if (!entry.available() || !ShopRegistry.registered(entry.item())) { send(player, definition, "item", false); return; }
        var wallet = account();
        if (wallet == null) { send(player, definition, "economy", false); return; }
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.item())), Math.multiplyExact(entry.quantity(), request.quantity()));
        String result;
        try {
            var inventory = new InventoryGrant(player, stack);
            boolean inject = Boolean.getBoolean("cworld.qa.production") && failNextGrant; failNextGrant = false;
            PurchaseTransaction.Grant grant = !inject ? inventory : new PurchaseTransaction.Grant() {
                public boolean prepare() { return inventory.prepare(); }
                public void commit() { inventory.commit(); throw new IllegalStateException("Injected post-grant failure for runtime refund QA"); }
                public void rollback() { inventory.rollback(); }
            };
            result = PurchaseTransaction.execute(wallet, player.getUUID(), Math.multiplyExact(entry.price(), request.quantity()), grant);
        } catch (RuntimeException e) {
            disabled = true;
            CobblemonWorldMod.LOGGER.error("Shop transaction could not be reconciled for {} shop={} entry={}; purchases disabled until restart", player.getUUID(), definition.id(), entry.id(), e);
            result = "economy";
        }
        if (result.equals("success")) {
            try {
                ObjectiveBridge.record(player, "purchase", entry.item(), stack.getCount());
                ObjectiveBridge.record(player, "get_item", entry.item(), stack.getCount());
            } catch (RuntimeException e) {
                CobblemonWorldMod.LOGGER.error("Purchase completed, but its objective hook failed for {} item={}", player.getUUID(), entry.item(), e);
            }
        }
        send(player, definition, result, false);
    }
    private static void send(ServerPlayer player, ShopDefinition d, String result, boolean open) {
        var wallet = account();
        BigDecimal balance = null;
        try { if (wallet != null) balance = wallet.balance(player.getUUID()); }
        catch (RuntimeException e) { CobblemonWorldMod.LOGGER.warn("BeastCoin balance lookup failed for {}", player.getUUID(), e); }
        var npc = NpcDefinitionRegistry.INSTANCE.get(d.npc());
        var entries = ShopRegistry.INSTANCE.entries(d.id()).stream().filter(e -> access(player, e.requiredFlags())).toList();
        var snapshot = new ShopSnapshot(d.id(), d.titleKey(), d.descriptionKey(), npc == null ? "" : npc.displayName(),
                balance == null ? "" : balance.stripTrailingZeros().toPlainString(), balance != null, d.categories(), entries, result);
        ServerPlayNetworking.send(player, new ShopSnapshotPayload(GSON.toJson(snapshot), open));
    }
}
