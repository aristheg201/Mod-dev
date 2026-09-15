package vn.svframe.svrelationships.fabric.relationship;

import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svrelationships.fabric.config.AnniversaryDefinitionService;
import vn.svframe.svrelationships.fabric.reward.RelationshipRewardService;
import vn.svframe.svrelationships.gameplay.AnniversaryDefinition;

import java.util.UUID;

public final class AnniversaryService {
    private final AnniversaryDefinitionService definitions;
    private final RelationshipService relationships;
    private final RelationshipRewardService rewards;

    public AnniversaryService(AnniversaryDefinitionService definitions, RelationshipService relationships, RelationshipRewardService rewards) {
        this.definitions = definitions;
        this.relationships = relationships;
        this.rewards = rewards;
    }

    public Result claim(ServerPlayerEntity player, UUID pokemonId, String anniversaryId, long nowMillis) {
        AnniversaryDefinition definition = definitions.snapshot().definitions().get(anniversaryId);
        if (definition == null) return new Result(Status.UNKNOWN_DEFINITION, 0, null);
        var state = relationships.existing(player.getUuid(), pokemonId).orElse(null);
        if (state == null || !state.partner() || state.partnerSinceMillis() <= 0) return new Result(Status.NOT_ELIGIBLE, 0, definition.messageKey());

        long elapsed = Math.max(0L, nowMillis - state.partnerSinceMillis());
        long cycle = Math.floorDiv(elapsed, definition.periodMillis());
        if (cycle < definition.minimumCycles()) return new Result(Status.NOT_DUE, cycle, definition.messageKey());

        String cycleFlag = "anniversary." + anniversaryId + ".cycle";
        long lastCycle = -1;
        String stored = state.flag(cycleFlag);
        if (stored != null) {
            try { lastCycle = Long.parseLong(stored); }
            catch (NumberFormatException ignored) { lastCycle = -1; }
        }
        if (lastCycle >= cycle) return new Result(Status.ALREADY_CLAIMED, cycle, definition.messageKey());

        if (!definition.rewardProfile().isBlank()) {
            String claimId = "anniversary:" + anniversaryId + ":" + cycle;
            var rewardResult = rewards.grantOnce(player, pokemonId, definition.rewardProfile(), state.relationshipId(), claimId);
            if (rewardResult != RelationshipRewardService.ClaimResult.DELIVERED
                    && rewardResult != RelationshipRewardService.ClaimResult.ALREADY_CLAIMED) {
                return new Result(Status.REWARD_FAILED, cycle, definition.messageKey());
            }
        }

        relationships.applyProgressionOnce(
                player.getUuid(), pokemonId,
                "anniversary." + anniversaryId + ".effects." + cycle,
                definition.progressionDeltas()
        );
        state.setFlag(cycleFlag, Long.toString(cycle));
        relationships.touch();
        return new Result(Status.SUCCESS, cycle, definition.messageKey());
    }

    public enum Status { SUCCESS, UNKNOWN_DEFINITION, NOT_ELIGIBLE, NOT_DUE, ALREADY_CLAIMED, REWARD_FAILED }
    public record Result(Status status, long cycle, String messageKey) {}
}
