package vn.svarcade.tcg.client.render;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.gui.ProfileTransformType;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.entity.PoseType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import vn.svarcade.tcg.client.component.Rect;
import vn.svarcade.tcg.client.component.Ui;
import vn.svarcade.tcg.duel.Duel;
import java.util.*;

/** One closed, paper-thin mesh per card; fronts are baked once through Cobblemon's profile provider. */
final class DuelCardMeshes {
    static final Identifier BACK = Identifier.of("svarcade_tcg", "textures/duel/card_back.png");
    private static final Identifier DEFENSE = Identifier.of("svarcade_tcg", "textures/duel/defense.png");
    private static final Identifier BOARD = Identifier.of("svarcade_tcg", "textures/duel/board.png");
    private final Map<String,Identifier> fronts = new LinkedHashMap<>();

    Identifier front(Duel.VisibleCard card) {
        String key = vn.svarcade.tcg.client.component.CardWorldsLanguage.language()+"|"+card.name()+"|"+card.species()+"|"+card.aspects()+"|"+card.type()+"|"+card.power()+"|"+card.effect();
        Identifier cached = fronts.get(key);
        if (cached != null) return cached;
        MinecraftClient client = MinecraftClient.getInstance();
        var buffers = client.getBufferBuilders().getEntityVertexConsumers();
        buffers.draw();
        var target = new SimpleFramebuffer(350,500,true,MinecraftClient.IS_SYSTEM_MAC);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorter = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            target.setClearColor(0,0,0,0);target.clear(MinecraftClient.IS_SYSTEM_MAC);target.beginWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,350,500,0,1000,21000),VertexSorter.BY_Z);
            modelView.identity().translate(0,0,-11000);RenderSystem.applyModelViewMatrix();
            DrawContext draw = new DrawContext(client,buffers);
            Ui ui = new Ui(draw,-1,-1,new ArrayList<>());
            ui.fill(new Rect(0,0,350,500),0xFF131824);
            ui.fill(new Rect(6,6,338,488),0xFFE3C88D);
            int identity = card.category().equals("reaction") ? 0xFF603A75 : 0xFF226F62;
            ui.fill(new Rect(10,10,330,480),identity);
            ui.fill(new Rect(17,17,316,466),0xFFF0E4CD);
            ui.fill(new Rect(24,25,302,42),0xFF24364B);
            vn.svarcade.tcg.data.Catalog.Card definition=null;
            if(client.currentScreen instanceof vn.svarcade.tcg.client.CardWorldsScreen screen)definition=screen.state.definitions().values().stream().filter(d->d.name().equals(card.name())).findFirst().orElse(null);
            if(definition!=null){var d=definition;definition=new vn.svarcade.tcg.data.Catalog.Card(d.id(),d.name(),d.category(),d.species(),d.aspects(),d.type(),d.family(),d.evolvesFrom(),d.extra(),d.level(),d.power(),d.text(),d.set(),d.rarity(),d.sources(),card.effect(),d.triggers(),d.modifiers());}
            String title=definition==null?card.name():vn.svarcade.tcg.client.component.CardWorldsLanguage.name(definition);
            ui.fit(title.toUpperCase(Locale.ROOT),new Rect(34,39,280,28),20,0xFFFFE3A4);
            ui.fill(new Rect(24,76,302,299),0xFFBA9B64);
            ui.c.fillGradient(29,81,321,370,0xFF456579,0xFF0B1829);
            // A quiet radial stage behind the actual resolved Pokémon artwork.
            for(int i=0;i<6;i++) ui.frame(new Rect(44+i*8,98+i*10,262-i*16,254-i*20),0x1749C9F5);
            if(card.species()==null||card.species().isBlank()) {
                // Authored arcane seal for Spell/Trap art, with a distinct palette by card category.
                for(int r=96;r>=22;r-=18) {
                    for(int i=0;i<72;i++) {
                        double angle=i*Math.PI/36;
                        ui.fill(new Rect(175+(int)(Math.cos(angle)*r)-1,223+(int)(Math.sin(angle)*r)-1,3,3),0xFFE9D299);
                    }
                }
                ui.fill(new Rect(171,166,8,114),0xFFE9D299);ui.fill(new Rect(126,219,98,8),0xFFE9D299);
                ui.fill(new Rect(160,208,30,30),identity);
                ui.fit(card.category().toUpperCase(Locale.ROOT),new Rect(83,326,230,20),16,0xFFB8EEE5);
            }
            draw.draw();
            if (card.species()!=null && !card.species().isBlank()) {
                var props = PokemonProperties.Companion.parse(card.species());
                props.setAspects(new HashSet<>(card.aspects()));
                FloatingState state = new FloatingState();
                state.updateAge(80);state.updatePartialTicks(0);
                draw.getMatrices().push();
                draw.getMatrices().translate(175,120,140);
                PokemonGuiUtilsKt.drawProfilePokemon(props.asRenderablePokemon(),draw.getMatrices(),
                    new Quaternionf().rotationXYZ(.16f,-.55f,0),PoseType.PROFILE,state,0,110,
                    ProfileTransformType.PROFILE,false,1,1,1,1,0,0,13);
                draw.getMatrices().pop();
            }
            ui.fit(card.type().toUpperCase(Locale.ROOT)+" / "+card.category().toUpperCase(Locale.ROOT),new Rect(28,386,298,20),15,0xFF443726);
            ui.fill(new Rect(27,412,296,39),0xFFE2D3B6);
            ui.fit(definition==null?vn.svarcade.tcg.client.component.CardWorldsLanguage.translate(card.text()):vn.svarcade.tcg.client.component.CardWorldsLanguage.effect(definition),new Rect(35,425,280,18),11,0xFF57452F);
            ui.fill(new Rect(26,462,298,2),0xFF8A6240);
            ui.fit("ATK / "+card.power(),new Rect(195,474,127,18),16,0xFF342B26);
            ui.text("CARD WORLDS",28,474,13,0xFF806C50);
            draw.draw();
            RenderSystem.bindTexture(target.getColorAttachment());
            NativeImage pixels = new NativeImage(350,500,false);
            pixels.loadFromTextureImage(0,false);
            pixels.mirrorVertically();
            Identifier texture = client.getTextureManager().registerDynamicTexture("duel_card",new NativeImageBackedTexture(pixels));
            client.getTextureManager().getTexture(texture).setFilter(true,false);
            fronts.put(key,texture);
            return texture;
        } finally {
            buffers.draw();target.delete();
            modelView.popMatrix();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,sorter);
            client.getFramebuffer().beginWrite(true);
            RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.disableBlend();
        }
    }
    void close() {
        fronts.values().forEach(MinecraftClient.getInstance().getTextureManager()::destroyTexture);
        fronts.clear();
    }
    void defense(WorldRenderContext context,Vec3d base) {
        plane(context,DEFENSE,base.add(0,.018,0),0,4.7f,4.7f,0xFFFFFFFF);
    }
    void board(WorldRenderContext context,Vec3d origin) {
        Identifier board=vn.svarcade.tcg.client.component.CardWorldsLanguage.language().equals("vi_vn")?Identifier.of("svarcade_tcg","textures/duel/board_vi.png"):BOARD;
        plane(context,board,origin.add(0,.006,0),0,43,29,0xFFFFFFFF);
    }
    void card(WorldRenderContext context,Identifier front,Vec3d center,float yaw,float width,float depth) {
        card(context,front,center,yaw,width,depth,0);
    }
    void card(WorldRenderContext context,Identifier front,Vec3d center,float yaw,float width,float depth,float tilt) {
        if(front==null)return;
        MatrixStack stack=context.matrixStack();if(stack==null||context.consumers()==null)return;
        stack.push();
        Vec3d camera=context.camera().getPos();stack.translate(center.x-camera.x,center.y-camera.y,center.z-camera.z);
        stack.multiply(new Quaternionf().rotationY((float)Math.toRadians(yaw)));
        stack.multiply(new Quaternionf().rotationX(tilt));
        var entry=stack.peek();
        var buffer=context.consumers().getBuffer(RenderLayer.getEntityCutoutNoCull(front));
        float x=width/2,z=depth/2,half=.009f;
        quad(buffer,entry,-x,half,-z,-x,half,z,x,half,z,x,half,-z,0xFFFFFFFF);
        var back=context.consumers().getBuffer(RenderLayer.getEntityCutoutNoCull(BACK));
        quad(back,entry,-x,-half,z,-x,-half,-z,x,-half,-z,x,-half,z,0xFFFFFFFF);
        // The four connecting edges share the texture rim; nothing overlaps the face.
        quad(back,entry,-x,-half,-z,-x,-half,z,-x,half,z,-x,half,-z,0xFF9D8454);
        quad(back,entry,x,half,-z,x,half,z,x,-half,z,x,-half,-z,0xFF9D8454);
        quad(back,entry,-x,half,-z,x,half,-z,x,-half,-z,-x,-half,-z,0xFF9D8454);
        quad(back,entry,-x,-half,z,x,-half,z,x,half,z,-x,half,z,0xFF9D8454);
        stack.pop();
    }
    private void plane(WorldRenderContext context,Identifier texture,Vec3d center,float yaw,float width,float depth,int color) {
        MatrixStack stack=context.matrixStack();if(stack==null||context.consumers()==null)return;
        stack.push();Vec3d camera=context.camera().getPos();stack.translate(center.x-camera.x,center.y-camera.y,center.z-camera.z);
        stack.multiply(new Quaternionf().rotationY((float)Math.toRadians(yaw)));
        float x=width/2,z=depth/2;
        quad(context.consumers().getBuffer(RenderLayer.getEntityCutoutNoCull(texture)),stack.peek(),-x,0,-z,-x,0,z,x,0,z,x,0,-z,color);
        stack.pop();
    }
    private static void quad(VertexConsumer buffer,MatrixStack.Entry entry,float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,float dx,float dy,float dz,int color) {
        vertex(buffer,entry,ax,ay,az,0,0,color);vertex(buffer,entry,bx,by,bz,0,1,color);
        vertex(buffer,entry,cx,cy,cz,1,1,color);vertex(buffer,entry,dx,dy,dz,1,0,color);
    }
    private static void vertex(VertexConsumer buffer,MatrixStack.Entry entry,float x,float y,float z,float u,float v,int color) {
        buffer.vertex(entry,x,y,z).color(color).texture(u,v).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(entry,0,1,0);
    }
}
