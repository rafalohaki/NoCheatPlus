package fr.neatmonster.nocheatplus.checks.blockbreak;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import fr.neatmonster.nocheatplus.compat.AlmostBoolean;

public class FastBreakDecisionTest {

    private long clamp;

    @Before
    public void setup() {
        clamp = 100;
    }

    @Test
    public void testShouldSkip() {
        assertTrue(FastBreakDecision.shouldSkip(AlmostBoolean.YES));
        assertFalse(FastBreakDecision.shouldSkip(AlmostBoolean.NO));
        assertFalse(FastBreakDecision.shouldSkip(AlmostBoolean.MAYBE));
    }

    @Test
    public void testAdjustedElapsed_NO() {
        assertEquals(150, FastBreakDecision.adjustedElapsed(150, AlmostBoolean.NO, clamp));
    }

    @Test
    public void testAdjustedElapsed_MAYBE() {
        assertEquals(80, FastBreakDecision.adjustedElapsed(80, AlmostBoolean.MAYBE, clamp));
        assertEquals(100, FastBreakDecision.adjustedElapsed(150, AlmostBoolean.MAYBE, clamp));
    }

    @Test
    public void testAdjustedElapsed_YES() {
        assertEquals(50, FastBreakDecision.adjustedElapsed(50, AlmostBoolean.YES, clamp));
    }
}
