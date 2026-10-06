package io.github.aristheg201.cobblemonworld.client;

import io.github.aristheg201.cobblemonworld.network.RpgSkillPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class BossKeybinds {
    public static final KeyMapping DASH = bind("dash", GLFW.GLFW_KEY_Z);
    public static final KeyMapping GUARD = bind("guard", GLFW.GLFW_KEY_X);
    public static final KeyMapping BREAK = bind("break", GLFW.GLFW_KEY_C);
    public static final KeyMapping PURGE = bind("purge", GLFW.GLFW_KEY_V);
    public static final KeyMapping ANCHOR = bind("anchor", GLFW.GLFW_KEY_G);
    public static final KeyMapping PARTNER = bind("partner", GLFW.GLFW_KEY_H);

    private BossKeybinds() {}

    private static KeyMapping bind(String id, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.cobblemonworld." + id,
                key,
                "key.categories.cobblemonworld"
        ));
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (DASH.consumeClick()) send("DASH");
            while (GUARD.consumeClick()) send("GUARD");
            while (BREAK.consumeClick()) send("BREAK");
            while (PURGE.consumeClick()) send("PURGE");
            while (ANCHOR.consumeClick()) send("ANCHOR");
            while (PARTNER.consumeClick()) send("PARTNER");
        });
    }

    private static void send(String skill) {
        if (ClientPlayNetworking.canSend(RpgSkillPayload.TYPE)) {
            ClientPlayNetworking.send(new RpgSkillPayload(skill));
        }
    }
}
