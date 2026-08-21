package vn.svframe.lively.security;

import vn.svframe.lively.ai.AiAction;
import vn.svframe.lively.ai.Decision;
import vn.svframe.lively.world.WorldMutationPolicy;

import java.util.Set;

/** Final gate between cognition and server mutation. */
public final class AuthorityLayer {
    private static final Set<String> SAFE_ACTIONS = Set.of(
            "consume_food", "seek_food", "perform_occupation", "offer_trade",
            "start_dialogue", "travel_home", "flee", "defend",
            "combat_action", "quest_proposal", "event_proposal", "semantic_structure_state"
    );

    public Validation validate(Decision decision, long currentNpcRevision, long currentWorldRevision) {
        if (decision.npcRevision() != currentNpcRevision) return new Validation(false, "stale_npc_revision");
        if (decision.worldRevision() != currentWorldRevision) return new Validation(false, "stale_world_revision");
        if (!SAFE_ACTIONS.contains(decision.action().type())) return new Validation(false, "unknown_action_type");
        if (decision.action().risk() == AiAction.Risk.PRIVILEGED) return new Validation(false, "privileged_ai_action");
        return new Validation(true, "accepted");
    }

    public Validation validateWorldMutation(WorldMutationPolicy policy, WorldMutationPolicy.Proposal proposal) {
        WorldMutationPolicy.Decision result = policy.evaluate(proposal);
        return new Validation(result.allowed(), result.reason());
    }

    public record Validation(boolean allowed, String reason) {}
}
