package vn.svarcade.tcg.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svarcade.tcg.data.Catalog;
import vn.svarcade.tcg.economy.CardStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Brigadier command tree. All user-facing output is delegated to MessageService. */
public final class CardWorldsCommands {
    private static final Map<String,String> FINISHES = new LinkedHashMap<>();
    static {
        FINISHES.put("normal", "Normal");
        FINISHES.put("holo", "Holo");
        FINISHES.put("reverse_holo", "Reverse Holo");
        FINISHES.put("full_art", "Full Art");
        FINISHES.put("secret", "Secret");
    }

    private CardWorldsCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, TcgMod mod) {
        dispatcher.register(root("cardworlds", mod));
        dispatcher.register(root("tcg", mod));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> root(String name, TcgMod mod) {
        return literal(name)
            .executes(ctx -> open(ctx, mod))
            .then(literal("open").executes(ctx -> open(ctx, mod)))
            .then(literal("starter").executes(ctx -> starter(ctx, mod)))
            .then(literal("reload")
                .requires(source -> source.hasPermissionLevel(3))
                .executes(ctx -> reload(ctx, mod)))
            .then(literal("spectate")
                .then(literal("list").executes(ctx -> spectateList(ctx, mod)))
                .then(literal("leave").executes(ctx -> spectateLeave(ctx, mod)))
                .then(argument("player", EntityArgumentType.player()).executes(ctx -> spectate(ctx, mod))))
            .then(literal("realm")
                .requires(source -> source.hasPermissionLevel(3))
                .then(literal("status").executes(ctx -> realmStatus(ctx, mod)))
                .then(literal("return").executes(ctx -> realmReturn(ctx, mod))))
            .then(literal("qa")
                .requires(source -> source.hasPermissionLevel(3))
                .then(literal("spectator")
                    .then(literal("enter").executes(ctx -> qaSpectatorEnter(ctx, mod)))
                    .then(literal("leave").executes(ctx -> qaSpectatorLeave(ctx, mod))))
                .then(literal("spelltrap")
                    .then(literal("prepare").executes(ctx -> qaSpellTrap(ctx, mod, "prepare")))
                    .then(literal("chain").executes(ctx -> qaSpellTrap(ctx, mod, "chain")))
                    .then(literal("resolve").executes(ctx -> qaSpellTrap(ctx, mod, "resolve"))))
                .then(literal("creation")
                    .then(literal("prepare").executes(ctx -> qaCreation(ctx, mod, "prepare")))
                    .then(literal("summon").executes(ctx -> qaCreation(ctx, mod, "summon")))
                    .then(literal("tick").executes(ctx -> qaCreation(ctx, mod, "tick")))))
            .then(literal("qa_position")
                .requires(source -> Boolean.getBoolean("cardworlds.qa") && source.hasPermissionLevel(3))
                .executes(ctx -> qaPosition(ctx, mod)))
            .then(literal("grant")
                .requires(source -> source.hasPermissionLevel(3))
                .then(literal("card")
                    .then(argument("player", EntityArgumentType.player())
                        .then(argument("card", StringArgumentType.word())
                            .suggests((ctx, builder) -> suggestCards(mod, builder))
                            .executes(ctx -> grantCard(ctx, mod, 1, "normal"))
                            .then(argument("amount", IntegerArgumentType.integer(1, 1000))
                                .executes(ctx -> grantCard(ctx, mod, IntegerArgumentType.getInteger(ctx, "amount"), "normal"))
                                .then(argument("finish", StringArgumentType.word())
                                    .suggests((ctx, builder) -> suggestFinishes(builder))
                                    .executes(ctx -> grantCard(ctx, mod, IntegerArgumentType.getInteger(ctx, "amount"),
                                        StringArgumentType.getString(ctx, "finish"))))))))
                .then(literal("coins")
                    .then(argument("player", EntityArgumentType.player())
                        .then(argument("amount", LongArgumentType.longArg(1, 1_000_000_000L))
                            .executes(ctx -> grantCoins(ctx, mod)))))
                .then(literal("deck")
                    .then(argument("player", EntityArgumentType.player())
                        .then(argument("template", StringArgumentType.word())
                            .suggests((ctx, builder) -> suggestTemplates(mod, builder))
                            .executes(ctx -> grantDeck(ctx, mod, null))
                            .then(argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> grantDeck(ctx, mod, StringArgumentType.getString(ctx, "name")))))))
                .then(literal("starter")
                    .then(argument("player", EntityArgumentType.player())
                        .then(argument("template", StringArgumentType.word())
                            .suggests((ctx, builder) -> suggestTemplates(mod, builder))
                            .executes(ctx -> grantStarter(ctx, mod))))));
    }

    private static int open(CommandContext<ServerCommandSource> ctx, TcgMod mod) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            messages(mod, source).send(source, "command.player_only");
            return 0;
        }
        mod.commandOpen(player);
        return 1;
    }

    private static int starter(CommandContext<ServerCommandSource> ctx, TcgMod mod) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            messages(mod, source).send(source, "command.player_only");
            return 0;
        }
        if (!ready(mod, source)) return 0;
        try {
            mod.commandStore().createProfile(player.getUuidAsString(), "crossroads");
            mod.commandOpen(player);
            messages(mod, source).send(source, "command.starter.success",
                Map.of("template", "crossroads"));
            return 1;
        } catch (Exception ex) {
            messages(mod, source).send(source, "command.failed",
                Map.of("error", safeError(ex)));
            return 0;
        }
    }

    private static int reload(CommandContext<ServerCommandSource> ctx, TcgMod mod) {
        ServerCommandSource source = ctx.getSource();
        if (mod.commandHasActiveDuels()) {
            messages(mod, source).send(source, "command.reload.blocked");
            return 0;
        }
        try {
            mod.commandReload(source.getServer());
            messages(mod, source).send(source, "command.reload.success");
            return 1;
        } catch (Exception ex) {
            messages(mod, source).send(source, "command.reload.failed",
                Map.of("error", safeError(ex)));
            return 0;
        }
    }

    private static int grantCard(CommandContext<ServerCommandSource> ctx, TcgMod mod, int amount, String finishId) {
        ServerCommandSource source = ctx.getSource();
        if (!ready(mod, source)) return 0;
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
            String query = StringArgumentType.getString(ctx, "card");
            Catalog.Card card = resolveCard(mod.commandCatalog(), query);
            if (card == null) {
                messages(mod, source).send(source, "command.grant.card.invalid",
                    Map.of("card", query));
                return 0;
            }
            String finish = FINISHES.get(finishId.toLowerCase(Locale.ROOT));
            if (finish == null) {
                messages(mod, source).send(source, "command.grant.finish.invalid",
                    Map.of("finish", finishId));
                return 0;
            }
            if (!mod.commandStore().hasProfile(target.getUuidAsString())) {
                messages(mod, source).send(source, "command.profile_missing",
                    Map.of("target", target.getName().getString()));
                return 0;
            }

            mod.commandStore().adminGrantCard(target.getUuidAsString(), card.id(), amount, finish);
            Map<String,String> vars = Map.of(
                "target", target.getName().getString(),
                "card", card.name(),
                "card_id", card.id(),
                "amount", Integer.toString(amount),
                "finish", finish
            );
            messages(mod, source).send(source, "command.grant.card.success", vars);
            if (source.getPlayer() == null || !source.getPlayer().getUuid().equals(target.getUuid())) {
                messages(mod, source).send(target.getCommandSource(), "command.grant.card.received", vars);
            }
            mod.commandOpen(target);
            return 1;
        } catch (Exception ex) {
            messages(mod, source).send(source, "command.failed",
                Map.of("error", safeError(ex)));
            return 0;
        }
    }

    private static int grantCoins(CommandContext<ServerCommandSource> ctx, TcgMod mod) {
        ServerCommandSource source = ctx.getSource();
        if (!ready(mod, source)) return 0;
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
            long amount = LongArgumentType.getLong(ctx, "amount");
            if (!mod.commandStore().hasProfile(target.getUuidAsString())) {
                messages(mod, source).send(source, "command.profile_missing",
                    Map.of("target", target.getName().getString()));
                return 0;
            }
            mod.commandStore().adminGrantCoins(target.getUuidAsString(), amount);
            Map<String,String> vars = Map.of(
                "target", target.getName().getString(),
                "amount", Long.toString(amount)
            );
            messages(mod, source).send(source, "command.grant.coins.success", vars);
            if (source.getPlayer() == null || !source.getPlayer().getUuid().equals(target.getUuid())) {
                messages(mod, source).send(target.getCommandSource(), "command.grant.coins.received", vars);
            }
            mod.commandOpen(target);
            return 1;
        } catch (Exception ex) {
            messages(mod, source).send(source, "command.failed",
                Map.of("error", safeError(ex)));
            return 0;
        }
    }

    private static int grantDeck(CommandContext<ServerCommandSource> ctx, TcgMod mod, String requestedName) {
        ServerCommandSource source = ctx.getSource();
        if (!ready(mod, source)) return 0;
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
            String template = StringArgumentType.getString(ctx, "template");
            if (!mod.commandCatalog().starters().containsKey(template)) {
                messages(mod, source).send(source, "command.grant.template.invalid",
                    Map.of("template", template));
                return 0;
            }
            if (!mod.commandStore().hasProfile(target.getUuidAsString())) {
                messages(mod, source).send(source, "command.profile_missing",
                    Map.of("target", target.getName().getString()));
                return 0;
            }
            String deckName = requestedName == null || requestedName.isBlank() ? template : requestedName.trim();
            mod.commandStore().adminGrantDeck(target.getUuidAsString(), template, deckName);
            Map<String,String> vars = Map.of(
                "target", target.getName().getString(),
                "template", template,
                "deck", deckName
            );
            messages(mod, source).send(source, "command.grant.deck.success", vars);
            if (source.getPlayer() == null || !source.getPlayer().getUuid().equals(target.getUuid())) {
                messages(mod, source).send(target.getCommandSource(), "command.grant.deck.received", vars);
            }
            mod.commandOpen(target);
            return 1;
        } catch (Exception ex) {
            messages(mod, source).send(source, "command.failed",
                Map.of("error", safeError(ex)));
            return 0;
        }
    }

    private static int grantStarter(CommandContext<ServerCommandSource> ctx, TcgMod mod) {
        ServerCommandSource source = ctx.getSource();
        if (!ready(mod, source)) return 0;
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
            String template = StringArgumentType.getString(ctx, "template");
            if (!mod.commandCatalog().starters().containsKey(template)) {
                messages(mod, source).send(source, "command.grant.template.invalid",
                    Map.of("template", template));
                return 0;
            }
            if (mod.commandStore().hasProfile(target.getUuidAsString())) {
                messages(mod, source).send(source, "command.profile_exists",
                    Map.of("target", target.getName().getString()));
                return 0;
            }
            mod.commandStore().createProfile(target.getUuidAsString(), template);
            Map<String,String> vars = Map.of(
                "target", target.getName().getString(),
                "template", template
            );
            messages(mod, source).send(source, "command.grant.starter.success", vars);
            messages(mod, source).send(target.getCommandSource(), "command.grant.starter.received", vars);
            mod.commandOpen(target);
            return 1;
        } catch (Exception ex) {
            messages(mod, source).send(source, "command.failed",
                Map.of("error", safeError(ex)));
            return 0;
        }
    }

    private static int spectateList(CommandContext<ServerCommandSource> ctx, TcgMod mod) {
        ServerCommandSource source=ctx.getSource();List<String> matches=mod.commandSpectatable(source.getServer());
        messages(mod,source).send(source,"command.spectate.list",Map.of("matches",matches.isEmpty()?"none":String.join(" | ",matches)));return matches.size();
    }

    private static int spectate(CommandContext<ServerCommandSource> ctx, TcgMod mod) {
        ServerCommandSource source=ctx.getSource();ServerPlayerEntity viewer=source.getPlayer();
        if(viewer==null){messages(mod,source).send(source,"command.player_only");return 0;}
        try{ServerPlayerEntity target=EntityArgumentType.getPlayer(ctx,"player");mod.commandSpectate(viewer,target);messages(mod,source).send(source,"command.spectate.started",Map.of("target",target.getName().getString()));return 1;}
        catch(Exception ex){messages(mod,source).send(source,"command.failed",Map.of("error",safeError(ex)));return 0;}
    }

    private static int spectateLeave(CommandContext<ServerCommandSource> ctx, TcgMod mod) {
        ServerCommandSource source=ctx.getSource();ServerPlayerEntity viewer=source.getPlayer();
        if(viewer==null){messages(mod,source).send(source,"command.player_only");return 0;}mod.commandSpectateLeave(viewer);messages(mod,source).send(source,"command.spectate.left");return 1;
    }

    private static int realmStatus(CommandContext<ServerCommandSource> ctx, TcgMod mod) {
        ServerCommandSource source=ctx.getSource();messages(mod,source).send(source,"command.realm.status",Map.of("status",mod.commandRealmStatus(source.getServer())));return 1;
    }

    private static int realmReturn(CommandContext<ServerCommandSource> ctx, TcgMod mod) {
        ServerCommandSource source=ctx.getSource();ServerPlayerEntity player=source.getPlayer();if(player==null){messages(mod,source).send(source,"command.player_only");return 0;}
        mod.commandRealmReturn(player);messages(mod,source).send(source,"command.realm.returned");return 1;
    }

    private static int qaSpectatorEnter(CommandContext<ServerCommandSource> ctx, TcgMod mod) {
        ServerCommandSource source=ctx.getSource();ServerPlayerEntity player=source.getPlayer();
        if(player==null){messages(mod,source).send(source,"command.player_only");return 0;}
        try{mod.commandQaSpectatorEnter(player);messages(mod,source).send(source,"command.qa.success",Map.of("action","spectator enter"));return 1;}
        catch(Exception ex){messages(mod,source).send(source,"command.failed",Map.of("error",safeError(ex)));return 0;}
    }

    private static int qaSpectatorLeave(CommandContext<ServerCommandSource> ctx, TcgMod mod) {
        ServerCommandSource source=ctx.getSource();ServerPlayerEntity player=source.getPlayer();
        if(player==null){messages(mod,source).send(source,"command.player_only");return 0;}
        try{mod.commandSpectateLeave(player);messages(mod,source).send(source,"command.qa.success",Map.of("action","spectator leave"));return 1;}
        catch(Exception ex){messages(mod,source).send(source,"command.failed",Map.of("error",safeError(ex)));return 0;}
    }

    private static int qaSpellTrap(CommandContext<ServerCommandSource> ctx, TcgMod mod, String stage) {
        ServerCommandSource source=ctx.getSource();ServerPlayerEntity player=source.getPlayer();
        if(player==null){messages(mod,source).send(source,"command.player_only");return 0;}
        try{mod.commandQaSpellTrap(player,stage);messages(mod,source).send(source,"command.qa.success",Map.of("action","spelltrap "+stage));return 1;}
        catch(Exception ex){messages(mod,source).send(source,"command.failed",Map.of("error",safeError(ex)));return 0;}
    }

    private static int qaPosition(CommandContext<ServerCommandSource> ctx, TcgMod mod) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            messages(mod, source).send(source, "command.player_only");
            return 0;
        }
        try {
            mod.commandQaPosition(player);
            messages(mod, source).send(source, "command.qa.success", Map.of("stage", "position"));
            return 1;
        } catch (Exception ex) {
            messages(mod, source).send(source, "command.failed", Map.of("error", safeError(ex)));
            return 0;
        }
    }

    private static int qaCreation(CommandContext<ServerCommandSource> ctx, TcgMod mod, String stage) {
        ServerCommandSource source=ctx.getSource();ServerPlayerEntity player=source.getPlayer();
        if(player==null){messages(mod,source).send(source,"command.player_only");return 0;}
        try{mod.commandQaCreation(player,stage);messages(mod,source).send(source,"command.qa.success",Map.of("action","creation "+stage));return 1;}
        catch(Exception ex){messages(mod,source).send(source,"command.failed",Map.of("error",safeError(ex)));return 0;}
    }

    private static boolean ready(TcgMod mod, ServerCommandSource source) {
        if (mod.commandCatalog() != null && mod.commandStore() != null) return true;
        messages(mod, source).send(source, "command.not_ready");
        return false;
    }

    private static MessageService messages(TcgMod mod, ServerCommandSource source) {
        return mod.commandMessages(source.getServer());
    }

    private static Catalog.Card resolveCard(Catalog catalog, String query) {
        Catalog.Card exact = catalog.cards().get(query);
        if (exact != null) return exact;
        for (Catalog.Card card : catalog.cards().values()) {
            if (card.id().equalsIgnoreCase(query) || card.name().equalsIgnoreCase(query)) return card;
        }
        return null;
    }

    private static CompletableFuture<Suggestions> suggestCards(TcgMod mod, SuggestionsBuilder builder) {
        Catalog catalog = mod.commandCatalog();
        if (catalog == null) return builder.buildFuture();
        String needle = builder.getRemainingLowerCase();
        catalog.cards().values().stream()
            .sorted((a,b) -> a.id().compareToIgnoreCase(b.id()))
            .filter(card -> card.id().toLowerCase(Locale.ROOT).startsWith(needle)
                || card.name().toLowerCase(Locale.ROOT).contains(needle))
            .forEach(card -> builder.suggest(card.id()));
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestTemplates(TcgMod mod, SuggestionsBuilder builder) {
        Catalog catalog = mod.commandCatalog();
        if (catalog == null) return builder.buildFuture();
        String needle = builder.getRemainingLowerCase();
        catalog.starters().keySet().stream()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(needle))
            .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestFinishes(SuggestionsBuilder builder) {
        String needle = builder.getRemainingLowerCase();
        FINISHES.keySet().stream()
            .filter(id -> id.startsWith(needle))
            .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static String safeError(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}
