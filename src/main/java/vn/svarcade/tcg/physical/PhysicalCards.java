package vn.svarcade.tcg.physical;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import vn.svarcade.tcg.data.*;
import vn.svarcade.tcg.economy.CardStore;
import vn.svarcade.tcg.fabric.TcgMod;
import java.util.*;

public final class PhysicalCards {
    private static TcgMod mod;
    private static Catalog catalog;
    private static CardIdentityResolver identities;
    private static Map<String,Catalog.Card> presentation=Map.of();
    public static void initialize(TcgMod owner){mod=owner;CardItems.initialize();PayloadTypeRegistry.playS2C().register(CardRedeemPop.ID,CardRedeemPop.CODEC);PhysicalLoot.initialize();BlankCapture.initialize();}
    public static void bind(Catalog value){catalog=value;identities=new CardIdentityResolver(value);PhysicalLoot.reload();}
    public static void clear(){catalog=null;identities=null;BlankCapture.clear();}
    public static Catalog catalog(){return catalog;}
    public static CardStore store(){return mod.physicalStore();}
    public static CardIdentityResolver identities(){return identities;}
    public static void presentation(Map<String,Catalog.Card> definitions){presentation=definitions==null?Map.of():definitions;}
    public static Catalog.Card definition(String id){var c=presentation.get(id);return c!=null?c:catalog==null?null:catalog.cards().get(id);}
    public static Text cardName(Catalog.Card card){
        if(card.name().startsWith("card."))return Text.translatable(card.name(),speciesName(card));
        String key="card.svarcade_tcg."+card.id()+".name";
        return Text.translatableWithFallback(key,card.name(),speciesName(card));
    }
    private static Text speciesName(Catalog.Card card){String id=card.species()==null?"":card.species();return Text.translatable("cobblemon.species."+id.substring(id.indexOf(':')+1)+".name");}
    public static void serverThread(ServerPlayerEntity player){if(!player.getServer().isOnThread())throw new IllegalStateException("Physical card mutation off server thread");}
    public static ItemStack create(String id,String finish,String origin,ServerPlayerEntity finder){
        if(catalog==null)throw new IllegalStateException("CATALOG_NOT_READY");
        return CardItems.physical(PhysicalCardData.create(catalog,id,finish,origin,finder==null?"":finder.getUuidAsString(),finder==null?"":finder.getName().getString()));
    }
    /** Uses the authoritative held stack; no GUI field or request card ID is used. */
    public static boolean redeem(ServerPlayerEntity player,Hand hand) {
        serverThread(player);ItemStack actual=player.getStackInHand(hand);
        if(catalog==null||!actual.isOf(CardItems.PHYSICAL_CARD)||actual.getCount()!=1)return false;
        try {
            var data=actual.get(CardItems.DATA);if(data==null)throw new IllegalArgumentException("PHYSICAL_DATA_MISSING");data.validate(catalog);
            ItemStack pop=actual.copy();
            CardStore.RedeemResult result=mod.physicalStore().redeemPhysical(player.getUuidAsString(),data.physicalId(),data.cardId(),data.finish(),data.origin(),data.finderUuid());
            if(!result.accepted()){player.sendMessage(Text.translatable("cardworlds.physical.already_redeemed"),false);return false;}
            // Commit precedes inventory mutation. If this decrement is lost, the same token recovers the serial.
            actual.decrement(1);player.getInventory().markDirty();player.currentScreenHandler.sendContentUpdates();
            mod.physicalSync(player);
            ServerPlayNetworking.send(player,new CardRedeemPop(pop));
            player.getServerWorld().playSound(null,player.getX(),player.getY(),player.getZ(),SoundEvents.ITEM_TOTEM_USE,SoundCategory.PLAYERS,1,1);
            player.getServerWorld().spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,player.getX(),player.getBodyY(.5),player.getZ(),40,.4,.7,.4,.15);
            player.sendMessage(Text.translatable("cardworlds.physical.redeemed",cardName(catalog.card(data.cardId()))),false);
            org.slf4j.LoggerFactory.getLogger("cardworlds-physical").info("CARDWORLDS_PHYSICAL_REDEEM player={} token={} serial={} result={}",player.getUuid(),data.physicalId(),result.serial(),result.status());
            return true;
        }catch(IllegalArgumentException e){player.sendMessage(Text.translatable("cardworlds.physical.invalid"),false);org.slf4j.LoggerFactory.getLogger("cardworlds-physical").warn("Rejected physical card for {}: {}",player.getUuid(),e.getMessage());return false;}
    }
    /** Inventory insertion may partially consume a stack; only the remainder is dropped. */
    public static boolean giveOrDrop(ServerPlayerEntity player,ItemStack stack){
        serverThread(player);player.getInventory().insertStack(stack);boolean dropped=!stack.isEmpty();
        if(dropped){ItemEntity item=new ItemEntity(player.getServerWorld(),player.getX(),player.getY()+.5,player.getZ(),stack.copy());item.setOwner(player.getUuid());item.setPickupDelay(10);if(!player.getServerWorld().spawnEntity(item))throw new IllegalStateException("Physical reward could not be dropped");stack.setCount(0);}
        player.getInventory().markDirty();player.currentScreenHandler.sendContentUpdates();return dropped;
    }
    private PhysicalCards(){}
}
