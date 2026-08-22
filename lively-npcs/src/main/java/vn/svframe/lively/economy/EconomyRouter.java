package vn.svframe.lively.economy;

import vn.svframe.lively.api.EconomyProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Multi-provider currency router. Provider choice is configuration/data, never a hard Core dependency. */
public final class EconomyRouter implements AutoCloseable {
    public record Resolution(String providerId, String currency, EconomyProvider provider) {}

    private final ConcurrentHashMap<String, EconomyProvider> providers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> routes = new ConcurrentHashMap<>();

    public void register(EconomyProvider provider) {
        if (provider == null) return;
        String id = normalizeProvider(provider.id());
        EconomyProvider previous = providers.put(id, provider);
        if (previous != null && previous != provider) {
            try { previous.close(); } catch (RuntimeException ignored) { }
        }
    }

    public boolean unregister(String providerId) {
        EconomyProvider removed = providers.remove(normalizeProvider(providerId));
        if (removed == null) return false;
        try { removed.close(); } catch (RuntimeException ignored) { }
        return true;
    }

    public void clearRoutes() { routes.clear(); }

    public void route(String currency, String providerId) {
        String key = normalizeCurrency(currency);
        String provider = normalizeProvider(providerId);
        if (key.isBlank()) throw new IllegalArgumentException("currency required");
        if (provider.equals("auto")) routes.remove(key); else routes.put(key, provider);
    }

    public Map<String, String> routes() { return Map.copyOf(routes); }

    public Map<String, Boolean> providers() {
        LinkedHashMap<String, Boolean> result = new LinkedHashMap<>();
        providers.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put(entry.getKey(), safeAvailable(entry.getValue())));
        return Map.copyOf(result);
    }

    public Optional<Resolution> resolve(String requestedCurrency) {
        String currency = normalizeCurrency(requestedCurrency);
        if (currency.isBlank()) return Optional.empty();

        int separator = currency.indexOf(':');
        if (separator > 0) {
            String prefix = currency.substring(0, separator);
            EconomyProvider explicit = providers.get(prefix);
            if (explicit != null) {
                String routedCurrency = currency.substring(separator + 1);
                if (usable(explicit, routedCurrency)) return Optional.of(new Resolution(prefix, routedCurrency, explicit));
                return Optional.empty();
            }
        }

        String configured = routes.get(currency);
        if (configured != null) {
            EconomyProvider provider = providers.get(configured);
            if (usable(provider, currency)) return Optional.of(new Resolution(configured, currency, provider));
            return Optional.empty();
        }

        if (isCobbleDollarAlias(currency)) {
            EconomyProvider provider = providers.get("cobbledollars");
            if (usable(provider, currency)) return Optional.of(new Resolution("cobbledollars", currency, provider));
        }

        List<Map.Entry<String, EconomyProvider>> candidates = new ArrayList<>(providers.entrySet());
        candidates.sort(Comparator.comparingInt((Map.Entry<String, EconomyProvider> entry) -> fallbackPriority(entry.getKey()))
                .thenComparing(Map.Entry::getKey));
        for (Map.Entry<String, EconomyProvider> entry : candidates) {
            if (usable(entry.getValue(), currency)) return Optional.of(new Resolution(entry.getKey(), currency, entry.getValue()));
        }
        return Optional.empty();
    }

    public CompletableFuture<BigDecimal> balance(UUID actor, String currency) {
        return resolve(currency).map(resolution -> resolution.provider().balance(actor, resolution.currency()))
                .orElseGet(() -> missing(currency));
    }

    public CompletableFuture<Boolean> deposit(UUID actor, BigDecimal amount, String currency) {
        validateAmount(amount);
        return resolve(currency).map(resolution -> resolution.provider().deposit(actor, amount, resolution.currency()))
                .orElseGet(() -> missing(currency));
    }

    public CompletableFuture<Boolean> withdraw(UUID actor, BigDecimal amount, String currency) {
        validateAmount(amount);
        return resolve(currency).map(resolution -> resolution.provider().withdraw(actor, amount, resolution.currency()))
                .orElseGet(() -> missing(currency));
    }

    public CompletableFuture<Boolean> transfer(UUID from, UUID to, BigDecimal amount, String currency) {
        validateAmount(amount);
        return resolve(currency).map(resolution -> resolution.provider().transfer(from, to, amount, resolution.currency()))
                .orElseGet(() -> missing(currency));
    }

    @Override public void close() {
        for (EconomyProvider provider : List.copyOf(providers.values())) {
            try { provider.close(); } catch (RuntimeException ignored) { }
        }
        providers.clear();
        routes.clear();
    }

    private static boolean usable(EconomyProvider provider, String currency) {
        if (provider == null || !safeAvailable(provider)) return false;
        try { return provider.supports(currency); }
        catch (RuntimeException ignored) { return false; }
    }

    private static boolean safeAvailable(EconomyProvider provider) {
        try { return provider.available(); }
        catch (RuntimeException ignored) { return false; }
    }

    private static int fallbackPriority(String providerId) {
        return switch (providerId) {
            case "beconomy" -> 10;
            case "impactor" -> 20;
            case "cobbledollars" -> 30;
            default -> 100;
        };
    }

    private static boolean isCobbleDollarAlias(String currency) {
        return currency.equals("cobbledollar") || currency.equals("cobbledollars") || currency.equals("cd");
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(new BigDecimal("10000000000000")) > 0) {
            throw new IllegalArgumentException("invalid economy amount");
        }
    }

    private static <T> CompletableFuture<T> missing(String currency) {
        return CompletableFuture.failedFuture(new IllegalStateException("no economy provider available for currency " + currency));
    }

    private static String normalizeCurrency(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeProvider(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        if (normalized.isBlank()) throw new IllegalArgumentException("provider id required");
        return normalized;
    }
}
