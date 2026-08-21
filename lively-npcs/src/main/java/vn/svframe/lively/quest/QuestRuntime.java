package vn.svframe.lively.quest;

import vn.svframe.lively.actor.ActorId;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Persistent quest state machine with bounded objectives, runtime signals and isolated lifecycle listeners. */
public final class QuestRuntime {
    public enum Status { OFFERED, ACTIVE, COMPLETED, FAILED, EXPIRED, CANCELLED }
    public enum ObjectiveType { DELIVERY, COLLECTION, EXPLORATION, COMBAT, SOCIAL, ESCORT, INVESTIGATION, CUSTOM }
    private static final Set<String> SIGNAL_KEYS = Set.of("event", "structure", "species", "actor", "npc", "crime", "battle", "location");

    public record Objective(String id, ObjectiveType type, String target, long required, boolean optional, boolean hidden,
                            Map<String, String> facts) {
        public Objective {
            Objects.requireNonNull(id); Objects.requireNonNull(type);
            target = target == null ? "" : target;
            required = Math.max(1L, required);
            facts = Map.copyOf(facts == null ? Map.of() : facts);
        }
    }

    public record Quest(UUID id, ActorId issuer, ActorId owner, String title, List<Objective> objectives,
                        Map<String, Long> progress, Status status, Instant createdAt, Instant expiresAt,
                        Map<String, String> facts, long revision) {
        public Quest {
            Objects.requireNonNull(id); Objects.requireNonNull(title); Objects.requireNonNull(status); Objects.requireNonNull(createdAt);
            objectives = List.copyOf(objectives);
            progress = Map.copyOf(progress);
            facts = Map.copyOf(facts);
        }
        public boolean expired(Instant now) { return expiresAt != null && !expiresAt.isAfter(now); }
        public boolean publicOffer() { return owner == null && status == Status.OFFERED; }
    }

    public interface Listener {
        default void onCreated(Quest quest) {}
        default void onClaimed(Quest quest) {}
        default void onProgressed(Quest before, Quest after) {}
        default void onStatusChanged(Quest before, Quest after) {}
        default void onCompleted(Quest quest) {}
    }

    private final ConcurrentHashMap<UUID, Quest> quests = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong revision = new AtomicLong();

    public void addListener(Listener listener) { if (listener != null) listeners.addIfAbsent(listener); }
    public void removeListener(Listener listener) { listeners.remove(listener); }

    public Quest create(ActorId issuer, ActorId owner, String title, List<Objective> objectives, Duration ttl,
                        Map<String, String> facts) {
        if (objectives == null || objectives.isEmpty() || objectives.size() > 32) throw new IllegalArgumentException("invalid objectives");
        Instant now = Instant.now();
        Quest quest = new Quest(UUID.randomUUID(), issuer, owner, title, objectives, Map.of(), Status.OFFERED, now,
                ttl == null ? null : now.plus(ttl), facts == null ? Map.of() : facts, revision.incrementAndGet());
        quests.put(quest.id(), quest);
        notifyCreated(quest);
        return quest;
    }

    public Optional<Quest> claim(UUID id, ActorId owner) {
        if (owner == null) return Optional.empty();
        AtomicReference<Quest> before = new AtomicReference<>();
        AtomicReference<Quest> changed = new AtomicReference<>();
        Quest result = quests.computeIfPresent(id, (key, old) -> {
            if (!old.publicOffer() || old.expired(Instant.now())) return old;
            before.set(old);
            Quest next = new Quest(old.id(), old.issuer(), owner, old.title(), old.objectives(), old.progress(), Status.ACTIVE,
                    old.createdAt(), old.expiresAt(), old.facts(), revision.incrementAndGet());
            changed.set(next);
            return next;
        });
        Quest next = changed.get();
        if (next != null) {
            notifyStatus(before.get(), next);
            notifyClaimed(next);
            return Optional.of(next);
        }
        return Optional.ofNullable(result).filter(q -> owner.equals(q.owner()) && q.status() == Status.ACTIVE);
    }

    public Optional<Quest> activate(UUID id) { return mutateStatus(id, Status.OFFERED, Status.ACTIVE); }
    public Optional<Quest> cancel(UUID id) { return quests.containsKey(id) ? setStatus(id, Status.CANCELLED) : Optional.empty(); }
    public Optional<Quest> fail(UUID id) { return setStatus(id, Status.FAILED); }

    public Optional<Quest> progress(UUID id, String objective, long delta) {
        if (delta <= 0L) return Optional.empty();
        AtomicReference<Quest> before = new AtomicReference<>();
        AtomicReference<Quest> changed = new AtomicReference<>();
        quests.computeIfPresent(id, (key, old) -> {
            if (old.status() != Status.ACTIVE) return old;
            Objective target = old.objectives().stream().filter(value -> value.id().equals(objective)).findFirst().orElse(null);
            if (target == null) return old;
            Map<String, Long> progress = new HashMap<>(old.progress());
            progress.merge(objective, delta, QuestRuntime::saturatingAdd);
            boolean done = old.objectives().stream().filter(value -> !value.optional())
                    .allMatch(value -> progress.getOrDefault(value.id(), 0L) >= value.required());
            before.set(old);
            Quest next = new Quest(old.id(), old.issuer(), old.owner(), old.title(), old.objectives(), progress,
                    done ? Status.COMPLETED : old.status(), old.createdAt(), old.expiresAt(), old.facts(), revision.incrementAndGet());
            changed.set(next);
            return next;
        });
        Quest next = changed.get();
        if (next == null) return Optional.empty();
        Quest old = before.get();
        notifyProgressed(old, next);
        if (old.status() != next.status()) {
            notifyStatus(old, next);
            if (next.status() == Status.COMPLETED) notifyCompleted(next);
        }
        return Optional.of(next);
    }

    /**
     * Applies one semantic runtime signal to matching active objectives for an owner. Matching is bounded and only
     * uses explicit target/fact aliases, so unrelated events cannot accidentally complete a quest.
     */
    public int signal(ActorId owner, ObjectiveType type, String target, long amount, Map<String, String> facts) {
        if (owner == null || type == null || amount <= 0L) return 0;
        long boundedAmount = Math.min(1_000_000L, amount);
        Map<String, String> signalFacts = facts == null ? Map.of() : Map.copyOf(facts);
        int progressed = 0;
        for (Quest quest : byOwner(owner).stream().filter(value -> value.status() == Status.ACTIVE).limit(128).toList()) {
            for (Objective objective : quest.objectives()) {
                if (objective.type() != type || quest.progress().getOrDefault(objective.id(), 0L) >= objective.required()) continue;
                if (!matchesSignal(objective, target, signalFacts)) continue;
                if (progress(quest.id(), objective.id(), boundedAmount).isPresent()) progressed++;
                break;
            }
        }
        return progressed;
    }

    /** Atomic, persistent idempotency marker used by reward/integration services. */
    public boolean markFactIfAbsent(UUID id, String key, String value) {
        if (key == null || key.isBlank() || key.length() > 96 || value == null || value.length() > 512) return false;
        AtomicReference<Boolean> marked = new AtomicReference<>(false);
        quests.computeIfPresent(id, (ignored, old) -> {
            if (old.facts().containsKey(key)) return old;
            Map<String, String> facts = new HashMap<>(old.facts());
            facts.put(key, value);
            marked.set(true);
            return new Quest(old.id(), old.issuer(), old.owner(), old.title(), old.objectives(), old.progress(), old.status(),
                    old.createdAt(), old.expiresAt(), facts, revision.incrementAndGet());
        });
        return marked.get();
    }

    public int expire(Instant now) {
        int count = 0;
        for (Quest quest : List.copyOf(quests.values())) {
            if ((quest.status() == Status.ACTIVE || quest.status() == Status.OFFERED) && quest.expired(now)) {
                if (setStatus(quest.id(), Status.EXPIRED).isPresent()) count++;
            }
        }
        return count;
    }

    public List<Quest> byOwner(ActorId owner) {
        return quests.values().stream().filter(quest -> Objects.equals(owner, quest.owner())).toList();
    }
    public List<Quest> publicOffers() {
        return quests.values().stream().filter(Quest::publicOffer).filter(quest -> !quest.expired(Instant.now())).toList();
    }

    private static boolean matchesSignal(Objective objective, String target, Map<String, String> signalFacts) {
        String expected = objective.target();
        if (expected.isBlank() || expected.equals("*")) return true;
        if (same(expected, target)) return true;
        for (String key : SIGNAL_KEYS) {
            String objectiveFact = objective.facts().get(key);
            String signalFact = signalFacts.get(key);
            if (same(expected, signalFact) || same(objectiveFact, target) || same(objectiveFact, signalFact)) return true;
        }
        return false;
    }

    private static boolean same(String left, String right) {
        return left != null && right != null && !left.isBlank() && !right.isBlank() && left.equalsIgnoreCase(right);
    }

    private Optional<Quest> mutateStatus(UUID id, Status expected, Status next) {
        Quest old = quests.get(id);
        return old == null || old.status() != expected ? Optional.empty() : setStatus(id, next);
    }

    private Optional<Quest> setStatus(UUID id, Status nextStatus) {
        AtomicReference<Quest> before = new AtomicReference<>();
        AtomicReference<Quest> changed = new AtomicReference<>();
        quests.computeIfPresent(id, (key, old) -> {
            if (old.status() == nextStatus) return old;
            before.set(old);
            Quest next = new Quest(old.id(), old.issuer(), old.owner(), old.title(), old.objectives(), old.progress(), nextStatus,
                    old.createdAt(), old.expiresAt(), old.facts(), revision.incrementAndGet());
            changed.set(next);
            return next;
        });
        Quest next = changed.get();
        if (next == null) return Optional.empty();
        notifyStatus(before.get(), next);
        if (next.status() == Status.COMPLETED) notifyCompleted(next);
        return Optional.of(next);
    }

    public Snapshot snapshot() { return new Snapshot(revision.get(), Map.copyOf(quests)); }
    public void restore(Snapshot snapshot) {
        quests.clear(); quests.putAll(snapshot.quests()); revision.set(Math.max(0L, snapshot.revision()));
    }

    public record Snapshot(long revision, Map<UUID, Quest> quests) {
        public Snapshot { quests = Map.copyOf(quests); }
    }

    private void notifyCreated(Quest quest) { for (Listener listener : listeners) safe(() -> listener.onCreated(quest)); }
    private void notifyClaimed(Quest quest) { for (Listener listener : listeners) safe(() -> listener.onClaimed(quest)); }
    private void notifyProgressed(Quest before, Quest after) { for (Listener listener : listeners) safe(() -> listener.onProgressed(before, after)); }
    private void notifyStatus(Quest before, Quest after) { for (Listener listener : listeners) safe(() -> listener.onStatusChanged(before, after)); }
    private void notifyCompleted(Quest quest) { for (Listener listener : listeners) safe(() -> listener.onCompleted(quest)); }
    private static void safe(Runnable callback) { try { callback.run(); } catch (RuntimeException ignored) {} }
    private static long saturatingAdd(long a, long b) {
        try { return Math.addExact(a, b); }
        catch (ArithmeticException overflow) { return Long.MAX_VALUE; }
    }
}
