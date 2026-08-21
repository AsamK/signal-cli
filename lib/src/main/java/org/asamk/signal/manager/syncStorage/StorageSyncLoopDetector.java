package org.asamk.signal.manager.syncStorage;

import org.asamk.signal.manager.util.LeakyBucket;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class StorageSyncLoopDetector {

    private static final int FINGERPRINT_HISTORY = 3;

    private final BooleanSupplier isMultiDevice;
    private final List<Integer> recentFingerprints = new ArrayList<>();
    private final LeakyBucket contentBucket = new LeakyBucket(3,
            Duration.ofHours(1).toMillis(),
            new InMemoryBucketState());
    private final LeakyBucket rateBucket = new LeakyBucket(100,
            Duration.ofMinutes(10).toMillis(),
            new InMemoryBucketState());

    public StorageSyncLoopDetector(final BooleanSupplier isMultiDevice) {
        this.isMultiDevice = isMultiDevice;
    }

    public synchronized Decision onWriteAttempt(
            final WriteOperationResult write,
            final boolean fetchedRemoteManifest,
            final boolean isRetry
    ) {
        return onWriteAttempt(write, fetchedRemoteManifest, isRetry, System.currentTimeMillis());
    }

    synchronized Decision onWriteAttempt(
            final WriteOperationResult write,
            final boolean fetchedRemoteManifest,
            final boolean isRetry,
            final long now
    ) {
        if (!isMultiDevice.getAsBoolean() || isRetry) {
            return Decision.Allowed.INSTANCE;
        }

        final var fingerprint = fingerprint(write);
        final var chargeContent = fetchedRemoteManifest && fingerprint != null && recentFingerprints.contains(
                fingerprint);

        if (chargeContent && !contentBucket.hasRoom(now)) {
            return new Decision.Denied(Cause.REPEATED_PAYLOAD, contentBucket.level(now));
        }
        if (fetchedRemoteManifest && !rateBucket.hasRoom(now)) {
            return new Decision.Denied(Cause.WRITE_RATE, rateBucket.level(now));
        }

        if (chargeContent) {
            contentBucket.use(now);
        }
        if (fetchedRemoteManifest) {
            rateBucket.use(now);
        }
        if (fingerprint != null) {
            remember(fingerprint);
        }

        return Decision.Allowed.INSTANCE;
    }

    public synchronized void onWriteFailed() {
        onWriteFailed(System.currentTimeMillis());
    }

    synchronized void onWriteFailed(final long now) {
        contentBucket.refund(now);
        rateBucket.refund(now);
    }

    public synchronized void onConverged() {
        contentBucket.clear();
    }

    private void remember(final int fingerprint) {
        recentFingerprints.remove(Integer.valueOf(fingerprint));
        recentFingerprints.addFirst(fingerprint);
        if (recentFingerprints.size() > FINGERPRINT_HISTORY) {
            recentFingerprints.removeLast();
        }
    }

    private static Integer fingerprint(final WriteOperationResult write) {
        if (write.inserts().isEmpty()) {
            return null;
        }

        return write.inserts()
                .stream()
                .map(record -> Arrays.hashCode(record.getProto().encode()))
                .sorted()
                .toList()
                .hashCode();
    }

    private static final class InMemoryBucketState implements LeakyBucket.State {

        private int level;
        private long levelUpdatedAt;

        @Override
        public int level() {
            return level;
        }

        @Override
        public long levelUpdatedAt() {
            return levelUpdatedAt;
        }

        @Override
        public void update(final int level, final long levelUpdatedAt) {
            this.level = level;
            this.levelUpdatedAt = levelUpdatedAt;
        }
    }

    public enum Cause {
        REPEATED_PAYLOAD,
        WRITE_RATE
    }

    public sealed interface Decision {

        enum Allowed implements Decision {
            INSTANCE
        }

        record Denied(Cause cause, int level) implements Decision {}
    }
}
