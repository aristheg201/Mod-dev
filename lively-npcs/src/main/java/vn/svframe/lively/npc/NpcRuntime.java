package vn.svframe.lively.npc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svframe.lively.dialogue.DialogueService;
import vn.svframe.lively.model.NpcState;
import vn.svframe.lively.persistence.NpcStateRegistry;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Command-created NPC runtime. Cognition survives despawn/body changes.
 *
 * <p>Definition writes are coalesced on a dedicated single I/O worker. Runtime commands therefore never rewrite the
 * whole NPC definition file on the Minecraft server thread. The newest captured definition snapshot always wins; a
 * shutdown flush provides the durability barrier.</p>
 */
public final class NpcRuntime implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("livelynpcs");

    public enum Flag { AI, INVULNERABLE, GRAVITY, SILENT, NAME_VISIBLE }

    private final ConcurrentHashMap<UUID, NpcDefinition> definitions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, NpcBody> bodies = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<NpcDefinition.BodyType, NpcBodyProvider> providers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> entityToNpc = new ConcurrentHashMap<>();
    private final NpcDefinitionStore store;
    private final NpcStateRegistry states;
    private final ExecutorService definitionIo;
    private final Object persistLock = new Object();

    private volatile Map<UUID, NpcDefinition> pendingDefinitionSnapshot;
    private volatile CompletableFuture<Void> persistenceTail = CompletableFuture.completedFuture(null);
    private volatile boolean closed;

    public NpcRuntime(NpcDefinitionStore store, NpcStateRegistry states) {
        this.store = Objects.requireNonNull(store);
        this.states = Objects.requireNonNull(states);
        this.definitionIo = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Lively-NpcDefinition-IO");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void registerProvider(NpcDefinition.BodyType type, NpcBodyProvider provider) {
        providers.put(Objects.requireNonNull(type), Objects.requireNonNull(provider));
    }

    public boolean supports(NpcDefinition.BodyType type) { return providers.containsKey(type); }

    /** Initial world load. This is called once during server-session startup, before the runtime is advertised ready. */
    public void load() {
        definitions.clear();
        definitions.putAll(store.loadAll());
        for (NpcDefinition definition : definitions.values()) {
            NpcState state = states.getOrCreate(definition.id(), definition.name(), definition.role());
            if (!state.name().equals(definition.name())) state.rename(definition.name());
            if (!state.role().equals(definition.role())) state.setRole(definition.role());
        }
    }

    public void restoreSpawned(MinecraftServer server) {
        definitions.values().stream().filter(NpcDefinition::spawned).forEach(definition -> {
            try {
                if (!spawn(server, definition.id())) {
                    LOGGER.warn("Unable to restore spawned Lively NPC {} ({}) because its body provider is unavailable or rejected the spawn",
                            definition.id(), definition.bodyType());
                }
            } catch (RuntimeException error) {
                LOGGER.error("Failed to restore spawned Lively NPC {} ({})", definition.id(), definition.bodyType(), error);
            }
        });
    }

    public NpcDefinition create(String name, String role, NpcDefinition.BodyType type, String bodyKey, String skinName,
                                String world, Vec3d pos, float yaw, float pitch) {
        ensureOpen();
        if (!providers.containsKey(type)) throw new IllegalStateException("body provider unavailable: " + type);
        UUID id = UUID.randomUUID();
        NpcDefinition definition = new NpcDefinition(id, name, role, type, bodyKey, skinName, world, pos.x, pos.y, pos.z, yaw, pitch,
                false, true, true, true, false, true, Map.of());
        definitions.put(id, definition);
        states.getOrCreate(id, name, role);
        persist();
        return definition;
    }

    public Optional<NpcDefinition> get(UUID id) { return Optional.ofNullable(definitions.get(id)); }
    public Map<UUID, NpcDefinition> snapshot() { return Map.copyOf(definitions); }
    public Optional<NpcBody> body(UUID id) { return Optional.ofNullable(bodies.get(id)); }
    public Optional<Vec3d> position(UUID id) {
        NpcBody body = bodies.get(id);
        return body != null ? body.position() : get(id).map(d -> new Vec3d(d.x(), d.y(), d.z()));
    }
    public Optional<String> worldKey(UUID id) {
        NpcBody body = bodies.get(id);
        return body != null ? body.worldKey() : get(id).map(NpcDefinition::world);
    }
    public Optional<UUID> npcForEntity(UUID entityUuid) { return Optional.ofNullable(entityToNpc.get(entityUuid)); }

    public boolean remove(MinecraftServer server, UUID id) {
        ensureOpen();
        NpcBody body = bodies.remove(id);
        unregisterBody(body);
        if (body != null) body.despawn(server);
        NpcDefinition removed = definitions.remove(id);
        if (removed == null) return false;
        persist();
        return true;
    }

    public boolean spawn(MinecraftServer server, UUID id) {
        ensureOpen();
        NpcDefinition definition = definitions.get(id);
        if (definition == null) return false;
        NpcBodyProvider provider = providers.get(definition.bodyType());
        if (provider == null) return false;
        NpcBody body = bodies.computeIfAbsent(id, ignored -> provider.create(definition));
        if (!body.spawned()) body.spawn(server, definition);
        registerBody(body);
        definitions.put(id, definition.withSpawned(true));
        persist();
        return true;
    }

    public boolean despawn(MinecraftServer server, UUID id) {
        ensureOpen();
        NpcDefinition definition = definitions.get(id);
        if (definition == null) return false;
        NpcBody body = bodies.get(id);
        unregisterBody(body);
        if (body != null && body.spawned()) body.despawn(server);
        definitions.put(id, definition.withSpawned(false));
        persist();
        return true;
    }

    public boolean teleport(MinecraftServer server, UUID id, String world, Vec3d position, float yaw, float pitch) {
        ensureOpen();
        NpcDefinition definition = definitions.get(id);
        if (definition == null) return false;
        NpcDefinition moved = definition.withPosition(world, position.x, position.y, position.z, yaw, pitch);
        definitions.put(id, moved);
        NpcBody body = bodies.get(id);
        if (body != null && body.spawned()) {
            unregisterBody(body);
            body.teleport(server, world, position, yaw, pitch);
            if (!body.spawned()) {
                bodies.remove(id);
                spawn(server, id);
            } else {
                registerBody(body);
            }
        }
        persist();
        return true;
    }

    /** Used by navigation. Position is persisted by periodic checkpoints, never every animation step. */
    public boolean moveStep(MinecraftServer server, UUID id, String world, Vec3d position, float yaw, float pitch) {
        NpcDefinition definition = definitions.get(id);
        NpcBody body = bodies.get(id);
        if (definition == null || body == null || !body.spawned()) return false;
        body.moveStep(server, world, position, yaw, pitch);
        registerBody(body);
        definitions.put(id, definition.withPosition(world, position.x, position.y, position.z, yaw, pitch));
        return true;
    }

    /** Captures the newest definition state and schedules a coalesced asynchronous write. */
    public void checkpoint() { persist(); }

    public boolean changeBody(MinecraftServer server, UUID id, NpcDefinition.BodyType type, String key, String skin) {
        ensureOpen();
        NpcDefinition old = definitions.get(id);
        if (old == null || !providers.containsKey(type)) return false;
        boolean wasSpawned = old.spawned();
        NpcBody body = bodies.remove(id);
        unregisterBody(body);
        if (body != null) body.despawn(server);
        definitions.put(id, old.withBody(type, key, skin).withSpawned(wasSpawned));
        if (wasSpawned) spawn(server, id);
        persist();
        return true;
    }

    public boolean setSkin(MinecraftServer server, UUID id, String source) {
        ensureOpen();
        NpcDefinition old = definitions.get(id);
        if (old == null || old.bodyType() != NpcDefinition.BodyType.PLAYER) return false;
        boolean wasSpawned = old.spawned();
        NpcBody body = bodies.remove(id);
        unregisterBody(body);
        if (body != null) body.despawn(server);
        definitions.put(id, old.withSkin(source).withSpawned(wasSpawned));
        if (wasSpawned) spawn(server, id);
        persist();
        return true;
    }

    public boolean rename(UUID id, String name, String role) {
        ensureOpen();
        NpcDefinition old = definitions.get(id);
        if (old == null) return false;
        definitions.put(id, old.withNameRole(name, role));
        states.get(id).ifPresent(state -> {
            state.rename(name);
            state.setRole(role);
        });
        persist();
        return true;
    }

    public boolean setFlag(UUID id, Flag flag, boolean value) {
        ensureOpen();
        NpcDefinition old = definitions.get(id);
        if (old == null) return false;
        boolean ai = old.aiEnabled(), inv = old.invulnerable(), gravity = old.gravity(), silent = old.silent(), name = old.nameVisible();
        switch (flag) {
            case AI -> ai = value;
            case INVULNERABLE -> inv = value;
            case GRAVITY -> gravity = value;
            case SILENT -> silent = value;
            case NAME_VISIBLE -> name = value;
        }
        definitions.put(id, old.withFlags(ai, inv, gravity, silent, name));
        persist();
        return true;
    }

    public boolean setMetadata(UUID id, String key, String value) {
        ensureOpen();
        NpcDefinition old = definitions.get(id);
        if (old == null) return false;
        definitions.put(id, old.withMetadata(key, value));
        persist();
        return true;
    }

    public boolean setTrait(UUID id, String trait, double value) {
        NpcState state = states.get(id).orElse(null);
        if (state == null) return false;
        state.setTrait(trait, value);
        states.save(id);
        return true;
    }

    public boolean setNeed(UUID id, String need, double value) {
        NpcState state = states.get(id).orElse(null);
        if (state == null) return false;
        state.setNeed(need, value);
        states.save(id);
        return true;
    }

    public boolean lookAt(MinecraftServer server, UUID id, Vec3d target) {
        NpcBody body = bodies.get(id);
        if (body == null || !body.spawned()) return false;
        body.lookAt(server, target);
        return true;
    }

    public boolean interact(ServerPlayerEntity player, UUID entityUuid, DialogueService dialogues) {
        UUID npcId = entityToNpc.get(entityUuid);
        if (npcId == null) return false;
        NpcDefinition definition = definitions.get(npcId);
        if (definition == null) return false;
        NpcState state = states.get(npcId).orElse(null);
        NpcIdentityPolicy.Resolution identity = NpcIdentityPolicy.resolve(definition, state, player);
        dialogues.start(player, npcId, identity.displayName(), definition.role());
        if (state != null) {
            state.remember("physical_interaction", Map.of("player", player.getUuid().toString()), 0.20D, 1D);
            if (identity.revealed()) {
                state.remember("identity_revealed",
                        Map.of("player", player.getUuid().toString(), "identity", identity.displayName()), .72D, 1D);
            }
        }
        NpcBody body = bodies.get(npcId);
        if (body != null) body.onInteract(player);
        return true;
    }

    public void tick(MinecraftServer server) {
        for (Map.Entry<UUID, NpcBody> entry : bodies.entrySet()) {
            NpcDefinition definition = definitions.get(entry.getKey());
            if (definition == null || !definition.spawned()) continue;
            entry.getValue().tick(server, definition);
            registerBody(entry.getValue());
        }
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        for (Map.Entry<UUID, NpcBody> entry : bodies.entrySet()) {
            NpcDefinition definition = definitions.get(entry.getKey());
            if (definition != null && definition.spawned()) entry.getValue().onViewerJoin(player, definition);
        }
    }

    /** Despawns physical bodies and captures a final definition snapshot. Call {@link #flushDefinitions()} afterwards. */
    public void shutdown(MinecraftServer server) {
        checkpoint();
        bodies.values().forEach(body -> {
            try { body.despawn(server); }
            catch (RuntimeException error) { LOGGER.warn("Failed to despawn Lively NPC body {} during shutdown", body.npcId(), error); }
        });
        bodies.clear();
        entityToNpc.clear();
    }

    /**
     * Durability barrier used during server shutdown and production tests.
     * It does not return until the observed tail is still the current tail and no newer snapshot remains pending.
     */
    public CompletableFuture<Void> flushDefinitions() {
        persist();
        return awaitStableDefinitionTail();
    }

    private CompletableFuture<Void> awaitStableDefinitionTail() {
        final CompletableFuture<Void> observed;
        synchronized (persistLock) {
            if (pendingDefinitionSnapshot != null && persistenceTail.isDone()) schedulePersistLocked();
            observed = persistenceTail;
        }
        return observed.thenCompose(ignored -> {
            synchronized (persistLock) {
                if (pendingDefinitionSnapshot == null && persistenceTail == observed) {
                    return CompletableFuture.completedFuture(null);
                }
            }
            return awaitStableDefinitionTail();
        });
    }

    private void persist() {
        if (closed) return;
        Map<UUID, NpcDefinition> snapshot = Map.copyOf(definitions);
        synchronized (persistLock) {
            pendingDefinitionSnapshot = snapshot;
            if (persistenceTail.isDone()) schedulePersistLocked();
        }
    }

    /** Called only while persistLock is held. Each worker consumes the latest snapshot, collapsing intermediate edits. */
    private void schedulePersistLocked() {
        Map<UUID, NpcDefinition> snapshot = pendingDefinitionSnapshot;
        pendingDefinitionSnapshot = null;
        if (snapshot == null || closed) return;
        persistenceTail = CompletableFuture.runAsync(() -> store.saveAll(snapshot), definitionIo)
                .whenComplete((ignored, error) -> {
                    if (error != null) LOGGER.error("Lively NPC definition persistence failed", unwrap(error));
                    synchronized (persistLock) {
                        if (pendingDefinitionSnapshot != null && !closed) schedulePersistLocked();
                    }
                });
    }

    private static Throwable unwrap(Throwable error) {
        Throwable result = error;
        while (result instanceof CompletionException && result.getCause() != null) result = result.getCause();
        return result;
    }

    private void registerBody(NpcBody body) {
        if (body != null) body.entityUuid().ifPresent(uuid -> entityToNpc.put(uuid, body.npcId()));
    }

    private void unregisterBody(NpcBody body) {
        if (body != null) body.entityUuid().ifPresent(entityToNpc::remove);
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("NPC runtime is closed");
    }

    @Override
    public void close() {
        closed = true;
        definitionIo.shutdown();
    }
}
