package vn.svarcade.tcg.physical;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import vn.svarcade.tcg.data.CardIdentityResolver;
import vn.svarcade.tcg.mixin.CaptureBallAccess;
import java.util.*;

public final class BlankCapture {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("cardworlds-capture");
    private static final class Attempt {
        final UUID player,pokemon;final PhysicalCardData data;final EmptyPokeBallEntity ball;
        final long started;boolean issued;
        Attempt(ServerPlayerEntity player,PokemonEntity target,PhysicalCardData data,EmptyPokeBallEntity ball){this.player=player.getUuid();pokemon=target.getPokemon().getUuid();this.data=data;this.ball=ball;started=System.currentTimeMillis();}
    }
    // Kept until the ball callback has finished, including completed attempts, so a repeated callback is harmless.
    private static final Map<UUID,Attempt> attempts=new HashMap<>();
    public static void initialize(){ServerTickEvents.END_SERVER_TICK.register(server->{
        attempts.entrySet().removeIf(e->{Attempt a=e.getValue();
            if(a.ball.getCaptureFuture().isDone()&&System.currentTimeMillis()-a.started>120_000)return true;
            if(System.currentTimeMillis()-a.started>120_000){var p=a.ball.getCapturingPokemon();if(p!=null){p.getBusyLocks().remove(a.ball);p.setInvisible(false);}a.ball.discard();return true;}return false;
        });
    });}
    public static void clear(){attempts.clear();}
    public static boolean hasActiveAttempts(){return attempts.values().stream().anyMatch(a->!a.ball.getCaptureFuture().isDone());}
    public static boolean validTarget(ServerPlayerEntity player,LivingEntity target){
        return !player.isSpectator()&&player.isAlive()&&target instanceof PokemonEntity p&&target.getWorld()==player.getWorld()&&target.isAlive()&&!target.isRemoved()
            &&p.getPokemon().isWild()&&p.getPokemon().getOwnerUUID()==null&&p.getPokemon().getOwnerNPC()==null
            &&UncatchableProperty.INSTANCE.isCatchable(p)&&!p.isBusy()&&p.getBattleId()==null
            &&BattleRegistry.INSTANCE.getBattleByParticipatingPlayer(player)==null;
    }
    public static boolean attempt(ServerPlayerEntity player,LivingEntity target,Hand hand) {
        PhysicalCards.serverThread(player);ItemStack held=player.getStackInHand(hand);
        if(!held.isOf(CardItems.BLANK_CARD)||held.isEmpty()||PhysicalCards.catalog()==null||!validTarget(player,target)){
            player.sendMessage(Text.translatable("cardworlds.blank.invalid_target"),true);return false;
        }
        PokemonEntity entity=(PokemonEntity)target;Pokemon pokemon=entity.getPokemon();
        try {
            var card=PhysicalCards.identities().resolve(pokemon.getSpecies().getResourceIdentifier().toString(),pokemon.getAspects()).orElseThrow(()->new IllegalArgumentException("No registered card for target"));
            var data=new PhysicalCardData(1,UUID.randomUUID(),card.id(),pokemon.getShiny()?"Holo":"Normal","BLANK_CAPTURE",player.getUuidAsString(),player.getName().getString(),
                CardIdentityResolver.canonicalSpecies(pokemon.getSpecies().getResourceIdentifier().toString()),PhysicalCards.identities().relevantAspects(pokemon.getSpecies().getResourceIdentifier().toString(),pokemon.getAspects()),pokemon.getLevel(),pokemon.getShiny(),System.currentTimeMillis());
            data.validate(PhysicalCards.catalog());
            // The actual basic Poké Ball has the official 1.0 modifier and all installed capture event hooks.
            EmptyPokeBallEntity ball=new EmptyPokeBallEntity(PokeBalls.getPokeBall(),player.getServerWorld(),player,CobblemonEntities.EMPTY_POKEBALL);
            ball.setPosition(entity.getX(),entity.getBodyY(.5),entity.getZ());ball.setVelocity(player.getRotationVector().multiply(.25));
            Attempt context=new Attempt(player,entity,data,ball);attempts.put(ball.getUuid(),context);
            if(!player.getServerWorld().spawnEntity(ball)){attempts.remove(ball.getUuid());return false;}
            ((CaptureBallAccess)(Object)ball).cardworlds$hit(new EntityHitResult(entity));
            // A provider cancellation or other rejected hit does not begin a valid attempt.
            if(ball.getCapturingPokemon()!=entity||!entity.getBusyLocks().contains(ball)){attempts.remove(ball.getUuid());ball.discard();return false;}
            held.decrement(1);player.getInventory().markDirty();player.currentScreenHandler.sendContentUpdates();
            player.sendMessage(Text.translatable("cardworlds.blank.attempt"),true);
            ball.getCaptureFuture().thenAccept(success->{if(!success)player.getServer().execute(()->player.sendMessage(Text.translatable("cardworlds.blank.failed"),false));});
            LOG.info("CARDWORLDS_BLANK_CAPTURE_ATTEMPT player={} pokemon={} card={} basicModifier=1.0",player.getUuid(),pokemon.getUuid(),card.id());return true;
        }catch(IllegalArgumentException e){LOG.warn("Rejected Blank Card identity: {}",e.getMessage());player.sendMessage(Text.translatable("cardworlds.blank.identity_unavailable"),false);return false;}
    }
    public static boolean isCardAttempt(EmptyPokeBallEntity ball){return attempts.containsKey(ball.getUuid());}
    /** Runs at the original storage insertion site AFTER Cobblemon removes the exact world entity. Never inserts into party or PC. */
    public static boolean seal(EmptyPokeBallEntity ball,PokemonEntity entity,ServerPlayerEntity player,Pokemon pokemon) {
        PhysicalCards.serverThread(player);Attempt a=attempts.get(ball.getUuid());
        if(a==null||!a.player.equals(player.getUuid())||!a.pokemon.equals(pokemon.getUuid())||entity.getPokemon()!=pokemon)
            throw new IllegalStateException("Blank capture context mismatch: storage insertion blocked");
        if(a.issued){LOG.warn("CARDWORLDS_BLANK_DUPLICATE_CALLBACK pokemon={}",pokemon.getUuid());return true;}
        if(!entity.isRemoved())throw new IllegalStateException("Cobblemon success lifecycle did not remove captured entity");
        a.issued=true;
        boolean dropped=PhysicalCards.giveOrDrop(player,CardItems.physical(a.data));
        player.sendMessage(Text.translatable("cardworlds.blank.success",PhysicalCards.cardName(PhysicalCards.catalog().card(a.data.cardId()))),false);
        LOG.info("CARDWORLDS_BLANK_CAPTURE_SUCCESS player={} pokemon={} card={} token={} storageInsertion=false rewardDropped={}",player.getUuid(),pokemon.getUuid(),a.data.cardId(),a.data.physicalId(),dropped);
        return true;
    }
    private BlankCapture(){}
}
