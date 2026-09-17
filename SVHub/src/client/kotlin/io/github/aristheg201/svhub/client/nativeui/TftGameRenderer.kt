package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.JsonObject
import io.github.aristheg201.svhub.client.cobblemon.PokemonModelRenderer
import io.github.aristheg201.svhub.client.cobblemon.PokemonView
import io.github.aristheg201.svhub.ui.TftLayoutResolver
import io.github.aristheg201.svhub.ui.UiDensity
import io.github.aristheg201.svhub.ui.UiRect
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import kotlin.math.max
import kotlin.math.min

class TftUiState {
    var selectedOrigin: String? = null
    var selectedIndex: Int? = null
    var selectedItem: Int? = null
    fun clearUnit(){ selectedOrigin=null; selectedIndex=null }
}

object TftGameRenderer {
    private data class UnitToken(val instanceId:String,val unitId:String,val species:String,val star:Int,val hp:Int,val maxHp:Int,val mana:Int,val maxMana:Int,val team:Int,val aspects:Set<String>,val items:List<String>)
    private data class BenchToken(val index:Int,val instanceId:String,val unitId:String,val species:String,val star:Int,val aspects:Set<String>,val items:List<String>)
    private val bg=0xFF091215.toInt();private val panel=0xFF101B1F.toInt();private val panel2=0xFF18272B.toInt();private val line=0xFF2A3B3F.toInt();private val text=0xFFF2F6F4.toInt();private val muted=0xFF91A6A1.toInt();private val accent=0xFF4CC7B2.toInt();private val gold=0xFFE2BE62.toInt();private val danger=0xFFE36C5C.toInt()

    fun render(gui:GuiGraphics,font:Font,area:UiRect,density:UiDensity,view:JsonObject,mouseX:Int,mouseY:Int,ui:TftUiState,hit:(UiRect,String,()->Unit)->Unit,action:(String,Map<String,String>)->Unit){
        gui.fill(area.x,area.y,area.right,area.bottom,bg)
        val fields=view.getAsJsonObject("fields")?:JsonObject();val phase=view.str("phase");val canEdit=fields.str("canEditBoard")=="true"
        val layout=TftLayoutResolver.resolve(area,density)
        renderHud(gui,font,layout.hud,fields,phase)
        layout.traits?.let{renderTraits(gui,font,it,fields.str("traits"))}
        layout.players?.let{renderPlayers(gui,font,it,fields.str("players"))}
        val board=view.getAsJsonArray("board");val units=if(board==null)emptyMap() else (0 until board.size()).mapNotNull{i->parseUnit(board[i].asString)?.let{i to it}}.toMap()
        renderBoard(gui,font,layout.board,units,phase,canEdit,ui,hit,action)
        renderFooter(gui,font,layout.footer,view,fields,canEdit,ui,hit,action)
        if(density==UiDensity.COMPACT)renderCompactInfo(gui,font,area,fields)
        renderAugments(gui,font,layout.board,fields.str("augmentChoices"),action,hit)
        if(phase=="draft")renderDraft(gui,font,layout.board,fields.str("draft"),action,hit)
    }

    private fun renderHud(gui:GuiGraphics,font:Font,r:UiRect,f:JsonObject,phase:String){
        gui.fill(r.x,r.y,r.right,r.bottom,panel);gui.fill(r.x,r.bottom-1,r.right,r.bottom,line)
        val title="${f.str("round","1-1")}  •  Lv.${f.int("level",2)}  ${f.int("xp")}/${f.int("xpNext")} XP  •  ${f.int("gold")}g  •  HP ${f.int("hp",100)}"
        gui.drawString(font,fit(font,title,r.width-120),r.x+8,r.y+7,text,true)
        val timer=((f.long("phaseEndsAt")-System.currentTimeMillis()).coerceAtLeast(0L)+999)/1000
        gui.drawString(font,"${phase.uppercase()} ${if(timer>0)"${timer}s" else ""}",r.right-104,r.y+7,if(phase=="combat")danger else gold,true)
        if(r.height>24)gui.drawString(font,"Interest ${f.int("lastInterest")}  •  Streak ${f.int("streak")}",r.x+8,r.y+19,muted,false)
    }

    private fun renderTraits(gui:GuiGraphics,font:Font,r:UiRect,raw:String){
        gui.fill(r.x,r.y,r.right,r.bottom,panel);gui.drawString(font,"TRAITS",r.x+7,r.y+7,muted,true);var y=r.y+22
        raw.split(';').filter(String::isNotBlank).take(10).forEach{v->val p=v.split('~');if(p.size>=5){val count=p[2].toIntOrNull()?:0;val active=p[3].toIntOrNull()?:0;val next=p[4].toIntOrNull()?:0;gui.fill(r.x+5,y,r.right-5,y+23,if(active>0)0xFF18312D.toInt() else panel2);gui.fill(r.x+5,y,r.x+8,y+23,if(active>0)accent else muted);gui.drawString(font,fit(font,p.getOrElse(1){p[0]},r.width-44),r.x+12,y+5,text,active>0);gui.drawString(font,count.toString(),r.right-21,y+5,if(active>0)accent else muted,true);gui.drawString(font,if(next>0)"$active/$next" else "$active+",r.x+12,y+14,muted,false);y+=27;if(y+23>r.bottom)return}}
    }

    private fun renderPlayers(gui:GuiGraphics,font:Font,r:UiRect,raw:String){
        gui.fill(r.x,r.y,r.right,r.bottom,panel);gui.drawString(font,"PLAYERS",r.x+7,r.y+7,muted,true);var y=r.y+22
        raw.split(';').filter(String::isNotBlank).take(8).forEachIndexed{i,v->val p=v.split('~');if(p.size>=6){val hp=p[2].toIntOrNull()?:0;val place=p[4].toIntOrNull()?:0;val dead=p[5]=="1";gui.fill(r.x+5,y,r.right-5,y+21,panel2);gui.drawString(font,if(place>0)"#$place" else "${i+1}",r.x+8,y+5,if(dead)muted else gold,true);gui.drawString(font,fit(font,p[1],r.width-55),r.x+30,y+5,if(dead)muted else text,false);gui.drawString(font,hp.coerceAtLeast(0).toString(),r.right-23,y+5,if(hp<30)danger else text,true);y+=24;if(y+20>r.bottom)return}}
    }

    private fun renderBoard(gui:GuiGraphics,font:Font,r:UiRect,units:Map<Int,UnitToken>,phase:String,canEdit:Boolean,ui:TftUiState,hit:(UiRect,String,()->Unit)->Unit,action:(String,Map<String,String>)->Unit){
        gui.fill(r.x,r.y,r.right,r.bottom,0xFF0D171A.toInt())
        val tileW=min(((r.width-8)*2/15).coerceAtLeast(10),(((r.height-8)*4/25).coerceAtLeast(8))*4/3).coerceAtLeast(10);val tileH=max(8,tileW*3/4);val stepY=max(6,tileH-tileH/4);val bw=tileW*7+tileW/2;val bh=tileH+stepY*7;val sx=r.x+(r.width-bw)/2;val sy=r.y+(r.height-bh)/2
        for(row in 0 until 8)for(col in 0 until 7){val index=row*7+col;val x=sx+col*tileW+if(row and 1==1)tileW/2 else 0;val y=sy+row*stepY;val cell=UiRect(x,y,tileW-1,tileH-1);val own=row>=4;val selected=ui.selectedOrigin=="board"&&own&&ui.selectedIndex==index-28;drawHex(gui,cell,when{selected->0xFF2D7067.toInt();own->0xFF173530.toInt();else->0xFF302126.toInt()},if(own)0xFF356D63.toInt() else 0xFF6D3A42.toInt());val token=units[index];if(token!=null)renderUnit(gui,font,cell,token);if(canEdit&&phase=="planning"&&own)hit(cell,""){val slot=index-28;when{ui.selectedOrigin=="bench"&&ui.selectedIndex!=null->{action("deploy",mapOf("bench" to ui.selectedIndex.toString(),"slot" to slot.toString()));ui.clearUnit()};ui.selectedOrigin=="board"&&ui.selectedIndex!=null->{if(ui.selectedIndex==slot)ui.clearUnit() else{action("move",mapOf("from" to ui.selectedIndex.toString(),"to" to slot.toString()));ui.clearUnit()}};token!=null->{ui.selectedOrigin="board";ui.selectedIndex=slot}}}}
    }

    private fun renderUnit(gui:GuiGraphics,font:Font,r:UiRect,u:UnitToken){
        val modelSize=min(r.width,r.height*2).coerceAtLeast(12);if(r.width>=28&&r.height>=18){pokemonView(u.species,u.aspects,u.unitId)?.let{PokemonModelRenderer.render(gui,it,r.x+r.width/2,r.y+r.height/2+2,modelSize,yaw=if(u.team==0)0f else 180f,zoom=.8f,pitch=60f)}} else gui.drawCenteredString(font,shortUnit(u.unitId),r.x+r.width/2,r.y+r.height/2-4,text)
        val stars="★".repeat(u.star.coerceIn(1,3));gui.drawCenteredString(font,stars,r.x+r.width/2,r.y+1,gold)
        if(u.maxHp>0){val w=(r.width-4).coerceAtLeast(4);val hpw=(w*u.hp.coerceAtLeast(0)/u.maxHp).coerceIn(0,w);gui.fill(r.x+2,r.bottom-5,r.x+2+w,r.bottom-3,0xFF2A2020.toInt());gui.fill(r.x+2,r.bottom-5,r.x+2+hpw,r.bottom-3,if(u.team==0)accent else danger);if(u.maxMana>0){val mw=(w*u.mana.coerceAtLeast(0)/u.maxMana).coerceIn(0,w);gui.fill(r.x+2,r.bottom-2,r.x+2+mw,r.bottom,0xFF4D8BD8.toInt())}}
    }

    private fun renderFooter(gui:GuiGraphics,font:Font,r:UiRect,view:JsonObject,f:JsonObject,canEdit:Boolean,ui:TftUiState,hit:(UiRect,String,()->Unit)->Unit,action:(String,Map<String,String>)->Unit){
        gui.fill(r.x,r.y,r.right,r.bottom,panel);val compact=r.height<70;val benchRaw=f.str("bench");val bench=parseBench(benchRaw);val benchH=if(compact)16 else 25;val gap=2;val benchW=(r.width-gap*8)/9
        repeat(9){i->val x=r.x+i*(benchW+gap);val cell=UiRect(x,r.y,benchW,benchH);gui.fill(cell.x,cell.y,cell.right,cell.bottom,if(ui.selectedOrigin=="bench"&&ui.selectedIndex==i)0xFF275248.toInt() else panel2);bench.firstOrNull{it.index==i}?.let{b->gui.drawCenteredString(font,shortUnit(b.unitId),cell.x+cell.width/2,cell.y+4,text);if(!compact)gui.drawCenteredString(font,"★".repeat(b.star),cell.x+cell.width/2,cell.y+14,gold);if(canEdit)hit(cell,""){ui.selectedOrigin="bench";ui.selectedIndex=i}}}
        val cards=view.getAsJsonArray("cards");val shopY=r.y+benchH+4;if(cards!=null&&shopY<r.bottom){val n=min(5,cards.size());val shopW=(r.width-gap*(n-1))/n;repeat(n){i->val c=cards[i].asJsonObject;val cell=UiRect(r.x+i*(shopW+gap),shopY,shopW,(r.bottom-shopY).coerceAtLeast(14));gui.fill(cell.x,cell.y,cell.right,cell.bottom,panel2);val cost=c.get("value")?.asInt?:1;gui.fill(cell.x,cell.y,cell.x+3,cell.bottom,costColor(cost));gui.drawString(font,fit(font,c.get("label")?.asString?:"",cell.width-16),cell.x+6,cell.y+4,text,true);gui.drawString(font,"${cost}g",cell.right-18,cell.y+4,gold,true);if(cell.height>22)gui.drawString(font,fit(font,c.get("subtitle")?.asString?:"",cell.width-10),cell.x+6,cell.y+15,muted,false);hit(cell,""){action("buy",mapOf("index" to c.get("id").asString.substringAfter(':')))}}}
        val items=f.str("itemBench").split(',').filter(String::isNotBlank);if(items.isNotEmpty()&&!compact){var x=r.x;val y=(r.y-16).coerceAtLeast(0);items.take(8).forEachIndexed{i,item->val cell=UiRect(x+i*18,y,16,14);gui.fill(cell.x,cell.y,cell.right,cell.bottom,if(ui.selectedItem==i)0xFF544B28.toInt() else panel2);gui.drawCenteredString(font,itemGlyph(item),cell.x+8,cell.y+3,gold);hit(cell,""){ui.selectedItem=if(ui.selectedItem==i)null else i}}}
    }

    private fun renderCompactInfo(gui:GuiGraphics,font:Font,area:UiRect,f:JsonObject){val label="${f.int("boardCount")}/${f.int("unitCap")} • ${f.str("opponent")}";gui.drawString(font,fit(font,label,area.width-12),area.x+6,area.y+26,muted,false)}
    private fun renderAugments(gui:GuiGraphics,font:Font,b:UiRect,raw:String,action:(String,Map<String,String>)->Unit,hit:(UiRect,String,()->Unit)->Unit){val choices=raw.split(';').filter(String::isNotBlank);if(choices.isEmpty())return;val root=b.inset(10);gui.fill(root.x,root.y,root.right,root.bottom,0xF20C1518.toInt());gui.drawCenteredString(font,"CHOOSE AUGMENT",root.x+root.width/2,root.y+8,gold);val w=(root.width-16)/choices.size.coerceAtMost(3);choices.take(3).forEachIndexed{i,v->val p=v.split('~');val r=UiRect(root.x+5+i*w,root.y+24,w-5,(root.height-30).coerceAtLeast(22));gui.fill(r.x,r.y,r.right,r.bottom,panel2);gui.drawCenteredString(font,fit(font,p.getOrElse(1){p[0]},r.width-8),r.x+r.width/2,r.y+6,text);if(r.height>32)gui.drawString(font,fit(font,p.getOrElse(2){""},r.width-10),r.x+5,r.y+20,muted,false);hit(r,""){action("choose_augment",mapOf("id" to p[0]))}}}
    private fun renderDraft(gui:GuiGraphics,font:Font,b:UiRect,raw:String,action:(String,Map<String,String>)->Unit,hit:(UiRect,String,()->Unit)->Unit){val offers=raw.split(';').filter(String::isNotBlank);if(offers.isEmpty())return;val root=b.inset(8);gui.fill(root.x,root.y,root.right,root.bottom,0xE80B1417.toInt());gui.drawCenteredString(font,"SHARED DRAFT",root.x+root.width/2,root.y+5,gold);val cols=if(root.width>=330)5 else 3;val gap=4;val w=(root.width-gap*(cols-1))/cols;offers.forEachIndexed{i,v->val p=v.split('~');if(p.size>=6){val rr=UiRect(root.x+(i%cols)*(w+gap),root.y+18+(i/cols)*32,w,28);val taken=p[4].isNotBlank();gui.fill(rr.x,rr.y,rr.right,rr.bottom,if(taken)0xFF172023.toInt() else panel2);gui.drawString(font,fit(font,p[1],rr.width-8),rr.x+5,rr.y+5,if(taken)muted else text,true);gui.drawString(font,itemGlyph(p[3]),rr.x+5,rr.y+16,gold,false);if(!taken&&p[5]=="1")hit(rr,""){action("draft_pick",mapOf("index" to p[0]))}}}}
    private fun drawHex(gui:GuiGraphics,r:UiRect,fill:Int,border:Int){val cut=max(2,r.width/8);val q=max(2,r.height/4);gui.fill(r.x+cut,r.y,r.right-cut,r.bottom,border);gui.fill(r.x,r.y+q,r.right,r.bottom-q,border);gui.fill(r.x+cut+1,r.y+1,r.right-cut-1,r.bottom-1,fill);gui.fill(r.x+1,r.y+q+1,r.right-1,r.bottom-q-1,fill)}
    private fun pokemonView(speciesId:String,aspects:Set<String>,fallback:String):PokemonView?{val species=ResourceLocation.tryParse(speciesId)?.let(PokemonSpecies::getByIdentifier)?:return null;return PokemonView("$speciesId|${aspects.sorted().joinToString(",")}","",speciesId,aspects,species.translatedName.string.ifBlank{fallback},species.nationalPokedexNumber,species.resourceIdentifier.namespace!="cobblemon")}
    private fun parseUnit(raw:String):UnitToken?{if(raw.isBlank())return null;val p=raw.split('~');if(p.size<9)return null;return UnitToken(p[0],p[1],p[2],p[3].toIntOrNull()?:1,p[4].toIntOrNull()?:-1,p[5].toIntOrNull()?:-1,p[6].toIntOrNull()?:0,p[7].toIntOrNull()?:0,p[8].toIntOrNull()?:0,p.getOrNull(9).orEmpty().split(',').filter(String::isNotBlank).toSet(),p.getOrNull(10).orEmpty().split(',').filter(String::isNotBlank))}
    private fun parseBench(raw:String)=raw.split(';').filter(String::isNotBlank).mapNotNull{v->val p=v.split('~');if(p.size<5)null else BenchToken(p[0].toIntOrNull()?:return@mapNotNull null,p[1],p[2],p[3],p[4].toIntOrNull()?:1,p.getOrNull(5).orEmpty().split(',').filter(String::isNotBlank).toSet(),p.getOrNull(6).orEmpty().split(',').filter(String::isNotBlank))}
    private fun JsonObject.str(k:String,f:String="")=runCatching{get(k)?.asString?:f}.getOrDefault(f);private fun JsonObject.int(k:String,f:Int=0)=runCatching{get(k)?.asInt?:f}.getOrDefault(f);private fun JsonObject.long(k:String,f:Long=0L)=runCatching{get(k)?.asLong?:f}.getOrDefault(f)
    private fun fit(font:Font,v:String,w:Int)=font.plainSubstrByWidth(v,w.coerceAtLeast(4));private fun shortUnit(id:String)=id.replace('_',' ').split(' ').joinToString(""){it.take(2)}.take(5).uppercase();private fun costColor(cost:Int)=when(cost){1->0xFF8EA09B.toInt();2->0xFF63BE7B.toInt();3->0xFF5B9BE5.toInt();4->0xFFA66DDB.toInt();else->0xFFE0B44E.toInt()};private fun itemGlyph(id:String)=when{ id.startsWith("full:")->"◆";id.startsWith("combo:")->"◇";id.contains("sword")->"⚔";id.contains("rod")->"✦";else->"•" }
}
