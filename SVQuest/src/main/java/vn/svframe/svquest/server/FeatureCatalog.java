package vn.svframe.svquest.server;

import java.util.LinkedHashMap;
import java.util.Map;

public final class FeatureCatalog {
    private FeatureCatalog() {}

    public static final Map<String, String> COMMANDS = new LinkedHashMap<>();
    static {
        COMMANDS.put("pokemon_skills", "pokeskill");
        COMMANDS.put("gts", "gts");
        COMMANDS.put("skins", "trangphuc");
        COMMANDS.put("shop", "shop");
        COMMANDS.put("hunts", "hunts");
        COMMANDS.put("raids", "raid list");
        COMMANDS.put("ranked", "ranked");
        COMMANDS.put("battle_factory", "battlefactory");
        COMMANDS.put("wonder_trade", "wondertrade");
        COMMANDS.put("sts", "sts");
        COMMANDS.put("research", "research");
        COMMANDS.put("expeditions", "expeditions");
        COMMANDS.put("showcase", "showcase");
        COMMANDS.put("daily", "daily");
        COMMANDS.put("battle_pass", "battlepass");
        COMMANDS.put("fusion", "fusion");
        COMMANDS.put("homes", "home");
        COMMANDS.put("warps", "warp");
        COMMANDS.put("waypoints", "waypoint");
        COMMANDS.put("claims", "claim");
    }
}
