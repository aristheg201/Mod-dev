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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Cobblemon 1.7.3 BattleAI binding. It reasons only over battle-visible state and legitimately revealed memory. */
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
        CobblemonBattleKnowledge.register(npcId, battle, active.getActor());
        CandidateSet raw = candidates(active, battle, moveset, forceSwitch);
        CandidateSet candidates = coordinated(active, battle, raw);
        if (candidates.responses().isEmpty()) return PassActionResponse.INSTANCE;

        var snapshot = LivelyApi.states() == null ? null : LivelyApi.states().snapshot(npcId).orElse(null);
        CombatCortex.CombatState state = new CombatCortex.CombatState(
                battle.getTurn(), battle.getTurn(), aggression(snapshot), caution(snapshot),
                List.copyOf(candidates.actions()), features(active, side));
        Optional<CombatCortex.Decision> decision = LivelyApi.combat().choose(
                state, this::simulate, CombatCortex.SearchBudget.trainer(skill));
        if (decision.isEmpty()) return PassActionResponse.INSTANCE;
        rememberDecision(battle, decision.get());
        CobblemonBattleKnowledge.rememberIntent(battle, active, decision.get().action());
        return candidates.responses().getOrDefault(decision.get().action().id(), PassActionResponse.INSTANCE);
    }

    private CandidateSet coordinated(ActiveBattlePokemon active, PokemonBattle battle, CandidateSet raw) {
        List<CombatCortex.CombatAction> actions = new ArrayList<>();
        Map<String, ShowdownActionResponse> responses = new LinkedHashMap<>();
        for (CombatCortex.CombatAction action : raw.actions()) {
            double penalty = CobblemonBattleKnowledge.coordinationPenalty(npcId, battle, active, action);
            if (penalty >= 0.99D) continue;
            double knowledgeFactor = revealedKnowledgeFactor(battle, action);
            if (knowledgeFactor <= 0.01D) continue;
            double value = clamp01(action.immediateValue() * (1D - penalty) * knowledgeFactor);
            Map<String, String> metadata = new LinkedHashMap<>(action.metadata());
            if (penalty > 0D) metadata.put("coordination_penalty", Double.toString(penalty));
            if (Math.abs(knowledgeFactor - 1D) > .001D) metadata.put("revealed_knowledge_factor", Double.toString(knowledgeFactor));
            actions.add(new CombatCortex.CombatAction(action.id(), value, action.risk(), metadata));
            responses.put(action.id(), raw.responses().get(action.id()));
        }
        return new CandidateSet(actions, responses);
    }

    private CandidateSet candidates(ActiveBattlePokemon active, PokemonBattle battle, ShowdownMoveset moveset, boolean forceSwitch) {
        List<CombatCortex.CombatAction> actions = new ArrayList<>();
        Map<String, ShowdownActionResponse> responses = new LinkedHashMap<>();
        addSwitches(active, battle, moveset, forceSwitch, actions, responses);
        if (!forceSwitch && moveset != null) addMoves(active, battle, moveset, actions, responses);
        return new CandidateSet(actions, responses);
    }

    private void addMoves(ActiveBattlePokemon active, PokemonBattle battle, ShowdownMoveset moveset,
                          List<CombatCortex.CombatAction> actions, Map<String, ShowdownActionResponse> responses) {
        for (InBattleMove move : moveset.getMoves()) {
            if (move == null || !move.canBeUsed()) continue;
            List<String> gimmicks = gimmicks(moveset);
            List<Targetable> targets = move.mustBeUsed() ? List.of() : move.getTargets(active);
            if (targets.isEmpty()) {
                for (String gimmick : gimmicks) addMoveCandidate(active, battle, moveset, move, null, gimmick, actions, responses);
            } else {
                for (Targetable target : targets) {
                    if (target == null) continue;
                    for (String gimmick : gimmicks) addMoveCandidate(active, battle, moveset, move, target, gimmick, actions, responses);
                }
            }
        }
    }

    private void addMoveCandidate(ActiveBattlePokemon active, PokemonBattle battle, ShowdownMoveset moveset, InBattleMove move,
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
        metadata.put("kind", "move");
        metadata.put("move", move.getId());
        metadata.put("target", targetPnx == null ? "auto" : targetPnx);
        if (template.getElementalType() != null) metadata.put("move_type", normalizeMove(template.getElementalType().getName()));
        if (template.getDamageCategory() != null) metadata.put("category", normalizeMove(template.getDamageCategory().getName()));
        metadata.put("power", Double.toString(Math.max(0D, template.getPower())));
        if (target instanceof ActiveBattlePokemon targetPokemon) {
            metadata.put("target_pokemon", targetPokemon.getBattlePokemon().getUuid().toString());
            double hp = fraction(targetPokemon.getBattlePokemon().getHealth(), targetPokemon.getBattlePokemon().getMaxHealth());
            metadata.put("target_hp", Double.toString(hp));
            double effectiveness = TypeMatchup.multiplier(template.getElementalType(), targetPokemon.getBattlePokemon().getEffectedPokemon().getTypes());
            metadata.put("effectiveness", Double.toString(effectiveness));
        }
        if (gimmick != null) metadata.put("gimmick", gimmick);
        actions.add(new CombatCortex.CombatAction(id, value, risk, metadata));
        responses.put(id, response);
    }

    private void addSwitches(ActiveBattlePokemon active, PokemonBattle battle, ShowdownMoveset moveset, boolean forceSwitch,
                             List<CombatCortex.CombatAction> actions, Map<String, ShowdownActionResponse> responses) {
        if (!forceSwitch && moveset != null && moveset.getTrapped()) return;
        for (BattlePokemon reserve : active.getActor().getPokemonList()) {
            if (reserve == null || reserve == active.getBattlePokemon() || !reserve.canBeSentOut() || reserve.getWillBeSwitchedIn()) continue;
            SwitchActionResponse response = new SwitchActionResponse(reserve.getUuid());
            if (moveset != null && !response.isValid(active, moveset, forceSwitch)) continue;
            double hp = fraction(reserve.getHealth(), reserve.getMaxHealth());
            double currentHp = fraction(active.getBattlePokemon().getHealth(), active.getBattlePokemon().getMaxHealth());
            double matchup = reserveMatchup(reserve, active.getSide().getOppositeSide().getActivePokemon(), battle.getBattleId());
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
        double value = power > 0D ? 0.18D + power * 0.62D * accuracy : statusUtility(template, active);
        if (template.getPriority() > 0) value += Math.min(0.12D, template.getPriority() * 0.035D);
        if (hasStab(active, template.getElementalType()) && power > 0D) value += 0.10D;
        double effectiveness = 1D;
        double targetHp = 1D;
        if (target instanceof ActiveBattlePokemon targetPokemon) {
            targetHp = fraction(targetPokemon.getBattlePokemon().getHealth(), targetPokemon.getBattlePokemon().getMaxHealth());
            effectiveness = TypeMatchup.multiplier(template.getElementalType(), targetPokemon.getBattlePokemon().getEffectedPokemon().getTypes());
            if (power > 0D) {
                value += (1D - targetHp) * 0.12D;
                value += effectivenessBonus(effectiveness);
            }
        }
        if (gimmick != null) value += gimmickTiming(active, targetHp, effectiveness, power);
        return clamp01(value);
    }

    private double revealedKnowledgeFactor(PokemonBattle battle, CombatCortex.CombatAction action) {
        if (!"move".equals(action.metadata().get("kind"))) return 1D;
        String rawTarget = action.metadata().get("target_pokemon");
        String rawMove = action.metadata().get("move");
        if (rawTarget == null || rawMove == null) return 1D;
        UUID targetId;
        try { targetId = UUID.fromString(rawTarget); }
        catch (IllegalArgumentException ignored) { return 1D; }

        CobblemonBattleKnowledge.RevealedPokemon known = CobblemonBattleKnowledge.known(npcId, battle.getBattleId(), targetId);
        if (known.moves().isEmpty() && known.ability().isBlank() && known.item().isBlank()) return 1D;
        MoveTemplate template = Moves.getByNameOrDummy(rawMove);
        double power = Math.max(0D, template.getPower());
        if (power <= 0D || template.getElementalType() == null) return 1D;

        String type = normalizeMove(template.getElementalType().getName());
        String ability = normalizeMove(known.ability());
        String item = normalizeMove(known.item());
        double effectiveness = number(action.metadata().get("effectiveness"), 1D);
        double targetHp = number(action.metadata().get("target_hp"), 1D);

        if (knownImmunity(type, ability, item)) return 0.015D;
        if (ability.endsWith("wonderguard") && effectiveness <= 1D) return 0.03D;

        double factor = 1D;
        String category = action.metadata().getOrDefault("category", "");
        if (item.endsWith("assaultvest") && category.equals("special")) factor *= .88D;
        if ((item.endsWith("focussash") || ability.endsWith("sturdy")) && targetHp >= .995D) factor *= .92D;
        if (item.endsWith("leftovers") || item.endsWith("blacksludge")) factor *= 1.025D;
        return Math.max(.01D, Math.min(1.15D, factor));
    }

    private static boolean knownImmunity(String type, String ability, String item) {
        return switch (type) {
            case "ground" -> ability.endsWith("levitate") || ability.endsWith("eartheater") || item.endsWith("airballoon");
            case "water" -> ability.endsWith("waterabsorb") || ability.endsWith("stormdrain") || ability.endsWith("dryskin");
            case "fire" -> ability.endsWith("flashfire") || ability.endsWith("wellbakedbody");
            case "electric" -> ability.endsWith("voltabsorb") || ability.endsWith("lightningrod") || ability.endsWith("motordrive");
            case "grass" -> ability.endsWith("sapsipper");
            default -> false;
        };
    }

    private double statusUtility(MoveTemplate template, ActiveBattlePokemon active) {
        String name = normalizeMove(template.getName());
        double ownHp = fraction(active.getBattlePokemon().getHealth(), active.getBattlePokemon().getMaxHealth());
        if (Set.of("recover", "roost", "slackoff", "softboiled", "milkdrink", "synthesis", "moonlight", "morningsun", "rest").contains(name)) {
            return clamp01(0.18D + (1D - ownHp) * 0.72D);
        }
        if (Set.of("protect", "detect", "kingsshield", "spikyshield", "banefulbunker").contains(name)) return 0.42D;
        if (Set.of("swordsdance", "nastyplot", "dragondance", "calmmind", "bulkup", "quiverdance", "shellsmash").contains(name)) {
            return ownHp > 0.55D ? 0.54D : 0.30D;
        }
        if (Set.of("stealthrock", "spikes", "toxicspikes", "stickyweb", "tailwind", "trickroom", "reflect", "lightscreen", "auroraveil").contains(name)) return 0.50D;
        Double[] chances = template.getEffectChances();
        double chance = 0D;
        if (chances != null) for (Double c : chances) if (c != null) chance = Math.max(chance, c > 1D ? c / 100D : c);
        return clamp01(0.30D + Math.min(0.18D, chance * 0.18D));
    }

    private double gimmickTiming(ActiveBattlePokemon active, double targetHp, double effectiveness, double power) {
        double ownHp = fraction(active.getBattlePokemon().getHealth(), active.getBattlePokemon().getMaxHealth());
        double value = 0.015D;
        if (ownHp < 0.42D) value += 0.07D;
        if (targetHp > 0.45D && power > 0.25D) value += 0.035D;
        if (effectiveness >= 2D) value += 0.055D;
        if (targetHp < 0.15D && power > 0.35D) value -= 0.055D;
        return value;
    }

    private double reserveMatchup(BattlePokemon reserve, List<ActiveBattlePokemon> opponents, UUID battleId) {
        if (opponents.isEmpty()) return 0.5D;
        double bestOffense = 1D;
        double worstIncoming = 1D;
        for (ActiveBattlePokemon opponent : opponents) {
            for (var move : reserve.getMoveSet().getMoves()) {
                if (move == null || move.getCurrentPp() <= 0) continue;
                bestOffense = Math.max(bestOffense, TypeMatchup.multiplier(move.getType(), opponent.getBattlePokemon().getEffectedPokemon().getTypes()));
            }
            CobblemonBattleKnowledge.RevealedPokemon known = CobblemonBattleKnowledge.known(npcId, battleId, opponent.getBattlePokemon().getUuid());
            if (!known.moves().isEmpty()) {
                for (String moveName : known.moves()) {
                    MoveTemplate template = Moves.getByNameOrDummy(moveName);
                    worstIncoming = Math.max(worstIncoming,
                            TypeMatchup.multiplier(template.getElementalType(), reserve.getEffectedPokemon().getTypes()));
                }
            } else {
                for (ElementalType opponentType : opponent.getBattlePokemon().getEffectedPokemon().getTypes()) {
                    worstIncoming = Math.max(worstIncoming, TypeMatchup.multiplier(opponentType, reserve.getEffectedPokemon().getTypes()));
                }
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
        for (ElementalType type : active.getBattlePokemon().getEffectedPokemon().getTypes()) if (moveType.equals(type)) return true;
        return false;
    }

    private List<CombatCortex.Outcome> simulate(CombatCortex.CombatState state, CombatCortex.CombatAction action) {
        double hit = Math.max(0.20D, 1D - action.risk());
        double successValue = action.immediateValue() * ("switch".equals(action.metadata().get("kind")) ? 0.45D : 0.70D);
        double failureValue = -Math.max(0.05D, action.risk() * 0.35D);
        CombatCortex.CombatState next = new CombatCortex.CombatState(
                state.revision(), state.turn() + 1, state.aggression(), state.caution(), state.legalActions(), state.features());
        return List.of(new CombatCortex.Outcome(successValue, hit, next), new CombatCortex.Outcome(failureValue, 1D - hit, next));
    }

    private void rememberDecision(PokemonBattle battle, CombatCortex.Decision decision) {
        if (LivelyApi.states() == null) return;
        LivelyApi.states().get(npcId).ifPresent(state -> state.remember("battle_decision",
                Map.of("turn", Integer.toString(battle.getTurn()), "action", decision.action().id(),
                        "budget_exhausted", Boolean.toString(decision.budgetExhausted())), 0.30D, 1D));
    }

    private Map<String, Double> features(ActiveBattlePokemon active, BattleSide side) {
        double ownHp = fraction(active.getBattlePokemon().getHealth(), active.getBattlePokemon().getMaxHealth());
        double opponentHp = side.getOppositeSide().getActivePokemon().stream()
                .mapToDouble(p -> fraction(p.getBattlePokemon().getHealth(), p.getBattlePokemon().getMaxHealth())).average().orElse(1D);
        long healthyReserve = active.getActor().getPokemonList().stream().filter(BattlePokemon::canBeSentOut).count();
        return Map.of("own_hp", ownHp, "opponent_hp", opponentHp, "healthy_reserve", (double) healthyReserve, "npc_skill", (double) skill);
    }

    private double aggression(vn.svframe.lively.model.NpcSnapshot snapshot) {
        double brave = snapshot == null ? 0.5D : snapshot.trait("brave");
        double greedy = snapshot == null ? 0.5D : snapshot.trait("greedy");
        return clamp01(0.30D + skill * 0.09D + brave * 0.16D + greedy * 0.04D + recentOutcomeBalance(snapshot) * 0.05D);
    }

    private double caution(vn.svframe.lively.model.NpcSnapshot snapshot) {
        double brave = snapshot == null ? 0.5D : snapshot.trait("brave");
        double suspicious = snapshot == null ? 0.5D : snapshot.trait("suspicious");
        return clamp01(0.76D - skill * 0.07D - brave * 0.14D + suspicious * 0.10D - recentOutcomeBalance(snapshot) * 0.04D);
    }

    private double recentOutcomeBalance(vn.svframe.lively.model.NpcSnapshot snapshot) {
        if (snapshot == null) return 0D;
        double sum = 0D; int count = 0;
        for (var memory : snapshot.recentMemories()) {
            if (memory.type().equals("battle_won")) { sum += 1D; count++; }
            else if (memory.type().equals("battle_lost")) { sum -= 1D; count++; }
            if (count >= 8) break;
        }
        return count == 0 ? 0D : Math.max(-1D, Math.min(1D, sum / count));
    }

    private static double number(String raw, double fallback) {
        try { return raw == null ? fallback : Double.parseDouble(raw); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private static String normalizeMove(String name) { return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    private static double normalizeAccuracy(double value) { if (value <= 0D) return 1D; return clamp01(value > 1D ? value / 100D : value); }
    private static double fraction(int value, int max) { return max <= 0 ? 0D : clamp01((double) value / (double) max); }
    private static double clamp01(double value) { return Math.max(0D, Math.min(1D, value)); }
    private record CandidateSet(List<CombatCortex.CombatAction> actions, Map<String, ShowdownActionResponse> responses) {}
}
