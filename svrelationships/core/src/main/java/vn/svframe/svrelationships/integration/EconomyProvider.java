package vn.svframe.svrelationships.integration;

import java.math.BigDecimal;
import java.util.UUID;

public interface EconomyProvider {
    String id();
    boolean available();
    BigDecimal balance(UUID playerId, String currencyId);
    boolean withdraw(UUID playerId, String currencyId, BigDecimal amount);
    boolean deposit(UUID playerId, String currencyId, BigDecimal amount);
    boolean set(UUID playerId, String currencyId, BigDecimal amount);
}
