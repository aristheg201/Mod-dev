package vn.svframe.lively.quest;

import vn.svframe.lively.actor.ActorId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class QuestRuntime {
    public enum Status { OFFERED, ACTIVE, COMPLETED, FAILED, EXPIRED, CANCELLED }
    public enum ObjectiveType { DELIVERY, COLLECTION, EXPLORATION, COMBAT, SOCIAL, ESCORT, INVESTIGATION, CUSTOM }
    public record Objective(String id, ObjectiveType type, String target, long required, boolean optional, boolean hidden, Map<String,String> facts) {
        public Objective { Objects.requireNonNull(id); Objects.requireNonNull(type); required = Math.max(1L, required); facts = Map.copyOf(facts); }
    }
    public record Quest(UUID id, ActorId issuer, ActorId owner, String title, List<Objective> objectives,
                        Map<String,Long> progress, Status status, Instant createdAt, Instant expiresAt,
                        Map<String,String> facts, long revision) {
        public Quest { objectives = List.copyOf(objectives); progress = Map.copyOf(progress); facts = Map.copyOf(facts); }
        public boolean expired(Instant now) { return expiresAt != null && !expiresAt.isAfter(now); }
    }
    private final ConcurrentHashMap<UUID,Quest> quests = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();

    public Quest create(ActorId issuer, ActorId owner, String title, List<Objective> objectives, Duration ttl, Map<String,String> facts) {
        if (objectives.isEmpty() || objectives.size() > 32) throw new IllegalArgumentException("invalid objectives");
        Instant now = Instant.now();
        Quest q = new Quest(UUID.randomUUID(), issuer, owner, title, objectives, Map.of(), Status.OFFERED, now,
                ttl == null ? null : now.plus(ttl), facts, revision.incrementAndGet()); quests.put(q.id(), q); return q;
    }
    public Optional<Quest> activate(UUID id) { return mutateStatus(id, Status.OFFERED, Status.ACTIVE); }
    public Optional<Quest> cancel(UUID id) { Quest old = quests.get(id); if (old == null) return Optional.empty(); return setStatus(id, Status.CANCELLED); }
    public Optional<Quest> fail(UUID id) { return setStatus(id, Status.FAILED); }
    public Optional<Quest> progress(UUID id, String objectiveId, long delta) {
        Quest old = quests.get(id); if (old == null || old.status()!=Status.ACTIVE || delta<=0) return Optional.empty();
        Objective obj = old.objectives().stream().filter(o->o.id().equals(objectiveId)).findFirst().orElse(null); if (obj==null) return Optional.empty();
        Map<String,Long> p = new java.util.HashMap<>(old.progress()); p.merge(objectiveId, delta, Long::sum);
        boolean done = old.objectives().stream().filter(o->!o.optional()).allMatch(o->p.getOrDefault(o.id(),0L)>=o.required());
        Quest next = new Quest(old.id(),old.issuer(),old.owner(),old.title(),old.objectives(),p,done?Status.COMPLETED:old.status(),old.createdAt(),old.expiresAt(),old.facts(),revision.incrementAndGet()); quests.put(id,next); return Optional.of(next);
    }
    public int expire(Instant now) { int n=0; for (Quest q: List.copyOf(quests.values())) if ((q.status()==Status.ACTIVE||q.status()==Status.OFFERED)&&q.expired(now)) { setStatus(q.id(),Status.EXPIRED); n++; } return n; }
    public List<Quest> byOwner(ActorId owner) { return quests.values().stream().filter(q->owner.equals(q.owner())).toList(); }
    private Optional<Quest> mutateStatus(UUID id, Status expected, Status next) { Quest old=quests.get(id); if(old==null||old.status()!=expected)return Optional.empty(); return setStatus(id,next); }
    private Optional<Quest> setStatus(UUID id, Status next) { return Optional.ofNullable(quests.computeIfPresent(id,(k,o)->new Quest(o.id(),o.issuer(),o.owner(),o.title(),o.objectives(),o.progress(),next,o.createdAt(),o.expiresAt(),o.facts(),revision.incrementAndGet()))); }
    public Snapshot snapshot(){return new Snapshot(revision.get(),Map.copyOf(quests));}
    public void restore(Snapshot s){quests.clear();quests.putAll(s.quests());revision.set(s.revision());}
    public record Snapshot(long revision, Map<UUID,Quest> quests){public Snapshot{quests=Map.copyOf(quests);}}
}
