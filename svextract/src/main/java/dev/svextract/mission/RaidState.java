package dev.svextract.mission;

public enum RaidState {
    RESERVED, ACTIVE, SETTLING, CLOSED;
    public boolean canTransitionTo(RaidState next) {
        return switch (this) {
            case RESERVED -> next == ACTIVE || next == SETTLING;
            case ACTIVE -> next == SETTLING;
            case SETTLING -> next == CLOSED;
            case CLOSED -> false;
        };
    }
}
