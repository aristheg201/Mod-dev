package dev.svextract.world;

/** Only main-thread template construction and verified objective switches may modify protected training maps. */
public final class WorldMutationScope implements AutoCloseable {
    private static final ThreadLocal<Boolean> ALLOWED=ThreadLocal.withInitial(()->false);
    private final boolean previous;
    private WorldMutationScope(){previous=ALLOWED.get();ALLOWED.set(true);}
    public static WorldMutationScope enter(){return new WorldMutationScope();}
    public static boolean allowed(){return ALLOWED.get();}
    @Override public void close(){if(previous)ALLOWED.set(true);else ALLOWED.remove();}
}
