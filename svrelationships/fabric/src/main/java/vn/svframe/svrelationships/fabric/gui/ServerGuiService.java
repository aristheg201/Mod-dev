package vn.svframe.svrelationships.fabric.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import vn.svframe.svrelationships.fabric.command.PokemonReferenceResolver;
import vn.svframe.svrelationships.fabric.config.GameplayDefinitionService;
import vn.svframe.svrelationships.fabric.localization.MessageService;
import vn.svframe.svrelationships.fabric.relationship.LifeInteractionService;
import vn.svframe.svrelationships.fabric.relationship.PartnershipService;
import vn.svframe.svrelationships.fabric.relationship.RelationshipService;
import vn.svframe.svrelationships.fabric.reward.RelationshipRewardService;
import vn.svframe.svrelationships.integration.ProviderHub;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ServerGuiService {
    private final GuiDefinitionService definitions;
    private final MessageService messages;
    private final ProviderHub providers;
    private final RelationshipService relationships;
    private final LifeInteractionService interactions;
    private final PartnershipService partnerships;
    private final RelationshipRewardService rewards;

    public ServerGuiService(GuiDefinitionService definitions, MessageService messages, ProviderHub providers,
                            RelationshipService relationships, LifeInteractionService interactions,
                            PartnershipService partnerships, RelationshipRewardService rewards) {
        this.definitions = definitions;
        this.messages = messages;
        this.providers = providers;
        this.relationships = relationships;
        this.interactions = interactions;
        this.partnerships = partnerships;
        this.rewards = rewards;
    }

    public boolean openMain(ServerPlayerEntity player) {
        var pokemon = providers.pokemonProvider().flatMap(provider -> provider.ownedPokemon(player.getUuid()).stream().findFirst());
        return open("main", player, pokemon.map(p -> p.pokemonId()).orElse(null));
    }

    public boolean openRelationship(ServerPlayerEntity player, UUID pokemonId) {
        return open("relationship", player, pokemonId);
    }

    public boolean open(String guiId, ServerPlayerEntity player, UUID pokemonId) {
        GuiDefinitionService.GuiDefinition definition = definitions.snapshot().definitions().get(guiId);
        if (definition == null) return false;
        SimpleGui gui = new SimpleGui(screen(definition.screen()), player, false);
        gui.setLockPlayerInventory(true);
        Map<String, Object> placeholders = placeholders(player, pokemonId);
        gui.setTitle(messages.text(definition.titleKey(), placeholders));
        for (GuiDefinitionService.ComponentDefinition component : definition.components().values()) {
            Identifier itemId = Identifier.tryParse(component.itemId());
            if (itemId == null || !Registries.ITEM.containsId(itemId)) continue;
            Item item = Registries.ITEM.get(itemId);
            GuiElementBuilder builder = new GuiElementBuilder(item).setName(messages.text(component.nameKey(), placeholders));
            for (String loreKey : component.loreKeys()) builder.addLoreLine(messages.text(loreKey, placeholders));
            builder.setCallback((index, clickType, action) -> dispatch(component, player, pokemonId));
            gui.setSlot(component.slot(), builder);
        }
        gui.open();
        return true;
    }

    private void dispatch(GuiDefinitionService.ComponentDefinition component, ServerPlayerEntity player, UUID pokemonId) {
        switch (component.action()) {
            case "none" -> { }
            case "open_relationship" -> {
                if (pokemonId != null) openRelationship(player, pokemonId);
                else providers.pokemonProvider().flatMap(provider -> provider.ownedPokemon(player.getUuid()).stream().findFirst())
                        .ifPresent(snapshot -> openRelationship(player, snapshot.pokemonId()));
            }
            case "list_partners" -> relationships.partners(player.getUuid()).stream().findFirst()
                    .ifPresent(partner -> openRelationship(player, partner.key().pokemonId()));
            case "claim_reward" -> {
                if (pokemonId != null) rewards.claim(player, pokemonId, component.args().getOrDefault("profile", ""), System.currentTimeMillis());
                openRelationship(player, pokemonId);
            }
            case "interaction" -> {
                if (pokemonId != null) interactions.interact(player.getUuid(), pokemonId, component.args().getOrDefault("id", ""), System.currentTimeMillis());
                openRelationship(player, pokemonId);
            }
            case "gift" -> {
                if (pokemonId != null) interactions.gift(player, pokemonId, component.args().getOrDefault("id", ""), System.currentTimeMillis());
                openRelationship(player, pokemonId);
            }
            case "advance_partnership" -> {
                if (pokemonId != null) partnerships.advance(player.getUuid(), pokemonId, component.args().getOrDefault("milestone", ""));
                openRelationship(player, pokemonId);
            }
            default -> { }
        }
    }

    private Map<String, Object> placeholders(ServerPlayerEntity player, UUID pokemonId) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("player", player.getName().getString());
        values.put("partner_count", relationships.partners(player.getUuid()).size());
        values.put("partner_capacity", relationships.capacity(player.getUuid()));
        if (pokemonId != null) {
            values.put("pokemon_uuid", pokemonId);
            values.put("bond", relationships.progression(player.getUuid(), pokemonId, "bond"));
            values.put("romance", relationships.progression(player.getUuid(), pokemonId, "romance"));
            values.put("bond_rank", relationships.rank(player.getUuid(), pokemonId, "bond").orElse(""));
            values.put("romance_rank", relationships.rank(player.getUuid(), pokemonId, "romance").orElse(""));
            values.put("partner", relationships.state(player.getUuid(), pokemonId).partner());
            providers.pokemonProvider().flatMap(provider -> provider.findOwned(player.getUuid(), pokemonId)).ifPresent(snapshot -> {
                values.put("pokemon", snapshot.speciesId());
                values.put("level", snapshot.level());
                values.put("form", snapshot.formId());
            });
        }
        return values;
    }

    private static ScreenHandlerType<?> screen(String id) {
        return switch (id) {
            case "generic_9x1" -> ScreenHandlerType.GENERIC_9X1;
            case "generic_9x2" -> ScreenHandlerType.GENERIC_9X2;
            case "generic_9x3" -> ScreenHandlerType.GENERIC_9X3;
            case "generic_9x4" -> ScreenHandlerType.GENERIC_9X4;
            case "generic_9x5" -> ScreenHandlerType.GENERIC_9X5;
            case "hopper" -> ScreenHandlerType.HOPPER;
            case "generic_3x3" -> ScreenHandlerType.GENERIC_3X3;
            default -> ScreenHandlerType.GENERIC_9X6;
        };
    }
}
