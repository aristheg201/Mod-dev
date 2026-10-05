package vn.worldcomesalive.client;
import vn.worldcomesalive.WorldComesAlive;
import vn.worldcomesalive.server.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class WorldComesAliveClient implements ClientModInitializer {
    @Override public void onInitializeClient(){net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(CitizenRenderer.MALE,()->CitizenModels.mesh(false));
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(CitizenRenderer.FEMALE,()->CitizenModels.mesh(true));
        EntityRendererRegistry.register(vn.worldcomesalive.furniture.FurnitureRegistry.SEAT,context->new net.minecraft.client.render.entity.EntityRenderer<vn.worldcomesalive.furniture.SeatEntity>(context){@Override public net.minecraft.util.Identifier getTexture(vn.worldcomesalive.furniture.SeatEntity seat){return net.minecraft.util.Identifier.ofVanilla("textures/misc/white.png");}});
        EntityRendererRegistry.register(WorldComesAlive.CITIZEN,CitizenRenderer::new);ClientPlayNetworking.registerGlobalReceiver(InteractionPackets.Snapshot.ID,(packet,ctx)->ctx.client().execute(()->{var view=WorldStore.JSON.fromJson(packet.json(),InteractionPackets.View.class);if(java.util.Set.of("management","requests","journal").contains(view.mode())){if(ctx.client().currentScreen instanceof CivilizationScreen screen)screen.update(view);else ctx.client().setScreen(new CivilizationScreen(view));}else if(view.mode().equals("lodging")){if(ctx.client().currentScreen instanceof LodgingScreen screen)screen.update(view);else ctx.client().setScreen(new LodgingScreen(view));}else if(view.mode().equals("dialogue")){if(ctx.client().currentScreen instanceof CitizenScreen screen&&!(screen instanceof TavernOrderScreen)&&!(screen instanceof LodgingScreen)&&!(screen instanceof CivilizationScreen))screen.update(view);else ctx.client().setScreen(new CitizenScreen(view));}else{if(ctx.client().currentScreen instanceof TavernOrderScreen screen)screen.update(view);else ctx.client().setScreen(new TavernOrderScreen(view));}}));}
}
