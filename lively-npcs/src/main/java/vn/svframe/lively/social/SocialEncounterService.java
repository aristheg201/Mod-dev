package vn.svframe.lively.social;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Vec3d;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.crime.CrimeEngine;
import vn.svframe.lively.model.NpcSnapshot;
import vn.svframe.lively.model.NpcState;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.society.SocietyApi;
import vn.svframe.lively.simulation.SocietySimulationService;
import vn.svframe.lively.world.SemanticStructureRegistry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded NPC-to-NPC encounters. No lifestyle animation is required; consequences live in memory/social/crime state. */
public final class SocialEncounterService {
    public enum Kind { GREETING, CHAT, GOSSIP, ARGUMENT, RECONCILIATION, PARTNER_TIME }

    private static final long PULSE_TICKS = 400L;
    private static final long PAIR_COOLDOWN_TICKS = 1200L;
    private static final int MAX_SAMPLE = 96;
    private static final int MAX_ENCOUNTERS = 32;
    private static final double RANGE_SQ = 10D * 10D;

    private final MinecraftServer server;
    private final ConcurrentHashMap<String, Long> pairCooldown = new ConcurrentHashMap<>();
    private long lastPulse;
    private int cursor;

    public SocialEncounterService(MinecraftServer server) { this.server = server; }

    public void tick(long tick) {
        if (tick - lastPulse < PULSE_TICKS || LivelyApi.npcs() == null || LivelyApi.states() == null) return;
        lastPulse = tick;
        List<NpcDefinition> all = LivelyApi.npcs().snapshot().values().stream().filter(NpcDefinition::spawned)
                .sorted(Comparator.comparing(value -> value.id().toString())).toList();
        if (all.size() < 2) return;
        int count = Math.min(MAX_SAMPLE, all.size());
        ArrayList<Sample> samples = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            NpcDefinition definition = all.get((cursor + i) % all.size());
            if (SocietyApi.law().activeCustody(new ActorId(definition.id(), ActorId.Kind.NPC)).isPresent()) continue;
            Vec3d position = LivelyApi.npcs().position(definition.id()).orElse(null);
            String world = LivelyApi.npcs().worldKey(definition.id()).orElse(definition.world());
            NpcState state = LivelyApi.states().get(definition.id()).orElse(null);
            if (position != null && world != null && state != null && sociallyAvailable(definition.id())) {
                samples.add(new Sample(definition, state, world, position));
            }
        }
        cursor = (cursor + count) % all.size();
        int encounters = 0;
        for (int i = 0; i < samples.size() && encounters < MAX_ENCOUNTERS; i++) {
            Sample left = samples.get(i);
            Sample right = nearest(left, samples, i + 1, tick);
            if (right == null) continue;
            interact(left, right, samples, tick);
            pairCooldown.put(pairKey(left.definition.id(), right.definition.id()), tick);
            encounters++;
        }
        if (pairCooldown.size() > 4096) {
            long cutoff = tick - PAIR_COOLDOWN_TICKS * 4L;
            pairCooldown.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        }
    }

    private Sample nearest(Sample left, List<Sample> samples, int start, long tick) {
        Sample best = null; double bestDistance = RANGE_SQ;
        for (int i = start; i < samples.size(); i++) {
            Sample right = samples.get(i);
            if (!left.world.equals(right.world)) continue;
            String pair = pairKey(left.definition.id(), right.definition.id());
            if (tick - pairCooldown.getOrDefault(pair, Long.MIN_VALUE / 2L) < PAIR_COOLDOWN_TICKS) continue;
            double distance = left.position.squaredDistanceTo(right.position);
            if (distance <= bestDistance) { bestDistance = distance; best = right; }
        }
        return best;
    }

    private void interact(Sample left, Sample right, List<Sample> nearby, long tick) {
        ActorId a = actor(left), b = actor(right);
        SocialEngine.Relationship relation = LivelyApi.social().relationship(a, b);
        NpcSnapshot la = left.state.snapshot(12), rb = right.state.snapshot(12);
        RomanceEngine.Bond bond = LivelyApi.romance().bond(a, b).orElse(null);
        double hostility = relation.hostility();
        double aggression = Math.max(la.trait("aggression"), rb.trait("aggression"));
        double stress = Math.max(la.need("stress"), rb.need("stress"));
        SplittableRandom random = new SplittableRandom(left.definition.id().getMostSignificantBits()
                ^ right.definition.id().getLeastSignificantBits() ^ (tick / PULSE_TICKS));

        Kind kind;
        if (bond != null && bond.stage() != RomanceEngine.Stage.ENDED && bond.stage() != RomanceEngine.Stage.SEPARATED) {
            if (bond.jealousy() > .72D && random.nextDouble() < .38D) kind = Kind.ARGUMENT;
            else kind = Kind.PARTNER_TIME;
        } else if (hostility > .58D && (aggression > .55D || stress > .68D)) kind = Kind.ARGUMENT;
        else if ((relation.trust() < -.20D || relation.affection() < -.20D) && hostility < .50D && random.nextDouble() < .20D) kind = Kind.RECONCILIATION;
        else if (tryGossip(a, b) || tryGossip(b, a)) kind = Kind.GOSSIP;
        else if (relation.familiarity() < .20D) kind = Kind.GREETING;
        else kind = Kind.CHAT;

        switch (kind) {
            case GREETING -> apply(a, b, .006D, .008D, .004D, 0D, .025D, "ambient_greeting", left, right);
            case CHAT -> apply(a, b, .012D, .018D, .008D, 0D, .020D, "ambient_chat", left, right);
            case GOSSIP -> apply(a, b, .004D, .008D, 0D, 0D, .018D, "ambient_gossip", left, right);
            case RECONCILIATION -> apply(a, b, .025D, .030D, .010D, -.035D, .030D, "ambient_reconciliation", left, right);
            case PARTNER_TIME -> {
                apply(a, b, .018D, .028D, .008D, -.010D, .028D, "ambient_partner_time", left, right);
                LivelyApi.romance().recordSharedMemory(a, b, "ambient_time_together", .10D);
            }
            case ARGUMENT -> argument(a, b, left, right, nearby, aggression, hostility, random);
        }
        remember(left, kind, right.definition.id());
        remember(right, kind, left.definition.id());
    }

    private void argument(ActorId a, ActorId b, Sample left, Sample right, List<Sample> nearby,
                          double aggression, double hostility, SplittableRandom random) {
        LivelyApi.social().apply(a, b, new SocialEngine.SocialDelta(-.035D, -.055D, -.010D, .035D,
                -.025D, -.015D, .045D, "ambient_argument", Map.of()));
        if (LivelyApi.romance().bond(a, b).isPresent()) LivelyApi.romance().applyJealousy(a, b, .05D, "argument");
        if (aggression < .78D || hostility < .64D || random.nextDouble() >= aggression * .28D) return;

        ActorId aggressor = left.state.snapshot(1).trait("aggression") >= right.state.snapshot(1).trait("aggression") ? a : b;
        ActorId victim = aggressor.equals(a) ? b : a;
        Set<ActorId> witnesses = witnesses(left, right, nearby);
        String location = location(left.world, left.position);
        CrimeEngine.Crime crime = LivelyApi.crime().create(CrimeEngine.Type.ASSAULT, victim, aggressor, location,
                "social_conflict", witnesses, Map.of("kind", "ambient_assault", "semantic_only", "true"));
        LivelyApi.crime().addEvidence(crime.id(), CrimeEngine.EvidenceType.OPPORTUNITY, null, aggressor,
                .82D, .78D, false, Map.of("location", location == null ? "" : location));
        for (ActorId witness : witnesses) {
            LivelyApi.crime().addEvidence(crime.id(), CrimeEngine.EvidenceType.WITNESS, witness, aggressor,
                    .68D, .72D, false, Map.of("encounter", "ambient_argument"));
        }
        if (aggressor.kind() == ActorId.Kind.NPC) {
            LivelyApi.states().get(aggressor.uuid()).ifPresent(state -> state.remember("ambient_assault_committed",
                    Map.of("crime", crime.id().toString(), "victim", victim.uuid().toString()), .80D, 1D));
        }
    }

    private boolean tryGossip(ActorId carrier, ActorId receiver) {
        SocialEngine.Rumor rumor = LivelyApi.social().snapshot().rumors().values().stream()
                .filter(value -> value.carriers().contains(carrier) && !value.carriers().contains(receiver) && !value.expired(java.time.Instant.now()))
                .max(Comparator.comparingDouble(value -> value.confidence() * value.importance())).orElse(null);
        return rumor != null && LivelyApi.social().propagate(rumor.id(), carrier, receiver).isPresent();
    }

    private void apply(ActorId a, ActorId b, double trust, double affection, double respect, double fear,
                       double familiarity, String reason, Sample left, Sample right) {
        LivelyApi.social().apply(a, b, new SocialEngine.SocialDelta(trust, affection, respect, fear,
                Math.max(0D, trust * .5D), 0D, familiarity, reason,
                Map.of("left", left.definition.id().toString(), "right", right.definition.id().toString())));
    }

    private Set<ActorId> witnesses(Sample left, Sample right, List<Sample> nearby) {
        HashSet<ActorId> result = new HashSet<>();
        for (Sample sample : nearby) {
            if (sample.definition.id().equals(left.definition.id()) || sample.definition.id().equals(right.definition.id())) continue;
            if (!sample.world.equals(left.world) || sample.position.squaredDistanceTo(left.position) > 14D * 14D) continue;
            result.add(actor(sample));
            if (result.size() >= 6) break;
        }
        return Set.copyOf(result);
    }

    private String location(String world, Vec3d position) {
        return LivelyApi.structures().at(world, position.x, position.y, position.z).stream()
                .findFirst().map(SemanticStructureRegistry.Structure::id).orElse(null);
    }

    private boolean sociallyAvailable(UUID npcId) {
        SocietySimulationService simulation = SocietyApi.simulation();
        SocietySimulationService.Activity activity = simulation == null ? null : simulation.activities().get(npcId);
        return activity != SocietySimulationService.Activity.SLEEP
                && activity != SocietySimulationService.Activity.CRIME
                && activity != SocietySimulationService.Activity.DEBT_COLLECTION;
    }

    private void remember(Sample sample, Kind kind, UUID other) {
        sample.state.remember("ambient_social_encounter",
                Map.of("kind", kind.name(), "other", other.toString()), .22D, 1D);
    }

    private static ActorId actor(Sample sample) { return new ActorId(sample.definition.id(), ActorId.Kind.NPC); }
    private static String pairKey(UUID a, UUID b) { return a.compareTo(b) <= 0 ? a + ":" + b : b + ":" + a; }
    private record Sample(NpcDefinition definition, NpcState state, String world, Vec3d position) {}
}
