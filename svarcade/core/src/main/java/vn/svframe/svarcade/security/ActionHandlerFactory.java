package vn.svframe.svarcade.security;

import java.util.Set;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;

/** Compiles one configured action binding into the shared dispatcher. */
public interface ActionHandlerFactory {
    void validate(Node config);
    default Set<Id> dependencies(Node config) { return Set.of(); }
    default Set<SessionServices.Key<?>> requires(Node config) { return Set.of(); }
    ActionDispatcher.Handler create(GenericSession session, Node config);
}
