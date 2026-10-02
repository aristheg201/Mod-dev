package vn.svarcade.tcg.economy;

/** Durable purchase stages surround provider calls; an uncertain call is never repeated. */
public final class MarketCheckout {
    public interface Wallet {
        boolean debit(String owner, long amount, String currency);
        void credit(String owner, long amount, String currency);
    }

    public static void buy(CardStore store, Wallet wallet, String buyer, String listing, String request) {
        synchronized (store) {
            var payment = store.prepareMarketPayment(buyer, listing, request);
            String state = payment.state();
            if (state.equals("COMPLETE")) return;
            if (state.equals("DECLINED")) throw new IllegalArgumentException("Not enough Beast Coin.");
            if (state.equals("DEBITING") || state.equals("CREDITING"))
                throw new IllegalStateException("Market payment outcome requires reconciliation: " + request);
            if (state.equals("RESERVED")) {
                store.marketPaymentState(request, "RESERVED", "DEBITING");
                if (!wallet.debit(buyer, payment.price(), payment.currency())) {
                    store.marketPaymentState(request, "DEBITING", "DECLINED");
                    throw new IllegalArgumentException("Not enough Beast Coin.");
                }
                store.marketPaymentState(request, "DEBITING", "DEBITED");
                state = "DEBITED";
            }
            if (state.equals("DEBITED")) {
                store.marketPaymentState(request, "DEBITED", "CREDITING");
                long proceeds = payment.price() - payment.tax();
                if (proceeds > 0) wallet.credit(payment.seller(), proceeds, payment.currency());
                store.marketPaymentState(request, "CREDITING", "CREDITED");
                state = "CREDITED";
            }
            if (!state.equals("CREDITED")) throw new IllegalStateException("Invalid market payment state: " + state);
            store.completeMarketPayment(request);
        }
    }

    private MarketCheckout() {}
}
