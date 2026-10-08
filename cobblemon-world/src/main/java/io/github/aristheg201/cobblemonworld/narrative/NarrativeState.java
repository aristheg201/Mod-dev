package io.github.aristheg201.cobblemonworld.narrative;

import java.util.*;

/** IDs are persisted; translated prose is never used as progression identity. */
public final class NarrativeState {
    public int schema;
    public boolean scamPaid;
    public Map<String, NarrativeRewards.Payment> payments = new LinkedHashMap<>();
    public String main = "";
    public Set<String> finished = new LinkedHashSet<>();
    public Map<String, String> chains = new LinkedHashMap<>();
    public Set<String> completedChains = new LinkedHashSet<>();
    public Map<String, Integer> personality = new LinkedHashMap<>();
    public Map<String, Integer> losses = new LinkedHashMap<>();
    public Map<String, String> sceneNodes = new LinkedHashMap<>();
    public List<Turn> transcript = new ArrayList<>();
    public Map<String, Claim> claims = new LinkedHashMap<>();
    public record Turn(String scene, String node, String speaker, String textKey) {}
    public record Claim(String pokemonUuid, String status) {}
    public void normalize() {
        if (payments == null) payments = new LinkedHashMap<>();
        if (main == null) main = "";
        if (finished == null) finished = new LinkedHashSet<>();
        if (chains == null) chains = new LinkedHashMap<>();
        if (completedChains == null) completedChains = new LinkedHashSet<>();
        if (personality == null) personality = new LinkedHashMap<>();
        if (losses == null) losses = new LinkedHashMap<>();
        if (sceneNodes == null) sceneNodes = new LinkedHashMap<>();
        if (transcript == null) transcript = new ArrayList<>();
        if (claims == null) claims = new LinkedHashMap<>();
    }
}
