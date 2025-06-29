package fr.neatmonster.nocheatplus.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import fr.neatmonster.nocheatplus.utilities.PenaltyTime;

/**
 * A test suite for the {@link PenaltyTime} class, verifying the logic
 * of applying and checking time-based penalties.
 */
@DisplayName("PenaltyTime Tests")
public class TestPenaltyTime {

    /** A fixed point in time used as a reference to make tests deterministic. */
    private static final long START_TIME = 100_000L;
    
    private PenaltyTime penaltyTime;

    @BeforeEach
    void setUp() {
        penaltyTime = new PenaltyTime();
    }

    @Test
    @DisplayName("isPenalty should be false by default for any timestamp")
    void shouldNotHavePenaltyByDefault() {
        assertFalse(penaltyTime.isPenalty(START_TIME), "A new instance should not have a penalty.");
    }

    @Test
    @DisplayName("applying a zero-duration penalty should have no effect")
    void applyingZeroDurationPenaltyShouldHaveNoEffect() {
        penaltyTime.applyPenalty(START_TIME, 0);
        assertFalse(penaltyTime.isPenalty(START_TIME), "A penalty with zero duration should not be active.");
    }

    @Test
    @DisplayName("applyPenalty should only set a new penalty if it expires later than the existing one")
    void applyPenaltyShouldOnlySetLaterExpiringPenalty() {
        // Given a penalty is active
        penaltyTime.applyPenalty(START_TIME, 50L);

        // When a new penalty is applied that would expire earlier
        penaltyTime.applyPenalty(START_TIME + 10, 20L); // Expires at START_TIME + 30, which is < START_TIME + 50

        // Then the original penalty should remain in effect
        assertTrue(penaltyTime.isPenalty(START_TIME + 49), "Original penalty should still be active.");
        assertFalse(penaltyTime.isPenalty(START_TIME + 50), "Original penalty expiration should be unchanged.");

        // When a new penalty is applied that expires later
        penaltyTime.applyPenalty(START_TIME, 100L); // Expires at START_TIME + 100

        // Then the new, longer penalty should be in effect
        assertTrue(penaltyTime.isPenalty(START_TIME + 99), "The new, longer penalty should be active.");
        assertFalse(penaltyTime.isPenalty(START_TIME + 100), "The penalty should expire based on the new duration.");
    }

    /**
     * Tests for scenarios where a time penalty has already been applied.
     */
    @Nested
    @DisplayName("when a penalty is active")
    class WhenPenaltyIsActive {

        private static final long PENALTY_DURATION = 50L;

        @BeforeEach
        void setUp() {
            // Apply a penalty before each test in this group
            penaltyTime.applyPenalty(START_TIME, PENALTY_DURATION);
        }

        @Test
        @DisplayName("isPenalty should be false for timestamps before the penalty started")
        void isPenaltyShouldBeFalseBeforePenaltyStart() {
            assertFalse(penaltyTime.isPenalty(START_TIME - 1));
        }

        @Test
        @DisplayName("isPenalty should be true exactly at the start time")
        void isPenaltyShouldBeTrueAtStartTime() {
            assertTrue(penaltyTime.isPenalty(START_TIME));
        }

        @Test
        @DisplayName("isPenalty should be true during the penalty period")
        void isPenaltyShouldBeTrueDuringPenaltyPeriod() {
            assertTrue(penaltyTime.isPenalty(START_TIME + PENALTY_DURATION / 2));
        }

        @Test
        @DisplayName("isPenalty should be true just before the penalty expires")
        void isPenaltyShouldBeTrueJustBeforeExpiration() {
            assertTrue(penaltyTime.isPenalty(START_TIME + PENALTY_DURATION - 1));
        }

        @Test
        @DisplayName("isPenalty should be false exactly when the penalty expires")
        void isPenaltyShouldBeFalseAtExpiration() {
            assertFalse(penaltyTime.isPenalty(START_TIME + PENALTY_DURATION));
        }

        @Test
        @DisplayName("isPenalty should be false after the penalty has expired")
        void isPenaltyShouldBeFalseAfterExpiration() {
            assertFalse(penaltyTime.isPenalty(START_TIME + PENALTY_DURATION + 1));
        }
    }
}