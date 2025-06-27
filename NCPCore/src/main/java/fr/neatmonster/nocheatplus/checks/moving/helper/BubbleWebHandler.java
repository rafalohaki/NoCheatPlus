package fr.neatmonster.nocheatplus.checks.moving.helper;

import fr.neatmonster.nocheatplus.checks.moving.magic.Magic;

/**
 * Utility methods for handling bubble stream movement while a player is
 * trapped in webs.
 */
public final class BubbleWebHandler {

    private BubbleWebHandler() {}

    /**
     * Compute the next upward speed when a player is in a web inside a
     * bubble column.
     *
     * <p>The model uses a simple acceleration per tick combined with the
     * usual water friction to approximate vanilla mechanics.</p>
     *
     * @param lastSpeed the vertical speed of the previous tick
     * @return the predicted vertical speed limited by the bubble stream cap
     */
    public static double computeAscendSpeed(double lastSpeed) {
        double next = lastSpeed * Magic.FRICTION_MEDIUM_WATER + Magic.bubbleWebUpAcceleration;
        if (next > Magic.bubbleStreamAscend) {
            next = Magic.bubbleStreamAscend;
        }
        return next;
    }
}
