package io.github.aristheg201.cobblemonworld.qa;

import io.github.aristheg201.cobblemonworld.client.screen.TrainerPhoneScreen;
import io.github.aristheg201.cobblemonworld.network.QaAckPayload;
import io.github.aristheg201.cobblemonworld.network.QaControlPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Queue;

public final class CWorldQaClientHarness {
    private static final Queue<QaControlPayload> QUEUE = new ArrayDeque<>();
    private static final int PERF_WARMUP_TICKS = 120;
    private static final double SOFTWARE_MIN_AVERAGE_FPS = 5.0;
    private static final double SOFTWARE_MAX_AVERAGE_FRAME_MS = 200.0;
    private static final double HARDWARE_MIN_AVERAGE_FPS = 30.0;
    private static final double HARDWARE_MAX_AVERAGE_FRAME_MS = 33.34;

    private static QaControlPayload current;
    private static int settleTicks;
    private static int waitTicks;
    private static boolean capturing;
    private static boolean worldCreateRequested;
    private static int bootTicks;
    private static int worldWaitTicks;
    private static int worldReadyTicks;
    private static int stopCountdown = -1;
    private static int stopWarmupTicks;

    private static long fpsSamples;
    private static long fpsTotal;
    private static int minFps = Integer.MAX_VALUE;
    private static long frameSamples;
    private static long frameTotalNs;

    private CWorldQaClientHarness() {}

    public static void register() {
        System.out.println("CWORLD_QA_CLIENT_DRIVER_LOADED");
        ClientTickEvents.END_CLIENT_TICK.register(CWorldQaClientHarness::tick);
    }

    public static void enqueue(QaControlPayload payload) {
        QUEUE.add(payload);
    }

    private static void tick(Minecraft client) {
        if (stopCountdown >= 0) {
            if (stopCountdown-- == 0) {
                client.stop();
            }
            return;
        }

        if (Boolean.getBoolean("cworld.qa.production") && client.player != null && client.player.isDeadOrDying()) {
            client.player.respawn();
            client.setScreen(null);
            return;
        }
        if (client.player == null) {
            if (!worldCreateRequested) {
                if (!atlasReady(client)) {
                    bootTicks = 0;
                    return;
                }
                if (++bootTicks < 20 || client.screen == null) return;

                worldCreateRequested = true;
                worldWaitTicks = 0;
                if (Boolean.getBoolean("cworld.qa.multiplayer")) {
                    String address = System.getProperty("cworld.qa.address", "127.0.0.1:25571");
                    net.minecraft.client.gui.screens.ConnectScreen.startConnecting(client.screen, client,
                            net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),
                            new net.minecraft.client.multiplayer.ServerData("Production QA", address,
                                    net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null);
                    System.out.println("CWORLD_QA_MULTIPLAYER_CONNECT_REQUEST " + address);
                    return;
                }

                LevelSettings settings = new LevelSettings(
                        "Cobblemon World QA",
                        GameType.SURVIVAL,
                        false,
                        Difficulty.NORMAL,
                        !Boolean.getBoolean("cworld.qa.production"),
                        new GameRules(),
                        WorldDataConfiguration.DEFAULT
                );
                WorldOptions options = new WorldOptions(0xC0BB1E5L, true, false);

                System.out.println("CWORLD_QA_CLIENT_ATLAS_READY");
                if (Boolean.getBoolean("cworld.qa.restart")) {
                    System.out.println("CWORLD_QA_SINGLEPLAYER_REOPEN_REQUEST world=CWorldQA");
                    client.createWorldOpenFlows().openWorld("CWorldQA", () -> client.stop());
                    return;
                }
                System.out.println("CWORLD_QA_SINGLEPLAYER_CREATE_REQUEST world=CWorldQA");
                client.createWorldOpenFlows().createFreshLevel(
                        "CWorldQA",
                        settings,
                        options,
                        WorldPresets::createNormalWorldDimensions,
                        client.screen
                );
                return;
            }

            if (++worldWaitTicks > 1800) {
                String detail = "Integrated singleplayer world did not become playable within 90 seconds.";
                System.err.println("CWORLD_QA_CLIENT_FAILED " + detail);
                throw new IllegalStateException(detail);
            }
            return;
        }

        if (worldWaitTicks >= 0) {
            if (Boolean.getBoolean("cworld.qa.production")) {
                client.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
                client.getTutorial().stop();
                client.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
                client.getToasts().clear();
            }
            System.out.println("CWORLD_QA_SINGLEPLAYER_CONNECTED " + client.player.getGameProfile().getName());
            worldWaitTicks = -1;
            worldReadyTicks = 0;
        }

        // The player object becomes available before the terrain transition has necessarily
        // produced a real world frame. Never let visual QA photograph "Loading terrain...".
        if (client.level == null || ++worldReadyTicks < 60) return;

        samplePerformance(client);
        ProductionQaClient.autoBattle(client);

        if (capturing) return;

        if (current == null) {
            current = QUEUE.poll();
            if (current == null) return;
            waitTicks = 0;

            if ("capture_world".equals(current.action())) {
                client.setScreen(null);
                settleTicks = 20;
            } else if ("capture_toast".equals(current.action())) {
                client.setScreen(null);
                // The network toast above proves delivery. Add the same vanilla TutorialToast
                // locally so the screenshot deterministically captures its presentation.
                TutorialToast toast = new TutorialToast(
                        TutorialToast.Icons.RECIPE_BOOK,
                        Component.literal("Level Cap Increased"),
                        Component.literal(current.primary() == null || current.primary().isBlank()
                                ? "Level cap updated"
                                : current.primary()),
                        false
                );
                client.getTutorial().addTimedToast(toast, 100);
                settleTicks = 4;
            } else if ("capture_phone".equals(current.action())) {
                int scale = phoneScale(current.primary());
                if (client.options.guiScale().get() != scale) {
                    client.options.guiScale().set(scale);
                    client.resizeDisplay();
                }
                settleTicks = 12;
            } else if ("stop".equals(current.action())) {
                // Short dedicated restart/dependency scenarios can finish before the
                // performance warm-up. Collect real samples before applying the same
                // threshold; do not turn successful gameplay checks into a teardown crash.
                if (Boolean.getBoolean("cworld.qa.production")
                        && (fpsSamples < 20L || frameSamples < 20L) && stopWarmupTicks++ < 200) {
                    QUEUE.add(current);
                    current = null;
                    return;
                }
                verifyPerformance(client);
                ack(client, token(current), true, "singleplayer client stopping");
                current = null;
                // Give the integrated server time to consume the local-network ACK and print
                // its final QA/performance evidence before the client shuts the JVM down.
                stopCountdown = 60;
                return;
            } else if (current.action().startsWith("prod_")) {
                settleTicks = 0;
            } else {
                fail(client, "Unknown QA control action: " + current.action());
                return;
            }
        }

        waitTicks++;
        if (waitTicks > (current.action().equals("prod_narrative") ? 12000 : 360)) {
            fail(client, "Timed out preparing " + current.action() + " " + current.primary());
            return;
        }

        if (current.action().startsWith("prod_")) {
            int result = ProductionQaClient.perform(client, current);
            if (result == 2) capture(client, current.secondary());
            else if (result == 1) { ack(client, token(current), true, "normal client input completed"); current = null; }
            return;
        }
        if ("capture_phone".equals(current.action())) {
            if (!(client.screen instanceof TrainerPhoneScreen phone)) return;
            phone.qaSelectApp(phoneApp(current.primary()));
        }

        if (settleTicks-- > 0) return;
        capture(client, current.secondary());
    }

    private static void samplePerformance(Minecraft client) {
        if (worldReadyTicks < PERF_WARMUP_TICKS) return;

        int fps = client.getFps();
        if (fps > 0) {
            fpsSamples++;
            fpsTotal += fps;
            minFps = Math.min(minFps, fps);
        }

        long frameNs = client.getFrameTimeNs();
        if (frameNs > 0L) {
            frameSamples++;
            frameTotalNs += frameNs;
        }
    }

    private static void verifyPerformance(Minecraft client) {
        if (fpsSamples < 20L || frameSamples < 20L) {
            fail(client, "Not enough client performance samples: fps=" + fpsSamples + " frame=" + frameSamples);
            return;
        }

        double averageFps = fpsTotal / (double) fpsSamples;
        double averageFrameMs = frameTotalNs / (double) frameSamples / 1_000_000.0;
        boolean softwareRenderer = "1".equals(System.getenv("LIBGL_ALWAYS_SOFTWARE"));
        double minimumFps = softwareRenderer ? SOFTWARE_MIN_AVERAGE_FPS : HARDWARE_MIN_AVERAGE_FPS;
        double maximumFrameMs = softwareRenderer ? SOFTWARE_MAX_AVERAGE_FRAME_MS : HARDWARE_MAX_AVERAGE_FRAME_MS;

        if (averageFps < minimumFps) {
            fail(client, "Average FPS regression: " + averageFps + " < " + minimumFps
                    + " mode=" + (softwareRenderer ? "software" : "hardware"));
            return;
        }
        if (averageFrameMs > maximumFrameMs) {
            fail(client, "Average frame-time regression: " + averageFrameMs + "ms > " + maximumFrameMs
                    + "ms mode=" + (softwareRenderer ? "software" : "hardware"));
            return;
        }

        System.out.printf(
                Locale.ROOT,
                "CWORLD_QA_PERF_CLIENT_PASS mode=%s avg_fps=%.2f min_fps=%d avg_frame_ms=%.2f samples=%d%n",
                softwareRenderer ? "software-regression" : "hardware",
                averageFps,
                minFps == Integer.MAX_VALUE ? 0 : minFps,
                averageFrameMs,
                Math.min(fpsSamples, frameSamples)
        );
    }

    private static int phoneScale(String value) {
        if (value == null) return 2;
        int split = value.indexOf('|');
        if (split <= 0) return 2;
        try {
            int scale = Integer.parseInt(value.substring(0, split));
            return scale == 3 ? 3 : 2;
        } catch (NumberFormatException ignored) {
            return 2;
        }
    }

    private static String phoneApp(String value) {
        if (value == null || value.isBlank()) return "home";
        int split = value.indexOf('|');
        if (split < 0 || split >= value.length() - 1) return value;
        return value.substring(split + 1);
    }

    private static boolean atlasReady(Minecraft client) {
        try {
            client.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(ResourceLocation.fromNamespaceAndPath("minecraft", "block/stone"));
            return true;
        } catch (IllegalStateException ignored) {
            return false;
        }
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
        if (payload != null && payload.secondary() != null && !payload.secondary().isBlank()) return payload.secondary();
        return payload == null ? "unknown" : payload.primary();
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
