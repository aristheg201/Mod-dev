package vn.svframe.svquest.server;

import net.fabricmc.api.DedicatedServerModInitializer;

public final class SVQuestDedicatedServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        new ServerRuntime().register();
    }
}
