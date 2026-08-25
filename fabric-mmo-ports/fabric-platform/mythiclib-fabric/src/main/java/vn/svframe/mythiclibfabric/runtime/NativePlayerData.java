package vn.svframe.mythiclibfabric.runtime;

import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.mythiclibfabric.runtime.session.CooldownMapRuntime;
import vn.svframe.mythiclibfabric.runtime.session.MythicPlayerSessionRuntime;
import vn.svframe.mythiclibfabric.runtime.session.ProfileSessionRuntime;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Native Fabric player-data object replacing MythicLib 1.7.1 MMOPlayerData's Bukkit player binding. */
public final class NativePlayerData {
    public final AtomicInteger damageParticleCount = new AtomicInteger();

    private final UUID entityId;
    private final boolean lookup;
    private final CooldownMapRuntime cooldowns = new CooldownMapRuntime(System::currentTimeMillis);
    private final MythicPlayerSessionRuntime sessions;
    private final Map<String, Object> externalData = new ConcurrentHashMap<>();
    private volatile ServerPlayerEntity player;
    private volatile String lastPlayerName;
    private volatile long lastLogActivity;
    private volatile UUID officialId;
    private volatile long nextLeftClick;

    public NativePlayerData(boolean lookup, UUID entityId) {
        this.lookup = lookup;
        this.entityId = Objects.requireNonNull(entityId, "entityId");
        this.sessions = new MythicPlayerSessionRuntime(lookup, System::currentTimeMillis, Set.of());
        this.officialId = entityId;
        this.lastLogActivity = System.currentTimeMillis();
    }

    public NativePlayerData(UUID entityId) { this(false, entityId); }

    public UUID uniqueId() { return entityId; }
    public boolean lookup() { return lookup; }
    public long lastLogActivity() { return lastLogActivity; }
    public String playerName() { return lastPlayerName == null ? entityId.toString() : lastPlayerName; }
    public boolean online() { return player != null; }
    public ServerPlayerEntity player() { return Objects.requireNonNull(player, "Player is offline"); }
    public UUID officialId() { return officialId; }
    public void officialId(UUID officialId) { this.officialId = Objects.requireNonNull(officialId, "Official ID cannot be null"); }
    public CooldownMapRuntime cooldowns() { return cooldowns; }
    public MythicPlayerSessionRuntime sessions() { return sessions; }

    public synchronized void updatePlayer(ServerPlayerEntity next) {
        if (next == null && player != null) {
            player = null;
            lastLogActivity = System.currentTimeMillis();
        } else if (next != null && player == null) {
            player = next;
            lastLogActivity = System.currentTimeMillis();
            lastPlayerName = next.getGameProfile().getName();
        }
    }

    public boolean hasProfile() {
        ProfileSessionRuntime session = sessions.profileSession();
        return session != null && session.hasProfile();
    }

    public boolean playing() {
        ProfileSessionRuntime session = sessions.profileSession();
        return session != null && session.isReady();
    }

    public boolean timedOut() {
        sessions.flushTimedOutSessions();
        return !online() && sessions.savedSessions().isEmpty();
    }

    public void shutdownSession() { sessions.shutdownSession(); }
    public void clearNextSessionBuffer() { sessions.clearNextSessionBuffer(); }
    public ProfileSessionRuntime chooseProfile(UUID profile, ProfileSessionRuntime.UpdateReason reason) { return sessions.chooseProfile(profile, reason); }

    public void blockLeftClicks(long millis) { nextLeftClick = System.currentTimeMillis() + millis; }
    public boolean canLeftClick() { return System.currentTimeMillis() > nextLeftClick; }

    public <T> T externalData(String key, Class<T> type) {
        Object value = externalData.get(key);
        return value == null ? null : type.cast(value);
    }

    public void externalData(String key, Object value) {
        Objects.requireNonNull(key, "key");
        if (value == null) externalData.remove(key); else externalData.put(key, value);
    }

    public boolean hasExternalData(String key) { return externalData.containsKey(key); }

    public void tickOnline() { damageParticleCount.set(0); }

    @Override public boolean equals(Object other) { return other instanceof NativePlayerData data && entityId.equals(data.entityId); }
    @Override public int hashCode() { return entityId.hashCode(); }
}
