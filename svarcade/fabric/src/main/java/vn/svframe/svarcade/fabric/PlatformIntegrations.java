package vn.svframe.svarcade.fabric;

import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.reward.RewardProvider;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.loadout.*;

/** Runtime-validated optional mod adapters. A loaded mod is not a capability until its adapter resolves. */
final class PlatformIntegrations {
    private final MinecraftServer server;
    private final CobblemonPartyBridge cobblemon;
    private final Registry<RewardProvider> rewardProviders;
    private final Set<String> capabilities;
    private final Map<String,String> unavailable;

    private PlatformIntegrations(MinecraftServer server, CobblemonPartyBridge cobblemon, Registry<RewardProvider> rewardProviders,
                                 Set<String> capabilities, Map<String,String> unavailable) {
        this.server = Objects.requireNonNull(server); this.cobblemon = cobblemon; this.rewardProviders = Objects.requireNonNull(rewardProviders);
        this.capabilities = Set.copyOf(capabilities); this.unavailable = Map.copyOf(unavailable);
    }

    static PlatformIntegrations discover(MinecraftServer server) {
        FabricLoader loader = FabricLoader.getInstance(); Set<String> capabilities = new LinkedHashSet<>(); Map<String,String> unavailable = new LinkedHashMap<>();
        Registry.Builder<RewardProvider> rewards = new Registry.Builder<>(); CobblemonPartyBridge cobblemon = null;
        if (loader.isModLoaded("cobblemon")) {
            if (!IntegrationDetector.supportedCobblemon(loader.getModContainer("cobblemon"))) unavailable.put("cobblemon", "requires >=1.8.1");
            else {
                Optional<CobblemonPartyBridge> bridge = CobblemonPartyBridge.discover();
                if (bridge.isPresent()) { cobblemon = bridge.get(); capabilities.add("cobblemon"); }
                else unavailable.put("cobblemon", "1.8.1 API boundary unresolved");
            }
        }
        if (IntegrationDetector.installed("luckperms")) {
            Optional<LuckPermsRewardProvider> provider = LuckPermsRewardProvider.discover();
            if (provider.isPresent()) { rewards.add(Id.of("svarcade:permission"), provider.get()); capabilities.add("luckperms"); }
            else unavailable.put("luckperms", "LuckPerms API 5.x unavailable");
        }
        // Do not advertise these capabilities merely because a mod id exists. Their concrete adapters are added independently.
        for (String capability : List.of("economy", "placeholder", "svquest", "svframe")) {
            if (IntegrationDetector.installed(capability)) unavailable.put(capability, "adapter not resolved");
        }
        return new PlatformIntegrations(server, cobblemon, rewards.build(), capabilities, unavailable);
    }

    void initializeSession(GenericSession session) {
        if (cobblemon == null || session.definition().systems().stream().noneMatch(spec -> spec.id().equals(LoadoutSystem.ID))) return;
        LoadoutAccess loadouts = session.services().require(LoadoutAccess.ACCESS); List<StateChange> applied = new ArrayList<>();
        try {
            for (Participant participant : session.participants().values()) {
                if (participant.kind() != Participant.Kind.PLAYER) continue;
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(participant.id());
                if (player == null) throw new IllegalStateException("Cobblemon loadout owner is offline: " + participant.id());
                for (LoadoutAccess.Snapshot snapshot : cobblemon.snapshots(player, 64)) {
                    StateChange change = loadouts.prepareRegister(participant.id(), snapshot); change.apply(); applied.add(change);
                }
            }
            if (!applied.isEmpty()) session.changed();
        } catch (RuntimeException failure) {
            for (int i = applied.size() - 1; i >= 0; i--) {
                try { applied.get(i).rollback(); } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        }
    }

    Registry<RewardProvider> rewardProviders() { return rewardProviders; }
    Set<String> capabilities() { return capabilities; }
    Map<String,String> unavailable() { return unavailable; }
}
