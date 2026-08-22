package vn.svframe.lively.integration.cobblemon;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.FossilRevivedEvent;
import com.cobblemon.mod.common.api.events.pokemon.FriendshipUpdatedEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokedexDataChangedEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonRecallEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonSentEvent;
import com.cobblemon.mod.common.api.events.pokemon.TradeEvent;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.quest.QuestRuntime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cobblemon awareness that is intentionally separate from wild/migration accounting: player trades, bonds, research
 * and send/recall activity. Event handlers use a periodically rebuilt NPC spatial index and bounded cooldown maps, so
 * common Pokémon actions never trigger an all-NPC scan.
 */
public final class CobblemonSocialResearchAwarenessService {
    private static final int SPATIAL_CELL = 64;
    private static final long SPATIAL_REFRESH_TICKS = 100L;
    private static final int MAX_NEARBY_NPCS = 24;
    private static final long TEAM_EVENT_COOLDOWN_MS = 2_000L;
    private static final long BOND_EVENT_COOLDOWN_MS = 5_000L;
    private static final int MAX_COOLDOWNS = 8192;

    private record NpcSample(UUID id, String world, Vec3d position) {}
    private record Cell(String world, int x, int z) {
        static Cell of(String world, Vec3d position) {
            return new Cell(world, Math.floorDiv((int) Math.floor(position.x), SPATIAL_CELL),
                    Math.floorDiv((int) Math.floor(position.z), SPATIAL_CELL));
        }
    }

    private final ConcurrentHashMap<String, Long> cooldowns = new ConcurrentHashMap<>();
    private volatile Map<Cell, List<NpcSample>> spatial = Map.of();
    private volatile MinecraftServer server;
    private volatile boolean installed;

    public synchronized void install() {
        if (installed) return;
        installed = true;
        CobblemonEvents.TRADE_EVENT_POST.subscribe(this::onTrade);
        CobblemonEvents.FRIENDSHIP_UPDATED.subscribe(this::onFriendship);
        CobblemonEvents.FOSSIL_REVIVED.subscribe(this::onFossilRevived);
        CobblemonEvents.POKEDEX_DATA_CHANGED_POST.subscribe(this::onPokedexChanged);
        CobblemonEvents.POKEMON_SENT_POST.subscribe(this::onSent);
        CobblemonEvents.POKEMON_RECALL_POST.subscribe(this::onRecall);
        ServerLifecycleEvents.SERVER_STARTED.register(this::startSession);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::stopSession);
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private void startSession(MinecraftServer next) {
        server = next;
        cooldowns.clear();
        spatial = Map.of();
        rebuildSpatial();
    }

    private void stopSession(MinecraftServer stopping) {
        if (server != stopping) return;
        cooldowns.clear();
        spatial = Map.of();
        server = null;
    }

    private void tick(MinecraftServer current) {
        if (server != current) return;
        if (current.getTicks() % SPATIAL_REFRESH_TICKS == 0L) rebuildSpatial();
        if (current.getTicks() % 1200L == 0L && cooldowns.size() > MAX_COOLDOWNS) {
            long cutoff = System.currentTimeMillis() - Duration.ofMinutes(5).toMillis();
            cooldowns.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        }
    }

    private void onTrade(TradeEvent.Post event) {
        MinecraftServer current = server;
        if (current == null || event == null) return;
        UUID first = event.getTradeParticipant1().getUuid();
        UUID second = event.getTradeParticipant2().getUuid();
        Pokemon firstPokemon = event.getTradeParticipant1Pokemon();
        Pokemon secondPokemon = event.getTradeParticipant2Pokemon();
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("participant1", first.toString());
        facts.put("participant2", second.toString());
        facts.put("pokemon1", species(firstPokemon));
        facts.put("pokemon2", species(secondPokemon));
        signalPlayer(first, "cobblemon:trade", facts);
        signalPlayer(second, "cobblemon:trade", facts);
        rememberAroundPlayer(first, "pokemon_trade_observed", facts, .48D);
        rememberAroundPlayer(second, "pokemon_trade_observed", facts, .48D);
    }

    private void onFriendship(FriendshipUpdatedEvent event) {
        MinecraftServer current = server;
        if (current == null || event == null || event.getPokemon() == null) return;
        Pokemon pokemon = event.getPokemon();
        ServerPlayerEntity owner = pokemon.getOwnerPlayer();
        if (owner == null) return;
        int before = event.getNewFriendshipInitial();
        int after = event.getNewFriendship();
        int delta = after - before;
        if (delta == 0 || !allow("bond:" + pokemon.getUuid(), BOND_EVENT_COOLDOWN_MS)) return;
        Map<String, String> facts = Map.of(
                "pokemon", pokemon.getUuid().toString(),
                "species", species(pokemon),
                "friendship", Integer.toString(after),
                "delta", Integer.toString(delta));
        signal(owner, "cobblemon:bond", facts);
        if (Math.abs(delta) >= 2) rememberNearby(owner.getServerWorld().getRegistryKey().getValue().toString(), owner.getPos(),
                "pokemon_bond_changed", facts, Math.min(.55D, .18D + Math.abs(delta) / 100D));
    }

    private void onFossilRevived(FossilRevivedEvent event) {
        if (server == null || event == null || event.getPlayer() == null || event.getPokemon() == null) return;
        ServerPlayerEntity player = event.getPlayer();
        Pokemon pokemon = event.getPokemon();
        Map<String, String> facts = Map.of(
                "pokemon", pokemon.getUuid().toString(),
                "species", species(pokemon),
                "research", "fossil_revived");
        signal(player, "cobblemon:research", facts);
        rememberNearby(player.getServerWorld().getRegistryKey().getValue().toString(), player.getPos(),
                "pokemon_research_breakthrough", facts, .72D);
    }

    private void onPokedexChanged(PokedexDataChangedEvent.Post event) {
        MinecraftServer current = server;
        if (current == null || event == null || event.getPlayerUUID() == null) return;
        ServerPlayerEntity player = current.getPlayerManager().getPlayer(event.getPlayerUUID());
        if (player == null || !allow("dex:" + player.getUuid(), 750L)) return;
        Map<String, String> facts = new HashMap<>();
        facts.put("research", "pokedex_progress");
        facts.put("player", player.getUuid().toString());
        if (event.getRecord() != null) facts.put("record", bounded(event.getRecord().toString(), 160));
        signal(player, "cobblemon:research", facts);
    }

    private void onSent(PokemonSentEvent.Post event) {
        if (server == null || event == null || event.getPokemon() == null || event.getPokemonEntity() == null) return;
        Pokemon pokemon = event.getPokemon();
        ServerPlayerEntity owner = pokemon.getOwnerPlayer();
        if (owner == null || !allow("sent:" + owner.getUuid() + ":" + pokemon.getUuid(), TEAM_EVENT_COOLDOWN_MS)) return;
        Map<String, String> facts = Map.of("pokemon", pokemon.getUuid().toString(), "species", species(pokemon), "action", "sent_out");
        signal(owner, "cobblemon:send_out", facts);
        rememberNearby(owner.getServerWorld().getRegistryKey().getValue().toString(), event.getPosition(),
                "trainer_sent_pokemon", facts, .16D);
    }

    private void onRecall(PokemonRecallEvent.Post event) {
        if (server == null || event == null || event.getPokemon() == null) return;
        Pokemon pokemon = event.getPokemon();
        ServerPlayerEntity owner = pokemon.getOwnerPlayer();
        if (owner == null || !allow("recall:" + owner.getUuid() + ":" + pokemon.getUuid(), TEAM_EVENT_COOLDOWN_MS)) return;
        Map<String, String> facts = Map.of("pokemon", pokemon.getUuid().toString(), "species", species(pokemon), "action", "recalled");
        signal(owner, "cobblemon:recall", facts);
        rememberNearby(owner.getServerWorld().getRegistryKey().getValue().toString(), owner.getPos(),
                "trainer_recalled_pokemon", facts, .12D);
    }

    private void signalPlayer(UUID playerId, String target, Map<String, String> facts) {
        MinecraftServer current = server;
        if (current == null) return;
        ServerPlayerEntity player = current.getPlayerManager().getPlayer(playerId);
        if (player != null) signal(player, target, facts);
    }

    private void signal(ServerPlayerEntity player, String target, Map<String, String> facts) {
        ActorId owner = new ActorId(player.getUuid(), ActorId.Kind.PLAYER);
        LivelyApi.quests().signal(owner, QuestRuntime.ObjectiveType.CUSTOM, target, 1L, facts);
    }

    private void rememberAroundPlayer(UUID playerId, String type, Map<String, String> facts, double importance) {
        MinecraftServer current = server;
        if (current == null) return;
        ServerPlayerEntity player = current.getPlayerManager().getPlayer(playerId);
        if (player != null) rememberNearby(player.getServerWorld().getRegistryKey().getValue().toString(), player.getPos(), type, facts, importance);
    }

    private void rememberNearby(String world, Vec3d position, String type, Map<String, String> facts, double importance) {
        if (LivelyApi.states() == null || world == null || position == null) return;
        for (NpcSample sample : nearby(world, position, 64D, MAX_NEARBY_NPCS)) {
            LivelyApi.states().get(sample.id()).ifPresent(state -> state.remember(type, facts, importance, 1D));
        }
    }

    private void rebuildSpatial() {
        if (LivelyApi.npcs() == null) {
            spatial = Map.of();
            return;
        }
        Map<Cell, List<NpcSample>> mutable = new LinkedHashMap<>();
        for (NpcDefinition npc : LivelyApi.npcs().snapshot().values()) {
            if (!npc.spawned()) continue;
            String world = LivelyApi.npcs().worldKey(npc.id()).orElse(npc.world());
            Vec3d position = LivelyApi.npcs().position(npc.id()).orElse(null);
            if (world == null || position == null) continue;
            mutable.computeIfAbsent(Cell.of(world, position), ignored -> new ArrayList<>()).add(new NpcSample(npc.id(), world, position));
        }
        Map<Cell, List<NpcSample>> frozen = new LinkedHashMap<>();
        mutable.forEach((cell, values) -> frozen.put(cell, List.copyOf(values)));
        spatial = Map.copyOf(frozen);
    }

    private List<NpcSample> nearby(String world, Vec3d position, double radius, int limit) {
        Cell center = Cell.of(world, position);
        int distance = Math.max(1, (int) Math.ceil(radius / SPATIAL_CELL));
        double radiusSq = radius * radius;
        ArrayList<NpcSample> result = new ArrayList<>();
        for (int dx = -distance; dx <= distance && result.size() < limit; dx++) {
            for (int dz = -distance; dz <= distance && result.size() < limit; dz++) {
                for (NpcSample sample : spatial.getOrDefault(new Cell(world, center.x() + dx, center.z() + dz), List.of())) {
                    if (sample.position().squaredDistanceTo(position) <= radiusSq) {
                        result.add(sample);
                        if (result.size() >= limit) break;
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private boolean allow(String key, long cooldownMillis) {
        long now = System.currentTimeMillis();
        Long previous = cooldowns.put(key, now);
        return previous == null || now - previous >= cooldownMillis;
    }

    private static String species(Pokemon pokemon) {
        return pokemon == null || pokemon.getSpecies() == null ? "unknown" : pokemon.getSpecies().getResourceIdentifier().toString();
    }

    private static String bounded(String value, int max) {
        if (value == null) return "";
        String clean = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
