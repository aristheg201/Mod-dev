package vn.svframe.svarcade.systems.board;

import java.util.*;
import java.util.function.Function;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.board.MovementRules.Move;
import vn.svframe.svarcade.systems.objective.*;
import vn.svframe.svarcade.systems.objective.ObjectiveAccess.Result;
import vn.svframe.svarcade.systems.turn.*;

/** Composes board predicates, offers and clock expiry into transactional generic outcomes. */
public final class BoardAdjudicationSystem implements SessionSystem, AdjudicationAccess {
    public static final Id ID=Id.of("svarcade:board_adjudication");
    public static final SessionServices.Key<AdjudicationAccess> ACCESS=new SessionServices.Key<>(ID,AdjudicationAccess.class);
    public static final class Plan implements SystemSchema,SystemFactory {
        @Override public void validate(Node config) { BoardOutcomeRules.Config.parse(config); }
        @Override public Set<Id> dependencies() { return Set.of(BoardSystem.ID,MovementSystem.ID,ObjectiveSystem.ID,TurnSystem.ID); }
        @Override public Set<SessionServices.Key<?>> requires() { return Set.of(BoardSystem.ACCESS,MovementSystem.ACCESS,ObjectiveSystem.ACCESS,TurnSystem.ACCESS); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(ACCESS); }
        @Override public SessionSystem create(GenericSession session,Node config) {
            BoardAdjudicationSystem system=new BoardAdjudicationSystem(BoardOutcomeRules.Config.parse(config),
                    session.services().require(BoardSystem.ACCESS),session.services().require(MovementSystem.ACCESS),
                    session.services().require(ObjectiveSystem.ACCESS),session.services().require(TurnSystem.ACCESS),
                    session.thread(),actor -> session.participants().get(actor),session::changed);
            session.services().provide(ACCESS,system); return system;
        }
    }
    private record OfferState(String offeredBy,long boardRevision) { }
    private final BoardAccess board;
    private final MovementAccess movement;
    private final ObjectiveAccess objective;
    private final TurnAccess clock;
    private final ThreadGuard thread;
    private final Function<UUID,Participant> participants;
    private final Runnable changed;
    private final BoardOutcomeRules outcome;
    private OfferState state;
    private long revision;
    private boolean active,closed;
    public BoardAdjudicationSystem(BoardOutcomeRules.Config config,BoardAccess board,MovementAccess movement,ObjectiveAccess objective,
                                   TurnAccess clock,ThreadGuard thread,Function<UUID,Participant> participants,Runnable changed) {
        this.board=Objects.requireNonNull(board); this.movement=Objects.requireNonNull(movement); this.objective=Objects.requireNonNull(objective);
        this.clock=Objects.requireNonNull(clock); this.thread=Objects.requireNonNull(thread); this.participants=Objects.requireNonNull(participants); this.changed=Objects.requireNonNull(changed);
        outcome=new BoardOutcomeRules(config,movement.definition());
        if (!objective.reasons().containsAll(config.reasons().values())) throw new ConfigException("Adjudication references an unregistered outcome reason");
    }
    private void requireActive() { thread.check(); if (!active) throw new IllegalStateException("Board adjudication inactive"); }
    private String team(UUID actor) {
        Participant p=participants.apply(actor);
        if (p==null || p.kind()==Participant.Kind.SPECTATOR || !movement.definition().teams().contains(p.team())) throw new IllegalArgumentException("Invalid adjudication participant");
        return p.team();
    }
    private void alignment() {
        if (!clock.team().equals(board.position().turn())) throw new ConfigException("Board and clock active teams differ");
    }
    @Override public void start() {
        thread.check(); if (active || closed) throw new IllegalStateException("Adjudication already started/closed");
        alignment(); state=new OfferState(null,-1); active=true; settle();
    }
    @Override public boolean mayPlay() { requireActive(); return objective.result().isEmpty() && clock.running() && !clock.expired(); }
    @Override public Optional<String> offeredBy() { requireActive(); return Optional.ofNullable(state.offeredBy()); }
    @Override public BoardOutcomeRules.Config rules() { requireActive(); return outcome.config(); }
    private StateChange stateChange(String offer,long observedBoardRevision) {
        OfferState before=state,after=new OfferState(offer,observedBoardRevision); long expected=revision;
        if (expected==Long.MAX_VALUE) throw new IllegalStateException("Adjudication revision exhausted");
        return new StateChange() {
            private int status;
            @Override public void apply() {
                requireActive(); if (status!=0 || state!=before || revision!=expected || board.revision()!=observedBoardRevision) throw new IllegalStateException("Stale adjudication transaction");
                state=after; revision++; status=1;
            }
            @Override public void rollback() {
                requireActive(); if (status!=1 || state!=after || revision!=expected+1) throw new IllegalStateException("Adjudication rollback conflict");
                state=before; revision=expected; status=2;
            }
        };
    }
    @Override public MovePlan prepareAfterMove(UUID actor,GridPosition next) {
        requireActive(); if (!mayPlay()) throw new IllegalStateException("Match cannot accept moves");
        alignment(); String movingTeam=team(actor);
        if (!movingTeam.equals(board.position().turn()) || next.ply()!=Math.incrementExact(board.position().ply())) throw new IllegalArgumentException("Invalid adjudication move candidate");
        int repetitions=Math.incrementExact(board.repetitions(movement.repetitionKey(next)));
        Optional<Result> result=outcome.automatic(next,repetitions); List<StateChange> changes=new ArrayList<>();
        String offer=state.offeredBy(); if (result.isPresent() || offer!=null && !offer.equals(movingTeam)) offer=null;
        changes.add(stateChange(offer,Math.incrementExact(board.revision())));
        result.ifPresent(value -> changes.add(objective.prepare(Map.of(),Optional.of(value))));
        return new MovePlan(changes,result.isPresent());
    }
    @Override public List<StateChange> prepareCommand(UUID actor,Command command,Optional<Move> intended) {
        requireActive(); Objects.requireNonNull(command); Objects.requireNonNull(intended);
        if (!mayPlay()) throw new IllegalStateException("Match cannot accept commands"); alignment(); String actorTeam=team(actor);
        if (intended.isPresent() && command!=Command.CLAIM_REPETITION && command!=Command.CLAIM_QUIET) throw new IllegalArgumentException("Unexpected intended move");
        if (command==Command.OFFER_DRAW) {
            if (!outcome.config().allowDrawOffers() || state.offeredBy()!=null) throw new IllegalArgumentException("Draw offer unavailable");
            return List.of(stateChange(actorTeam,board.revision()));
        }
        if (command==Command.ACCEPT_DRAW || command==Command.DECLINE_DRAW) {
            if (!outcome.config().allowDrawOffers() || state.offeredBy()==null || state.offeredBy().equals(actorTeam)) throw new IllegalArgumentException("No opponent draw offer");
            if (command==Command.DECLINE_DRAW) return List.of(stateChange(null,board.revision()));
            return finish(outcome.draw(BoardOutcomeRules.Cause.AGREEMENT),true);
        }
        if (command==Command.RESIGN) {
            if (!outcome.config().allowResign()) throw new IllegalArgumentException("Resignation unavailable");
            return finish(outcome.loss(board.position(),actorTeam,false),true);
        }
        if (!actorTeam.equals(board.position().turn())) throw new IllegalArgumentException("Only the active team may claim");
        GridPosition position=board.position(); int repetitions=board.repetitions();
        if (intended.isPresent()) { position=movement.apply(position,intended.get()); repetitions=Math.incrementExact(board.repetitions(movement.repetitionKey(position))); }
        BoardOutcomeRules.Claim claim=command==Command.CLAIM_REPETITION ? BoardOutcomeRules.Claim.REPETITION : BoardOutcomeRules.Claim.QUIET;
        Result result=outcome.claim(position,repetitions,claim).orElseThrow(() -> new IllegalArgumentException("Draw claim is not satisfied"));
        return finish(result,true);
    }
    private List<StateChange> finish(Result result,boolean requireTime) {
        return List.of(clock.prepareRunning(false,requireTime),stateChange(null,board.revision()),objective.prepare(Map.of(),Optional.of(result)));
    }
    private void applyAutomatic(List<StateChange> changes) {
        CompositeChange transaction=new CompositeChange(thread,changes);
        try { transaction.apply(); }
        catch (RuntimeException failure) {
            try { transaction.rollback(); } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
        changed.run();
    }
    private void settle() {
        alignment();
        if (objective.result().isPresent()) {
            if (clock.running()) throw new ConfigException("Finished match still has a running clock");
            return;
        }
        if (state.boardRevision()!=board.revision()) {
            Optional<Result> result=outcome.automatic(board.position(),board.repetitions());
            if (result.isPresent()) { applyAutomatic(finish(result.get(),false)); return; }
            // Normal moves update offers transactionally. Unobserved internal mutations invalidate offers.
            state=new OfferState(null,board.revision());
        }
        if (clock.expired()) applyAutomatic(finish(outcome.loss(board.position(),clock.team(),true),false));
    }
    @Override public void tick(long tick) { requireActive(); settle(); }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String,Object> snapshot() {
        requireActive(); Map<String,Object> data=new LinkedHashMap<>(Map.of("revision",revision,"board_revision",state.boardRevision()));
        if (state.offeredBy()!=null) data.put("offer",state.offeredBy()); return Map.copyOf(data);
    }
    @Override public void restore(int schema,Map<String,Object> saved) {
        thread.check(); if (active || closed || schema!=1) throw new IllegalArgumentException("Invalid adjudication restore");
        Node n=new Node(saved,"adjudication-state"); n.only("revision","board_revision","offer");
        long restored=n.integer("revision",0,Long.MAX_VALUE-1), observed=n.integer("board_revision",0,Long.MAX_VALUE-1);
        String offer=n.has("offer") ? n.string("offer") : null;
        if (observed!=board.revision() || offer!=null && (!outcome.config().allowDrawOffers() || !movement.definition().teams().contains(offer) || objective.result().isPresent())) throw new ConfigException("Persisted adjudication/board mismatch");
        alignment(); if (objective.result().isPresent() && clock.running()) throw new ConfigException("Persisted finished clock is running");
        state=new OfferState(offer,observed); revision=restored; active=true;
    }
    @Override public void close() { thread.check(); active=false; closed=true; state=null; }
}
