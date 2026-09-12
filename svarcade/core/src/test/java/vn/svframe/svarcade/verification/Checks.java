package vn.svframe.svarcade.verification;

import java.util.Objects;

/** Dependency-free assertions also reused by the JUnit bridge. */
public final class Checks {
    private Checks() { }
    public static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    public static void rejects(Class<? extends Throwable> type, Runnable operation) {
        try { operation.run(); }
        catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError("Unexpected exception", failure);
        }
        throw new AssertionError("Expected " + type.getName());
    }
}
