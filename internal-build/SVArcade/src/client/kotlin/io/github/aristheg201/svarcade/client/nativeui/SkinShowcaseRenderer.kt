package io.github.aristheg201.svarcade.client.nativeui

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import io.github.aristheg201.svarcade.client.cobblemon.PokemonModelRenderer
import io.github.aristheg201.svarcade.client.cobblemon.PokemonView
import io.github.aristheg201.svarcade.ui.UiRect
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import kotlin.math.max
import kotlin.math.min

/** Premium skin preview stage with procedural art and the live Cobblemon resolver. */
object SkinShowcaseRenderer {
    data class Skin(
        val id:String,
        val name:String,
        val species:String,
        val aspect:String,
        val source:String,
        val rarity:String,
        val owned:Boolean
    )

    fun render(gui:GuiGraphics,font:Font,rect:UiRect,skin:Skin):Boolean {
        if(rect.width<120||rect.height<84)return false
        val accent=rarityColor(skin.rarity)
        val accentRgb=accent and 0x00FFFFFF
        gui.fill(rect.x,rect.y,rect.right,rect.bottom,0xFF050A0E.toInt())
        gui.fill(rect.x+2,rect.y+2,rect.right-2,rect.bottom-2,0xFF09151B.toInt())

        repeat(6){index->
            val inset=6+index*5
            if(rect.width>inset*2&&rect.height>inset*2+24){
                val alpha=(22-index*2).coerceAtLeast(8)
                gui.fill(rect.x+inset,rect.y+inset,rect.right-inset,rect.bottom-26-inset/2,(alpha shl 24) or accentRgb)
            }
        }
        val horizonY=rect.y+rect.height*58/100
        gui.fill(rect.x+10,horizonY,rect.right-10,horizonY+1,(0x66 shl 24) or accentRgb)
        gui.fill(rect.x+22,horizonY+5,rect.right-22,horizonY+6,(0x33 shl 24) or accentRgb)

        val frameW=min(rect.width-20,max(104,rect.height*5/4))
        val frameH=(rect.height-38).coerceAtLeast(48)
        val frameX=rect.x+(rect.width-frameW)/2
        val frameY=rect.y+7
        gui.fill(frameX,frameY,frameX+frameW,frameY+2,accent)
        gui.fill(frameX,frameY,frameX+2,frameY+frameH,accent)
        gui.fill(frameX+frameW-2,frameY,frameX+frameW,frameY+frameH,accent)
        gui.fill(frameX,frameY+frameH-2,frameX+frameW,frameY+frameH,accent)
        val corner=7
        gui.fill(frameX,frameY,frameX+corner,frameY+4,0xFFF2F6F4.toInt())
        gui.fill(frameX+frameW-corner,frameY,frameX+frameW,frameY+4,0xFFF2F6F4.toInt())
        gui.fill(frameX,frameY+frameH-4,frameX+corner,frameY+frameH,0xFFF2F6F4.toInt())
        gui.fill(frameX+frameW-corner,frameY+frameH-4,frameX+frameW,frameY+frameH,0xFFF2F6F4.toInt())

        val now=System.currentTimeMillis()
        val seed=skin.id.hashCode()
        repeat(12){i->
            val spanX=(rect.width-28).coerceAtLeast(1)
            val spanY=(frameH-22).coerceAtLeast(1)
            val t=(now/45L+i*83L+seed.toLong()*17L)
            val px=rect.x+14+Math.floorMod((t*(23+i*5)).toInt(),spanX)
            val py=rect.y+10+Math.floorMod((t*(13+i*7)).toInt(),spanY)
            val size=if(i%4==0)3 else 2
            val alpha=if(i%3==0)0xCC else 0x88
            gui.fill(px,py,px+size,py+size,(alpha shl 24) or accentRgb)
        }

        val pedestalW=min(132,frameW-22).coerceAtLeast(62)
        val pedestalX=rect.x+rect.width/2-pedestalW/2
        val pedestalY=frameY+frameH-15
        gui.fill(pedestalX,pedestalY,pedestalX+pedestalW,pedestalY+5,0xEE1A2A30.toInt())
        gui.fill(pedestalX+8,pedestalY+5,pedestalX+pedestalW-8,pedestalY+10,0xD00A1114.toInt())
        gui.fill(pedestalX,pedestalY,pedestalX+pedestalW,pedestalY+2,accent)

        val modelRect=UiRect(frameX+10,frameY+8,(frameW-20).coerceAtLeast(34),(frameH-24).coerceAtLeast(36))
        val view=pokemonView(skin)
        val rendered=view!=null&&PokemonModelRenderer.renderPreview(gui,view,"skin-showcase:"+skin.id,modelRect)
        if(!rendered) NativePixelArt.icon(gui,"skins",rect.x+rect.width/2-18,rect.y+rect.height/2-22,36,accent)

        gui.fill(rect.x+3,rect.bottom-25,rect.right-3,rect.bottom-3,0xE6070D11.toInt())
        gui.fill(rect.x+3,rect.bottom-25,rect.right-3,rect.bottom-23,accent)
        val title=font.plainSubstrByWidth(skin.name,(rect.width-30).coerceAtLeast(40))
        gui.drawCenteredString(font,title,rect.x+rect.width/2,rect.bottom-19,0xFFF2F6F4.toInt())
        val sub=(skin.rarity.ifBlank{"Skin"})+" • "+skin.source
        gui.drawCenteredString(font,font.plainSubstrByWidth(sub,(rect.width-30).coerceAtLeast(40)),rect.x+rect.width/2,rect.bottom-9,accent)
        return rendered
    }

    private fun pokemonView(skin:Skin):PokemonView? {
        val id=ResourceLocation.tryParse(skin.species)?:return null
        val species=PokemonSpecies.getByIdentifier(id)?:return null
        val aspects=skin.aspect.split(',').map(String::trim).filter(String::isNotBlank).toSet()
        return PokemonView(
            key=skin.species+"|"+aspects.sorted().joinToString(","),
            route="",
            speciesId=skin.species,
            aspects=aspects,
            displayName=species.translatedName.string.ifBlank{skin.name},
            dexNumber=species.nationalPokedexNumber,
            fakemon=species.resourceIdentifier.namespace!="cobblemon"
        )
    }

    fun rarityColor(raw:String)=when(raw.lowercase()){
        "legendary","mythic","mythical"->0xFFFFC857.toInt()
        "epic"->0xFFC47CFF.toInt()
        "rare"->0xFF5FA8FF.toInt()
        "uncommon"->0xFF67D391.toInt()
        else->0xFF91A6A1.toInt()
    }
}
