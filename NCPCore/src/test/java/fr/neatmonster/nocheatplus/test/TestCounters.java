package fr.neatmonster.nocheatplus.test;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.junit.Test;

import fr.neatmonster.nocheatplus.stats.Counters;

public class TestCounters {

    @Test
    public void testAddMultipleCounts() {
        Counters counters = new Counters();
        int id = counters.registerKey("foo");
        counters.addPrimaryThread(id, 5);
        counters.add(id, 3, true);
        counters.add(id, 2, false);

        Map<String, Integer> pt = counters.getPrimaryThreadCounts();
        Map<String, Integer> sy = counters.getSynchronizedCounts();
        Map<String, Integer> merged = counters.getMergedCounts();

        assertEquals(Integer.valueOf(8), pt.get("foo"));
        assertEquals(Integer.valueOf(2), sy.get("foo"));
        assertEquals(Integer.valueOf(10), merged.get("foo"));
    }
}
