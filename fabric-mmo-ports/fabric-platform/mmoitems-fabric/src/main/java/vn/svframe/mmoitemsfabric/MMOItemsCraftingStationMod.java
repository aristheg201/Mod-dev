package vn.svframe.mmoitemsfabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import vn.svframe.compat.YamlLite;
import vn.svframe.mythiclibfabric.runtime.RpgProfileRegistry;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Native server-side chest GUI for legacy MMOItems crafting stations and persistent serial crafting queues. */
public final class MMOItemsCraftingStationMod implements ModInitializer {
    private static final Logger LOG = Logger.getLogger("MMOItems-Fabric/CraftingStations");
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("MMOItems");
    private static final Path STATION_ROOT = ROOT.resolve("crafting-stations");
    private static final Path QUEUE_FILE = ROOT.resolve("fabric-crafting-queues.bin");
    private static final int QUEUE_MAGIC = 0x4D495151;
    private static final int QUEUE_VERSION = 1;
    private static final int RECIPE_PAGE_SIZE = 42;
    private static final int PREVIOUS_SLOT = 42;
    private static final int INFO_SLOT = 43;
    private static final int NEXT_SLOT = 44;
    private static final int QUEUE_FIRST_SLOT = 45;
    private static final int QUEUE_LAST_SLOT = 53;

    private static final Map<String, Station> STATIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final List<QueueEntry> QUEUE = new ArrayList<>();
    private static final Object QUEUE_LOCK = new Object();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MMOItems-Fabric-CraftingQueue");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile long configStamp = Long.MIN_VALUE;
    private static long ticks;
    private static volatile MinecraftServer server;

    @Override
    public void onInitialize() {
        reloadStations();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("mistation")
                    .then(argument("station", StringArgumentType.word())
                            .executes(ctx -> open(ctx.getSource().getPlayerOrThrow(), StringArgumentType.getString(ctx, "station")))));
            dispatcher.register(literal("mistationreload")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(ctx -> {
                        reloadStations();
                        ctx.getSource().sendFeedback(() -> Text.literal("MMOItems crafting stations reloaded: " + STATIONS.size()), true);
                        return STATIONS.size();
                    }));
        });
        ServerLifecycleEvents.SERVER_STARTED.register(value -> {
            server = value;
            loadQueue();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(value -> {
            saveQueueNow(snapshotQueue());
            SESSIONS.clear();
            server = null;
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(value -> {
            IO.shutdown();
            try { IO.awaitTermination(5, TimeUnit.SECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        });
        ServerTickEvents.END_SERVER_TICK.register(value -> tick(value));
    }

    public static boolean handleClick(ServerPlayerEntity player, ScreenHandler handler, int slotIndex, SlotActionType actionType) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null || session.handler != handler) return false;
        if (slotIndex < 0 || slotIndex > 53) return false;

        if (slotIndex == PREVIOUS_SLOT && session.page > 0) {
            session.page--;
            refresh(session, player);
            return true;
        }
        if (slotIndex == NEXT_SLOT && (session.page + 1) * RECIPE_PAGE_SIZE < visibleRecipes(session.station, player).size()) {
            session.page++;
            refresh(session, player);
            return true;
        }
        String recipeId = session.recipeSlots.get(slotIndex);
        if (recipeId != null) {
            craft(player, session.station, recipeId);
            refresh(session, player);
            return true;
        }
        UUID queueId = session.queueSlots.get(slotIndex);
        if (queueId != null) {
            claim(player, queueId);
            refresh(session, player);
            return true;
        }
        return true;
    }

    private static int open(ServerPlayerEntity player, String stationId) {
        Station station = STATIONS.get(norm(stationId));
        if (station == null) {
            player.sendMessage(Text.literal("Unknown crafting station: " + stationId), false);
            return 0;
        }
        Session session = new Session(station, new SimpleInventory(54));
        SESSIONS.put(player.getUuid(), session);
        refresh(session, player);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInventory, ignored) -> {
            GenericContainerScreenHandler handler = GenericContainerScreenHandler.createGeneric9x6(syncId, playerInventory, session.inventory);
            session.handler = handler;
            return handler;
        }, Text.literal(station.name)));
        return 1;
    }

    private static void tick(MinecraftServer minecraftServer) {
        ticks++;
        if (ticks % 20L == 0L) {
            long now = System.currentTimeMillis();
            for (ServerPlayerEntity player : minecraftServer.getPlayerManager().getPlayerList()) {
                Session session = SESSIONS.get(player.getUuid());
                if (session == null) continue;
                if (session.handler == null || player.currentScreenHandler != session.handler) {
                    SESSIONS.remove(player.getUuid(), session);
                    continue;
                }
                if (session.lastRefreshSecond != now / 1000L) {
                    session.lastRefreshSecond = now / 1000L;
                    refresh(session, player);
                    session.handler.sendContentUpdates();
                }
            }
        }
        if (ticks % 100L == 0L) {
            long next = stationStamp();
            if (next != configStamp) reloadStations();
        }
    }

    private static void craft(ServerPlayerEntity player, Station station, String recipeId) {
        Recipe recipe = station.recipes.get(recipeId);
        if (recipe == null || !conditionsMet(player, recipe)) return;
        synchronized (QUEUE_LOCK) {
            long active = QUEUE.stream().filter(entry -> entry.player.equals(player.getUuid()) && entry.station.equals(station.id)).count();
            if (active >= station.maxQueueSize) {
                player.sendMessage(Text.literal("Crafting queue is full."), false);
                return;
            }
            if (!hasIngredients(player, recipe.ingredients)) {
                player.sendMessage(Text.literal("Missing crafting ingredients."), false);
                return;
            }
            consumeIngredients(player, recipe.ingredients);
            long now = System.currentTimeMillis();
            long base = now;
            for (QueueEntry entry : QUEUE) {
                if (entry.player.equals(player.getUuid()) && entry.station.equals(station.id)) base = Math.max(base, entry.completion);
            }
            QUEUE.add(new QueueEntry(UUID.randomUUID(), player.getUuid(), station.id, recipe.id, now,
                    base + Math.max(0, recipe.craftingTimeSeconds) * 1000L));
        }
        saveQueueAsync();
    }

    private static void claim(ServerPlayerEntity player, UUID queueId) {
        QueueEntry claimed = null;
        synchronized (QUEUE_LOCK) {
            long now = System.currentTimeMillis();
            for (int i = 0; i < QUEUE.size(); i++) {
                QueueEntry entry = QUEUE.get(i);
                if (!entry.id.equals(queueId) || !entry.player.equals(player.getUuid()) || now < entry.completion) continue;
                claimed = entry;
                QUEUE.remove(i);
                break;
            }
        }
        if (claimed == null) return;
        Station station = STATIONS.get(claimed.station);
        Recipe recipe = station == null ? null : station.recipes.get(claimed.recipe);
        if (recipe == null) {
            LOG.warning("Cannot claim missing crafting recipe " + claimed.station + ':' + claimed.recipe);
            return;
        }
        ItemStack output = output(recipe.output);
        if (output.isEmpty()) return;
        if (!player.getInventory().insertStack(output)) player.dropItem(output, false);
        saveQueueAsync();
    }

    private static boolean conditionsMet(ServerPlayerEntity player, Recipe recipe) {
        RpgProfileRegistry.Snapshot profile = RpgProfileRegistry.mergeOrDefault(player.getUuid());
        for (Condition condition : recipe.conditions) {
            switch (condition.type) {
                case "level" -> {
                    int required = integer(first(condition.params, "level", "amount"), 0);
                    if (profile.level() < required) return false;
                }
                case "permission" -> {
                    String permission = first(condition.params, "permission", "perm", "node");
                    if (!hasPermission(player, permission)) return false;
                }
                case "class" -> {
                    String required = norm(first(condition.params, "class", "id", "name"));
                    if (!required.isEmpty() && !norm(profile.playerClass()).equals(required)) return false;
                }
                case "money" -> {
                    // Money is intentionally validated only when a supported economy is present.
                    double required = decimal(first(condition.params, "amount", "money"), 0.0);
                    if (required > 0.0 && !EconomyAccess.hasAndWithdraw(player, required, false)) return false;
                }
                default -> {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<Recipe> visibleRecipes(Station station, ServerPlayerEntity player) {
        List<Recipe> out = new ArrayList<>();
        for (Recipe recipe : station.recipes.values()) {
            if (recipe.hideWhenLocked && !conditionsMet(player, recipe)) continue;
            out.add(recipe);
        }
        return out;
    }

    private static void refresh(Session session, ServerPlayerEntity player) {
        session.inventory.clear();
        session.recipeSlots.clear();
        session.queueSlots.clear();
        List<Recipe> recipes = visibleRecipes(session.station, player);
        int pages = Math.max(1, (recipes.size() + RECIPE_PAGE_SIZE - 1) / RECIPE_PAGE_SIZE);
        session.page = Math.max(0, Math.min(session.page, pages - 1));
        int start = session.page * RECIPE_PAGE_SIZE;
        for (int local = 0; local < RECIPE_PAGE_SIZE && start + local < recipes.size(); local++) {
            Recipe recipe = recipes.get(start + local);
            boolean unlocked = conditionsMet(player, recipe);
            ItemStack icon = unlocked ? output(recipe.output) : new ItemStack(Items.BARRIER);
            if (icon.isEmpty()) icon = new ItemStack(Items.PAPER);
            icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(recipe.id + (unlocked ? "" : " [locked]")));
            session.inventory.setStack(local, icon);
            session.recipeSlots.put(local, recipe.id);
        }
        if (session.page > 0) session.inventory.setStack(PREVIOUS_SLOT, named(new ItemStack(Items.ARROW), "Previous page"));
        session.inventory.setStack(INFO_SLOT, named(new ItemStack(Items.CRAFTING_TABLE), session.station.name + " " + (session.page + 1) + "/" + pages));
        if ((session.page + 1) * RECIPE_PAGE_SIZE < recipes.size()) session.inventory.setStack(NEXT_SLOT, named(new ItemStack(Items.ARROW), "Next page"));

        List<QueueEntry> queue = playerQueue(player.getUuid(), session.station.id);
        long now = System.currentTimeMillis();
        for (int i = 0; i < queue.size() && QUEUE_FIRST_SLOT + i <= QUEUE_LAST_SLOT; i++) {
            QueueEntry entry = queue.get(i);
            boolean ready = now >= entry.completion;
            long seconds = Math.max(0L, (entry.completion - now + 999L) / 1000L);
            ItemStack icon = new ItemStack(ready ? Items.LIME_DYE : Items.CLOCK);
            icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(entry.recipe + (ready ? " [ready]" : " [" + seconds + "s]")));
            int slot = QUEUE_FIRST_SLOT + i;
            session.inventory.setStack(slot, icon);
            session.queueSlots.put(slot, entry.id);
        }
        session.inventory.markDirty();
        if (session.handler != null) session.handler.sendContentUpdates();
    }

    private static List<QueueEntry> playerQueue(UUID player, String station) {
        synchronized (QUEUE_LOCK) {
            return QUEUE.stream().filter(entry -> entry.player.equals(player) && entry.station.equals(station))
                    .sorted(Comparator.comparingLong(entry -> entry.completion)).toList();
        }
    }

    private static boolean hasIngredients(ServerPlayerEntity player, List<Ingredient> ingredients) {
        Map<String, Integer> required = aggregate(ingredients);
        Map<String, Integer> available = new HashMap<>();
        for (int slot = 0; slot < Math.min(36, player.getInventory().size()); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;
            for (Ingredient ingredient : ingredients) {
                if (matches(stack, ingredient)) available.merge(ingredient.key(), stack.getCount(), Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> entry : required.entrySet()) if (available.getOrDefault(entry.getKey(), 0) < entry.getValue()) return false;
        return true;
    }

    private static void consumeIngredients(ServerPlayerEntity player, List<Ingredient> ingredients) {
        Map<String, Integer> remaining = aggregate(ingredients);
        for (int slot = 0; slot < Math.min(36, player.getInventory().size()) && !remaining.isEmpty(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;
            for (Ingredient ingredient : ingredients) {
                Integer need = remaining.get(ingredient.key());
                if (need == null || need <= 0 || !matches(stack, ingredient)) continue;
                int take = Math.min(need, stack.getCount());
                stack.decrement(take);
                int left = need - take;
                if (left <= 0) remaining.remove(ingredient.key()); else remaining.put(ingredient.key(), left);
                break;
            }
        }
        player.getInventory().markDirty();
    }

    private static Map<String, Integer> aggregate(List<Ingredient> ingredients) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients) out.merge(ingredient.key(), Math.max(1, ingredient.amount), Integer::sum);
        return out;
    }

    private static boolean matches(ItemStack stack, Ingredient ingredient) {
        if (ingredient.kind.equals("mmoitem") || ingredient.kind.equals("mmoitems")) {
            MMOItemsGameplayMod.Template template = MMOItemsGameplayMod.template(stack);
            if (template == null || !template.id().equalsIgnoreCase(ingredient.id)) return false;
            if (!ingredient.itemType.isEmpty() && !MMOItemsTypeRegistry.isA(template.type(), ingredient.itemType)) return false;
            return ingredient.level <= 0 || MMOItemsGameplayMod.upgradeLevel(stack) >= ingredient.level;
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        String wanted = ingredient.id.toLowerCase(Locale.ROOT).replace(' ', '_');
        return id.toString().equals(wanted) || id.getPath().equals(wanted) || id.toString().equals("minecraft:" + wanted);
    }

    private static ItemStack output(Output output) {
        if (output == null || output.id.isEmpty()) return ItemStack.EMPTY;
        if (output.kind.equals("mmoitem") || output.kind.equals("mmoitems")) {
            return MMOItemsFabricMod.createStack(output.itemType, output.id, Math.max(1, output.amount));
        }
        String raw = output.id.toLowerCase(Locale.ROOT).replace(' ', '_');
        Identifier identifier = raw.contains(":") ? Identifier.tryParse(raw) : Identifier.tryParse("minecraft:" + raw);
        if (identifier == null || !Registries.ITEM.containsId(identifier)) return ItemStack.EMPTY;
        Item item = Registries.ITEM.get(identifier);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, output.amount));
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return stack;
    }

    private static synchronized void reloadStations() {
        try {
            Map<String, Station> loaded = new LinkedHashMap<>();
            if (Files.isDirectory(STATION_ROOT)) {
                try (var paths = Files.walk(STATION_ROOT)) {
                    for (Path file : paths.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                            .sorted().toList()) {
                        Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
                        String fallbackId = removeExtension(file.getFileName().toString());
                        Station station = parseStation(fallbackId, root);
                        loaded.put(station.id, station);
                    }
                }
            }
            STATIONS.clear();
            STATIONS.putAll(loaded);
            configStamp = stationStamp();
            LOG.info("Loaded native crafting stations=" + STATIONS.size());
        } catch (Exception exception) {
            LOG.log(Level.SEVERE, "Failed to reload crafting stations; keeping previous snapshot", exception);
        }
    }

    private static Station parseStation(String fallbackId, Map<String, Object> root) {
        String id = norm(string(root.getOrDefault("id", fallbackId)));
        String name = string(root.getOrDefault("name", fallbackId));
        int maxQueue = Math.max(1, integer(root.get("max-queue-size"), 10));
        Map<String, Recipe> recipes = new LinkedHashMap<>();
        Map<String, Object> rawRecipes = map(root.get("recipes"));
        for (Map.Entry<String, Object> entry : rawRecipes.entrySet()) {
            Map<String, Object> raw = map(entry.getValue());
            List<Condition> conditions = new ArrayList<>();
            for (Object value : list(raw.get("conditions"))) {
                Call call = parseCall(String.valueOf(value));
                conditions.add(new Condition(call.name, call.params));
            }
            List<Ingredient> ingredients = new ArrayList<>();
            for (Object value : list(raw.get("ingredients"))) {
                Call call = parseCall(String.valueOf(value));
                ingredients.add(new Ingredient(call.name,
                        normType(first(call.params, "type", "item-type")),
                        first(call.params, "id", "material"),
                        Math.max(1, integer(first(call.params, "amount"), 1)),
                        Math.max(0, integer(first(call.params, "level"), 0))));
            }
            Output output = parseOutput(raw.get("output"));
            int craftingTime = Math.max(0, integer(raw.get("crafting-time"), 0));
            Map<String, Object> options = map(raw.get("options"));
            boolean outputItem = bool(options.get("output-item"), false);
            boolean silent = bool(options.get("silent-craft"), false);
            boolean hide = bool(options.get("hide-when-locked"), false);
            recipes.put(entry.getKey(), new Recipe(entry.getKey(), List.copyOf(conditions), List.copyOf(ingredients), output,
                    craftingTime, outputItem, silent, hide));
        }
        return new Station(id, name, maxQueue, Map.copyOf(recipes));
    }

    private static Output parseOutput(Object raw) {
        if (raw instanceof String value) {
            Call call = parseCall(value);
            String itemType = normType(first(call.params, "type", "item-type"));
            String id = first(call.params, "id", "material");
            return new Output(call.name, itemType, id, Math.max(1, integer(first(call.params, "amount"), 1)));
        }
        Map<String, Object> map = map(raw);
        String kind = string(map.getOrDefault("kind", map.getOrDefault("source", "mmoitem"))).toLowerCase(Locale.ROOT);
        String itemType = normType(string(map.getOrDefault("type", "")));
        String id = string(map.getOrDefault("id", map.getOrDefault("material", "")));
        return new Output(kind, itemType, id, Math.max(1, integer(map.get("amount"), 1)));
    }

    private static Call parseCall(String raw) {
        String value = raw == null ? "" : raw.trim();
        int open = value.indexOf('{');
        if (open < 0) return new Call(value.toLowerCase(Locale.ROOT), Map.of());
        int close = value.lastIndexOf('}');
        if (close < open) close = value.length();
        String name = value.substring(0, open).trim().toLowerCase(Locale.ROOT);
        Map<String, String> params = new LinkedHashMap<>();
        for (String part : splitArgs(value.substring(open + 1, close))) {
            int equals = unquotedEquals(part);
            if (equals <= 0) continue;
            params.put(part.substring(0, equals).trim().toLowerCase(Locale.ROOT), unquote(part.substring(equals + 1).trim()));
        }
        return new Call(name, Map.copyOf(params));
    }

    private static List<String> splitArgs(String value) {
        List<String> result = new ArrayList<>();
        char quote = 0;
        int depth = 0;
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (quote != 0) {
                if (c == quote && (i == 0 || value.charAt(i - 1) != '\\')) quote = 0;
            } else if (c == '\'' || c == '"') quote = c;
            else if (c == '{' || c == '[' || c == '(') depth++;
            else if (c == '}' || c == ']' || c == ')') depth--;
            else if ((c == ',' || c == ';') && depth == 0) {
                result.add(value.substring(start, i));
                start = i + 1;
            }
        }
        result.add(value.substring(start));
        return result;
    }

    private static int unquotedEquals(String value) {
        char quote = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (quote != 0) {
                if (c == quote && (i == 0 || value.charAt(i - 1) != '\\')) quote = 0;
            } else if (c == '\'' || c == '"') quote = c;
            else if (c == '=') return i;
        }
        return -1;
    }

    private static String unquote(String value) {
        if (value.length() > 1 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) return value.substring(1, value.length() - 1);
        return value;
    }

    private static boolean hasPermission(ServerPlayerEntity player, String permission) {
        if (permission == null || permission.isBlank()) return true;
        if (player.hasPermissionLevel(2)) return true;
        if (!FabricLoader.getInstance().isModLoaded("luckperms")) return false;
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = provider.getMethod("get").invoke(null);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, player.getUuid());
            if (user == null) return false;
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object permissionData = cachedData.getClass().getMethod("getPermissionData").invoke(cachedData);
            Object result = permissionData.getClass().getMethod("checkPermission", String.class).invoke(permissionData, permission);
            return Boolean.TRUE.equals(result.getClass().getMethod("asBoolean").invoke(result));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static void saveQueueAsync() {
        List<QueueEntry> snapshot = snapshotQueue();
        IO.execute(() -> saveQueueNow(snapshot));
    }

    private static List<QueueEntry> snapshotQueue() {
        synchronized (QUEUE_LOCK) { return List.copyOf(QUEUE); }
    }

    private static void saveQueueNow(List<QueueEntry> snapshot) {
        try {
            Files.createDirectories(QUEUE_FILE.getParent());
            Path temp = QUEUE_FILE.resolveSibling(QUEUE_FILE.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
                out.writeInt(QUEUE_MAGIC);
                out.writeInt(QUEUE_VERSION);
                out.writeInt(snapshot.size());
                for (QueueEntry entry : snapshot) {
                    out.writeLong(entry.id.getMostSignificantBits()); out.writeLong(entry.id.getLeastSignificantBits());
                    out.writeLong(entry.player.getMostSignificantBits()); out.writeLong(entry.player.getLeastSignificantBits());
                    out.writeUTF(entry.station); out.writeUTF(entry.recipe); out.writeLong(entry.started); out.writeLong(entry.completion);
                }
            }
            Files.move(temp, QUEUE_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            LOG.log(Level.SEVERE, "Failed to save crafting queue", exception);
        }
    }

    private static void loadQueue() {
        synchronized (QUEUE_LOCK) {
            QUEUE.clear();
            if (!Files.isRegularFile(QUEUE_FILE)) return;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(QUEUE_FILE)))) {
                if (in.readInt() != QUEUE_MAGIC || in.readInt() != QUEUE_VERSION) throw new IOException("Unsupported crafting queue file");
                int count = Math.max(0, Math.min(100000, in.readInt()));
                for (int i = 0; i < count; i++) {
                    UUID id = new UUID(in.readLong(), in.readLong());
                    UUID player = new UUID(in.readLong(), in.readLong());
                    QUEUE.add(new QueueEntry(id, player, norm(in.readUTF()), in.readUTF(), in.readLong(), in.readLong()));
                }
            } catch (EOFException exception) {
                LOG.log(Level.SEVERE, "Crafting queue file is truncated", exception);
                QUEUE.clear();
            } catch (IOException exception) {
                LOG.log(Level.SEVERE, "Failed to load crafting queue", exception);
                QUEUE.clear();
            }
        }
    }

    private static long stationStamp() {
        long latest = 0L;
        if (!Files.isDirectory(STATION_ROOT)) return latest;
        try (var paths = Files.walk(STATION_ROOT)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) latest = Math.max(latest, Files.getLastModifiedTime(path).toMillis());
        } catch (IOException ignored) { }
        return latest;
    }

    private static String first(Map<String, String> params, String... keys) {
        for (String key : keys) {
            String value = params.get(key);
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private static List<?> list(Object value) {
        if (value instanceof List<?> list) return list;
        if (value instanceof Collection<?> collection) return new ArrayList<>(collection);
        return value == null ? List.of() : List.of(value);
    }

    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) { return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of(); }
    private static String string(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static int integer(Object value, int fallback) { if (value instanceof Number number) return number.intValue(); try { return Integer.parseInt(String.valueOf(value).trim()); } catch (Exception ignored) { return fallback; } }
    private static double decimal(Object value, double fallback) { if (value instanceof Number number) return number.doubleValue(); try { return Double.parseDouble(String.valueOf(value).trim()); } catch (Exception ignored) { return fallback; } }
    private static boolean bool(Object value, boolean fallback) { if (value instanceof Boolean bool) return bool; if (value == null) return fallback; return Boolean.parseBoolean(String.valueOf(value)); }
    private static String removeExtension(String name) { int dot = name.lastIndexOf('.'); return dot < 0 ? name : name.substring(0, dot); }
    private static String norm(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-'); }
    private static String normType(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'); }

    private static final class Session {
        final Station station;
        final SimpleInventory inventory;
        final Map<Integer, String> recipeSlots = new HashMap<>();
        final Map<Integer, UUID> queueSlots = new HashMap<>();
        int page;
        long lastRefreshSecond;
        ScreenHandler handler;
        Session(Station station, SimpleInventory inventory) { this.station = station; this.inventory = inventory; }
    }

    private record Station(String id, String name, int maxQueueSize, Map<String, Recipe> recipes) {}
    private record Recipe(String id, List<Condition> conditions, List<Ingredient> ingredients, Output output,
                          int craftingTimeSeconds, boolean outputItem, boolean silentCraft, boolean hideWhenLocked) {}
    private record Condition(String type, Map<String, String> params) {}
    private record Ingredient(String kind, String itemType, String id, int amount, int level) {
        String key() { return kind + ':' + itemType + ':' + id.toLowerCase(Locale.ROOT) + ':' + level; }
    }
    private record Output(String kind, String itemType, String id, int amount) {}
    private record QueueEntry(UUID id, UUID player, String station, String recipe, long started, long completion) {}
    private record Call(String name, Map<String, String> params) {}

    /** Optional economy bridge. No service is fabricated when a real economy is absent. */
    private static final class EconomyAccess {
        static boolean hasAndWithdraw(ServerPlayerEntity player, double amount, boolean withdraw) {
            if (amount <= 0.0) return true;
            // BEconomy is supported reflectively to keep this Fabric port server-only and dependency-optional.
            try {
                Class<?> apiClass = Class.forName("org.beconomy.api.BEconomy");
                for (Method method : apiClass.getMethods()) {
                    String name = method.getName().toLowerCase(Locale.ROOT);
                    if (!name.equals("balance") && !name.equals("getbalance")) continue;
                    Object target = java.lang.reflect.Modifier.isStatic(method.getModifiers()) ? null : apiClass.getDeclaredConstructor().newInstance();
                    Object result = method.getParameterCount() == 1 ? method.invoke(target, player.getUuid()) : null;
                    if (result instanceof Number number) return number.doubleValue() >= amount && !withdraw;
                }
            } catch (ReflectiveOperationException ignored) { }
            return false;
        }
    }
}
