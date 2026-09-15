package vn.svframe.svrelationships.fabric.reward;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import vn.svframe.svrelationships.fabric.config.ConfigService;
import vn.svframe.svrelationships.fabric.config.GameplayDefinitionService;
import vn.svframe.svrelationships.fabric.persistence.RewardClaimRepository;
import vn.svframe.svrelationships.fabric.relationship.RelationshipService;
import vn.svframe.svrelationships.gameplay.RewardProfileDefinition;
import vn.svframe.svrelationships.integration.EconomyProvider;
import vn.svframe.svrelationships.integration.ProviderHub;
import vn.svframe.svrelationships.relationship.RelationshipState;
import vn.svframe.svrelationships.reward.RewardClaimKey;
import vn.svframe.svrelationships.reward.RewardClaimStatus;
import vn.svframe.svrelationships.reward.RewardResolution;
import vn.svframe.svrelationships.reward.WeightedRewardSelector;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

public final class RelationshipRewardService {
    private final ConfigService config;
    private final GameplayDefinitionService definitions;
    private final RelationshipService relationships;
    private final ProviderHub providers;
    private final RewardClaimRepository claims;
    private final WeightedRewardSelector selector = new WeightedRewardSelector();

    public RelationshipRewardService(ConfigService config, GameplayDefinitionService definitions, RelationshipService relationships,
                                     ProviderHub providers, RewardClaimRepository claims) {
        this.config = config;
        this.definitions = definitions;
        this.relationships = relationships;
        this.providers = providers;
        this.claims = claims;
    }

    public ClaimResult claim(ServerPlayerEntity player, UUID pokemonId, String profileId, long nowMillis) {
        RewardProfileDefinition profile = definitions.snapshot().rewardProfiles().get(profileId);
        if (profile == null) return ClaimResult.UNKNOWN_PROFILE;
        RelationshipState relationship = relationships.state(player.getUuid(), pokemonId);
        if (!relationship.partner()) return ClaimResult.NOT_ELIGIBLE;
        long duration = GameplayDefinitionService.durationMillis(profile.period());
        if (duration <= 0) return ClaimResult.NOT_ELIGIBLE;
        String periodId = Long.toUnsignedString(Math.floorDiv(nowMillis, duration));
        UUID scopeId = switch (profile.scope()) {
            case "per_player" -> UUID.nameUUIDFromBytes((player.getUuid() + ":" + profile.id()).getBytes(StandardCharsets.UTF_8));
            case "per_household" -> relationship.householdId() == null ? relationship.relationshipId() : relationship.householdId();
            default -> relationship.relationshipId();
        };
        RewardClaimKey key = new RewardClaimKey(scopeId, profile.id(), periodId);
        var existing = claims.get(key);
        if (existing.isPresent() && existing.get().status() == RewardClaimStatus.DELIVERED) return ClaimResult.ALREADY_CLAIMED;

        claims.reserve(key);
        RewardResolution resolution = claims.get(key).map(RewardClaimRepository.Claim::resolution).orElse(null);
        if (resolution == null) {
            long seed = key.relationshipId().getMostSignificantBits() ^ key.relationshipId().getLeastSignificantBits()
                    ^ profile.id().hashCode() ^ periodId.hashCode();
            resolution = selector.select(profile, seed);
            claims.resolve(key, resolution);
        }
        if (!deliver(player, pokemonId, resolution)) return ClaimResult.DELIVERY_FAILED;
        claims.delivered(key);
        return ClaimResult.DELIVERED;
    }

    private boolean deliver(ServerPlayerEntity player, UUID pokemonId, RewardResolution resolution) {
        return switch (resolution.rewardType()) {
            case "item" -> deliverItem(player, resolution.value(), resolution.amount());
            case "economy" -> deliverEconomy(player, resolution.value(), resolution.amount());
            case "progression" -> deliverProgression(player, pokemonId, resolution.value(), resolution.amount());
            default -> false;
        };
    }

    private boolean deliverItem(ServerPlayerEntity player, String rawId, long amount) {
        Identifier id = Identifier.tryParse(rawId);
        if (id == null || !Registries.ITEM.containsId(id) || amount <= 0) return false;
        var item = Registries.ITEM.get(id);
        long remaining = amount;
        while (remaining > 0) {
            int count = (int) Math.min(remaining, item.getMaxCount());
            ItemStack stack = new ItemStack(item, count);
            if (!player.getInventory().insertStack(stack) && !stack.isEmpty()) player.dropItem(stack, false);
            remaining -= count;
        }
        return true;
    }

    private boolean deliverEconomy(ServerPlayerEntity player, String requestedProvider, long amount) {
        if (amount < 0) return false;
        EconomyProvider provider = null;
        if (!"default".equalsIgnoreCase(requestedProvider) && !requestedProvider.isBlank()) {
            provider = providers.economy(requestedProvider).orElse(null);
        }
        if (provider == null) {
            for (String id : config.snapshot().economyPriority()) {
                Optional<EconomyProvider> candidate = providers.economy(id);
                if (candidate.isPresent() && candidate.get().available()) { provider = candidate.get(); break; }
            }
        }
        return provider != null && provider.deposit(player.getUuid(), "default", BigDecimal.valueOf(amount));
    }

    private boolean deliverProgression(ServerPlayerEntity player, UUID pokemonId, String trackId, long amount) {
        if (!definitions.snapshot().progressionTracks().containsKey(trackId)) return false;
        relationships.addProgression(player.getUuid(), pokemonId, trackId, amount);
        return true;
    }

    public enum ClaimResult { DELIVERED, ALREADY_CLAIMED, NOT_ELIGIBLE, UNKNOWN_PROFILE, DELIVERY_FAILED }
}
