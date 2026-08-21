package vn.svframe.lively.npc;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Vanilla player-model NPC. A Fabric FakePlayer provides authoritative entity state while vanilla packets render it.
 * It is never inserted into PlayerManager, so it is not a real login and cannot receive chat/commands.
 */
public final class PlayerModelBody implements NpcBody {
    private final UUID npcId;
    private final MojangProfileResolver profiles;
    private FakePlayer fake;
    private CompletableFuture<GameProfile> profileFuture;
    private GameProfile profile;
    private boolean visible;
    private long lastSyncTick;

    public PlayerModelBody(UUID npcId, MojangProfileResolver profiles) {
        this.npcId = npcId; this.profiles = profiles;
    }

    @Override public UUID npcId() { return npcId; }
    @Override public NpcDefinition.BodyType type() { return NpcDefinition.BodyType.PLAYER; }
    @Override public boolean spawned() { return visible && fake != null; }
    @Override public Optional<UUID> entityUuid() { return fake == null ? Optional.empty() : Optional.of(fake.getUuid()); }

    @Override
    public void spawn(MinecraftServer server, NpcDefinition definition) {
        if (spawned() || profileFuture != null) return;
        ServerWorld world = world(server, definition.world());
        if (world == null) throw new IllegalArgumentException("unknown world " + definition.world());
        profileFuture = profiles.resolve(server, npcId, definition.skinName(), definition.name());
        profileFuture.whenComplete((resolved, error) -> server.execute(() -> {
            profileFuture = null;
            if (error != null) resolved = new GameProfile(npcId, "LivelyNPC");
            profile = resolved;
            fake = FakePlayer.get(world, profile);
            fake.refreshPositionAndAngles(definition.x(), definition.y(), definition.z(), definition.yaw(), definition.pitch());
            fake.setCustomName(Text.literal(definition.name()));
            fake.setCustomNameVisible(definition.nameVisible());
            fake.setInvulnerable(definition.invulnerable());
            fake.setNoGravity(!definition.gravity());
            fake.setSilent(definition.silent());
            world.onPlayerConnected(fake);
            visible = true;
            for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) showTo(viewer);
        }));
    }

    @Override
    public void despawn(MinecraftServer server) {
        if (fake != null) {
            int entityId = fake.getId();
            for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
                viewer.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(new int[]{entityId}));
                viewer.networkHandler.sendPacket(new PlayerRemoveS2CPacket(List.of(npcId)));
            }
            fake.discard();
        }
        fake = null; profile = null; profileFuture = null; visible = false;
    }

    @Override
    public void teleport(MinecraftServer server, String worldKey, Vec3d position, float yaw, float pitch) {
        if (!spawned()) return;
        ServerWorld target = world(server, worldKey);
        if (target == null) return;
        if (fake.getServerWorld() != target) { despawn(server); return; }
        fake.refreshPositionAndAngles(position.x, position.y, position.z, yaw, pitch);
        syncPosition(server);
    }

    @Override
    public void lookAt(MinecraftServer server, Vec3d target) {
        if (!spawned()) return;
        Vec3d delta = target.subtract(fake.getPos());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        fake.setYaw(yaw); fake.setPitch(pitch); fake.setHeadYaw(yaw);
        byte head = (byte) Math.floor(yaw * 256.0F / 360.0F);
        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) viewer.networkHandler.sendPacket(new EntitySetHeadYawS2CPacket(fake, head));
    }

    @Override
    public void tick(MinecraftServer server, NpcDefinition definition) {
        if (!spawned()) return;
        fake.setInvulnerable(definition.invulnerable()); fake.setNoGravity(!definition.gravity()); fake.setSilent(definition.silent());
        long tick = server.getTicks();
        if (tick - lastSyncTick >= 10L) { lastSyncTick = tick; syncPosition(server); }
    }

    @Override
    public void onViewerJoin(ServerPlayerEntity player, NpcDefinition definition) {
        if (spawned()) showTo(player);
    }

    private void showTo(ServerPlayerEntity viewer) {
        if (fake == null) return;
        viewer.networkHandler.sendPacket(new PlayerListS2CPacket(PlayerListS2CPacket.Action.ADD_PLAYER, fake));
        viewer.networkHandler.sendPacket(new EntitySpawnS2CPacket(fake, 0, fake.getBlockPos()));
        viewer.getServer().execute(() -> viewer.networkHandler.sendPacket(new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_LISTED, fake)));
    }

    private void syncPosition(MinecraftServer server) {
        if (fake == null) return;
        EntityPositionS2CPacket packet = new EntityPositionS2CPacket(fake);
        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) viewer.networkHandler.sendPacket(packet);
    }

    private static ServerWorld world(MinecraftServer server, String key) {
        Identifier id = Identifier.tryParse(key);
        return id == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
    }
}
