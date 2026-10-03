package vn.svarcade.tcg.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import vn.svarcade.tcg.client.card.CardRenderer;
import vn.svarcade.tcg.client.component.*;
import vn.svarcade.tcg.data.Catalog;
import vn.svarcade.tcg.physical.*;
import java.util.*;

/** One thin physical model. Its front is rendered by the existing Card Worlds presentation pipeline. */
public final class PhysicalCardRenderer {
    public static final int WIDTH=192,HEIGHT=288;
    private static final Identifier BACK=Identifier.of("svarcade_tcg","textures/item/card_back.png");
    private static final Identifier FALLBACK=Identifier.of("svarcade_tcg","textures/item/physical_card.png");
    private record Cached(Identifier texture,Catalog.Card definition,String language){}
    private static final Map<String,Cached> CACHE=new LinkedHashMap<>(64,.75f,true);
    private static final LinkedHashSet<String> REQUESTED=new LinkedHashSet<>();
    private static boolean baking;
    private static long popCount;
    public static boolean isBaking(){return baking;}
    public static long popCount(){return popCount;}
    public static void initialize(){
        BuiltinItemRendererRegistry.INSTANCE.register(CardItems.PHYSICAL_CARD,PhysicalCardRenderer::render);
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(client.world!=null&&!REQUESTED.isEmpty()){String id=REQUESTED.iterator().next();REQUESTED.remove(id);bake(client,id);}});
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener(){
            public Identifier getFabricId(){return Identifier.of("svarcade_tcg","physical_card_fronts");}
            public void reload(ResourceManager manager){clear();}
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{clear();PhysicalCards.presentation(Map.of());});
        ClientPlayNetworking.registerGlobalReceiver(CardRedeemPop.ID,(payload,context)->context.client().execute(()->{
            var stack=payload.stack();if(stack.isOf(CardItems.PHYSICAL_CARD)&&stack.get(CardItems.DATA)!=null){
                context.client().gameRenderer.showFloatingItem(stack.copy());popCount++;
                org.slf4j.LoggerFactory.getLogger("cardworlds-physical-client").info("CARDWORLDS_REDEEM_POP_EXECUTED card={} floatingItem=svarcade_tcg:physical_card",stack.get(CardItems.DATA).cardId());
            }
        }));
    }
    private static void clear(){var textures=MinecraftClient.getInstance().getTextureManager();CACHE.values().forEach(c->textures.destroyTexture(c.texture()));CACHE.clear();REQUESTED.clear();}
    private static Identifier front(String id){
        var cached=CACHE.get(id);var definition=PhysicalCards.definition(id);
        if(cached!=null&&(cached.definition()!=definition||!cached.language().equals(CardWorldsLanguage.language()))){MinecraftClient.getInstance().getTextureManager().destroyTexture(cached.texture());CACHE.remove(id);cached=null;}
        if(cached!=null)return cached.texture();if(definition!=null)REQUESTED.add(id);return FALLBACK;
    }
    private static void bake(MinecraftClient client,String id){
        Catalog.Card definition=PhysicalCards.definition(id);if(definition==null)return;
        // A texture supplied by a resource pack takes precedence over the generated front.
        Identifier override=Identifier.of("svarcade_tcg","textures/physical_cards/"+id.replace(':','/')+".png");
        if(client.getResourceManager().getResource(override).isPresent()){CACHE.put(id,new Cached(override,definition,CardWorldsLanguage.language()));return;}
        var projection=new Matrix4f(RenderSystem.getProjectionMatrix());var sorter=RenderSystem.getVertexSorting();
        var modelView=RenderSystem.getModelViewStack();modelView.pushMatrix();modelView.identity();RenderSystem.applyModelViewMatrix();
        SimpleFramebuffer framebuffer=new SimpleFramebuffer(WIDTH,HEIGHT,true,MinecraftClient.IS_SYSTEM_MAC);
        try {
            baking=true;framebuffer.setClearColor(0,0,0,1);framebuffer.clear(MinecraftClient.IS_SYSTEM_MAC);framebuffer.beginWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,WIDTH,HEIGHT,0,-1000,1000),VertexSorter.BY_Z);
            DrawContext context=new DrawContext(client,client.getBufferBuilders().getEntityVertexConsumers());
            Ui ui=new Ui(context,-100,-100,new ArrayList<>());CardRenderer.draw(ui,definition,new Rect(0,0,WIDTH,HEIGHT),0,false,true,"physical:"+id);
            ui.fill(new Rect(8,HEIGHT-19,WIDTH-16,13),0xFF08131E);ui.fit(definition.rarity(),new Rect(12,HEIGHT-18,WIDTH-24,12),10,Ui.GOLD);context.draw();
            var pixels=ScreenshotRecorder.takeScreenshot(framebuffer);
            Identifier texture=client.getTextureManager().registerDynamicTexture("cardworlds_physical",new NativeImageBackedTexture(pixels));
            if(CACHE.size()>=64){String oldest=CACHE.keySet().iterator().next();client.getTextureManager().destroyTexture(CACHE.remove(oldest).texture());}
            CACHE.put(id,new Cached(texture,definition,CardWorldsLanguage.language()));
        }catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger("cardworlds-physical-client").warn("Physical card front fallback for {}",id,e);}
        finally {baking=false;RenderSystem.disableScissor();framebuffer.delete();client.getFramebuffer().beginWrite(true);RenderSystem.setProjectionMatrix(projection,sorter);modelView.popMatrix();RenderSystem.applyModelViewMatrix();RenderSystem.setShaderColor(1,1,1,1);}
    }
    private static void render(ItemStack stack,ModelTransformationMode mode,MatrixStack matrices,VertexConsumerProvider consumers,int light,int overlay){
        var data=stack.get(CardItems.DATA);Identifier texture=data==null?FALLBACK:front(data.cardId());
        var entry=matrices.peek();VertexConsumer face=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        quad(face,entry,.19f,.05f,.81f,.95f,.515f,light,overlay,0,0,1);
        VertexConsumer back=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(BACK));
        quad(back,entry,.81f,.05f,.19f,.95f,.485f,light,overlay,0,0,-1);
        // Edge faces make the physical card visibly thin in hand and as a dropped item.
        VertexConsumer edge=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(BACK));
        side(edge,entry,.19f,.05f,.19f,.95f,light,overlay,-1,0);side(edge,entry,.81f,.95f,.81f,.05f,light,overlay,1,0);
        side(edge,entry,.19f,.95f,.81f,.95f,light,overlay,0,1);side(edge,entry,.81f,.05f,.19f,.05f,light,overlay,0,-1);
        if(data!=null&&!data.finish().equals("Normal")){
            VertexConsumer shine=consumers.getBuffer(RenderLayer.getEntityGlint());
            quad(shine,entry,.19f,.05f,.81f,.95f,.516f,light,overlay,0,0,1);
        }
    }
    private static void quad(VertexConsumer v,MatrixStack.Entry e,float x1,float y1,float x2,float y2,float z,int light,int overlay,float nx,float ny,float nz){
        vertex(v,e,x1,y1,z,0,1,light,overlay,nx,ny,nz);vertex(v,e,x2,y1,z,1,1,light,overlay,nx,ny,nz);vertex(v,e,x2,y2,z,1,0,light,overlay,nx,ny,nz);vertex(v,e,x1,y2,z,0,0,light,overlay,nx,ny,nz);
    }
    private static void side(VertexConsumer v,MatrixStack.Entry e,float x1,float y1,float x2,float y2,int light,int overlay,float nx,float ny){
        vertex(v,e,x1,y1,.485f,0,0,light,overlay,nx,ny,0);vertex(v,e,x2,y2,.485f,1,0,light,overlay,nx,ny,0);vertex(v,e,x2,y2,.515f,1,1,light,overlay,nx,ny,0);vertex(v,e,x1,y1,.515f,0,1,light,overlay,nx,ny,0);
    }
    private static void vertex(VertexConsumer v,MatrixStack.Entry e,float x,float y,float z,float u,float w,int light,int overlay,float nx,float ny,float nz){v.vertex(e.getPositionMatrix(),x,y,z).color(255,255,255,255).texture(u,w).overlay(overlay).light(light).normal(e,nx,ny,nz);}
    private PhysicalCardRenderer(){}
}
