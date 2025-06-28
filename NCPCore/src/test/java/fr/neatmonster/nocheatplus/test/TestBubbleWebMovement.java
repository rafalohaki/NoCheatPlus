package fr.neatmonster.nocheatplus.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import fr.neatmonster.nocheatplus.checks.moving.helper.BubbleWebHandler;
import fr.neatmonster.nocheatplus.checks.moving.magic.Magic;

/**
 * Regression tests for bubble column + web physics.
 */
public class TestBubbleWebMovement {

    @Test
    public void testAscendDoesNotExceedLimit() {
        double speed = 0.0;
        for (int i = 0; i < 20; i++) {
            speed = BubbleWebHandler.computeAscendSpeed(speed);
            assertTrue(speed <= Magic.bubbleStreamAscend + 1e-9,
                    "Exceeded ascend limit at iteration " + i + ": " + speed);
        }
    }

    @Test
    public void testDescendDoesNotExceedLimit() {
        double speed = 0.0;
        for (int i = 0; i < 20; i++) {
            speed = BubbleWebHandler.computeDescendSpeed(speed);
            assertTrue(speed >= -Magic.bubbleStreamDescend - 1e-9,
                    "Exceeded descend limit at iteration " + i + ": " + speed);
        }
    }
}
