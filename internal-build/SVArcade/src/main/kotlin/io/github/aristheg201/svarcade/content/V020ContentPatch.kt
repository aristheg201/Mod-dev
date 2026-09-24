package io.github.aristheg201.svarcade.content

import com.google.gson.JsonObject

/** Installs the 0.2 player-facing native modules while keeping SkiesSkins authoritative for cosmetics. */
object V020ContentPatch {
    private const val MARKER="svarcade_v020"
    fun apply(content:HubContent):HubContent?{
        if(content.pages.any{it.id==MARKER})return null
        if(content.pages.none{it.id=="svarcade_v011"})return null
        val home=content.page("home")?:return null
        val theme=content.themes["sv_native"]?:HubTheme(id="sv_native",backgroundPreset="pixel_neon",panelStyle="clean",buttonStyle="clean",titleAnimation="glow_pulse",palette=ThemePalette(background=0xFF070B13.toInt(),panel=0xF0121B2B.toInt(),panelAlt=0xF01B2A42.toInt(),text=0xFFF4F7FF.toInt(),mutedText=0xFFA6B4CC.toInt(),accent=0xFF36D6FF.toInt(),accent2=0xFFFFC857.toInt(),danger=0xFFFF5E73.toInt()),motionStrength=.8f)
        val clean=home.components.filterNot{it.id.startsWith("v011_")||it.id.startsWith("v020_")}
        val launch=listOf(component("v020_title","animated_text","text" to "<bold>SV WORLD • 0.2</bold>","animation" to "glow_pulse","align" to "center","scale" to 1.45),component("v020_intro","notice","text" to "Gacha + Arcade chạy native trong SVArcade. Skin definitions, ownership, apply/remove và shop vẫn do SkiesSkins quản lý."),nativeButton("v020_native","MỞ SV PLATFORM","dashboard","Dashboard client + server."),nativeButton("v020_gacha","GACHA CS:GO","gacha","Roll server-authoritative; reward được grant vào inventory SkiesSkins."),nativeButton("v020_skins","SKIN SHOWCASE","skins","Showcase DBZ/Naruto/PokeLegends; ownership đọc trực tiếp từ SkiesSkins."),nativeButton("v020_arcade","SV ARCADE","arcade","Chess, Cờ Tướng, Cờ Cá Ngựa, UNO, PokéDraft, TFT và Tower Defense."),nativeButton("v020_companions","LINH THÚ","companions","Vanilla companions."),component("v020_sep","separator"))
        val patchedHome=home.copy(theme=theme.id,components=launch+clean)
        val pages=content.pages.filterNot{it.id in setOf("gacha","skin_showcase","companions","native_platform","native_arcade","native_wallet",MARKER)}.map{if(it.id==home.id)patchedHome else it}+listOf(
            modulePage("native_platform","native","SV Platform","Native Gacha + Arcade, SkiesSkins-backed cosmetics.","dashboard","One client/server UI. Skin state is never duplicated in SVArcade."),
            modulePage("gacha","gacha","Gacha CS:GO","Server rolls first, client animates after.","gacha","Pity lives in SVArcade; winning skin is granted through SkiesSkinsAPI."),
            modulePage("skin_showcase","skins/showcase","Skin Showcase","SkiesSkins is the authoritative backend.","skins","658 SVArcade skin definitions are read from SkiesSkins. Buy/apply/remove persist through SkiesSkins."),
            modulePage("native_arcade","arcade","SV Arcade","Seven server-authoritative game engines.","arcade","Pokémon Chess • Cờ Tướng • Cờ Cá Ngựa • UNO • PokéDraft • Pokémon TFT • Tower Defense."),
            modulePage("native_wallet","wallet","Arcade Wallet","Only SVArcade-native gameplay currencies live here.","wallet",ServerHelpText.get("V020ContentPatch_30")),
            modulePage("companions","companions","Linh Thú","Vanilla companion controller.","companions","Allay, Axolotl, Bee, Cat, Fox, Frog, Parrot, Rabbit, Wolf, Armadillo, Sniffer."),
            HubPage(MARKER,"system/v020","system",LocalizedText.of("SVArcade 0.2"),theme=theme.id,showInNavigation=false,components=listOf(component("v020_marker","text","text" to "SVArcade 0.2 native platform + SkiesSkins backend installed.")))
        )
        return content.copy(themes=content.themes+(theme.id to theme),pages=pages)
    }
    private fun modulePage(id:String,route:String,title:String,subtitle:String,module:String,body:String)=HubPage(id=id,route=route,category="native",title=LocalizedText.of(title),subtitle=LocalizedText.of(subtitle),theme="sv_native",tags=listOf("native",module,"svarcade"),components=listOf(component("${id}_hero","animated_text","text" to "<bold>$title</bold>","animation" to "shimmer","align" to "center","scale" to 1.3),component("${id}_body","notice","text" to body),nativeButton("${id}_open","MỞ $title",module,"Mở native client UI; server giữ state.")))
    private fun nativeButton(id:String,label:String,module:String,description:String)=HubComponent(id=id,type="button",props=json("label" to label,"description" to description),action=HubActionSpec("$id:open","native_open",module,cooldownMs=250L))
    private fun component(id:String,type:String,vararg props:Pair<String,Any>)=HubComponent(id,type,json(*props))
    private fun json(vararg pairs:Pair<String,Any>)=JsonObject().apply{pairs.forEach{(k,v)->when(v){is Number->addProperty(k,v);is Boolean->addProperty(k,v);else->addProperty(k,v.toString())}}}
}
