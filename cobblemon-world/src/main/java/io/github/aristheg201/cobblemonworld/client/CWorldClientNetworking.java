package io.github.aristheg201.cobblemonworld.client;

import io.github.aristheg201.cobblemonworld.client.screen.TrainerPhoneScreen;
import io.github.aristheg201.cobblemonworld.network.PhoneSnapshotPayload;
import io.github.aristheg201.cobblemonworld.network.QaControlPayload;
import io.github.aristheg201.cobblemonworld.network.ToastPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import net.minecraft.network.chat.Component;

public final class CWorldClientNetworking {
    private CWorldClientNetworking() {}

    public static void register() {
        ObjectiveHud.register();
        ClientPlayNetworking.registerGlobalReceiver(io.github.aristheg201.cobblemonworld.network.ConversationClosePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().screen instanceof io.github.aristheg201.cobblemonworld.client.screen.DialogueScreen dialogue
                            && dialogue.snapshot().session().equals(payload.session())) context.client().setScreen(null);
                }));
        ClientPlayNetworking.registerGlobalReceiver(io.github.aristheg201.cobblemonworld.network.ConversationPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().screen instanceof io.github.aristheg201.cobblemonworld.client.screen.DialogueScreen dialogue) dialogue.update(payload.json());
                    else context.client().setScreen(new io.github.aristheg201.cobblemonworld.client.screen.DialogueScreen(payload.json()));
                }));
        ClientPlayNetworking.registerGlobalReceiver(io.github.aristheg201.cobblemonworld.network.NavigationPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ObjectiveHud.receive(payload.json())));
        ClientPlayNetworking.registerGlobalReceiver(io.github.aristheg201.cobblemonworld.network.ShopSnapshotPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (payload.open()) context.client().setScreen(new io.github.aristheg201.cobblemonworld.client.screen.ShopScreen(payload.json()));
                    else if (context.client().screen instanceof io.github.aristheg201.cobblemonworld.client.screen.ShopScreen shop) shop.update(payload.json());
                }));
        ClientPlayNetworking.registerGlobalReceiver(PhoneSnapshotPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().screen instanceof TrainerPhoneScreen phone) phone.update(payload.json());
                    else context.client().setScreen(new TrainerPhoneScreen(payload.json()));
                }));

        ClientPlayNetworking.registerGlobalReceiver(ToastPayload.TYPE, (payload, context) ->
                context.client().execute(() -> showToast(payload)));

        if (Boolean.getBoolean("cworld.qa.client")) {
            ClientPlayNetworking.registerGlobalReceiver(QaControlPayload.TYPE, (payload, context) ->
                    context.client().execute(() ->
                            io.github.aristheg201.cobblemonworld.qa.CWorldQaClientHarness.enqueue(payload)));
        }
    }

    private static void showToast(ToastPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (Boolean.getBoolean("cworld.qa.client")) {
            // First-join vanilla tutorial/chat notices otherwise stack over the toast under test.
            mc.getToasts().clear();
        }
        TutorialToast.Icons icon = switch (payload.category()) {
            case "message", "contact" -> TutorialToast.Icons.SOCIAL_INTERACTIONS;
            case "objective", "story" -> TutorialToast.Icons.RECIPE_BOOK;
            case "level_cap" -> TutorialToast.Icons.TREE;
            case "badge", "faction" -> TutorialToast.Icons.WOODEN_PLANKS;
            default -> TutorialToast.Icons.MOUSE;
        };
        TutorialToast toast = new TutorialToast(
                icon,
                payload.title(),
                payload.body(),
                false
        );
        mc.getTutorial().addTimedToast(toast, 100);
        System.out.println("CWORLD_TUTORIAL_TOAST category=" + payload.category()
                + " title=" + payload.title().getString());
    }
}
