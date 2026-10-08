package io.github.aristheg201.cobblemonworld.shop;

import com.google.gson.Gson;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Items;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;

public final class ShopRegistry implements SimpleSynchronousResourceReloadListener {
    public static final ShopRegistry INSTANCE = new ShopRegistry();
    private static final Gson GSON = new Gson();
    private Map<String, ShopDefinition> definitions = Map.of();
    private Map<String, List<ShopEntry>> catalogs = Map.of();

    public static void register() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(INSTANCE);
    }
    public ResourceLocation getFabricId() { return ResourceLocation.fromNamespaceAndPath("cobblemonworld", "shops"); }

    public void onResourceManagerReload(ResourceManager manager) {
        Map<String, ShopDefinition> next = new LinkedHashMap<>();
        manager.listResources("shops", id -> id.getNamespace().equals("cobblemonworld") && id.getPath().endsWith(".json"))
                .forEach((id, resource) -> {
                    try (var reader = resource.openAsReader()) {
                        ShopDefinition d = GSON.fromJson(reader, ShopDefinition.class);
                        validate(d);
                        // A server-owner override has the same complete schema as a datapack definition.
                        var override = FabricLoader.getInstance().getConfigDir().resolve("cobblemonworld/shops/" + d.id() + ".json");
                        if (Files.isRegularFile(override)) {
                            try (var custom = Files.newBufferedReader(override, StandardCharsets.UTF_8)) {
                                d = GSON.fromJson(custom, ShopDefinition.class);
                            }
                        }
                        validate(d);
                        next.put(d.id(), d);
                    } catch (Exception e) {
                        CobblemonWorldMod.LOGGER.error("Shop definition {} rejected; purchases fail closed for it", id, e);
                    }
                });
        Map<String, List<ShopEntry>> rows = new LinkedHashMap<>();
        Map<String, ShopDefinition> valid = new LinkedHashMap<>();
        next.forEach((id, definition) -> {
            try { rows.put(id, generate(definition)); valid.put(id, definition); }
            catch (RuntimeException e) { CobblemonWorldMod.LOGGER.error("Shop {} disabled after catalog validation failed", id, e); }
        });
        definitions = Map.copyOf(valid);
        catalogs = Map.copyOf(rows);
        ShopService.invalidateSessions();
    }
    private static void validate(ShopDefinition d) {
        Objects.requireNonNull(d);
        if (d.id() == null || !d.id().matches("[a-z0-9_]+") || d.npc() == null) throw new IllegalArgumentException("Invalid shop identity");
        if (d.categories() == null || d.categories().isEmpty()) throw new IllegalArgumentException("Missing categories");
        if (!d.categories().contains("all") || new HashSet<>(d.categories()).size() != d.categories().size())
            throw new IllegalArgumentException("Categories must be unique and include all");
        Set<String> ids = new HashSet<>();
        if (d.rules() != null) for (var rule : d.rules()) { checkPrice(rule.price()); Pattern.compile(rule.pattern()); }
        if (d.overrides() != null) d.overrides().values().forEach(o -> { if (o.price() != null) checkPrice(o.price()); });
        if (d.entries() != null) for (var e : d.entries()) {
            checkPrice(e.price());
            if (e.quantity() < 1 || e.quantity() > 64 || e.id() == null) throw new IllegalArgumentException("Invalid bundle");
            if (e.id().isBlank() || e.id().length() > 256 || !ids.add(e.id())) throw new IllegalArgumentException("Duplicate or invalid entry id");
            if (e.categories() == null || e.categories().isEmpty() || !d.categories().containsAll(e.categories()))
                throw new IllegalArgumentException("Invalid entry categories");
            ResourceLocation.parse(e.item());
        }
    }
    private static void checkPrice(int price) {
        if (price < 1 || price > 500) throw new IllegalArgumentException("Shop price must be 1–500 BeastCoin: " + price);
    }
    private static List<ShopEntry> generate(ShopDefinition d) {
        Map<String, ShopEntry> rows = new LinkedHashMap<>();
        if (d.entries() != null) for (ShopEntry e : d.entries()) {
            rows.put(e.id(), new ShopEntry(e.id(), e.item(), e.quantity(), e.price(), e.categories(), e.requiredFlags(), e.sortOrder(), registered(e.item())));
        }
        if (d.registryNamespaces() != null && !d.registryNamespaces().isEmpty()) {
            for (var item : BuiltInRegistries.ITEM) {
                var id = BuiltInRegistries.ITEM.getKey(item);
                if (!d.registryNamespaces().contains(id.getNamespace()) || item == Items.AIR) continue;
                if (d.excludedItems() != null && d.excludedItems().contains(id.toString())) continue;
                var override = d.overrides() == null ? null : d.overrides().get(id.getPath());
                if (override != null && Boolean.TRUE.equals(override.excluded())) continue;
                List<String> categories = List.of("materials");
                int price = 25;
                if (d.rules() != null) for (var rule : d.rules()) {
                    if (Pattern.matches(rule.pattern(), id.getPath())) {
                        categories = rule.categories(); price = rule.price(); break;
                    }
                }
                if (override != null) {
                    if (override.categories() != null) categories = override.categories();
                    if (override.price() != null) price = override.price();
                }
                if (categories == null || categories.isEmpty() || !d.categories().containsAll(categories))
                    throw new IllegalArgumentException("Invalid category for " + id);
                rows.put(id.toString(), new ShopEntry(id.toString(), id.toString(), 1, price, categories, List.of(), 0, true));
            }
            CobblemonWorldMod.LOGGER.info("Shop {} enumerated {} registered {} items", d.id(), rows.size(), d.registryNamespaces());
        }
        return rows.values().stream().sorted(Comparator.comparingInt(ShopEntry::sortOrder).thenComparing(ShopEntry::id)).toList();
    }
    public static boolean registered(String item) {
        var id = ResourceLocation.tryParse(item);
        return id != null && BuiltInRegistries.ITEM.containsKey(id) && BuiltInRegistries.ITEM.get(id) != Items.AIR;
    }
    public ShopDefinition get(String id) { return definitions.get(id); }
    public ShopDefinition forNpc(String npc) { return definitions.values().stream().filter(d -> d.npc().equals(npc)).findFirst().orElse(null); }
    public List<ShopEntry> entries(String id) { return catalogs.getOrDefault(id, List.of()); }
}
