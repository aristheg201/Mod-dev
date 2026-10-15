package dev.svextract.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import java.lang.reflect.*;
import java.util.*;

/** Verified against Cobblemon Fabric 1.8.2. No storage movement, ownership rewrite or Pokémon cloning. */
public final class CobblemonAdapter {
    private Object storage;
    private Method getParty,getSlot,getUuid,recall;
    public void initialize() throws ReflectiveOperationException{
        if(!FabricLoader.getInstance().isModLoaded("cobblemon"))return;
        Class<?> mod=Class.forName("com.cobblemon.mod.common.Cobblemon");
        storage=mod.getMethod("getStorage").invoke(mod.getField("INSTANCE").get(null));
        getParty=Class.forName("com.cobblemon.mod.common.api.storage.PokemonStoreManager").getMethod("getParty",ServerPlayerEntity.class);
        getSlot=Class.forName("com.cobblemon.mod.common.api.storage.party.PartyStore").getMethod("get",int.class);
        Class<?> pokemon=Class.forName("com.cobblemon.mod.common.pokemon.Pokemon");
        getUuid=pokemon.getMethod("getUuid");recall=pokemon.getMethod("recall");
    }
    public boolean present(){return FabricLoader.getInstance().isModLoaded("cobblemon");}
    public boolean ready(){return storage!=null && recall!=null;}
    public List<UUID> partyIds(ServerPlayerEntity player) throws ReflectiveOperationException{
        List<UUID> ids=new ArrayList<>();for(Object p:party(player))ids.add((UUID)getUuid.invoke(p));return ids;
    }
    private List<Object> party(ServerPlayerEntity player) throws ReflectiveOperationException{
        if(!present())return List.of();if(!ready())throw new IllegalStateException("Installed Cobblemon API is incompatible; deployment blocked to protect companions");
        Object party=getParty.invoke(storage,player);List<Object> out=new ArrayList<>(6);
        for(int i=0;i<6;i++){Object p=getSlot.invoke(party,i);if(p!=null)out.add(p);}return out;
    }
    public void recallParty(ServerPlayerEntity player) throws ReflectiveOperationException{
        for(Object pokemon:party(player))recall.invoke(pokemon);
    }
    public net.minecraft.entity.LivingEntity companionEntity(ServerPlayerEntity p,UUID selected) throws ReflectiveOperationException{
        for(Object pokemon:party(p))if(identity(pokemon).equals(selected))return (net.minecraft.entity.LivingEntity)pokemon.getClass().getMethod("getEntity").invoke(pokemon);
        return null;
    }
    public static UUID owner(Object pokemon) throws ReflectiveOperationException{return (UUID)pokemon.getClass().getMethod("getOwnerUUID").invoke(pokemon);}
    public static UUID identity(Object pokemon) throws ReflectiveOperationException{return (UUID)pokemon.getClass().getMethod("getUuid").invoke(pokemon);}
}
