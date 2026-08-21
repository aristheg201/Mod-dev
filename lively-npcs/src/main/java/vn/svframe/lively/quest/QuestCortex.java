package vn.svframe.lively.quest;

import vn.svframe.lively.model.NpcSnapshot;

import java.util.Optional;

/** Causal quest proposal from NPC state; rewards remain typed and bounded. */
public final class QuestCortex {
    public record QuestProposal(String type, String target, int amount, long rewardBudget, long sourceRevision) {}

    public Optional<QuestProposal> propose(NpcSnapshot npc) {
        String missing = npc.beliefs().get("missing_resource");
        if (missing != null && !missing.isBlank()) {
            int amount = Math.max(1, Math.min(16, (int) Math.ceil(1D + npc.need("money") * 4D)));
            return Optional.of(new QuestProposal("fetch_resource", missing, amount, amount * 250L, npc.revision()));
        }
        String missingCreature = npc.beliefs().get("missing_creature");
        if (missingCreature != null && !missingCreature.isBlank()) {
            return Optional.of(new QuestProposal("locate_creature", missingCreature, 1, 1500L, npc.revision()));
        }
        return Optional.empty();
    }

    public Validation validate(QuestProposal proposal, long currentRevision) {
        if (proposal.sourceRevision() != currentRevision) return new Validation(false, "stale_source");
        if (proposal.amount() < 1 || proposal.amount() > 64) return new Validation(false, "amount_out_of_range");
        if (proposal.rewardBudget() < 0 || proposal.rewardBudget() > 100_000L) return new Validation(false, "reward_out_of_range");
        if (proposal.target().isBlank() || proposal.target().length() > 128) return new Validation(false, "invalid_target");
        return new Validation(true, "accepted");
    }

    public record Validation(boolean allowed, String reason) {}
}
