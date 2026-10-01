package vn.svarcade.tcg.client.screens;
import vn.svarcade.tcg.client.*;import vn.svarcade.tcg.client.component.*;import vn.svarcade.tcg.client.card.CardRenderer;import java.util.*;
public final class DeckBuilderScreen implements Page {
 public void render(CardWorldsScreen a,Ui u,Rect b){int y=b.y();u.text("cardworlds.ui.deck_builder_30473",b.x(),y,26,Ui.GOLD);
  a.select(u,a.deckName,new Rect(b.x()+200,y-3,270,32),a.state.savedDecks().stream().map(d->d.name()).toList(),a::loadDeck);
  List<String> templates=a.state.deckTemplates().keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();String selected=a.templateDeck.isBlank()?(templates.isEmpty()?"Templates":templates.getFirst()):a.templateDeck;
  a.select(u,"Template: "+selected,new Rect(b.right()-365,y-3,215,32),templates,v->a.templateDeck=v);
  u.button("cardworlds.ui.load",new Rect(b.right()-143,y-3,64,32),true,!templates.isEmpty(),()->{String id=a.templateDeck.isBlank()?templates.getFirst():a.templateDeck;a.loadTemplate(id);});
  u.button("cardworlds.ui.new",new Rect(b.right()-72,y-3,72,32),false,true,()->{a.main.clear();a.extra.clear();a.deckName="New Deck";a.templateDeck="";a.fields.put("deckName",a.deckName);});
  u.text("cardworlds.ui.catalog_e8ad6"+a.state.total()+" cards  ·  "+templates.size()+" generated deck templates",b.x(),y+34,13,Ui.MUTED);
  Rect pool=new Rect(b.x(),y+63,420,b.h()-63),deck=new Rect(pool.right()+16,y+63,b.w()-436,b.h()-63);u.panel(pool);u.panel(deck);a.field(u,"search","Search collection…",new Rect(pool.x()+12,pool.y()+12,pool.w()-24,32));
  var cards=a.state.definitions().values().stream().filter(c->a.count(c.id())>0&&CardWorldsLanguage.name(c).toLowerCase().contains(a.fields.getOrDefault("search","").toLowerCase())).sorted(Comparator.comparing(c->c.name())).toList();
  int cw=116,ch=158;Rect cardPool=new Rect(pool.x()+13,pool.y()+56,pool.w()-26,pool.h()-100);var poolScroll=a.beginScroll(u,"deck/pool",cardPool,((cards.size()+2)/3)*170);
  for(int i=0;i<cards.size();i++){var card=cards.get(i);Rect cr=new Rect(cardPool.x()+i%3*128,cardPool.y()+i/3*170-poolScroll.offset(),cw,ch);if(!u.visible(cr))continue;
   CardRenderer.draw(u,card,cr,a.count(card.id()),card.id().equals(a.selected),true,"deckpool");u.click(cr,()->{a.selected=card.id();a.add(card);});}
  a.endScroll(u,poolScroll);u.text("cardworlds.ui.click_a_card_to_add_an_available_copy",pool.x()+13,pool.bottom()-29,12,Ui.MUTED);
  a.field(u,"deckName","Deck name",new Rect(deck.x()+14,deck.y()+12,deck.w()-197,34));a.select(u,a.format,new Rect(deck.right()-169,deck.y()+12,154,34),List.of("CASUAL","RANKED","UNLIMITED","EVENT","UNDERGROUND"),v->a.format=v);
  u.text("cardworlds.ui.main_deck_97f62"+a.main.size()+" / "+a.state.rules().maxDeck(),deck.x()+17,deck.y()+60,19,Ui.WHITE);int mainH=Math.max(96,deck.h()-310);tiles(a,u,new Rect(deck.x()+15,deck.y()+89,deck.w()-30,mainH),a.main,false);
  int ey=deck.y()+91+mainH;u.text("cardworlds.ui.extra_deck_7af07"+a.extra.size()+" / "+a.state.rules().extraDeck(),deck.x()+17,ey,18,Ui.WHITE);tiles(a,u,new Rect(deck.x()+15,ey+26,deck.w()-30,112),a.extra,true);
  var errors=a.legality();u.fit(errors.isEmpty()?"Legal for "+a.format.toLowerCase():errors.getFirst(),new Rect(deck.x()+17,deck.bottom()-73,deck.w()-35,20),14,errors.isEmpty()?0xFF83D4A0:0xFFE5AD75);u.button("cardworlds.ui.clear",new Rect(deck.x()+16,deck.bottom()-46,100,31),false,true,()->{a.main.clear();a.extra.clear();});u.button("cardworlds.ui.save_deck",new Rect(deck.right()-184,deck.bottom()-46,168,31),true,errors.isEmpty(),a::saveDeck);
 }
 private void tiles(CardWorldsScreen a,Ui u,Rect r,List<String> serials,boolean extra){
  Map<String,List<String>> groups=new LinkedHashMap<>();for(String token:serials){var c=a.bySerial(token);if(c!=null)groups.computeIfAbsent(c.id(),k->new ArrayList<>()).add(token);}
  int cols=7,cw=(r.w()-14-6*8)/7,ch=Math.min(108,(int)(cw*1.4));var region=a.beginScroll(u,extra?"deck/extra":"deck/main",r,((groups.size()+cols-1)/cols)*(ch+24));
  int i=0;for(var group:groups.entrySet()){var card=a.state.definitions().get(group.getKey());Rect t=new Rect(r.x()+i%cols*(cw+8),r.y()+i/cols*(ch+24)-region.offset(),cw,ch);i++;if(!u.visible(new Rect(t.x(),t.y(),t.w(),t.h()+20)))continue;
   CardRenderer.draw(u,card,t,group.getValue().size(),false,true,"indeck");u.click(t,()->a.selected=card.id());
   u.button("-",new Rect(t.x(),t.bottom()-3,26,21),false,true,()->a.remove(group.getValue().getLast()));u.button("+",new Rect(t.right()-26,t.bottom()-3,26,21),false,true,()->a.add(card));}
  a.endScroll(u,region);
 }
}
