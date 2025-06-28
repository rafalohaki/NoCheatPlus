package fr.neatmonster.nocheatplus.test;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import fr.neatmonster.nocheatplus.utilities.TimedInteractionTracker;

public class TimedInteractionTrackerTest {

    @Test
    public void testFirstInteractionAlwaysQualifies() {
        TimedInteractionTracker tracker = new TimedInteractionTracker(800);
        assertTrue(tracker.updateAndQualifies(System.currentTimeMillis()));
    }

    @Test
    public void testSecondInteractionWithinTimeoutQualifies() throws InterruptedException {
        TimedInteractionTracker tracker = new TimedInteractionTracker(500);
        tracker.updateAndQualifies(System.currentTimeMillis());
        Thread.sleep(200);
        assertTrue(tracker.updateAndQualifies(System.currentTimeMillis()));
    }

    @Test
    public void testSecondInteractionOutsideTimeoutFails() throws InterruptedException {
        TimedInteractionTracker tracker = new TimedInteractionTracker(100);
        tracker.updateAndQualifies(System.currentTimeMillis());
        Thread.sleep(150);
        assertFalse(tracker.updateAndQualifies(System.currentTimeMillis()));
    }

    @Test
    public void testResetBehavior() {
        TimedInteractionTracker tracker = new TimedInteractionTracker(800);
        tracker.updateAndQualifies(System.currentTimeMillis());
        tracker.reset();
        assertEquals(0, tracker.getLast());
    }
}
