package vn.svframe.lively.integration.cobblemon;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.PassActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.SwitchActionResponse;
import com.cobblemon.mod.common.battles.Targetable;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.combat.CombatCortex;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Cobblemon 1.7.3 BattleAI binding. It only reasons over battle-visible state plus Lively NPC memory. */
public final class LivelyCobblemonBattleAI implements BattleAI {
    private final UUID npcId;
    private final int skill;

    public LivelyCobblemonBattleAI(UUID npcId, int skill) {
        this.npcId = npcId;
        this.skill = Math.max(1, Math.min(5, skill));
    }

    @Override
    public ShowdownActionResponse choose(ActiveBattlePokemon active, PokemonBattle battle, BattleSide side,
                                         ShowdownMoveset moveset, boolean forceSwitch) {
        CandidateSet candidates = candidates(active, moveset, forceSwitch);
        if (candidates.responses().isEmpty()) return PassActionResponse.INSTANCE;

        CombatCortex.CombatState state = new CombatCortex.CombatState(
                battle.getTurn(), battle.getTurn(), aggression(), caution(),
                List.copyOf(candidates.actions()), features(active, side));
        Optional<CombatCortex.Decision> decision = LivelyApi.combat().choose(
                state, this::simulate, CombatCortex.SearchBudget.trainer(skill));
        if (decision.isEmpty()) return PassActionResponse.INSTANCE;
        rememberDecision(battle, decision.get());
        return candidates.responses().getOrDefault(decision.get().action().id(), PassActionResponse.INSTANCE);
    }

    private CandidateSet candidates(ActiveBattlePokemon active, ShowdownMoveset moveset, boolean forceSwitch) {
        List<CombatCortex.CombatAction> actions = new ArrayList<>();
        Map<String, ShowdownActionResponse> responses = new LinkedHashMap<>();
        addSwitches(active, moveset, forceSwitch, actions, responses);
        if (!forceSwitch && moveset != null) addMoves(active, moveset, actions, responses);
        return new CandidateSet(actions, responses);
    }

    private void addMoves(ActiveBattlePokemon active, ShowdownMoveset moveset,
                          List<CombatCortex.CombatAction> actions, Map<String, ShowdownActionResponse> responses) {
        for (InBattleMove move : moveset.getMoves()) {
            if (move == null || !move.canBeUsed()) continue;
            List<String> gimmicks = gimmicks(moveset);
            List<Targetable> targets = move.mustBeUsed() ? List.of() : move.getTargets(active);
            if (targets.isEmpty()) {
                for (String gimmick : gimmicks) addMoveCandidate(active, moveset, move, null, gimmick, actions, responses);
            } else {
                for (Targetable target : targets) {
                    if (target == null) continue;
                    for (String gimmick : gimmicks) addMoveCandidate(active, moveset, move, target, gimmick, actions, responses);
                }
            }
        }
    }

    private void addMoveCandidate(ActiveBattlePokemon active, ShowdownMoveset moveset, InBattleMove move,
                                  Targetable target, String gimmick, List<CombatCortex.CombatAction> actions,
                                  Map<String, ShowdownActionResponse> responses) {
        String targetPnx = target == null ? null : target.getPNX();
        MoveActionResponse response = new MoveActionResponse(move.getId(), targetPnx, gimmick);
        if (!response.isValid(active, moveset, false)) return;
        MoveTemplate template = Moves.getByNameOrDummy(move.getId());
        double value = moveValue(active, target, template, gimmick);
        double accuracy = normalizeAccuracy(template.getAccuracy());
        double risk = Math.max(0.02D, 1D - accuracy);
        String id = "move:" + move.getId() + ":" + (targetPnx == null ? "auto" : targetPnx) + ":" + (gimmick == null ? "none" : gimmick);
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("kind", "move"); metadata.put("move", move.getId());
        metadata.put("target", targetPnx == null ? "auto" : targetPnx);
        if (gimmick != null) metadata.put("gimmick", gimmick);
        actions.add(new CombatCortex.CombatAction(id, value, risk, metadata)); responses.put(id, response);
    }

    private void addSwitches(ActiveBattlePokemon active, ShowdownMoveset moveset, boolean forceSwitch,
                             List<CombatCortex.CombatAction> actions, Map<String, ShowdownActionResponse> responses) {
        if (!forceSwitch && moveset != null && moveset.getTrapped()) return;
        for (BattlePokemon reserve : active.getActor().getPokemonList()) {
            if (reserve == null || reserve == active.getBattlePokemon() || !reserve.canBeSentOut() || reserve.getWillBeSwitchedIn()) continue;
            SwitchActionResponse response = new SwitchActionResponse(reserve.getUuid());
            if (moveset != null && !response.isValid(active, moveset, forceSwitch)) continue;
            double hp = fraction(reserve.getHealth(), reserve.getMaxHealth());
            double currentHp = fraction(active.getBattlePokemon().getHealth(), active.getBattlePokemon().getMaxHealth());
            double matchup = reserveMatchup(reserve, active.getSide().getOppositeSide().getActivePokemon());
            double value = forceSwitch ? 0.42D + hp * 0.30D + matchup * 0.28D
                    : 0.12D + hp * 0.24D + (1D - currentHp) * 0.16D + matchup * 0.26D;
            String id = "switch:" + reserve.getUuid();
            actions.add(new CombatCortex.CombatAction(id, clamp01(value), 0.10D,
                    Map.of("kind", "switch", "pokemon", reserve.getUuid().toString(), "matchup", Double.toString(matchup))));
            responses.put(id, response);
        }
    }

    private List<String> gimmicks(ShowdownMoveset moveset) {
        List<String> result = new ArrayList<>(); result.add(null);
        if (moveset.getCanMegaEvo()) result.add(ShowdownMoveset.Gimmick.MEGA_EVOLUTION.getId());
        if (moveset.getCanUltraBurst()) result.add(ShowdownMoveset.Gimmick.ULTRA_BURST.getId());
        if (moveset.getCanDynamax()) result.add(ShowdownMoveset.Gimmick.DYNAMAX.getId());
        if (moveset.getCanTerastallize() != null) result.add(ShowdownMoveset.Gimmick.TERASTALLIZATION.getId());
        if (moveset.getCanZMove() != null && !moveset.getCanZMove().isEmpty()) result.add(ShowdownMoveset.Gimmick.Z_POWER.getId());
        return result;
    }

    private double moveValue(ActiveBattlePokemon active, Targetable target, MoveTemplate template, String gimmick) {
        double power = Math.max(0D, Math.min(250D, template.getPower())) / 250D;
        double accuracy = normalizeAccuracy(template.getAccuracy());
        double value = power > 0D ? 0.18D + power * 0.62D * accuracy : 0.28D;
        if (template.getPriority() > 0) value += Math.min(0.12D, template.getPriority() * 0.035D);
        if (hasStab(active, template.getElementalType())) value += 0.10D;
        if (target instanceof ActiveBattlePokemon targetPokemon) {
            double targetHp = fraction(targetPokemon.getBattlePokemon().getHealth(), targetPokemon.getBattlePokemon().getMaxHealth());
            double effectiveness = TypeMatchup.multiplier(template.getElementalType(), targetPokemon.getBattlePokemon().getEffectedPokemon().getTypes());
            if (power > 0D) {
                value += (1D - targetHp) * 0.12D;
                value += effectivenessBonus(effectiveness);
            }
        }
        if (gimmick != null) value += 0.09D;
        return clamp01(value);
    }

    private double reserveMatchup(BattlePokemon reserve, List<ActiveBattlePokemon> opponents) {
        if (opponents.isEmpty()) return 0.5D;
        double bestOffense = 1D;
        double worstIncoming = 1D;
        for (ActiveBattlePokemon opponent : opponents) {
            for (var move : reserve.getMoveSet().getMoves()) {
                if (move == null || move.getCurrentPp() <= 0) continue;
                bestOffense = Math.max(bestOffense, TypeMatchup.multiplier(move.getType(), opponent.getBattlePokemon().getEffectedPokemon().getTypes()));
            }
            for (ElementalType opponentType : opponent.getBattlePokemon().getEffectedPokemon().getTypes()) {
                worstIncoming = Math.max(worstIncoming, TypeMatchup.multiplier(opponentType, reserve.getEffectedPokemon().getTypes()));
            }
        }
        double offensiveScore = bestOffense >= 4D ? 1D : bestOffense >= 2D ? 0.8D : bestOffense < 1D ? 0.25D : 0.5D;
        double defensiveScore = worstIncoming >= 4D ? 0D : worstIncoming >= 2D ? 0.2D : worstIncoming < 1D ? 0.8D : 0.5D;
        return clamp01(offensiveScore * 0.62D + defensiveScore * 0.38D);
    }

    private static double effectivenessBonus(double multiplier) {
        if (multiplier <= 0D) return -0.70D;
        if (multiplier >= 4D) return 0.34D;
        if (multiplier >= 2D) return 0.22D;
        if (multiplier < 1D) return -0.18D;
        return 0D;
    }

    private boolean hasStab(ActiveBattlePokemon active, ElementalType moveType) {
        if (moveType == null) return false;
        for (ElementalType type : active.getBattlePokemon().getEffectedPokemon().getTypes()) {
            if (moveType.equals(type)) return true;
        }
        return false;
    }

    private List<CombatCortex.Outcome> simulate(CombatCortex.CombatState state, CombatCortex.CombatAction action) {
        double hit = Math.max(0.20D, 1D - action.risk());
        double successValue = action.immediateValue() * ("switch".equals(action.metadata().get("kind")) ? 0.45D : 0.70D);
        double failureValue = -Math.max(0.05D, action.risk() * 0.35D);
        CombatCortex.CombatState next = new CombatCortex.CombatState(
                state.revision(), state.turn() + 1, state.aggression(), state.caution(), state.legalActions(), state.features());
        return List.of(new CombatCortex.Outcome(successValue, hit, next),
                new CombatCortex.Outcome(failureValue, 1D - hit, next));
    }

    private void rememberDecision(PokemonBattle battle, CombatCortex.Decision decision) {
        if (LivelyApi.states() == null) return;
        LivelyApi.states().get(npcId).ifPresent(state -> state.remember(
                "battle_decision",
                Map.of(
                        "turn", Integer.toString(battle.getTurn()),
                        "action", decision.action().id(),
                        "budget_exhausted", Boolean.toString(decision.budgetExhausted())),
                0.30D, 1D));
    }

    private Map<String, Double> features(ActiveBattlePokemon active, BattleSide side) {
        double ownHp = fraction(active.getBattlePokemon().getHealth(), active.getBattlePokemon().getMaxHealth());
        double opponentHp = side.getOppositeSide().getActivePokemon().stream()
                .mapToDouble(p -> fraction(p.getBattlePokemon().getHealth(), p.getBattlePokemon().getMaxHealth()))
                .average().orElse(1D);
        long healthyReserve = active.getActor().getPokemonList().stream().filter(BattlePokemon::canBeSentOut).count();
        return Map.of("own_hp", ownHp, "opponent_hp", opponentHp, "healthy_reserve", (double) healthyReserve,
                "npc_skill", (double) skill);
    }

    private double aggression() {
        double brave = npcTrait("brave", 0.5D);
        double greedy = npcTrait("greedy", 0.5D);
        return clamp01(0.30D + skill * 0.09D + brave * 0.16D + greedy * 0.04D + recentOutcomeBalance() * 0.05D);
    }

    private double caution() {
        double brave = npcTrait("brave", 0.5D);
        double suspicious = npcTrait("suspicious", 0.5D);
        return clamp01(0.76D - skill * 0.07D - brave * 0.14D + suspicious * 0.10D - recentOutcomeBalance() * 0.04D);
    }

    private double recentOutcomeBalance() {
        if (LivelyApi.states() == null) return 0D;
        return LivelyApi.states().snapshot(npcId).map(snapshot -> {
            double sum = 0D; int count = 0;
            for (var memory : snapshot.recentMemories()) {
                if (memory.type().equals("battle_won")) { sum += 1D; count++; }
                else if (memory.type().equals("battle_lost")) { sum -= 1D; count++; }
                if (count >= 8) break;
            }
            return count == 0 ? 0D : Math.max(-1D, Math.min(1D, sum / count));
        }).orElse(0D);
    }

    private double npcTrait(String key, double fallback) {
        if (LivelyApi.states() == null) return fallback;
        return LivelyApi.states().snapshot(npcId).map(snapshot -> snapshot.trait(key)).orElse(fallback);
    }

    private static double normalizeAccuracy(double value) { if (value <= 0D) return 1D; return clamp01(value > 1D ? value / 100D : value); }
    private static double fraction(int value, int max) { return max <= 0 ? 0D : clamp01((double) value / (double) max); }
    private static double clamp01(double value) { return Math.max(0D, Math.min(1D, value)); }
    private record CandidateSet(List<CombatCortex.CombatAction> actions, Map<String, ShowdownActionResponse> responses) {}
}
