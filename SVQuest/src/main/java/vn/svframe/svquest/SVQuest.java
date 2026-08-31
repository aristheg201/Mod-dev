package vn.svframe.svquest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svframe.svquest.network.ActionPayload;
import vn.svframe.svquest.network.StatePayload;

public final class SVQuest implements ModInitializer {
    public static final String MOD_ID = "svquest";
    public static final Logger LOGGER = LoggerFactory.getLogger("SVQuest");

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StatePayload.ID, StatePayload.CODEC);
        LOGGER.info("SVQuest common payloads registered safely.");
    }
}
