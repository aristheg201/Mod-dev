package io.github.aristheg201.cobblemonworld.qa;

import io.github.aristheg201.cobblemonworld.client.screen.TrainerPhoneScreen;
import io.github.aristheg201.cobblemonworld.network.QaAckPayload;
import io.github.aristheg201.cobblemonworld.network.QaControlPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import java.util.ArrayDeque;
import java.util.Queue;

public final class CWorldQaClientHarness {
    private static final Queue<QaControlPayload> QUEUE = new ArrayDeque<>();
    private static QaControlPayload current;
    private static int settleTicks;
    private static int waitTicks;
    private static boolean capturing;
    private static boolean connectRequested;
    private static int bootTicks;
    private static int connectWaitTicks;

    private CWorldQaClientHarness() {}

    public static void register() {
        System.out.println("CWORLD_QA_CLIENT_DRIVER_LOADED");
        ClientTickEvents.END_CLIENT_TICK.register(CWorldQaClientHarness::tick);
    }

    public static void enqueue(QaControlPayload payload) {
        QUEUE.add(payload);
    }

    private static void tick(Minecraft client) {
        if (client.player == null) {
            if (!connectRequested) {
                if (++bootTicks < 40 || client.screen == null) return;

                connectRequested = true;
                connectWaitTicks = 0;
                ServerAddress address = new ServerAddress("127.0.0.1", 25579);
                ServerData serverData = new ServerData(
                        "Cobblemon World QA", address.toString(), ServerData.Type.OTHER);
                System.out.println("CWORLD_QA_CLIENT_CONNECT_REQUEST 127.0.0.1:25579");
                ConnectScreen.startConnecting(client.screen, client, address, serverData, true, null);
                return;
            }

            if (++connectWaitTicks > 600) {
                String detail = "Client could not join QA server within 30 seconds.";
                System.err.println("CWORLD_QA_CLIENT_FAILED " + detail);
                throw new IllegalStateException(detail);
            }
            return;
        }

        if (connectRequested && connectWaitTicks >= 0) {
            System.out.println("CWORLD_QA_CLIENT_CONNECTED " + client.player.getGameProfile().getName());
            connectWaitTicks = -1;
        }

        if (capturing) return;

        if (current == null) {
            current = QUEUE.poll();
            if (current == null) return;
            waitTicks = 0;

            if ("capture_world".equals(current.action()) || "capture_toast".equals(current.action())) {
                client.setScreen(null);
                settleTicks = "capture_toast".equals(current.action()) ? 8 : 20;
            } else if ("capture_phone".equals(current.action())) {
                settleTicks = 8;
            } else if ("stop".equals(current.action())) {
                ack(client, token(current), true, "client stopping");
                current = null;
                client.stop();
                return;
            } else {
                fail(client, "Unknown QA control action: " + current.action());
                return;
            }
        }

        waitTicks++;
        if (waitTicks > 240) {
            fail(client, "Timed out preparing " + current.action() + " " + current.primary());
            return;
        }

        if ("capture_phone".equals(current.action())) {
            if (!(client.screen instanceof TrainerPhoneScreen phone)) return;
            phone.qaSelectApp(current.primary());
        }

        if (settleTicks-- > 0) return;
        capture(client, current.secondary());
    }

    private static void capture(Minecraft client, String fileName) {
        capturing = true;
        String token = token(current);
        String requestedName = (fileName == null || fileName.isBlank() ? token : fileName);
        final String safeName = requestedName.endsWith(".png") ? requestedName : requestedName + ".png";

        Screenshot.grab(client.gameDirectory, safeName, client.getMainRenderTarget(), message -> {
            System.out.println("CWORLD_QA_CAPTURE " + safeName + " :: " + message.getString());
            ack(client, token, true, safeName);
            current = null;
            capturing = false;
        });
    }

    private static String token(QaControlPayload payload) {
        if (payload.secondary() != null && !payload.secondary().isBlank()) return payload.secondary();
        return payload.primary();
    }

    private static void ack(Minecraft client, String token, boolean ok, String detail) {
        if (client.getConnection() != null) {
            ClientPlayNetworking.send(new QaAckPayload(token, ok, detail));
        }
    }

    private static void fail(Minecraft client, String detail) {
        System.err.println("CWORLD_QA_CLIENT_FAILED " + detail);
        ack(client, token(current), false, detail);
        throw new IllegalStateException(detail);
    }
}
