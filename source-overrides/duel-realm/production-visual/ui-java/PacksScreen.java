package vn.svarcade.tcg.client.screens;
import vn.svarcade.tcg.client.*;
import vn.svarcade.tcg.client.component.*;
import vn.svarcade.tcg.client.render.PokemonModels;
import vn.svarcade.tcg.data.Catalog;
import net.minecraft.item.Items;
import java.util.*;

public final class PacksScreen implements Page {
 public static Catalog.Card preview(CardWorldsScreen a){return a.state.banners().stream().filter(b->b.id().equals(a.banner)).findFirst().map(b->a.state.definitions().get(b.previewCard())).orElse(null);}
 public void render(CardWorldsScreen a,Ui u,Rect b){
  u.text("cardworlds.ui.packs_ac22d",b.x(),b.y(),29,Ui.GOLD);if(a.state.banners().isEmpty())return;
  var selected=a.state.banners().stream().filter(t->t.id().equals(a.banner)).findFirst().orElse(a.state.banners().getFirst());a.banner=selected.id();
  Rect list=new Rect(b.x(),b.y()+50,221,b.h()-50);var listScroll=a.beginScroll(u,"packs/list",list,a.state.banners().size()*112-13);
  for(int i=0;i<a.state.banners().size();i++){
   var banner=a.state.banners().get(i);Rect r=new Rect(list.x(),list.y()+i*112-listScroll.offset(),207,99);if(!u.visible(r))continue;
   u.panel(r);var mascot=a.state.definitions().get(banner.previewCard());
   if(mascot!=null)PokemonModels.draw(u,mascot.species(),mascot.aspects(),new Rect(r.x()+5,r.y()+12,53,70),"pack-list/"+banner.id());else u.item(Items.ENDER_CHEST,new Rect(r.x()+13,r.y()+19,39,39));
   u.paragraph(banner.name(),new Rect(r.x()+65,r.y()+17,131,62),18,Ui.WHITE);if(banner.id().equals(a.banner))u.ornament(r,Ui.CYAN);
   u.click(r,()->a.banner=banner.id());
  }a.endScroll(u,listScroll);
  Rect art=new Rect(b.x()+234,b.y()+50,380,b.h()-50);u.arena(art);u.fill(art,0x8809192B);
  Rect pack=new Rect(art.x()+55,art.y()+37+(int)(Math.sin(System.currentTimeMillis()/1200.0)*5),270,Math.min(369,art.h()-75));
  pack(u,pack,selected.name(),preview(a),"pack/"+selected.id());
  Rect info=new Rect(art.right()+17,b.y()+50,b.right()-art.right()-17,b.h()-50);u.panel(info);int ix=info.x()+17;
  u.paragraph(selected.name(),new Rect(ix,info.y()+18,info.w()-34,64),25,Ui.GOLD);
  u.text(selected.slots()+" cards per pack",ix,info.y()+88,17,Ui.WHITE);
  u.text("cardworlds.ui.pity"+selected.pity()+" / "+selected.hardPity(),ix,info.y()+123,17,Ui.WHITE);
  u.meter(new Rect(ix,info.y()+149,info.w()-34,8),selected.pity()/(double)selected.hardPity(),Ui.GOLD);
  u.fit(selected.guaranteed()?"Next high rarity: featured":"Featured guarantee available",new Rect(ix,info.y()+173,info.w()-34,19),13,Ui.MUTED);
  u.text("cardworlds.ui.next_card_rates",ix,info.y()+207,15,Ui.GOLD);
  Rect rates=new Rect(ix,info.y()+234,info.w()-34,Math.max(50,info.h()-392));
  var rateScroll=a.beginScroll(u,"packs/rates/"+selected.id(),rates,selected.rates().size()*22);
  for(int i=0;i<selected.rates().size();i++){Rect line=new Rect(ix,rates.y()+i*22-rateScroll.offset(),rates.w()-14,20);if(u.visible(line))u.fit(selected.rates().get(i),line,14,Ui.MUTED);}
  a.endScroll(u,rateScroll);
  u.text("cardworlds.ui.scroll_to_view_all_rates",ix,info.bottom()-145,12,Ui.MUTED);
  CurrencyPurchaseUi.render(a,u,selected,info,ix);
 }
 public static void pack(Ui u,Rect r,String name,Catalog.Card mascot,String key){
  u.c.fillGradient(r.x(),r.y(),r.right(),r.bottom(),0xFF46547E,0xFF151226);u.ornament(r,Ui.GOLD);u.frame(r.inset(7),0xFF958061);
  u.fill(new Rect(r.x()+8,r.y()+9,r.w()-16,13),0xFFAC925E);u.fill(new Rect(r.x()+8,r.bottom()-20,r.w()-16,11),0xFFAC925E);
  if(mascot!=null)PokemonModels.draw(u,mascot.species(),mascot.aspects(),new Rect(r.x()+11,r.y()+32,r.w()-22,r.h()-112),key);
  else u.item(Items.ENDER_CHEST,new Rect(r.x()+r.w()/2-32,r.y()+r.h()/2-32,64,64));
  u.paragraph(name,new Rect(r.x()+19,r.bottom()-75,r.w()-38,55),25,Ui.GOLD);
 }
}
