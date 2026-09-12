package vn.svframe.svarcade.runtime;

import java.util.Set;
import vn.svframe.svarcade.config.Node;

@FunctionalInterface
public interface SystemFactory {
    SessionSystem create(GenericSession session, Node config);
    default Set<SessionServices.Key<?>> provides() { return Set.of(); }
    default Set<SessionServices.Key<?>> requires() { return Set.of(); }
}
