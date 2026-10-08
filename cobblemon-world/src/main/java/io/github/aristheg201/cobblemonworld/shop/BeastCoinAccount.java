package io.github.aristheg201.cobblemonworld.shop;

import java.math.BigDecimal;
import java.util.UUID;

public interface BeastCoinAccount {
    BigDecimal balance(UUID player);
    boolean debit(UUID player, BigDecimal amount);
    void refund(UUID player, BigDecimal amount);
}
