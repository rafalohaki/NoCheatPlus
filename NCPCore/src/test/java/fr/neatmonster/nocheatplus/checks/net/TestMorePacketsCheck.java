package fr.neatmonster.nocheatplus.checks.net;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.*;

import fr.neatmonster.nocheatplus.utilities.ds.count.ActionFrequency;
import fr.neatmonster.nocheatplus.utilities.TickTask;

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
        assertEquals(3, info.burnStart);
        assertEquals(1, info.empty);
    }

    @Test
    public void testLagBelowOne_NoAdjustmentOccurs() throws Exception {
        try (MockedStatic<TickTask> tick = mockStatic(TickTask.class)) {
            tick.when(() -> TickTask.getLag(anyLong(), eq(true))).thenReturn(0.75f);
            final var method = NetStatic.class.getDeclaredMethod("adjustEmptyForLag", int.class, long.class, int.class);
            method.setAccessible(true);
            int adjusted = (Integer) method.invoke(null, 3, 5000L, 5);
            assertEquals("Lag < 1.0 should result in no scaling", 3, adjusted);
        }
    }

    @Test
    public void testLagExactlyOne_NoAdjustmentOccurs() throws Exception {
        try (MockedStatic<TickTask> tick = mockStatic(TickTask.class)) {
            tick.when(() -> TickTask.getLag(anyLong(), eq(true))).thenReturn(1.0f);
            final var method = NetStatic.class.getDeclaredMethod("adjustEmptyForLag", int.class, long.class, int.class);
            method.setAccessible(true);
            int adjusted = (Integer) method.invoke(null, 4, 5000L, 5);
            assertEquals("Lag == 1.0 should result in no scaling", 4, adjusted);
        }
    }

    @Test
    public void testLagAboveOne_AdjustmentOccursAndClamped() throws Exception {
        try (MockedStatic<TickTask> tick = mockStatic(TickTask.class)) {
            tick.when(() -> TickTask.getLag(anyLong(), eq(true))).thenReturn(2.0f);
            final var method = NetStatic.class.getDeclaredMethod("adjustEmptyForLag", int.class, long.class, int.class);
            method.setAccessible(true);
            int adjusted = (Integer) method.invoke(null, 4, 5000L, 5);
            assertEquals("Lag = 2.0 should scale down empty to 2", 2, adjusted);
        }
    }

    @Test
    public void testLagHigh_ClampingToZero() throws Exception {
        try (MockedStatic<TickTask> tick = mockStatic(TickTask.class)) {
            tick.when(() -> TickTask.getLag(anyLong(), eq(true))).thenReturn(100f);
            final var method = NetStatic.class.getDeclaredMethod("adjustEmptyForLag", int.class, long.class, int.class);
            method.setAccessible(true);
            int adjusted = (Integer) method.invoke(null, 3, 5000L, 5);
            assertEquals("Extreme lag should not reduce below 0", 0, adjusted);
        }
    }
}
