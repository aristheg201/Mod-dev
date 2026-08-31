package vn.svframe.svquest.server;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svquest.SVQuest;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Restores beta.5 Cobblemon milestone events that the reduced beta.9 bridge did not emit. */
public final class CobblemonMilestoneBridge {
    private final MinecraftServer server;
    private final QuestEngine engine;

    public CobblemonMilestoneBridge(MinecraftServer server, QuestEngine engine) {
        this.server = server;
        this.engine = engine;
    }

    public void install() {
        if (!FabricLoader.getInstance().isModLoaded("cobblemon")) return;
        try {
            Class<?> events = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            subscribe(events, "LEVEL_UP_EVENT", this::levelUp);
            subscribe(events, "BATTLE_FAINTED", this::battleFainted);
            subscribe(events, "ZPOWER_USED", this::zMove);
            SVQuest.LOGGER.info("SVQuest Cobblemon milestone bridge enabled.");
        } catch (Throwable t) {
            SVQuest.LOGGER.warn("SVQuest Cobblemon milestone bridge disabled safely: {}", t.toString());
        }
    }

    private void levelUp(Object event) {
        Object pokemon = call(event, "getPokemon");
        ServerPlayerEntity player = owner(pokemon);
        if (player == null) return;
        long oldLevel = number(call(event, "getOldLevel"));
        long newLevel = number(call(event, "getNewLevel"));
        long delta = Math.max(1, newLevel - oldLevel);
        engine.emit(player, "LEVEL_UP", delta, pokemonMeta(pokemon));
        engine.absolute(player, "POKEMON_LEVEL", newLevel, pokemonMeta(pokemon));
    }

    private void battleFainted(Object event) {
        Object killed = call(event, "getKilled");
        Object pokemon = first(call(killed, "getOriginalPokemon"), call(killed, "getEffectedPokemon"));
        Map<String, String> meta = pokemonMeta(pokemon);
        Object actor = call(killed, "getActor");
        Object side = call(actor, "getSide");
        Object opposite = call(side, "getOppositeSide");
        Object actors = call(opposite, "getActors");
        if (!(actors instanceof Iterable<?> iterable)) return;
        for (Object other : iterable) {
            Object ids = call(other, "getPlayerUUIDs");
            if (!(ids instanceof Iterable<?> players)) continue;
            for (Object id : players) {
                if (id instanceof UUID uuid) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                    if (player != null) engine.emit(player, "DEFEAT", 1, meta);
                }
            }
        }
    }

    private void zMove(Object event) {
        Object battlePokemon = first(call(event, "getPokemon"), call(event, "getBattlePokemon"));
        Object actor = call(battlePokemon, "getActor");
        Object ids = call(actor, "getPlayerUUIDs");
        if (!(ids instanceof Iterable<?> iterable)) return;
        for (Object id : iterable) {
            if (id instanceof UUID uuid) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player != null) engine.emit(player, "ZMOVE_USE", 1, Map.of());
            }
        }
    }

    private ServerPlayerEntity owner(Object pokemon) {
        Object direct = call(pokemon, "getOwnerPlayer");
        if (direct instanceof ServerPlayerEntity player) return player;
        Object uuid = first(call(pokemon, "getOwnerUUID"), call(pokemon, "getOwnerUuid"));
        return uuid instanceof UUID id ? server.getPlayerManager().getPlayer(id) : null;
    }

    private static Map<String, String> pokemonMeta(Object pokemon) {
        HashMap<String, String> out = new HashMap<>();
        Object species = call(pokemon, "getSpecies");
        Object name = call(species, "getName");
        String value = name == null ? "" : String.valueOf(name).toLowerCase(java.util.Locale.ROOT);
        if (value.isBlank()) {
            Object id = call(species, "getResourceIdentifier");
            value = id == null ? "" : String.valueOf(id).toLowerCase(java.util.Locale.ROOT);
            int colon = value.indexOf(':');
            if (colon >= 0) value = value.substring(colon + 1);
        }
        out.put("species", value);
        out.put("target", value);
        return out;
    }

    private static void subscribe(Class<?> owner, String field, Consumer<Object> callback) {
        try {
            Object observable = owner.getField(field).get(null);
            Method subscribe = observable.getClass().getMethod("subscribe", Consumer.class);
            subscribe.invoke(observable, (Consumer<Object>) event -> {
                try { callback.accept(event); }
                catch (Throwable t) { SVQuest.LOGGER.debug("Cobblemon {} callback failed safely: {}", field, t.toString()); }
            });
        } catch (Throwable t) {
            SVQuest.LOGGER.debug("Cobblemon event {} unavailable: {}", field, t.toString());
        }
    }

    private static Object call(Object target, String method) {
        if (target == null) return null;
        try { return target.getClass().getMethod(method).invoke(target); }
        catch (Throwable ignored) { return null; }
    }
    private static Object first(Object a, Object b) { return a != null ? a : b; }
    private static long number(Object value) { return value instanceof Number n ? n.longValue() : 0L; }
}
