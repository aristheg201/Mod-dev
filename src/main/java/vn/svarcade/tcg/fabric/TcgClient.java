package vn.svarcade.tcg.fabric;

import com.google.gson.Gson;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import vn.svarcade.tcg.client.CardWorldsScreen;
import vn.svarcade.tcg.client.render.PokemonModels;

import java.util.List;

/**
 * Client lifecycle for Card Worlds. Active duel snapshots survive Minecraft's
 * cross-dimension terrain screen so the Duel HUD is restored after Realm sync.
 */
public final class TcgClient implements ClientModInitializer {
    public static final Gson JSON=new Gson();
    public static KeyBinding OPEN;
    private static TcgMod.Snapshot pendingRealmSnapshot;

    @Override
    public void onInitializeClient(){
        PokemonModels.initialize();
        vn.svarcade.tcg.client.render.PhysicalCardRenderer.initialize();
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES).registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener(){
            public net.minecraft.util.Identifier getFabricId(){return net.minecraft.util.Identifier.of("svarcade_tcg","presentation_caches");}
            public void reload(net.minecraft.resource.ResourceManager resources){
                vn.svarcade.tcg.integration.CardWorldsIntegrations.reload();
                vn.svarcade.tcg.client.render.PokemonDuelAnimationResolver.invalidate();
                vn.svarcade.tcg.client.component.CardWorldsLanguage.invalidate();
                vn.svarcade.tcg.client.render.DuelWorldScene.invalidatePresentation();
            }
        });
        OPEN=KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cardworlds.open",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_Y,"key.categories.cardworlds"));

        ClientTickEvents.END_CLIENT_TICK.register(client->{
            while(OPEN.wasPressed()){
                if(client.currentScreen==null&&client.player!=null)request("open",List.of(),0);
                else if(client.currentScreen instanceof CardWorldsScreen screen&&!screen.editing())screen.close();
            }

            TcgMod.Snapshot pending=pendingRealmSnapshot;
            if(pending==null||client.player==null||client.world==null)return;

            boolean realm=client.world.getRegistryKey().getValue().toString().equals("svarcade_tcg:duel_realm");
            boolean result=pending.duel()!=null&&!pending.duel().winner().isBlank();

            if(client.currentScreen instanceof CardWorldsScreen){
                if(realm||result)pendingRealmSnapshot=null;
                return;
            }

            if(realm||result){
                client.setScreen(new CardWorldsScreen(pending));
                pendingRealmSnapshot=null;
                org.slf4j.LoggerFactory.getLogger("cardworlds-client").info(
                    "CARDWORLDS_DUEL_UI_REOPEN world={} result={}",
                    client.world.getRegistryKey().getValue(),result);
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(TcgPackets.Snapshot.ID,(packet,ctx)->
            ctx.client().execute(()->{
                TcgMod.Snapshot state=JSON.fromJson(packet.json(),TcgMod.Snapshot.class);
                vn.svarcade.tcg.physical.PhysicalCards.presentation(state.definitions());
                boolean realmFlow=state.duel()!=null||state.spectator();
                if(realmFlow)pendingRealmSnapshot=state;
                else pendingRealmSnapshot=null;

                if(ctx.client().currentScreen instanceof CardWorldsScreen screen){
                    screen.update(state);
                    return;
                }

                if(realmFlow){
                    if(ctx.client().world!=null&&ctx.client().world.getRegistryKey().getValue().toString().equals("svarcade_tcg:duel_realm")){
                        ctx.client().setScreen(new CardWorldsScreen(state));
                        pendingRealmSnapshot=null;
                    }
                    return;
                }

                if(state.open())ctx.client().setScreen(new CardWorldsScreen(state));
            })
        );
    }

    public static void request(String action,List<String> args,long revision){
        var client=MinecraftClient.getInstance();
        if ("duel".equals(action) && client.currentScreen instanceof CardWorldsScreen screen && screen.state.spectator()) {
            org.slf4j.LoggerFactory.getLogger("cardworlds-client").info("CARDWORLDS_SPECTATOR_ACTION_BLOCKED");
            return;
        }
        if(client.getNetworkHandler()!=null&&ClientPlayNetworking.canSend(TcgPackets.Input.ID))
            ClientPlayNetworking.send(new TcgPackets.Input(JSON.toJson(new TcgPackets.Request(action,args,revision))));
    }
}
