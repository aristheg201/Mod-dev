package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.ui.UiRect
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.language.I18n

/** Shared end-of-game presentation. Games provide semantic payload; this screen owns every pixel in its area. */
object ArcadeResultRenderer {
    data class Hooks(
        val control:(UiRect,String,Boolean,()->Unit)->Unit,
        val continueAction:()->Unit,
        val rematch:()->Unit,
        val exit:()->Unit
    )

    fun render(gui:GuiGraphics,font:Font,area:UiRect,view:JsonObject,hooks:Hooks,backdrop:(UiRect)->Unit) {
        val result=view.getAsJsonObject("resultPresentation")?:return
        val gameId=view.str("gameId","arcade")
        val accent=gameAccent(gameId)
        gui.fill(area.x,area.y,area.right,area.bottom,0xFF050B0E.toInt())

        val margin=6
        val controlsH=27
        val sceneHeight=(area.height-120).coerceAtLeast(50)
        val scene=UiRect(area.x+margin,area.y+margin,(area.width-margin*2).coerceAtLeast(80),sceneHeight)
        gui.fill(scene.x,scene.y,scene.right,scene.bottom,0xFF081217.toInt())
        backdrop(UiRect(scene.x,scene.y,scene.width,(scene.height-42).coerceAtLeast(8)))
        gui.fill(scene.x,scene.y,scene.right,scene.y+3,accent)
        gui.fill(scene.x,scene.bottom-40,scene.right,scene.bottom,0xE4070E12.toInt())

        val outcome=result.str("outcome","complete")
        val outcomeText=trOr("gui.svhub.result."+outcome,outcome.replaceFirstChar(Char::uppercase))
        gui.drawCenteredString(font,outcomeText,scene.x+scene.width/2,scene.bottom-32,if(outcome=="defeat")0xFFE36C5C.toInt() else 0xFFE2BE62.toInt())
        val reason=result.str("reason","complete")
        gui.drawCenteredString(font,trOr("gui.svhub.result.reason."+reason,tr("gui.svhub.result.complete")),scene.x+scene.width/2,scene.bottom-18,0xFFF2F6F4.toInt())

        val panelTop=scene.bottom+6
        val panelBottom=area.bottom-controlsH-5
        val panelH=(panelBottom-panelTop).coerceAtLeast(44)
        gui.fill(area.x+margin,panelTop,area.right-margin,panelBottom,0xF0101B1F.toInt())
        val gap=5
        val contentW=area.width-margin*2
        val columnW=((contentW-gap*4)/3).coerceAtLeast(64)
        renderSection(gui,font,UiRect(area.x+margin+gap,panelTop+5,columnW,panelH-10),tr("gui.svhub.result.stats"),result.getAsJsonArray("stats"),"metric",accent)
        renderSection(gui,font,UiRect(area.x+margin+gap*2+columnW,panelTop+5,columnW,panelH-10),tr("gui.svhub.result.rewards"),result.getAsJsonArray("rewards"),"reward",0xFFE2BE62.toInt())
        renderSection(gui,font,UiRect(area.x+margin+gap*3+columnW*2,panelTop+5,columnW,panelH-10),tr("gui.svhub.result.progress"),result.getAsJsonArray("progression"),"progress",0xFF4CC7B2.toInt())

        val y=area.bottom-controlsH+2
        val buttonGap=4
        val buttonW=((area.width-buttonGap*4)/3).coerceAtLeast(52)
        hooks.control(UiRect(area.x+buttonGap,y,buttonW,20),tr("gui.svhub.result.continue"),true,hooks.continueAction)
        hooks.control(UiRect(area.x+buttonGap*2+buttonW,y,buttonW,20),tr("gui.svhub.result.rematch"),result.bool("canRematch",true),hooks.rematch)
        hooks.control(UiRect(area.x+buttonGap*3+buttonW*2,y,buttonW,20),tr("gui.svhub.result.exit"),true,hooks.exit)
    }

    private fun renderSection(gui:GuiGraphics,font:Font,rect:UiRect,title:String,lines:JsonArray?,kind:String,accent:Int){
        gui.fill(rect.x,rect.y,rect.right,rect.bottom,0xE018272B.toInt())
        gui.fill(rect.x,rect.y,rect.x+3,rect.bottom,accent)
        gui.fill(rect.x,rect.y,rect.right,rect.y+1,accent)
        gui.drawString(font,title,rect.x+8,rect.y+6,0xFFF2F6F4.toInt(),true)
        val entries=lines?.let{array->(0 until array.size()).mapNotNull{runCatching{array[it].asJsonObject}.getOrNull()}}.orEmpty()
        if(entries.isEmpty()){
            gui.drawString(font,tr("gui.svhub.result.none"),rect.x+8,rect.y+21,0xFF91A6A1.toInt(),false)
            return
        }
        var y=rect.y+21
        entries.take(4).forEach{line->
            val key=line.str("key")
            val value=line.str("value")
            val label=trOr("gui.svhub.result."+kind+"."+key,humanize(key))
            val rendered=if(value.isBlank())label else label+": "+value
            gui.drawString(font,font.plainSubstrByWidth(rendered,(rect.width-14).coerceAtLeast(12)),rect.x+8,y,0xFF91A6A1.toInt(),false)
            y+=11
            if(y+9>rect.bottom)return
        }
    }

    private fun gameAccent(id:String)=when(id){
        "chess"->0xFFE4D9BE.toInt()
        "tower_defense"->0xFF80B56B.toInt()
        "tft"->0xFF4CC7B2.toInt()
        else->0xFFE2BE62.toInt()
    }
    private fun humanize(value:String)=value.replace('_',' ').split(' ').filter(String::isNotBlank).joinToString(" "){it.replaceFirstChar(Char::uppercase)}
    private fun trOr(key:String,fallback:String):String=I18n.get(key).let{if(it==key)fallback else it}
    private fun tr(key:String)=I18n.get(key)
    private fun JsonObject.str(key:String,fallback:String="")=runCatching{get(key)?.asString?:fallback}.getOrDefault(fallback)
    private fun JsonObject.bool(key:String,fallback:Boolean=false)=runCatching{get(key)?.asBoolean?:fallback}.getOrDefault(fallback)
}
