package io.github.aristheg201.cobblemonworld.qa;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.google.gson.Gson;
import io.github.aristheg201.cobblemonworld.narrative.*;
import io.github.aristheg201.cobblemonworld.npc.*;
import io.github.aristheg201.cobblemonworld.network.*;
import io.github.aristheg201.cobblemonworld.progression.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.RelativeMovement;
import java.util.*;
import java.math.BigDecimal;

/** QA fixtures author a blank map and supplies; only ordinary client interactions advance narrative. */
public final class NarrativeQaServer {
    public record Control(String stage,String kind,int entity,int x,int y,int z,String offer){}
    private record Step(NarrativeRegistry.Stage stage,String chain,boolean loss){}
    private static final List<Step> STEPS=new ArrayList<>();private static final Set<String> ACK=new HashSet<>();
    private static final Map<String,Integer> PLACED=new LinkedHashMap<>();
    private static int index,age,joinedAge,pcEvidence,lossesBefore,retries;private static boolean initialized,sent,finished,liveTeamLogged;private static String token;private static NPCEntity npc;
    public static void register(){LiveTrainerAudit.register();ServerLifecycleEvents.SERVER_STARTED.register(s->s.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL,true));ServerTickEvents.END_SERVER_TICK.register(NarrativeQaServer::tick);}
    public static void ack(QaAckPayload p){if(!p.ok())throw new IllegalStateException("CWORLD_NARRATIVE_QA_FAILED "+p.token()+" "+p.detail());ACK.add(p.token());}
    private static void tick(MinecraftServer server){
        if(finished){if(server.getPlayerList().getPlayers().isEmpty())server.halt(false);return;}
        if(server.getPlayerList().getPlayers().isEmpty() || server.getTickCount()<160)return;
        ServerPlayer p=server.getPlayerList().getPlayers().getFirst();
        if(!initialized){if(++joinedAge<100)return;initialize(p);initialized=true;}
        if(index>=STEPS.size()){
            if(!Boolean.getBoolean("cworld.qa.opening")) {
                if(pcEvidence<2){
                    String species=pcEvidence==0?"groudon":"kyogre",capture="legendary-native-pc-"+species+".png";
                    if(!sent){
                        verifyClaims(p);platform(p,4);BlockPos pos=new BlockPos(4,120,0);
                        var block=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("cobblemon:pc"));
                        if(!(block instanceof com.cobblemon.mod.common.block.PCBlock))throw new IllegalStateException("Native PC block unavailable");
                        var state=block.defaultBlockState();p.serverLevel().setBlockAndUpdate(pos,state);block.setPlacedBy(p.serverLevel(),pos,state,p,new ItemStack(block));
                        p.teleportTo(p.serverLevel(),1,120,3,EnumSet.noneOf(RelativeMovement.class),-135,0);
                        ServerPlayNetworking.send(p,new QaControlPayload("prod_narrative",new Gson().toJson(new Control(species,"pc",-1,4,120,0,"")),capture));
                        sent=true;age=0;
                    }
                    if(ACK.remove(capture)){System.out.println("CWORLD_NARRATIVE_QA_PASS "+capture+" objective=storage-view");pcEvidence++;sent=false;}
                    else if(++age>1200)throw new IllegalStateException("CWORLD_NARRATIVE_QA_FAILED native PC evidence timeout "+species);
                    return;
                }
                verifyClaims(p);
            }
            ProgressionStore.INSTANCE.save();PoiStore.save();NpcPlacementStore.INSTANCE.save();
            System.out.println("CWORLD_NARRATIVE_QA_FINISHED steps="+STEPS.size()+" main="+NarrativeEngine.state(p).narrative.finished.size()+" chains="+NarrativeEngine.state(p).narrative.completedChains);
            ServerPlayNetworking.send(p,new QaControlPayload("stop","","narrative-stop"));finished=true;return;
        }
        var step=STEPS.get(index);var s=step.stage();
        if(!sent){
            age=0;liveTeamLogged=false;token=(step.loss()?"loss-":step.chain().isBlank()?"main-":"side-")+s.id()+(retries>0?"-retry-"+retries:"")+".png";
            Control control=prepare(p,step);if(control==null)return;
            lossesBefore=NarrativeEngine.state(p).narrative.losses.getOrDefault(s.target(),0);sent=true;System.out.println("CWORLD_NARRATIVE_QA_BEGIN "+token+" nonOp="+!p.hasPermissions(2));
            ServerPlayNetworking.send(p,new QaControlPayload("prod_narrative",new Gson().toJson(control),token));
        }
        age++;
        if(npc!=null && TrainerBattleService.hasLiveBattle(npc) && !liveTeamLogged){
            liveTeamLogged=true;int ace=1;for(var mon:npc.getParty()){ace=Math.max(ace,mon.getLevel());for(var stat:CompetitiveTeams.STATS)if(mon.getIvs().get(stat)<0)throw new IllegalStateException("Invalid live IV");}
            System.out.println("CWORLD_NARRATIVE_QA_LIVE_TEAM npc="+s.target()+" ace="+ace+" skill="+npc.getSkill());
        }
        boolean complete=step.loss()?NarrativeEngine.state(p).narrative.losses.getOrDefault(s.target(),0)>0 && !NarrativeEngine.state(p).narrative.finished.contains(s.id()):NarrativeEngine.state(p).narrative.finished.contains(s.id());
        if(ACK.contains(token) && complete){
            if(s.id().equals("unknown_first") && !NarrativeEngine.state(p).unreadMessages.contains("mysterious:first_contact"))throw new IllegalStateException("First unknown message failed to arrive after the investigation");
            System.out.println("CWORLD_NARRATIVE_QA_PASS "+token+" objective="+s.type());ACK.remove(token);index++;sent=false;retries=0;
        }else if(!step.loss() && s.type().equals("battle") && ACK.contains(token) && NarrativeEngine.state(p).narrative.losses.getOrDefault(s.target(),0)>lossesBefore){
            if(++retries>3)throw new IllegalStateException("CWORLD_NARRATIVE_QA_FAILED three legitimate retry losses "+token);
            System.out.println("CWORLD_NARRATIVE_QA_RETRY after native loss target="+s.target()+" attempt="+retries);ACK.remove(token);sent=false;
        }else if(age>12000)throw new IllegalStateException("CWORLD_NARRATIVE_QA_FAILED timeout="+token+" current="+NarrativeEngine.state(p).narrative.main+" ACK="+ACK.contains(token));
    }
    private static void initialize(ServerPlayer p){
        if(p.hasPermissions(2))throw new IllegalStateException("QA player is OP");
        var n=NarrativeEngine.state(p).narrative;
        if(Boolean.getBoolean("cworld.qa.opening") && (NarrativeEngine.state(p).unreadMessages.contains("mysterious:first_contact") || NarrativeEngine.state(p).readMessages.contains("mysterious:first_contact")))throw new IllegalStateException("Unknown contact appeared before the First Challenge");
        if(n.finished.size()>0 && !Boolean.getBoolean("cworld.qa.resume"))throw new IllegalStateException("Fresh narrative QA world required");
        p.serverLevel().setDayTime(6000);p.serverLevel().setWeatherParameters(0,0,false,false);
        p.getServer().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,p.getServer());
        p.getServer().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,p.getServer());
        p.getServer().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE).set(false,p.getServer());
        var api=org.krripe.beconomy.api.BEconomy.INSTANCE.getAPI();
        if(api.getCurrencyList().stream().noneMatch(c->c.getCurrencyType().equalsIgnoreCase("beastcoin")))api.createCurrency("BeastCoin","QA progression currency","minecraft:gold_nugget",0,BigDecimal.ZERO,"BC",false);
        api.setBalance(p.getUUID(),BigDecimal.valueOf(100),"beastcoin");
        p.getInventory().setItem(0,new ItemStack(io.github.aristheg201.cobblemonworld.item.ModItems.TRAINER_PHONE));p.getInventory().selected=0;
        int validated=0;for(var team:NarrativeRegistry.INSTANCE.teams.values())for(var member:team.members()){
            var mon=CompetitiveTeams.create(p,member);for(int i=0;i<6;i++)if(mon.getIvs().get(CompetitiveTeams.STATS[i])!=(member.ivOverrides()==null?31:member.ivOverrides().getOrDefault(CompetitiveTeams.STATS[i].getShowdownId(),31)))throw new IllegalStateException("IV validation failed "+team.id());validated++;
        }
        System.out.println("CWORLD_NARRATIVE_QA_REGISTRY_TEAM_VALIDATION members="+validated+" teams="+NarrativeRegistry.INSTANCE.teams.size());
        for(var stage:NarrativeRegistry.INSTANCE.stages.values())if(java.util.Set.of("deliver","collect").contains(stage.type()) && !BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(stage.item())))throw new IllegalStateException("Unresolved narrative item "+stage.item());
        for(var s:NarrativeRegistry.INSTANCE.data.campaign())if(!n.finished.contains(s.id()) && (!Boolean.getBoolean("cworld.qa.opening") || s.act().equals("0"))){
            if(s.id().equals("mara_first"))STEPS.add(new Step(s,"",true));STEPS.add(new Step(s,"",false));}
        if(!Boolean.getBoolean("cworld.qa.opening"))for(String id:NarrativeRegistry.INSTANCE.chains.keySet()){
            var c=NarrativeRegistry.INSTANCE.chains.get(id);if(!n.completedChains.contains(id))for(var s:c.stages())if(!n.finished.contains(s.id()))STEPS.add(new Step(s,id,false));}
        System.out.println("CWORLD_NARRATIVE_QA_NON_OP_CONFIRMED player="+p.getGameProfile().getName()+" steps="+STEPS.size());
    }
    private static Control prepare(ServerPlayer p,Step step){
        var s=step.stage();npc=null;
        if(s.type().equals("battle")){party(p,step.loss());
            if(s.target().equals("mysterious"))for(var mon:Cobblemon.INSTANCE.getStorage().getParty(p)) {
                mon.getMoveSet().clear();for(String move:List.of("psystrike","aurasphere","icebeam","flamethrower"))mon.getMoveSet().add(com.cobblemon.mod.common.api.moves.Moves.getByName(move).create());
            }
            if(s.target().equals("weather_guardian_kyogre"))for(var mon:Cobblemon.INSTANCE.getStorage().getParty(p))mon.swapHeldItem(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("cobblemon:focus_sash"))),false,false);
        }else if(Cobblemon.INSTANCE.getStorage().getParty(p).get(0)==null)party(p,false);
        if(s.type().equals("heal")){
            var party=Cobblemon.INSTANCE.getStorage().getParty(p);
            if("magikarp".equals(s.item())){var pc=Cobblemon.INSTANCE.getStorage().getPC(p);for(var mon:pc)if(mon.getSpecies().getName().equalsIgnoreCase("magikarp")){
                pc.remove(mon);var previous=party.get(0);if(previous!=null)pc.add(previous);party.set(0,mon);break;
            }}
            party.get(0).setCurrentHealth(1);
        }
        if(s.type().equals("deliver") || s.type().equals("collect")){
            p.getInventory().setItem(8,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(s.item())),s.amount()));p.inventoryMenu.broadcastChanges();}
        int location=PLACED.computeIfAbsent(s.target(),k->PLACED.size());double x=4+location*18;
        platform(p,x);p.teleportTo(p.serverLevel(),x-3,120,3,EnumSet.noneOf(RelativeMovement.class),-135,0);
        if(s.target().equals("mysterious")){
            var cfg=io.github.aristheg201.cobblemonworld.config.CWorldConfig.INSTANCE;cfg.finalEncounterEnabled=true;cfg.finalEncounterX=x;cfg.finalEncounterY=120;cfg.finalEncounterZ=0;
            for(var entity:p.serverLevel().getAllEntities())if(entity instanceof io.github.aristheg201.cobblemonworld.boss.MysteriousFigureEntity)return new Control(s.id(),s.type(),entity.getId(),0,0,0,step.chain());
            return null;
        }
        if(NarrativeRegistry.INSTANCE.pois.containsKey(s.target())){
            BlockPos pos=BlockPos.containing(x,120,0);p.serverLevel().setBlockAndUpdate(pos,Blocks.LECTERN.defaultBlockState());PoiStore.PLACES.put(s.target(),new PoiStore.Position(p.level().dimension().location().toString(),pos.getX(),pos.getY(),pos.getZ()));PoiStore.save();
            if(s.type().equals("capture")){
                var wild=PokemonProperties.Companion.parse(s.item()+" level=5").createEntity(p.serverLevel());wild.getPokemon().setCurrentHealth(1);wild.setNoAi(true);wild.moveTo(x,120,0,0,0);p.serverLevel().addFreshEntity(wild);
                p.getInventory().setItem(8,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("cobblemon:poke_ball")),32));p.inventoryMenu.broadcastChanges();
                return new Control(s.id(),s.type(),wild.getId(),0,0,0,step.chain());
            }
            return new Control(s.id(),s.type(),-1,pos.getX(),pos.getY(),pos.getZ(),step.chain());
        }
        var placement=NpcPlacementStore.INSTANCE.get(s.target());
        if(placement==null){p.teleportTo(p.serverLevel(),x,120,0,EnumSet.noneOf(RelativeMovement.class),135,60);
            p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4).withSuppressedOutput(),"cworld npc place "+s.target());placement=NpcPlacementStore.INSTANCE.get(s.target());
            p.teleportTo(p.serverLevel(),x-3,120,3,EnumSet.noneOf(RelativeMovement.class),-135,0);}
        if(placement==null)throw new IllegalStateException("Cannot author QA NPC "+s.target());
        p.teleportTo(p.serverLevel(),placement.x()-3,placement.y(),placement.z()+3,EnumSet.noneOf(RelativeMovement.class),-135,0);
        var entity=PersonalActors.personal(s.target())?PersonalActors.find(p,s.target()):p.serverLevel().getEntity(UUID.fromString(placement.entityUuid()));
        if(!(entity instanceof NPCEntity found))return null;npc=found;
        if(npc.getXRot()!=0)throw new IllegalStateException("NPC pitch drift "+s.target());
        return new Control(s.id(),s.type(),npc.getId(),0,0,0,step.chain());
    }
    private static void platform(ServerPlayer p,double x){for(int dx=-5;dx<=5;dx++)for(int dz=-5;dz<=5;dz++){
        p.serverLevel().setBlockAndUpdate(BlockPos.containing(x+dx,119,dz),Blocks.STONE_BRICKS.defaultBlockState());
        for(int y=120;y<126;y++)p.serverLevel().setBlockAndUpdate(BlockPos.containing(x+dx,y,dz),Blocks.AIR.defaultBlockState());}}
    private static void party(ServerPlayer p,boolean lose){
        int legalLevel=Math.max(5,LevelCapService.getCap(p));
        var party=Cobblemon.INSTANCE.getStorage().getParty(p);party.clearParty();
        if(lose){var mon=PokemonProperties.Companion.parse("magikarp level=5 moves=splash").create(p);mon.setCurrentHealth(1);party.set(0,mon);return;}
        for(int i=0;i<6;i++){
            var mon=PokemonProperties.Companion.parse("mewtwo level="+legalLevel+" nature=timid moves=energyball,icebeam,thunderbolt,flamethrower").create(p);
            for(var stat:CompetitiveTeams.STATS)mon.setIV(stat,31);mon.setEV(CompetitiveTeams.STATS[3],252);mon.setEV(CompetitiveTeams.STATS[5],252);mon.setEV(CompetitiveTeams.STATS[0],4);
            mon.swapHeldItem(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("cobblemon:focus_sash"))),false,false);party.set(i,mon);
        }
    }
    private static void verifyClaims(ServerPlayer p){
        for(String species:List.of("groudon","kyogre")){
            var claim=NarrativeEngine.state(p).narrative.claims.get(SeasonalRewards.SEASON+":"+species);if(claim==null || !claim.status().equals("delivered"))throw new IllegalStateException("Missing seasonal claim "+species);
            UUID id=UUID.fromString(claim.pokemonUuid());int count=0;for(var store:Cobblemon.INSTANCE.getStorage().getPCs(p.getUUID(),p.registryAccess()))for(var pokemon:store)if(pokemon.getUuid().equals(id))count++;
            if(count!=1)throw new IllegalStateException("Party-full PC claim count "+species+"="+count);
            int speciesCount=0;
            for(var store:Cobblemon.INSTANCE.getStorage().getPCs(p.getUUID(),p.registryAccess()))for(var pokemon:store)if(pokemon.getSpecies().getName().equalsIgnoreCase(species)){
                speciesCount++;
                if(pokemon.getLevel()!=70 || pokemon.getShiny() || !pokemon.getNature().getName().getPath().equals(species.equals("groudon")?"adamant":"modest") || !pokemon.getAbility().getName().equals(species.equals("groudon")?"drought":"drizzle"))throw new IllegalStateException("Legendary reward quality mismatch "+species);
                for(var stat:CompetitiveTeams.STATS)if(pokemon.getIvs().get(stat)!=25)throw new IllegalStateException("Legendary IV quality mismatch "+species);
                if(pokemon.getMoveSet().getMoves().size()!=4)throw new IllegalStateException("Legendary missing moves "+species);
            }
            if(speciesCount!=1)throw new IllegalStateException("Unexpected second Legendary species UUID "+species+" count="+speciesCount);
            if(!SeasonalRewards.claim(p,species))throw new IllegalStateException("Repeated entitlement lookup failed");
            count=0;for(var store:Cobblemon.INSTANCE.getStorage().getPCs(p.getUUID(),p.registryAccess()))for(var pokemon:store)if(pokemon.getUuid().equals(id))count++;
            if(count!=1)throw new IllegalStateException("Duplicate after claim retry "+species);
            System.out.println("CWORLD_NARRATIVE_QA_LEGENDARY_PC_ONE_ONLY species="+species+" uuid="+id+" count="+count+" level=70 sixIV=25 qualityVerified=true");
        }
    }
}
