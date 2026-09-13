package vn.svframe.svarcade.config;

import java.util.Set;

/** System-specific structure/semantics/reference checks; never game-ID dispatch. */
public interface SystemSchema {
    void validate(Node config);
    default Set<Id> dependencies() { return Set.of(); }
    /** Config-sensitive dependencies are compiled before publication. */
    default Set<Id> dependencies(Node config) { return dependencies(); }
}
