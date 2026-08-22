package vn.svframe.lively.economy;

import vn.svframe.lively.actor.ActorId;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Persistent semantic debt/loan state used by banks, friends and loan sharks. */
public final class DebtEngine {
    public enum Status { ACTIVE, DELINQUENT, COLLECTION, REPAID, DEFAULTED, FORGIVEN }

    public record Contract(UUID id, ActorId creditor, ActorId debtor, long principal, long outstanding,
                           int interestBpsPerPeriod, Instant issuedAt, Instant dueAt, Instant lastAccruedAt,
                           boolean legal, Status status, Map<String, String> facts, long revision) {
        public Contract {
            Objects.requireNonNull(id); Objects.requireNonNull(creditor); Objects.requireNonNull(debtor);
            Objects.requireNonNull(issuedAt); Objects.requireNonNull(dueAt); Objects.requireNonNull(lastAccruedAt);
            Objects.requireNonNull(status);
            principal = Math.max(1L, principal); outstanding = Math.max(0L, outstanding);
            interestBpsPerPeriod = Math.max(0, Math.min(5_000, interestBpsPerPeriod));
            facts = Map.copyOf(facts == null ? Map.of() : facts);
        }
    }

    private static final long MAX_PRINCIPAL = 1_000_000_000_000L;
    private static final int MAX_CONTRACTS = 100_000;
    private final ConcurrentHashMap<UUID, Contract> contracts = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();

    public Contract issue(ActorId creditor, ActorId debtor, long principal, int interestBpsPerPeriod,
                          Instant dueAt, boolean legal, Map<String, String> facts) {
        Objects.requireNonNull(creditor); Objects.requireNonNull(debtor); Objects.requireNonNull(dueAt);
        if (creditor.equals(debtor) || principal <= 0L || principal > MAX_PRINCIPAL) throw new IllegalArgumentException("invalid debt contract");
        if (contracts.size() >= MAX_CONTRACTS) throw new IllegalStateException("debt_contract_limit");
        Instant now = Instant.now();
        Contract contract = new Contract(UUID.randomUUID(), creditor, debtor, principal, principal,
                interestBpsPerPeriod, now, dueAt.isBefore(now) ? now : dueAt, now, legal, Status.ACTIVE,
                sanitize(facts), revision.incrementAndGet());
        contracts.put(contract.id(), contract);
        return contract;
    }

    public void accrue(Instant now, Duration period) {
        Objects.requireNonNull(now); Objects.requireNonNull(period);
        long periodMillis = Math.max(1L, period.toMillis());
        for (UUID id : contracts.keySet()) {
            contracts.computeIfPresent(id, (ignored, old) -> {
                if (terminal(old.status()) || old.outstanding() == 0L) return old;
                long elapsed = Math.max(0L, Duration.between(old.lastAccruedAt(), now).toMillis());
                long periods = Math.min(365L, elapsed / periodMillis);
                long outstanding = old.outstanding();
                if (periods > 0L && old.interestBpsPerPeriod() > 0) {
                    for (long i = 0; i < periods; i++) {
                        long interest = Math.max(1L, Math.round(outstanding * (old.interestBpsPerPeriod() / 10_000D)));
                        try { outstanding = Math.min(MAX_PRINCIPAL, Math.addExact(outstanding, interest)); }
                        catch (ArithmeticException overflow) { outstanding = MAX_PRINCIPAL; break; }
                    }
                }
                Status status = old.status();
                if (status == Status.ACTIVE && now.isAfter(old.dueAt()) && outstanding > 0L) status = Status.DELINQUENT;
                if (periods == 0L && status == old.status()) return old;
                Instant accruedAt = periods == 0L ? old.lastAccruedAt() : old.lastAccruedAt().plusMillis(periodMillis * periods);
                return new Contract(old.id(), old.creditor(), old.debtor(), old.principal(), outstanding,
                        old.interestBpsPerPeriod(), old.issuedAt(), old.dueAt(), accruedAt, old.legal(), status,
                        old.facts(), revision.incrementAndGet());
            });
        }
    }

    public Optional<Contract> pay(UUID id, long amount) {
        if (amount <= 0L) return Optional.empty();
        AtomicReference<Contract> changed = new AtomicReference<>();
        contracts.computeIfPresent(id, (ignored, old) -> {
            if (terminal(old.status())) { changed.set(old); return old; }
            long nextOutstanding = Math.max(0L, old.outstanding() - Math.min(old.outstanding(), amount));
            Status next = nextOutstanding == 0L ? Status.REPAID : old.status();
            Contract value = new Contract(old.id(), old.creditor(), old.debtor(), old.principal(), nextOutstanding,
                    old.interestBpsPerPeriod(), old.issuedAt(), old.dueAt(), old.lastAccruedAt(), old.legal(), next,
                    old.facts(), revision.incrementAndGet());
            changed.set(value); return value;
        });
        return Optional.ofNullable(changed.get());
    }

    public Optional<Contract> startCollection(UUID id, String reason) {
        return updateStatus(id, Status.COLLECTION, reason);
    }

    public Optional<Contract> defaultContract(UUID id, String reason) {
        return updateStatus(id, Status.DEFAULTED, reason);
    }

    public Optional<Contract> forgive(UUID id, String reason) {
        return updateStatus(id, Status.FORGIVEN, reason);
    }

    public Optional<Contract> get(UUID id) { return Optional.ofNullable(contracts.get(id)); }

    public List<Contract> forDebtor(ActorId debtor) {
        return contracts.values().stream().filter(contract -> contract.debtor().equals(debtor))
                .sorted(Comparator.comparing(Contract::issuedAt)).toList();
    }

    public List<Contract> forCreditor(ActorId creditor) {
        return contracts.values().stream().filter(contract -> contract.creditor().equals(creditor))
                .sorted(Comparator.comparing(Contract::issuedAt)).toList();
    }

    public Snapshot snapshot() { return new Snapshot(revision.get(), Map.copyOf(contracts)); }
    public void restore(Snapshot snapshot) {
        contracts.clear();
        if (snapshot != null) contracts.putAll(snapshot.contracts());
        revision.set(snapshot == null ? 0L : Math.max(0L, snapshot.revision()));
    }

    private Optional<Contract> updateStatus(UUID id, Status status, String reason) {
        AtomicReference<Contract> changed = new AtomicReference<>();
        contracts.computeIfPresent(id, (ignored, old) -> {
            if (terminal(old.status())) { changed.set(old); return old; }
            HashMap<String, String> facts = new HashMap<>(old.facts());
            if (reason != null && !reason.isBlank()) facts.put("last_status_reason", reason.substring(0, Math.min(256, reason.length())));
            Contract value = new Contract(old.id(), old.creditor(), old.debtor(), old.principal(), old.outstanding(),
                    old.interestBpsPerPeriod(), old.issuedAt(), old.dueAt(), old.lastAccruedAt(), old.legal(), status,
                    facts, revision.incrementAndGet());
            changed.set(value); return value;
        });
        return Optional.ofNullable(changed.get());
    }

    private static boolean terminal(Status status) {
        return status == Status.REPAID || status == Status.DEFAULTED || status == Status.FORGIVEN;
    }

    private static Map<String, String> sanitize(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) return Map.of();
        HashMap<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            if (result.size() >= 32) break;
            String key = entry.getKey(), value = entry.getValue();
            if (key == null || key.isBlank() || key.length() > 64 || value == null || value.length() > 256) continue;
            result.put(key, value);
        }
        return Map.copyOf(result);
    }

    public record Snapshot(long revision, Map<UUID, Contract> contracts) {
        public Snapshot { contracts = Map.copyOf(contracts == null ? Map.of() : contracts); }
    }
}
