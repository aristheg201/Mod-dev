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
        ClientPlayNetworking.registerGlobalReceiver(PhoneSnapshotPayload.TYPE, (payload, context) ->
                context.client().execute(() -> context.client().setScreen(new TrainerPhoneScreen(payload.json()))));

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
        TutorialToast.Icons icon = switch (payload.category()) {
            case "message", "contact" -> TutorialToast.Icons.SOCIAL_INTERACTIONS;
            case "objective", "story" -> TutorialToast.Icons.RECIPE_BOOK;
            case "level_cap" -> TutorialToast.Icons.TREE;
            case "badge", "faction" -> TutorialToast.Icons.WOODEN_PLANKS;
            default -> TutorialToast.Icons.MOUSE;
        };
        TutorialToast toast = new TutorialToast(
                icon,
                Component.literal(payload.title()),
                Component.literal(payload.body()),
                false
        );
        mc.getTutorial().addTimedToast(toast, 100);
    }
}
