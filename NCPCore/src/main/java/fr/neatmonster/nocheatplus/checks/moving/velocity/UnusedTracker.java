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
package fr.neatmonster.nocheatplus.checks.moving.velocity;

/**
 * Track unused velocity for one direction of one axis (absolute amounts only!).
 * 
 * @author asofold
 *
 */
public class UnusedTracker {

    // Using ticks for timing may be problematic; consider adding a sequence counter.
    // If the player is already moving at stored velocity, results can be less precise.

    /** Tick of the last time the direction had been blocked. */
    private int lastBlocked = 0;
    /**
     * Tick of the last time the direction had not been blocked, after it had
     * been blocked just before (start of phase).
     */
    private int lastNotBlockedStart = 0;

    // Simple counters used for diagnostics.
    private int resultUpdateCount = 0;
    private int resultViolationCount = 0;
    /** Absolute amount. */
    private double resultViolationAmount = 0.0;

    public void updateState(final int tick, final boolean blocked) {
        if (blocked) {
            lastBlocked = tick;
            // Blocked overrides not blocked.
            if (tick == lastNotBlockedStart) {
                lastNotBlockedStart--;
            }
        }
        else {
            // Only update not blocked tick, if this means a state change.
            // (Blocked overrides not blocked.)
            if (lastNotBlockedStart <= lastBlocked) {
                lastNotBlockedStart = tick;
            }
        }
        resultUpdateCount ++;
    }

    /**
     * 
     * @param tick Tick when the value had been added (not invalidated).
     * @param amount Absolute amount.
     */
    public void addValue(final int tick, final double amount) {
        if (amount < 0.0) {
            throw new IllegalArgumentException("The added amount must be greater than zero.");
        }
        // Test if really applicable.
        if (
                // Direction has been blocked since adding the entry.
                lastBlocked >= lastNotBlockedStart
                // Entry had been added too long ago, the direction might have been blocked between.
                || tick <= lastNotBlockedStart
                ) {
            return;
        }
        // Add to internals.
        addViolation(amount);
    }

    private void addViolation(final double amount) {
        resultViolationCount ++;
        resultViolationAmount += amount;
    }

    /**
     * Reset result counters, including the update count.
     */
    public void resetResults() {
        resultUpdateCount = 0;
        resultViolationCount = 0;
        resultViolationAmount = 0.0;
    }

    public int getResultUpdateCount() {
        return resultUpdateCount;
    }

    public int getResultViolationCount() {
        return resultViolationCount;
    }

    public double getResultViolationAmount() {
        return resultViolationAmount;
    }

}
