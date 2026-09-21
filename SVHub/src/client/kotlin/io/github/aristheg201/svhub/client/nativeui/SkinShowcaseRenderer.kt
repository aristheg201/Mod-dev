package io.github.aristheg201.svhub.client.nativeui

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import io.github.aristheg201.svhub.client.cobblemon.PokemonModelRenderer
import io.github.aristheg201.svhub.client.cobblemon.PokemonView
import io.github.aristheg201.svhub.ui.UiRect
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import kotlin.math.max
import kotlin.math.min

/** Premium skin preview stage backed by real GUI art and the live Cobblemon resolver. */
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

    private val BACKGROUND=ResourceLocation.fromNamespaceAndPath("svhub","textures/gui/generated/home_bg.png")
    private val FRAME=ResourceLocation.fromNamespaceAndPath("svhub","textures/gui/generated/pixel_frame.png")
    private val SPARKLES=ResourceLocation.fromNamespaceAndPath("svhub","textures/gui/generated/sparkle_strip.png")

    fun render(gui:GuiGraphics,font:Font,rect:UiRect,skin:Skin):Boolean {
        if(rect.width<120||rect.height<84)return false
        gui.fill(rect.x,rect.y,rect.right,rect.bottom,0xFF070C10.toInt())
        gui.blit(BACKGROUND,rect.x,rect.y,rect.width,rect.height,0,0,512,288,512,288)

        val accent=rarityColor(skin.rarity)
        val frameW=min(rect.width-18,max(96,rect.height*5/4))
        val frameH=rect.height-18
        val frameX=rect.x+(rect.width-frameW)/2
        val frameY=rect.y+7
        gui.blit(FRAME,frameX,frameY,frameW,frameH,0,0,64,64,64,64)

        // Animated authored sparkle texture plus a restrained spotlight/pedestal.
        val sparkleOffset=((System.currentTimeMillis()/45L)%960L).toInt()
        val sparkleW=min(rect.width-20,320)
        gui.blit(SPARKLES,rect.x+(rect.width-sparkleW)/2,rect.y+5,sparkleW,20,sparkleOffset,0,sparkleW,64,1024,64)
        repeat(8){i->
            val t=(System.currentTimeMillis()/55L+i*97L)%1000L
            val px=rect.x+8+((t*(31+i*7))%(rect.width-16).coerceAtLeast(1)).toInt()
            val py=rect.y+10+((t*(17+i*11))%(rect.height-34).coerceAtLeast(1)).toInt()
            val a=(90+(i%3)*45).coerceAtMost(220)
            gui.fill(px,py,px+2,py+2,(a shl 24) or (accent and 0x00FFFFFF))
        }

        val pedestalW=min(118,frameW-18).coerceAtLeast(54)
        val pedestalY=rect.bottom-28
        gui.fill(rect.x+rect.width/2-pedestalW/2,pedestalY,rect.x+rect.width/2+pedestalW/2,pedestalY+7,0xD018272B.toInt())
        gui.fill(rect.x+rect.width/2-pedestalW/2+8,pedestalY+7,rect.x+rect.width/2+pedestalW/2-8,pedestalY+11,0xB00A1114.toInt())
        gui.fill(rect.x+rect.width/2-pedestalW/2,pedestalY,rect.x+rect.width/2+pedestalW/2,pedestalY+2,accent)

        val modelRect=UiRect(frameX+12,frameY+12,(frameW-24).coerceAtLeast(32),(frameH-43).coerceAtLeast(38))
        val view=pokemonView(skin)
        val rendered=view!=null&&PokemonModelRenderer.renderPreview(gui,view,"skin-showcase:"+skin.id,modelRect)
        if(!rendered) {
            NativePixelArt.icon(gui,"skins",rect.x+rect.width/2-18,rect.y+rect.height/2-22,36,accent)
        }

        val title=font.plainSubstrByWidth(skin.name,(rect.width-30).coerceAtLeast(40))
        gui.drawCenteredString(font,title,rect.x+rect.width/2,rect.bottom-15,0xFFF2F6F4.toInt())
        val sub=(skin.rarity.ifBlank{"Skin"})+" • "+skin.source
        gui.drawCenteredString(font,font.plainSubstrByWidth(sub,(rect.width-30).coerceAtLeast(40)),rect.x+rect.width/2,rect.bottom-6,accent)
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
