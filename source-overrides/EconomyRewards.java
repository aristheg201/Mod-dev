package vn.svarcade.tcg.integration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Optional hard-PvE payout bridge.
 *
 * No economy mod is a hard dependency. When present, the bridge calls the actual public API
 * discovered from BEconomy 1.5, CobbleDollars 2.0.0 Beta 6.1 and Impactor 5.3.5.
 */
public final class EconomyRewards {
    private static final Logger LOG = LoggerFactory.getLogger("cardworlds-economy");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Provider {
        public boolean enabled = true;
        public String amount = "0";
        public String currency = "primary";
    }

    public static final class Config {
        public boolean enabled = true;
        public String payoutPolicy = "FIRST_AVAILABLE";
        public List<String> priority = new ArrayList<>(List.of("beconomy", "cobbledollars", "impactor"));
        public Provider beconomy = provider("10", "beastcoin");
        public Provider cobbledollars = provider("5000", "cobbledollars");
        public Provider impactor = provider("5000", "primary");

        private static Provider provider(String amount, String currency) {
            Provider p = new Provider();
            p.amount = amount;
            p.currency = currency;
            return p;
        }
    }

    public record PayoutResult(boolean paid, boolean duplicate, List<String> providers, String message) {}

    private final Path dir;
    private final Path configFile;
    private final Path receiptsFile;
    private final Set<String> receipts = new HashSet<>();
    private Config config;

    public EconomyRewards() {
        try {
            dir = FabricLoader.getInstance().getConfigDir().resolve("svarcade_tcg");
            configFile = dir.resolve("hard-pve-rewards.json");
            receiptsFile = dir.resolve("hard-pve-reward-receipts.txt");
            Files.createDirectories(dir);
            config = loadConfig();
            if (Files.exists(receiptsFile)) {
                for (String line : Files.readAllLines(receiptsFile)) {
                    String id = line.trim();
                    if (!id.isBlank()) receipts.add(id);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize Card Worlds economy rewards", e);
        }
    }

    public synchronized PayoutResult payHardWin(ServerPlayerEntity player, String matchId) {
        if (!config.enabled) return new PayoutResult(false, false, List.of(), "Hard Duel won. Economy rewards are disabled.");
        if (receipts.contains(matchId)) return new PayoutResult(false, true, List.of(), "Hard Duel reward was already claimed for this match.");

        List<String> paid = new ArrayList<>();
        boolean all = "ALL_ENABLED".equalsIgnoreCase(config.payoutPolicy);
        List<String> order = config.priority == null || config.priority.isEmpty()
            ? List.of("beconomy", "cobbledollars", "impactor") : config.priority;

        for (String raw : order) {
            String provider = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
            boolean ok = switch (provider) {
                case "beconomy" -> payoutBEconomy(player);
                case "cobbledollars" -> payoutCobbleDollars(player);
                case "impactor" -> payoutImpactor(player);
                default -> false;
            };
            if (ok) {
                paid.add(provider);
                if (!all) break;
            }
        }

        if (paid.isEmpty()) {
            return new PayoutResult(false, false, List.of(),
                "Hard Duel won, but no enabled economy provider was available. Check hard-pve-rewards.json.");
        }

        try {
            Files.writeString(receiptsFile, matchId + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            receipts.add(matchId);
        } catch (Exception e) {
            LOG.error("Reward paid but receipt could not be persisted for {}", matchId, e);
        }
        return new PayoutResult(true, false, List.copyOf(paid),
            "Hard Duel reward paid via " + String.join(", ", paid) + ".");
    }

    private boolean payoutBEconomy(ServerPlayerEntity player) {
        Provider cfg = normalize(config.beconomy, "10", "beastcoin");
        if (!cfg.enabled || !FabricLoader.getInstance().isModLoaded("beconomy")) return false;
        try {
            BigDecimal amount = positiveDecimal(cfg.amount);
            if (amount.signum() <= 0) return false;
            Class<?> bridge = Class.forName("org.krripe.beconomy.api.BEconomy");
            Object instance = bridge.getField("INSTANCE").get(null);
            Object initialized = bridge.getMethod("isInitialized").invoke(instance);
            if (initialized instanceof Boolean b && !b) return false;
            Object api = bridge.getMethod("getAPI").invoke(instance);
            Method fallback = find(api.getClass(), "getCurrencyTypeOrFallback", 1);
            String currency = (String) fallback.invoke(api, cfg.currency == null ? "beastcoin" : cfg.currency);
            Method add = find(api.getClass(), "addBalance", 3);
            add.invoke(api, player.getUuid(), amount, currency);
            return true;
        } catch (Throwable e) {
            LOG.warn("BEconomy hard-duel payout failed", e);
            return false;
        }
    }

    private boolean payoutCobbleDollars(ServerPlayerEntity player) {
        Provider cfg = normalize(config.cobbledollars, "5000", "cobbledollars");
        if (!cfg.enabled || !FabricLoader.getInstance().isModLoaded("cobbledollars")) return false;
        try {
            BigInteger amount = new BigInteger(cfg.amount.trim());
            if (amount.signum() <= 0) return false;
            Class<?> ext = Class.forName("fr.harmex.cobbledollars.common.utils.extensions.PlayerExtensionKt");
            Method earn = find(ext, "earnCobbleDollars", 3);
            Object result = earn.invoke(null, player, amount, true);
            return !(result instanceof Boolean b) || b;
        } catch (Throwable e) {
            LOG.warn("CobbleDollars hard-duel payout failed", e);
            return false;
        }
    }

    private boolean payoutImpactor(ServerPlayerEntity player) {
        Provider cfg = normalize(config.impactor, "5000", "primary");
        if (!cfg.enabled || !FabricLoader.getInstance().isModLoaded("impactor")) return false;
        try {
            BigDecimal amount = positiveDecimal(cfg.amount);
            if (amount.signum() <= 0) return false;
            Class<?> economy = Class.forName("net.impactdev.impactor.api.economy.EconomyService");
            Object service = economy.getMethod("instance").invoke(null);
            Method accountMethod = find(service.getClass(), "account", 1);
            Object future = accountMethod.invoke(service, player.getUuid());
            Object account = future instanceof CompletableFuture<?> f ? f.join() : future;
            Method deposit = find(account.getClass(), "deposit", 1);
            Object tx = deposit.invoke(account, amount);
            Method successful = find(tx.getClass(), "successful", 0);
            return Boolean.TRUE.equals(successful.invoke(tx));
        } catch (Throwable e) {
            LOG.warn("Impactor hard-duel payout failed", e);
            return false;
        }
    }

    private Config loadConfig() throws Exception {
        Config defaults = new Config();
        if (!Files.exists(configFile)) {
            Files.writeString(configFile, JSON.toJson(defaults), StandardOpenOption.CREATE_NEW);
            return defaults;
        }
        Config loaded = JSON.fromJson(Files.readString(configFile), Config.class);
        if (loaded == null) return defaults;
        if (loaded.priority == null || loaded.priority.isEmpty()) loaded.priority = defaults.priority;
        if (loaded.payoutPolicy == null || loaded.payoutPolicy.isBlank()) loaded.payoutPolicy = defaults.payoutPolicy;
        loaded.beconomy = normalize(loaded.beconomy, "10", "beastcoin");
        loaded.cobbledollars = normalize(loaded.cobbledollars, "5000", "cobbledollars");
        loaded.impactor = normalize(loaded.impactor, "5000", "primary");
        return loaded;
    }

    private static Provider normalize(Provider p, String amount, String currency) {
        if (p == null) p = new Provider();
        if (p.amount == null || p.amount.isBlank()) p.amount = amount;
        if (p.currency == null || p.currency.isBlank()) p.currency = currency;
        return p;
    }

    private static BigDecimal positiveDecimal(String raw) {
        return new BigDecimal(raw == null || raw.isBlank() ? "0" : raw.trim());
    }

    private static Method find(Class<?> type, String name, int parameters) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameters) return method;
        }
        throw new IllegalStateException("Missing method " + type.getName() + "#" + name + "/" + parameters);
    }
}
