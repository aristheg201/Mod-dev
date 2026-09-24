package io.github.aristheg201.svarcade.client.nativeui

import io.github.aristheg201.svarcade.ui.UiRect
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import kotlin.math.abs
import kotlin.math.roundToInt

/** Resolution-independent framing and typography, separate from artwork and live content. */
object ArcadeUiPainter {
    val body = ResourceLocation.fromNamespaceAndPath("svarcade","notosans")
    val display = ResourceLocation.fromNamespaceAndPath("svarcade","cinzel")
    fun text(value:String, heading:Boolean=false)=Component.literal(value).withStyle { it.withFont(if(heading)display else body) }
    fun line(gui:GuiGraphics,x0:Int,y0:Int,x1:Int,y1:Int,color:Int) {
        val steps=maxOf(abs(x1-x0),abs(y1-y0),1)
        for(i in 0..steps) { val x=x0+(x1-x0)*i/steps;val y=y0+(y1-y0)*i/steps;gui.fill(x,y,x+1,y+1,color) }
    }
    fun frame(gui:GuiGraphics,r:UiRect,color:Int=0xFF315562.toInt(),fill:Boolean=true) {
        if(fill) gui.fillGradient(r.x+1,r.y+1,r.right-1,r.bottom-1,0xF00D2939.toInt(),0xF0051824.toInt())
        val c=5
        line(gui,r.x+c,r.y,r.right-c,r.y,color);line(gui,r.x+c,r.bottom,r.right-c,r.bottom,color)
        line(gui,r.x,r.y+c,r.x,r.bottom-c,color);line(gui,r.right,r.y+c,r.right,r.bottom-c,color)
        line(gui,r.x,r.y+c,r.x+c,r.y,color);line(gui,r.right-c,r.y,r.right,r.y+c,color)
        line(gui,r.x,r.bottom-c,r.x+c,r.bottom,color);line(gui,r.right-c,r.bottom,r.right,r.bottom-c,color)
        val edge=0xFF8D7849.toInt()
        listOf(r.x to 1,r.right to -1).forEach { (x,d) ->
            line(gui,x+d*2,r.y+13,x+d*2,r.y+7,edge);line(gui,x+d*2,r.y+7,x+d*7,r.y+2,edge);line(gui,x+d*7,r.y+2,x+d*16,r.y+2,edge)
            line(gui,x+d*2,r.bottom-13,x+d*2,r.bottom-7,edge);line(gui,x+d*2,r.bottom-7,x+d*7,r.bottom-2,edge)
        }
    }
    fun icon(gui:GuiGraphics,id:String,x:Int,y:Int,size:Int,color:Int) {
        fun l(a:Int,b:Int,c:Int,d:Int)=line(gui,x+a*size/24,y+b*size/24,x+c*size/24,y+d*size/24,color)
        when(id) {
            "ranked" -> { l(2,6,6,19);l(6,19,18,19);l(18,19,22,6);l(22,6,16,12);l(16,12,12,3);l(12,3,8,12);l(8,12,2,6);l(6,22,18,22) }
            "ai" -> { l(5,7,19,7);l(19,7,21,18);l(21,18,12,22);l(12,22,3,18);l(3,18,5,7);l(12,7,12,1);l(7,12,9,14);l(15,14,17,12) }
            "normal" -> { l(3,2,20,21);l(2,3,19,22);l(3,21,20,2);l(4,22,21,3);l(2,17,7,22);l(17,22,22,17) }
            "clock" -> { l(8,2,16,2);l(16,2,22,8);l(22,8,22,16);l(22,16,16,22);l(16,22,8,22);l(8,22,2,16);l(2,16,2,8);l(2,8,8,2);l(12,6,12,12);l(12,12,17,15) }
            else -> { l(12,1,22,7);l(22,7,19,20);l(19,20,12,24);l(12,24,5,20);l(5,20,2,7);l(2,7,12,1);l(12,5,17,10);l(17,10,9,16);l(9,16,15,19) }
        }
    }
}
