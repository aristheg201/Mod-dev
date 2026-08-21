package vn.svframe.lively;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svframe.lively.dialogue.DialogueService;

public final class LivelyNpcs implements ModInitializer {
    public static final String MOD_ID = "livelynpcs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private DialogueService dialogueService;

    @Override
    public void onInitialize() {
        dialogueService = new DialogueService();
        dialogueService.install();
        LOGGER.info("Lively NPCs initialized: offline AI core, authority layer, dialogue and combat cortex ready");
    }
}
