package vn.svframe.lively.economy;

import vn.svframe.lively.actor.ActorId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Internal simulation economy. External currency is bridged by integrations, never hardwired into core. */
public final class EconomyEngine {
    public enum TransactionType { BUY, SELL, WAGE, RENT, TAX, GIFT, SERVICE, TRANSFER }
    public record Wallet(ActorId owner, long balance, long revision) {}
    public record StockKey(UUID businessId, String itemId) { public StockKey { Objects.requireNonNull(businessId); Objects.requireNonNull(itemId); } }
    public record Stock(StockKey key, long quantity, long targetQuantity, long basePrice, double demand, double supply, long revision) {
        public Stock { quantity = Math.max(0L, quantity); targetQuantity = Math.max(1L, targetQuantity); basePrice = Math.max(0L, basePrice); demand = unit(demand); supply = unit(supply); }
        public long price() {
            double scarcity = 1D + (1D - Math.min(1D, quantity / (double) targetQuantity)) * 0.65D;
            double market = 0.70D + demand * 0.55D + (1D - supply) * 0.35D;
            return Math.max(1L, Math.round(basePrice * scarcity * market));
        }
    }
    public record Business(UUID id, ActorId owner, String name, String locationId, boolean open,
                           List<ActorId> employees, Map<String, String> facts, long revision) {
        public Business { Objects.requireNonNull(id); Objects.requireNonNull(owner); Objects.requireNonNull(name); employees = List.copyOf(employees); facts = Map.copyOf(facts); }
    }
    public record Transaction(UUID id, TransactionType type, ActorId from, ActorId to, long amount,
                              String reference, Instant at, boolean committed) {}

    private final ConcurrentHashMap<ActorId, Wallet> wallets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Business> businesses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<StockKey, Stock> stocks = new ConcurrentHashMap<>();
    private final List<Transaction> ledger = java.util.Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong revision = new AtomicLong();

    public Wallet ensureWallet(ActorId owner, long initialBalance) {
        return wallets.computeIfAbsent(owner, key -> new Wallet(key, Math.max(0L, initialBalance), revision.incrementAndGet()));
    }

    public Optional<Transaction> transfer(TransactionType type, ActorId from, ActorId to, long amount, String reference) {
        if (amount <= 0L || amount > 10_000_000_000_000L || from.equals(to)) return Optional.empty();
        synchronized (wallets) {
            Wallet source = ensureWallet(from, 0L); Wallet target = ensureWallet(to, 0L);
            if (source.balance() < amount) return Optional.empty();
            long rev = revision.incrementAndGet();
            wallets.put(from, new Wallet(from, source.balance() - amount, rev));
            wallets.put(to, new Wallet(to, Math.addExact(target.balance(), amount), rev));
            Transaction tx = new Transaction(UUID.randomUUID(), type, from, to, amount, reference, Instant.now(), true);
            synchronized (ledger) { ledger.add(tx); if (ledger.size() > 100_000) ledger.subList(0, ledger.size() - 100_000).clear(); }
            return Optional.of(tx);
        }
    }

    public Business createBusiness(ActorId owner, String name, String locationId, Map<String, String> facts) {
        Business business = new Business(UUID.randomUUID(), owner, name, locationId, true, List.of(), facts, revision.incrementAndGet());
        businesses.put(business.id(), business); return business;
    }

    public Optional<Business> setOpen(UUID id, boolean open) {
        long rev = revision.incrementAndGet();
        return Optional.ofNullable(businesses.computeIfPresent(id, (key, old) -> new Business(old.id(), old.owner(), old.name(), old.locationId(), open, old.employees(), old.facts(), rev)));
    }

    public Stock setStock(UUID businessId, String itemId, long quantity, long target, long basePrice, double demand, double supply) {
        if (!businesses.containsKey(businessId)) throw new IllegalArgumentException("unknown business");
        Stock stock = new Stock(new StockKey(businessId, itemId), quantity, target, basePrice, demand, supply, revision.incrementAndGet());
        stocks.put(stock.key(), stock); return stock;
    }

    public Optional<Transaction> buy(UUID businessId, ActorId buyer, String itemId, long quantity) {
        if (quantity <= 0L || quantity > 1_000_000L) return Optional.empty();
        Business business = businesses.get(businessId); StockKey key = new StockKey(businessId, itemId); Stock stock = stocks.get(key);
        if (business == null || !business.open() || stock == null || stock.quantity() < quantity) return Optional.empty();
        long total = Math.multiplyExact(stock.price(), quantity);
        Optional<Transaction> tx = transfer(TransactionType.BUY, buyer, business.owner(), total, businessId + ":" + itemId);
        if (tx.isEmpty()) return Optional.empty();
        long rev = revision.incrementAndGet();
        stocks.put(key, new Stock(key, stock.quantity() - quantity, stock.targetQuantity(), stock.basePrice(), unit(stock.demand() + 0.03D), unit(stock.supply() - 0.02D), rev));
        return tx;
    }

    public void marketTick() {
        for (Map.Entry<StockKey, Stock> entry : stocks.entrySet()) {
            Stock s = entry.getValue();
            double demand = s.demand() + (0.5D - s.demand()) * 0.02D;
            double supply = s.supply() + (0.5D - s.supply()) * 0.02D;
            stocks.put(entry.getKey(), new Stock(s.key(), s.quantity(), s.targetQuantity(), s.basePrice(), demand, supply, revision.incrementAndGet()));
        }
    }

    public Snapshot snapshot() { synchronized (ledger) { return new Snapshot(revision.get(), Map.copyOf(wallets), Map.copyOf(businesses), Map.copyOf(stocks), List.copyOf(ledger)); } }
    public void restore(Snapshot snapshot) { wallets.clear(); wallets.putAll(snapshot.wallets()); businesses.clear(); businesses.putAll(snapshot.businesses()); stocks.clear(); stocks.putAll(snapshot.stocks()); synchronized (ledger) { ledger.clear(); ledger.addAll(snapshot.ledger()); } revision.set(snapshot.revision()); }
    public record Snapshot(long revision, Map<ActorId, Wallet> wallets, Map<UUID, Business> businesses, Map<StockKey, Stock> stocks, List<Transaction> ledger) {
        public Snapshot { wallets = Map.copyOf(wallets); businesses = Map.copyOf(businesses); stocks = Map.copyOf(stocks); ledger = List.copyOf(ledger); }
    }
    private static double unit(double v) { return Math.max(0D, Math.min(1D, v)); }
}
