package vn.worldcomesalive.generation.v2;

/** Expected candidate rejection: the sampled site cannot satisfy the current settlement program. */
public final class PlanningRejectedException extends RuntimeException {
    public PlanningRejectedException(String message){super(message);}
    public PlanningRejectedException(String message,Throwable cause){super(message,cause);}
}
