package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.JsonObject
import io.github.aristheg201.svhub.ui.*
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.language.I18n
import java.util.UUID

class CosmeticStoreUi {
    var kind = "ARENA"
    var selected = ""
    val scene = PokemonSceneState()
    val requests = mutableMapOf<String, String>()
}

object CosmeticStoreRenderer {
    data class Hooks(val control: (UiRect, String, Boolean, Boolean, () -> Unit) -> Unit, val intent: (String, JsonObject) -> Unit)

    fun render(gui: GuiGraphics, font: Font, area: UiRect, state: JsonObject, ui: CosmeticStoreUi, hooks: Hooks) {
        fun text(key: String) = I18n.get("gui.svhub.store."+key)
        val balances=state.getAsJsonObject("balances")?:JsonObject()
        val gold=0xFFFFCD75.toInt()
        val teal=0xFF4CC7B2.toInt()
        val textColor=0xFFF2F6F4.toInt()
        val panel=0xFF101B1F.toInt()
        val panel2=0xFF17262B.toInt()

        gui.fill(area.x,area.y,area.right,area.bottom,0xFF060D11.toInt())
        gui.fill(area.x,area.y,area.right,area.y+42,panel)
        gui.fill(area.x,area.y,area.right,area.y+2,teal)
        gui.drawString(font,text("preview"),area.x+10,area.y+9,textColor,true)

        val beast="BeastCoin  "+balances.str("BeastCoin","—")
        val hunter="HunterCoin  "+balances.str("HunterCoin","—")
        val wallet=hunter+"    "+beast
        val walletText=font.plainSubstrByWidth(wallet,(area.width*58/100).coerceAtLeast(80))
        gui.drawString(font,walletText,area.right-font.width(walletText)-10,area.y+9,gold,true)

        val tabY=area.y+22
        val tabWidth=((area.width-26)/2).coerceAtLeast(58)
        listOf("ARENA" to "arenas","TACTICIAN" to "tacticians").forEachIndexed{index,(kind,label)->
            hooks.control(UiRect(area.x+8+index*(tabWidth+6),tabY,tabWidth,18),text(label),true,ui.kind==kind){
                ui.kind=kind
                ui.selected=""
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
        val rowHeight=((listRect.height-10-rowGap*(offers.size-1).coerceAtLeast(0))/offers.size.coerceAtLeast(1)).coerceIn(24,36)
        offers.forEachIndexed{index,offer->
            val y=listRect.y+5+index*(rowHeight+rowGap)
            if(y+rowHeight>listRect.bottom-4)return@forEachIndexed
            val selected=offer==chosen
            val owned=offer.bool("owned")
            val equipped=offer.bool("equipped")
            val rect=UiRect(listRect.x+5,y,listRect.width-10,rowHeight)
            gui.fill(rect.x,rect.y,rect.right,rect.bottom,if(selected)0xFF20383A.toInt() else panel2)
            gui.fill(rect.x,rect.y,rect.x+3,rect.bottom,if(equipped)gold else if(selected)teal else 0xFF33464B.toInt())
            val status=when{equipped->" ✓";owned->" •";else->""}
            hooks.control(rect,name(offer)+status,true,selected){ui.selected=offer.str("id")}
        }

        val previewX=listRect.right+8
        val previewW=(area.right-previewX-8).coerceAtLeast(74)
        val infoH=52
        val stageH=(bodyH-infoH-6).coerceAtLeast(54)
        val stage=UiRect(previewX,bodyTop,previewW,stageH)
        gui.fill(stage.x,stage.y,stage.right,stage.bottom,0xFF081217.toInt())
        gui.fill(stage.x,stage.y,stage.right,stage.y+2,teal)
        gui.fill(stage.x,stage.y,stage.x+2,stage.bottom,0xFF2A454B.toInt())
        gui.fill(stage.right-2,stage.y,stage.right,stage.bottom,0xFF2A454B.toInt())

        val arenaId=if(ui.kind=="ARENA")chosen.str("id") else state.str("arena","kanto_stadium")
        val arena=MinecraftArenaRegistry.definition(arenaId)
        if(arena!=null){
            val sceneRect=stage.inset(5)
            val origin=SceneVec3(arena.boardOrigin.x.toDouble(),arena.boardOrigin.y.toDouble(),arena.boardOrigin.z.toDouble())
            val cell=SceneVec3(arena.cellSize.x.toDouble(),arena.cellSize.y.toDouble(),arena.cellSize.z.toDouble())
            val camera=SceneCameraFraming.board(
                arena.camera(ArenaCameraRole.ARENA_PREVIEW,SceneCameras.TFT),sceneRect,origin,
                arena.boardColumns,arena.boardRows,
                arena.benchAnchors.map{SceneVec3(it.x.toDouble(),it.y.toDouble(),it.z.toDouble())},
                widthFraction=.72,cellSize=cell
            )
            val tactician=if(ui.kind=="TACTICIAN")chosen else state.getAsJsonArray("offers")?.map{it.asJsonObject}
                ?.find{it.str("kind")=="TACTICIAN"&&it.str("id")==state.str("tactician")}
            val actor=tactician?.let{offer->
                val scale=runCatching{offer.get("scale").asDouble}.getOrDefault(1.0)
                val position=if(ui.kind=="TACTICIAN")
                    origin+SceneVec3(arena.boardColumns*cell.x/2,arena.boardRows*cell.y/2,0.0)
                else SceneVec3(arena.tacticianSpawn.x.toDouble(),arena.tacticianSpawn.y.toDouble(),arena.tacticianSpawn.z.toDouble())
                SceneTacticianNode(
                    "store:"+offer.str("id"),
                    SceneTransform(position,scale=SceneVec3(scale,scale,scale)),
                    entityId=offer.str("entity"),
                    pokemonSpecies=offer.str("species"),
                    pokemonAspects=offer.str("aspects").split(',').filter(String::isNotBlank).toSet()
                )
            }
            PokemonScene3D.render(gui,font,sceneRect,arena.boardColumns,arena.boardRows,emptyList(),ui.scene,
                camera=camera,arenaId=arenaId,arenaSeed="store:"+arenaId,tactician=actor)
        }

        gui.fill(stage.x+5,stage.y+5,stage.right-5,stage.y+21,0xC9071014.toInt())
        gui.drawString(font,font.plainSubstrByWidth(text("preview")+" · "+name(chosen),(stage.width-18).coerceAtLeast(30)),stage.x+10,stage.y+9,textColor,true)

        val info=UiRect(previewX,stage.bottom+6,previewW,(bodyBottom-stage.bottom-6).coerceAtLeast(44))
        gui.fill(info.x,info.y,info.right,info.bottom,panel)
        gui.fill(info.x,info.y,info.x+3,info.bottom,if(chosen.bool("equipped"))gold else teal)
        val owned=chosen.bool("owned")
        val equipped=chosen.bool("equipped")
        val stateLabel=when{equipped->text("equipped");owned->text("owned");else->chosen.str("price")+" "+chosen.str("currency")}
        gui.drawString(font,font.plainSubstrByWidth(name(chosen),(info.width-112).coerceAtLeast(30)),info.x+10,info.y+8,textColor,true)
        gui.drawString(font,font.plainSubstrByWidth(stateLabel,(info.width-112).coerceAtLeast(30)),info.x+10,info.y+23,if(owned)teal else gold,false)

        val actionW=(info.width*38/100).coerceIn(78,150).coerceAtMost(info.width-18)
        val actionX=info.right-actionW-8
        val buttonLabel=if(equipped)text("equipped") else if(owned)text("equip") else I18n.get("gui.svhub.store.buy",chosen.str("price"),chosen.str("currency"))
        hooks.control(UiRect(actionX,info.y+9,actionW,(info.height-18).coerceAtLeast(22)),buttonLabel,!equipped,equipped){
            val key=ui.kind+":"+ui.selected
            hooks.intent(if(owned)"equip" else "buy",JsonObject().apply{
                addProperty("kind",ui.kind)
                addProperty("id",ui.selected)
                addProperty("requestId",ui.requests.getOrPut(key){UUID.randomUUID().toString()})
            })
        }
    }

    private fun name(offer:JsonObject)=if(offer.str("kind")=="ARENA")I18n.get("gui.svhub.store.arena."+offer.str("id")) else offer.str("name")
    private fun JsonObject.str(key:String,fallback:String="")=runCatching{get(key)?.asString?:fallback}.getOrDefault(fallback)
    private fun JsonObject.bool(key:String)=runCatching{get(key)?.asBoolean==true}.getOrDefault(false)
}
