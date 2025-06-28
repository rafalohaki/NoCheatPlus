package fr.neatmonster.nocheatplus.util;

import static org.junit.Assert.*;

import org.junit.Test;

public class TimedInteractionTrackerTest {

    @Test
    public void testFirstInteractionAlwaysQualifies() {
        TimedInteractionTracker tracker = new TimedInteractionTracker(800);
        assertTrue(tracker.updateAndQualifies(System.currentTimeMillis()));
    }

    @Test
    public void testQualifiesWithinTimeout() throws InterruptedException {
        TimedInteractionTracker tracker = new TimedInteractionTracker(300);
        tracker.updateAndQualifies(System.currentTimeMillis());
        Thread.sleep(100);
        assertTrue(tracker.updateAndQualifies(System.currentTimeMillis()));
    }

    @Test
    public void testFailsOutsideTimeout() throws InterruptedException {
        TimedInteractionTracker tracker = new TimedInteractionTracker(100);
        tracker.updateAndQualifies(System.currentTimeMillis());
        Thread.sleep(150);
        assertFalse(tracker.updateAndQualifies(System.currentTimeMillis()));
    }

    @Test
    public void testReset() {
        TimedInteractionTracker tracker = new TimedInteractionTracker(800);
        tracker.updateAndQualifies(System.currentTimeMillis());
        tracker.reset();
        assertEquals(0, tracker.getLast());
    }

    @Test
    public void testUpdateTimestampCorrectly() {
        TimedInteractionTracker tracker = new TimedInteractionTracker(300);
        long t1 = System.currentTimeMillis();
        assertTrue(tracker.updateAndQualifies(t1));

        long t2 = t1 + 100;
        assertTrue(tracker.updateAndQualifies(t2));
        assertEquals(t1, tracker.getLast());
    }
}
