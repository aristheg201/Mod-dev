package vn.svframe.svarcade.systems.board;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.systems.objective.ObjectiveAccess.Result;

/** Pure outcome predicates over generic board state; thresholds and material proofs are data. */
public final class BoardOutcomeRules {
    public enum Claim { REPETITION, QUIET }
    public enum Cause { IMMOBILE_THREATENED, IMMOBILE_SAFE, MATERIAL, REPETITION_CLAIM, REPETITION_AUTO,
        QUIET_CLAIM, QUIET_AUTO, AGREEMENT, RESIGN, RESIGN_DRAW, TIMEOUT, TIMEOUT_DRAW }
    public record Config(int repetitionClaim, int repetitionAutomatic, long quietClaim, long quietAutomatic,
                         boolean adjudicateImmobility, boolean allowDrawOffers, boolean allowResign,
                         Map<Cause, Id> reasons, MaterialRules.Config material) {
        public Config {
            reasons=Map.copyOf(reasons); Objects.requireNonNull(material);
            if (repetitionClaim<0 || repetitionClaim>1000 || repetitionAutomatic<0 || repetitionAutomatic>1000 || quietClaim<0 || quietAutomatic<0
                    || repetitionAutomatic>0 && repetitionClaim>repetitionAutomatic || quietAutomatic>0 && quietClaim>quietAutomatic
                    || !reasons.keySet().equals(Set.of(Cause.values()))) throw new ConfigException("Invalid adjudication limits or reasons");
        }
        public static Config parse(Node n) {
            n.only("repetition_claim", "repetition_automatic", "quiet_claim_plies", "quiet_automatic_plies", "adjudicate_immobility", "allow_draw_offers", "allow_resign", "reasons", "material");
            Node values=n.node("reasons"); Map<Cause,Id> reasons=new EnumMap<>(Cause.class);
            for (String key : values.values().keySet()) reasons.put(Cause.valueOf(key),Id.of(values.string(key)));
            return new Config((int)n.integer("repetition_claim",0,1000),(int)n.integer("repetition_automatic",0,1000),
                    n.integer("quiet_claim_plies",0,Long.MAX_VALUE),n.integer("quiet_automatic_plies",0,Long.MAX_VALUE),
                    n.bool("adjudicate_immobility",true),n.bool("allow_draw_offers",true),n.bool("allow_resign",true),reasons,MaterialRules.Config.parse(n.node("material")));
        }
    }
    private final Config config;
    private final MovementRules movement;
    private final MaterialRules material;
    public BoardOutcomeRules(Config config, MovementDefinition definition) {
        this.config=Objects.requireNonNull(config); movement=new MovementRules(definition); material=new MaterialRules(definition,config.material());
    }
    public Config config() { return config; }
    public Result draw(Cause cause) { return new Result(config.reasons().get(cause),Set.of(),true); }
    public Optional<Result> automatic(GridPosition position, int occurrences) {
        if (occurrences<1) throw new IllegalArgumentException("Position occurrence count");
        boolean immobile=config.adjudicateImmobility() && movement.successors(position).isEmpty();
        if (immobile && movement.threatened(position,position.turn())) return Optional.of(new Result(config.reasons().get(Cause.IMMOBILE_THREATENED),opponents(position.turn()),false));
        if (material.insufficientForAll(position)) return Optional.of(draw(Cause.MATERIAL));
        if (immobile) return Optional.of(draw(Cause.IMMOBILE_SAFE));
        if (config.quietAutomatic()>0 && position.quietPlies()>=config.quietAutomatic()) return Optional.of(draw(Cause.QUIET_AUTO));
        if (config.repetitionAutomatic()>0 && occurrences>=config.repetitionAutomatic()) return Optional.of(draw(Cause.REPETITION_AUTO));
        return Optional.empty();
    }
    private Set<String> opponents(String team) {
        if (!movement.definition().teams().contains(team)) throw new IllegalArgumentException("Unknown result team");
        Set<String> winners=new LinkedHashSet<>(movement.definition().teams()); winners.remove(team); return Set.copyOf(winners);
    }
    public Optional<Result> claim(GridPosition position, int occurrences, Claim claim) {
        if (automatic(position,occurrences).isPresent()) return Optional.empty();
        return switch (claim) {
            case REPETITION -> config.repetitionClaim()>0 && occurrences>=config.repetitionClaim() ? Optional.of(draw(Cause.REPETITION_CLAIM)) : Optional.empty();
            case QUIET -> config.quietClaim()>0 && position.quietPlies()>=config.quietClaim() ? Optional.of(draw(Cause.QUIET_CLAIM)) : Optional.empty();
        };
    }
    public Result loss(GridPosition position, String loser, boolean timeout) {
        Set<String> candidates=opponents(loser); boolean possible=false;
        for (String team : candidates) if (!material.insufficient(position,team)) { possible=true; break; }
        if (!possible) return draw(timeout ? Cause.TIMEOUT_DRAW : Cause.RESIGN_DRAW);
        return new Result(config.reasons().get(timeout ? Cause.TIMEOUT : Cause.RESIGN),candidates,false);
    }
}
