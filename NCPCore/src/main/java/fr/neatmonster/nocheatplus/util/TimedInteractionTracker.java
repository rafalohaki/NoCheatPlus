package fr.neatmonster.nocheatplus.util;

/**
 * Utility class to track and validate time based interactions.
 */
public final class TimedInteractionTracker {

    private long lastInteract;
    private final long timeout;

    /**
     * Create a tracker with the given timeout in milliseconds.
     *
     * @param timeoutMs timeout in milliseconds
     */
    public TimedInteractionTracker(final long timeoutMs) {
        this.timeout = timeoutMs;
    }

    /**
     * Update the tracker with the provided timestamp.
     *
     * @param now current time in milliseconds
     * @return {@code true} if the interaction is within the timeout
     */
    public boolean updateAndQualifies(final long now) {
        if (lastInteract <= 0) {
            lastInteract = now;
            return true;
        }

        final long diff = now - lastInteract;
        if (diff < timeout) {
            // preserve the earliest timestamp for multiple fast interactions
            lastInteract = Math.min(now, lastInteract);
            return true;
        }
        lastInteract = now;
        return false;
    }

    /** Reset the tracker. */
    public void reset() {
        lastInteract = 0;
    }

    /**
     * Get the last interaction timestamp.
     */
    public long getLast() {
        return lastInteract;
    }

    /**
     * Set the last interaction timestamp.
     *
     * @param timestamp the timestamp to set
     */
    public void setLast(final long timestamp) {
        this.lastInteract = timestamp;
    }
}
