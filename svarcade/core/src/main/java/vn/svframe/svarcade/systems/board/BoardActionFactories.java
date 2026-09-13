package vn.svframe.svarcade.systems.board;

import java.util.*;
import java.util.function.BooleanSupplier;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.*;
import vn.svframe.svarcade.systems.turn.*;

/** Standard data-bound board action handlers; action IDs remain definition-authored. */
public final class BoardActionFactories {
    public static final Id MOVE = Id.of("svarcade:board_move");
    public static final Id COMMAND = Id.of("svarcade:board_command");
    private BoardActionFactories() { }

    public static Registry<ActionHandlerFactory> create() {
        return new Registry.Builder<ActionHandlerFactory>()
                .add(MOVE, new MoveFactory())
                .add(COMMAND, new CommandFactory())
                .build();
    }

    private record Common(Id effect, Set<String> states) {
        private Common { states = Set.copyOf(states); }
        static Common parse(Node n, String... extra) {
            Set<String> allowed = new LinkedHashSet<>(List.of("effect", "allowed_states")); allowed.addAll(List.of(extra));
            n.only(allowed.toArray(String[]::new));
            Set<String> states = n.has("allowed_states") ? n.strings("allowed_states") : Set.of();
            for (String state : states) if (!state.matches("[A-Za-z0-9_-]{1,80}")) throw n.error("allowed_states", "Invalid state identifier");
            return new Common(Id.of(n.string("effect")), states);
        }
        Set<Id> dependencies(Set<Id> base) {
            Set<Id> result = new LinkedHashSet<>(base); if (!states.isEmpty()) result.add(StateMachineSystem.ID); return Set.copyOf(result);
        }
        Set<SessionServices.Key<?>> requires(Set<SessionServices.Key<?>> base) {
            Set<SessionServices.Key<?>> result = new LinkedHashSet<>(base); if (!states.isEmpty()) result.add(StateMachineAccess.ACCESS); return Set.copyOf(result);
        }
        BooleanSupplier phase(GenericSession session) {
            if (states.isEmpty()) return () -> true;
            StateMachineAccess fsm = session.services().require(StateMachineAccess.ACCESS);
            return () -> states.contains(fsm.state());
        }
    }

    private static final class MoveFactory implements ActionHandlerFactory {
        private static final Set<Id> DEPS = Set.of(BoardSystem.ID, TurnSystem.ID, BoardAdjudicationSystem.ID);
        private static final Set<SessionServices.Key<?>> REQUIRES = Set.of(BoardSystem.ACCESS, TurnSystem.ACCESS, BoardAdjudicationSystem.ACCESS);
        @Override public void validate(Node config) { Common.parse(config); }
        @Override public Set<Id> dependencies(Node config) { return Common.parse(config).dependencies(DEPS); }
        @Override public Set<SessionServices.Key<?>> requires(Node config) { return Common.parse(config).requires(REQUIRES); }
        @Override public ActionDispatcher.Handler create(GenericSession session, Node config) {
            Common common = Common.parse(config);
            return new BoardMoveHandler(session.services().require(BoardSystem.ACCESS), session.services().require(TurnSystem.ACCESS),
                    session.services().require(BoardAdjudicationSystem.ACCESS), common.phase(session), common.effect());
        }
    }

    private static final class CommandFactory implements ActionHandlerFactory {
        private record Parsed(Common common, AdjudicationAccess.Command command) { }
        private static final Set<Id> DEPS = Set.of(BoardSystem.ID, BoardAdjudicationSystem.ID);
        private static final Set<SessionServices.Key<?>> REQUIRES = Set.of(BoardSystem.ACCESS, BoardAdjudicationSystem.ACCESS);
        private Parsed parse(Node config) {
            Common common = Common.parse(config, "command");
            try { return new Parsed(common, AdjudicationAccess.Command.valueOf(config.string("command").toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException e) { throw config.error("command", "Unknown board command"); }
        }
        @Override public void validate(Node config) { parse(config); }
        @Override public Set<Id> dependencies(Node config) { return parse(config).common().dependencies(DEPS); }
        @Override public Set<SessionServices.Key<?>> requires(Node config) { return parse(config).common().requires(REQUIRES); }
        @Override public ActionDispatcher.Handler create(GenericSession session, Node config) {
            Parsed parsed = parse(config);
            return new BoardCommandHandler(session.services().require(BoardAdjudicationSystem.ACCESS), parsed.command(),
                    session.services().require(BoardSystem.ACCESS), parsed.common().phase(session), parsed.common().effect());
        }
    }
}
