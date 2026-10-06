package io.github.aristheg201.cobblemonworld.client;

import io.github.aristheg201.cobblemonworld.client.screen.TrainerPhoneScreen;
import io.github.aristheg201.cobblemonworld.network.PhoneSnapshotPayload;
import io.github.aristheg201.cobblemonworld.network.QaControlPayload;
import io.github.aristheg201.cobblemonworld.network.ToastPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
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
        SystemToast.add(
                mc.getToasts(),
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.literal(payload.title()),
                Component.literal(payload.body())
        );
    }
}
