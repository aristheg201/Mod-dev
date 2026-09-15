package vn.svframe.svrelationships.fabric.integration;

import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import vn.svframe.svrelationships.fabric.household.HouseholdService;

public final class TextPlaceholderBridge {
    private TextPlaceholderBridge() {
    }

    public static void register(HouseholdService households) {
        Placeholders.register(Identifier.of("svrelationships", "household_set"), (context, argument) -> {
            if (!context.hasPlayer()) {
                return PlaceholderResult.invalid();
            }
            boolean present = households.get(context.player().getUuid()).isPresent();
            return PlaceholderResult.value(Text.literal(Boolean.toString(present)));
        });
        Placeholders.register(Identifier.of("svrelationships", "household_profile"), (context, argument) -> {
            if (!context.hasPlayer()) {
                return PlaceholderResult.invalid();
            }
            return households.get(context.player().getUuid())
                    .map(state -> PlaceholderResult.value(Text.literal(state.profileId())))
                    .orElseGet(PlaceholderResult::invalid);
        });
        Placeholders.register(Identifier.of("svrelationships", "household_x"), (context, argument) -> coordinate(households, context.player(), Axis.X));
        Placeholders.register(Identifier.of("svrelationships", "household_y"), (context, argument) -> coordinate(households, context.player(), Axis.Y));
        Placeholders.register(Identifier.of("svrelationships", "household_z"), (context, argument) -> coordinate(households, context.player(), Axis.Z));
    }

    private static PlaceholderResult coordinate(HouseholdService households, net.minecraft.server.network.ServerPlayerEntity player, Axis axis) {
        if (player == null) {
            return PlaceholderResult.invalid();
        }
        return households.get(player.getUuid()).map(state -> {
            int value = switch (axis) {
                case X -> state.anchor().x();
                case Y -> state.anchor().y();
                case Z -> state.anchor().z();
            };
            return PlaceholderResult.value(Text.literal(Integer.toString(value)));
        }).orElseGet(PlaceholderResult::invalid);
    }

    private enum Axis {
        X, Y, Z
    }
}
