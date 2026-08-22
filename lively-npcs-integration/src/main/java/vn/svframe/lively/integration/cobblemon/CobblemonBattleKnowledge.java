package vn.svframe.lively.integration.cobblemon;

import com.cobblemon.mod.common.api.battles.interpreter.Effect;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.combat.CombatCortex;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-battle fair-information cache. It learns only from public Showdown interpreter instructions.
 * It deliberately never reads an opponent's unrevealed moveset, held item or ability from server state.
 */
public final class CobblemonBattleKnowledge {
    private static final Set<String> TEAM_SETUP = Set.of(
            "stealthrock", "spikes", "toxicspikes", "stickyweb",
            "tailwind", "trickroom", "reflect", "lightscreen", "auroraveil",
            "sunnyday", "raindance", "sandstorm", "snowscape", "hail",
            "electricterrain", "grassyterrain", "mistyterrain", "psychicterrain");

    public record RevealedPokemon(Set<String> moves, String ability, String item) {
        public RevealedPokemon {
            moves = Set.copyOf(moves);
            ability = ability == null ? "" : ability;
            item = item == null ? "" : item;
        }
    }

    private record Observer(UUID npcId, UUID actorId) {}
    private record KnowledgeKey(UUID npcId, UUID battleId, UUID pokemonId) {}
    private record TurnKey(UUID battleId, UUID actorId, int turn) {}
    private record Intent(UUID activePokemonId, String kind, String move, String target, String switchPokemon, String gimmick, double value) {}

    private static final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Observer>> OBSERVERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<KnowledgeKey, MutableKnowledge> KNOWLEDGE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<TurnKey, ConcurrentHashMap<UUID, Intent>> INTENTS = new ConcurrentHashMap<>();

    private CobblemonBattleKnowledge() {}

    public static void register(UUID npcId, PokemonBattle battle, BattleActor actor) {
        if (npcId == null || battle == null || actor == null) return;
        OBSERVERS.computeIfAbsent(battle.getBattleId(), ignored -> new ConcurrentHashMap<>())
                .put(actor.getUuid(), new Observer(npcId, actor.getUuid()));
    }

    public static void observeMove(PokemonBattle battle, BattlePokemon user, MoveTemplate move) {
        if (battle == null || user == null || move == null || user.getActor() == null) return;
        String id = move.getName();
        if (id == null || id.isBlank()) return;
        observe(battle, user, "move", id);
    }

    public static void observeAbility(PokemonBattle battle, BattlePokemon user, Effect effect) {
        if (battle == null || user == null || effect == null || user.getActor() == null) return;
        String id = effect.getId();
        if (id == null || id.isBlank()) return;
        observe(battle, user, "ability", id);
    }

    public static void observeItem(PokemonBattle battle, BattlePokemon user, Effect effect) {
        if (battle == null || user == null || effect == null || user.getActor() == null) return;
        String id = effect.getId();
        if (id == null || id.isBlank()) return;
        observe(battle, user, "item", id);
    }

    private static void observe(PokemonBattle battle, BattlePokemon user, String kind, String value) {
        Map<UUID, Observer> observers = OBSERVERS.get(battle.getBattleId());
        if (observers == null || observers.isEmpty()) return;
        BattleActor userActor = user.getActor();
        for (Observer observer : observers.values()) {
            BattleActor observingActor = battle.getActor(observer.actorId());
            if (observingActor == null || observingActor.getSide() == userActor.getSide()) continue;
            KnowledgeKey key = new KnowledgeKey(observer.npcId(), battle.getBattleId(), user.getUuid());
            MutableKnowledge knowledge = KNOWLEDGE.computeIfAbsent(key, ignored -> new MutableKnowledge());
            boolean changed = switch (kind) {
                case "move" -> knowledge.moves.add(value);
                case "ability" -> !value.equals(knowledge.ability) && setAbility(knowledge, value);
                case "item" -> !value.equals(knowledge.item) && setItem(knowledge, value);
                default -> false;
            };
            if (changed) remember(observer.npcId(), battle, user, kind, value);
        }
    }

    private static boolean setAbility(MutableKnowledge knowledge, String value) { knowledge.ability = value; return true; }
    private static boolean setItem(MutableKnowledge knowledge, String value) { knowledge.item = value; return true; }

    private static void remember(UUID npcId, PokemonBattle battle, BattlePokemon user, String kind, String value) {
        if (LivelyApi.states() == null) return;
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("battle", battle.getBattleId().toString());
        facts.put("turn", Integer.toString(battle.getTurn()));
        facts.put("opponent_actor", user.getActor().getUuid().toString());
        facts.put("pokemon", user.getUuid().toString());
        facts.put(kind, value);
        LivelyApi.states().get(npcId).ifPresent(state -> state.remember("battle_revealed_" + kind, facts, 0.55D, 1D));
    }

    public static RevealedPokemon known(UUID npcId, UUID battleId, UUID pokemonId) {
        MutableKnowledge knowledge = KNOWLEDGE.get(new KnowledgeKey(npcId, battleId, pokemonId));
        return knowledge == null ? new RevealedPokemon(Set.of(), "", "") : knowledge.snapshot();
    }

    /** Returns a penalty in [0,1] for actor-level duplicate/conflicting actions during a doubles turn. */
    public static double coordinationPenalty(UUID npcId, PokemonBattle battle, ActiveBattlePokemon active,
                                             CombatCortex.CombatAction candidate) {
        if (battle == null || active == null || candidate == null) return 0D;
        UUID actorId = active.getActor().getUuid();
        TurnKey key = new TurnKey(battle.getBattleId(), actorId, battle.getTurn());
        Map<UUID, Intent> intents = INTENTS.get(key);
        if (intents == null || intents.isEmpty()) return 0D;
        String kind = candidate.metadata().getOrDefault("kind", "");
        String move = normalize(candidate.metadata().getOrDefault("move", ""));
        String target = candidate.metadata().getOrDefault("target", "");
        String switchPokemon = candidate.metadata().getOrDefault("pokemon", "");
        String gimmick = candidate.metadata().getOrDefault("gimmick", "");
        double penalty = 0D;
        for (Intent intent : intents.values()) {
            if (intent.activePokemonId().equals(active.getBattlePokemon().getUuid())) continue;
            if (kind.equals("switch") && !switchPokemon.isBlank() && switchPokemon.equals(intent.switchPokemon())) penalty = Math.max(penalty, 1D);
            if (!gimmick.isBlank() && gimmick.equals(intent.gimmick())) penalty = Math.max(penalty, 0.92D);
            if (kind.equals("move") && TEAM_SETUP.contains(move) && move.equals(intent.move())) {
                penalty = Math.max(penalty, 0.96D);
            }
            if (kind.equals("move") && !target.isBlank() && !target.equals("auto") && target.equals(intent.target()) && intent.value() >= 0.72D) {
                penalty = Math.max(penalty, 0.16D);
            }
        }
        return Math.min(1D, penalty);
    }

    public static void rememberIntent(PokemonBattle battle, ActiveBattlePokemon active, CombatCortex.CombatAction action) {
        if (battle == null || active == null || action == null) return;
        TurnKey key = new TurnKey(battle.getBattleId(), active.getActor().getUuid(), battle.getTurn());
        INTENTS.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>()).put(active.getBattlePokemon().getUuid(),
                new Intent(active.getBattlePokemon().getUuid(), action.metadata().getOrDefault("kind", ""),
                        normalize(action.metadata().getOrDefault("move", "")), action.metadata().getOrDefault("target", ""),
                        action.metadata().getOrDefault("pokemon", ""), action.metadata().getOrDefault("gimmick", ""),
                        action.immediateValue()));
        INTENTS.keySet().removeIf(old -> old.battleId().equals(battle.getBattleId()) && old.turn() + 2 < battle.getTurn());
    }

    public static void unregisterBattle(UUID battleId) {
        if (battleId == null) return;
        OBSERVERS.remove(battleId);
        KNOWLEDGE.keySet().removeIf(key -> key.battleId().equals(battleId));
        INTENTS.keySet().removeIf(key -> key.battleId().equals(battleId));
    }

    public static int activeBattleCount() { return OBSERVERS.size(); }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static final class MutableKnowledge {
        final Set<String> moves = ConcurrentHashMap.newKeySet();
        volatile String ability = "";
        volatile String item = "";
        RevealedPokemon snapshot() { return new RevealedPokemon(new LinkedHashSet<>(moves), ability, item); }
    }
}
