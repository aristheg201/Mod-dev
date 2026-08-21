package vn.svframe.lively.world;

import vn.svframe.lively.actor.ActorId;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Hard world-integrity boundary. AI can change simulation state, not arbitrarily mutate Minecraft terrain. */
public final class WorldMutationPolicy {
    public enum MutationClass { NONE, TRANSIENT, SEMANTIC, PERSISTENT }
    public enum Source { AI, ADMIN, SYSTEM }
    public enum ActionKind {
        DIALOGUE, PARTICLE, SOUND, DISPLAY, TEMPORARY_ENTITY, TEMPORARY_BARRIER,
        STRUCTURE_STATE, ECONOMIC_STATE, RELATIONSHIP_STATE, EVENT_STATE,
        BLOCK_SET, BLOCK_BREAK, EXPLOSION, FIRE, FLUID, CONTAINER_MUTATION,
        NBT_MUTATION, COMMAND, REGISTERED_TRANSFORM
    }

    private static final Set<ActionKind> TRANSIENT_SAFE = Set.of(
            ActionKind.PARTICLE, ActionKind.SOUND, ActionKind.DISPLAY,
            ActionKind.TEMPORARY_ENTITY, ActionKind.TEMPORARY_BARRIER
    );
    private static final Set<ActionKind> SEMANTIC_SAFE = Set.of(
            ActionKind.STRUCTURE_STATE, ActionKind.ECONOMIC_STATE,
            ActionKind.RELATIONSHIP_STATE, ActionKind.EVENT_STATE
    );
    private static final Set<ActionKind> ALWAYS_FORBIDDEN_FOR_AI = Set.of(
            ActionKind.BLOCK_SET, ActionKind.BLOCK_BREAK, ActionKind.EXPLOSION,
            ActionKind.FIRE, ActionKind.FLUID, ActionKind.CONTAINER_MUTATION,
            ActionKind.NBT_MUTATION, ActionKind.COMMAND, ActionKind.REGISTERED_TRANSFORM
    );

    private final Duration maxTransientTtl;
    private final Set<String> adminPersistentTransforms;

    public WorldMutationPolicy(Duration maxTransientTtl, Set<String> adminPersistentTransforms) {
        this.maxTransientTtl = Objects.requireNonNull(maxTransientTtl);
        if (maxTransientTtl.isNegative() || maxTransientTtl.isZero() || maxTransientTtl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("maxTransientTtl must be >0 and <=1h");
        }
        this.adminPersistentTransforms = Set.copyOf(adminPersistentTransforms);
    }

    public static WorldMutationPolicy secureDefaults() {
        return new WorldMutationPolicy(Duration.ofMinutes(10), Set.of());
    }

    public Decision evaluate(Proposal proposal) {
        Objects.requireNonNull(proposal);
        if (proposal.source() == Source.AI && ALWAYS_FORBIDDEN_FOR_AI.contains(proposal.action())) {
            return Decision.deny("ai_world_mutation_forbidden");
        }
        return switch (proposal.mutationClass()) {
            case NONE -> proposal.action() == ActionKind.DIALOGUE
                    ? Decision.allow("non_world_action")
                    : Decision.deny("none_class_action_mismatch");
            case TRANSIENT -> evaluateTransient(proposal);
            case SEMANTIC -> SEMANTIC_SAFE.contains(proposal.action())
                    ? Decision.allow("semantic_state_only")
                    : Decision.deny("semantic_action_not_allowed");
            case PERSISTENT -> evaluatePersistent(proposal);
        };
    }

    private Decision evaluateTransient(Proposal proposal) {
        if (!TRANSIENT_SAFE.contains(proposal.action())) return Decision.deny("transient_action_not_allowed");
        Duration ttl = proposal.ttl();
        if (ttl == null || ttl.isNegative() || ttl.isZero()) return Decision.deny("transient_ttl_required");
        if (ttl.compareTo(maxTransientTtl) > 0) return Decision.deny("transient_ttl_too_long");
        return Decision.allow("bounded_transient_effect");
    }

    private Decision evaluatePersistent(Proposal proposal) {
        if (proposal.source() != Source.ADMIN) return Decision.deny("persistent_requires_admin_source");
        if (proposal.action() != ActionKind.REGISTERED_TRANSFORM) return Decision.deny("persistent_requires_registered_transform");
        String transform = proposal.arguments().getOrDefault("transform", "");
        if (!adminPersistentTransforms.contains(transform)) return Decision.deny("persistent_transform_not_allowlisted");
        if (proposal.structureId() == null || proposal.structureId().isBlank()) return Decision.deny("persistent_transform_requires_structure");
        return Decision.allow("allowlisted_admin_transform");
    }

    public record Proposal(
            ActorId actor,
            Source source,
            MutationClass mutationClass,
            ActionKind action,
            String structureId,
            Duration ttl,
            Map<String, String> arguments
    ) {
        public Proposal {
            Objects.requireNonNull(source);
            Objects.requireNonNull(mutationClass);
            Objects.requireNonNull(action);
            arguments = Map.copyOf(arguments);
        }
    }

    public record Decision(boolean allowed, String reason) {
        public Decision { Objects.requireNonNull(reason); }
        public static Decision allow(String reason) { return new Decision(true, reason); }
        public static Decision deny(String reason) { return new Decision(false, reason); }
    }
}
