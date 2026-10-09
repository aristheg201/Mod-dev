package io.github.aristheg201.cobblemonworld.narrative;

import io.github.aristheg201.cobblemonworld.shop.*;
import io.github.aristheg201.cobblemonworld.progression.ProgressionStore;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import java.math.BigDecimal;

/** Missing economy leaves an entitlement pending; it never substitutes HunterCoin or a fake wallet. */
public final class NarrativeRewards {
    public record Payment(int amount,String state,String before){}
    public static void offer(ServerPlayer p,String id,int amount){
        if(amount<=0)return;
        var ledger=NarrativeEngine.state(p).narrative.payments;
        ledger.putIfAbsent(id,new Payment(amount,"pending",""));ProgressionStore.INSTANCE.save();retry(p);
    }
    public static void retry(ServerPlayer p){
        if(!FabricLoader.getInstance().isModLoaded("beconomy"))return;
        BeastCoinAccount account;
        try {account=new BEconomyAccount();}catch(RuntimeException|LinkageError e){return;}
        var ledger=NarrativeEngine.state(p).narrative.payments;
        for(var entry:java.util.List.copyOf(ledger.entrySet())){
            var payment=entry.getValue();if(!payment.state().equals("pending"))continue;
            try {
                var before=account.balance(p.getUUID());var amount=BigDecimal.valueOf(payment.amount());
                ledger.put(entry.getKey(),new Payment(payment.amount(),"prepared",before.toPlainString()));
                if(!ProgressionStore.INSTANCE.saveChecked()){ledger.put(entry.getKey(),payment);return;}
                account.refund(p.getUUID(),amount);
                if(account.balance(p.getUUID()).compareTo(before.add(amount))!=0)throw new IllegalStateException("Reward credit did not reconcile");
                ledger.put(entry.getKey(),new Payment(payment.amount(),"paid",before.toPlainString()));ProgressionStore.INSTANCE.save();
            }catch(RuntimeException e){CobblemonWorldMod.LOGGER.error("BeastCoin reward needs reconciliation: player={} entitlement={}; automatic credit stopped",p.getUUID(),entry.getKey(),e);return;}
        }
    }
    public static boolean scam(ServerPlayer p){
        var state=NarrativeEngine.state(p);if(state.narrative.scamPaid)return true;
        if(!FabricLoader.getInstance().isModLoaded("beconomy"))return false;
        try{
            var account=new BEconomyAccount();var product=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DEAD_BUSH);
            String result=PurchaseTransaction.execute(account,p.getUUID(),10,new InventoryGrant(p,product));
            if(!result.equals("success"))return false;state.narrative.scamPaid=true;ProgressionStore.INSTANCE.save();return true;
        }catch(RuntimeException|LinkageError e){CobblemonWorldMod.LOGGER.error("Recoverable scam transaction failed closed for {}",p.getUUID(),e);return false;}
    }
}
