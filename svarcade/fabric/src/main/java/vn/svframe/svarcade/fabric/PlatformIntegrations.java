package vn.svframe.svarcade.fabric;

import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.fabric.api.SVArcadeIntegration;
import vn.svframe.svarcade.reward.RewardProvider;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.loadout.*;

/** Runtime-validated optional mod adapters. A loaded mod is not a capability until its adapter resolves. */
final class PlatformIntegrations implements AutoCloseable {
    static final String ENTRYPOINT = "svarcade:integration";
    private final MinecraftServer server;
    private final CobblemonPartyBridge cobblemon;
    private final PlaceholderBridge placeholders;
    private final List<GenericGameRuntime.SessionInitializer> sessionInitializers;
    private final Registry<RewardProvider> rewardProviders;
    private final Set<String> capabilities;
    private final Map<String,String> unavailable;

    private PlatformIntegrations(MinecraftServer server, CobblemonPartyBridge cobblemon, PlaceholderBridge placeholders,
                                 List<GenericGameRuntime.SessionInitializer> sessionInitializers, Registry<RewardProvider> rewardProviders,
                                 Set<String> capabilities, Map<String,String> unavailable) {
        this.server = Objects.requireNonNull(server); this.cobblemon = cobblemon; this.placeholders = placeholders;
        this.sessionInitializers = List.copyOf(sessionInitializers); this.rewardProviders = Objects.requireNonNull(rewardProviders);
        this.capabilities = Set.copyOf(capabilities); this.unavailable = Map.copyOf(unavailable);
    }

    static PlatformIntegrations discover(MinecraftServer server) {
        FabricLoader loader = FabricLoader.getInstance(); Set<String> capabilities = new LinkedHashSet<>(); Map<String,String> unavailable = new LinkedHashMap<>();
        Map<Id,RewardProvider> rewards = new LinkedHashMap<>(); List<GenericGameRuntime.SessionInitializer> initializers = new ArrayList<>();
        CobblemonPartyBridge cobblemon = null; PlaceholderBridge placeholders = null;
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
            if (provider.isPresent()) { rewards.put(Id.of("svarcade:permission"), provider.get()); capabilities.add("luckperms"); }
            else unavailable.put("luckperms", "LuckPerms API 5.x unavailable");
        }
        if (IntegrationDetector.installed("placeholder")) {
            Optional<PlaceholderBridge> bridge = PlaceholderBridge.discover();
            if (bridge.isPresent()) { placeholders = bridge.get(); capabilities.add("placeholder"); }
            else unavailable.put("placeholder", "Text Placeholder API 2.4.x unavailable");
        }

        for (EntrypointContainer<SVArcadeIntegration> container : loader.getEntrypointContainers(ENTRYPOINT, SVArcadeIntegration.class)) {
            String provider = container.getProvider().getMetadata().getId();
            try {
                SVArcadeIntegration integration = container.getEntrypoint(); Set<String> advertised = validateCapabilities(integration.capabilities());
                Map<Id,RewardProvider> stagedRewards = new LinkedHashMap<>(); List<GenericGameRuntime.SessionInitializer> stagedInitializers = new ArrayList<>();
                integration.register(new SVArcadeIntegration.Context() {
                    @Override public void rewardProvider(Id id, RewardProvider rewardProvider) {
                        Objects.requireNonNull(id); Objects.requireNonNull(rewardProvider);
                        if (rewards.containsKey(id) || stagedRewards.putIfAbsent(id, rewardProvider) != null) throw new IllegalStateException("Duplicate reward provider: " + id);
                    }
                    @Override public void sessionInitializer(GenericGameRuntime.SessionInitializer initializer) {
                        if (stagedInitializers.size() >= 32) throw new IllegalStateException("Integration initializer limit"); stagedInitializers.add(Objects.requireNonNull(initializer));
                    }
                });
                for (String capability : advertised) if (capabilities.contains(capability)) throw new IllegalStateException("Duplicate integration capability: " + capability);
                rewards.putAll(stagedRewards); initializers.addAll(stagedInitializers); capabilities.addAll(advertised);
            } catch (RuntimeException | LinkageError failure) {
                unavailable.put("entrypoint/" + provider, concise(failure));
            }
        }

        if (IntegrationDetector.installed("economy") && !capabilities.contains("economy")) unavailable.put("economy", "provider lacks nonblocking exactly-once transaction API");
        for (String capability : List.of("svquest", "svframe")) if (IntegrationDetector.installed(capability) && !capabilities.contains(capability)) unavailable.put(capability, "no SVArcade integration entrypoint");
        return new PlatformIntegrations(server, cobblemon, placeholders, initializers, new Registry<>(rewards), capabilities, unavailable);
    }

    private static Set<String> validateCapabilities(Set<String> raw) {
        Objects.requireNonNull(raw); if (raw.isEmpty() || raw.size() > 16) throw new IllegalStateException("Integration capability count"); Set<String> result = new LinkedHashSet<>();
        for (String capability : raw) {
            if (capability == null || !capability.matches("[a-z0-9_.-]{1,64}") || !result.add(capability)) throw new IllegalStateException("Invalid integration capability: " + capability);
        }
        return Set.copyOf(result);
    }
    private static String concise(Throwable failure) { String text = failure.toString(); return text.length() <= 256 ? text : text.substring(0, 256); }

    void bindRuntime(GenericGameRuntime runtime) { if (placeholders != null) placeholders.register(runtime); }

    void initializeSession(GenericSession session) {
        if (cobblemon != null && session.definition().systems().stream().anyMatch(spec -> spec.id().equals(LoadoutSystem.ID))) importCobblemon(session);
        for (GenericGameRuntime.SessionInitializer initializer : sessionInitializers) initializer.initialize(session);
    }
    private void importCobblemon(GenericSession session) {
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
            for (int i = applied.size() - 1; i >= 0; i--) try { applied.get(i).rollback(); } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
    }

    Registry<RewardProvider> rewardProviders() { return rewardProviders; }
    Set<String> capabilities() { return capabilities; }
    Map<String,String> unavailable() { return unavailable; }
    @Override public void close() { if (placeholders != null) placeholders.close(); }
}
