package vn.svarcade.tcg.client.screens;
import vn.svarcade.tcg.client.*;import vn.svarcade.tcg.client.component.*;import vn.svarcade.tcg.client.card.CardRenderer;import net.minecraft.client.MinecraftClient;import java.util.*;
public final class MarketScreen implements Page {
 public void render(CardWorldsScreen a,Ui u,Rect b){a.field(u,"marketSearch","Search listings…",new Rect(b.x(),b.y(),290,34));u.chip(a.trade?"Direct Trade":"Exchange",new Rect(b.x()+304,b.y(),155,34),a.trade,()->a.trade=!a.trade);u.text("cardworlds.ui.sale_fee_5_minimum_1_coin",b.x()+480,b.y()+10,14,Ui.MUTED);Rect rows=new Rect(b.x(),b.y()+50,b.w()-299,b.h()-50);u.panel(rows);
 if(a.trade){trades(a,u,rows);CollectionScreen.inspector(a,u,new Rect(b.right()-281,b.y(),281,b.h()));return;}
 u.text("CARD",rows.x()+17,rows.y()+14,13,Ui.GOLD);u.text("cardworlds.ui.rarity_a4b8c",rows.x()+308,rows.y()+14,13,Ui.GOLD);u.text("cardworlds.ui.price",rows.right()-189,rows.y()+14,13,Ui.GOLD);int y=rows.y()+42;var listings=a.state.market().stream().filter(l->l.name().toLowerCase().contains(a.fields.getOrDefault("marketSearch","").toLowerCase())).toList();
 Rect viewport=new Rect(rows.x()+10,rows.y()+42,rows.w()-20,rows.h()-154);var marketScroll=a.beginScroll(u,"market/listings/"+a.state.page(),viewport,listings.size()*72);y=viewport.y()-marketScroll.offset();
 for(var l:listings){if(y+65<=viewport.y()||y>=viewport.bottom()){y+=72;continue;}var card=a.state.definitions().values().stream().filter(d->d.name().equals(l.name())).findFirst().orElse(null);Rect row=new Rect(rows.x()+10,y,rows.w()-34,65);u.fill(row,row.contains(u.mx,u.my)?0xFF173246:0xFF0A1C2A);if(card!=null){CardRenderer.draw(u,card,new Rect(row.x()+6,y+4,40,56),0,false,true,"market");u.text(card.name(),row.x()+60,y+10,18,Ui.WHITE);u.text(l.seller().equals(MinecraftClient.getInstance().player.getUuidAsString())?"Your listing":"Collector · "+l.seller().substring(0,8),row.x()+60,y+37,12,Ui.MUTED);u.text(card.rarity(),row.x()+297,y+23,14,Ui.GOLD);u.click(new Rect(row.x(),row.y(),row.w()-110,row.h()),()->a.selected=card.id());}u.text(Long.toString(l.price()),row.right()-175,y+23,18,Ui.GOLD);boolean mine=l.seller().equals(MinecraftClient.getInstance().player.getUuidAsString());u.button(mine?"Cancel":"Buy",new Rect(row.right()-93,y+18,82,29),!mine,mine||a.state.profile().coins()>=l.price(),()->a.send(mine?"cancel_listing":"buy",l.id()));y+=72;}
 a.endScroll(u,marketScroll);
 if(listings.isEmpty()){u.text("cardworlds.ui.no_listings",rows.x()+23,y+23,23,Ui.WHITE);u.paragraph("cardworlds.ui.select_an_owned_card_in_collection_then_list_it_here",new Rect(rows.x()+23,y+63,rows.w()-46,64),17,Ui.MUTED);}
 var selected=a.selectedCard();u.text(selected==null?"Select a card to sell":selected.name(),rows.x()+17,rows.bottom()-95,18,Ui.WHITE);a.field(u,"price","Price in HunterCoin",new Rect(rows.x()+17,rows.bottom()-62,189,34));u.button("cardworlds.ui.list_card",new Rect(rows.x()+217,rows.bottom()-62,125,34),true,selected!=null&&a.owned(selected.id())!=null,()->a.send("list",a.owned(a.selected).serial(),a.fields.getOrDefault("price","0")));
 u.button("<",new Rect(rows.right()-89,rows.bottom()-61,31,32),false,a.state.page()>0,()->a.send("refresh",Integer.toString(a.state.page()-1)));u.button(">",new Rect(rows.right()-48,rows.bottom()-61,31,32),false,a.state.market().size()==20,()->a.send("refresh",Integer.toString(a.state.page()+1)));
 CollectionScreen.inspector(a,u,new Rect(b.right()-281,b.y(),281,b.h()));
 }
 private void trades(CardWorldsScreen a,Ui u,Rect r){
  u.text("cardworlds.ui.direct_trade_990af",r.x()+17,r.y()+17,22,Ui.GOLD);
  a.select(u,a.peer.isBlank()?"cardworlds.ui.online_duelists":a.peer,new Rect(r.x()+17,r.y()+52,r.w()-34,30),a.state.players(),name->{a.peer=name;a.send("browse_trade",name);});
  Rect offers=new Rect(r.x()+17,r.y()+97,r.w()-34,104);var offerScroll=a.beginScroll(u,"trade/offers",offers,a.state.trades().size()*42);
  int y=offers.y()-offerScroll.offset();for(var t:a.state.trades()){
   if(y+36>offers.y()&&y<offers.bottom()){u.fit(t.offered()+" ? "+t.requested()+" + "+t.coins()+" coins",new Rect(offers.x(),y+5,offers.w()-216,29),16,Ui.WHITE);
    u.button(t.incoming()?"Accept":"Cancel",new Rect(offers.right()-207,y,86,30),t.incoming(),true,()->a.send(t.incoming()?"accept_offer":"cancel_offer",t.id()));
    if(t.incoming())u.button("cardworlds.ui.decline",new Rect(offers.right()-110,y,88,30),false,true,()->a.send("cancel_offer",t.id()));}y+=42;
  }a.endScroll(u,offerScroll);
  u.text("cardworlds.ui.request_from"+(a.state.tradePeer().isBlank()?"a collector":a.state.tradePeer()),r.x()+17,r.y()+216,16,Ui.GOLD);
  Map<String,vn.svarcade.tcg.fabric.TcgMod.BinderCard> unique=new LinkedHashMap<>();for(var card:a.state.tradeShelf())unique.putIfAbsent(card.name(),card);
  Rect shelf=new Rect(r.x()+17,r.y()+247,r.w()-34,Math.max(65,r.h()-323));var shelfScroll=a.beginScroll(u,"trade/shelf/"+a.state.tradePeer(),shelf,((unique.size()+5)/6)*156);
  int col=0;for(var c:unique.values()){var def=a.state.definitions().values().stream().filter(d->d.name().equals(c.name())).findFirst().orElse(null);if(def==null)continue;
   Rect cr=new Rect(shelf.x()+col%6*113,shelf.y()+col/6*156-shelfScroll.offset(),101,141);col++;if(!u.visible(cr))continue;
   CardRenderer.draw(u,def,cr,0,c.serial().equals(a.fields.get("requested")),true,"trade");u.click(cr,()->a.fields.put("requested",c.serial()));
  }a.endScroll(u,shelfScroll);
  a.field(u,"tradeCoins","Coins to include",new Rect(r.x()+17,r.bottom()-53,180,33));
  u.button("cardworlds.ui.send_offer",new Rect(r.x()+211,r.bottom()-53,160,33),true,a.selectedCard()!=null&&a.owned(a.selected)!=null&&a.fields.containsKey("requested"),()->a.send("offer",a.owned(a.selected).serial(),a.fields.get("requested"),a.fields.getOrDefault("tradeCoins","0")));
  u.fit("cardworlds.ui.your_card"+(a.selectedCard()==null?"select in Collection":a.selectedCard().name()),new Rect(r.x()+385,r.bottom()-43,r.w()-400,22),14,Ui.MUTED);
 }
}
