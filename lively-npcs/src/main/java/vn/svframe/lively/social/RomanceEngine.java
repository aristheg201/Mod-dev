package vn.svframe.lively.social;

import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.actor.ActorRegistry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Romance is a social-state machine, not a heart counter. */
public final class RomanceEngine {
    public enum Stage { INTEREST, DATING, PARTNERED, ENGAGED, MARRIED, SEPARATED, ENDED }
    public record Bond(UUID id, ActorId a, ActorId b, Stage stage, double stability, double jealousy,
                       Instant since, Instant updatedAt, List<String> sharedMemories, Map<String, String> facts) {
        public Bond {
            Objects.requireNonNull(id); Objects.requireNonNull(a); Objects.requireNonNull(b); Objects.requireNonNull(stage);
            Objects.requireNonNull(since); Objects.requireNonNull(updatedAt);
            stability = unit(stability); jealousy = unit(jealousy);
            sharedMemories = List.copyOf(sharedMemories.size() > 64 ? sharedMemories.subList(sharedMemories.size() - 64, sharedMemories.size()) : sharedMemories);
            facts = Map.copyOf(facts);
        }
    }
    public record Proposal(boolean allowed, String reason, double compatibility) {}

    private final ConcurrentHashMap<SocialEngine.Pair, Bond> bonds = new ConcurrentHashMap<>();
    private final SocialEngine social;
    private final ActorRegistry actors;

    public RomanceEngine(SocialEngine social, ActorRegistry actors) {
        this.social = Objects.requireNonNull(social); this.actors = Objects.requireNonNull(actors);
    }

    public Proposal canBegin(ActorId a, ActorId b) {
        if (a.equals(b)) return new Proposal(false, "same_actor", 0D);
        SocialEngine.Relationship relation = social.relationship(a, b);
        double compatibility = social.compatibility(actors, a, b);
        if (relation.familiarity() < 0.25D) return new Proposal(false, "not_familiar_enough", compatibility);
        if (relation.trust() < 0.10D || relation.affection() < 0.18D) return new Proposal(false, "relationship_not_ready", compatibility);
        if (compatibility < 0.45D) return new Proposal(false, "low_compatibility", compatibility);
        return new Proposal(true, "accepted", compatibility);
    }

    public Optional<Bond> begin(ActorId a, ActorId b) {
        Proposal proposal = canBegin(a, b); if (!proposal.allowed()) return Optional.empty();
        SocialEngine.Pair pair = SocialEngine.Pair.normalized(a, b);
        Instant now = Instant.now();
        Bond bond = new Bond(UUID.randomUUID(), pair.a(), pair.b(), Stage.INTEREST,
                proposal.compatibility(), 0D, now, now, List.of(), Map.of());
        Bond previous = bonds.putIfAbsent(pair, bond);
        return Optional.ofNullable(previous == null ? bond : previous);
    }

    public Optional<Bond> transition(ActorId a, ActorId b, Stage next) {
        SocialEngine.Pair pair = SocialEngine.Pair.normalized(a, b);
        return Optional.ofNullable(bonds.computeIfPresent(pair, (key, old) -> {
            if (!legal(old.stage(), next)) return old;
            SocialEngine.Relationship relation = social.relationship(a, b);
            double compatibility = social.compatibility(actors, a, b);
            double stability = unit(old.stability() * 0.65D + compatibility * 0.20D + relation.trust() * 0.15D);
            return new Bond(old.id(), old.a(), old.b(), next, stability, old.jealousy(), old.since(), Instant.now(), old.sharedMemories(), old.facts());
        }));
    }

    public Optional<Bond> recordSharedMemory(ActorId a, ActorId b, String memory, double positiveWeight) {
        SocialEngine.Pair pair = SocialEngine.Pair.normalized(a, b);
        return Optional.ofNullable(bonds.computeIfPresent(pair, (key, old) -> {
            java.util.ArrayList<String> memories = new java.util.ArrayList<>(old.sharedMemories()); memories.add(memory);
            return new Bond(old.id(), old.a(), old.b(), old.stage(), unit(old.stability() + positiveWeight * 0.08D),
                    unit(old.jealousy() - positiveWeight * 0.03D), old.since(), Instant.now(), memories, old.facts());
        }));
    }

    public Optional<Bond> applyJealousy(ActorId a, ActorId b, double amount, String reason) {
        SocialEngine.Pair pair = SocialEngine.Pair.normalized(a, b);
        return Optional.ofNullable(bonds.computeIfPresent(pair, (key, old) -> {
            Map<String, String> facts = new java.util.HashMap<>(old.facts()); facts.put("last_jealousy_reason", reason);
            double jealousy = unit(old.jealousy() + amount);
            double stability = unit(old.stability() - Math.max(0D, amount) * 0.12D);
            Stage stage = stability < 0.14D && old.stage() != Stage.MARRIED ? Stage.ENDED : old.stage();
            return new Bond(old.id(), old.a(), old.b(), stage, stability, jealousy, old.since(), Instant.now(), old.sharedMemories(), facts);
        }));
    }

    public Optional<Bond> bond(ActorId a, ActorId b) { return Optional.ofNullable(bonds.get(SocialEngine.Pair.normalized(a, b))); }
    public Map<SocialEngine.Pair, Bond> snapshot() { return Map.copyOf(bonds); }
    public void restore(Map<SocialEngine.Pair, Bond> snapshot) { bonds.clear(); bonds.putAll(snapshot); }

    private static boolean legal(Stage current, Stage next) {
        if (next == Stage.ENDED) return current != Stage.ENDED;
        return switch (current) {
            case INTEREST -> next == Stage.DATING;
            case DATING -> next == Stage.PARTNERED || next == Stage.SEPARATED;
            case PARTNERED -> next == Stage.ENGAGED || next == Stage.SEPARATED;
            case ENGAGED -> next == Stage.MARRIED || next == Stage.SEPARATED;
            case MARRIED -> next == Stage.SEPARATED;
            case SEPARATED -> next == Stage.PARTNERED || next == Stage.ENDED;
            case ENDED -> false;
        };
    }
    private static double unit(double value) { return Math.max(0D, Math.min(1D, value)); }
}
