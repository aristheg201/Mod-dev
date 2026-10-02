package vn.svarcade.tcg.integration;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import vn.svarcade.tcg.economy.CardWorldsCurrency;

/** Card Worlds' authoritative adapter for the server's BEconomy 1.5 currencies. */
public final class BEconomyCardWorlds {
    public static final String BEAST=CardWorldsCurrency.BEAST;
    public static final String HUNTER=CardWorldsCurrency.HUNTER;
    public static final int PULL_BEAST=CardWorldsCurrency.PULL_BEAST;
    public static final int PULL_HUNTER=CardWorldsCurrency.PULL_HUNTER;

    public record PullPrice(String currency,int amount) {}
    public record Reward(int amount,String currency) {}

    private static final Logger LOG=LoggerFactory.getLogger("cardworlds-beconomy");
    private static final Object LOCK=new Object();
    private static final Set<String> REWARD_RECEIPTS=ConcurrentHashMap.newKeySet();
    private static final Map<String,String> NOTICES=new ConcurrentHashMap<>();
    private static volatile boolean receiptsLoaded;
    private static volatile Object cachedApi;

    public static PullPrice pullPrice(String request) {
        String prefix=request==null?"":request.substring(0,Math.max(0,request.indexOf(':'))).toLowerCase(Locale.ROOT);
        return switch(prefix) {
            case BEAST -> new PullPrice(BEAST,PULL_BEAST);
            case HUNTER -> new PullPrice(HUNTER,PULL_HUNTER);
            default -> throw new IllegalArgumentException("Choose Beast Coin or Hunter Coin for this pull.");
        };
    }

    public static void chargePull(String owner,String request) {
        PullPrice price=pullPrice(request);
        UUID player=UUID.fromString(owner);
        Object api=api();
        requireCurrency(api,price.currency());
        try {
            Method subtract=find(api.getClass(),"subtractBalance",3);
            boolean paid=Boolean.TRUE.equals(subtract.invoke(api,player,BigDecimal.valueOf(price.amount()),price.currency()));
            if(!paid) throw new IllegalArgumentException("Not enough "+(price.currency().equals(BEAST)?"Beast Coin.":"Hunter Coin."));
        } catch(IllegalArgumentException e) { throw e; }
        catch(Throwable e) { throw new IllegalStateException("BEconomy pull payment failed",e); }
    }

    public static void refundPull(String owner,String request) {
        PullPrice price=pullPrice(request);
        try { add(UUID.fromString(owner),price.amount(),price.currency()); }
        catch(RuntimeException e) { LOG.error("CARDWORLDS_ECONOMY_REFUND_FAILED owner={} currency={} amount={}",owner,price.currency(),price.amount(),e); }
    }

    public static int rewardAmount(boolean npc,String difficulty,boolean ranked,boolean won) {
        if(npc) return switch(difficulty==null?"":difficulty.toUpperCase(Locale.ROOT)) {
            case "EASY" -> 0;
            case "NORMAL" -> won?15:0;
            case "HARD" -> won?20:5;
            default -> 0;
        };
        return ranked?(won?50:25):(won?30:15);
    }

    public static void rewardMatch(String matchId,String a,String b,boolean npc,String difficulty,boolean ranked,int winnerSeat) {
        if(npc) {
            rewardOnce(matchId,UUID.fromString(a),rewardAmount(true,difficulty,false,winnerSeat==0));
            return;
        }
        rewardOnce(matchId,UUID.fromString(a),rewardAmount(false,"",ranked,winnerSeat==0));
        rewardOnce(matchId,UUID.fromString(b),rewardAmount(false,"",ranked,winnerSeat==1));
    }

    private static void rewardOnce(String matchId,UUID player,int amount) {
        if(amount<=0) { NOTICES.put(matchId+"|"+player,""); return; }
        loadReceipts();
        String receipt=matchId+"|"+player+"|"+BEAST+"|"+amount;
        synchronized(LOCK) {
            if(REWARD_RECEIPTS.contains(receipt)) return;
            add(player,amount,BEAST);
            persistReceipt(receipt);
            REWARD_RECEIPTS.add(receipt);
        }
        NOTICES.put(matchId+"|"+player,"+"+amount+" Beast Coin");
    }

    public static String resultMessage(String base,String matchId,String player) {
        String reward=NOTICES.remove(matchId+"|"+player);
        return reward==null||reward.isBlank()?base:base+" · "+reward;
    }

    public static BigDecimal balance(String owner,String currency) {
        try {
            Object api=api();requireCurrency(api,currency);
            Object value=find(api.getClass(),"getBalance",2).invoke(api,UUID.fromString(owner),currency);
            return value instanceof BigDecimal d?d:BigDecimal.ZERO;
        } catch(Throwable e) { return BigDecimal.ZERO; }
    }

    public static void verifyRuntime() {
        Object api=api();
        boolean beast=currencyExists(api,BEAST),hunter=currencyExists(api,HUNTER);
        if(!beast||!hunter) throw new IllegalStateException("Required BEconomy currencies missing: beastcoin="+beast+" huntercoin="+hunter);
        LOG.info("CARDWORLDS_QA_ECONOMY authority=dedicated_server beastcoin={} huntercoin={}",beast,hunter);
        LOG.info("CARDWORLDS_QA_GACHA beastcoin={} huntercoin={} exchange_display=25000:1",PULL_BEAST,PULL_HUNTER);
        LOG.info("CARDWORLDS_QA_REWARDS easy_win={} easy_loss={} normal_win={} normal_loss={} hard_win={} hard_loss={} pvp_win={} pvp_loss={} ranked_win={} ranked_loss={} currency={} hunter_reward=0",
            rewardAmount(true,"EASY",false,true),rewardAmount(true,"EASY",false,false),
            rewardAmount(true,"NORMAL",false,true),rewardAmount(true,"NORMAL",false,false),
            rewardAmount(true,"HARD",false,true),rewardAmount(true,"HARD",false,false),
            rewardAmount(false,"",false,true),rewardAmount(false,"",false,false),
            rewardAmount(false,"",true,true),rewardAmount(false,"",true,false),BEAST);
    }

    private static void add(UUID player,int amount,String currency) {
        Object api=api();requireCurrency(api,currency);
        try { find(api.getClass(),"addBalance",3).invoke(api,player,BigDecimal.valueOf(amount),currency); }
        catch(Throwable e) { throw new IllegalStateException("BEconomy reward failed",e); }
    }

    private static Object api() {
        Object value=cachedApi;if(value!=null)return value;
        synchronized(LOCK) {
            if(cachedApi!=null)return cachedApi;
            if(!FabricLoader.getInstance().isModLoaded("beconomy"))throw new IllegalStateException("BEconomy is required for Card Worlds rewards and pulls.");
            try {
                Class<?> bridge=Class.forName("org.krripe.beconomy.api.BEconomy");
                Object instance=bridge.getField("INSTANCE").get(null);
                if(!Boolean.TRUE.equals(bridge.getMethod("isInitialized").invoke(instance)))throw new IllegalStateException("BEconomy is not initialized.");
                cachedApi=bridge.getMethod("getAPI").invoke(instance);
                return cachedApi;
            } catch(RuntimeException e) { throw e; }
            catch(Throwable e) { throw new IllegalStateException("BEconomy 1.5 API unavailable",e); }
        }
    }

    private static boolean currencyExists(Object api,String currency) {
        try { return Boolean.TRUE.equals(find(api.getClass(),"currencyExists",1).invoke(api,currency)); }
        catch(Throwable e) { return false; }
    }
    private static void requireCurrency(Object api,String currency) {
        if(!currencyExists(api,currency))throw new IllegalStateException("BEconomy currency is not configured: "+currency);
    }

    private static final Map<String,Method> METHODS=new ConcurrentHashMap<>();
    private static Method find(Class<?> type,String name,int parameters) {
        return METHODS.computeIfAbsent(type.getName()+"#"+name+"/"+parameters,key->{
            for(Method method:type.getMethods())if(method.getName().equals(name)&&method.getParameterCount()==parameters)return method;
            throw new IllegalStateException("Missing BEconomy method "+key);
        });
    }

    private static Path receiptsFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("svarcade_tcg").resolve("beconomy-reward-receipts.txt");
    }
    private static void loadReceipts() {
        if(receiptsLoaded)return;
        synchronized(LOCK) {
            if(receiptsLoaded)return;
            try {
                Path file=receiptsFile();Files.createDirectories(file.getParent());
                if(Files.exists(file))for(String line:Files.readAllLines(file))if(!line.isBlank())REWARD_RECEIPTS.add(line.trim());
                receiptsLoaded=true;
            } catch(Exception e) { throw new IllegalStateException("Cannot load Card Worlds reward receipts",e); }
        }
    }
    private static void persistReceipt(String receipt) {
        try { Files.writeString(receiptsFile(),receipt+System.lineSeparator(),StandardOpenOption.CREATE,StandardOpenOption.APPEND); }
        catch(Exception e) { throw new IllegalStateException("Reward was not receipted; refusing ambiguous payout state.",e); }
    }

    private BEconomyCardWorlds(){}
}
