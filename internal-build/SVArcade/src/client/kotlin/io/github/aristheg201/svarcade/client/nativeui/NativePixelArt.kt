package io.github.aristheg201.svarcade.client.nativeui

import net.minecraft.client.gui.GuiGraphics

object NativePixelArt {
    fun icon(gui: GuiGraphics, id: String, x: Int, y: Int, size: Int, color: Int) {
        val s = (size / 8).coerceAtLeast(1)
        fun px(px: Int, py: Int, w: Int = 1, h: Int = 1, c: Int = color) = gui.fill(x + px * s, y + py * s, x + (px + w) * s, y + (py + h) * s, c)
        when (id) {
            "dashboard" -> { px(1,1,2,2); px(5,1,2,2); px(1,5,2,2); px(5,5,2,2) }
            "gacha" -> { px(1,2,6,4); px(2,1,4,1); px(2,6,4,1); px(3,3,2,2,0xFF0C1518.toInt()) }
            "skins" -> { px(2,1,4,1); px(1,2,2,5); px(5,2,2,5); px(3,3,2,4) }
            "arcade" -> { px(2,1,4,1); px(1,2,6,4); px(2,6,4,1); px(2,3); px(4,3); px(5,4) }
            "companions" -> { px(2,1); px(5,1); px(1,2,2,2); px(5,2,2,2); px(2,4,4,3) }
            "wallet" -> { px(1,2,6,5); px(2,1,4,1); px(4,3,3,2,0xFF0C1518.toInt()) }
            "chess" -> { px(3,1,2,1); px(2,2,4,1); px(3,3,2,2); px(2,5,4,1); px(1,6,6,1) }
            "xiangqi" -> { px(1,1,6,6); px(2,2,4,4,0xFF0C1518.toInt()); px(3,2,2,4) }
            "ludo" -> { px(1,1,2,2); px(5,1,2,2); px(1,5,2,2); px(5,5,2,2); px(3,3,2,2) }
            "uno", "cards", "pokecards" -> { px(2,1,4,6); px(1,2,1,5); px(6,1,1,5); px(3,3,2,2,0xFF0C1518.toInt()) }
            "tft" -> { px(1,1,6,6); px(2,2,1,1,0xFF0C1518.toInt()); px(4,2,2,2,0xFF0C1518.toInt()); px(2,5,3,1,0xFF0C1518.toInt()) }
            "tower_defense" -> { px(2,1,4,1); px(1,2,6,2); px(2,4,4,3); px(3,3,2,1,0xFF0C1518.toInt()) }
            else -> { px(1,1,6,6); px(2,2,4,4,0xFF0C1518.toInt()) }
        }
    }

    fun companion(gui: GuiGraphics, id: String, centerX: Int, groundY: Int, scale: Int, primary: Int, secondary: Int, facingRight: Boolean = true) {
        val s = scale.coerceAtLeast(1)
        val mirror = if (facingRight) 1 else -1
        fun rect(rx:Int, ry:Int, rw:Int, rh:Int, color:Int=primary) {
            val x1=centerX+rx*s*mirror; val x2=centerX+(rx+rw)*s*mirror
            gui.fill(minOf(x1,x2),groundY+ry*s,maxOf(x1,x2),groundY+(ry+rh)*s,color)
        }
        gui.fill(centerX-7*s,groundY+1,centerX+7*s,groundY+2*s,0x44000000)
        when(id){
            "allay","bee","parrot"->{rect(-3,-8,6,5);rect(-5,-7,2,3,secondary);rect(3,-7,2,3,secondary);rect(-2,-10,4,2,secondary);rect(-1,-7,1,1,0xFF101719.toInt());rect(1,-7,1,1,0xFF101719.toInt())}
            "axolotl","frog","armadillo"->{rect(-5,-5,10,4);rect(-4,-8,7,3,secondary);rect(-6,-7,2,2);rect(4,-4,3,2,secondary);rect(-2,-7,1,1,0xFF101719.toInt());rect(1,-7,1,1,0xFF101719.toInt())}
            "sniffer"->{rect(-6,-6,12,5);rect(-7,-5,3,3,secondary);rect(-4,-9,8,3,secondary);rect(-5,-2,2,2);rect(3,-2,2,2);rect(-5,-8,1,1,0xFF101719.toInt())}
            else->{rect(-5,-6,9,5);rect(-4,-10,6,4,secondary);rect(-4,-12,2,3);rect(0,-12,2,3);rect(-4,-2,2,3);rect(2,-2,2,3);rect(4,-6,3,2,secondary);rect(-3,-9,1,1,0xFF101719.toInt())}
        }
    }

    fun healthBar(gui:GuiGraphics,x:Int,y:Int,width:Int,value:Int,maximum:Int,color:Int){
        gui.fill(x,y,x+width,y+6,0xFF162126.toInt()); val fill=if(maximum<=0)0 else width*value.coerceIn(0,maximum)/maximum
        if(fill>1)gui.fill(x+1,y+1,x+fill-1,y+5,color)
    }

    fun gamePiece(gui:GuiGraphics,token:String,x:Int,y:Int,size:Int){
        if(token.isBlank())return
        val dark=token.firstOrNull()?.isLowerCase()==true||token.startsWith("2")||token.startsWith("4")
        val base=if(dark)0xFF27343A.toInt() else 0xFFE6E0C7.toInt(); val accent=if(dark)0xFFE36C5C.toInt() else 0xFF267B71.toInt(); val pad=(size/5).coerceAtLeast(2)
        gui.fill(x+pad,y+pad,x+size-pad,y+size-pad,base);gui.fill(x+pad+1,y+pad+1,x+size-pad-1,y+pad+3,accent)
        val bars=(token.first().uppercaseChar().code%3)+1;repeat(bars){i->val bx=x+size/2-bars*2+i*4;gui.fill(bx,y+size/2-2,bx+2,y+size/2+3,accent)}
    }
}
