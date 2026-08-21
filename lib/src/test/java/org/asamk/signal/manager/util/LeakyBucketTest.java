package org.asamk.signal.manager.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeakyBucketTest {

    private static final long NOW = 1_700_000_000_000L;

    @Test
    void dripsLevelsAndCarriesPartialIntervals() {
        final var state = new TestState();
        final var bucket = new LeakyBucket(3, Duration.ofHours(1).toMillis(), state);

        bucket.use(NOW);
        bucket.use(NOW);
        bucket.use(NOW);

        assertEquals(2, bucket.level(NOW + Duration.ofMinutes(90).toMillis()));
        assertEquals(1, bucket.level(NOW + Duration.ofMinutes(121).toMillis()));
    }

    @Test
    void refundNeverDropsBelowZero() {
        final var bucket = new LeakyBucket(1, 1_000, new TestState());

        bucket.refund(NOW);

        assertEquals(0, bucket.level(NOW));
        assertTrue(bucket.hasRoom(NOW));
    }

    @Test
    void clockMovingBackwardsRefillsTheBucket() {
        final var bucket = new LeakyBucket(1, 1_000, new TestState());
        bucket.use(NOW);

        assertTrue(bucket.hasRoom(NOW - 1));
    }

    private static final class TestState implements LeakyBucket.State {

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
}
