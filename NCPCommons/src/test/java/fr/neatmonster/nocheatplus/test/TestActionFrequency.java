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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import fr.neatmonster.nocheatplus.utilities.ds.count.ActionFrequency;

@DisplayName("ActionFrequency Tests")
public class TestActionFrequency {

    @Nested
    @DisplayName("When manipulating buckets directly")
    class DirectManipulationTest {

        private ActionFrequency freq;

        @BeforeEach
        void setUp() {
            freq = new ActionFrequency(10, 100);
        }

        @Test
        @DisplayName("score() should sum the values of all buckets")
        void scoreShouldSumAllBuckets() {
            for (int i = 0; i < 10; i++) {
                freq.setBucket(i, 1);
            }
            assertEquals(10f, freq.score(1f), "Score should be 10 for 10 buckets with a value of 1.");
        }

        @Test
        @DisplayName("clear() should reset the score to zero")
        void clearShouldResetScore() {
            for (int i = 0; i < 10; i++) {
                freq.setBucket(i, 1);
            }
            freq.clear(0); // The parameter for clear is unused in the implementation.
            assertEquals(0f, freq.score(1f), "Score should be 0 after clearing.");
        }
    }

    @Nested
    @DisplayName("When adding actions over time")
    class TimeBasedDecayTest {

        private static final int BUCKET_COUNT = 3;
        private static final int BUCKET_DURATION_MS = 333;
        private static final int TOTAL_DURATION_MS = BUCKET_COUNT * BUCKET_DURATION_MS; // 999ms
        private static final long START_TIME = 100_000L; // Use a non-zero start time

        private ActionFrequency freq;

        @BeforeEach
        void setUp() {
            freq = new ActionFrequency(BUCKET_COUNT, BUCKET_DURATION_MS);
            freq.update(START_TIME);
            // Add 999 actions, one per millisecond. Each bucket will receive 333 actions.
            for (int i = 0; i < TOTAL_DURATION_MS; i++) {
                freq.add(START_TIME + i, 1f);
            }
        }

        @Test
        @DisplayName("should have a full score immediately after being filled")
        void shouldHaveFullScoreAfterFilling() {
            assertEquals(TOTAL_DURATION_MS, freq.score(1f), "Initial score should be the sum of all additions.");
        }

        @Test
        @DisplayName("score should decay as one bucket expires")
        void scoreShouldDecayAsOneBucketExpires() {
            // Move time forward by exactly the total duration, causing the first bucket to expire.
            freq.update(START_TIME + TOTAL_DURATION_MS);
            float expectedScore = TOTAL_DURATION_MS - BUCKET_DURATION_MS; // 666
            assertEquals(expectedScore, freq.score(1f), "Score should decrease by one bucket's worth.");
        }

        @Test
        @DisplayName("score should decay further as a second bucket expires")
        void scoreShouldDecayAsTwoBucketsExpire() {
            // Move time forward by total_duration + one_bucket_duration
            freq.update(START_TIME + TOTAL_DURATION_MS + BUCKET_DURATION_MS);
            float expectedScore = TOTAL_DURATION_MS - (2 * BUCKET_DURATION_MS); // 333
            assertEquals(expectedScore, freq.score(1f), "Score should decrease by two buckets' worth.");
        }

        @Test
        @DisplayName("score should be zero after all buckets have expired")
        void scoreShouldBeZeroAfterAllBucketsExpire() {
            // Move time forward enough for all buckets to expire
            freq.update(START_TIME + TOTAL_DURATION_MS + (BUCKET_COUNT * BUCKET_DURATION_MS));
            assertEquals(0f, freq.score(1f), "Score should be zero when all buckets are expired.");
        }
    }

    @Test
    @DisplayName("update() should not throw an exception for alternating positive and negative timestamps")
    void updateShouldHandleAlternatingSignTimestamps() {
        // This test verifies robustness against unusual time inputs.
        assertDoesNotThrow(() -> {
            ActionFrequency freq = new ActionFrequency(10, 100);
            long sig = 1;
            for (int i = 0; i < 1000; i++) {
                freq.update(i * sig);
                sig *= -1;
            }
        }, "update() should not fail with fluctuating timestamps.");
    }
}