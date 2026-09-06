package vn.svframe.leaderboards;

import com.google.gson.*;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.kyori.adventure.platform.fabric.FabricServerAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static net.minecraft.server.command.CommandManager.literal;

public final class SVFrameLeaderboards implements ModInitializer {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final List<Entry> HUNTER_TOP = new CopyOnWriteArrayList<>();
    private static final int MAX_TOP = 10;
    private static MinecraftServer server;
    private static Config config;
    private static long ticks;

    @Override
    public void onInitialize() {
        config = Config.load();
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            refresh();
            registerPlaceholders();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            server = null;
            HUNTER_TOP.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (++ticks % Math.max(200L, config.refreshTicks) == 0L) refresh();
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("bxh")
                    .then(literal("huntercoin").executes(ctx -> show(ctx.getSource())))
                    .then(literal("reload").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> reload(ctx.getSource()))));
            dispatcher.register(literal("svleaderboard")
                    .then(literal("huntercoin").executes(ctx -> show(ctx.getSource())))
                    .then(literal("refresh").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> refreshCommand(ctx.getSource())))
                    .then(literal("reload").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> reload(ctx.getSource()))));
        });
    }

    private static int show(ServerCommandSource source) {
        if (server == null) return 0;
        refresh();
        var audience = FabricServerAudiences.of(server).audience(source);
        audience.sendMessage(MINI.deserialize(config.title));
        if (HUNTER_TOP.isEmpty()) {
            audience.sendMessage(MINI.deserialize(config.empty));
            return 1;
        }
        int rank = 1;
        for (Entry entry : HUNTER_TOP) {
            audience.sendMessage(MINI.deserialize(formatLine(config.line, rank++, entry)));
        }
        return 1;
    }

    private static int refreshCommand(ServerCommandSource source) {
        refresh();
        FabricServerAudiences.of(source.getServer()).audience(source)
                .sendMessage(MINI.deserialize(config.refreshed.replace("<count>", Integer.toString(HUNTER_TOP.size()))));
        return 1;
    }

    private static int reload(ServerCommandSource source) {
        config = Config.load();
        refresh();
        FabricServerAudiences.of(source.getServer()).audience(source).sendMessage(MINI.deserialize(config.reloaded));
        return 1;
    }

    private static void registerPlaceholders() {
        if (!FabricLoader.getInstance().isModLoaded("placeholder-api")) return;
        for (int i = 1; i <= MAX_TOP; i++) {
            final int rank = i;
            Placeholders.register(Identifier.of("svframe", "huntercoin_top_" + rank + "_name"),
                    (ctx, arg) -> PlaceholderResult.value(entry(rank).map(Entry::name).orElse("-")));
            Placeholders.register(Identifier.of("svframe", "huntercoin_top_" + rank + "_value"),
                    (ctx, arg) -> PlaceholderResult.value(entry(rank).map(e -> amount(e.balance)).orElse("0")));
            Placeholders.register(Identifier.of("svframe", "huntercoin_top_" + rank + "_line"),
                    (ctx, arg) -> PlaceholderResult.value(entry(rank).map(e -> stripMini(formatLine(config.line, rank, e))).orElse("-")));
        }
        Placeholders.register(Identifier.of("svframe", "huntercoin_title"),
                (ctx, arg) -> PlaceholderResult.value(stripMini(config.title)));
    }

    private static Optional<Entry> entry(int oneBased) {
        int index = oneBased - 1;
        return index >= 0 && index < HUNTER_TOP.size() ? Optional.of(HUNTER_TOP.get(index)) : Optional.empty();
    }

    private static synchronized void refresh() {
        if (server == null || !FabricLoader.getInstance().isModLoaded("beconomy")) return;
        LinkedHashMap<UUID, String> profiles = loadKnownProfiles();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            profiles.put(player.getUuid(), player.getGameProfile().getName());
        }

        ArrayList<Entry> values = new ArrayList<>();
        for (var profile : profiles.entrySet()) {
            BigDecimal balance = BEconomyBridge.balance(profile.getKey(), config.currency);
            if (balance != null) values.add(new Entry(profile.getKey(), profile.getValue(), balance));
        }
        values.sort(Comparator.comparing(Entry::balance).reversed().thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        HUNTER_TOP.clear();
        HUNTER_TOP.addAll(values.subList(0, Math.min(MAX_TOP, values.size())));
    }

    private static LinkedHashMap<UUID, String> loadKnownProfiles() {
        LinkedHashMap<UUID, String> result = new LinkedHashMap<>();
        Path userCache = Path.of("usercache.json");
        if (!Files.isRegularFile(userCache)) return result;
        try (var reader = Files.newBufferedReader(userCache)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonArray()) return result;
            for (JsonElement el : root.getAsJsonArray()) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                if (!obj.has("uuid") || !obj.has("name")) continue;
                try { result.put(UUID.fromString(obj.get("uuid").getAsString()), obj.get("name").getAsString()); }
                catch (IllegalArgumentException ignored) { }
            }
        } catch (IOException | JsonParseException ignored) { }
        return result;
    }

    private static String formatLine(String template, int rank, Entry entry) {
        return template
                .replace("<rank>", Integer.toString(rank))
                .replace("<player>", entry.name)
                .replace("<amount>", amount(entry.balance))
                .replace("<currency>", config.currencyDisplay);
    }

    private static String amount(BigDecimal value) {
        BigDecimal normalized = value.setScale(Math.max(0, config.decimals), RoundingMode.DOWN).stripTrailingZeros();
        return normalized.toPlainString();
    }

    private static String stripMini(String value) {
        return value.replaceAll("<[^>]+>", "");
    }

    private record Entry(UUID uuid, String name, BigDecimal balance) { }

    private static final class BEconomyBridge {
        private static Object api;
        private static Method getBalance;

        static BigDecimal balance(UUID uuid, String currency) {
            try {
                if (api == null || getBalance == null) resolve();
                if (api == null || getBalance == null) return null;
                Object value = getBalance.invoke(api, uuid, currency);
                if (value instanceof BigDecimal bd) return bd;
                if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
                return value == null ? null : new BigDecimal(value.toString());
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static void resolve() throws Exception {
            Class<?> type = Class.forName("org.krripe.beconomy.api.BEconomy");
            try {
                Method staticApi = type.getMethod("getAPI");
                api = staticApi.invoke(null);
            } catch (ReflectiveOperationException first) {
                Field instance = type.getField("INSTANCE");
                Object singleton = instance.get(null);
                api = type.getMethod("getAPI").invoke(singleton);
            }
            for (Method method : api.getClass().getMethods()) {
                if (method.getName().equals("getBalance") && method.getParameterCount() == 2
                        && method.getParameterTypes()[0] == UUID.class && method.getParameterTypes()[1] == String.class) {
                    getBalance = method;
                    break;
                }
            }
        }
    }

    private static final class Config {
        String currency = "huntercoin";
        String currencyDisplay = "HunterCoin";
        int decimals = 0;
        long refreshTicks = 1200;
        String title = "<gradient:#FFD54F:#FF8F00><bold>BXH HUNTERCOIN</bold></gradient>";
        String line = "<gray>#<rank></gray> <yellow><player></yellow> <dark_gray>•</dark_gray> <gold><amount></gold> <yellow><currency></yellow>";
        String empty = "<gray>Chưa có dữ liệu HunterCoin.</gray>";
        String refreshed = "<green>Đã làm mới BXH HunterCoin: <count> hạng.</green>";
        String reloaded = "<green>Đã tải lại cấu hình BXH.</green>";

        static Config load() {
            Path path = FabricLoader.getInstance().getConfigDir().resolve("svframe-leaderboards.json");
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try {
                if (Files.isRegularFile(path)) {
                    Config loaded = gson.fromJson(Files.readString(path), Config.class);
                    if (loaded != null) return loaded;
                }
                Config created = new Config();
                Files.createDirectories(path.getParent());
                Files.writeString(path, gson.toJson(created));
                return created;
            } catch (Exception ignored) {
                return new Config();
            }
        }
    }
}
