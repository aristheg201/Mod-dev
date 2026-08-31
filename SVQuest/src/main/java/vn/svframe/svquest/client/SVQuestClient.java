package vn.svframe.svquest.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import vn.svframe.svquest.SVQuest;
import vn.svframe.svquest.network.ActionPayload;
import vn.svframe.svquest.network.StatePayload;

public final class SVQuestClient implements ClientModInitializer {
    public static final ClientState STATE = new ClientState();
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.svquest.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.svquest"));

        ClientPlayNetworking.registerGlobalReceiver(StatePayload.ID, (payload, context) ->
                context.client().execute(() -> STATE.apply(payload.state())));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                client.setScreen(new QuestScreen());
                requestSync();
            }
        });
        SVQuest.LOGGER.info("SVQuest client initialized safely. Press J to open the hub.");
    }

    public static void requestSync() {
        try {
            if (ClientPlayNetworking.canSend(ActionPayload.ID)) {
                ClientPlayNetworking.send(new ActionPayload("sync"));
            } else {
                STATE.markServerUnavailable();
            }
        } catch (Throwable t) {
            STATE.markServerUnavailable();
        }
    }

    public static void action(String id) {
        try {
            if (ClientPlayNetworking.canSend(ActionPayload.ID)) {
                ClientPlayNetworking.send(new ActionPayload(id));
            }
        } catch (Throwable ignored) {
            STATE.markServerUnavailable();
        }
    }
}
