package vn.svframe.lively.npc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.svframe.lively.persistence.NpcStateRegistry;
import vn.svframe.lively.persistence.NpcStateStore;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NpcRuntimeAsyncPersistenceTest {
    @TempDir Path temp;

    @Test
    void coalescesEditsAndFlushesNewestDefinitionSnapshot() {
        NpcDefinitionStore definitions = new NpcDefinitionStore(temp.resolve("npcs.tsv"));
        NpcStateRegistry states = new NpcStateRegistry(new NpcStateStore(temp.resolve("states")));
        NpcRuntime runtime = new NpcRuntime(definitions, states);
        runtime.registerProvider(NpcDefinition.BodyType.PLAYER, definition -> new StubBody(definition.id()));

        NpcDefinition npc = runtime.create("Alpha", "merchant", NpcDefinition.BodyType.PLAYER, "", "default",
                "minecraft:overworld", new Vec3d(1, 64, 2), 0F, 0F);
        assertTrue(runtime.rename(npc.id(), "Beta", "researcher"));
        assertTrue(runtime.setMetadata(npc.id(), "home.structure", "lab"));
        assertTrue(runtime.setFlag(npc.id(), NpcRuntime.Flag.SILENT, true));

        runtime.flushDefinitions().join();
        NpcDefinition loaded = definitions.loadAll().get(npc.id());
        assertEquals("Beta", loaded.name());
        assertEquals("researcher", loaded.role());
        assertEquals("lab", loaded.metadata().get("home.structure"));
        assertTrue(loaded.silent());

        runtime.close();
        states.close();
    }

    private static final class StubBody implements NpcBody {
        private final UUID id;
        StubBody(UUID id) { this.id = id; }
        @Override public UUID npcId() { return id; }
        @Override public NpcDefinition.BodyType type() { return NpcDefinition.BodyType.PLAYER; }
        @Override public boolean spawned() { return false; }
        @Override public Optional<UUID> entityUuid() { return Optional.empty(); }
        @Override public void spawn(MinecraftServer server, NpcDefinition definition) {}
        @Override public void despawn(MinecraftServer server) {}
        @Override public void teleport(MinecraftServer server, String worldKey, Vec3d position, float yaw, float pitch) {}
        @Override public void lookAt(MinecraftServer server, Vec3d target) {}
        @Override public void tick(MinecraftServer server, NpcDefinition definition) {}
    }
}
