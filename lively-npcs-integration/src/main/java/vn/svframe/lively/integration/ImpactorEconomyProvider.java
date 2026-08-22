package vn.svframe.lively.integration;

import vn.svframe.lively.api.EconomyProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Optional Impactor 5.3.5 multi-currency adapter, using the real async Account API through reflection. */
final class ImpactorEconomyProvider implements EconomyProvider {
    private final Object service;
    private final Method currencies;
    private final Method primary;
    private final Method registered;
    private final Method currencyKey;
    private final Method account;
    private final Method balance;
    private final Method depositAsync;
    private final Method withdrawAsync;
    private final Method transferAsync;
    private final Method transactionSuccessful;
    private final Method transferSuccessful;
    private final boolean usable;

    private ImpactorEconomyProvider(Object service, Method currencies, Method primary, Method registered,
                                    Method currencyKey, Method account, Method balance, Method depositAsync,
                                    Method withdrawAsync, Method transferAsync, Method transactionSuccessful,
                                    Method transferSuccessful, boolean usable) {
        this.service = service; this.currencies = currencies; this.primary = primary; this.registered = registered;
        this.currencyKey = currencyKey; this.account = account; this.balance = balance;
        this.depositAsync = depositAsync; this.withdrawAsync = withdrawAsync; this.transferAsync = transferAsync;
        this.transactionSuccessful = transactionSuccessful; this.transferSuccessful = transferSuccessful; this.usable = usable;
    }

    static EconomyProvider create() {
        try {
            Class<?> serviceType = Class.forName("net.impactdev.impactor.api.economy.EconomyService");
            Class<?> currencyProviderType = Class.forName("net.impactdev.impactor.api.economy.currency.CurrencyProvider");
            Class<?> currencyType = Class.forName("net.impactdev.impactor.api.economy.currency.Currency");
            Class<?> accountType = Class.forName("net.impactdev.impactor.api.economy.accounts.Account");
            Class<?> transactionType = Class.forName("net.impactdev.impactor.api.economy.transactions.EconomyTransaction");
            Class<?> transferType = Class.forName("net.impactdev.impactor.api.economy.transactions.EconomyTransferTransaction");
            Object service = serviceType.getMethod("instance").invoke(null);
            return new ImpactorEconomyProvider(service,
                    serviceType.getMethod("currencies"),
                    currencyProviderType.getMethod("primary"),
                    currencyProviderType.getMethod("registered"),
                    currencyType.getMethod("key"),
                    serviceType.getMethod("account", currencyType, UUID.class),
                    accountType.getMethod("balance"),
                    accountType.getMethod("depositAsync", BigDecimal.class),
                    accountType.getMethod("withdrawAsync", BigDecimal.class),
                    accountType.getMethod("transferAsync", accountType, BigDecimal.class),
                    transactionType.getMethod("successful"), transferType.getMethod("successful"), true);
        } catch (ReflectiveOperationException | LinkageError error) {
            return new ImpactorEconomyProvider(null, null, null, null, null, null, null, null, null, null, null, null, false);
        }
    }

    @Override public String id() { return "impactor"; }
    @Override public boolean available() { return usable && service != null; }
    @Override public boolean supports(String currency) {
        if (!available()) return false;
        try { return resolveCurrency(currency) != null; }
        catch (RuntimeException ignored) { return false; }
    }

    @Override public CompletableFuture<BigDecimal> balance(UUID actor, String currency) {
        Object selected = requireCurrency(currency);
        return account(selected, actor).thenApply(value -> (BigDecimal) invoke(balance, value));
    }

    @Override public CompletableFuture<Boolean> deposit(UUID actor, BigDecimal amount, String currency) {
        Object selected = requireCurrency(currency);
        return account(selected, actor).thenCompose(value -> future(invoke(depositAsync, value, amount)))
                .thenApply(transaction -> Boolean.TRUE.equals(invoke(transactionSuccessful, transaction)));
    }

    @Override public CompletableFuture<Boolean> withdraw(UUID actor, BigDecimal amount, String currency) {
        Object selected = requireCurrency(currency);
        return account(selected, actor).thenCompose(value -> future(invoke(withdrawAsync, value, amount)))
                .thenApply(transaction -> Boolean.TRUE.equals(invoke(transactionSuccessful, transaction)));
    }

    @Override public CompletableFuture<Boolean> transfer(UUID from, UUID to, BigDecimal amount, String currency) {
        if (from.equals(to)) return CompletableFuture.completedFuture(false);
        Object selected = requireCurrency(currency);
        CompletableFuture<Object> source = account(selected, from);
        CompletableFuture<Object> target = account(selected, to);
        return source.thenCombine(target, Accounts::new)
                .thenCompose(pair -> future(invoke(transferAsync, pair.from(), pair.to(), amount)))
                .thenApply(transaction -> Boolean.TRUE.equals(invoke(transferSuccessful, transaction)));
    }

    private CompletableFuture<Object> account(Object currency, UUID actor) {
        return future(invoke(account, service, currency, actor));
    }

    private Object requireCurrency(String currency) {
        Object value = resolveCurrency(currency);
        if (value == null) throw new IllegalArgumentException("Impactor currency not found: " + currency);
        return value;
    }

    private Object resolveCurrency(String requested) {
        Object provider = invoke(currencies, service);
        String target = requested == null ? "" : requested.trim().toLowerCase(Locale.ROOT);
        if (target.isBlank() || target.equals("default") || target.equals("primary")) return invoke(primary, provider);
        Object registeredValue = invoke(registered, provider);
        if (!(registeredValue instanceof Set<?> currenciesSet)) return null;
        Object suffixMatch = null;
        for (Object currency : currenciesSet) {
            String key = String.valueOf(invoke(currencyKey, currency)).toLowerCase(Locale.ROOT);
            if (key.equals(target)) return currency;
            if (!target.contains(":") && key.endsWith(":" + target)) suffixMatch = currency;
        }
        return suffixMatch;
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<Object> future(Object value) {
        if (!(value instanceof CompletableFuture<?> future)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Impactor API did not return CompletableFuture"));
        }
        return (CompletableFuture<Object>) future;
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            throw new CompletionException(cause);
        } catch (ReflectiveOperationException error) { throw new CompletionException(error); }
    }

    private record Accounts(Object from, Object to) {}
}
