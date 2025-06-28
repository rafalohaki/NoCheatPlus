package fr.neatmonster.nocheatplus.checks.moving.helper;

import fr.neatmonster.nocheatplus.checks.moving.magic.Magic;

/**
 * Utilities for modeling bubble column movement while inside cobwebs.
 */
public final class BubbleWebHandler {

    private BubbleWebHandler() {}

    /**
     * Compute the next vertical speed for a player in a cobweb inside a bubble
     * column.
     *
     * <p>The calculation uses a simplified per-tick acceleration model that
     * multiplies the previous speed by {@link Magic#FRICTION_MEDIUM_WATER} and
     * then adds a small directional acceleration. The result is clamped to the
     * configured bubble stream terminal velocities.</p>
     *
     * @param lastSpeed the previous vertical speed
     * @param upward true for an upward bubble stream, false for a downward stream
     * @return the predicted vertical speed limited by the bubble stream cap
     */
    public static double computeBubbleSpeed(double lastSpeed, boolean upward) {
        double acceleration = upward ? Magic.bubbleWebUpAcceleration : Magic.bubbleWebDownAcceleration;
        double limit = upward ? Magic.bubbleStreamAscend : -Magic.bubbleStreamDescend;
        double next = lastSpeed * Magic.FRICTION_MEDIUM_WATER + acceleration;
        if (upward) {
            return Math.min(next, limit);
        } else {
            return Math.max(next, limit);
        }
    }

    /**
     * Convenience wrapper for upward bubble streams.
     *
     * @param lastSpeed last vertical speed
     * @return next speed limited by {@link Magic#bubbleStreamAscend}
     */
    public static double computeAscendSpeed(double lastSpeed) {
        return computeBubbleSpeed(lastSpeed, true);
    }
}
