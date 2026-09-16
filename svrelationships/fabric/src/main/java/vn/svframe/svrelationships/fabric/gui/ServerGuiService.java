package vn.svframe.svrelationships.fabric.gui;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Species;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import vn.svframe.svrelationships.fabric.family.DaycareRuntimeService;
import vn.svframe.svrelationships.fabric.localization.MessageService;
import vn.svframe.svrelationships.fabric.persistence.LineageRepository;
import vn.svframe.svrelationships.fabric.relationship.CeremonyService;
import vn.svframe.svrelationships.fabric.relationship.LifeInteractionService;
import vn.svframe.svrelationships.fabric.relationship.PartnershipService;
import vn.svframe.svrelationships.fabric.relationship.RelationshipService;
import vn.svframe.svrelationships.fabric.reward.RelationshipRewardService;
import vn.svframe.svrelationships.family.DaycareDefinition;
import vn.svframe.svrelationships.integration.ProviderHub;
import vn.svframe.svrelationships.integration.PokemonProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Generic config-driven inventory GUI renderer. */
public final class ServerGuiService {
    private final GuiDefinitionService definitions;
    private final MessageService messages;
    private final ProviderHub providers;
    private final RelationshipService relationships;
    private final LifeInteractionService interactions;
    private final PartnershipService partnerships;
    private final RelationshipRewardService rewards;
    private final CeremonyService ceremonies;
    private final DaycareRuntimeService daycare;
    private final LineageRepository lineage;
    private final Map<UUID, DaycareDraft> daycareDrafts = new ConcurrentHashMap<>();

    public ServerGuiService(GuiDefinitionService definitions, MessageService messages, ProviderHub providers,
                            RelationshipService relationships, LifeInteractionService interactions,
                            PartnershipService partnerships, RelationshipRewardService rewards,
                            CeremonyService ceremonies, DaycareRuntimeService daycare, LineageRepository lineage) {
        this.definitions = definitions;
        this.messages = messages;
        this.providers = providers;
        this.relationships = relationships;
        this.interactions = interactions;
        this.partnerships = partnerships;
        this.rewards = rewards;
        this.ceremonies = ceremonies;
        this.daycare = daycare;
        this.lineage = lineage;
    }

    public boolean openMain(ServerPlayerEntity player) { return open("main", player, null, 0); }
    public boolean openRelationship(ServerPlayerEntity player, UUID pokemonId) { return open("relationship", player, pokemonId, 0); }
    public boolean open(String guiId, ServerPlayerEntity player, UUID pokemonId) { return open(guiId, player, pokemonId, 0); }

    public boolean open(String guiId, ServerPlayerEntity player, UUID pokemonId, int requestedPage) {
        GuiDefinitionService.GuiDefinition definition = definitions.snapshot().definitions().get(guiId);
        if (definition == null) return false;
        List<ResolvedRepeater> repeaters = resolveRepeaters(definition, player);
        int pages = repeaters.stream().mapToInt(ResolvedRepeater::pageCount).max().orElse(1);
        int page = Math.max(0, Math.min(requestedPage, Math.max(0, pages - 1)));
        SimpleGui gui = new SimpleGui(screen(definition.screen()), player, false);
        gui.setLockPlayerInventory(true);
        Map<String, Object> base = placeholders(player, pokemonId);
        base.put("page", page + 1);
        base.put("pages", pages);
        addDraftPlaceholders(player, base);
        gui.setTitle(messages.text(definition.titleKey(), base));

        for (var component : definition.components().values()) {
            if (!conditions(component.conditions(), player, pokemonId, page, pages)) continue;
            GuiElementBuilder builder = itemElement(component.itemId(), component.nameKey(), component.loreKeys(), base);
            if (builder == null) continue;
            Map<String, Object> context = Map.copyOf(base);
            builder.setCallback((index, clickType, action) -> dispatch(component.action(), component.args(), context, player, pokemonId, guiId, page, pages));
            gui.setSlot(component.slot(), builder);
        }

        for (ResolvedRepeater resolved : repeaters) {
            var repeater = resolved.definition();
            int offset = page * repeater.slots().size();
            for (int local = 0; local < repeater.slots().size(); local++) {
                int index = offset + local;
                if (index >= resolved.entries().size()) break;
                DynamicEntry entry = resolved.entries().get(index);
                Map<String, Object> values = new LinkedHashMap<>(base);
                values.putAll(entry.placeholders());
                if (!conditions(repeater.conditions(), player, entry.pokemonId(), page, pages)) continue;
                GuiElementBuilder builder = repeaterElement(repeater, entry, values);
                if (builder == null) continue;
                Map<String, Object> context = Map.copyOf(values);
                builder.setCallback((slot, clickType, action) -> dispatch(repeater.action(), repeater.args(), context, player, entry.pokemonId(), guiId, page, pages));
                gui.setSlot(repeater.slots().get(local), builder);
            }
        }
        gui.open();
        return true;
    }

    private GuiElementBuilder repeaterElement(GuiDefinitionService.RepeaterDefinition repeater, DynamicEntry entry, Map<String, Object> values) {
        if ("pokemon_model".equals(repeater.visual()) && entry.pokemonVisual() != null) {
            GuiElementBuilder result = pokemonModelElement(entry.pokemonVisual(), repeater.nameKey(), repeater.loreKeys(), values);
            if (result != null) return result;
        }
        return itemElement(repeater.itemId(), repeater.nameKey(), repeater.loreKeys(), values);
    }

    private GuiElementBuilder pokemonModelElement(PokemonVisual visual, String nameKey, List<String> loreKeys, Map<String, Object> values) {
        Identifier speciesId = Identifier.tryParse(visual.speciesId());
        if (speciesId == null) return null;
        Species species = PokemonSpecies.INSTANCE.getByIdentifier(speciesId);
        if (species == null) return null;
        try {
            ItemStack stack = PokemonItem.from(species, visual.aspects().toArray(String[]::new));
            GuiElementBuilder builder = new GuiElementBuilder(stack).setName(messages.text(nameKey, values));
            for (String lore : loreKeys) builder.addLoreLine(messages.text(lore, values));
            return builder;
        } catch (RuntimeException exception) { return null; }
    }

    private GuiElementBuilder itemElement(String rawItem, String nameKey, List<String> loreKeys, Map<String, Object> values) {
        Identifier id = Identifier.tryParse(rawItem);
        if (id == null || !Registries.ITEM.containsId(id)) return null;
        Item item = Registries.ITEM.get(id);
        GuiElementBuilder builder = new GuiElementBuilder(item).setName(messages.text(nameKey, values));
        for (String lore : loreKeys) builder.addLoreLine(messages.text(lore, values));
        return builder;
    }

    private void dispatch(String action, Map<String, String> rawArgs, Map<String, Object> context,
                          ServerPlayerEntity player, UUID pokemonId, String currentGui, int page, int pages) {
        Map<String, String> args = resolveArgs(rawArgs, context);
        switch (action) {
            case "none" -> { }
            case "open_gui" -> open(args.getOrDefault("id", "main"), player, pokemonId, 0);
            case "open_relationship" -> { if (pokemonId != null) openRelationship(player, pokemonId); }
            case "page_next" -> { if (page + 1 < pages) open(currentGui, player, pokemonId, page + 1); }
            case "page_previous" -> { if (page > 0) open(currentGui, player, pokemonId, page - 1); }
            case "claim_reward" -> {
                if (pokemonId != null) sendRewardFeedback(player, rewards.claim(player, pokemonId, args.getOrDefault("profile", ""), System.currentTimeMillis()));
                if (pokemonId != null) openRelationship(player, pokemonId);
            }
            case "interaction" -> {
                if (pokemonId != null) {
                    String id = args.getOrDefault("id", "");
                    var result = interactions.interact(player.getUuid(), pokemonId, id, System.currentTimeMillis());
                    sendInteractionFeedback(player, id, result);
                    openRelationship(player, pokemonId);
                }
            }
            case "gift" -> {
                if (pokemonId != null) {
                    var result = interactions.gift(player, pokemonId, args.getOrDefault("id", ""), System.currentTimeMillis());
                    sendGiftFeedback(player, result);
                    openRelationship(player, pokemonId);
                }
            }
            case "advance_partnership" -> {
                if (pokemonId != null) {
                    var result = partnerships.advance(player.getUuid(), pokemonId, args.getOrDefault("milestone", ""));
                    sendPartnershipFeedback(player, result);
                    openRelationship(player, pokemonId);
                }
            }
            case "ceremony" -> {
                if (pokemonId != null) {
                    var result = ceremonies.perform(player.getUuid(), pokemonId, args.getOrDefault("id", ""), System.currentTimeMillis());
                    if (result.status() == CeremonyService.Status.SUCCESS && result.messageKey() != null) player.sendMessage(messages.text(result.messageKey()), false);
                    else if (result.status() != CeremonyService.Status.SUCCESS) player.sendMessage(messages.text("command.partnership.invalid_route_transition"), false);
                    openRelationship(player, pokemonId);
                }
            }
            case "daycare_begin" -> beginDaycare(player, args.getOrDefault("definition", ""));
            case "daycare_select" -> selectDaycareParticipant(player, pokemonId);
            case "daycare_cancel" -> { daycareDrafts.remove(player.getUuid()); open("daycare", player, null, 0); }
            default -> { }
        }
    }

    private void beginDaycare(ServerPlayerEntity player, String definitionId) {
        DaycareDefinition definition = daycare.definition(definitionId).orElse(null);
        if (definition == null) { player.sendMessage(messages.text("command.daycare.unknown_definition"), false); return; }
        daycareDrafts.put(player.getUuid(), new DaycareDraft(definitionId, new ArrayList<>()));
        if (definition.participantRoles().isEmpty()) finishDaycareDraft(player);
        else open("daycare_setup", player, null, 0);
    }

    private void selectDaycareParticipant(ServerPlayerEntity player, UUID pokemonId) {
        DaycareDraft draft = daycareDrafts.get(player.getUuid());
        if (draft == null || pokemonId == null) { open("daycare", player, null, 0); return; }
        DaycareDefinition definition = daycare.definition(draft.definitionId()).orElse(null);
        if (definition == null) { daycareDrafts.remove(player.getUuid()); player.sendMessage(messages.text("command.daycare.unknown_definition"), false); return; }
        if (draft.participants().contains(pokemonId)) {
            player.sendMessage(messages.text("command.daycare.invalid_participants"), false);
            open("daycare_setup", player, null, 0);
            return;
        }
        draft.participants().add(pokemonId);
        if (draft.participants().size() >= definition.participantRoles().size()) finishDaycareDraft(player);
        else open("daycare_setup", player, null, 0);
    }

    private void finishDaycareDraft(ServerPlayerEntity player) {
        DaycareDraft draft = daycareDrafts.get(player.getUuid());
        if (draft == null) return;
        var result = daycare.start(player.getUuid(), draft.definitionId(), List.copyOf(draft.participants()), System.currentTimeMillis());
        sendDaycareFeedback(player, result);
        daycareDrafts.remove(player.getUuid());
        open("daycare", player, null, 0);
    }

    private void sendInteractionFeedback(ServerPlayerEntity player, String id, LifeInteractionService.Result result) {
        String key = switch (result) {
            case SUCCESS -> interactions.messageKey(id).orElse("command.interaction.success");
            case UNKNOWN_DEFINITION -> "command.interaction.unknown_definition";
            case COOLDOWN -> "command.interaction.cooldown";
            case REQUIREMENTS -> "command.interaction.requirements";
            case MISSING_ITEM -> "command.interaction.missing_item";
            case INVALID_ITEM -> "command.interaction.invalid_item";
        };
        player.sendMessage(messages.text(key), false);
    }

    private void sendGiftFeedback(ServerPlayerEntity player, LifeInteractionService.Result result) {
        String key = switch (result) {
            case SUCCESS -> "command.gift.success";
            case UNKNOWN_DEFINITION -> "command.gift.unknown_definition";
            case COOLDOWN -> "command.gift.cooldown";
            case REQUIREMENTS -> "command.gift.requirements";
            case MISSING_ITEM -> "command.gift.missing_item";
            case INVALID_ITEM -> "command.gift.invalid_item";
        };
        player.sendMessage(messages.text(key), false);
    }

    private void sendPartnershipFeedback(ServerPlayerEntity player, PartnershipService.Result result) {
        String key = switch (result) {
            case SUCCESS -> "command.partnership.success";
            case UNKNOWN_MILESTONE -> "command.partnership.unknown_milestone";
            case POKEMON_PROVIDER_UNAVAILABLE -> "command.partnership.pokemon_provider_unavailable";
            case NOT_OWNED -> "command.partnership.not_owned";
            case SELECTOR_REJECTED -> "command.partnership.selector_rejected";
            case HOUSEHOLD_REQUIRED -> "command.partnership.household_required";
            case PROGRESSION_REQUIRED -> "command.partnership.progression_required";
            case CAPACITY_FULL -> "command.partnership.capacity_full";
            case INVALID_ROUTE_TRANSITION -> "command.partnership.invalid_route_transition";
        };
        player.sendMessage(messages.text(key), false);
    }

    private void sendRewardFeedback(ServerPlayerEntity player, RelationshipRewardService.ClaimResult result) {
        String key = switch (result) {
            case DELIVERED -> "command.reward.delivered";
            case ALREADY_CLAIMED -> "command.reward.already_claimed";
            case NOT_ELIGIBLE -> "command.reward.not_eligible";
            case UNKNOWN_PROFILE -> "command.reward.unknown_profile";
            case DELIVERY_FAILED -> "command.reward.delivery_failed";
        };
        player.sendMessage(messages.text(key), false);
    }

    private void sendDaycareFeedback(ServerPlayerEntity player, DaycareRuntimeService.StartResult result) {
        String key = switch (result) {
            case STARTED -> "command.daycare.started";
            case UNKNOWN_DEFINITION -> "command.daycare.unknown_definition";
            case ALREADY_ACTIVE -> "command.daycare.already_active";
            case NOT_OWNED -> "command.daycare.not_owned";
            case NOT_PARTNER -> "command.daycare.not_partner";
            case COST_FAILED -> "command.daycare.cost_failed";
            case INVALID_PARTICIPANTS -> "command.daycare.invalid_participants";
        };
        player.sendMessage(messages.text(key), false);
    }

    private Map<String, String> resolveArgs(Map<String, String> args, Map<String, Object> context) {
        Map<String, String> result = new LinkedHashMap<>();
        args.forEach((key, value) -> {
            String resolved = value;
            for (var entry : context.entrySet()) resolved = resolved.replace("<" + entry.getKey() + ">", String.valueOf(entry.getValue()));
            result.put(key, resolved);
        });
        return result;
    }

    private List<ResolvedRepeater> resolveRepeaters(GuiDefinitionService.GuiDefinition gui, ServerPlayerEntity player) {
        List<ResolvedRepeater> result = new ArrayList<>();
        for (var repeater : gui.repeaters().values()) {
            List<DynamicEntry> entries = switch (repeater.source()) {
                case "owned_pokemon" -> owned(player);
                case "partners" -> partners(player);
                case "daycare_sessions" -> daycareSessions(player);
                case "daycare_definitions" -> daycareDefinitions();
                case "lineage" -> lineage(player);
                default -> List.of();
            };
            int size = Math.max(1, repeater.slots().size());
            result.add(new ResolvedRepeater(repeater, entries, Math.max(1, (entries.size() + size - 1) / size)));
        }
        return result;
    }

    private List<DynamicEntry> owned(ServerPlayerEntity player) {
        return providers.pokemonProvider().map(provider -> provider.ownedPokemon(player.getUuid()).stream().map(snapshot -> pokemonEntry(player, snapshot)).toList()).orElse(List.of());
    }

    private List<DynamicEntry> partners(ServerPlayerEntity player) {
        var provider = providers.pokemonProvider().orElse(null); if (provider == null) return List.of();
        return relationships.partners(player.getUuid()).stream().map(state -> provider.findOwned(player.getUuid(), state.key().pokemonId()).map(snapshot -> pokemonEntry(player, snapshot)).orElse(null)).filter(java.util.Objects::nonNull).toList();
    }

    private DynamicEntry pokemonEntry(ServerPlayerEntity player, PokemonProvider.PokemonSnapshot snapshot) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("pokemon_uuid", snapshot.pokemonId()); values.put("pokemon", snapshot.speciesId()); values.put("form", snapshot.formId()); values.put("level", snapshot.level());
        addTrackPlaceholders(values, player, snapshot.pokemonId(), "bond"); addTrackPlaceholders(values, player, snapshot.pokemonId(), "romance");
        values.put("partner", relationships.existing(player.getUuid(), snapshot.pokemonId()).map(value -> value.partner()).orElse(false));
        return new DynamicEntry(snapshot.pokemonId(), values, new PokemonVisual(snapshot.speciesId(), snapshot.aspects()));
    }

    private List<DynamicEntry> daycareDefinitions() {
        return daycare.definitions().stream().map(definition -> {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("definition", definition.id()); values.put("mode", definition.mode()); values.put("participants_required", definition.participantRoles().size());
            values.put("duration_hours", Math.max(1L, definition.durationMillis() / 3_600_000L)); values.put("cost", definition.cost());
            return new DynamicEntry(null, values, null);
        }).toList();
    }

    private List<DynamicEntry> daycareSessions(ServerPlayerEntity player) {
        return daycare.sessions(player.getUuid()).stream().sorted(java.util.Comparator.comparingLong(value -> value.startedAtMillis())).map(session -> {
            Map<String, Object> values = new LinkedHashMap<>(); values.put("session", session.sessionId()); values.put("status", session.status()); values.put("definition", session.definitionId()); values.put("participants", session.participantPokemonIds().size()); values.put("complete_at", session.completeAtMillis()); values.put("produced_pokemon", session.producedPokemonId() == null ? "" : session.producedPokemonId());
            return new DynamicEntry(session.producedPokemonId(), values, null);
        }).toList();
    }

    private List<DynamicEntry> lineage(ServerPlayerEntity player) {
        return lineage.byOwner(player.getUuid()).stream().sorted(java.util.Comparator.comparingInt(value -> value.generation())).map(record -> {
            Map<String, Object> values = new LinkedHashMap<>(); values.put("pokemon_uuid", record.pokemonId()); values.put("generation", record.generation()); values.put("parents", record.parentPokemonIds().size()); values.put("profile", record.inheritanceProfile()); values.put("born_at", record.bornAtMillis());
            PokemonVisual visual = providers.pokemonProvider().flatMap(provider -> provider.findOwned(player.getUuid(), record.pokemonId())).map(snapshot -> new PokemonVisual(snapshot.speciesId(), snapshot.aspects())).orElse(null);
            return new DynamicEntry(record.pokemonId(), values, visual);
        }).toList();
    }

    private boolean conditions(Map<String, String> conditions, ServerPlayerEntity player, UUID pokemonId, int page, int pages) {
        for (var condition : conditions.entrySet()) {
            String key = condition.getKey(), expected = condition.getValue();
            boolean ok;
            if (key.equals("pokemon_selected")) ok = Boolean.parseBoolean(expected) == (pokemonId != null);
            else if (key.equals("partner")) ok = pokemonId != null && relationships.existing(player.getUuid(), pokemonId).map(value -> value.partner()).orElse(false) == Boolean.parseBoolean(expected);
            else if (key.equals("has_previous_page")) ok = Boolean.parseBoolean(expected) == (page > 0);
            else if (key.equals("has_next_page")) ok = Boolean.parseBoolean(expected) == (page + 1 < pages);
            else if (key.startsWith("progression.") && key.endsWith(".min") && pokemonId != null) {
                String track = key.substring("progression.".length(), key.length() - ".min".length()); ok = relationships.progression(player.getUuid(), pokemonId, track) >= Long.parseLong(expected);
            } else if (key.startsWith("route.") && key.endsWith(".in") && pokemonId != null) {
                String route = key.substring("route.".length(), key.length() - ".in".length()); String current = relationships.currentRoute(player.getUuid(), pokemonId, route); ok = java.util.Arrays.stream(expected.split("\\|")).map(String::trim).anyMatch(current::equals);
            } else if (key.startsWith("route.") && pokemonId != null) {
                String route = key.substring("route.".length()); ok = expected.equals(relationships.currentRoute(player.getUuid(), pokemonId, route));
            } else ok = true;
            if (!ok) return false;
        }
        return true;
    }

    private Map<String, Object> placeholders(ServerPlayerEntity player, UUID pokemonId) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("player", player.getName().getString()); values.put("partner_count", relationships.partners(player.getUuid()).size()); values.put("partner_capacity", relationships.capacity(player.getUuid()));
        if (pokemonId != null) {
            values.put("pokemon_uuid", pokemonId); addTrackPlaceholders(values, player, pokemonId, "bond"); addTrackPlaceholders(values, player, pokemonId, "romance"); values.put("partner", relationships.state(player.getUuid(), pokemonId).partner());
            try { values.put("romance_route", relationships.currentRoute(player.getUuid(), pokemonId, "romance")); } catch (RuntimeException ignored) { values.put("romance_route", ""); }
            providers.pokemonProvider().flatMap(provider -> provider.findOwned(player.getUuid(), pokemonId)).ifPresent(snapshot -> { values.put("pokemon", snapshot.speciesId()); values.put("level", snapshot.level()); values.put("form", snapshot.formId()); });
        }
        return values;
    }

    private void addTrackPlaceholders(Map<String, Object> values, ServerPlayerEntity player, UUID pokemonId, String trackId) {
        long current = relationships.progression(player.getUuid(), pokemonId, trackId);
        var track = relationships.track(trackId).orElse(null);
        long minimum = track == null ? 0 : track.minimum(); long maximum = track == null ? Math.max(1, current) : track.maximum();
        long span = Math.max(1L, maximum - minimum); double fraction = Math.max(0D, Math.min(1D, (double)(current - minimum) / span)); int filled = (int)Math.round(fraction * 10D);
        String bar = "█".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, 10 - filled));
        String rankId = relationships.rank(player.getUuid(), pokemonId, trackId).orElse(""); String rankDisplay = rankId;
        if (track != null) rankDisplay = track.ranks().stream().filter(rank -> rank.id().equals(rankId)).findFirst().map(rank -> messages.text(rank.displayKey()).getString()).orElse(rankId);
        values.put(trackId, current); values.put(trackId + "_min", minimum); values.put(trackId + "_max", maximum); values.put(trackId + "_percent", Math.round(fraction * 100D)); values.put(trackId + "_bar", bar); values.put(trackId + "_rank", rankDisplay);
    }

    private void addDraftPlaceholders(ServerPlayerEntity player, Map<String, Object> values) {
        DaycareDraft draft = daycareDrafts.get(player.getUuid()); if (draft == null) { values.put("daycare_definition", ""); values.put("selected_count", 0); values.put("required_count", 0); values.put("current_role", ""); return; }
        DaycareDefinition definition = daycare.definition(draft.definitionId()).orElse(null); if (definition == null) return;
        int selected = draft.participants().size(); values.put("daycare_definition", draft.definitionId()); values.put("selected_count", selected); values.put("required_count", definition.participantRoles().size()); values.put("current_role", selected < definition.participantRoles().size() ? definition.participantRoles().get(selected) : "");
    }

    private static ScreenHandlerType<?> screen(String id) {
        return switch (id) { case "generic_9x1" -> ScreenHandlerType.GENERIC_9X1; case "generic_9x2" -> ScreenHandlerType.GENERIC_9X2; case "generic_9x3" -> ScreenHandlerType.GENERIC_9X3; case "generic_9x4" -> ScreenHandlerType.GENERIC_9X4; case "generic_9x5" -> ScreenHandlerType.GENERIC_9X5; case "hopper" -> ScreenHandlerType.HOPPER; case "generic_3x3" -> ScreenHandlerType.GENERIC_3X3; default -> ScreenHandlerType.GENERIC_9X6; };
    }

    private record PokemonVisual(String speciesId, Set<String> aspects) { private PokemonVisual { aspects = Set.copyOf(aspects); } }
    private record DynamicEntry(UUID pokemonId, Map<String, Object> placeholders, PokemonVisual pokemonVisual) { }
    private record ResolvedRepeater(GuiDefinitionService.RepeaterDefinition definition, List<DynamicEntry> entries, int pageCount) { }
    private record DaycareDraft(String definitionId, List<UUID> participants) { }
}
