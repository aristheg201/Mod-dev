package io.github.aristheg201.cobblemonworld.shop;

import org.krripe.beconomy.api.BEconomy;
import org.krripe.beconomy.api.EconomyAPI;
import java.math.BigDecimal;
import java.util.UUID;

/** Loaded only when BEconomy is installed. Explicit lookup prevents its primary-currency fallback. */
public final class BEconomyAccount implements BeastCoinAccount {
    private final EconomyAPI api;
    private final String currency;

    public BEconomyAccount() {
        if (!BEconomy.INSTANCE.isInitialized()) throw new IllegalStateException("BEconomy is not initialized");
        api = BEconomy.INSTANCE.getAPI();
        currency = api.getCurrencyList().stream()
                .map(c -> c.getCurrencyType())
                .filter(c -> "BeastCoin".equalsIgnoreCase(c))
                .findFirst().orElseThrow(() -> new IllegalStateException("BEconomy has no BeastCoin currency"));
        checkCurrency();
    }

    private void checkCurrency() {
        if (!api.currencyExists(currency)) throw new IllegalStateException("BeastCoin currency disappeared");
    }
    public BigDecimal balance(UUID player) { checkCurrency(); return api.getBalance(player, currency); }
    public boolean debit(UUID player, BigDecimal amount) {
        checkCurrency();
        if (amount.signum() <= 0) throw new IllegalArgumentException("Non-positive debit");
        return api.subtractBalance(player, amount, currency);
    }
    public void refund(UUID player, BigDecimal amount) { checkCurrency(); api.addBalance(player, amount, currency); }
}
