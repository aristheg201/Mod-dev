package vn.svframe.svrelationships.fabric.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import vn.svframe.svrelationships.fabric.family.DaycareRuntimeService;
import vn.svframe.svrelationships.fabric.localization.MessageService;
import vn.svframe.svrelationships.fabric.persistence.LineageRepository;
import vn.svframe.svrelationships.fabric.relationship.CeremonyService;
import vn.svframe.svrelationships.fabric.relationship.LifeInteractionService;
import vn.svframe.svrelationships.fabric.relationship.PartnershipService;
import vn.svframe.svrelationships.fabric.relationship.RelationshipService;
import vn.svframe.svrelationships.fabric.reward.RelationshipRewardService;
import vn.svframe.svrelationships.integration.ProviderHub;
import vn.svframe.svrelationships.integration.PokemonProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Generic config-driven inventory GUI renderer. */
public final class ServerGuiService {
    private final GuiDefinitionService definitions;
    private final MessageService messages;
    private final ProviderHub providers;
    private final RelationshipService relationships;
    private final LifeInteractionService interactions;
    private final PartnershipService partnerships;
    private final RelationshipRewardService rewards;
    private final CeremonyService ceremonies;
    private final DaycareRuntimeService daycare;
    private final LineageRepository lineage;

    public ServerGuiService(GuiDefinitionService definitions, MessageService messages, ProviderHub providers,
                            RelationshipService relationships, LifeInteractionService interactions,
                            PartnershipService partnerships, RelationshipRewardService rewards,
                            CeremonyService ceremonies, DaycareRuntimeService daycare, LineageRepository lineage) {
        this.definitions=definitions;this.messages=messages;this.providers=providers;this.relationships=relationships;
        this.interactions=interactions;this.partnerships=partnerships;this.rewards=rewards;this.ceremonies=ceremonies;
        this.daycare=daycare;this.lineage=lineage;
    }

    public boolean openMain(ServerPlayerEntity player){return open("main",player,null,0);}
    public boolean openRelationship(ServerPlayerEntity player,UUID pokemonId){return open("relationship",player,pokemonId,0);}
    public boolean open(String guiId,ServerPlayerEntity player,UUID pokemonId){return open(guiId,player,pokemonId,0);}

    public boolean open(String guiId,ServerPlayerEntity player,UUID pokemonId,int requestedPage){
        GuiDefinitionService.GuiDefinition definition=definitions.snapshot().definitions().get(guiId);if(definition==null)return false;
        List<ResolvedRepeater> repeaters=resolveRepeaters(definition,player);
        int pages=repeaters.stream().mapToInt(ResolvedRepeater::pageCount).max().orElse(1);int page=Math.max(0,Math.min(requestedPage,Math.max(0,pages-1)));
        SimpleGui gui=new SimpleGui(screen(definition.screen()),player,false);gui.setLockPlayerInventory(true);
        Map<String,Object> base=placeholders(player,pokemonId);base.put("page",page+1);base.put("pages",pages);gui.setTitle(messages.text(definition.titleKey(),base));
        for(var component:definition.components().values()){
            if(!conditions(component.conditions(),player,pokemonId,base,page,pages))continue;
            GuiElementBuilder builder=element(component.itemId(),component.nameKey(),component.loreKeys(),base);if(builder==null)continue;
            builder.setCallback((index,clickType,action)->dispatch(component.action(),component.args(),player,pokemonId,guiId,page,pages));gui.setSlot(component.slot(),builder);
        }
        for(ResolvedRepeater resolved:repeaters){
            var repeater=resolved.definition();int offset=page*repeater.slots().size();
            for(int local=0;local<repeater.slots().size();local++){
                int index=offset+local;if(index>=resolved.entries().size())break;DynamicEntry entry=resolved.entries().get(index);
                Map<String,Object> values=new LinkedHashMap<>(base);values.putAll(entry.placeholders());
                if(!conditions(repeater.conditions(),player,entry.pokemonId(),values,page,pages))continue;
                GuiElementBuilder builder=element(repeater.itemId(),repeater.nameKey(),repeater.loreKeys(),values);if(builder==null)continue;
                builder.setCallback((slot,clickType,action)->dispatch(repeater.action(),repeater.args(),player,entry.pokemonId(),guiId,page,pages));gui.setSlot(repeater.slots().get(local),builder);
            }
        }
        gui.open();return true;
    }

    private GuiElementBuilder element(String rawItem,String nameKey,List<String> loreKeys,Map<String,Object> values){Identifier id=Identifier.tryParse(rawItem);if(id==null||!Registries.ITEM.containsId(id))return null;Item item=Registries.ITEM.get(id);GuiElementBuilder builder=new GuiElementBuilder(item).setName(messages.text(nameKey,values));for(String lore:loreKeys)builder.addLoreLine(messages.text(lore,values));return builder;}

    private void dispatch(String action,Map<String,String> args,ServerPlayerEntity player,UUID pokemonId,String currentGui,int page,int pages){
        switch(action){
            case "none"->{ }
            case "open_gui"->open(args.getOrDefault("id","main"),player,pokemonId,0);
            case "open_relationship"->{if(pokemonId!=null)openRelationship(player,pokemonId);}
            case "page_next"->{if(page+1<pages)open(currentGui,player,pokemonId,page+1);}
            case "page_previous"->{if(page>0)open(currentGui,player,pokemonId,page-1);}
            case "claim_reward"->{if(pokemonId!=null)rewards.claim(player,pokemonId,args.getOrDefault("profile",""),System.currentTimeMillis());openRelationship(player,pokemonId);}
            case "interaction"->{if(pokemonId!=null)interactions.interact(player.getUuid(),pokemonId,args.getOrDefault("id",""),System.currentTimeMillis());openRelationship(player,pokemonId);}
            case "gift"->{if(pokemonId!=null)interactions.gift(player,pokemonId,args.getOrDefault("id",""),System.currentTimeMillis());openRelationship(player,pokemonId);}
            case "advance_partnership"->{if(pokemonId!=null)partnerships.advance(player.getUuid(),pokemonId,args.getOrDefault("milestone",""));openRelationship(player,pokemonId);}
            case "ceremony"->{if(pokemonId!=null)ceremonies.perform(player.getUuid(),pokemonId,args.getOrDefault("id",""),System.currentTimeMillis());openRelationship(player,pokemonId);}
            default->{ }
        }
    }

    private List<ResolvedRepeater> resolveRepeaters(GuiDefinitionService.GuiDefinition gui,ServerPlayerEntity player){List<ResolvedRepeater> result=new ArrayList<>();for(var repeater:gui.repeaters().values()){List<DynamicEntry> entries=switch(repeater.source()){
        case "owned_pokemon"->owned(player);
        case "partners"->partners(player);
        case "daycare_sessions"->daycare(player);
        case "lineage"->lineage(player);
        default->List.of();
    };int size=Math.max(1,repeater.slots().size());int pageCount=Math.max(1,(entries.size()+size-1)/size);result.add(new ResolvedRepeater(repeater,entries,pageCount));}return result;}

    private List<DynamicEntry> owned(ServerPlayerEntity player){return providers.pokemonProvider().map(provider->provider.ownedPokemon(player.getUuid()).stream().map(snapshot->pokemonEntry(player,snapshot)).toList()).orElse(List.of());}
    private List<DynamicEntry> partners(ServerPlayerEntity player){var provider=providers.pokemonProvider().orElse(null);if(provider==null)return List.of();return relationships.partners(player.getUuid()).stream().map(state->provider.findOwned(player.getUuid(),state.key().pokemonId()).map(snapshot->pokemonEntry(player,snapshot)).orElse(null)).filter(java.util.Objects::nonNull).toList();}
    private DynamicEntry pokemonEntry(ServerPlayerEntity player,PokemonProvider.PokemonSnapshot snapshot){Map<String,Object> values=new LinkedHashMap<>();values.put("pokemon_uuid",snapshot.pokemonId());values.put("pokemon",snapshot.speciesId());values.put("form",snapshot.formId());values.put("level",snapshot.level());values.put("bond",relationships.progression(player.getUuid(),snapshot.pokemonId(),"bond"));values.put("romance",relationships.progression(player.getUuid(),snapshot.pokemonId(),"romance"));values.put("bond_rank",relationships.rank(player.getUuid(),snapshot.pokemonId(),"bond").orElse(""));values.put("romance_rank",relationships.rank(player.getUuid(),snapshot.pokemonId(),"romance").orElse(""));values.put("partner",relationships.existing(player.getUuid(),snapshot.pokemonId()).map(v->v.partner()).orElse(false));return new DynamicEntry(snapshot.pokemonId(),values);}
    private List<DynamicEntry> daycare(ServerPlayerEntity player){return daycare.sessions(player.getUuid()).stream().sorted(java.util.Comparator.comparingLong(v->v.startedAtMillis())).map(session->{Map<String,Object> values=new LinkedHashMap<>();values.put("session",session.sessionId());values.put("status",session.status());values.put("definition",session.definitionId());values.put("participants",session.participantPokemonIds().size());values.put("complete_at",session.completeAtMillis());values.put("produced_pokemon",session.producedPokemonId()==null?"":session.producedPokemonId());return new DynamicEntry(session.producedPokemonId(),values);}).toList();}
    private List<DynamicEntry> lineage(ServerPlayerEntity player){return lineage.byOwner(player.getUuid()).stream().sorted(java.util.Comparator.comparingInt(v->v.generation())).map(record->{Map<String,Object> values=new LinkedHashMap<>();values.put("pokemon_uuid",record.pokemonId());values.put("generation",record.generation());values.put("parents",record.parentPokemonIds().size());values.put("profile",record.inheritanceProfile());values.put("born_at",record.bornAtMillis());return new DynamicEntry(record.pokemonId(),values);}).toList();}

    private boolean conditions(Map<String,String> conditions,ServerPlayerEntity player,UUID pokemonId,Map<String,Object> values,int page,int pages){for(var condition:conditions.entrySet()){String key=condition.getKey(),expected=condition.getValue();boolean ok=switch(key){
        case "pokemon_selected"->Boolean.parseBoolean(expected)==(pokemonId!=null);
        case "partner"->pokemonId!=null&&relationships.existing(player.getUuid(),pokemonId).map(v->v.partner()).orElse(false)==Boolean.parseBoolean(expected);
        case "has_previous_page"->Boolean.parseBoolean(expected)==(page>0);
        case "has_next_page"->Boolean.parseBoolean(expected)==(page+1<pages);
        default->{if(key.startsWith("progression.")&&key.endsWith(".min")&&pokemonId!=null){String track=key.substring("progression.".length(),key.length()-".min".length());yield relationships.progression(player.getUuid(),pokemonId,track)>=Long.parseLong(expected);}if(key.startsWith("route.")&&pokemonId!=null){String route=key.substring("route.".length());try{yield expected.equals(relationships.currentRoute(player.getUuid(),pokemonId,route));}catch(RuntimeException ignored){yield false;}}if(key.equals("personality")&&pokemonId!=null)yield relationships.existing(player.getUuid(),pokemonId).map(v->expected.equals(v.personalityId())).orElse(false);yield true;}
    };if(!ok)return false;}return true;}

    private Map<String,Object> placeholders(ServerPlayerEntity player,UUID pokemonId){Map<String,Object> values=new LinkedHashMap<>();values.put("player",player.getName().getString());values.put("partner_count",relationships.partners(player.getUuid()).size());values.put("partner_capacity",relationships.capacity(player.getUuid()));if(pokemonId!=null){values.put("pokemon_uuid",pokemonId);values.put("bond",relationships.progression(player.getUuid(),pokemonId,"bond"));values.put("romance",relationships.progression(player.getUuid(),pokemonId,"romance"));values.put("bond_rank",relationships.rank(player.getUuid(),pokemonId,"bond").orElse(""));values.put("romance_rank",relationships.rank(player.getUuid(),pokemonId,"romance").orElse(""));values.put("partner",relationships.state(player.getUuid(),pokemonId).partner());providers.pokemonProvider().flatMap(provider->provider.findOwned(player.getUuid(),pokemonId)).ifPresent(snapshot->{values.put("pokemon",snapshot.speciesId());values.put("level",snapshot.level());values.put("form",snapshot.formId());});}return values;}
    private static ScreenHandlerType<?> screen(String id){return switch(id){case "generic_9x1"->ScreenHandlerType.GENERIC_9X1;case "generic_9x2"->ScreenHandlerType.GENERIC_9X2;case "generic_9x3"->ScreenHandlerType.GENERIC_9X3;case "generic_9x4"->ScreenHandlerType.GENERIC_9X4;case "generic_9x5"->ScreenHandlerType.GENERIC_9X5;case "hopper"->ScreenHandlerType.HOPPER;case "generic_3x3"->ScreenHandlerType.GENERIC_3X3;default->ScreenHandlerType.GENERIC_9X6;};}
    private record DynamicEntry(UUID pokemonId,Map<String,Object> placeholders){}private record ResolvedRepeater(GuiDefinitionService.RepeaterDefinition definition,List<DynamicEntry> entries,int pageCount){}
}
