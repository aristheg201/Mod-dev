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
    private final Set<UUID> managedPokemon = new HashSet<>();
    private final Map<UUID, Boolean> ownerActive = new HashMap<>();

    public HouseholdMaterializationService(MinecraftServer server, HouseholdService households,
                                           RelationshipService relationships, RuntimeMetrics metrics) {
        this.server = server;
        this.households = households;
        this.relationships = relationships;
        this.metrics = metrics;
    }

    public void tick() {
        long started = System.nanoTime();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            households.get(player.getUuid()).ifPresent(household -> evaluateOwner(player, household));
        }
        cleanupOrphans();
        metrics.gauge("household.materialized", managedPokemon.size());
        metrics.add("household.tick_nanos", System.nanoTime() - started);
        metrics.increment("household.tick_count");
    }

    private void evaluateOwner(ServerPlayerEntity owner, HouseholdState household) {
        boolean wasActive = ownerActive.getOrDefault(owner.getUuid(), false);
        int threshold = wasActive ? households.deactivationRadius(household) : households.activeRadius(household);
        boolean shouldBeActive = sameDimension(owner, household) && within(owner.getPos(), household, threshold);
        ownerActive.put(owner.getUuid(), shouldBeActive);
        if (!shouldBeActive) {
            recallManagedFor(owner.getUuid(), household);
            return;
        }

        List<Pokemon> partnerPokemon = new ArrayList<>();
        for (var relationship : relationships.partners(owner.getUuid())) {
            if (!household.householdId().equals(relationship.householdId())) continue;
            findOwned(owner, relationship.key().pokemonId()).ifPresent(partnerPokemon::add);
        }
        int maximum = households.maxMaterializedPartners(household);
        List<HouseholdMaterializationPlanner.Candidate> candidates = new ArrayList<>();
        for (Pokemon pokemon : partnerPokemon) {
            boolean managed = managedPokemon.contains(pokemon.getUuid());
            double distance = pokemon.getEntity() == null ? 0.0 : pokemon.getEntity().getPos().squaredDistanceTo(anchor(household));
            candidates.add(new HouseholdMaterializationPlanner.Candidate(pokemon.getUuid(), distance, managed));
        }
        Set<UUID> selected = new HashSet<>(planner.select(candidates, maximum));
        for (Pokemon pokemon : partnerPokemon) {
            if (selected.contains(pokemon.getUuid())) materialize(owner, household, pokemon, selected.size());
            else if (managedPokemon.contains(pokemon.getUuid())) recall(pokemon);
        }
        enforceBoundary(household, partnerPokemon);
    }

    private void materialize(ServerPlayerEntity owner, HouseholdState household, Pokemon pokemon, int population) {
        if (pokemon.getEntity() != null) return;
        ServerWorld world = owner.getServerWorld();
        Vec3d position = spawnPosition(world, household, pokemon.getUuid(), Math.max(1, population));
        var entity = pokemon.sendOut(world, position, null, sent -> Unit.INSTANCE);
        if (entity != null) {
            managedPokemon.add(pokemon.getUuid());
            metrics.increment("household.materialize");
        }
    }

    private void enforceBoundary(HouseholdState household, List<Pokemon> pokemon) {
        int radius = households.deactivationRadius(household);
        for (Pokemon partner : pokemon) {
            if (!managedPokemon.contains(partner.getUuid()) || partner.getEntity() == null) continue;
            if (!within(partner.getEntity().getPos(), household, radius)) recall(partner);
        }
    }

    private void recallManagedFor(UUID ownerId, HouseholdState household) {
        for (var relationship : relationships.partners(ownerId)) {
            if (!household.householdId().equals(relationship.householdId()) || !managedPokemon.contains(relationship.key().pokemonId())) continue;
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerId);
            if (owner != null) findOwned(owner, relationship.key().pokemonId()).ifPresent(this::recall);
        }
    }

    private void recall(Pokemon pokemon) {
        if (pokemon.getEntity() != null) pokemon.getEntity().recallWithAnimation();
        if (managedPokemon.remove(pokemon.getUuid())) metrics.increment("household.recall");
    }

    private void cleanupOrphans() {
        managedPokemon.removeIf(id -> server.getPlayerManager().getPlayerList().stream().noneMatch(player ->
                findOwned(player, id).map(pokemon -> pokemon.getEntity() != null).orElse(false)));
    }

    private java.util.Optional<Pokemon> findOwned(ServerPlayerEntity player, UUID pokemonId) {
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) if (pokemon.getUuid().equals(pokemonId)) return java.util.Optional.of(pokemon);
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getPC(player)) if (pokemon.getUuid().equals(pokemonId)) return java.util.Optional.of(pokemon);
        return java.util.Optional.empty();
    }

    private boolean sameDimension(ServerPlayerEntity player, HouseholdState household) {
        return player.getServerWorld().getRegistryKey().getValue().toString().equals(household.anchor().dimensionId());
    }

    private boolean within(Vec3d pos, HouseholdState household, int radius) {
        if (radius <= 0) return false;
        Vec3d center = anchor(household);
        double dx = Math.abs(pos.x - center.x);
        double dy = Math.abs(pos.y - center.y);
        double dz = Math.abs(pos.z - center.z);
        return switch (households.boundary(household)) {
            case "cuboid" -> dx <= radius && dy <= radius && dz <= radius;
            case "cylindrical" -> dx * dx + dz * dz <= (double) radius * radius;
            default -> dx * dx + dy * dy + dz * dz <= (double) radius * radius;
        };
    }

    private Vec3d spawnPosition(ServerWorld world, HouseholdState household, UUID pokemonId, int population) {
        int ring = Math.max(2, Math.min(6, population + 1));
        int hash = pokemonId.hashCode();
        int dx = Math.floorMod(hash, ring * 2 + 1) - ring;
        int dz = Math.floorMod(hash >>> 8, ring * 2 + 1) - ring;
        int x = household.anchor().x() + dx;
        int z = household.anchor().z() + dz;
        int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        int y = Math.max(household.anchor().y(), top);
        return Vec3d.ofBottomCenter(new BlockPos(x, y, z));
    }

    private static Vec3d anchor(HouseholdState household) {
        return new Vec3d(household.anchor().x() + 0.5, household.anchor().y() + 0.5, household.anchor().z() + 0.5);
    }
}
