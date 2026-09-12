package vn.svframe.svarcade.runtime;

import java.util.Map;

/** Explicit versioned state; start/tick/restore/close execute on the owner thread. */
public interface SessionSystem {
    int stateSchema();
    void start();
    void tick(long tick);
    Map<String, Object> snapshot();
    void restore(int schema, Map<String, Object> state);
    /** Must be idempotent, including after partial start/restore failure. */
    void close();
}
