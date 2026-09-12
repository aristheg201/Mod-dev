package vn.svframe.svarcade.systems.board;

import java.util.*;
import java.util.function.BooleanSupplier;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.*;

/** Definition-registered draw/resign/claim action; bots and humans use the same handler. */
public final class BoardCommandHandler implements ActionDispatcher.Handler {
    private final AdjudicationAccess adjudication;
    private final AdjudicationAccess.Command command;
    private final BoardAccess board;
    private final BooleanSupplier phaseAllows;
    private final Id effect;
    public BoardCommandHandler(AdjudicationAccess adjudication,AdjudicationAccess.Command command,BoardAccess board,BooleanSupplier phaseAllows,Id effect) {
        this.adjudication=Objects.requireNonNull(adjudication); this.command=Objects.requireNonNull(command); this.board=Objects.requireNonNull(board);
        this.phaseAllows=Objects.requireNonNull(phaseAllows); this.effect=Objects.requireNonNull(effect);
    }
    @Override public Optional<String> reject(GenericSession session,IntentGate.Facts facts,IntentGate.Intent intent) {
        if (!phaseAllows.getAsBoolean()) return Optional.of("phase");
        return adjudication.mayPlay() ? Optional.empty() : Optional.of("result_or_clock");
    }
    @Override public ActionDispatcher.Prepared prepare(GenericSession session,IntentGate.Facts facts,IntentGate.Intent intent) {
        Node n=new Node(intent.payload(),"board-command"); n.only("move");
        Optional<MovementRules.Move> intended=n.has("move") ? Optional.of(BoardMoveHandler.decode(n.node("move").values(),board.position().size())) : Optional.empty();
        StateChange change=new CompositeChange(session.thread(),adjudication.prepareCommand(facts.actor(),command,intended));
        return new ActionDispatcher.Prepared(change::apply,change::rollback,List.of(new ActionDispatcher.Effect(effect,Map.of("command",command.name()))));
    }
}
