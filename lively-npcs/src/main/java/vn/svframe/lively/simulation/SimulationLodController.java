package vn.svframe.lively.simulation;

/** Distance/importance based simulation budget. It never requires scanning all saved NPCs every tick. */
public final class SimulationLodController {
    public enum Level {
        ACTIVE(1), NEARBY(10), DISTANT(200), DORMANT(1200);
        private final int tickInterval;
        Level(int tickInterval) { this.tickInterval = tickInterval; }
        public int tickInterval() { return tickInterval; }
    }

    public Level classify(double nearestPlayerDistance, double importance, boolean eventParticipant, boolean inCombat) {
        if (inCombat || eventParticipant || nearestPlayerDistance <= 48D) return Level.ACTIVE;
        if (nearestPlayerDistance <= 128D || importance >= 0.80D) return Level.NEARBY;
        if (nearestPlayerDistance <= 512D || importance >= 0.45D) return Level.DISTANT;
        return Level.DORMANT;
    }

    public boolean shouldSimulate(long gameTick, Level level, long stableActorHash) {
        int interval = level.tickInterval();
        long offset = Math.floorMod(stableActorHash, interval);
        return Math.floorMod(gameTick, interval) == offset;
    }
}
