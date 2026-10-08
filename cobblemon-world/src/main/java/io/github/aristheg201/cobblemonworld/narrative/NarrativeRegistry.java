package io.github.aristheg201.cobblemonworld.narrative;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authored campaign, conversations, chains and teams share a validated data index. */
public final class NarrativeRegistry {
    public static final NarrativeRegistry INSTANCE = new NarrativeRegistry();
    public record Stage(String id, String act, String chapter, String title, String objective, String type,
                        String target, String item, int amount, String scene, String[] flags, String legacyFlag) {}
    public record Chain(String id, String title, String giver, String prerequisite, int reward, Stage[] stages) {}
    public record Choice(String id, String text, String reply, String next, String personality, String action) {}
    public record Node(String id, String speaker, String text, Choice[] choices) {}
    public record Scene(String id, String start, Node[] nodes) {}
    public record Team(String id, String strategy, Member[] members) {}
    public record Member(String species, String form, String properties, int level, String nature, String ability, String[] moves,
                         String item, int[] evs, Map<String,Integer> ivOverrides) {}
    public record Actor(String id, String name, String skin, String team, String spawnPolicy, String hideAfterStage, String reappearStage) {}
    public record Poi(String id, String name, String block, String sighting) {}
    public record Reward(String id,String species,String properties,int[] ivs,String battle) {}
    public record Season(String id,String chain,String[] requirements,Reward[] rewards) {}
    public record World(Stage[] campaign, Chain[] chains, Scene[] scenes, Team[] teams, Actor[] actors, Poi[] pois, Season[] seasons) {}
    public World data;
    public final Map<String, Stage> stages = new LinkedHashMap<>();
    public final Map<String, Chain> chains = new LinkedHashMap<>();
    public final Map<String, Scene> scenes = new LinkedHashMap<>();
    public final Map<String, Team> teams = new LinkedHashMap<>();
    public final Map<String, Poi> pois = new LinkedHashMap<>();
    public void load() {
        try (var in = getClass().getClassLoader().getResourceAsStream("data/cobblemonworld/narrative/world.json")) {
            data = new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8), World.class);
            stages.clear(); chains.clear(); scenes.clear(); teams.clear(); pois.clear();
            for (Stage s : data.campaign()) addStage(s);
            for (Chain c : data.chains()) {
                if (chains.put(c.id(), c) != null || c.stages().length < 3 || c.reward() < 0 || c.reward() > 20)
                    throw new IllegalStateException("Invalid substantial chain " + c.id());
                for (Stage s : c.stages()) addStage(s);
            }
            for (Scene s : data.scenes()) if (scenes.put(s.id(), s) != null) throw new IllegalStateException("Duplicate scene " + s.id());
            for (Team t : data.teams()) if (teams.put(t.id(), t) != null || t.members().length < 1 || t.members().length > 6)
                throw new IllegalStateException("Invalid team " + t.id());
            for (Poi p : data.pois()) if (pois.put(p.id(), p) != null) throw new IllegalStateException("Duplicate POI " + p.id());
            validate();
        } catch (Exception e) { throw new IllegalStateException("Cannot load authored narrative", e); }
    }
    private void addStage(Stage s) {
        if (s.id() == null || stages.put(s.id(), s) != null || s.amount() < 1 || !Set.of("talk","battle","travel","inspect","deliver","collect","buy","heal","claim","capture").contains(s.type()))
            throw new IllegalStateException("Invalid stage " + s.id());
    }
    public Node node(Scene s, String id) { return Arrays.stream(s.nodes()).filter(n -> n.id().equals(id)).findFirst().orElse(null); }
    private void validate() {
        Set<String> rewardIds=new HashSet<>();
        for(Season season:data.seasons()) {
            if(!chains.containsKey(season.chain()) || season.rewards().length==0)throw new IllegalStateException("Invalid seasonal chain " + season.id());
            for(Reward reward:season.rewards())if(!rewardIds.add(reward.id()) || reward.ivs().length!=6 || Arrays.stream(reward.ivs()).anyMatch(v->v<0||v>31))throw new IllegalStateException("Invalid seasonal reward " + reward.id());
        }
        for (Stage s : stages.values()) if (!scenes.containsKey(s.scene())) throw new IllegalStateException("Missing scene " + s.scene());
        for (Scene s : scenes.values()) {
            if (node(s,s.start()) == null) throw new IllegalStateException("Missing scene start " + s.id());
            Set<String> visited = new HashSet<>(); ArrayDeque<String> queue = new ArrayDeque<>(); queue.add(s.start());
            while (!queue.isEmpty()) {
                Node n = node(s,queue.remove()); if (!visited.add(n.id())) continue;
                if (n.choices() == null || n.choices().length == 0) throw new IllegalStateException("Unusable dialogue " + s.id() + ":" + n.id());
                Set<String> ids = new HashSet<>();
                for (Choice c : n.choices()) {
                    if (!ids.add(c.id()) || c.text() == null) throw new IllegalStateException("Invalid dialogue choice " + s.id());
                    if (c.next() != null && !c.next().isBlank()) {
                        if (node(s,c.next()) == null) throw new IllegalStateException("Broken dialogue destination " + s.id() + ":" + c.next());
                        queue.add(c.next());
                    }
                }
            }
            if (visited.size() != s.nodes().length) throw new IllegalStateException("Unreachable dialogue node " + s.id());
        }
    }
}
