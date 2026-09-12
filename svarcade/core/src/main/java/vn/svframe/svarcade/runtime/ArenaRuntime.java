package vn.svframe.svarcade.runtime;

import java.util.*;
import vn.svframe.svarcade.config.Id;

/** Exclusive arena claims; stale cleanup cannot release a replacement session. */
public final class ArenaRuntime {
    public record Key(Id definition, String arena) {
        public Key { Objects.requireNonNull(definition); Objects.requireNonNull(arena); }
    }
    public record Lease(Key arena, UUID session, UUID token) { }
    private final Map<Key, Lease> leases = new HashMap<>();
    public synchronized Lease acquire(Key arena, UUID session) {
        Objects.requireNonNull(arena); Objects.requireNonNull(session);
        if (leases.containsKey(arena)) throw new IllegalStateException("Arena already owned: " + arena);
        Lease lease = new Lease(arena, session, UUID.randomUUID());
        leases.put(arena, lease);
        return lease;
    }
    public synchronized boolean owns(Lease lease) { return lease.equals(leases.get(lease.arena())); }
    public synchronized boolean release(Lease lease) { return leases.remove(lease.arena(), lease); }
    public synchronized Map<Key, Lease> snapshot() { return Map.copyOf(leases); }
}
