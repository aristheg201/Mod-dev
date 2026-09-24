package io.github.aristheg201.svarcade.client.nativeui

import com.google.gson.JsonObject
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import io.github.aristheg201.svarcade.client.cobblemon.PokemonModelRenderer
import io.github.aristheg201.svarcade.client.cobblemon.PokemonView
import io.github.aristheg201.svarcade.ui.*
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.language.I18n
import net.minecraft.resources.ResourceLocation
import java.util.UUID

class CosmeticStoreUi {
    var kind = "ARENA"
    var selected = ""
    var page = 0
    val scene = PokemonSceneState()
    val requests = mutableMapOf<String, String>()
}

object CosmeticStoreRenderer {
    data class Hooks(val control: (UiRect, String, Boolean, Boolean, () -> Unit) -> Unit, val intent: (String, JsonObject) -> Unit)

    fun render(gui: GuiGraphics, font: Font, area: UiRect, state: JsonObject, ui: CosmeticStoreUi, hooks: Hooks) {
        fun text(key: String) = I18n.get("gui.svarcade.store."+key)
        val balances=state.getAsJsonObject("balances")?:JsonObject()
        val gold=0xFFFFCD75.toInt()
        val teal=0xFF4CC7B2.toInt()
        val textColor=0xFFF2F6F4.toInt()
        val panel=0xFF101B1F.toInt()
        val panel2=0xFF17262B.toInt()

        gui.fill(area.x,area.y,area.right,area.bottom,0xFF060D11.toInt())
        gui.fill(area.x,area.y,area.right,area.y+42,panel)
        gui.fill(area.x,area.y,area.right,area.y+2,teal)
        hooks.control(UiRect(area.x+6,area.y+5,68,16),"‹ "+I18n.get("gui.svarcade.nav.store"),true,false){
            hooks.intent("open",JsonObject().apply{addProperty("module","arcade")})
        }

        val economyReady=state.bool("economyReady")
        val wallet=balances.entrySet().joinToString("   ") { (currency, amount) -> "$currency  ${amount.asString}" }
        val walletText=font.plainSubstrByWidth(wallet,(area.width-92).coerceAtLeast(80))
        gui.drawString(font,walletText,area.right-font.width(walletText)-10,area.y+9,if(economyReady)gold else 0xFFE36C5C.toInt(),true)

        val tabY=area.y+22
        val tabWidth=((area.width-26)/2).coerceAtLeast(58)
        listOf("ARENA" to "arenas","TACTICIAN" to "tacticians").forEachIndexed{index,(kind,label)->
            hooks.control(UiRect(area.x+8+index*(tabWidth+6),tabY,tabWidth,18),text(label),true,ui.kind==kind){
                ui.kind=kind
                ui.selected=""
                ui.page=0
            }
        }

        val offers=state.getAsJsonArray("offers")?.map{it.asJsonObject}?.filter{it.str("kind")==ui.kind}.orEmpty()
        val chosen=offers.find{it.str("id")==ui.selected}?:offers.firstOrNull{it.bool("equipped")}?:offers.firstOrNull()?:return
        ui.selected=chosen.str("id")

        val bodyTop=area.y+48
        val bodyBottom=area.bottom-8
        val bodyH=(bodyBottom-bodyTop).coerceAtLeast(72)
        val listWidth=(area.width*30/100).coerceIn(112,210).coerceAtMost((area.width-110).coerceAtLeast(80))
        val listRect=UiRect(area.x+8,bodyTop,listWidth,bodyH)
        gui.fill(listRect.x,listRect.y,listRect.right,listRect.bottom,panel)
        gui.fill(listRect.x,listRect.y,listRect.x+2,listRect.bottom,teal)

        val rowGap=4
        val needsPages=offers.size*28>listRect.height-10
        val pageSize=if(needsPages)((listRect.height-34)/28).coerceAtLeast(1) else offers.size.coerceAtLeast(1)
        val pages=(offers.size+pageSize-1)/pageSize
        ui.page=ui.page.coerceIn(0,(pages-1).coerceAtLeast(0))
        val rowHeight=if(needsPages)24 else ((listRect.height-10-rowGap*(offers.size-1).coerceAtLeast(0))/offers.size.coerceAtLeast(1)).coerceIn(24,36)
        offers.drop(ui.page*pageSize).take(pageSize).forEachIndexed{index,offer->
            val y=listRect.y+5+index*(rowHeight+rowGap)
            if(y+rowHeight>listRect.bottom-4)return@forEachIndexed
            val selected=offer==chosen
            val owned=offer.bool("owned")
            val equipped=offer.bool("equipped")
            val rect=UiRect(listRect.x+5,y,listRect.width-10,rowHeight)
            val status=when{equipped->" ✓";owned->" •";else->""}
            hooks.control(rect,name(offer)+status,true,selected){ui.selected=offer.str("id")}
        }
        if(needsPages){
            val y=listRect.bottom-23
            hooks.control(UiRect(listRect.x+5,y,24,18),"‹",ui.page>0,false){ui.page--}
            hooks.control(UiRect(listRect.right-29,y,24,18),"›",ui.page+1<pages,false){ui.page++}
            gui.drawCenteredString(font,"${ui.page+1}/$pages",listRect.x+listRect.width/2,y+5,textColor)
        }

        val previewX=listRect.right+8
        val previewW=(area.right-previewX-8).coerceAtLeast(74)
        val infoH=68
        val stageH=(bodyH-infoH-6).coerceAtLeast(20)
        val stage=UiRect(previewX,bodyTop,previewW,stageH)
        gui.fill(stage.x,stage.y,stage.right,stage.bottom,0xFF081217.toInt())
        gui.fill(stage.x,stage.y,stage.right,stage.y+2,teal)
        gui.fill(stage.x,stage.y,stage.x+2,stage.bottom,0xFF2A454B.toInt())
        gui.fill(stage.right-2,stage.y,stage.right,stage.bottom,0xFF2A454B.toInt())

        val arenaId=if(ui.kind=="ARENA")chosen.str("id") else state.str("arena","kanto_stadium")
        val arena=MinecraftArenaRegistry.definition(arenaId)
        val speciesId=ResourceLocation.tryParse(chosen.str("species"))
        val species=speciesId?.let(PokemonSpecies::getByIdentifier)
        if(arena!=null){
            val sceneRect=stage.inset(5)
            val origin=SceneVec3(arena.boardOrigin.x.toDouble(),arena.boardOrigin.y.toDouble(),arena.boardOrigin.z.toDouble())
            val cell=SceneVec3(arena.cellSize.x.toDouble(),arena.cellSize.y.toDouble(),arena.cellSize.z.toDouble())
            val camera=SceneCameraFraming.board(
                arena.camera(ArenaCameraRole.ARENA_PREVIEW,SceneCameras.TFT),sceneRect,origin,
                arena.boardColumns,arena.boardRows,
                arena.framingAnchors().map{SceneVec3(it.x.toDouble(),it.y.toDouble(),it.z.toDouble())},
                widthFraction=.76,cellSize=cell
            )
            val tactician=if(ui.kind=="TACTICIAN")chosen else state.getAsJsonArray("offers")?.map{it.asJsonObject}
                ?.find{it.str("kind")=="TACTICIAN"&&it.str("id")==state.str("tactician")}
            val actor=tactician?.let{offer->
                val authoredScale=runCatching{offer.get("scale").asDouble}.getOrDefault(1.0)
                val scale=if(ui.kind=="TACTICIAN")(authoredScale*1.25).coerceAtLeast(.82) else authoredScale
                val position=SceneVec3(arena.tacticianSpawn.x.toDouble(),arena.tacticianSpawn.y.toDouble(),arena.tacticianSpawn.z.toDouble())
                SceneTacticianNode(
                    CosmeticPreviewActors.arena(offer.str("id")),
                    SceneTransform(position,scale=SceneVec3(scale,scale,scale)),
                    entityId=offer.str("entity"),
                    pokemonSpecies=offer.str("species"),
                    pokemonAspects=offer.str("aspects").split(',').filter(String::isNotBlank).toSet()
                )
            }
            PokemonScene3D.render(gui,font,sceneRect,arena.boardColumns,arena.boardRows,emptyList(),ui.scene,
                camera=camera,arenaId=arenaId,arenaSeed="store:"+arenaId,tactician=actor)
            if(ui.kind=="TACTICIAN"&&species!=null){
                val aspects=chosen.str("aspects").split(',').filter(String::isNotBlank).toSet()
                val view=PokemonView(chosen.str("id"),"",speciesId.toString(),aspects,name(chosen),species.nationalPokedexNumber,false)
                val portraitW=(stage.width*30/100).coerceAtLeast(72)
                val portraitX=if(arena.tacticianSpawn.x < arena.boardOrigin.x + (arena.boardColumns-1)*arena.cellSize.x/2)
                    stage.x+12 else stage.right-portraitW-10
                val portraitRect=UiRect(portraitX,stage.y+25,portraitW,(stage.height-34).coerceAtLeast(40))
                gui.fill(portraitRect.x-4,portraitRect.y-4,portraitRect.right+4,portraitRect.bottom+4,0xA8081217.toInt())
                gui.fill(portraitRect.x-4,portraitRect.y-4,portraitRect.x-1,portraitRect.bottom+4,teal)
                PokemonModelRenderer.renderPreview(gui,view,CosmeticPreviewActors.portrait(chosen.str("id")),portraitRect)
            }
        }else if(ui.kind=="TACTICIAN"&&species!=null){
            val aspects=chosen.str("aspects").split(',').filter(String::isNotBlank).toSet()
            val view=PokemonView(chosen.str("id"),"",speciesId.toString(),aspects,name(chosen),species.nationalPokedexNumber,false)
            val modelRect=UiRect(stage.x+12,stage.y+26,(stage.width-24).coerceAtLeast(12),(stage.height-36).coerceAtLeast(12))
            PokemonModelRenderer.renderPreview(gui,view,"store:"+chosen.str("id"),modelRect)
        }

        gui.fill(stage.x+5,stage.y+5,stage.right-5,stage.y+21,0xC9071014.toInt())
        gui.drawString(font,font.plainSubstrByWidth(text("preview")+" · "+name(chosen),(stage.width-18).coerceAtLeast(30)),stage.x+10,stage.y+9,textColor,true)

        val info=UiRect(previewX,stage.bottom+6,previewW,(bodyBottom-stage.bottom-6).coerceAtLeast(44))
        gui.fill(info.x,info.y,info.right,info.bottom,panel)
        gui.fill(info.x,info.y,info.x+3,info.bottom,if(chosen.bool("equipped"))gold else teal)
        val owned=chosen.bool("owned")
        val equipped=chosen.bool("equipped")
        val stateLabel=when{equipped->text("equipped");owned->text("owned");else->chosen.str("price")+" "+chosen.str("currency")}
        gui.drawString(font,font.plainSubstrByWidth(name(chosen),(info.width-20).coerceAtLeast(12)),info.x+10,info.y+7,textColor,true)
        gui.drawString(font,font.plainSubstrByWidth(stateLabel,(info.width-20).coerceAtLeast(12)),info.x+10,info.y+20,if(owned)teal else gold,false)

        val actionW=info.width-16
        val actionX=info.x+8
        val currencyReady=chosen.bool("currencyReady") || chosen.str("price")=="0"
        val buttonLabel=when {
            equipped -> text("equipped")
            owned -> text("equip")
            !currencyReady -> text("economy_unavailable")
            else -> I18n.get("gui.svarcade.store.buy",chosen.str("price"),chosen.str("currency"))
        }
        val actionEnabled=!equipped && (owned || currencyReady)
        hooks.control(UiRect(actionX,info.bottom-28,actionW,22),buttonLabel,actionEnabled,equipped){
            val key=ui.kind+":"+ui.selected
            hooks.intent(if(owned)"equip" else "buy",JsonObject().apply{
                addProperty("kind",ui.kind)
                addProperty("id",ui.selected)
                addProperty("requestId",ui.requests.getOrPut(key){UUID.randomUUID().toString()})
            })
        }
    }

    private fun name(offer:JsonObject)=if(offer.str("kind")=="ARENA")I18n.get("gui.svarcade.store.arena."+offer.str("id")) else offer.str("name")
    private fun JsonObject.str(key:String,fallback:String="")=runCatching{get(key)?.asString?:fallback}.getOrDefault(fallback)
    private fun JsonObject.bool(key:String)=runCatching{get(key)?.asBoolean==true}.getOrDefault(false)
}
