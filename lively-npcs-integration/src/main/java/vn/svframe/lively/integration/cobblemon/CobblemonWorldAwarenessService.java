package vn.svframe.lively.integration.cobblemon;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.api.events.entity.SpawnEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.events.pokemon.evolution.EvolutionCompleteEvent;
import com.cobblemon.mod.common.api.events.pokemon.healing.PokemonHealedEvent;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.economy.EconomyEngine;
import vn.svframe.lively.event.WorldEventEngine;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.social.SocialEngine;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges public Cobblemon 1.7.3 events into the generic Lively world model.
 * Only immutable primitive facts cross the boundary. No worker retains live Cobblemon entities.
 */
public final class CobblemonWorldAwarenessService {
    private static final long CREATURE_TTL_MS = Duration.ofMinutes(20).toMillis();
    private static final long MIGRATION_WINDOW_MS = Duration.ofMinutes(10).toMillis();
    private static final long MIGRATION_COOLDOWN_MS = Duration.ofMinutes(30).toMillis();
    private static final int MIGRATION_THRESHOLD = 14;
    private static final int MAX_TRACKED_SPAWNS = 4096;
    private static final double OBSERVER_RADIUS_SQ = 48D * 48D;
    private static final int SPATIAL_CELL = 64;
    private static final long SPATIAL_REFRESH_TICKS = 100L;

    private record SpawnSample(long at, ActorId actor, String world, String species, Vec3d position, boolean rare) {}
    private record NpcSample(UUID id, String world, Vec3d position) {}
    private record SpatialCell(String world, int x, int z) {
        static SpatialCell of(String world, Vec3d position) {
            return new SpatialCell(world, Math.floorDiv((int) Math.floor(position.x), SPATIAL_CELL),
                    Math.floorDiv((int) Math.floor(position.z), SPATIAL_CELL));
        }
    }

    private final ConcurrentHashMap<String, ArrayDeque<SpawnSample>> spawns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ActorId, Long> creatureExpiry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> migrationCooldown = new ConcurrentHashMap<>();
    private volatile Map<SpatialCell, List<NpcSample>> spatial = Map.of();
    private volatile MinecraftServer server;
    private volatile boolean installed;

    public synchronized void install() {
        if (installed) return;
        installed = true;

        LivelyApi.blockCapabilities().register("cobblemon:healing_machine", Set.of("heal_pokemon", "clinic"));
        LivelyApi.blockCapabilities().register("cobblemon:pc", Set.of("pokemon_storage", "clinic"));
        LivelyApi.blockCapabilities().register("cobblemon:pasture", Set.of("pokemon_habitat", "ranch"));
        LivelyApi.blockCapabilities().register("cobblemon:fossil_analyzer", Set.of("fossil_research", "research"));

        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe((SpawnEvent<PokemonEntity> event) -> onSpawn(event.getEntity()));
        CobblemonEvents.POKEMON_CAPTURED.subscribe(this::onCapture);
        CobblemonEvents.EVOLUTION_COMPLETE.subscribe(this::onEvolution);
        CobblemonEvents.POKEMON_HEALED.subscribe(this::onHeal);
        CobblemonEvents.BATTLE_VICTORY.subscribe(this::onBattleVictory);

        ServerLifecycleEvents.SERVER_STARTED.register(this::startSession);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::stopSession);
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private void startSession(MinecraftServer next) {
        server = next;
        spawns.clear();
        creatureExpiry.clear();
        migrationCooldown.clear();
        spatial = Map.of();
        rebuildSpatialIndex();
    }

    private void stopSession(MinecraftServer stopping) {
        if (server != stopping) return;
        clearTransientCreatures();
        spawns.clear();
        creatureExpiry.clear();
        migrationCooldown.clear();
        spatial = Map.of();
        server = null;
    }

    private void onSpawn(PokemonEntity entity) {
        MinecraftServer active = server;
        if (active == null || entity == null || entity.getWorld().isClient()
                || entity.getCommandTags().contains("lively_body")) return;
        Pokemon pokemon = entity.getPokemon();
        if (pokemon == null) return;
        String world = entity.getWorld().getRegistryKey().getValue().toString();
        Vec3d position = entity.getPos();
        ActorId actor = new ActorId(pokemon.getUuid(), ActorId.Kind.CREATURE);
        String species = pokemon.getSpecies().getResourceIdentifier().toString();
        boolean rare = pokemon.getShiny() || pokemon.isLegendary() || pokemon.isMythical() || pokemon.isUltraBeast();
        boolean wild = pokemon.getOwnerUUID() == null;

        Map<String, Double> social = Map.of(
                "level", Math.min(1D, pokemon.getLevel() / 100D),
                "rarity", rare ? 1D : 0D,
                "wild", wild ? 1D : 0D);
        Map<String, String> facts = new HashMap<>();
        facts.put("species", species);
        facts.put("level", Integer.toString(pokemon.getLevel()));
        facts.put("world", world);
        facts.put("x", compact(position.x)); facts.put("y", compact(position.y)); facts.put("z", compact(position.z));
        facts.put("shiny", Boolean.toString(pokemon.getShiny()));
        facts.put("legendary", Boolean.toString(pokemon.isLegendary()));
        facts.put("mythical", Boolean.toString(pokemon.isMythical()));
        facts.put("ultra_beast", Boolean.toString(pokemon.isUltraBeast()));
        if (pokemon.getOwnerUUID() != null) facts.put("owner", pokemon.getOwnerUUID().toString());
        Set<String> tags = new HashSet<>(Set.of("pokemon", wild ? "wild" : "owned"));
        if (rare) tags.add("rare");
        LivelyApi.actors().upsert(actor, pokemon.getSpecies().getName(), social, facts, tags);
        creatureExpiry.put(actor, System.currentTimeMillis() + CREATURE_TTL_MS);

        if (wild) {
            SpawnSample sample = new SpawnSample(System.currentTimeMillis(), actor, world, species, position, rare);
            ArrayDeque<SpawnSample> queue = spawns.computeIfAbsent(world + "|" + species, ignored -> new ArrayDeque<>());
            synchronized (queue) {
                queue.addLast(sample);
                while (queue.size() > MAX_TRACKED_SPAWNS) queue.removeFirst();
            }
        }
        if (rare) rememberNearby(world, position, "rare_pokemon_seen", Map.of(
                "pokemon", actor.uuid().toString(), "species", species, "level", Integer.toString(pokemon.getLevel()),
                "shiny", Boolean.toString(pokemon.getShiny())), .72D);
    }

    private void onCapture(PokemonCapturedEvent event) {
        MinecraftServer active = server;
        if (active == null || event == null || event.getPlayer() == null || event.getPokemon() == null) return;
        ServerPlayerEntity player = event.getPlayer();
        Pokemon pokemon = event.getPokemon();
        String species = pokemon.getSpecies().getResourceIdentifier().toString();
        boolean rare = isRare(pokemon);
        ActorId playerActor = new ActorId(player.getUuid(), ActorId.Kind.PLAYER);
        LivelyApi.actors().upsert(playerActor, player.getName().getString(), Map.of(),
                Map.of("world", player.getServerWorld().getRegistryKey().getValue().toString()), Set.of("player", "trainer"));
        Map<String, String> facts = Map.of("player", player.getUuid().toString(), "species", species,
                "pokemon", pokemon.getUuid().toString(), "rare", Boolean.toString(rare));
        rememberNearby(player.getServerWorld().getRegistryKey().getValue().toString(), player.getPos(),
                "pokemon_capture_observed", facts, rare ? .75D : .38D);
        socialResponseNearby(player, rare, facts);
        changePokemonMarket(species, -.02D, rare ? .04D : .015D);
    }

    private void onEvolution(EvolutionCompleteEvent event) {
        MinecraftServer active = server;
        if (active == null || event == null || event.getPokemon() == null) return;
        Pokemon pokemon = event.getPokemon();
        Entity owner = pokemon.getOwnerEntity();
        if (owner == null || owner.getWorld().isClient()) return;
        String from = event.getSourcePokemon() == null ? "unknown" : event.getSourcePokemon().getSpecies().getResourceIdentifier().toString();
        String to = pokemon.getSpecies().getResourceIdentifier().toString();
        rememberNearby(owner.getWorld().getRegistryKey().getValue().toString(), owner.getPos(), "pokemon_evolution_observed",
                Map.of("from", from, "to", to, "pokemon", pokemon.getUuid().toString()), .52D);
    }

    private void onHeal(PokemonHealedEvent event) {
        MinecraftServer active = server;
        if (active == null || event == null || event.getPokemon() == null || !event.isHealed()) return;
        Entity owner = event.getPokemon().getOwnerEntity();
        if (owner == null || owner.getWorld().isClient()) return;
        rememberNearby(owner.getWorld().getRegistryKey().getValue().toString(), owner.getPos(), "pokemon_healed_nearby",
                Map.of("species", event.getPokemon().getSpecies().getResourceIdentifier().toString(),
                        "amount", Integer.toString(event.getAmount()), "full", Boolean.toString(event.isFullHeal())), .24D);
    }

    private void onBattleVictory(BattleVictoryEvent event) {
        MinecraftServer active = server;
        if (active == null || event == null) return;
        for (var winner : event.getWinners()) {
            for (UUID playerId : winner.getPlayerUUIDs()) {
                ServerPlayerEntity player = active.getPlayerManager().getPlayer(playerId);
                if (player == null) continue;
                ActorId actor = new ActorId(playerId, ActorId.Kind.PLAYER);
                LivelyApi.actors().upsert(actor, player.getName().getString(), Map.of(),
                        Map.of("world", player.getServerWorld().getRegistryKey().getValue().toString()), Set.of("player", "trainer"));
                LivelyApi.social().changeReputation(actor, SocialEngine.ReputationScope.GLOBAL, "", .0025D);
                rememberNearby(player.getServerWorld().getRegistryKey().getValue().toString(), player.getPos(), "trainer_battle_victory_observed",
                        Map.of("player", playerId.toString(), "battle", event.getBattle().getBattleId().toString()), .30D);
            }
        }
    }

    private void tick(MinecraftServer active) {
        if (server != active) return;
        long tick = active.getTicks();
        if (tick % SPATIAL_REFRESH_TICKS == 0L) rebuildSpatialIndex();
        if (tick % 1200L != 0L) return;
        long now = System.currentTimeMillis();
        pruneCreatures(now);
        for (Map.Entry<String, ArrayDeque<SpawnSample>> entry : spawns.entrySet()) evaluateMigration(entry.getKey(), entry.getValue(), now);
    }

    private void rebuildSpatialIndex() {
        if (LivelyApi.npcs() == null) { spatial = Map.of(); return; }
        Map<SpatialCell, List<NpcSample>> mutable = new LinkedHashMap<>();
        for (NpcDefinition npc : LivelyApi.npcs().snapshot().values()) {
            if (!npc.spawned()) continue;
            String world = LivelyApi.npcs().worldKey(npc.id()).orElse(npc.world());
            Vec3d position = LivelyApi.npcs().position(npc.id()).orElse(null);
            if (world == null || position == null) continue;
            mutable.computeIfAbsent(SpatialCell.of(world, position), ignored -> new ArrayList<>())
                    .add(new NpcSample(npc.id(), world, position));
        }
        Map<SpatialCell, List<NpcSample>> frozen = new LinkedHashMap<>();
        mutable.forEach((cell, values) -> frozen.put(cell, List.copyOf(values)));
        spatial = Map.copyOf(frozen);
    }

    private List<NpcSample> nearby(String world, Vec3d position, double radius, int limit) {
        if (world == null || position == null || limit <= 0) return List.of();
        SpatialCell center = SpatialCell.of(world, position);
        int cells = Math.max(1, (int) Math.ceil(radius / SPATIAL_CELL));
        double radiusSq = radius * radius;
        ArrayList<NpcSample> result = new ArrayList<>();
        for (int dx = -cells; dx <= cells && result.size() < limit; dx++) {
            for (int dz = -cells; dz <= cells && result.size() < limit; dz++) {
                for (NpcSample sample : spatial.getOrDefault(new SpatialCell(world, center.x() + dx, center.z() + dz), List.of())) {
                    if (sample.position().squaredDistanceTo(position) <= radiusSq) {
                        result.add(sample);
                        if (result.size() >= limit) break;
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private void evaluateMigration(String key, ArrayDeque<SpawnSample> queue, long now) {
        List<SpawnSample> samples;
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst().at() > MIGRATION_WINDOW_MS) queue.removeFirst();
            samples = List.copyOf(queue);
        }
        if (samples.size() < MIGRATION_THRESHOLD) return;
        long next = migrationCooldown.getOrDefault(key, 0L);
        if (now < next) return;
        SpawnSample first = samples.get(0);
        String seed = "pokemon_migration:" + sanitize(first.species()) + ":" + Integer.toUnsignedString(first.world().hashCode(), 36);
        if (LivelyApi.events().activeEvents().stream().anyMatch(event -> event.seed().equals(seed))) return;
        long rareCount = samples.stream().filter(SpawnSample::rare).count();
        double intensity = Math.min(1D, .32D + samples.size() / 40D + rareCount * .08D);
        Set<ActorId> participants = nearbyNpcActors(first.world(), average(samples), 96D, 24);
        var proposal = new WorldEventEngine.EventProposal(WorldEventEngine.Category.MIGRATION, seed, null, participants,
                intensity, Duration.ofHours(2), Map.of(
                "kind", "pokemon_migration", "species", first.species(), "world", first.world(),
                "observed_spawns", Integer.toString(samples.size()), "rare_observations", Long.toString(rareCount),
                "physical_world_mutation", "false"));
        if (LivelyApi.events().start(proposal).isPresent()) {
            migrationCooldown.put(key, now + MIGRATION_COOLDOWN_MS);
            changePokemonMarket(first.species(), .12D, rareCount > 0 ? .08D : .18D);
            rememberParticipants(participants, "pokemon_migration_detected", Map.of(
                    "species", first.species(), "world", first.world(), "count", Integer.toString(samples.size())), .62D);
        }
    }

    private void socialResponseNearby(ServerPlayerEntity player, boolean rare, Map<String, String> facts) {
        if (LivelyApi.states() == null) return;
        ActorId playerActor = new ActorId(player.getUuid(), ActorId.Kind.PLAYER);
        String world = player.getServerWorld().getRegistryKey().getValue().toString();
        for (NpcSample sample : nearby(world, player.getPos(), 48D, 128)) {
            ActorId observer = new ActorId(sample.id(), ActorId.Kind.NPC);
            LivelyApi.social().apply(observer, playerActor, new SocialEngine.SocialDelta(
                    .003D, .001D, rare ? .018D : .006D, rare ? .004D : 0D, 0D, 0D, .008D,
                    "pokemon_capture_observed", facts));
        }
    }

    private void rememberNearby(String world, Vec3d position, String type, Map<String, String> facts, double importance) {
        if (LivelyApi.states() == null) return;
        for (NpcSample sample : nearby(world, position, 48D, 128)) {
            LivelyApi.states().get(sample.id()).ifPresent(state -> state.remember(type, facts, importance, 1D));
        }
    }

    private Set<ActorId> nearbyNpcActors(String world, Vec3d center, double radius, int limit) {
        Set<ActorId> result = new HashSet<>();
        for (NpcSample sample : nearby(world, center, radius, limit)) result.add(new ActorId(sample.id(), ActorId.Kind.NPC));
        return Set.copyOf(result);
    }

    private void rememberParticipants(Set<ActorId> actors, String type, Map<String, String> facts, double importance) {
        if (LivelyApi.states() == null) return;
        for (ActorId actor : actors) if (actor.kind() == ActorId.Kind.NPC) {
            LivelyApi.states().get(actor.uuid()).ifPresent(state -> state.remember(type, facts, importance, .9D));
        }
    }

    private void changePokemonMarket(String species, double demandDelta, double supplyDelta) {
        EconomyEngine.Snapshot snapshot = LivelyApi.economy().snapshot();
        String canonical = "pokemon:" + species.toLowerCase(Locale.ROOT);
        for (EconomyEngine.Stock stock : snapshot.stocks().values()) {
            String item = stock.key().itemId().toLowerCase(Locale.ROOT);
            if (!item.equals(canonical) && !item.equals(species.toLowerCase(Locale.ROOT))) continue;
            LivelyApi.economy().setStock(stock.key().businessId(), stock.key().itemId(), stock.quantity(), stock.targetQuantity(), stock.basePrice(),
                    clamp01(stock.demand() + demandDelta), clamp01(stock.supply() + supplyDelta));
        }
    }

    private void pruneCreatures(long now) {
        for (Map.Entry<ActorId, Long> entry : creatureExpiry.entrySet()) {
            if (entry.getValue() > now) continue;
            if (creatureExpiry.remove(entry.getKey(), entry.getValue())) LivelyApi.actors().remove(entry.getKey());
        }
    }

    private void clearTransientCreatures() {
        for (ActorId actor : new ArrayList<>(creatureExpiry.keySet())) LivelyApi.actors().remove(actor);
    }

    private static boolean isRare(Pokemon pokemon) {
        return pokemon.getShiny() || pokemon.isLegendary() || pokemon.isMythical() || pokemon.isUltraBeast();
    }

    private static Vec3d average(List<SpawnSample> samples) {
        double x = 0D, y = 0D, z = 0D;
        for (SpawnSample sample : samples) { x += sample.position().x; y += sample.position().y; z += sample.position().z; }
        double n = Math.max(1D, samples.size());
        return new Vec3d(x / n, y / n, z / n);
    }

    private static String sanitize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.:-]", "_");
    }

    private static String compact(double value) { return String.format(Locale.ROOT, "%.2f", value); }
    private static double clamp01(double value) { return Math.max(0D, Math.min(1D, value)); }
}
