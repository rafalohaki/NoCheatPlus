package fr.neatmonster.nocheatplus.checks.blockbreak;

import fr.neatmonster.nocheatplus.compat.AlmostBoolean;

/**
 * Utility for deciding how FastBreak behaves depending on instant-break flags.
 */
public final class FastBreakDecision {

    private FastBreakDecision() {
    }

    /**
     * Determine whether FastBreak should be skipped entirely.
     *
     * @param flag value from instant break prediction
     * @return true if the check should be skipped
     */
    public static boolean shouldSkip(final AlmostBoolean flag) {
        return flag == AlmostBoolean.YES;
    }

    /**
     * Adjust elapsed time depending on instant break prediction.
     *
     * @param elapsed actual elapsed time in milliseconds
     * @param flag instant break flag
     * @param clamp value used for clamping when {@code flag == MAYBE}
     * @return adjusted elapsed time
     */
    public static long adjustedElapsed(final long elapsed, final AlmostBoolean flag, final long clamp) {
        if (flag == AlmostBoolean.MAYBE) {
            return Math.min(elapsed, clamp);
        }
        return elapsed;
    }
}
