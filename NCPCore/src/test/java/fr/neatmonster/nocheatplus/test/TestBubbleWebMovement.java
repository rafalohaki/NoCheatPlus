package fr.neatmonster.nocheatplus.test;

import static org.junit.Assert.assertTrue;

import fr.neatmonster.nocheatplus.checks.moving.helper.BubbleWebHandler;
import fr.neatmonster.nocheatplus.checks.moving.magic.Magic;
import org.junit.Test;

/**
 * Basic regression tests for bubble column interaction with cobwebs.
 */
public class TestBubbleWebMovement {

    @Test
    public void testAscendDoesNotExceedLimit() {
        double speed = 0.0;
        for (int i = 0; i < 20; i++) {
            speed = BubbleWebHandler.computeAscendSpeed(speed);
            assertTrue("Speed should not exceed bubbleStreamAscend", speed <= Magic.bubbleStreamAscend + 1e-9);
        }
    }

    @Test
    public void testHighSpeedClamped() {
        double speed = BubbleWebHandler.computeAscendSpeed(2.0);
        assertTrue("Speed should be clamped to bubbleStreamAscend", speed <= Magic.bubbleStreamAscend + 1e-9);
    }
}
