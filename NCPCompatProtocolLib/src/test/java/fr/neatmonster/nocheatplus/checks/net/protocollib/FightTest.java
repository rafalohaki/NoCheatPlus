package fr.neatmonster.nocheatplus.checks.net.protocollib;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Unit tests for {@link Fight}. */
public class FightTest {

    @Test
    public void testValuesBelowEpsilonAreNegligible() {
        float halfEps = Fight.VELOCITY_EPSILON / 2.0f;
        assertTrue(Fight.isNegligibleVelocity(halfEps, halfEps, halfEps));
    }

    @Test
    public void testValuesAboveEpsilonBypassNegligible() {
        float aboveEps = Fight.VELOCITY_EPSILON * 2.0f;
        assertFalse(Fight.isNegligibleVelocity(aboveEps, 0f, 0f));
    }
}
