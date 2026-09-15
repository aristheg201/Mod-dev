package vn.svframe.svrelationships.fabric.relationship;

import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svrelationships.fabric.config.AnniversaryDefinitionService;
import vn.svframe.svrelationships.fabric.reward.RelationshipRewardService;
import vn.svframe.svrelationships.gameplay.AnniversaryDefinition;
import vn.svframe.svrelationships.gameplay.RelationshipCycle;

import java.util.UUID;

public final class AnniversaryService {
    private final AnniversaryDefinitionService definitions;
    private final RelationshipService relationships;
    private final RelationshipRewardService rewards;

    public AnniversaryService(AnniversaryDefinitionService definitions, RelationshipService relationships, RelationshipRewardService rewards) { this.definitions = definitions; this.relationships = relationships; this.rewards = rewards; }

    public Result claim(ServerPlayerEntity player, UUID pokemonId, String anniversaryId, long nowMillis) {
        AnniversaryDefinition definition = definitions.snapshot().definitions().get(anniversaryId);
        if (definition == null) return new Result(Status.UNKNOWN_DEFINITION, 0, null);
        var state = relationships.existing(player.getUuid(), pokemonId).orElse(null);
        if (state == null || !state.partner() || state.partnerSinceMillis() <= 0) return new Result(Status.NOT_ELIGIBLE, 0, definition.messageKey());
        RelationshipCycle cycle = RelationshipCycle.resolve(state.partnerSinceMillis(), nowMillis, definition.periodMillis());
        if (cycle.index() < definition.minimumCycles()) return new Result(Status.NOT_DUE, cycle.index(), definition.messageKey());
        String cycleFlag = "anniversary." + anniversaryId + ".cycle";
        long lastCycle = parseLong(state.flag(cycleFlag), -1L);
        if (lastCycle >= cycle.index()) return new Result(Status.ALREADY_CLAIMED, cycle.index(), definition.messageKey());
        if (!definition.rewardProfile().isBlank()) {
            var rewardResult = rewards.grantOnce(player, pokemonId, definition.rewardProfile(), state.relationshipId(), cycle.claimId(anniversaryId));
            if (rewardResult != RelationshipRewardService.ClaimResult.DELIVERED && rewardResult != RelationshipRewardService.ClaimResult.ALREADY_CLAIMED) return new Result(Status.REWARD_FAILED, cycle.index(), definition.messageKey());
        }
        relationships.applyProgressionOnce(player.getUuid(), pokemonId, "anniversary." + anniversaryId + ".effects." + cycle.index(), definition.progressionDeltas());
        state.setFlag(cycleFlag, Long.toString(cycle.index()));
        relationships.touch();
        return new Result(Status.SUCCESS, cycle.index(), definition.messageKey());
    }

    private static long parseLong(String value, long fallback) { if (value == null) return fallback; try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return fallback; } }
    public enum Status { SUCCESS, UNKNOWN_DEFINITION, NOT_ELIGIBLE, NOT_DUE, ALREADY_CLAIMED, REWARD_FAILED }
    public record Result(Status status, long cycle, String messageKey) {}
}
