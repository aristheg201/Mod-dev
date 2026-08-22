package vn.svframe.lively.social;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bounded gossip pulse over relationships that actually exist. No all-pairs scan and no synthetic friendships. */
public final class RumorPropagationBootstrap implements ModInitializer {
    private static final int MAX_RUMORS_PER_PULSE = 32;
    private static final int MAX_ATTEMPTS_PER_PULSE = 128;
    private static final int MAX_SUCCESS_PER_PULSE = 32;

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 200L == 0L) pulse();
        });
    }

    int pulse() {
        SocialEngine.Snapshot snapshot = LivelyApi.social().snapshot();
        if (snapshot.rumors().isEmpty() || snapshot.relationships().isEmpty()) return 0;

        Map<ActorId, List<Neighbor>> graph = new HashMap<>();
        for (SocialEngine.Relationship relation : snapshot.relationships().values()) {
            SocialEngine.Pair pair = relation.pair();
            double affinity = propagationAffinity(relation);
            if (affinity < .14D) continue;
            graph.computeIfAbsent(pair.a(), ignored -> new ArrayList<>()).add(new Neighbor(pair.b(), affinity));
            graph.computeIfAbsent(pair.b(), ignored -> new ArrayList<>()).add(new Neighbor(pair.a(), affinity));
        }
        graph.values().forEach(values -> values.sort(Comparator.comparingDouble(Neighbor::affinity).reversed()
                .thenComparing(value -> value.actor().uuid().toString())));

        List<SocialEngine.Rumor> rumors = snapshot.rumors().values().stream()
                .filter(rumor -> !rumor.expired(Instant.now()) && rumor.hops() < 12)
                .sorted(Comparator.comparingDouble((SocialEngine.Rumor rumor) -> rumor.confidence() * rumor.importance()).reversed()
                        .thenComparing(rumor -> rumor.id().toString()))
                .limit(MAX_RUMORS_PER_PULSE).toList();

        int attempts = 0;
        int successful = 0;
        outer:
        for (SocialEngine.Rumor rumor : rumors) {
            for (ActorId carrier : rumor.carriers().stream().sorted(Comparator.comparing(actor -> actor.uuid().toString())).toList()) {
                for (Neighbor neighbor : graph.getOrDefault(carrier, List.of())) {
                    if (attempts++ >= MAX_ATTEMPTS_PER_PULSE || successful >= MAX_SUCCESS_PER_PULSE) break outer;
                    ActorId receiver = neighbor.actor();
                    if (receiver.kind() != ActorId.Kind.NPC || rumor.carriers().contains(receiver)) continue;
                    SocialEngine.Rumor propagated = LivelyApi.social().propagate(rumor.id(), carrier, receiver).orElse(null);
                    if (propagated == null) continue;
                    successful++;
                    if (LivelyApi.states() != null) {
                        LivelyApi.states().get(receiver.uuid()).ifPresent(state -> state.remember("rumor_received",
                                Map.of("rumor", propagated.id().toString(), "topic", propagated.topic(),
                                        "subject", propagated.subject().uuid().toString(),
                                        "confidence", Double.toString(propagated.confidence()),
                                        "from", carrier.uuid().toString()),
                                Math.max(.12D, propagated.importance() * .55D), propagated.confidence()));
                    }
                }
            }
        }
        return successful;
    }

    private static double propagationAffinity(SocialEngine.Relationship relation) {
        if (relation.type() == SocialEngine.RelationshipType.ENEMY) return 0D;
        return Math.max(0D, Math.min(1D, .15D + relation.familiarity() * .45D
                + Math.max(0D, relation.trust()) * .25D + Math.max(0D, relation.affection()) * .15D));
    }

    private record Neighbor(ActorId actor, double affinity) {}
}
