package dev.svextract.compat;

import net.fabricmc.loader.api.FabricLoader;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.*;

/** Uses the supplied BEconomy 1.5 binary's verified public API without a compulsory Kotlin dependency.
 * Read operations only until upstream offers idempotent durable debit/credit receipts.
 */
public final class BEconomyAdapter {
    public record Currency(String id,String name,String symbol) {}
    private Object api;
    private Method balance,currencies;
    public void initialize() throws ReflectiveOperationException {
        if(!FabricLoader.getInstance().isModLoaded("beconomy"))return;
        Class<?> facade=Class.forName("org.krripe.beconomy.api.BEconomy");
        Object instance=facade.getField("INSTANCE").get(null);
        if(!Boolean.TRUE.equals(facade.getMethod("isInitialized").invoke(instance)))throw new IllegalStateException("BEconomy is not initialized");
        api=facade.getMethod("getAPI").invoke(instance);
        Class<?> contract=Class.forName("org.krripe.beconomy.api.EconomyAPI");
        balance=contract.getMethod("getBalance",UUID.class,String.class);currencies=contract.getMethod("getCurrencyList");
    }
    public boolean ready(){return api!=null;}
    public List<Currency> currencies() throws ReflectiveOperationException {
        if(!ready())return List.of();
        List<Currency> out=new ArrayList<>();
        for(Object currency:(List<?>)currencies.invoke(api)){
            Class<?> c=currency.getClass();out.add(new Currency((String)c.getMethod("getCurrencyType").invoke(currency),
                    (String)c.getMethod("getName").invoke(currency),(String)c.getMethod("getSymbol").invoke(currency)));
        }
        return List.copyOf(out);
    }
    public BigDecimal balance(UUID player,String currency) throws ReflectiveOperationException{
        if(!ready())throw new IllegalStateException("BEconomy adapter unavailable");return (BigDecimal)balance.invoke(api,player,currency);
    }
}
