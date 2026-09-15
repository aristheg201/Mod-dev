package vn.svframe.svrelationships.fabric.household;

import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svrelationships.fabric.config.ConfigService;
import vn.svframe.svrelationships.household.HouseholdAnchor;
import vn.svframe.svrelationships.household.HouseholdState;

import java.util.Optional;
import java.util.UUID;

public final class HouseholdService {
    private final HouseholdRepository repository;
    private final ConfigService config;

    public HouseholdService(HouseholdRepository repository, ConfigService config) {
        this.repository = repository;
        this.config = config;
    }

    public HouseholdState setAtPlayer(ServerPlayerEntity player) {
        var pos = player.getBlockPos();
        String dimension = player.getServerWorld().getRegistryKey().getValue().toString();
        HouseholdState state = new HouseholdState(
                repository.get(player.getUuid()).map(HouseholdState::householdId).orElseGet(UUID::randomUUID),
                player.getUuid(),
                new HouseholdAnchor(dimension, pos.getX(), pos.getY(), pos.getZ()),
                config.snapshot().defaultHouseholdProfile()
        );
        repository.put(state);
        return state;
    }

    public Optional<HouseholdState> get(UUID ownerId) {
        return repository.get(ownerId);
    }

    public boolean clear(UUID ownerId) {
        return repository.remove(ownerId).isPresent();
    }

    public int activeRadius(HouseholdState state) {
        var profile = config.snapshot().householdProfiles().get(state.profileId());
        return profile == null ? 0 : profile.activeRadius();
    }

    public int deactivationRadius(HouseholdState state) {
        var profile = config.snapshot().householdProfiles().get(state.profileId());
        return profile == null ? 0 : profile.deactivationRadius();
    }
}
