package io.github.aristheg201.svhub.client.gui

import io.github.aristheg201.svhub.client.*
import io.github.aristheg201.svhub.client.cobblemon.CobblemonWikiProvider
import io.github.aristheg201.svhub.client.cobblemon.PokemonModelRenderer
import io.github.aristheg201.svhub.client.render.AnimatedTextRenderer
import io.github.aristheg201.svhub.client.render.PixelUi
import io.github.aristheg201.svhub.content.*
import io.github.aristheg201.svhub.network.HubActionC2S
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class HubScreen(private var route:String="home",private val overrideContent:HubContent?=null,private val returnTo:Screen?=null):Screen(Component.literal("SVHub")){
    private lateinit var search:EditBox;private var tick=0L
    private val content:HubContent get()=overrideContent?:ClientHubState.playerContent?:DefaultContent.create()
    override fun init(){search=EditBox(font,width/2-130,10,260,20,Component.literal("Tìm kiếm"));search.hint=Component.literal("Tìm Pokémon, Fakemon, lệnh, hướng dẫn...");search.setResponder{};addRenderableWidget(search)}
    override fun tick(){tick++}
    override fun onClose(){Minecraft.getInstance().setScreen(returnTo)}
    override fun render(gui:GuiGraphics,mx:Int,my:Int,partial:Float){val page=content.page(route);val theme=content.themeFor(page)?:HubTheme("pixel");PixelUi.background(gui,width,height,content,theme,tick);gui.fill(0,0,width,38,0xD80B0F18.toInt());gui.drawString(font,"SV HUB",12,15,theme.palette.accent,true);if(search.value.isNotBlank())renderSearch(gui,theme)else if(route.startsWith("pokemon/")||route.startsWith("fakemon/"))renderPokemon(gui,theme)else renderPage(gui,page,theme,mx,my);super.render(gui,mx,my,partial)}
    private fun renderPage(gui:GuiGraphics,page:HubPage?,theme:HubTheme,mx:Int,my:Int){val p=page?:content.page("home")?:return;gui.drawCenteredString(font,p.title.resolve(content.defaultLocale),width/2,48,theme.palette.accent);var y=68;p.components.forEach{c->when(c.type){"heading"->{val t=c.props.get("text")?.asString.orEmpty();AnimatedTextRenderer.render(gui,t,width/2,y,theme.palette.text,"pixel_pop",1.15f,true,tick);y+=28};"animated_text"->{AnimatedTextRenderer.render(gui,c.props.get("text")?.asString.orEmpty(),width/2,y,theme.palette.accent,c.props.get("animation")?.asString?:"glow_pulse",1.2f,true,tick);y+=30};"text","markdown","notice"->{val t=c.props.get("text")?.asString.orEmpty();font.split(Component.literal(t),width-80).take(6).forEach{line->gui.drawString(font,line,40,y,theme.palette.text,false);y+=12};y+=8};"grid"->{renderGrid(gui,c,theme,y);y+=140};"button"->{PixelUi.button(gui,width/2-90,y,180,28,mx in width/2-90..width/2+90&&my in y..y+28,theme);gui.drawCenteredString(font,c.props.get("label")?.asString?:"Mở",width/2,y+10,theme.palette.text);y+=34};"pokemon_model"->{val species=c.props.get("species")?.asString?:"cobblemon:pikachu";CobblemonWikiProvider.resolveRoute("pokemon/${java.net.URLEncoder.encode(species,java.nio.charset.StandardCharsets.UTF_8)}",content)?.let{PokemonModelRenderer.render(gui,it,width/2,y+60,96)};y+=130}}}}
    private fun renderGrid(gui:GuiGraphics,c:HubComponent,theme:HubTheme,startY:Int){val provider=c.props.get("provider")?.asString.orEmpty();val entries=when(provider){"cobblemon:pokemon"->CobblemonWikiProvider.pokemon(content).take(18).map{it.displayName to it.route};"cobblemon:fakemon"->CobblemonWikiProvider.fakemon(content).take(18).map{it.displayName to it.route};"svhub:commands"->CommandViewProvider.all().take(18).map{"/${it.name}" to it.route};else->emptyList()};var x=28;var y=startY;entries.forEach{(label,_)->PixelUi.panel(gui,x,y,120,30,theme.palette.panel,theme.palette.accent2);gui.drawCenteredString(font,font.plainSubstrByWidth(label,110),x+60,y+11,theme.palette.text);x+=128;if(x+120>width){x=28;y+=36}}}
    private fun renderSearch(gui:GuiGraphics,theme:HubTheme){var y=48;ClientHubState.search(search.value,12).forEach{hit->PixelUi.panel(gui,width/2-190,y,380,28,theme.palette.panel,theme.palette.accent2);gui.drawString(font,hit.title,width/2-178,y+6,theme.palette.text,true);gui.drawString(font,font.plainSubstrByWidth(hit.subtitle,180),width/2,y+6,theme.palette.mutedText,false);y+=32}}
    private fun renderPokemon(gui:GuiGraphics,theme:HubTheme){val view=CobblemonWikiProvider.resolveRoute(route,content)?:return;PokemonModelRenderer.render(gui,view,width/3,height/2,120,(tick%360).toFloat(),1f);gui.drawString(font,view.displayName,width/2,70,theme.palette.accent,true);gui.drawString(font,view.speciesId,width/2,88,theme.palette.mutedText,false);view.wikiPage?.let{gui.drawString(font,"Wiki: $it",width/2,106,theme.palette.text,false)}}
    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean{if(button==0&&search.value.isBlank()){val page=content.page(route);var y=68;page?.components?.forEach{c->val h=when(c.type){"heading"->28;"animated_text"->30;"text","markdown","notice"->56;"grid"->140;"button"->34;"pokemon_model"->130;else->0};if(c.type=="button"&&mx in (width/2-90).toDouble()..(width/2+90).toDouble()&&my in y.toDouble()..(y+28).toDouble()){c.action?.let{a->if(a.type=="open_page"){route=a.value;search.value=""}else ClientPlayNetworking.send(HubActionC2S(a.id))};return true};y+=h}}
        if(button==1&&returnTo!=null){Minecraft.getInstance().setScreen(returnTo);return true};return super.mouseClicked(mx,my,button)}
    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean{if((mods and 2)!=0&&key==70){setFocused(search);search.isFocused=true;return true};return super.keyPressed(key,scan,mods)}
}
