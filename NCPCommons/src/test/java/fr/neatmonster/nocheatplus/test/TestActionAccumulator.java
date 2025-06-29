/*
 * This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.neatmonster.nocheatplus.test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import fr.neatmonster.nocheatplus.utilities.ds.count.ActionAccumulator;

@DisplayName("ActionAccumulator Tests")
public class TestActionAccumulator {

    private static final int BUCKETS = 50;
    private static final int CAPACITY = 10;

    @Nested
    @DisplayName("When newly created")
    class WhenNew {
        private ActionAccumulator acc;

        @BeforeEach
        void setUp() {
            acc = new ActionAccumulator(BUCKETS, CAPACITY);
        }

        @Test
        @DisplayName("should be empty with zero counts and scores")
        void shouldBeEmpty() {
            assertAccumulatorIsEmpty(acc, "A new accumulator");
        }
    }

    @Nested
    @DisplayName("When filled with data")
    class WhenFilled {
        private ActionAccumulator acc;

        @BeforeEach
        void setUp() {
            acc = new ActionAccumulator(BUCKETS, CAPACITY);
            fillWithIncreasingValues(acc);
        }

        @Test
        @DisplayName("should have correct counts and scores in total and per bucket")
        void shouldHaveCorrectCountsAndScores() {
            assertStateAfterFillingWithIncreasingValues(acc);
        }

        @Test
        @DisplayName("clear() should reset all counts and scores to zero")
        void clearShouldResetToZero() {
            acc.clear();
            assertAccumulatorIsEmpty(acc, "An accumulator after being cleared");
        }
    }

    // --- Helper Methods ---

    /**
     * Fills the accumulator with numbers from 1 to buckets * capacity.
     */
    private void fillWithIncreasingValues(ActionAccumulator acc) {
        int totalValues = acc.numberOfBuckets() * acc.bucketCapacity();
        for (int i = 1; i <= totalValues; i++) {
            acc.add(i);
        }
    }

    /**
     * Asserts that the accumulator's state matches the expected state after being filled
     * by {@link #fillWithIncreasingValues(ActionAccumulator)}.
     */
    private void assertStateAfterFillingWithIncreasingValues(ActionAccumulator acc) {
        int buckets = acc.numberOfBuckets();
        int capacity = acc.bucketCapacity();
        long totalElements = (long) buckets * capacity;
        long expectedTotalScore = totalElements * (totalElements + 1) / 2; // Sum of 1 to N

        assertAll("Overall counts and scores should be correct",
                () -> assertEquals(totalElements, acc.count(), "Total count is incorrect."),
                () -> assertEquals(expectedTotalScore, acc.score(), "Total score is incorrect.")
        );

        for (int i = 0; i < buckets; i++) {
            final int bucketIndex = i;
            // The values are added in increasing order, so the latest values are in bucket 0.
            int start = (buckets - 1 - bucketIndex) * capacity + 1;
            int end = start + capacity - 1;
            // Sum of an arithmetic series: (n/2) * (first + last)
            long expectedBucketScore = (long) capacity * (start + end) / 2;

            assertAll("Bucket " + bucketIndex + " state should be correct",
                    () -> assertEquals(capacity, acc.bucketCount(bucketIndex),
                            "Bucket count at index " + bucketIndex + " is incorrect."),
                    () -> assertEquals(expectedBucketScore, acc.bucketScore(bucketIndex),
                            "Bucket score at index " + bucketIndex + " is incorrect.")
            );
        }
    }

    /**
     * Asserts that the given accumulator is completely empty (all counts and scores are zero).
     */
    private void assertAccumulatorIsEmpty(ActionAccumulator acc, String context) {
        assertAll(context + " should be empty",
                () -> assertEquals(0, acc.count(), "Total count should be zero."),
                () -> assertEquals(0, acc.score(), "Total score should be zero.")
        );

        for (int i = 0; i < acc.numberOfBuckets(); i++) {
            final int bucketIndex = i;
            assertAll("Bucket " + bucketIndex + " should be empty",
                    () -> assertEquals(0, acc.bucketCount(bucketIndex),
                            "Bucket count at index " + bucketIndex + " should be zero."),
                    () -> assertEquals(0, acc.bucketScore(bucketIndex),
                            "Bucket score at index " + bucketIndex + " should be zero.")
            );
        }
    }
}