package vn.svarcade.tcg.client.card;

import vn.svarcade.tcg.data.Catalog;
import vn.svarcade.tcg.client.component.*;
import vn.svarcade.tcg.client.render.PokemonModels;
import net.minecraft.item.Items;

public final class CardRenderer {
    public static int color(Catalog.Card d){if(d.extra())return 0xFF9D7CDE;return switch(d.category()){case "trainer"->0xFFCEAB6C;case "item"->0xFF53BBA6;case "technique"->0xFF698BDC;case "reaction"->0xFFC275C5;case "stadium"->0xFF719681;default->switch(d.type()){case "fire"->0xFFECA05F;case "water"->0xFF61B5E7;case "grass"->0xFF8ACA7A;case "electric"->0xFFE3C864;case "ghost","psychic"->0xFFBA8AE4;default->0xFFA6B4C8;};};}
    public static void draw(Ui ui,Catalog.Card d,Rect r,int count,boolean selected,boolean owned,String key){
        boolean hover=r.contains(ui.mx,ui.my);int edge=selected?Ui.CYAN:hover?Ui.GOLD:color(d);
        ui.c.fillGradient(r.x(),r.y(),r.right(),r.bottom(),0xFF283E51,0xFF07131F);ui.ornament(r,edge);

        boolean pokemon=d.category().equals("pokemon");
        int headerH=pokemon?31:22;
        ui.fill(new Rect(r.x()+3,r.y()+3,r.w()-6,headerH-3),0xEE091522);
        ui.fit(vn.svarcade.tcg.client.component.CardWorldsLanguage.name(d),new Rect(r.x()+7,r.y()+5,r.w()-14,pokemon?14:17),Math.min(14,Math.max(9,r.w()/9)),Ui.WHITE);
        if(pokemon){
            String stars="★".repeat(Math.max(1,d.level()));
            ui.fit(stars,new Rect(r.x()+7,r.y()+18,r.w()-14,11),Math.min(10,Math.max(7,r.w()/13)),Ui.GOLD);
        }

        int effectH=pokemon?(r.h()<150?30:Math.min(58,r.h()/3)):Math.max(26,Math.min(44,r.h()/3));
        int infoH=pokemon?16:14;
        int artY=r.y()+headerH+3;
        int artBottom=r.bottom()-effectH-infoH-6;
        Rect art=new Rect(r.x()+5,artY,r.w()-10,Math.max(24,artBottom-artY));
        ui.c.fillGradient(art.x(),art.y(),art.right(),art.bottom(),0xFF142B40,0xFF0A1523);
        if(pokemon)PokemonModels.draw(ui,d.species(),d.aspects(),art,key);
        else {var item=switch(d.category()){case "item"->Items.POTION;case "trainer"->Items.WRITABLE_BOOK;case "technique"->Items.BLAZE_POWDER;case "reaction"->Items.SHIELD;case "stadium"->Items.BEACON;default->Items.ENCHANTED_BOOK;};int size=Math.min(art.w()-12,art.h()-8);ui.item(item,new Rect(art.x()+(art.w()-size)/2,art.y()+(art.h()-size)/2,size,size));}

        int infoY=art.bottom()+2;
        ui.fill(new Rect(r.x()+4,infoY,r.w()-8,infoH),0xEE0B1925);
        String tributeTag=pokemon?(d.tributeCount()==0?"FREE":d.tributeCount()+"T"):"";
        String info=pokemon?vn.svarcade.tcg.client.component.CardWorldsLanguage.t("cardworlds.type."+d.type()).toUpperCase(java.util.Locale.ROOT)+"  ATK "+d.power()+"  •  "+tributeTag:d.category().toUpperCase();
        ui.fit(info,new Rect(r.x()+8,infoY+3,r.w()-16,infoH-3),Math.min(10,Math.max(7,r.w()/15)),edge);

        Rect rules=new Rect(r.x()+5,infoY+infoH+1,r.w()-10,r.bottom()-(infoY+infoH+1)-4);
        ui.fill(rules,0xEE08131E);
        String rulesText=vn.svarcade.tcg.client.component.CardWorldsLanguage.effect(d);
        ui.paragraph(rulesText,rules.inset(4),Math.max(7,Math.min(10,r.w()/15)),Ui.WHITE);

        if(count>0)ui.text("×"+count,r.right()-28,r.y()+6,11,Ui.WHITE);
        if(!owned)ui.fill(r,0x66061320);if(selected)ui.frame(r.inset(2),Ui.CYAN);
        if(hover){
            String meta=pokemon?" ★"+d.level()+" • "+d.summonRequirement()+" • ATK "+d.power():"";
            ui.tooltip=vn.svarcade.tcg.client.component.CardWorldsLanguage.name(d)+meta+" — "+rulesText+" — Set: "+d.set()+" • Print rarity: "+d.rarity();
        }
    }
    public static void back(Ui ui,Rect r){ui.c.fillGradient(r.x(),r.y(),r.right(),r.bottom(),0xFF12334A,0xFF071421);ui.ornament(r,Ui.GOLD);ui.frame(r.inset(6),Ui.LINE);ui.item(Items.COMPASS,new Rect(r.x()+r.w()/2-16,r.y()+r.h()/2-16,32,32));}
}
