package vn.svframe.svrelationships.fabric.reward;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import vn.svframe.svrelationships.fabric.config.ConfigService;
import vn.svframe.svrelationships.fabric.config.GameplayDefinitionService;
import vn.svframe.svrelationships.fabric.config.RewardPolicyService;
import vn.svframe.svrelationships.fabric.persistence.RewardClaimRepository;
import vn.svframe.svrelationships.fabric.relationship.RelationshipService;
import vn.svframe.svrelationships.gameplay.PersonalityDefinition;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RelationshipRewardService {
    private final ConfigService config;
    private final GameplayDefinitionService definitions;
    private final RelationshipService relationships;
    private final ProviderHub providers;
    private final RewardClaimRepository claims;
    private final RewardPolicyService policies;
    private final WeightedRewardSelector selector = new WeightedRewardSelector();

    public RelationshipRewardService(ConfigService config, GameplayDefinitionService definitions, RelationshipService relationships,
                                     ProviderHub providers, RewardClaimRepository claims, RewardPolicyService policies) {
        this.config = config;
        this.definitions = definitions;
        this.relationships = relationships;
        this.providers = providers;
        this.claims = claims;
        this.policies = policies;
    }

    public ClaimResult claim(ServerPlayerEntity player, UUID pokemonId, String profileId, long nowMillis) {
        RewardProfileDefinition profile = definitions.snapshot().rewardProfiles().get(profileId);
        if (profile == null) return ClaimResult.UNKNOWN_PROFILE;
        RelationshipState relationship = relationships.state(player.getUuid(), pokemonId);
        if (!relationship.partner()) return ClaimResult.NOT_ELIGIBLE;
        long duration = GameplayDefinitionService.durationMillis(profile.period());
        if (duration <= 0) return ClaimResult.NOT_ELIGIBLE;

        UUID scopeId = switch (profile.scope()) {
            case "per_player" -> UUID.nameUUIDFromBytes((player.getUuid() + ":" + profile.id()).getBytes(StandardCharsets.UTF_8));
            case "per_household" -> relationship.householdId() == null ? relationship.relationshipId() : relationship.householdId();
            default -> relationship.relationshipId();
        };
        RewardPolicyService.Policy policy = policies.snapshot().policy(profile.id());
        RewardClaimKey key = nextClaimKey(scopeId, profile, relationship, duration, nowMillis, policy);
        if (key == null) return ClaimResult.ALREADY_CLAIMED;

        RewardClaimRepository.Claim claim = claims.reserve(key);
        RewardResolution resolution = claim.resolution();
        if (resolution == null) {
            long seed = key.relationshipId().getMostSignificantBits() ^ key.relationshipId().getLeastSignificantBits()
                    ^ profile.id().hashCode() ^ key.periodId().hashCode();
            resolution = selector.select(profile, seed, personalityRewardMultipliers(relationship));
            claim = claims.resolve(key, resolution);
        }
        if (claim.status() == RewardClaimStatus.DELIVERED) return ClaimResult.ALREADY_CLAIMED;
        if (!deliver(player, pokemonId, resolution, policy)) return ClaimResult.DELIVERY_FAILED;
        claims.delivered(key);
        return ClaimResult.DELIVERED;
    }

    private Map<String, Double> personalityRewardMultipliers(RelationshipState relationship) {
        if (relationship.personalityId() == null || relationship.personalityId().isBlank()) return Map.of();
        PersonalityDefinition personality = definitions.snapshot().personalities().get(relationship.personalityId());
        return personality == null ? Map.of() : personality.rewardWeightMultipliers();
    }

    private RewardClaimKey nextClaimKey(UUID scopeId, RewardProfileDefinition profile, RelationshipState relationship,
                                        long duration, long nowMillis, RewardPolicyService.Policy policy) {
        long currentPeriod = Math.floorDiv(nowMillis, duration);
        long firstPeriod = currentPeriod;
        if ("accumulate".equals(policy.offlinePolicy())) {
            long partnerPeriod = Math.floorDiv(Math.max(0L, relationship.partnerSinceMillis()), duration);
            long boundedWindow = currentPeriod - Math.max(0, policy.maxPendingPeriods() - 1L);
            firstPeriod = Math.max(partnerPeriod, boundedWindow);
        }
        for (long period = firstPeriod; period <= currentPeriod; period++) {
            RewardClaimKey key = new RewardClaimKey(scopeId, profile.id(), Long.toString(period));
            Optional<RewardClaimRepository.Claim> existing = claims.get(key);
            if (existing.isEmpty() || existing.get().status() != RewardClaimStatus.DELIVERED) return key;
            if (period == Long.MAX_VALUE) break;
        }
        return null;
    }

    private boolean deliver(ServerPlayerEntity player, UUID pokemonId, RewardResolution resolution,
                            RewardPolicyService.Policy policy) {
        return switch (resolution.rewardType()) {
            case "item" -> deliverItem(player, resolution.value(), resolution.amount(), policy.overflowPolicy());
            case "economy" -> deliverEconomy(player, resolution.value(), resolution.amount());
            case "progression" -> deliverProgression(player, pokemonId, resolution.value(), resolution.amount());
            default -> false;
        };
    }

    private boolean deliverItem(ServerPlayerEntity player, String rawId, long amount, String overflowPolicy) {
        Identifier id = Identifier.tryParse(rawId);
        if (id == null || !Registries.ITEM.containsId(id) || amount <= 0) return false;
        Item item = Registries.ITEM.get(id);
        if ("deny".equals(overflowPolicy) && !canFit(player, item, amount)) return false;

        long remaining = amount;
        while (remaining > 0) {
            int count = (int) Math.min(remaining, item.getMaxCount());
            ItemStack stack = new ItemStack(item, count);
            player.getInventory().insertStack(stack);
            if (!stack.isEmpty()) player.dropItem(stack, false);
            remaining -= count;
        }
        return true;
    }

    private boolean canFit(ServerPlayerEntity player, Item item, long amount) {
        ItemStack template = new ItemStack(item);
        long capacity = 0L;
        for (int slot = 0; slot < PlayerInventory.MAIN_SIZE; slot++) {
            ItemStack existing = player.getInventory().getStack(slot);
            if (existing.isEmpty()) capacity += template.getMaxCount();
            else if (ItemStack.areItemsAndComponentsEqual(existing, template) && existing.isStackable()) capacity += Math.max(0, existing.getMaxCount() - existing.getCount());
            if (capacity >= amount) return true;
        }
        return false;
    }

    private boolean deliverEconomy(ServerPlayerEntity player, String requestedProvider, long amount) {
        if (amount < 0) return false;
        EconomyProvider provider = null;
        if (!"default".equalsIgnoreCase(requestedProvider) && !requestedProvider.isBlank()) provider = providers.economy(requestedProvider).orElse(null);
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
