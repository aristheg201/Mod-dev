package vn.worldcomesalive.civilization;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import vn.worldcomesalive.server.WorldSimulation;
import java.util.*;

/**
 * Player-facing settlement founding entry point.
 * The plow attachment is persistent logical equipment; the sent-out Pokemon is only presentation.
 */
public final class SettlementFounding {
    public static final Item PLOW=Registry.register(Registries.ITEM,Identifier.of("worldcomesalive","settlement_plow"),new Item(new Item.Settings().maxCount(1)));
    public static final class PlowAttachment {
        public UUID pokemon,owner;
        public String species="";
        public long attachedAt;
    }

    public static void initialize(){
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e->e.add(PLOW));
    }

    public static boolean toggle(ServerPlayerEntity player,PokemonEntity entity){
        WorldSimulation sim=WorldSimulation.active();if(sim==null)return false;
        var pokemon=entity.getPokemon();
        if(!pokemon.belongsTo(player)){player.sendMessage(Text.literal("Only your own Pokemon can pull settlement equipment."),true);return false;}
        var existing=sim.state.civilization.plowAttachments.get(entity.getUuid());
        if(existing!=null){
            if(!existing.owner.equals(player.getUuid()))return false;
            sim.state.civilization.plowAttachments.remove(entity.getUuid());
            ItemStack stack=new ItemStack(PLOW);if(!player.getInventory().insertStack(stack))player.dropItem(stack,false);
            player.sendMessage(Text.literal("Plow detached from "+pokemon.getSpecies().getName()+"."),true);sim.save();return true;
        }
        ItemStack held=player.getMainHandStack();if(!held.isOf(PLOW))return false;
        held.decrement(1);PlowAttachment a=new PlowAttachment();a.pokemon=entity.getUuid();a.owner=player.getUuid();a.species=pokemon.getSpecies().getName();a.attachedAt=sim.state.clock;
        sim.state.civilization.plowAttachments.put(entity.getUuid(),a);sim.save();player.sendMessage(Text.literal("Plow attached. Lead this Pokemon to suitable land and use the ground to found a settlement."),true);return true;
    }

    public static PokemonEntity nearbyPlowPokemon(ServerPlayerEntity player){
        WorldSimulation sim=WorldSimulation.active();if(sim==null)return null;
        for(PokemonEntity pokemon:sim.world.getEntitiesByClass(PokemonEntity.class,player.getBoundingBox().expand(12),e->true)){
            var a=sim.state.civilization.plowAttachments.get(pokemon.getUuid());
            if(a!=null&&a.owner.equals(player.getUuid()))return pokemon;
        }
        return null;
    }

    private SettlementFounding(){}
}
