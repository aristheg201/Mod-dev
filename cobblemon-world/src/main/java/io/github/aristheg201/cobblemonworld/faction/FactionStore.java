package io.github.aristheg201.cobblemonworld.faction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class FactionStore {
    public static final FactionStore INSTANCE = new FactionStore();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Path path;
    private Root root = new Root();
    private final Map<UUID, UUID> playerIndex = new LinkedHashMap<>();

    private FactionStore() {}

    public synchronized void load(MinecraftServer server) {
        path = server.getWorldPath(LevelResource.ROOT).resolve("cobblemonworld").resolve("factions.json");
        root = new Root();
        if (Files.exists(path)) {
            try {
                Root loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Root.class);
                if (loaded != null) root = loaded;
            } catch (Exception e) {
                CobblemonWorldMod.LOGGER.error("Failed to load native faction state", e);
            }
        }
        normalize();
        rebuildIndex();
    }

    public synchronized void save() {
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            CobblemonWorldMod.LOGGER.error("Failed to save native faction state", e);
        }
    }

    public synchronized Optional<FactionData> byPlayer(UUID playerId) {
        UUID factionId = playerIndex.get(playerId);
        return factionId == null ? Optional.empty() : Optional.ofNullable(root.factions.get(factionId));
    }

    public synchronized Optional<FactionData> byId(UUID id) {
        return Optional.ofNullable(root.factions.get(id));
    }

    public synchronized Optional<FactionData> byName(String name) {
        if (name == null) return Optional.empty();
        String needle = name.trim().toLowerCase(Locale.ROOT);
        return root.factions.values().stream()
                .filter(f -> f.name.toLowerCase(Locale.ROOT).equals(needle))
                .findFirst();
    }

    public synchronized List<FactionData> all() {
        return List.copyOf(root.factions.values());
    }

    public synchronized void put(FactionData faction) {
        faction.normalize();
        root.factions.put(faction.id, faction);
        rebuildIndex();
        save();
    }

    public synchronized void remove(UUID id) {
        root.factions.remove(id);
        rebuildIndex();
        save();
    }

    public synchronized void touch() {
        rebuildIndex();
        save();
    }

    private void normalize() {
        if (root == null) root = new Root();
        if (root.factions == null) root.factions = new LinkedHashMap<>();
        root.factions.values().removeIf(f -> f == null || f.id == null);
        root.factions.values().forEach(FactionData::normalize);
    }

    private void rebuildIndex() {
        playerIndex.clear();
        for (FactionData faction : root.factions.values()) {
            for (UUID member : faction.members.keySet()) playerIndex.put(member, faction.id);
        }
    }

    public enum Role {
        OWNER, OFFICER, MEMBER;

        public boolean canManageMembers() {
            return this == OWNER || this == OFFICER;
        }
    }

    public static final class FactionData {
        public UUID id = UUID.randomUUID();
        public String name = "";
        public UUID owner;
        public Map<UUID, Role> members = new LinkedHashMap<>();
        public Set<UUID> invites = new LinkedHashSet<>();
        public long createdEpochMillis = System.currentTimeMillis();

        public FactionData() {}

        public FactionData(UUID id, String name, UUID owner) {
            this.id = id;
            this.name = name;
            this.owner = owner;
            this.members.put(owner, Role.OWNER);
        }

        public void normalize() {
            if (id == null) id = UUID.randomUUID();
            if (name == null) name = "";
            if (members == null) members = new LinkedHashMap<>();
            if (invites == null) invites = new LinkedHashSet<>();
            if (owner != null) members.put(owner, Role.OWNER);
            members.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
            invites.removeIf(id -> id == null || members.containsKey(id));
        }

        public Role role(UUID playerId) {
            return members.get(playerId);
        }

        public int memberCount() {
            return members.size();
        }
    }

    private static final class Root {
        public int schemaVersion = 1;
        public Map<UUID, FactionData> factions = new LinkedHashMap<>();
    }
}
