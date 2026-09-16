package vn.svframe.svrelationships.fabric.relationship;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import vn.svframe.svrelationships.fabric.config.GameplayDefinitionService;
import vn.svframe.svrelationships.gameplay.GiftDefinition;
import vn.svframe.svrelationships.gameplay.InteractionDefinition;
import vn.svframe.svrelationships.gameplay.PersonalityDefinition;
import vn.svframe.svrelationships.relationship.RelationshipState;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class LifeInteractionService {
    private final GameplayDefinitionService definitions;
    private final RelationshipService relationships;

    public LifeInteractionService(GameplayDefinitionService definitions, RelationshipService relationships) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.relationships = Objects.requireNonNull(relationships, "relationships");
    }

    public Optional<String> messageKey(String interactionId) {
        InteractionDefinition definition = definitions.snapshot().interactions().get(interactionId);
        return definition == null ? Optional.empty() : Optional.of(definition.messageKey());
    }

    public Result interact(UUID playerId, UUID pokemonId, String interactionId, long nowMillis) {
        InteractionDefinition definition = definitions.snapshot().interactions().get(interactionId);
        if (definition == null) return Result.UNKNOWN_DEFINITION;
        RelationshipState state = relationships.state(playerId, pokemonId);
        String cooldownId = "interaction:" + interactionId;
        if (state.cooldownUntil(cooldownId) > nowMillis) return Result.COOLDOWN;
        if (!definition.requiredRoute().isBlank()) {
            String current = relationships.currentRoute(playerId, pokemonId, definition.requiredRoute());
            if (!matchesStateExpression(current, definition.requiredState())) return Result.REQUIREMENTS;
        }
        applyProgression(playerId, pokemonId, state, definition.progressionDeltas());
        relationships.setCooldown(playerId, pokemonId, cooldownId, Math.addExact(nowMillis, definition.cooldownMillis()));
        return Result.SUCCESS;
    }

    private static boolean matchesStateExpression(String current, String expression) {
        if (expression == null || expression.isBlank() || "*".equals(expression)) return true;
        return Arrays.stream(expression.split("\\|"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .anyMatch(current::equals);
    }

    public Result gift(ServerPlayerEntity player, UUID pokemonId, String giftId, long nowMillis) {
        GiftDefinition definition = definitions.snapshot().gifts().get(giftId);
        if (definition == null) return Result.UNKNOWN_DEFINITION;
        Identifier itemId = Identifier.tryParse(definition.itemId());
        if (itemId == null || !Registries.ITEM.containsId(itemId)) return Result.INVALID_ITEM;
        Item item = Registries.ITEM.get(itemId);
        if (count(player, item) < definition.consumeAmount()) return Result.MISSING_ITEM;
        RelationshipState state = relationships.state(player.getUuid(), pokemonId);
        String cooldownId = "gift:" + giftId;
        if (state.cooldownUntil(cooldownId) > nowMillis) return Result.COOLDOWN;
        remove(player, item, definition.consumeAmount());
        applyProgression(player.getUuid(), pokemonId, state, definition.progressionDeltas());
        relationships.setCooldown(player.getUuid(), pokemonId, cooldownId, Math.addExact(nowMillis, definition.cooldownMillis()));
        return Result.SUCCESS;
    }

    private void applyProgression(UUID playerId, UUID pokemonId, RelationshipState state, Map<String, Long> deltas) {
        PersonalityDefinition personality = state.personalityId() == null ? null : definitions.snapshot().personalities().get(state.personalityId());
        for (var entry : deltas.entrySet()) {
            double multiplier = personality == null ? 1.0D : personality.progressionMultipliers().getOrDefault(entry.getKey(), 1.0D);
            long adjusted = Math.round(entry.getValue() * multiplier);
            relationships.addProgression(playerId, pokemonId, entry.getKey(), adjusted);
        }
    }

    private static int count(ServerPlayerEntity player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) count += stack.getCount();
        }
        return count;
    }

    private static void remove(ServerPlayerEntity player, Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isOf(item)) continue;
            int taken = Math.min(remaining, stack.getCount());
            stack.decrement(taken);
            remaining -= taken;
        }
        if (remaining != 0) throw new IllegalStateException("Inventory changed while consuming gift");
    }

    public enum Result { SUCCESS, UNKNOWN_DEFINITION, COOLDOWN, REQUIREMENTS, MISSING_ITEM, INVALID_ITEM }
}
