package vn.svframe.svrelationships.fabric.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svrelationships.fabric.config.ConfigService;
import vn.svframe.svrelationships.fabric.config.GameplayDefinitionService;
import vn.svframe.svrelationships.fabric.household.HouseholdService;
import vn.svframe.svrelationships.fabric.localization.MessageService;
import vn.svframe.svrelationships.fabric.runtime.RuntimeCoordinator;
import vn.svframe.svrelationships.integration.IntegrationRegistry;
import vn.svframe.svrelationships.integration.ProviderHub;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class SVRelationshipCommands {
    private SVRelationshipCommands() {}

    public static void register(ConfigService config, GameplayDefinitionService gameplay, MessageService messages,
                                HouseholdService households, IntegrationRegistry integrations, ProviderHub providers,
                                RuntimeCoordinator runtime) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            PokemonReferenceResolver references = new PokemonReferenceResolver(runtime.relationships());
            LiteralArgumentBuilder<ServerCommandSource> root = literal("svrel");
            root.executes(c -> openMain(c.getSource(), runtime, messages));
            root.then(literal("open").executes(c -> openMain(c.getSource(), runtime, messages)));
            root.then(literal("status").executes(c -> status(c.getSource(), integrations, providers, messages)));
            root.then(playerPokemonGui(references, runtime, messages));
            root.then(literal("partners").executes(c -> partnerSummary(c.getSource(), runtime, messages)));
            root.then(playerInteraction(gameplay, references, runtime, messages));
            root.then(playerGift(gameplay, references, runtime, messages));
            root.then(playerRomance(references, runtime, messages));
            root.then(playerReward(gameplay, references, runtime, messages));
            root.then(playerDaycare(gameplay, references, runtime, messages));
            root.then(playerLineage(references, runtime, messages));
            root.then(integrationCommand(config, messages, integrations));
            root.then(literal("config").requires(s -> canAdmin(s, config, providers))
                    .then(literal("reload").executes(c -> reload(c.getSource(), runtime, messages))));
            root.then(adminRoot(config, gameplay, messages, providers, runtime, references));
            dispatcher.register(root);
            dispatcher.register(householdRoot(households, messages));
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> playerPokemonGui(PokemonReferenceResolver refs, RuntimeCoordinator rt, MessageService msg) {
        return literal("pokemon").then(pokemonArgument("pokemon", refs).executes(c ->
                openPokemon(c.getSource(), refs, rt, msg, StringArgumentType.getString(c, "pokemon"))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> playerInteraction(GameplayDefinitionService gameplay, PokemonReferenceResolver refs, RuntimeCoordinator rt, MessageService msg) {
        var id = argument("interaction", StringArgumentType.word())
                .suggests((c,b) -> { gameplay.snapshot().interactions().keySet().stream().sorted().forEach(b::suggest); return b.buildFuture(); })
                .executes(c -> interaction(c.getSource(), refs, rt, msg, StringArgumentType.getString(c,"pokemon"), StringArgumentType.getString(c,"interaction")));
        return literal("interact").then(pokemonArgument("pokemon", refs).then(id));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> playerGift(GameplayDefinitionService gameplay, PokemonReferenceResolver refs, RuntimeCoordinator rt, MessageService msg) {
        var id = argument("gift", StringArgumentType.word())
                .suggests((c,b) -> { gameplay.snapshot().gifts().keySet().stream().sorted().forEach(b::suggest); return b.buildFuture(); })
                .executes(c -> gift(c.getSource(), refs, rt, msg, StringArgumentType.getString(c,"pokemon"), StringArgumentType.getString(c,"gift")));
        return literal("gift").then(pokemonArgument("pokemon", refs).then(id));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> playerRomance(PokemonReferenceResolver refs, RuntimeCoordinator rt, MessageService msg) {
        var milestone = argument("milestone", StringArgumentType.word())
                .suggests((c,b) -> { rt.rules().snapshot().partnership().milestones().keySet().stream().sorted().forEach(b::suggest); return b.buildFuture(); })
                .executes(c -> advancePartnership(c.getSource(), refs, rt, msg, StringArgumentType.getString(c,"pokemon"), StringArgumentType.getString(c,"milestone")));
        return literal("romance").then(pokemonArgument("pokemon", refs).then(milestone));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> playerReward(GameplayDefinitionService gameplay, PokemonReferenceResolver refs, RuntimeCoordinator rt, MessageService msg) {
        var profile = argument("profile", StringArgumentType.word())
                .suggests((c,b) -> { gameplay.snapshot().rewardProfiles().keySet().stream().sorted().forEach(b::suggest); return b.buildFuture(); })
                .executes(c -> claimReward(c.getSource(), refs, rt, msg, StringArgumentType.getString(c,"pokemon"), StringArgumentType.getString(c,"profile")));
        return literal("reward").then(pokemonArgument("pokemon", refs).then(profile));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> playerDaycare(GameplayDefinitionService gameplay, PokemonReferenceResolver refs, RuntimeCoordinator rt, MessageService msg) {
        var root = literal("daycare");
        root.then(literal("list").executes(c -> daycareList(c.getSource(), rt, msg)));
        var definition = argument("definition", StringArgumentType.word())
                .suggests((c,b) -> { gameplay.snapshot().daycareDefinitions().keySet().stream().sorted().forEach(b::suggest); return b.buildFuture(); });
        var first = pokemonArgument("pokemon1", refs).executes(c -> daycareStart(c.getSource(), refs, rt, msg,
                StringArgumentType.getString(c,"definition"), List.of(StringArgumentType.getString(c,"pokemon1"))));
        first.then(pokemonArgument("pokemon2", refs).executes(c -> daycareStart(c.getSource(), refs, rt, msg,
                StringArgumentType.getString(c,"definition"), List.of(StringArgumentType.getString(c,"pokemon1"), StringArgumentType.getString(c,"pokemon2")))));
        definition.then(first);
        root.then(literal("start").then(definition));
        return root;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> playerLineage(PokemonReferenceResolver refs, RuntimeCoordinator rt, MessageService msg) {
        return literal("lineage").then(pokemonArgument("pokemon", refs).executes(c ->
                lineage(c.getSource(), refs, rt, msg, StringArgumentType.getString(c,"pokemon"))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> integrationCommand(ConfigService config, MessageService msg, IntegrationRegistry integrations) {
        var id = argument("id", StringArgumentType.word())
                .suggests((c,b) -> { integrations.snapshot().forEach(v -> b.suggest(v.id())); return b.buildFuture(); })
                .executes(c -> integrationInspect(c.getSource(), config, msg, integrations, StringArgumentType.getString(c,"id")));
        return literal("integration").then(literal("inspect").then(id));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> adminRoot(ConfigService config, GameplayDefinitionService gameplay,
                                                                          MessageService msg, ProviderHub providers,
                                                                          RuntimeCoordinator rt, PokemonReferenceResolver refs) {
        var admin = literal("admin").requires(s -> canAdmin(s, config, providers));
        admin.then(adminProgression(gameplay, rt, msg, refs));
        admin.then(adminRoute(gameplay, rt, msg, refs));
        admin.then(adminPartner(rt, msg, refs));
        admin.then(adminPersonality(gameplay, rt, msg, refs));
        admin.then(adminDaycare(rt, msg));
        admin.then(literal("capacity").then(playerArgument("player").executes(c ->
                adminCapacity(c.getSource(), rt, msg, StringArgumentType.getString(c,"player")))));
        admin.then(adminDebug(rt, msg, refs));
        return admin;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> adminProgression(GameplayDefinitionService gameplay, RuntimeCoordinator rt,
                                                                                 MessageService msg, PokemonReferenceResolver refs) {
        var root = literal("progression");
        var p = playerArgument("player");
        var pokemon = adminPokemonArgument("pokemon", "player", refs);
        pokemon.then(trackArgument(gameplay).executes(c -> {
            ServerPlayerEntity target = target(c.getSource(), StringArgumentType.getString(c,"player"));
            if (target == null) return playerMissing(c.getSource(), msg);
            UUID id = resolve(target, refs, StringArgumentType.getString(c,"pokemon"));
            if (id == null) return pokemonMissing(c.getSource(), msg);
            String track = StringArgumentType.getString(c,"track");
            long value = rt.relationships().progression(target.getUuid(), id, track);
            c.getSource().sendFeedback(() -> msg.text("command.admin.progression.value", Map.of("player",target.getGameProfile().getName(),"pokemon",id,"track",track,"value",value)), false);
            return 1;
        }));
        p.then(pokemon);
        root.then(literal("get").then(p));
        root.then(progressionMutation("set", gameplay, rt, msg, refs, false));
        root.then(progressionMutation("add", gameplay, rt, msg, refs, true));
        return root;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> progressionMutation(String name, GameplayDefinitionService gameplay,
                                                                                     RuntimeCoordinator rt, MessageService msg,
                                                                                     PokemonReferenceResolver refs, boolean add) {
        var p = playerArgument("player");
        var pokemon = adminPokemonArgument("pokemon", "player", refs);
        var track = trackArgument(gameplay);
        track.then(argument("value", LongArgumentType.longArg()).executes(c -> mutateProgression(c.getSource(), rt, msg, refs,
                StringArgumentType.getString(c,"player"), StringArgumentType.getString(c,"pokemon"), StringArgumentType.getString(c,"track"),
                LongArgumentType.getLong(c,"value"), add)));
        pokemon.then(track); p.then(pokemon);
        return literal(name).then(p);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> adminRoute(GameplayDefinitionService gameplay, RuntimeCoordinator rt,
                                                                           MessageService msg, PokemonReferenceResolver refs) {
        var root = literal("route");
        var getP = playerArgument("player");
        var getPokemon = adminPokemonArgument("pokemon", "player", refs);
        getPokemon.then(routeArgument(gameplay).executes(c -> {
            ServerPlayerEntity target = target(c.getSource(), StringArgumentType.getString(c,"player"));
            if (target == null) return playerMissing(c.getSource(), msg);
            UUID id = resolve(target, refs, StringArgumentType.getString(c,"pokemon"));
            if (id == null) return pokemonMissing(c.getSource(), msg);
            String route = StringArgumentType.getString(c,"route");
            String state = rt.relationships().currentRoute(target.getUuid(), id, route);
            c.getSource().sendFeedback(() -> msg.text("command.admin.route.value", Map.of("player",target.getGameProfile().getName(),"pokemon",id,"route",route,"state",state)), false);
            return 1;
        }));
        getP.then(getPokemon); root.then(literal("get").then(getP));

        var forceP = playerArgument("player");
        var forcePokemon = adminPokemonArgument("pokemon", "player", refs);
        var forceRoute = routeArgument(gameplay);
        forceRoute.then(argument("state", StringArgumentType.word())
                .suggests((c,b) -> { var r = gameplay.snapshot().routes().get(StringArgumentType.getString(c,"route")); if (r != null) r.states().keySet().stream().sorted().forEach(b::suggest); return b.buildFuture(); })
                .executes(c -> {
                    ServerPlayerEntity target = target(c.getSource(), StringArgumentType.getString(c,"player"));
                    if (target == null) return playerMissing(c.getSource(), msg);
                    UUID id = resolve(target, refs, StringArgumentType.getString(c,"pokemon"));
                    if (id == null) return pokemonMissing(c.getSource(), msg);
                    String route = StringArgumentType.getString(c,"route"); String state = StringArgumentType.getString(c,"state");
                    rt.relationships().forceRoute(target.getUuid(), id, route, state);
                    c.getSource().sendFeedback(() -> msg.text("command.admin.route.updated", Map.of("player",target.getGameProfile().getName(),"pokemon",id,"route",route,"state",state)), false);
                    return 1;
                }));
        forcePokemon.then(forceRoute); forceP.then(forcePokemon); root.then(literal("force").then(forceP));
        return root;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> adminPartner(RuntimeCoordinator rt, MessageService msg, PokemonReferenceResolver refs) {
        var root = literal("partner");
        root.then(partnerMutation("add", true, rt, msg, refs));
        root.then(partnerMutation("remove", false, rt, msg, refs));
        root.then(literal("list").then(playerArgument("player").executes(c -> adminCapacity(c.getSource(), rt, msg, StringArgumentType.getString(c,"player")))));
        return root;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> partnerMutation(String name, boolean add, RuntimeCoordinator rt, MessageService msg, PokemonReferenceResolver refs) {
        var p = playerArgument("player");
        p.then(adminPokemonArgument("pokemon", "player", refs).executes(c -> adminPartnerMutate(c.getSource(), rt, msg, refs,
                StringArgumentType.getString(c,"player"), StringArgumentType.getString(c,"pokemon"), add)));
        return literal(name).then(p);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> adminPersonality(GameplayDefinitionService gameplay, RuntimeCoordinator rt, MessageService msg, PokemonReferenceResolver refs) {
        var p = playerArgument("player");
        var pokemon = adminPokemonArgument("pokemon", "player", refs);
        pokemon.then(argument("personality", StringArgumentType.word())
                .suggests((c,b) -> { gameplay.snapshot().personalities().keySet().stream().sorted().forEach(b::suggest); return b.buildFuture(); })
                .executes(c -> {
                    ServerPlayerEntity target = target(c.getSource(), StringArgumentType.getString(c,"player"));
                    if (target == null) return playerMissing(c.getSource(), msg);
                    UUID id = resolve(target, refs, StringArgumentType.getString(c,"pokemon"));
                    if (id == null) return pokemonMissing(c.getSource(), msg);
                    String personality = StringArgumentType.getString(c,"personality");
                    rt.relationships().setPersonality(target.getUuid(), id, personality);
                    c.getSource().sendFeedback(() -> msg.text("command.admin.personality.updated", Map.of("player",target.getGameProfile().getName(),"pokemon",id,"personality",personality)), false);
                    return 1;
                }));
        p.then(pokemon);
        return literal("personality").then(literal("set").then(p));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> adminDaycare(RuntimeCoordinator rt, MessageService msg) {
        var session = argument("session", StringArgumentType.word())
                .suggests((c,b) -> { rt.daycare().ifPresent(svc -> c.getSource().getServer().getPlayerManager().getPlayerList().forEach(p -> svc.sessions(p.getUuid()).stream().filter(v -> "ACTIVE".equals(v.status())).forEach(v -> b.suggest(v.sessionId().toString())))); return b.buildFuture(); })
                .executes(c -> {
                    var svc = rt.daycare(); if (svc.isEmpty()) return runtimeUnavailable(c.getSource(), msg);
                    try {
                        boolean ok = svc.get().completeNow(UUID.fromString(StringArgumentType.getString(c,"session")), System.currentTimeMillis());
                        if (ok) { c.getSource().sendFeedback(() -> msg.text("command.admin.daycare.completed"), false); return 1; }
                        c.getSource().sendError(msg.text("command.admin.daycare.not_completed")); return 0;
                    } catch (IllegalArgumentException e) { c.getSource().sendError(msg.text("command.argument.invalid")); return 0; }
                });
        return literal("daycare").then(literal("complete").then(session));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> adminDebug(RuntimeCoordinator rt, MessageService msg, PokemonReferenceResolver refs) {
        var root = literal("debug");
        var p = playerArgument("player");
        p.then(adminPokemonArgument("pokemon", "player", refs).executes(c -> debugTrace(c.getSource(), rt, msg,
                StringArgumentType.getString(c,"player"), StringArgumentType.getString(c,"pokemon"))));
        root.then(literal("trace").then(p));
        root.then(literal("metrics").executes(c -> debugMetrics(c.getSource(), rt, msg)));
        return root;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> householdRoot(HouseholdService households, MessageService msg) {
        var root = literal("household");
        root.then(literal("set").executes(c -> householdSet(c.getSource(), households, msg)));
        root.then(literal("info").executes(c -> householdInfo(c.getSource(), households, msg)));
        root.then(literal("clear").executes(c -> householdClear(c.getSource(), households, msg)));
        return root;
    }

    private static RequiredArgumentBuilder<ServerCommandSource,String> pokemonArgument(String name, PokemonReferenceResolver refs) {
        return argument(name, StringArgumentType.word()).suggests((c,b) -> { ServerPlayerEntity p = c.getSource().getPlayer(); if (p != null) addPokemonSuggestions(p, refs, b); return b.buildFuture(); });
    }
    private static RequiredArgumentBuilder<ServerCommandSource,String> adminPokemonArgument(String name, String playerArg, PokemonReferenceResolver refs) {
        return argument(name, StringArgumentType.word()).suggests((c,b) -> { ServerPlayerEntity p = target(c.getSource(), StringArgumentType.getString(c,playerArg)); if (p != null) addPokemonSuggestions(p,refs,b); return b.buildFuture(); });
    }
    private static void addPokemonSuggestions(ServerPlayerEntity p, PokemonReferenceResolver refs, SuggestionsBuilder b) { refs.suggestions(p).forEach(s -> b.suggest(s.value(), Text.literal(s.label()))); }
    private static RequiredArgumentBuilder<ServerCommandSource,String> playerArgument(String name) { return argument(name,StringArgumentType.word()).suggests((c,b) -> { c.getSource().getServer().getPlayerManager().getPlayerList().forEach(p -> b.suggest(p.getGameProfile().getName())); return b.buildFuture(); }); }
    private static RequiredArgumentBuilder<ServerCommandSource,String> trackArgument(GameplayDefinitionService gameplay) { return argument("track",StringArgumentType.word()).suggests((c,b) -> { gameplay.snapshot().progressionTracks().keySet().stream().sorted().forEach(b::suggest); return b.buildFuture(); }); }
    private static RequiredArgumentBuilder<ServerCommandSource,String> routeArgument(GameplayDefinitionService gameplay) { return argument("route",StringArgumentType.word()).suggests((c,b) -> { gameplay.snapshot().routes().keySet().stream().sorted().forEach(b::suggest); return b.buildFuture(); }); }

    private static int openMain(ServerCommandSource s, RuntimeCoordinator rt, MessageService m) { ServerPlayerEntity p=s.getPlayer(); if(p==null)return playerMissing(s,m); if(rt.guis().isEmpty())return runtimeUnavailable(s,m); return rt.guis().get().openMain(p)?1:0; }
    private static int openPokemon(ServerCommandSource s, PokemonReferenceResolver refs, RuntimeCoordinator rt, MessageService m, String ref) { ServerPlayerEntity p=s.getPlayer(); if(p==null)return playerMissing(s,m); UUID id=resolve(p,refs,ref); if(id==null)return pokemonMissing(s,m); if(rt.guis().isEmpty())return runtimeUnavailable(s,m); return rt.guis().get().openRelationship(p,id)?1:0; }
    private static int partnerSummary(ServerCommandSource s, RuntimeCoordinator rt, MessageService m) { ServerPlayerEntity p=s.getPlayer(); if(p==null)return playerMissing(s,m); int current=rt.relationships().partners(p.getUuid()).size(), capacity=rt.relationships().capacity(p.getUuid()); s.sendFeedback(() -> m.text("command.partners.summary",Map.of("current",current,"capacity",capacity,"remaining",Math.max(0,capacity-current))),false); return 1; }
    private static int interaction(ServerCommandSource s,PokemonReferenceResolver refs,RuntimeCoordinator rt,MessageService m,String ref,String id){ServerPlayerEntity p=s.getPlayer();if(p==null)return playerMissing(s,m);UUID pokemon=resolve(p,refs,ref);if(pokemon==null)return pokemonMissing(s,m);return sendEnumResult(s,m,"command.interaction.",rt.interactions().interact(p.getUuid(),pokemon,id,System.currentTimeMillis()).name());}
    private static int gift(ServerCommandSource s,PokemonReferenceResolver refs,RuntimeCoordinator rt,MessageService m,String ref,String id){ServerPlayerEntity p=s.getPlayer();if(p==null)return playerMissing(s,m);UUID pokemon=resolve(p,refs,ref);if(pokemon==null)return pokemonMissing(s,m);return sendEnumResult(s,m,"command.gift.",rt.interactions().gift(p,pokemon,id,System.currentTimeMillis()).name());}
    private static int advancePartnership(ServerCommandSource s,PokemonReferenceResolver refs,RuntimeCoordinator rt,MessageService m,String ref,String milestone){ServerPlayerEntity p=s.getPlayer();if(p==null)return playerMissing(s,m);UUID pokemon=resolve(p,refs,ref);if(pokemon==null)return pokemonMissing(s,m);return sendEnumResult(s,m,"command.partnership.",rt.partnerships().advance(p.getUuid(),pokemon,milestone).name());}
    private static int claimReward(ServerCommandSource s,PokemonReferenceResolver refs,RuntimeCoordinator rt,MessageService m,String ref,String profile){ServerPlayerEntity p=s.getPlayer();if(p==null)return playerMissing(s,m);UUID pokemon=resolve(p,refs,ref);if(pokemon==null)return pokemonMissing(s,m);return sendEnumResult(s,m,"command.reward.",rt.rewards().claim(p,pokemon,profile,System.currentTimeMillis()).name());}
    private static int daycareStart(ServerCommandSource s,PokemonReferenceResolver refs,RuntimeCoordinator rt,MessageService m,String definition,List<String> references){ServerPlayerEntity p=s.getPlayer();if(p==null)return playerMissing(s,m);if(rt.daycare().isEmpty())return runtimeUnavailable(s,m);List<UUID> ids=new ArrayList<>();for(String ref:references){UUID id=resolve(p,refs,ref);if(id==null)return pokemonMissing(s,m);ids.add(id);}return sendEnumResult(s,m,"command.daycare.",rt.daycare().get().start(p.getUuid(),definition,ids,System.currentTimeMillis()).name());}
    private static int daycareList(ServerCommandSource s,RuntimeCoordinator rt,MessageService m){ServerPlayerEntity p=s.getPlayer();if(p==null)return playerMissing(s,m);if(rt.daycare().isEmpty())return runtimeUnavailable(s,m);var sessions=rt.daycare().get().sessions(p.getUuid());long active=sessions.stream().filter(v->"ACTIVE".equals(v.status())).count();s.sendFeedback(() -> m.text("command.daycare.summary",Map.of("total",sessions.size(),"active",active)),false);return 1;}
    private static int lineage(ServerCommandSource s,PokemonReferenceResolver refs,RuntimeCoordinator rt,MessageService m,String ref){ServerPlayerEntity p=s.getPlayer();if(p==null)return playerMissing(s,m);UUID id=resolve(p,refs,ref);if(id==null)return pokemonMissing(s,m);var r=rt.lineage().get(id);if(r.isEmpty()){s.sendError(m.text("command.lineage.none"));return 0;}var v=r.get();s.sendFeedback(() -> m.text("command.lineage.value",Map.of("pokemon",id,"generation",v.generation(),"parents",v.parentPokemonIds().size(),"profile",v.inheritanceProfile())),false);return 1;}
    private static int status(ServerCommandSource s,IntegrationRegistry integrations,ProviderHub providers,MessageService m){long active=integrations.snapshot().stream().filter(v->v.state().name().equals("ACTIVE")).count();int available=providers.economyIds().size()+(providers.permissionProvider().isPresent()?1:0)+(providers.pokemonProvider().isPresent()?1:0);s.sendFeedback(() -> m.text("command.status.summary",Map.of("active",active,"available",available)),false);return 1;}
    private static int integrationInspect(ServerCommandSource s,ConfigService config,MessageService m,IntegrationRegistry integrations,String id){var d=integrations.get(id);if(d.isEmpty()){s.sendError(m.text("command.integration.unknown",Map.of("id",id)));return 0;}var v=d.get();String state=config.snapshot().messages().getOrDefault(v.detailKey(),v.state().name());s.sendFeedback(() -> m.text("command.integration.info",Map.of("id",v.id(),"state",state)),false);return 1;}
    private static int reload(ServerCommandSource s,RuntimeCoordinator rt,MessageService m){var r=rt.reloadAll();if(!r.success()){s.sendError(m.text("config.reload.failure",Map.of("detail",r.detail())));return 0;}s.sendFeedback(() -> m.text("config.reload.success"),false);return 1;}
    private static int mutateProgression(ServerCommandSource s,RuntimeCoordinator rt,MessageService m,PokemonReferenceResolver refs,String playerName,String ref,String track,long value,boolean add){ServerPlayerEntity p=target(s,playerName);if(p==null)return playerMissing(s,m);UUID id=resolve(p,refs,ref);if(id==null)return pokemonMissing(s,m);long result=add?rt.relationships().addProgression(p.getUuid(),id,track,value):rt.relationships().setProgression(p.getUuid(),id,track,value);s.sendFeedback(() -> m.text("command.admin.progression.updated",Map.of("player",p.getGameProfile().getName(),"pokemon",id,"track",track,"value",result)),false);return 1;}
    private static int adminPartnerMutate(ServerCommandSource s,RuntimeCoordinator rt,MessageService m,PokemonReferenceResolver refs,String playerName,String ref,boolean add){ServerPlayerEntity p=target(s,playerName);if(p==null)return playerMissing(s,m);UUID id=resolve(p,refs,ref);if(id==null)return pokemonMissing(s,m);boolean ok=rt.relationships().setPartner(p.getUuid(),id,add,true);if(!ok)return sendEnumResult(s,m,"command.partner.","FAILED");s.sendFeedback(() -> m.text(add?"command.partner.added":"command.partner.removed",Map.of("player",p.getGameProfile().getName(),"pokemon",id)),false);return 1;}
    private static int adminCapacity(ServerCommandSource s,RuntimeCoordinator rt,MessageService m,String playerName){ServerPlayerEntity p=target(s,playerName);if(p==null)return playerMissing(s,m);int current=rt.relationships().partners(p.getUuid()).size(),capacity=rt.relationships().capacity(p.getUuid());s.sendFeedback(() -> m.text("command.admin.capacity.value",Map.of("player",p.getGameProfile().getName(),"current",current,"capacity",capacity,"remaining",Math.max(0,capacity-current),"over",Math.max(0,current-capacity))),false);return 1;}
    private static int debugTrace(ServerCommandSource s,RuntimeCoordinator rt,MessageService m,String playerName,String ref){ServerPlayerEntity p=target(s,playerName);if(p==null)return playerMissing(s,m);PokemonReferenceResolver refs=new PokemonReferenceResolver(rt.relationships());UUID id=resolve(p,refs,ref);if(id==null)return pokemonMissing(s,m);var state=rt.relationships().state(p.getUuid(),id);s.sendFeedback(() -> m.text("command.debug.trace",Map.of("player",p.getGameProfile().getName(),"pokemon",id,"bond",state.progression("bond"),"romance",state.progression("romance"),"partner",state.partner(),"capacity",rt.relationships().capacity(p.getUuid()),"route",rt.relationships().currentRoute(p.getUuid(),id,rt.rules().snapshot().partnership().routeId()),"personality",state.personalityId()==null?"":state.personalityId())),false);return 1;}
    private static int debugMetrics(ServerCommandSource s,RuntimeCoordinator rt,MessageService m){long ticks=rt.metrics().counter("runtime.tick_count"),nanos=rt.metrics().counter("runtime.tick_nanos"),average=ticks==0?0:(nanos/ticks)/1000L;s.sendFeedback(() -> m.text("command.debug.metrics",Map.of("ticks",ticks,"average_us",average,"relationships",rt.metrics().gauge("relationship.total"),"materialized",rt.metrics().gauge("household.materialized"),"materialize_events",rt.metrics().counter("household.materialize"),"recall_events",rt.metrics().counter("household.recall"))),false);return 1;}
    private static int householdSet(ServerCommandSource s,HouseholdService h,MessageService m){ServerPlayerEntity p=s.getPlayer();if(p==null)return playerMissing(s,m);var state=h.setAtPlayer(p);s.sendFeedback(() -> m.text("household.set.success",Map.of("dimension",state.anchor().dimensionId(),"x",state.anchor().x(),"y",state.anchor().y(),"z",state.anchor().z(),"profile",state.profileId())),false);return 1;}
    private static int householdInfo(ServerCommandSource s,HouseholdService h,MessageService m){ServerPlayerEntity p=s.getPlayer();if(p==null)return playerMissing(s,m);var state=h.get(p.getUuid());if(state.isEmpty()){s.sendError(m.text("household.info.none"));return 0;}var v=state.get();s.sendFeedback(() -> m.text("household.info.value",Map.of("dimension",v.anchor().dimensionId(),"x",v.anchor().x(),"y",v.anchor().y(),"z",v.anchor().z(),"profile",v.profileId(),"radius",h.activeRadius(v),"deactivation_radius",h.deactivationRadius(v))),false);return 1;}
    private static int householdClear(ServerCommandSource s,HouseholdService h,MessageService m){ServerPlayerEntity p=s.getPlayer();if(p==null)return playerMissing(s,m);h.clear(p.getUuid());s.sendFeedback(() -> m.text("household.clear.success"),false);return 1;}
    private static int sendEnumResult(ServerCommandSource s,MessageService m,String prefix,String value){String key=prefix+value.toLowerCase(Locale.ROOT);if("SUCCESS".equals(value)||"DELIVERED".equals(value)||"STARTED".equals(value)){s.sendFeedback(() -> m.text(key),false);return 1;}s.sendError(m.text(key));return 0;}
    private static ServerPlayerEntity target(ServerCommandSource s,String name){return s.getServer().getPlayerManager().getPlayer(name);}
    private static UUID resolve(ServerPlayerEntity p,PokemonReferenceResolver refs,String ref){return refs.resolve(p,ref).orElse(null);}
    private static int playerMissing(ServerCommandSource s,MessageService m){s.sendError(m.text("command.player.required"));return 0;}
    private static int pokemonMissing(ServerCommandSource s,MessageService m){s.sendError(m.text("command.pokemon.unknown"));return 0;}
    private static int runtimeUnavailable(ServerCommandSource s,MessageService m){s.sendError(m.text("command.runtime.unavailable"));return 0;}
    private static boolean canAdmin(ServerCommandSource s,ConfigService config,ProviderHub providers){if(s.getEntity()==null)return true;ServerPlayerEntity p=s.getPlayer();if(p!=null&&providers.permissionProvider().isPresent())return providers.permissionProvider().get().hasPermission(p.getUuid(),config.snapshot().adminPermission());return s.hasPermissionLevel(2);}
}
