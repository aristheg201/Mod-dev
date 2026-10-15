package dev.svextract.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Controls are stored by Minecraft in options.txt, independently of HUD settings. */
final class ExtractionKeyBindings {
    private final KeyBinding briefing=register("briefing",GLFW.GLFW_KEY_J);
    private final KeyBinding squad=register("squad",GLFW.GLFW_KEY_UNKNOWN);
    private final KeyBinding loot=register("loot",GLFW.GLFW_KEY_UNKNOWN);
    private final KeyBinding companionGun=register("companion_gun",GLFW.GLFW_KEY_UNKNOWN);
    private final KeyBinding buy=register("versus_buy",GLFW.GLFW_KEY_B);
    private int requestCooldown;

    private static KeyBinding register(String action,int defaultKey){
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.svextract."+action,
            InputUtil.Type.KEYSYM,defaultKey,"category.svextract"));
    }

    void tick(MinecraftClient client,Runnable openBriefing){
        if(requestCooldown>0)requestCooldown--;
        boolean showBriefing=consume(briefing);
        boolean showSquad=consume(squad);
        boolean showLoot=consume(loot);
        boolean lendGun=consume(companionGun);
        boolean showBuy=consume(buy);
        // Never trigger gameplay commands while typing, using a container, or disconnected.
        if(client.player==null||client.currentScreen!=null)return;
        if(showBuy){net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.svextract.network.Protocol.LobbyAction(9,"","",1,0,false));return;}
        if(showBriefing){openBriefing.run();return;}
        if(requestCooldown!=0)return;
        String command=showSquad?"svextract squad":showLoot?"svextract loot":lendGun?"svextract companiongun":null;
        if(command!=null){
            // Server command handlers validate mission state, ownership and inventory changes.
            client.player.networkHandler.sendChatCommand(command);
            requestCooldown=10;
        }
    }

    private static boolean consume(KeyBinding binding){
        boolean pressed=false;
        while(binding.wasPressed())pressed=true;
        return pressed;
    }
}
