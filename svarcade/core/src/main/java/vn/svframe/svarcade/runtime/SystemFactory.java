package vn.svframe.svarcade.runtime;

import vn.svframe.svarcade.config.Node;

@FunctionalInterface
public interface SystemFactory {
    SessionSystem create(GenericSession session, Node config);
}
