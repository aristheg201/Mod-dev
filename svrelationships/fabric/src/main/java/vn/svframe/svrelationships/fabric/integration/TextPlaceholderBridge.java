package vn.svframe.svrelationships.fabric.integration;

import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import vn.svframe.svrelationships.fabric.household.HouseholdService;
import vn.svframe.svrelationships.fabric.relationship.RelationshipService;

import java.util.UUID;

public final class TextPlaceholderBridge {
    private TextPlaceholderBridge() {}

    public static void register(HouseholdService households, RelationshipService relationships) {
        Placeholders.register(Identifier.of("svrelationships", "household_set"), (context, argument) -> {
            if (!context.hasPlayer()) return PlaceholderResult.invalid();
            return value(Boolean.toString(households.get(context.player().getUuid()).isPresent()));
        });
        Placeholders.register(Identifier.of("svrelationships", "household_profile"), (context, argument) -> {
            if (!context.hasPlayer()) return PlaceholderResult.invalid();
            return households.get(context.player().getUuid()).map(state -> value(state.profileId())).orElseGet(PlaceholderResult::invalid);
        });
        Placeholders.register(Identifier.of("svrelationships", "household_x"), (context, argument) -> coordinate(households, context.player(), Axis.X));
        Placeholders.register(Identifier.of("svrelationships", "household_y"), (context, argument) -> coordinate(households, context.player(), Axis.Y));
        Placeholders.register(Identifier.of("svrelationships", "household_z"), (context, argument) -> coordinate(households, context.player(), Axis.Z));
        Placeholders.register(Identifier.of("svrelationships", "partner_count"), (context, argument) -> {
            if (!context.hasPlayer()) return PlaceholderResult.invalid();
            return value(Integer.toString(relationships.partners(context.player().getUuid()).size()));
        });
        Placeholders.register(Identifier.of("svrelationships", "partner_capacity"), (context, argument) -> {
            if (!context.hasPlayer()) return PlaceholderResult.invalid();
            return value(Integer.toString(relationships.capacity(context.player().getUuid())));
        });
        Placeholders.register(Identifier.of("svrelationships", "bond"), (context, argument) -> progression(context.player(), argument, relationships, "bond"));
        Placeholders.register(Identifier.of("svrelationships", "romance"), (context, argument) -> progression(context.player(), argument, relationships, "romance"));
        Placeholders.register(Identifier.of("svrelationships", "personality"), (context, argument) -> relationshipText(context.player(), argument, relationships, Field.PERSONALITY));
        Placeholders.register(Identifier.of("svrelationships", "partner"), (context, argument) -> relationshipText(context.player(), argument, relationships, Field.PARTNER));
    }

    private static PlaceholderResult progression(ServerPlayerEntity player, String argument, RelationshipService relationships, String track) {
        UUID pokemon = pokemonId(argument); if (player == null || pokemon == null) return PlaceholderResult.invalid();
        return value(Long.toString(relationships.progression(player.getUuid(), pokemon, track)));
    }

    private static PlaceholderResult relationshipText(ServerPlayerEntity player, String argument, RelationshipService relationships, Field field) {
        UUID pokemon = pokemonId(argument); if (player == null || pokemon == null) return PlaceholderResult.invalid();
        return relationships.existing(player.getUuid(), pokemon).map(state -> switch (field) {
            case PERSONALITY -> state.personalityId() == null ? "" : state.personalityId();
            case PARTNER -> Boolean.toString(state.partner());
        }).map(TextPlaceholderBridge::value).orElseGet(PlaceholderResult::invalid);
    }

    private static UUID pokemonId(String argument) {
        if (argument == null || argument.isBlank()) return null;
        String value = argument.startsWith("uuid:") ? argument.substring(5) : argument;
        try { return UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; }
    }

    private static PlaceholderResult coordinate(HouseholdService households, ServerPlayerEntity player, Axis axis) {
        if (player == null) return PlaceholderResult.invalid();
        return households.get(player.getUuid()).map(state -> value(Integer.toString(switch (axis) {
            case X -> state.anchor().x(); case Y -> state.anchor().y(); case Z -> state.anchor().z();
        }))).orElseGet(PlaceholderResult::invalid);
    }
    private static PlaceholderResult value(String text) { return PlaceholderResult.value(Text.literal(text)); }
    private enum Axis { X, Y, Z }
    private enum Field { PERSONALITY, PARTNER }
}
