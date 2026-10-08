package io.github.aristheg201.cobblemonworld.client;

import com.google.gson.Gson;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import io.github.aristheg201.cobblemonworld.story.ObjectiveService;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;

public final class ObjectiveHud {
    private static final Gson GSON = new Gson();
    private static ObjectiveService.Navigation target;
    private static boolean loggedFailure;
    private ObjectiveHud() {}
    public static void receive(String json) { target = GSON.fromJson(json, ObjectiveService.Navigation.class); }
    public static void register() {
        try {
            HudRenderCallback.EVENT.register((g, tick) -> render(g));
            ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { target = null; loggedFailure = false; });
        } catch (RuntimeException | LinkageError e) { diagnose("registration", e); }
    }
    private static void diagnose(String stage, Throwable error) {
        if (loggedFailure) return;
        loggedFailure = true;
        CobblemonWorldMod.LOGGER.error("Objective HUD {} failed (Minecraft 1.21.1/Mojang mappings, target={})", stage, target, error);
    }
    private static void render(GuiGraphics g) {
        boolean pushed = false;
        try {
            var mc = Minecraft.getInstance();
            var t = target;
            if (mc.player == null || mc.options.hideGui || t == null || t.status().equals("none")) return;
            String text;
            boolean ready = t.status().equals("ready") && t.dimension().equals(mc.player.level().dimension().location().toString());
            double dx = t.x() - mc.player.getX(), dz = t.z() - mc.player.getZ();
            if (ready) {
                long distance = Math.round(Math.sqrt(dx * dx + dz * dz + Math.pow(t.y() - mc.player.getY(), 2)));
                text = Component.translatable("objective.cobblemonworld.distance", t.label(), distance).getString();
            } else text = Component.translatable("objective.cobblemonworld." + (t.status().equals("dimension") ? "dimension" : "unavailable"), t.label()).getString();
            text = mc.font.plainSubstrByWidth(text, Math.max(100, mc.getWindow().getGuiScaledWidth() - 60));
            int w = mc.font.width(text) + 31, x = (mc.getWindow().getGuiScaledWidth() - w) / 2;
            int y = mc.getWindow().getGuiScaledHeight() - 69;
            g.fill(x - 2, y - 3, x + w + 2, y + 15, 0xCC2F2119);
            g.fill(x - 2, y + 14, x + w + 2, y + 15, 0xFFC89F59);
            g.drawString(mc.font, text, x + 24, y + 2, 0xFFFFE6B7, false);
            if (ready) {
                double yaw = Math.toDegrees(Math.atan2(-dx, dz)) - mc.player.getYRot();
                g.pose().pushPose(); pushed = true;
                g.pose().translate(x + 11, y + 6, 0);
                g.pose().mulPose(new Quaternionf().rotateZ((float) Math.toRadians(yaw)));
                // Pixel arrow pointing forward; rotating it preserves direction even on fonts lacking arrow glyphs.
                g.fill(-1, -6, 2, 7, 0xFFF0C561);
                for (int i = 0; i < 5; i++) g.fill(-i, -6 + i, i + 1, -5 + i, 0xFFF0C561);
                g.pose().popPose(); pushed = false;
            }
        } catch (RuntimeException | LinkageError e) { if (pushed) g.pose().popPose(); diagnose("render", e); }
    }
    public static ObjectiveService.Navigation qaTarget() { return target; }
}
