package vn.worldcomesalive.client;
import vn.worldcomesalive.WorldComesAlive;
import vn.worldcomesalive.server.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class WorldComesAliveClient implements ClientModInitializer {
    @Override public void onInitializeClient(){net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(CitizenRenderer.MALE,()->CitizenModels.mesh(false));
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(CitizenRenderer.FEMALE,()->CitizenModels.mesh(true));
        EntityRendererRegistry.register(WorldComesAlive.CITIZEN,CitizenRenderer::new);ClientPlayNetworking.registerGlobalReceiver(InteractionPackets.Snapshot.ID,(packet,ctx)->ctx.client().execute(()->{var view=WorldStore.JSON.fromJson(packet.json(),InteractionPackets.View.class);if(ctx.client().currentScreen instanceof CitizenScreen screen)screen.update(view);else ctx.client().setScreen(new CitizenScreen(view));}));}
}
