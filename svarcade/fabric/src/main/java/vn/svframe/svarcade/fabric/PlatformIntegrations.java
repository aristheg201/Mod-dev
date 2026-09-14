package vn.svframe.svarcade.fabric;

import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.fabric.api.SVArcadeIntegration;
import vn.svframe.svarcade.reward.RewardProvider;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.loadout.*;

/** Runtime-validated optional mod adapters. A loaded mod is not a capability until its adapter resolves. */
final class PlatformIntegrations implements AutoCloseable {
    static final String ENTRYPOINT = "svarcade:integration";
    private static final EnumSet<PlayerStateSnapshot.Field> PROTECTED_FIELDS = EnumSet.allOf(PlayerStateSnapshot.Field.class);
    private final MinecraftServer server;
    private final IntegrationSettings settings;
    private final PlayerStateProtection playerState;
    private final CobblemonPartyBridge cobblemon;
    private final PlaceholderBridge placeholders;
    private final List<GenericGameRuntime.SessionInitializer> sessionInitializers;
    private final Registry<RewardProvider> rewardProviders;
    private final Set<String> capabilities;
    private final Map<String,String> unavailable;

    private PlatformIntegrations(MinecraftServer server, IntegrationSettings settings, PlayerStateProtection playerState,
                                 CobblemonPartyBridge cobblemon, PlaceholderBridge placeholders,
                                 List<GenericGameRuntime.SessionInitializer> sessionInitializers, Registry<RewardProvider> rewardProviders,
                                 Set<String> capabilities, Map<String,String> unavailable) {
        this.server = Objects.requireNonNull(server); this.settings = Objects.requireNonNull(settings); this.playerState = Objects.requireNonNull(playerState);
        this.cobblemon = cobblemon; this.placeholders = placeholders; this.sessionInitializers = List.copyOf(sessionInitializers);
        this.rewardProviders = Objects.requireNonNull(rewardProviders); this.capabilities = Set.copyOf(capabilities); this.unavailable = Map.copyOf(unavailable);
    }

    static PlatformIntegrations discover(MinecraftServer server, IntegrationSettings settings, PlayerStateProtection playerState) {
        Objects.requireNonNull(settings); Objects.requireNonNull(playerState);
        FabricLoader loader = FabricLoader.getInstance(); Set<String> capabilities = new LinkedHashSet<>(); Map<String,String> unavailable = new LinkedHashMap<>();
        Map<Id,RewardProvider> rewards = new LinkedHashMap<>(); List<GenericGameRuntime.SessionInitializer> initializers = new ArrayList<>();
        CobblemonPartyBridge cobblemon = null; PlaceholderBridge placeholders = null;

        if (settings.cobblemonEnabled() && loader.isModLoaded("cobblemon")) {
            if (!IntegrationDetector.supportedCobblemon(loader.getModContainer("cobblemon"))) unavailable.put("cobblemon", "requires >=1.8.1 <1.9.0");
            else {
                Optional<CobblemonPartyBridge> bridge = CobblemonPartyBridge.discover();
                if (bridge.isPresent()) { cobblemon = bridge.get(); capabilities.add("cobblemon"); }
                else unavailable.put("cobblemon", "1.8.1 API boundary unresolved");
            }
        }
        if (settings.luckPermsEnabled() && IntegrationDetector.installed("luckperms")) {
            Optional<LuckPermsRewardProvider> provider = LuckPermsRewardProvider.discover();
            if (provider.isPresent()) { rewards.put(settings.permissionRewardProvider(), provider.get()); capabilities.add("luckperms"); }
            else unavailable.put("luckperms", "LuckPerms API 5.x unavailable");
        }
        if (settings.placeholderEnabled() && IntegrationDetector.installed("placeholder")) {
            Optional<PlaceholderBridge> bridge = PlaceholderBridge.discover(settings.placeholderNamespace());
            if (bridge.isPresent()) { placeholders = bridge.get(); capabilities.add("placeholder"); }
            else unavailable.put("placeholder", "Text Placeholder API 2.4.x unavailable");
        }

        for (EntrypointContainer<SVArcadeIntegration> container : loader.getEntrypointContainers(ENTRYPOINT, SVArcadeIntegration.class)) {
            String provider = container.getProvider().getMetadata().getId();
            try {
                SVArcadeIntegration integration = container.getEntrypoint(); Set<String> advertised = validateCapabilities(integration.capabilities());
                Set<String> accepted = new LinkedHashSet<>(); for (String capability : advertised) if (enabled(settings, capability)) accepted.add(capability);
                if (accepted.isEmpty()) continue;
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
                for (String capability : accepted) if (capabilities.contains(capability)) throw new IllegalStateException("Duplicate integration capability: " + capability);
                if (accepted.contains("svquest") && !stagedRewards.containsKey(settings.svQuestRewardProvider()))
                    throw new IllegalStateException("SVQuest integration did not register configured reward provider " + settings.svQuestRewardProvider());
                rewards.putAll(stagedRewards); initializers.addAll(stagedInitializers); capabilities.addAll(accepted);
            } catch (RuntimeException | LinkageError failure) { unavailable.put("entrypoint/" + provider, concise(failure)); }
        }

        if (settings.economyEnabled() && IntegrationDetector.installed("economy") && !capabilities.contains("economy")) unavailable.put("economy", "provider lacks nonblocking exactly-once transaction API");
        if (settings.svQuestEnabled() && IntegrationDetector.installed("svquest") && !capabilities.contains("svquest")) unavailable.put("svquest", "no valid SVArcade integration entrypoint");
        if (settings.svFrameEnabled() && IntegrationDetector.installed("svframe") && !capabilities.contains("svframe")) unavailable.put("svframe", "no valid SVArcade integration entrypoint");
        return new PlatformIntegrations(server, settings, playerState, cobblemon, placeholders, initializers, new Registry<>(rewards), capabilities, unavailable);
    }

    private static boolean enabled(IntegrationSettings settings, String capability) {
        return switch (capability) {
            case "cobblemon" -> settings.cobblemonEnabled();
            case "luckperms" -> settings.luckPermsEnabled();
            case "placeholder" -> settings.placeholderEnabled();
            case "economy" -> settings.economyEnabled();
            case "svquest" -> settings.svQuestEnabled();
            case "svframe" -> settings.svFrameEnabled();
            default -> true;
        };
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

    /** Fresh-session hook. Player state is captured before any platform integration can mutate it. */
    void initializeSession(GenericSession session) {
        List<UUID> captured = new ArrayList<>();
        try {
            for (Participant participant : session.participants().values()) if (participant.kind() == Participant.Kind.PLAYER) {
                playerState.capture(participant.id(), PROTECTED_FIELDS); captured.add(participant.id()); bindPlayerCleanup(session, participant.id());
            }
            placeConfiguredStages(session);
            if (cobblemon != null && session.definition().systems().stream().anyMatch(spec -> spec.id().equals(LoadoutSystem.ID))) importCobblemon(session);
            for (GenericGameRuntime.SessionInitializer initializer : sessionInitializers) initializer.initialize(session);
        } catch (RuntimeException failure) {
            for (int i = captured.size() - 1; i >= 0; i--) if (server.getPlayerManager().getPlayer(captured.get(i)) != null) playerState.restore(captured.get(i));
            throw failure;
        }
    }

    private void placeConfiguredStages(GenericSession session) {
        String arenaId = session.lease().arena().arena(); Node arena = session.definition().arenas().get(arenaId);
        if (arena == null || !arena.has("player_stages")) return;
        String worldId = arena.string("world"); ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(worldId)));
        if (world == null) throw new IllegalStateException("Arena world unavailable: " + worldId);
        Node stages = arena.node("player_stages");
        for (Participant participant : session.participants().values()) {
            if (participant.kind() != Participant.Kind.PLAYER || !stages.has(participant.team())) continue;
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(participant.id());
            if (player == null) throw new IllegalStateException("Staged participant is offline: " + participant.id());
            List<?> raw = stages.list(participant.team());
            if (raw.size() != 5) throw stages.error(participant.team(), "Expected [x,y,z,yaw,pitch]");
            double x = number(raw.get(0), "x"), y = number(raw.get(1), "y"), z = number(raw.get(2), "z");
            float yaw = (float) number(raw.get(3), "yaw"), pitch = (float) number(raw.get(4), "pitch");
            if (!player.teleport(world, x, y, z, Set.of(), yaw, pitch)) throw new IllegalStateException("Stage teleport rejected: " + participant.id());
        }
    }
    private static double number(Object value, String field) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Stage " + field + " must be numeric");
        double result = number.doubleValue(); if (!Double.isFinite(result) || Math.abs(result) > 30_000_000) throw new IllegalArgumentException("Stage " + field + " outside world bounds"); return result;
    }

    /** Recovery hook: ownership is reattached without recapturing already-mutated live state. */
    void bindRecoveredState(GenericSession session, Map<UUID,UUID> owners) {
        for (Participant participant : session.participants().values()) if (participant.kind() == Participant.Kind.PLAYER
                && session.id().equals(owners.get(participant.id())) && playerState.protectedPlayer(participant.id())) bindPlayerCleanup(session, participant.id());
    }

    private void bindPlayerCleanup(GenericSession session, UUID player) {
        session.resources().own("player-state/" + player, () -> {
            if (!playerState.protectedPlayer(player)) return;
            if (server.getPlayerManager().getPlayer(player) == null) return;
            if (!playerState.restore(player)) throw new IllegalStateException("Player state restore failed: " + player);
        });
    }

    private void importCobblemon(GenericSession session) {
        LoadoutAccess loadouts = session.services().require(LoadoutAccess.ACCESS); List<StateChange> applied = new ArrayList<>();
        try {
            for (Participant participant : session.participants().values()) {
                if (participant.kind() != Participant.Kind.PLAYER) continue;
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(participant.id());
                if (player == null) throw new IllegalStateException("Cobblemon loadout owner is offline: " + participant.id());
                for (LoadoutAccess.Snapshot snapshot : cobblemon.snapshots(player, settings.cobblemonMaxParty(), settings.cobblemonHeldItem(), settings.cobblemonAspects(), settings.cobblemonMoves())) {
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
