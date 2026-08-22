package vn.svframe.lively.npc;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
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
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import vn.svframe.lively.animation.AnimationRequest;
import vn.svframe.lively.animation.AnimationResult;
import vn.svframe.lively.skin.SkinResolver;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Vanilla player-model NPC with asynchronous Mojang/signed custom skin support and no client mod requirement. */
public final class PlayerModelBody implements NpcBody {
    private static final long PROFILE_PROPAGATION_TICKS = 20L;

    private final UUID npcId;
    private final SkinResolver skins;
    private final AtomicLong generation = new AtomicLong();
    private final ConcurrentHashMap<UUID, Long> tabRemovalAt = new ConcurrentHashMap<>();
    private volatile CompletableFuture<GameProfile> profileFuture;
    private volatile GameProfile profile;
    private volatile FakePlayer fake;
    private volatile boolean visible;
    private long lastSyncTick;

    public PlayerModelBody(UUID npcId, SkinResolver skins) {
        this.npcId = npcId;
        this.skins = skins;
    }

    @Override public UUID npcId() { return npcId; }
    @Override public NpcDefinition.BodyType type() { return NpcDefinition.BodyType.PLAYER; }
    @Override public boolean spawned() { return visible && fake != null && !fake.isRemoved(); }
    @Override public Optional<UUID> entityUuid() { return fake == null ? Optional.empty() : Optional.of(fake.getUuid()); }
    @Override public Optional<Vec3d> position() { return fake == null ? Optional.empty() : Optional.of(fake.getPos()); }
    @Override public Optional<String> worldKey() { return fake == null ? Optional.empty() : Optional.of(fake.getServerWorld().getRegistryKey().getValue().toString()); }

    @Override
    public synchronized void spawn(MinecraftServer server, NpcDefinition definition) {
        if (spawned() || profileFuture != null) return;
        ServerWorld world = world(server, definition.world());
        if (world == null) throw new IllegalArgumentException("unknown world " + definition.world());

        long ticket = generation.incrementAndGet();
        CompletableFuture<GameProfile> pending = skins.resolve(server, npcId, definition.skinName(), definition.name());
        profileFuture = pending;
        pending.whenComplete((resolved, error) -> {
            GameProfile resolvedProfile = error == null && resolved != null ? resolved : new GameProfile(npcId, "LivelyNPC");
            server.execute(() -> finishSpawn(server, world, definition, pending, ticket, resolvedProfile));
        });
    }

    private synchronized void finishSpawn(MinecraftServer server, ServerWorld world, NpcDefinition definition,
                                          CompletableFuture<GameProfile> pending, long ticket, GameProfile resolvedProfile) {
        if (generation.get() != ticket || profileFuture != pending || pending.isCancelled() || spawned()) return;
        profileFuture = null;
        profile = resolvedProfile;
        FakePlayer created = FakePlayer.get(world, profile);
        fake = created;
        created.refreshPositionAndAngles(definition.x(), definition.y(), definition.z(), definition.yaw(), definition.pitch());
        created.setCustomName(Text.literal(definition.name()));
        created.setCustomNameVisible(definition.nameVisible());
        created.setInvulnerable(definition.invulnerable());
        created.setNoGravity(!definition.gravity());
        created.setSilent(definition.silent());
        world.onPlayerConnected(created);
        visible = true;
        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) showTo(viewer);
    }

    @Override
    public synchronized void despawn(MinecraftServer server) {
        generation.incrementAndGet();
        CompletableFuture<GameProfile> pending = profileFuture;
        profileFuture = null;
        if (pending != null) pending.cancel(true);
        FakePlayer current = fake;
        visible = false;
        fake = null;
        profile = null;
        tabRemovalAt.clear();
        if (current == null) return;
        int entityId = current.getId();
        UUID entityUuid = current.getUuid();
        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
            viewer.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(new int[]{entityId}));
            viewer.networkHandler.sendPacket(new PlayerRemoveS2CPacket(List.of(entityUuid)));
        }
        current.discard();
    }

    @Override
    public void teleport(MinecraftServer server, String worldKey, Vec3d position, float yaw, float pitch) {
        FakePlayer current = fake;
        if (!spawned() || current == null) return;
        ServerWorld target = world(server, worldKey);
        if (target == null) return;
        if (current.getServerWorld() != target) {
            despawn(server);
            return;
        }
        current.refreshPositionAndAngles(position.x, position.y, position.z, yaw, pitch);
        syncPosition(server);
    }

    @Override
    public void moveStep(MinecraftServer server, String worldKey, Vec3d position, float yaw, float pitch) {
        FakePlayer current = fake;
        if (!spawned() || current == null || !worldKey().orElse("").equals(worldKey)) return;
        current.refreshPositionAndAngles(position.x, position.y, position.z, yaw, pitch);
        current.setHeadYaw(yaw);
        syncPosition(server);
    }

    @Override
    public void lookAt(MinecraftServer server, Vec3d target) {
        FakePlayer current = fake;
        if (!spawned() || current == null) return;
        Vec3d delta = target.subtract(current.getPos());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        current.setYaw(yaw);
        current.setPitch(pitch);
        current.setHeadYaw(yaw);
        byte head = (byte) Math.floor(yaw * 256.0F / 360.0F);
        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
            viewer.networkHandler.sendPacket(new EntitySetHeadYawS2CPacket(current, head));
        }
    }

    @Override
    public boolean attack(MinecraftServer server, Entity target) {
        FakePlayer current = fake;
        if (!spawned() || current == null || target == null || target.isRemoved() || target == current) return false;
        current.attack(target);
        current.swingHand(Hand.MAIN_HAND);
        return true;
    }

    @Override
    public AnimationResult animate(MinecraftServer server, AnimationRequest request) {
        FakePlayer current = fake;
        if (!spawned() || current == null) return AnimationResult.unsupported(request.name(), "player body is not spawned");
        return switch (request.name()) {
            case "idle", "stand", "standing", "reset" -> {
                current.setSprinting(false);
                current.setSneaking(false);
                current.setPose(EntityPose.STANDING);
                yield AnimationResult.played(request.name(), "vanilla player standing state");
            }
            case "walk", "walking" -> {
                current.setSprinting(false);
                current.setSneaking(false);
                yield AnimationResult.played(request.name(), "walk animation follows server movement");
            }
            case "run", "running", "sprint" -> {
                current.setSneaking(false);
                current.setSprinting(true);
                yield AnimationResult.played(request.name(), "vanilla sprint state");
            }
            case "crouch", "sneak" -> {
                current.setSprinting(false);
                current.setSneaking(true);
                yield AnimationResult.played(request.name(), "vanilla crouch state");
            }
            case "swing", "attack", "swing_main", "swing_main_hand" -> {
                current.swingHand(Hand.MAIN_HAND);
                yield AnimationResult.played(request.name(), "vanilla main-hand swing");
            }
            case "swing_off", "swing_off_hand" -> {
                current.swingHand(Hand.OFF_HAND);
                yield AnimationResult.played(request.name(), "vanilla off-hand swing");
            }
            case "use", "use_item", "use_main", "bow" -> {
                current.setCurrentHand(Hand.MAIN_HAND);
                yield AnimationResult.played(request.name(), "vanilla item-use pose; visible pose depends on held item");
            }
            case "use_off" -> {
                current.setCurrentHand(Hand.OFF_HAND);
                yield AnimationResult.played(request.name(), "vanilla off-hand item-use pose");
            }
            case "sleep" -> {
                current.setSprinting(false);
                current.setSneaking(false);
                current.setPose(EntityPose.SLEEPING);
                yield AnimationResult.played(request.name(), "vanilla sleeping pose");
            }
            case "hurt", "damage" -> {
                current.getServerWorld().sendEntityStatus(current, (byte) 2);
                yield AnimationResult.played(request.name(), "vanilla hurt animation without applying damage");
            }
            default -> AnimationResult.unsupported(request.name(), "unsupported vanilla player animation");
        };
    }

    @Override
    public void tick(MinecraftServer server, NpcDefinition definition) {
        FakePlayer current = fake;
        if (!spawned() || current == null) return;
        current.setInvulnerable(definition.invulnerable());
        current.setNoGravity(!definition.gravity());
        current.setSilent(definition.silent());
        current.setCustomName(Text.literal(definition.name()));
        current.setCustomNameVisible(definition.nameVisible());
        long tick = server.getTicks();
        flushTabRemovals(server, current, tick);
        if (tick - lastSyncTick >= 10L) {
            lastSyncTick = tick;
            syncPosition(server);
        }
    }

    @Override public void onViewerJoin(ServerPlayerEntity player, NpcDefinition definition) { if (spawned()) showTo(player); }

    private void showTo(ServerPlayerEntity viewer) {
        FakePlayer current = fake;
        if (current == null || current.isRemoved()) return;
        viewer.networkHandler.sendPacket(new PlayerListS2CPacket(PlayerListS2CPacket.Action.ADD_PLAYER, current));
        viewer.networkHandler.sendPacket(new EntitySpawnS2CPacket(current, 0, current.getBlockPos()));
        tabRemovalAt.put(viewer.getUuid(), viewer.getServer().getTicks() + PROFILE_PROPAGATION_TICKS);
    }

    private void flushTabRemovals(MinecraftServer server, FakePlayer current, long tick) {
        for (var entry : List.copyOf(tabRemovalAt.entrySet())) {
            if (tick < entry.getValue()) continue;
            UUID viewerId = entry.getKey();
            if (!tabRemovalAt.remove(viewerId, entry.getValue())) continue;
            ServerPlayerEntity viewer = server.getPlayerManager().getPlayer(viewerId);
            if (viewer != null && fake == current && visible) {
                viewer.networkHandler.sendPacket(new PlayerRemoveS2CPacket(List.of(current.getUuid())));
            }
        }
    }

    private void syncPosition(MinecraftServer server) {
        FakePlayer current = fake;
        if (current == null || current.isRemoved()) return;
        EntityPositionS2CPacket packet = new EntityPositionS2CPacket(current);
        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) viewer.networkHandler.sendPacket(packet);
    }

    private static ServerWorld world(MinecraftServer server, String key) {
        Identifier id = Identifier.tryParse(key);
        return id == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
    }
}
