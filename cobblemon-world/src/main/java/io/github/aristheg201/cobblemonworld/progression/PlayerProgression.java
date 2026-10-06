package io.github.aristheg201.cobblemonworld.progression;

import io.github.aristheg201.cobblemonworld.config.CWorldConfig;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class PlayerProgression {
    public int levelCap = CWorldConfig.INSTANCE.defaultLevelCap;

    public String currentStory = "prologue";
    public String currentObjective = "";
    public String leagueTier = "UNRANKED";
    public int leaguePoints = 0;

    public Set<String> storyFlags = new LinkedHashSet<>();
    public Set<String> badges = new LinkedHashSet<>();
    public Set<String> contacts = new LinkedHashSet<>();
    public Set<String> unreadMessages = new LinkedHashSet<>();
    public Set<String> readMessages = new LinkedHashSet<>();
    public Set<String> activeSideQuests = new LinkedHashSet<>();
    public Set<String> completedSideQuests = new LinkedHashSet<>();
    public Map<String, Integer> questProgress = new LinkedHashMap<>();

    public void normalize() {
        int max = CWorldConfig.INSTANCE.maxLevelCap;
        if (levelCap < 1) levelCap = 1;
        if (levelCap > max) levelCap = max;
        if (currentStory == null || currentStory.isBlank()) currentStory = "prologue";
        if (currentObjective == null) currentObjective = "";
        if (leagueTier == null || leagueTier.isBlank()) leagueTier = "UNRANKED";
        if (leaguePoints < 0) leaguePoints = 0;
        if (storyFlags == null) storyFlags = new LinkedHashSet<>();
        if (badges == null) badges = new LinkedHashSet<>();
        if (contacts == null) contacts = new LinkedHashSet<>();
        if (unreadMessages == null) unreadMessages = new LinkedHashSet<>();
        if (readMessages == null) readMessages = new LinkedHashSet<>();
        if (activeSideQuests == null) activeSideQuests = new LinkedHashSet<>();
        if (completedSideQuests == null) completedSideQuests = new LinkedHashSet<>();
        if (questProgress == null) questProgress = new LinkedHashMap<>();
    }
}
