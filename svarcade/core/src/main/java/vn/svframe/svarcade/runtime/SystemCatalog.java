package vn.svframe.svarcade.runtime;

import java.util.Objects;
import vn.svframe.svarcade.config.*;

/**
 * Bootstrap registry that keeps a system's schema and runtime factory bound to the
 * same production plan instance. Definitions and sessions therefore cannot be
 * wired from two independently-maintained registries.
 */
public final class SystemCatalog {
    private final Registry<SystemSchema> schemas;
    private final Registry<SystemFactory> factories;

    private SystemCatalog(Registry<SystemSchema> schemas, Registry<SystemFactory> factories) {
        this.schemas = Objects.requireNonNull(schemas);
        this.factories = Objects.requireNonNull(factories);
    }

    public Registry<SystemSchema> schemas() { return schemas; }
    public Registry<SystemFactory> factories() { return factories; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final Registry.Builder<SystemSchema> schemas = new Registry.Builder<>();
        private final Registry.Builder<SystemFactory> factories = new Registry.Builder<>();

        public <T extends SystemSchema & SystemFactory> Builder add(Id id, T plan) {
            Objects.requireNonNull(id); Objects.requireNonNull(plan);
            schemas.add(id, plan);
            factories.add(id, plan);
            return this;
        }

        public SystemCatalog build() { return new SystemCatalog(schemas.build(), factories.build()); }
    }
}
