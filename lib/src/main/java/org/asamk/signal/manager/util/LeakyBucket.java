package org.asamk.signal.manager.util;

public final class LeakyBucket {

    private final int capacity;
    private final long dripIntervalMillis;
    private final State state;

    public LeakyBucket(final int capacity, final long dripIntervalMillis, final State state) {
        this.capacity = capacity;
        this.dripIntervalMillis = dripIntervalMillis;
        this.state = state;
    }

    public int level(final long now) {
        return calculateStateForCurrentTime(now).level();
    }

    public boolean hasRoom(final long now) {
        return level(now) < capacity;
    }

    public void use(final long now) {
        final var currentState = calculateStateForCurrentTime(now);
        state.update(currentState.level() + 1, currentState.levelUpdatedAt());
    }

    public void refund(final long now) {
        final var currentState = calculateStateForCurrentTime(now);
        state.update(Math.max(currentState.level() - 1, 0), currentState.levelUpdatedAt());
    }

    public void clear() {
        state.update(0, 0);
    }

    private Snapshot calculateStateForCurrentTime(final long now) {
        final var level = state.level();
        final var levelUpdatedAt = state.levelUpdatedAt();
        final var elapsed = now - levelUpdatedAt;

        if (level <= 0 || elapsed < 0) {
            return new Snapshot(0, now);
        }

        final var drips = elapsed / dripIntervalMillis;
        return new Snapshot((int) Math.max(level - drips, 0), levelUpdatedAt + dripIntervalMillis * drips);
    }

    private record Snapshot(int level, long levelUpdatedAt) {}

    public interface State {

        int level();

        long levelUpdatedAt();

        void update(int level, long levelUpdatedAt);
    }
}
