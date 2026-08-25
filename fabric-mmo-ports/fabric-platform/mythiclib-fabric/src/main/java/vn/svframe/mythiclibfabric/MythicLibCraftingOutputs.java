package vn.svframe.mythiclibfabric;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.mythiclibfabric.runtime.MythicLibCraftingRuntime;
import vn.svframe.mythiclibfabric.runtime.NativePlaceholderRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Native Fabric output layer for MythicLib item and command recipe outputs. */
public final class MythicLibCraftingOutputs {
    private static final Map<MythicLibCraftingRuntime.Recipe, CommandOutput> COMMANDS = new ConcurrentHashMap<>();
    private MythicLibCraftingOutputs() {}

    /** Registers the recipe and gives it MROCommand semantics: preview item, console commands on craft, no item grant. */
    public static void registerCommand(MythicLibCraftingRuntime.Recipe recipe, List<String> commands) {
        Objects.requireNonNull(recipe, "recipe");
        COMMANDS.put(recipe, new CommandOutput(commands));
        MythicLibCraftingRuntime.register(recipe);
    }

    public static void unregister(MythicLibCraftingRuntime.Recipe recipe) {
        if (recipe != null) COMMANDS.remove(recipe);
    }

    public static boolean isCommand(MythicLibCraftingRuntime.Recipe recipe) { return COMMANDS.containsKey(recipe); }

    /**
     * Mirrors MROCommand.applyResult: resolve player placeholders once, then run every
     * command from the server command source once for every crafted operation.
     */
    public static boolean applyCommand(ServerPlayerEntity player, MythicLibCraftingRuntime.Recipe recipe, int times) {
        CommandOutput output = COMMANDS.get(recipe);
        if (output == null || times <= 0) return false;
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        List<String> resolved = new ArrayList<>(output.commands.size());
        for (String command : output.commands) {
            if (command == null) continue;
            String parsed = NativePlaceholderRegistry.parse(player.getUuid(), command).trim();
            if (parsed.startsWith("/")) parsed = parsed.substring(1);
            if (!parsed.isBlank()) resolved.add(parsed);
        }
        for (int i = 0; i < times; i++) for (String command : resolved) {
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
        }
        return true;
    }

    private static final class CommandOutput {
        private final List<String> commands;
        private CommandOutput(List<String> commands) {
            this.commands = commands == null ? List.of() : List.copyOf(commands);
        }
    }
}
