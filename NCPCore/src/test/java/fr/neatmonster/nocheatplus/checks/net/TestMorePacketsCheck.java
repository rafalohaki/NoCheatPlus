package fr.neatmonster.nocheatplus.checks.net;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import fr.neatmonster.nocheatplus.utilities.ds.count.ActionFrequency;

public class TestMorePacketsCheck {

    @Test
    public void testNoViolationWithEmptyBuckets() {
        ActionFrequency freq = new ActionFrequency(5, 1000);
        ActionFrequency burst = new ActionFrequency(12, 5000);
        List<String> tags = new ArrayList<String>();
        double v = NetStatic.morePacketsCheck(freq, 0L, 0f, 2f, 2f, burst, 10f, 100.0, 1000.0, tags);
        assertEquals(0.0, v, 0.0001);
    }

    @Test
    public void testViolationForHighPacketFrequency() {
        ActionFrequency freq = new ActionFrequency(5, 1000);
        for (int i = 0; i < 5; i++) {
            freq.setBucket(i, 5f);
        }
        ActionFrequency burst = new ActionFrequency(12, 5000);
        List<String> tags = new ArrayList<String>();
        double v = NetStatic.morePacketsCheck(freq, 0L, 0f, 2f, 2f, burst, 10f, 100.0, 1000.0, tags);
        assertTrue(v > 0.0);
    }

    @Test
    public void testBurnInfoSeparatedBuckets() {
        ActionFrequency freq = new ActionFrequency(5, 1000);
        freq.setBucket(1, 2f);
        freq.setBucket(3, 1f);
        NetStatic.BurnInfo info = NetStatic.computeBurnInfo(freq);
        assertEquals(3, info.burnStart());
        assertEquals(1, info.empty());
    }

    @Test
    public void testBurnInfoSingleBucket() {
        ActionFrequency freq = new ActionFrequency(5, 1000);
        freq.setBucket(2, 1f);
        NetStatic.BurnInfo info = NetStatic.computeBurnInfo(freq);
        assertEquals(5, info.burnStart());
        assertEquals(0, info.empty());
    }

    @Test
    public void testBurnInfoTrailingEmpty() {
        ActionFrequency freq = new ActionFrequency(5, 1000);
        freq.setBucket(1, 1f);
        freq.setBucket(2, 1f);
        NetStatic.BurnInfo info = NetStatic.computeBurnInfo(freq);
        assertEquals(2, info.burnStart());
        assertEquals(2, info.empty());
    }
}
