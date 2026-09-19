package io.github.aristheg201.svhub.engine;

/** Explicit state is part of the recovery codec, never java.util.Random internals. */
public final class SeededRandom {
    private long state;
    public SeededRandom(long state) { this.state = state; }
    public long state() { return state; }
    public long nextLong() {
        long z = (state += 0x9e3779b97f4a7c15L);
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
    public double nextDouble() { return (nextLong() >>> 11) * 0x1.0p-53; }
    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("RNG bound must be positive");
        return (int) ((nextLong() >>> 1) % bound);
    }
}
