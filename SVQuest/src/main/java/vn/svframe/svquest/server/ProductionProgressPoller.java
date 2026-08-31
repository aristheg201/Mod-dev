package vn.svframe.svquest.server;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svquest.SVQuest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Polls production mod state where those mods do not expose a stable public event API.
 * Every probe is isolated: one broken/updated optional mod cannot crash SVQuest or the server.
 */
public final class ProductionProgressPoller {
    private final MinecraftServer server;
    private final QuestEngine engine;
    private final Map<UUID, Snapshot> snapshots = new HashMap<>();
    private final Map<UUID, Object> previousRaids = new HashMap<>();
    private final Set<String> fusionSpecies = new HashSet<>();
    private int ticks;

    private static final class Snapshot {
        Integer rankedWins, towerWins, huntCompletions, researchCompletions, expeditionXp;
        Long wonderDate, arcadeCoins;
        BigDecimal stsMoney;
        Long showcaseWins;
        Integer showcaseEntries;
        boolean fusionSeen;
    }

    public ProductionProgressPoller(MinecraftServer server, QuestEngine engine) {
        this.server = server;
        this.engine = engine;
        discoverFusionSpecies();
    }

    public void tick() {
        if (++ticks % 40 != 0) return; // two seconds: responsive without hammering DB-backed mods
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Snapshot s = snapshots.computeIfAbsent(player.getUuid(), k -> new Snapshot());
            probe("Ranked", () -> pollRanked(player, s));
            probe("BattleTower", () -> pollTower(player, s));
            probe("Hunts", () -> pollHunts(player, s));
            probe("Research", () -> pollResearch(player, s));
            probe("WonderTrade", () -> pollWonderTrade(player, s));
            probe("UltraSTS", () -> pollSts(player, s));
            probe("Expeditions", () -> pollExpeditions(player, s));
            probe("Showcase", () -> pollShowcase(player, s));
            probe("Minigames", () -> pollMinigames(player, s));
            probe("NovaRaids", () -> pollNovaRaids(player));
            probe("Fusion", () -> pollFusion(player, s));
        }
    }

    public void onJoin(ServerPlayerEntity player) {
        snapshots.remove(player.getUuid()); // first poll becomes baseline, never awards old activity by accident
        previousRaids.remove(player.getUuid());
    }

    public void onQuit(ServerPlayerEntity player) {
        snapshots.remove(player.getUuid());
        previousRaids.remove(player.getUuid());
    }

    private void pollRanked(ServerPlayerEntity player, Snapshot s) throws Exception {
        if (!loaded("cobblemon_ranked")) return;
        Class<?> root = Class.forName("cn.kurt6.cobblemon_ranked.CobblemonRanked");
        Object dao = root.getField("rankDao").get(null);
        Object season = root.getField("seasonManager").get(null);
        Object config = root.getField("config").get(null);
        if (dao == null || season == null || config == null) return;
        int seasonId = intValue(invoke(season, "getCurrentSeasonId"));
        String format = String.valueOf(invoke(config, "getDefaultFormat"));
        Method get = dao.getClass().getMethod("getPlayerData", UUID.class, int.class, String.class);
        Object data = get.invoke(dao, player.getUuid(), seasonId, format);
        if (data == null) return;
        int wins = intValue(invoke(data, "getWins"));
        int elo = intValue(invoke(data, "getElo"));
        if (s.rankedWins != null && wins > s.rankedWins) engine.signal(player, "ranked_win", wins - s.rankedWins);
        s.rankedWins = wins;
        if (elo >= 3000) engine.metric(player, "ranked_high", 1);
    }

    private void pollTower(ServerPlayerEntity player, Snapshot s) throws Exception {
        if (!loaded("cobblemon_battle_tower")) return;
        Class<?> manager = Class.forName("battle.tower.data.TowerDataManager");
        Object data = manager.getMethod("getPlayerData", UUID.class).invoke(null, player.getUuid());
        if (data == null) return;
        int wins = intValue(invoke(data, "getTotalWins"));
        if (s.towerWins != null && wins > s.towerWins) engine.signal(player, "battle_tower_win", wins - s.towerWins);
        s.towerWins = wins;
    }

    private void pollHunts(ServerPlayerEntity player, Snapshot s) throws Exception {
        if (!loaded("cobblehunts")) return;
        Class<?> root = Class.forName("com.cobblehunts.CobbleHunts");
        Object instance = root.getField("INSTANCE").get(null);
        Object data = root.getMethod("getPlayerData", ServerPlayerEntity.class).invoke(instance, player);
        if (data == null) return;
        int used = size(invoke(data, "getUsedPokemon"));
        int globals = size(invoke(data, "getCompletedGlobalHunts"));
        int completed = Math.max(used, globals);
        if (s.huntCompletions != null && completed > s.huntCompletions) engine.signal(player, "hunt_complete", completed - s.huntCompletions);
        s.huntCompletions = completed;
    }

    private void pollResearch(ServerPlayerEntity player, Snapshot s) throws Exception {
        if (!loaded("cobblemonresearchtasks")) return;
        Class<?> managerClass = Class.forName("github.jorgaomc.storage.MasteryManager");
        Object manager = managerClass.getMethod("get").invoke(null);
        String json = String.valueOf(managerClass.getMethod("buildPlayerSnapshotJson", UUID.class).invoke(manager, player.getUuid()));
        int completed = occurrences(json, "\"completed\":true") + occurrences(json, "\"taskCompleted\":true");
        if (s.researchCompletions != null && completed > s.researchCompletions) engine.signal(player, "research_complete", completed - s.researchCompletions);
        s.researchCompletions = completed;
    }

    private void pollWonderTrade(ServerPlayerEntity player, Snapshot s) throws Exception {
        if (!loaded("wondertrade")) return;
        Class<?> factory = Class.forName("com.kingpixel.wondertrade.database.DatabaseClientFactory");
        Object db = factory.getField("databaseClient").get(null);
        if (db == null) return;
        Object info = db.getClass().getMethod("getUserInfo", ServerPlayerEntity.class).invoke(db, player);
        if (info == null) return;
        long date = longValue(invoke(info, "getDate"));
        if (s.wonderDate != null && date > s.wonderDate) engine.signal(player, "wonder_trade");
        s.wonderDate = date;
    }

    private void pollSts(ServerPlayerEntity player, Snapshot s) throws Exception {
        if (!loaded("ultrasts")) return;
        Class<?> root = Class.forName("com.kingpixel.ultrasts.UltraSTS");
        Object db = root.getField("database").get(null);
        if (db == null) return;
        Object user = db.getClass().getMethod("getUser", ServerPlayerEntity.class).invoke(db, player);
        Object gains = invoke(user, "getMoneyGained");
        BigDecimal total = BigDecimal.ZERO;
        if (gains instanceof Map<?, ?> map) for (Object v : map.values()) if (v instanceof BigDecimal b) total = total.add(b);
        if (s.stsMoney != null && total.compareTo(s.stsMoney) > 0) engine.signal(player, "sts_trade");
        s.stsMoney = total;
    }

    private void pollExpeditions(ServerPlayerEntity player, Snapshot s) throws Exception {
        if (!loaded("cobblemon_expeditions")) return;
        Class<?> root = Class.forName("com.cobblemonexpeditions.CobblemonExpeditions");
        Object instance = root.getField("INSTANCE").get(null);
        Object manager = invoke(instance, "getManager");
        if (manager == null) return;
        Object data = manager.getClass().getMethod("get", UUID.class).invoke(manager, player.getUuid());
        if (data == null) return;
        int xp = intValue(invoke(data, "getRankXP"));
        if (s.expeditionXp != null && xp > s.expeditionXp) engine.signal(player, "expedition_complete");
        s.expeditionXp = xp;
    }

    private void pollShowcase(ServerPlayerEntity player, Snapshot s) throws Exception {
        if (!loaded("cobblemon_showcase")) return;
        Object entrypoint = findMainEntrypoint("com.svframe.showcase.CobblemonShowcase");
        if (entrypoint == null) return;
        Field serviceField = entrypoint.getClass().getDeclaredField("showcases");
        serviceField.setAccessible(true);
        Object service = serviceField.get(entrypoint);
        if (service == null) return;
        long wins = longValue(service.getClass().getMethod("wins", UUID.class).invoke(service, player.getUuid()));
        int entries = size(service.getClass().getMethod("byOwner", UUID.class).invoke(service, player.getUuid()));
        boolean increased = (s.showcaseWins != null && wins > s.showcaseWins) || (s.showcaseEntries != null && entries > s.showcaseEntries);
        if (increased) engine.signal(player, "showcase_complete");
        s.showcaseWins = wins;
        s.showcaseEntries = entries;
    }

    private void pollMinigames(ServerPlayerEntity player, Snapshot s) throws Exception {
        if (!loaded("cobblemonminigames")) return;
        Class<?> economy = Class.forName("com.barto.cobblemonminigames.arcade.economy.CoinEconomy");
        Object instance = economy.getField("INSTANCE").get(null);
        long coins = longValue(economy.getMethod("countArcadeCoins", ServerPlayerEntity.class).invoke(instance, player));
        if (s.arcadeCoins != null && coins > s.arcadeCoins) engine.signal(player, "minigame_complete");
        s.arcadeCoins = coins;
    }

    private void pollNovaRaids(ServerPlayerEntity player) throws Exception {
        if (!loaded("novaraids")) return;
        Class<?> cache = Class.forName("me.unariginal.novaraids.cache.PlayerRaidCache");
        Object current = cache.getMethod("currentRaid", ServerPlayerEntity.class).invoke(null, player);
        Object previous = previousRaids.get(player.getUuid());
        if (current != null) {
            previousRaids.put(player.getUuid(), current);
            return;
        }
        if (previous != null) {
            previousRaids.remove(player.getUuid());
            long defeated = longValue(invoke(previous, "bossDefeatTime"));
            if (defeated > 0) {
                engine.signal(player, "raid_complete");
                String category = String.valueOf(invoke(previous, "raidBossCategory")).toLowerCase(Locale.ROOT);
                if (category.contains("legend") || category.contains("myth")) engine.signal(player, "endgame_raid");
            }
        }
    }

    private void pollFusion(ServerPlayerEntity player, Snapshot s) throws Exception {
        if (!loaded("starlightfusion") || fusionSpecies.isEmpty() || s.fusionSeen) return;
        Class<?> cobblemon = Class.forName("com.cobblemon.mod.common.Cobblemon");
        Object instance = cobblemon.getField("INSTANCE").get(null);
        Object storage = invoke(instance, "getStorage");
        Object party = storage.getClass().getMethod("getParty", ServerPlayerEntity.class).invoke(storage, player);
        if (!(party instanceof Iterable<?> iterable)) return;
        for (Object pokemon : iterable) {
            Object species = invoke(pokemon, "getSpecies");
            String name = String.valueOf(invoke(species, "getName")).toLowerCase(Locale.ROOT);
            if (fusionSpecies.contains(name)) {
                s.fusionSeen = true;
                engine.signal(player, "fusion_complete");
                return;
            }
        }
    }

    private void discoverFusionSpecies() {
        if (!loaded("starlightfusion")) return;
        try {
            FabricLoader.getInstance().getModContainer("starlightfusion").ifPresent(container -> {
                for (Path root : container.getRootPaths()) {
                    Path base = root.resolve("data/cobblemon/species_additions");
                    if (!Files.exists(base)) continue;
                    try (var stream = Files.walk(base)) {
                        stream.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                            String file = p.getFileName().toString();
                            fusionSpecies.add(file.substring(0, file.length() - 5).toLowerCase(Locale.ROOT));
                        });
                    } catch (Exception ignored) { }
                }
            });
            SVQuest.LOGGER.info("SVQuest discovered {} StarlightFusion species for progression detection.", fusionSpecies.size());
        } catch (Throwable t) {
            SVQuest.LOGGER.warn("Fusion species discovery disabled safely: {}", t.toString());
        }
    }

    private Object findMainEntrypoint(String className) {
        try {
            for (ModInitializer ep : FabricLoader.getInstance().getEntrypoints("main", ModInitializer.class))
                if (ep.getClass().getName().equals(className)) return ep;
        } catch (Throwable ignored) { }
        return null;
    }

    private void probe(String name, ThrowingRunnable run) {
        try { run.run(); }
        catch (Throwable t) { SVQuest.LOGGER.debug("{} progression probe failed safely: {}", name, t.toString()); }
    }

    private boolean loaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
    private static Object invoke(Object target, String method) {
        if (target == null) return null;
        try { return target.getClass().getMethod(method).invoke(target); }
        catch (Throwable ignored) { return null; }
    }
    private static int intValue(Object v) { return v instanceof Number n ? n.intValue() : 0; }
    private static long longValue(Object v) { return v instanceof Number n ? n.longValue() : 0L; }
    private static int size(Object v) {
        if (v instanceof Collection<?> c) return c.size();
        if (v instanceof Map<?, ?> m) return m.size();
        return 0;
    }
    private static int occurrences(String source, String needle) {
        if (source == null || needle.isEmpty()) return 0;
        int count = 0, at = 0;
        while ((at = source.indexOf(needle, at)) >= 0) { count++; at += needle.length(); }
        return count;
    }
    @FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }
}
