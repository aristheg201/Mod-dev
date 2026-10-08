package io.github.aristheg201.cobblemonworld.npc;

/** The trainer's ace matches the strongest party member; authored team variation is retained. */
public final class TrainerLevelScaling {
    private TrainerLevelScaling() {}
    public static int level(int authoredLevel, int authoredAce, int playerAce, int offset) {
        return Math.max(1, Math.min(100, playerAce + offset + authoredLevel - authoredAce));
    }
}
