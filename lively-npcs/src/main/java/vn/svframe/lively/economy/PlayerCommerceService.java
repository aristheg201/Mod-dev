package vn.svframe.lively.economy;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import vn.svframe.lively.LivelyNpcs;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.model.NpcState;
import vn.svframe.lively.social.SocialEngine;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Real player purchases backed by an optional external economy and Lively's NPC business stock. */
public final class PlayerCommerceService {
    public record Result(boolean success, String message, long total, String currency, String provider) {}

    private static final ActorId SETTLEMENT = new ActorId(UUID.nameUUIDFromBytes(
            "lively:external-economy-settlement".getBytes(StandardCharsets.UTF_8)), ActorId.Kind.SYSTEM);
    private static final long SETTLEMENT_RESERVE = 10_000_000_000_000L;

    private final MinecraftServer server;
    private final EconomyRouter router;

    public PlayerCommerceService(MinecraftServer server, EconomyRouter router) {
        this.server = server;
        this.router = router;
        LivelyApi.economy().ensureWallet(SETTLEMENT, SETTLEMENT_RESERVE);
    }

    public Optional<EconomyEngine.Business> businessForNpc(UUID npcId) {
        ActorId owner = new ActorId(npcId, ActorId.Kind.NPC);
        return LivelyApi.economy().snapshot().businesses().values().stream()
                .filter(business -> business.owner().equals(owner)).findFirst();
    }

    public boolean present(ServerPlayerEntity player, UUID npcId) {
        EconomyEngine.Business business = businessForNpc(npcId).orElse(null);
        if (business == null) return false;
        if (!allowed(player, business, npcId)) return true;
        String currency = currencyFor(business);
        EconomyRouter.Resolution resolution = router.resolve(currency).orElse(null);
        String provider = resolution == null ? "unavailable" : resolution.providerId();

        player.sendMessage(Text.literal("[" + business.name() + "]").formatted(Formatting.GOLD, Formatting.BOLD), false);
        if (!business.open()) {
            player.sendMessage(Text.literal("Cửa hàng hiện đang đóng.").formatted(Formatting.GRAY), false);
            return true;
        }
        if (resolution == null) {
            player.sendMessage(Text.literal("Không có economy provider cho currency '" + currency + "'.").formatted(Formatting.RED), false);
            return true;
        }

        EconomyEngine.Snapshot snapshot = LivelyApi.economy().snapshot();
        List<EconomyEngine.Stock> stocks = snapshot.stocks().values().stream()
                .filter(stock -> stock.key().businessId().equals(business.id()))
                .sorted(Comparator.comparing(stock -> stock.key().itemId())).limit(24).toList();
        if (stocks.isEmpty()) {
            player.sendMessage(Text.literal("Hết hàng. Chủ tiệm cũng không thể bán không khí mãi được.").formatted(Formatting.GRAY), false);
            return true;
        }

        player.sendMessage(Text.literal("Currency: " + currency + " via " + provider).formatted(Formatting.DARK_GRAY), false);
        for (EconomyEngine.Stock stock : stocks) {
            MutableText line = Text.literal("• " + stock.key().itemId() + "  $" + stock.price() + "  [" + stock.quantity() + "] ")
                    .formatted(Formatting.WHITE);
            appendBuy(line, business.id(), stock.key().itemId(), 1, "[1]");
            appendBuy(line, business.id(), stock.key().itemId(), 8, " [8]");
            appendBuy(line, business.id(), stock.key().itemId(), 64, " [64]");
            player.sendMessage(line, false);
        }
        return true;
    }

    public CompletableFuture<Result> buy(ServerPlayerEntity requester, UUID businessId, String itemId, int quantity) {
        if (quantity <= 0 || quantity > 2304) return CompletableFuture.completedFuture(new Result(false, "Số lượng không hợp lệ.", 0L, "", ""));
        Identifier identifier = Identifier.tryParse(itemId);
        if (identifier == null || !Registries.ITEM.containsId(identifier)) {
            return CompletableFuture.completedFuture(new Result(false, "Item không tồn tại: " + itemId, 0L, "", ""));
        }

        EconomyEngine.Snapshot initial = LivelyApi.economy().snapshot();
        EconomyEngine.Business business = initial.businesses().get(businessId);
        EconomyEngine.Stock stock = initial.stocks().get(new EconomyEngine.StockKey(businessId, itemId));
        if (business == null || !business.open() || stock == null || stock.quantity() < quantity) {
            return CompletableFuture.completedFuture(new Result(false, "Cửa hàng đóng hoặc không đủ hàng.", 0L, "", ""));
        }
        UUID npcId = business.owner().kind() == ActorId.Kind.NPC ? business.owner().uuid() : null;
        if (npcId != null && !allowed(requester, business, npcId)) {
            return CompletableFuture.completedFuture(new Result(false, "NPC từ chối giao dịch với bạn.", 0L, "", ""));
        }

        final long total;
        try { total = Math.multiplyExact(stock.price(), quantity); }
        catch (ArithmeticException overflow) { return CompletableFuture.completedFuture(new Result(false, "Giá giao dịch vượt giới hạn.", 0L, "", "")); }
        if (total <= 0L || total > 1_000_000_000_000L) return CompletableFuture.completedFuture(new Result(false, "Giá giao dịch không hợp lệ.", 0L, "", ""));

        String currency = currencyFor(business);
        EconomyRouter.Resolution resolution = router.resolve(currency).orElse(null);
        if (resolution == null) return CompletableFuture.completedFuture(new Result(false, "Không có economy provider cho " + currency, total, currency, ""));
        UUID playerId = requester.getUuid();
        BigDecimal amount = BigDecimal.valueOf(total);
        CompletableFuture<Result> result = new CompletableFuture<>();

        resolution.provider().withdraw(playerId, amount, resolution.currency()).whenComplete((withdrawn, error) -> {
            if (error != null || !Boolean.TRUE.equals(withdrawn)) {
                result.complete(new Result(false, "Không đủ tiền hoặc economy provider từ chối giao dịch.", total, currency, resolution.providerId()));
                return;
            }
            server.execute(() -> commitPurchase(playerId, businessId, itemId, quantity, stock.price(), total, currency, resolution, result));
        });
        return result;
    }

    private void commitPurchase(UUID playerId, UUID businessId, String itemId, int quantity, long quotedUnitPrice,
                                long total, String currency, EconomyRouter.Resolution resolution, CompletableFuture<Result> result) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) {
            refund(playerId, total, resolution, result, "Bạn đã rời server trước khi giao dịch hoàn tất.");
            return;
        }
        EconomyEngine.Snapshot current = LivelyApi.economy().snapshot();
        EconomyEngine.Business business = current.businesses().get(businessId);
        EconomyEngine.StockKey key = new EconomyEngine.StockKey(businessId, itemId);
        EconomyEngine.Stock stock = current.stocks().get(key);
        if (business == null || !business.open() || stock == null || stock.quantity() < quantity || stock.price() != quotedUnitPrice) {
            refund(playerId, total, resolution, result, "Tồn kho/giá vừa thay đổi, tiền đã được hoàn lại.");
            return;
        }

        String reference = "external-buy:" + playerId + ":" + businessId + ":" + itemId + ":" + System.nanoTime();
        if (LivelyApi.economy().transfer(EconomyEngine.TransactionType.BUY, SETTLEMENT, business.owner(), total, reference).isEmpty()) {
            refund(playerId, total, resolution, result, "Settlement nội bộ từ chối giao dịch, tiền đã được hoàn lại.");
            return;
        }

        LivelyApi.economy().setStock(businessId, itemId, stock.quantity() - quantity, stock.targetQuantity(), stock.basePrice(),
                Math.min(1D, stock.demand() + .03D), Math.max(0D, stock.supply() - .02D));
        Item item = Registries.ITEM.get(Identifier.of(itemId));
        grant(player, item, quantity);
        if (business.owner().kind() == ActorId.Kind.NPC && LivelyApi.states() != null) {
            LivelyApi.states().get(business.owner().uuid()).ifPresent(state -> state.remember("shop_sale",
                    Map.of("player", playerId.toString(), "item", itemId, "quantity", Integer.toString(quantity),
                            "total", Long.toString(total), "currency", currency, "provider", resolution.providerId()), .42D, 1D));
        }
        result.complete(new Result(true, "Đã mua " + quantity + "x " + itemId + ".", total, currency, resolution.providerId()));
    }

    private void refund(UUID playerId, long total, EconomyRouter.Resolution resolution, CompletableFuture<Result> result, String message) {
        resolution.provider().deposit(playerId, BigDecimal.valueOf(total), resolution.currency()).whenComplete((refunded, error) -> {
            if (error != null || !Boolean.TRUE.equals(refunded)) {
                LivelyNpcs.LOGGER.error("External economy refund failed for player={} amount={} provider={}", playerId, total, resolution.providerId(), error);
                result.complete(new Result(false, message + " CẢNH BÁO: provider không xác nhận refund; kiểm tra log.", total, resolution.currency(), resolution.providerId()));
            } else {
                result.complete(new Result(false, message, total, resolution.currency(), resolution.providerId()));
            }
        });
    }

    private boolean allowed(ServerPlayerEntity player, EconomyEngine.Business business, UUID npcId) {
        if (LivelyApi.states() == null) return true;
        NpcState state = LivelyApi.states().get(npcId).orElse(null);
        double trust = state == null ? 0D : state.snapshot(1).relationship(player.getUuid()).trust();
        ActorId viewer = new ActorId(player.getUuid(), ActorId.Kind.PLAYER);
        LivelyApi.actors().upsert(viewer, player.getName().getString(), Map.of(),
                Map.of("world", player.getServerWorld().getRegistryKey().getValue().toString()), java.util.Set.of("player"));
        double reputation = LivelyApi.social().reputation(viewer, SocialEngine.ReputationScope.GLOBAL, "");
        BusinessAccessPolicy.Decision decision = BusinessAccessPolicy.evaluate(business, trust, reputation);
        if (!decision.visible()) return false;
        if (!decision.allowed()) {
            player.sendMessage(Text.literal(business.open() ? "NPC không muốn giao dịch với bạn." : "Cửa hàng đang đóng.").formatted(Formatting.RED), false);
            return false;
        }
        return true;
    }

    private String currencyFor(EconomyEngine.Business business) {
        String currency = business.facts().get("currency");
        if ((currency == null || currency.isBlank()) && business.owner().kind() == ActorId.Kind.NPC && LivelyApi.npcs() != null) {
            currency = LivelyApi.npcs().get(business.owner().uuid()).map(definition -> definition.metadata().get("business.currency")).orElse(null);
        }
        return currency == null || currency.isBlank() ? "cobbledollar" : currency.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static void grant(ServerPlayerEntity player, Item item, int quantity) {
        int remaining = quantity;
        int max = Math.max(1, new ItemStack(item).getMaxCount());
        while (remaining > 0) {
            int take = Math.min(max, remaining);
            ItemStack stack = new ItemStack(item, take);
            player.getInventory().insertStack(stack);
            if (!stack.isEmpty()) player.dropItem(stack, false);
            remaining -= take;
        }
    }

    private static void appendBuy(MutableText line, UUID business, String item, int amount, String label) {
        String command = "/livelyshop buy " + business + " " + item + " " + amount;
        line.append(Text.literal(label).setStyle(Style.EMPTY.withColor(Formatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))));
    }
}
