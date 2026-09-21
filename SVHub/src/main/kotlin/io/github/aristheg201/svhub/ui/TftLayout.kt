package io.github.aristheg201.svhub.ui

/** Pure layout contract for the dedicated TFT presentation. Rectangles are mutually exclusive safe areas. */
data class TftResolvedLayout(
    val hud: UiRect,
    val traits: UiRect?,
    val players: UiRect?,
    val itemRail: UiRect?,
    val board: UiRect,
    val footer: UiRect
) {
    fun allRects(): List<UiRect> = buildList {
        add(hud)
        traits?.let(::add)
        players?.let(::add)
        itemRail?.let(::add)
        add(board)
        add(footer)
    }
}

object TftLayoutResolver {
    fun resolve(area: UiRect, density: UiDensity): TftResolvedLayout {
        val gap=if(density==UiDensity.COMPACT)2 else 6
        val hudH=when(density){UiDensity.COMPACT->22;UiDensity.REGULAR->30;UiDensity.WIDE->34}.coerceAtMost(area.height/3)
        val footerH=when(density){
            UiDensity.COMPACT->when{area.height<135->36;area.height<190->52;else->66}
            UiDensity.REGULAR->(area.height*.22f).toInt().coerceIn(64,92)
            UiDensity.WIDE->(area.height*.22f).toInt().coerceIn(78,108)
        }.coerceAtMost((area.height-hudH-gap-36).coerceAtLeast(24))
        val leftW=when(density){UiDensity.WIDE->96;UiDensity.REGULAR->78;UiDensity.COMPACT->0}.coerceAtMost((area.width/4).coerceAtLeast(0))
        val rightW=when(density){
            UiDensity.WIDE->82
            UiDensity.REGULAR->64
            UiDensity.COMPACT->if(area.width>=240)48 else 40
        }.coerceAtMost((area.width/4).coerceAtLeast(1))
        val hud=UiRect(area.x,area.y,area.width,hudH)
        val footer=UiRect(area.x+gap,(area.bottom-footerH).coerceAtLeast(hud.bottom+gap+24),(area.width-gap*2).coerceAtLeast(40),footerH)
        val sideTop=hud.bottom+gap
        val sideBottom=footer.y-gap
        val sideH=(sideBottom-sideTop).coerceAtLeast(0)
        val traits=if(leftW>0&&sideH>=24)UiRect(area.x,sideTop,leftW,sideH) else null
        val players=if(density==UiDensity.WIDE&&sideH>=94){
            val reserveForItems=48+gap
            val playerH=(sideH*58/100).coerceAtLeast(42).coerceAtMost((sideH-reserveForItems).coerceAtLeast(42))
            UiRect(area.right-rightW,sideTop,rightW,playerH)
        }else null
        val itemTop=players?.let{it.bottom+gap}?:sideTop
        val itemH=sideBottom-itemTop
        val itemRail=if(rightW>0&&itemH>=18)UiRect(area.right-rightW,itemTop,rightW,itemH) else null
        val leftReserve=if(traits!=null)leftW+gap else 0
        val rightReserve=if(players!=null||itemRail!=null)rightW+gap else 0
        val boardX=area.x+leftReserve
        val boardY=sideTop
        val boardRight=(area.right-rightReserve).coerceAtLeast(boardX+40)
        val boardBottom=sideBottom.coerceAtLeast(boardY+24)
        val board=UiRect(boardX,boardY,(boardRight-boardX).coerceAtLeast(40),(boardBottom-boardY).coerceAtLeast(24))
        return TftResolvedLayout(hud,traits,players,itemRail,board,footer)
    }
}
