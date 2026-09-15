package vn.svframe.svrelationships.fabric.household;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Unit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import vn.svframe.svrelationships.fabric.diagnostics.RuntimeMetrics;
import vn.svframe.svrelationships.fabric.relationship.RelationshipService;
import vn.svframe.svrelationships.household.HouseholdMaterializationPlanner;
import vn.svframe.svrelationships.household.HouseholdState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HouseholdMaterializationService {
    private final MinecraftServer server;
    private final HouseholdService households;
    private final RelationshipService relationships;
    private final RuntimeMetrics metrics;
    private final HouseholdMaterializationPlanner planner = new HouseholdMaterializationPlanner();
    private final Map<UUID, ManagedPokemon> managedPokemon = new HashMap<>();
    private final Map<UUID, Boolean> ownerActive = new HashMap<>();
    private long tickIndex;

    public HouseholdMaterializationService(MinecraftServer server, HouseholdService households, RelationshipService relationships, RuntimeMetrics metrics) {
        this.server = server; this.households = households; this.relationships = relationships; this.metrics = metrics;
    }

    public void tick() {
        long started = System.nanoTime();
        tickIndex++;
        Set<UUID> onlineOwners = new HashSet<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            onlineOwners.add(player.getUuid());
            households.get(player.getUuid()).ifPresent(household -> {
                int interval = Math.max(1, households.evaluationIntervalTicks(household));
                int phase = Math.floorMod(player.getUuid().hashCode(), interval);
                if (Math.floorMod(tickIndex, interval) == phase) evaluateOwner(player, household);
            });
        }
        recallOfflineOwners(onlineOwners);
        metrics.gauge("household.materialized", managedPokemon.size());
        metrics.add("household.tick_nanos", System.nanoTime() - started);
        metrics.increment("household.tick_count");
    }

    private void evaluateOwner(ServerPlayerEntity owner, HouseholdState household) {
        boolean wasActive = ownerActive.getOrDefault(owner.getUuid(), false);
        int threshold = wasActive ? households.deactivationRadius(household) : households.activeRadius(household);
        boolean shouldBeActive = sameDimension(owner, household) && within(owner.getPos(), household, threshold);
        ownerActive.put(owner.getUuid(), shouldBeActive);
        if (!shouldBeActive) { recallManagedFor(owner.getUuid()); return; }

        Map<UUID, Pokemon> owned = ownedIndex(owner);
        List<Pokemon> partnerPokemon = new ArrayList<>();
        for (var relationship : relationships.partners(owner.getUuid())) {
            if (!household.householdId().equals(relationship.householdId())) continue;
            Pokemon pokemon = owned.get(relationship.key().pokemonId());
            if (pokemon != null) partnerPokemon.add(pokemon);
        }
        int maximum = households.maxMaterializedPartners(household);
        List<HouseholdMaterializationPlanner.Candidate> candidates = new ArrayList<>();
        for (Pokemon pokemon : partnerPokemon) {
            boolean managed = managedPokemon.containsKey(pokemon.getUuid());
            double distance = pokemon.getEntity() == null ? 0.0 : pokemon.getEntity().getPos().squaredDistanceTo(anchor(household));
            candidates.add(new HouseholdMaterializationPlanner.Candidate(pokemon.getUuid(), distance, managed));
        }
        Set<UUID> selected = new HashSet<>(planner.select(candidates, maximum));
        for (Pokemon pokemon : partnerPokemon) {
            if (selected.contains(pokemon.getUuid())) materialize(owner, household, pokemon, selected.size());
            else recallIfManaged(pokemon.getUuid());
        }
        enforceBoundary(household, partnerPokemon);
    }

    private Map<UUID, Pokemon> ownedIndex(ServerPlayerEntity player) {
        Map<UUID, Pokemon> result = new HashMap<>();
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) result.put(pokemon.getUuid(), pokemon);
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getPC(player)) result.put(pokemon.getUuid(), pokemon);
        return result;
    }

    private void materialize(ServerPlayerEntity owner, HouseholdState household, Pokemon pokemon, int population) {
        if (pokemon.getEntity() != null) return;
        ServerWorld world = owner.getServerWorld();
        Vec3d position = spawnPosition(world, household, pokemon.getUuid(), Math.max(1, population));
        var entity = pokemon.sendOut(world, position, null, sent -> Unit.INSTANCE);
        if (entity != null) {
            managedPokemon.put(pokemon.getUuid(), new ManagedPokemon(owner.getUuid(), pokemon));
            metrics.increment("household.materialize");
        }
    }

    private void enforceBoundary(HouseholdState household, List<Pokemon> pokemon) {
        int radius = households.deactivationRadius(household);
        for (Pokemon partner : pokemon) {
            if (!managedPokemon.containsKey(partner.getUuid()) || partner.getEntity() == null) continue;
            if (!within(partner.getEntity().getPos(), household, radius)) recallIfManaged(partner.getUuid());
        }
    }

    private void recallManagedFor(UUID ownerId) {
        List<UUID> ids = managedPokemon.entrySet().stream().filter(entry -> entry.getValue().ownerId().equals(ownerId)).map(Map.Entry::getKey).toList();
        ids.forEach(this::recallIfManaged);
    }

    private void recallOfflineOwners(Set<UUID> onlineOwners) {
        List<UUID> ids = managedPokemon.entrySet().stream().filter(entry -> !onlineOwners.contains(entry.getValue().ownerId())).map(Map.Entry::getKey).toList();
        ids.forEach(this::recallIfManaged);
        ownerActive.keySet().removeIf(owner -> !onlineOwners.contains(owner));
    }

    private void recallIfManaged(UUID pokemonId) {
        ManagedPokemon managed = managedPokemon.remove(pokemonId);
        if (managed == null) return;
        Pokemon pokemon = managed.pokemon();
        if (pokemon.getEntity() != null) pokemon.getEntity().recallWithAnimation();
        metrics.increment("household.recall");
    }

    private boolean sameDimension(ServerPlayerEntity player, HouseholdState household) { return player.getServerWorld().getRegistryKey().getValue().toString().equals(household.anchor().dimensionId()); }
    private boolean within(Vec3d pos, HouseholdState household, int radius) {
        if (radius <= 0) return false;
        Vec3d center = anchor(household); double dx = Math.abs(pos.x - center.x), dy = Math.abs(pos.y - center.y), dz = Math.abs(pos.z - center.z);
        return switch (households.boundary(household)) {
            case "cuboid" -> dx <= radius && dy <= radius && dz <= radius;
            case "cylindrical" -> dx * dx + dz * dz <= (double) radius * radius;
            default -> dx * dx + dy * dy + dz * dz <= (double) radius * radius;
        };
    }
    private Vec3d spawnPosition(ServerWorld world, HouseholdState household, UUID pokemonId, int population) {
        int ring = Math.max(2, Math.min(6, population + 1)); int hash = pokemonId.hashCode();
        int dx = Math.floorMod(hash, ring * 2 + 1) - ring, dz = Math.floorMod(hash >>> 8, ring * 2 + 1) - ring;
        int x = household.anchor().x() + dx, z = household.anchor().z() + dz;
        int y = Math.max(household.anchor().y(), world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z));
        return Vec3d.ofBottomCenter(new BlockPos(x, y, z));
    }
    private static Vec3d anchor(HouseholdState household) { return new Vec3d(household.anchor().x() + 0.5, household.anchor().y() + 0.5, household.anchor().z() + 0.5); }
    private record ManagedPokemon(UUID ownerId, Pokemon pokemon) {}
}
