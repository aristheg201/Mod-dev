package vn.svarcade.tcg.economy;

/**
 * Shared Card Worlds currency contract.
 *
 * This class is intentionally client/server safe and has zero BEconomy API references.
 * The dedicated server uses the ids/prices for authoritative transactions.
 * The client only uses the presentation descriptor; the actual custom models come from
 * the server-provided resource pack.
 */
public final class CardWorldsCurrency {
    public static final String BEAST="beastcoin";
    public static final String HUNTER="huntercoin";

    public static final int PULL_BEAST=150;
    public static final int PULL_HUNTER=2;
    public static final int HUNTER_EXCHANGE_VND=25_000;

    public record Visual(String itemId,int customModelData) {}
    public record Balances(java.math.BigDecimal beast,java.math.BigDecimal hunter,boolean available) {
        public static Balances unavailable(){return new Balances(null,null,false);}
    }

    // These values are the resource-pack contract, not BEconomy-owned assets.
    public static final Visual BEAST_VISUAL=new Visual("minecraft:gold_ingot",6);
    public static final Visual HUNTER_VISUAL=new Visual("minecraft:gold_ingot",2);

    public static Visual visual(String currency) {
        return switch(currency) {
            case BEAST -> BEAST_VISUAL;
            case HUNTER -> HUNTER_VISUAL;
            default -> throw new IllegalArgumentException("Unknown Card Worlds currency "+currency);
        };
    }

    private CardWorldsCurrency(){}
}
