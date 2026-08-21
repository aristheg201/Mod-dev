package vn.svframe.lively.npc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import vn.svframe.lively.dialogue.DialogueService;
import vn.svframe.lively.model.NpcState;
import vn.svframe.lively.persistence.NpcStateRegistry;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Command-created NPC runtime. Definitions are persistent; bodies are reconstructed when the server starts. */
public final class NpcRuntime {
    private final ConcurrentHashMap<UUID, NpcDefinition> definitions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, NpcBody> bodies = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<NpcDefinition.BodyType, NpcBodyProvider> providers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> entityToNpc = new ConcurrentHashMap<>();
    private final NpcDefinitionStore store;
    private final NpcStateRegistry states;

    public NpcRuntime(NpcDefinitionStore store, NpcStateRegistry states) {
        this.store = Objects.requireNonNull(store);
        this.states = Objects.requireNonNull(states);
    }

    public void registerProvider(NpcDefinition.BodyType type, NpcBodyProvider provider) {
        providers.put(Objects.requireNonNull(type), Objects.requireNonNull(provider));
    }

    public boolean supports(NpcDefinition.BodyType type) { return providers.containsKey(type); }

    public void load() {
        definitions.clear(); definitions.putAll(store.loadAll());
        for (NpcDefinition definition : definitions.values()) {
            NpcState state = states.getOrCreate(definition.id(), definition.name(), definition.role());
            if (!state.name().equals(definition.name())) state.rename(definition.name());
            if (!state.role().equals(definition.role())) state.setRole(definition.role());
        }
    }

    public void restoreSpawned(MinecraftServer server) {
        definitions.values().stream().filter(NpcDefinition::spawned).forEach(definition -> {
            try { spawn(server, definition.id()); } catch (RuntimeException ignored) {}
        });
    }

    public NpcDefinition create(String name, String role, NpcDefinition.BodyType type,
                                String bodyKey, String skinName, String world, Vec3d pos, float yaw, float pitch) {
        if (!providers.containsKey(type)) throw new IllegalStateException("body provider unavailable: " + type);
        UUID id = UUID.randomUUID();
        NpcDefinition definition = new NpcDefinition(id, name, role, type, bodyKey, skinName, world,
                pos.x, pos.y, pos.z, yaw, pitch, false, true, true, true, false, true, Map.of());
        definitions.put(id, definition); states.getOrCreate(id, name, role); persist(); return definition;
    }

    public Optional<NpcDefinition> get(UUID id) { return Optional.ofNullable(definitions.get(id)); }
    public Map<UUID, NpcDefinition> snapshot() { return Map.copyOf(definitions); }
    public Optional<UUID> npcForEntity(UUID entityUuid) { return Optional.ofNullable(entityToNpc.get(entityUuid)); }

    public boolean remove(MinecraftServer server, UUID id) {
        NpcBody body = bodies.remove(id); unregisterBody(body);
        if (body != null) body.despawn(server);
        NpcDefinition removed = definitions.remove(id);
        if (removed == null) return false;
        store.saveAll(definitions); return true;
    }

    public boolean spawn(MinecraftServer server, UUID id) {
        NpcDefinition definition = definitions.get(id); if (definition == null) return false;
        NpcBodyProvider provider = providers.get(definition.bodyType()); if (provider == null) return false;
        NpcBody body = bodies.computeIfAbsent(id, ignored -> provider.create(definition));
        if (!body.spawned()) body.spawn(server, definition);
        registerBody(body);
        definitions.put(id, definition.withSpawned(true)); persist(); return true;
    }

    public boolean despawn(MinecraftServer server, UUID id) {
        NpcDefinition definition = definitions.get(id); if (definition == null) return false;
        NpcBody body = bodies.get(id); unregisterBody(body);
        if (body != null && body.spawned()) body.despawn(server);
        definitions.put(id, definition.withSpawned(false)); persist(); return true;
    }

    public boolean teleport(MinecraftServer server, UUID id, String world, Vec3d position, float yaw, float pitch) {
        NpcDefinition definition = definitions.get(id); if (definition == null) return false;
        NpcDefinition moved = definition.withPosition(world, position.x, position.y, position.z, yaw, pitch);
        definitions.put(id, moved);
        NpcBody body = bodies.get(id);
        if (body != null && body.spawned()) {
            unregisterBody(body); body.teleport(server, world, position, yaw, pitch);
            if (!body.spawned()) { bodies.remove(id); spawn(server, id); } else registerBody(body);
        }
        persist(); return true;
    }

    public boolean changeBody(MinecraftServer server, UUID id, NpcDefinition.BodyType type, String key, String skin) {
        NpcDefinition old = definitions.get(id); if (old == null || !providers.containsKey(type)) return false;
        boolean wasSpawned = old.spawned(); NpcBody body = bodies.remove(id); unregisterBody(body);
        if (body != null) body.despawn(server);
        definitions.put(id, old.withBody(type, key, skin).withSpawned(wasSpawned));
        if (wasSpawned) spawn(server, id); persist(); return true;
    }

    public boolean rename(UUID id, String name, String role) {
        NpcDefinition old = definitions.get(id); if (old == null) return false;
        definitions.put(id, old.withNameRole(name, role));
        states.get(id).ifPresent(state -> { state.rename(name); state.setRole(role); });
        persist(); return true;
    }

    public boolean lookAt(MinecraftServer server, UUID id, Vec3d target) {
        NpcBody body = bodies.get(id); if (body == null || !body.spawned()) return false;
        body.lookAt(server, target); return true;
    }

    public boolean interact(ServerPlayerEntity player, UUID entityUuid, DialogueService dialogues) {
        UUID npcId = entityToNpc.get(entityUuid); if (npcId == null) return false;
        NpcDefinition definition = definitions.get(npcId); if (definition == null) return false;
        dialogues.start(player, npcId, definition.name(), definition.role());
        states.get(npcId).ifPresent(state -> state.remember("physical_interaction", Map.of("player", player.getUuid().toString()), 0.20D, 1D));
        NpcBody body = bodies.get(npcId); if (body != null) body.onInteract(player);
        return true;
    }

    public void tick(MinecraftServer server) {
        for (Map.Entry<UUID, NpcBody> entry : bodies.entrySet()) {
            NpcDefinition definition = definitions.get(entry.getKey());
            if (definition == null || !definition.spawned()) continue;
            entry.getValue().tick(server, definition); registerBody(entry.getValue());
        }
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        for (Map.Entry<UUID, NpcBody> entry : bodies.entrySet()) {
            NpcDefinition definition = definitions.get(entry.getKey());
            if (definition != null && definition.spawned()) entry.getValue().onViewerJoin(player, definition);
        }
    }

    public void shutdown(MinecraftServer server) {
        bodies.values().forEach(body -> body.despawn(server)); bodies.clear(); entityToNpc.clear(); persist();
    }

    private void registerBody(NpcBody body) { if (body != null) body.entityUuid().ifPresent(uuid -> entityToNpc.put(uuid, body.npcId())); }
    private void unregisterBody(NpcBody body) { if (body != null) body.entityUuid().ifPresent(entityToNpc::remove); }
    private void persist() { store.saveAll(definitions); }
}
