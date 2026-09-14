package vn.svframe.svarcade.fabric;

import java.util.*;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.*;
import vn.svframe.svarcade.runtime.*;

/** Exact owner-thread Minecraft 1.21.1 player-state bridge. Item components are round-tripped through ItemStack NBT. */
final class FabricPlayerStateBridge implements PlayerStateProtection.Bridge {
    private final MinecraftServer server;
    FabricPlayerStateBridge(MinecraftServer server) { this.server = Objects.requireNonNull(server); }

    @Override public PlayerStateSnapshot capture(UUID playerId, EnumSet<PlayerStateSnapshot.Field> fields) {
        ServerPlayerEntity player = player(playerId); PlayerStateSnapshot.Position position = null; PlayerStateSnapshot.Mode mode = null;
        List<PlayerStateSnapshot.Slot> inventory = List.of(); int selectedSlot = -1; List<PlayerStateSnapshot.Effect> effects = List.of();
        if (fields.contains(PlayerStateSnapshot.Field.POSITION)) {
            position = new PlayerStateSnapshot.Position(player.getWorld().getRegistryKey().getValue().toString(),
                    player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
        }
        if (fields.contains(PlayerStateSnapshot.Field.MODE)) {
            PlayerAbilities abilities = player.getAbilities();
            mode = new PlayerStateSnapshot.Mode(player.interactionManager.getGameMode().getName(), abilities.allowFlying, abilities.flying,
                    abilities.getWalkSpeed(), abilities.getFlySpeed());
        }
        if (fields.contains(PlayerStateSnapshot.Field.INVENTORY)) {
            PlayerInventory source = player.getInventory(); List<PlayerStateSnapshot.Slot> rows = new ArrayList<>(); RegistryWrapper.WrapperLookup registries = server.getRegistryManager();
            for (int slot = 0; slot < source.size(); slot++) {
                ItemStack stack = source.getStack(slot); if (stack.isEmpty()) continue;
                NbtElement encoded = stack.encode(registries); String item = Registries.ITEM.getId(stack.getItem()).toString();
                rows.add(new PlayerStateSnapshot.Slot(slot, item, stack.getCount(), encoded.toString()));
            }
            inventory = List.copyOf(rows); selectedSlot = source.getSelectedSlot();
        }
        if (fields.contains(PlayerStateSnapshot.Field.EFFECTS)) {
            List<PlayerStateSnapshot.Effect> rows = new ArrayList<>();
            for (StatusEffectInstance effect : player.getStatusEffects()) {
                Identifier id = Registries.STATUS_EFFECT.getId(effect.getEffectType().value());
                if (id == null) throw new IllegalStateException("Unregistered status effect");
                rows.add(new PlayerStateSnapshot.Effect(id.toString(), effect.getAmplifier(), effect.getDuration(), effect.isAmbient(), effect.shouldShowParticles(), effect.shouldShowIcon()));
            }
            effects = List.copyOf(rows);
        }
        return new PlayerStateSnapshot(1, fields, position, mode, inventory, selectedSlot, effects);
    }

    @Override public void restore(UUID playerId, PlayerStateSnapshot snapshot) {
        ServerPlayerEntity player = player(playerId);
        if (snapshot.fields().contains(PlayerStateSnapshot.Field.POSITION)) restorePosition(player, snapshot.position());
        if (snapshot.fields().contains(PlayerStateSnapshot.Field.MODE)) restoreMode(player, snapshot.mode());
        if (snapshot.fields().contains(PlayerStateSnapshot.Field.INVENTORY)) restoreInventory(player, snapshot.inventory(), snapshot.selectedSlot());
        if (snapshot.fields().contains(PlayerStateSnapshot.Field.EFFECTS)) restoreEffects(player, snapshot.effects());
    }

    private void restorePosition(ServerPlayerEntity player, PlayerStateSnapshot.Position position) {
        RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(position.world())); ServerWorld world = server.getWorld(key);
        if (world == null) throw new IllegalStateException("Saved world unavailable: " + position.world());
        if (!player.teleport(world, position.x(), position.y(), position.z(), Set.of(), position.yaw(), position.pitch()))
            throw new IllegalStateException("Player teleport restore rejected");
    }
    private void restoreMode(ServerPlayerEntity player, PlayerStateSnapshot.Mode mode) {
        GameMode gameMode = GameMode.byName(mode.gameMode(), null); if (gameMode == null) throw new IllegalStateException("Unknown saved game mode: " + mode.gameMode());
        player.changeGameMode(gameMode); PlayerAbilities abilities = player.getAbilities(); abilities.allowFlying = mode.allowFlight(); abilities.flying = mode.flying();
        abilities.setWalkSpeed(mode.walkSpeed()); abilities.setFlySpeed(mode.flySpeed()); player.sendAbilitiesUpdate();
    }
    private void restoreInventory(ServerPlayerEntity player, List<PlayerStateSnapshot.Slot> slots, int selectedSlot) {
        PlayerInventory target = player.getInventory(); RegistryWrapper.WrapperLookup registries = server.getRegistryManager();
        target.clear();
        for (PlayerStateSnapshot.Slot saved : slots) {
            if (saved.slot() >= target.size()) throw new IllegalStateException("Saved inventory slot unavailable: " + saved.slot());
            try {
                NbtElement nbt = new StringNbtReader(new com.mojang.brigadier.StringReader(saved.components())).parseElement();
                ItemStack stack = ItemStack.fromNbt(registries, nbt).orElseThrow(() -> new IllegalStateException("Saved ItemStack failed to decode"));
                Identifier actual = Registries.ITEM.getId(stack.getItem());
                if (!actual.toString().equals(saved.item()) || stack.getCount() != saved.count()) throw new IllegalStateException("Saved ItemStack identity/count mismatch");
                target.setStack(saved.slot(), stack);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) { throw new IllegalStateException("Invalid saved ItemStack NBT", failure); }
        }
        target.setSelectedSlot(selectedSlot); target.markDirty(); player.currentScreenHandler.sendContentUpdates();
    }
    private void restoreEffects(ServerPlayerEntity player, List<PlayerStateSnapshot.Effect> effects) {
        player.clearStatusEffects();
        for (PlayerStateSnapshot.Effect saved : effects) {
            Identifier id = Identifier.of(saved.id()); var entry = Registries.STATUS_EFFECT.getEntry(id).orElseThrow(() -> new IllegalStateException("Saved status effect unavailable: " + id));
            player.addStatusEffect(new StatusEffectInstance(entry, Math.toIntExact(saved.remainingTicks()), saved.amplifier(), saved.ambient(), saved.particles(), saved.icon()));
        }
    }
    private ServerPlayerEntity player(UUID id) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(id); if (player == null) throw new IllegalStateException("Player is offline: " + id); return player;
    }
}
