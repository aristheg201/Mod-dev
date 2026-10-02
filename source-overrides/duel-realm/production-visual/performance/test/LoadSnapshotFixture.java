package vn.svarcade.tcg.duel;
import vn.svarcade.tcg.fabric.*;
import vn.svarcade.tcg.data.*;
import vn.svarcade.tcg.economy.*;
import java.util.*;
public final class LoadSnapshotFixture {
 public static TcgMod.Snapshot full(Catalog catalog,CardStore.UiData data,Duel.View view){return new TcgMod.Snapshot("",data.profile(),data.binder().stream().map(o->{var c=catalog.card(o.card());return new TcgMod.BinderCard(o.serial(),c.name(),c.category(),c.type(),c.power(),c.text(),o.finish(),o.origin(),o.lock().isEmpty());}).toList(),0,data.counts().size(),catalog.cards().size(),data.deckNames(),data.market(),data.banners(),List.of("Opponent"),"",view,List.of("Response windows and all Chain links resolve authoritatively."),List.of(),List.of(),"",catalog.cards(),catalog.starters(),data.inventory(),data.counts(),data.decks(),data.formats(),List.of(),"Load Player","Load Bot",false,false,catalog.rules(),Map.of(),CardWorldsCurrency.Balances.unavailable());}
 public static TcgMod.Snapshot dynamic(TcgMod.Snapshot base,Duel.View view){return new TcgMod.Snapshot(base.notice(),base.profile(),base.binder(),base.page(),base.collected(),base.total(),base.decks(),base.market(),base.banners(),base.players(),base.challenge(),view,base.guides(),base.trades(),base.tradeShelf(),base.tradePeer(),base.definitions(),base.deckTemplates(),base.inventory(),base.counts(),base.savedDecks(),base.formats(),base.pulls(),base.playerName(),base.opponentName(),base.spectator(),base.open(),base.rules(),base.sellers(),base.currencyBalances()).withContent(null).withUi(null);}
 static Map<String,Object> legacy(TcgMod.Snapshot base,Object view){Map<String,Object> map=new LinkedHashMap<>();try{for(var c:TcgMod.Snapshot.class.getRecordComponents())map.put(c.getName(),c.getAccessor().invoke(base));}catch(Exception e){throw new AssertionError(e);}map.put("duel",view);return map;}
}
