package fr.neatmonster.nocheatplus.utilities;

/**
 * Helper to track the timing of repeated interactions and judge whether the
 * current interaction happened quickly enough compared to the previous one.
 */
public final class TimedInteractionTracker {

    private long lastInteract;
    private final long timeout;

    /**
     * Create a new tracker with the supplied timeout in milliseconds.
     *
     * @param timeoutMs maximum difference in milliseconds for an interaction to
     *                  qualify as "fast"
     */
    public TimedInteractionTracker(final long timeoutMs) {
        this.timeout = timeoutMs;
    }

    /**
     * Update the last interaction time and test if the interaction qualifies as
     * happening quickly after the previous one.
     *
     * @param now current timestamp in milliseconds
     * @return {@code true} if the interaction occurred within the configured
     *         timeout of the previous interaction
     */
    public boolean updateAndQualifies(final long now) {
        if (lastInteract <= 0) {
            lastInteract = now;
            return true;
        }
        final long diff = now - lastInteract;
        if (diff < timeout) {
            lastInteract = Math.min(now, lastInteract);
            return true;
        }
        lastInteract = now;
        return false;
    }

    /**
     * Reset the tracker to an invalid state.
     */
    public void reset() {
        lastInteract = 0;
    }

    /**
     * Get the last interaction timestamp.
     *
     * @return last interaction timestamp in milliseconds or {@code 0} if none
     */
    public long getLast() {
        return lastInteract;
    }

    /**
     * Set the last interaction timestamp.
     *
     * @param timestamp new last interaction time
     */
    public void setLast(final long timestamp) {
        this.lastInteract = timestamp;
    }
}
