package vn.svframe.lively.navigation;

/** Pure retry policy so blocked navigation cannot turn into continuous world sampling. */
final class NavigationRetryPolicy {
    private static final long BASE_TICKS = 20L;
    private static final long MAX_TICKS = 20L * 30L;
    private static final int STATIC_FAILURE_LIMIT = 6;

    private NavigationRetryPolicy() {}

    static long delayTicks(int failures) {
        int bounded = Math.max(1, Math.min(12, failures));
        long multiplier = 1L << Math.min(5, bounded - 1);
        return Math.min(MAX_TICKS, BASE_TICKS * multiplier);
    }

    static boolean abandon(WorldNavigationService.Mode mode, int failures) {
        if (mode == WorldNavigationService.Mode.FOLLOW || mode == WorldNavigationService.Mode.ESCORT) return false;
        return failures >= STATIC_FAILURE_LIMIT;
    }
}
