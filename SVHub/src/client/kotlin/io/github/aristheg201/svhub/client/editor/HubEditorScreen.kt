package io.github.aristheg201.svhub.client.editor

import com.google.gson.JsonObject
import io.github.aristheg201.svhub.client.ClientHubState
import io.github.aristheg201.svhub.client.gui.HubScreen
import io.github.aristheg201.svhub.content.*
import io.github.aristheg201.svhub.network.HubEditorChunkC2S
import io.github.aristheg201.svhub.util.Compression
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.concurrent.ThreadLocalRandom

class HubEditorScreen : Screen(Component.literal("SVHub Editor")) {
    private var draft = clone(ClientHubState.editorContent ?: ClientHubState.playerContent ?: DefaultContent.create())
    private var pageIndex = 0
    private var componentIndex = -1
    private var syncing = false
    private lateinit var titleBox: EditBox
    private lateinit var routeBox: EditBox
    private lateinit var categoryBox: EditBox
    private lateinit var primaryBox: EditBox

    override fun init() {
        addRenderableWidget(Button.builder(Component.literal("+ Page")){ addPage() }.bounds(8,8,58,20).build())
        addRenderableWidget(Button.builder(Component.literal("+ Component")){ Minecraft.getInstance().setScreen(ComponentPickerScreen(this)) }.bounds(70,8,92,20).build())
        addRenderableWidget(Button.builder(Component.literal("Assets")){ Minecraft.getInstance().setScreen(AssetStudioScreen(this,draft,pageIndex)) }.bounds(166,8,54,20).build())
        addRenderableWidget(Button.builder(Component.literal("Fakemon")){ Minecraft.getInstance().setScreen(FakemonEditorScreen(this,draft)) }.bounds(224,8,64,20).build())
        addRenderableWidget(Button.builder(Component.literal("Preview")){ Minecraft.getInstance().setScreen(HubScreen(draft.pages.getOrNull(pageIndex)?.route ?: "home",clone(draft),this)) }.bounds(292,8,62,20).build())
        addRenderableWidget(Button.builder(Component.literal("Publish")){ publish() }.bounds(358,8,66,20).build())
        titleBox=field(44,"Tiêu đề"){v->updatePage{it.copy(title=LocalizedText(it.title.values+("vi_vn" to v)))}}
        routeBox=field(70,"Route"){v->updatePage{it.copy(route=v)}}
        categoryBox=field(96,"Category"){v->updatePage{it.copy(category=v)}}
        primaryBox=field(150,"Nội dung component"){v->updateComponentProps{p->p.addProperty("text",v)}}
        syncFields()
    }

    private fun field(y:Int,hint:String,onChange:(String)->Unit):EditBox{val x=(width*.68).toInt();val b=EditBox(font,x,y,width-x-12,20,Component.literal(hint));b.hint=Component.literal(hint);b.setMaxLength(4096);b.setResponder{if(!syncing)onChange(it)};addRenderableWidget(b);return b}

    override fun render(gui:GuiGraphics,mouseX:Int,mouseY:Int,partialTick:Float){
        val theme=draft.themes[draft.defaultTheme]?:HubTheme("editor");io.github.aristheg201.svhub.client.render.PixelUi.background(gui,width,height,draft,theme,System.currentTimeMillis()/50)
        gui.fill(0,0,width,34,0xE80B0F18.toInt());val left=(width*.25).toInt();val mid=(width*.65).toInt();gui.drawString(font,"PAGES",12,42,theme.palette.accent,true);gui.drawString(font,"COMPONENTS",left+12,42,theme.palette.accent,true);gui.drawString(font,"INSPECTOR",mid+12,42,theme.palette.accent,true)
        var y=58;draft.pages.forEachIndexed{i,p->if(y<height-20){val active=i==pageIndex;if(active)gui.fill(8,y,left-6,y+22,0x88314A68.toInt());gui.drawString(font,p.title.resolve(draft.defaultLocale).take(22),14,y+7,if(active)theme.palette.accent else theme.palette.text,false);y+=24}}
        y=58;draft.pages.getOrNull(pageIndex)?.components?.forEachIndexed{i,c->if(y<height-20){val active=i==componentIndex;if(active)gui.fill(left+8,y,mid-6,y+24,0x88314A68.toInt());gui.drawString(font,"${i+1}. ${c.type}",left+14,y+8,if(active)theme.palette.accent else theme.palette.text,false);y+=26}}
        ClientHubState.lastEditorMessage?.let{gui.drawString(font,it,mid+12,height-24,theme.palette.accent,false)}
        super.render(gui,mouseX,mouseY,partialTick)
    }

    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean{
        if(button==0&&my>=58){val left=(width*.25).toInt();val mid=(width*.65).toInt();if(mx<left){val i=((my-58)/24).toInt();if(i in draft.pages.indices){pageIndex=i;componentIndex=-1;syncFields();return true}} else if(mx<mid){val i=((my-58)/26).toInt();if(i in (draft.pages.getOrNull(pageIndex)?.components?.indices?:IntRange.EMPTY)){componentIndex=i;syncFields();return true}}}
        return super.mouseClicked(mx,my,button)
    }

    internal fun draftForChild():HubContent=draft
    internal fun acceptAssetDraft(value:HubContent){draft=clone(value);syncFields()}
    internal fun acceptFakemonDraft(value:HubContent){draft=clone(value);syncFields()}
    internal fun addPickedComponent(type:String){val props=JsonObject();when(type){"heading","text","markdown","animated_text","notice"->props.addProperty("text","Nội dung mới");"button"->props.addProperty("label","Nút mới");"grid"->props.addProperty("provider","svhub:commands");"pokemon_model"->props.addProperty("species","cobblemon:pikachu")};val c=HubComponent("component_${System.nanoTime()}",type,props,action=if(type=="button")HubActionSpec("action_${System.nanoTime()}","open_page","home")else null);val pages=draft.pages.toMutableList();val p=pages.getOrNull(pageIndex)?:return;pages[pageIndex]=p.copy(components=p.components+c);draft=draft.copy(pages=pages);componentIndex=pages[pageIndex].components.lastIndex;syncFields()}

    private fun addPage(){val pages=draft.pages+HubPage("page_${draft.pages.size+1}","page/${draft.pages.size+1}","custom",LocalizedText.of("Trang mới"));draft=draft.copy(pages=pages);pageIndex=pages.lastIndex;componentIndex=-1;syncFields()}
    private fun updatePage(f:(HubPage)->HubPage){val pages=draft.pages.toMutableList();val p=pages.getOrNull(pageIndex)?:return;pages[pageIndex]=f(p);draft=draft.copy(pages=pages)}
    private fun updateComponentProps(f:(JsonObject)->Unit){val pages=draft.pages.toMutableList();val p=pages.getOrNull(pageIndex)?:return;val cs=p.components.toMutableList();val c=cs.getOrNull(componentIndex)?:return;val props=c.props.deepCopy();f(props);cs[componentIndex]=c.copy(props=props);pages[pageIndex]=p.copy(components=cs);draft=draft.copy(pages=pages)}
    private fun syncFields(){if(!::titleBox.isInitialized)return;syncing=true;val p=draft.pages.getOrNull(pageIndex);titleBox.value=p?.title?.resolve("vi_vn").orEmpty();routeBox.value=p?.route.orEmpty();categoryBox.value=p?.category.orEmpty();primaryBox.value=p?.components?.getOrNull(componentIndex)?.props?.get("text")?.let{runCatching{it.asString}.getOrDefault("")}.orEmpty();syncing=false}
    private fun publish(){val validation=HubValidator.validate(draft);if(!validation.ok){ClientHubState.lastEditorMessage=validation.errors.firstOrNull();return};val encoded=Compression.encodeUtf8(HubContentCodec.encode(draft.copy(revision=ClientHubState.serverRevision)));val chunks=Compression.chunks(encoded);val transfer=ThreadLocalRandom.current().nextLong();chunks.forEachIndexed{i,s->ClientPlayNetworking.send(HubEditorChunkC2S(transfer,ClientHubState.serverRevision,i,chunks.size,s))}}

    companion object { fun clone(content:HubContent):HubContent=HubContentCodec.decode(HubContentCodec.encode(content)) }
}
