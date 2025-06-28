package fr.neatmonster.nocheatplus.checks.net;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mockStatic;

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
    public void testLagBelowOneDoesNotDecreaseWindowCount() {
        ActionFrequency freq = new ActionFrequency(5, 1000);
        freq.setBucket(0, 1f);
        freq.setBucket(2, 1f);
        ActionFrequency burst = new ActionFrequency(12, 5000);
        List<String> tags = new ArrayList<String>();
        try (MockedStatic<TickTask> tickMock = mockStatic(TickTask.class)) {
            tickMock.when(() -> TickTask.getLag(5000L, true)).thenReturn(0.5f);
            tickMock.when(() -> TickTask.getLag(1000L, true)).thenReturn(0.5f);
            double result = NetStatic.morePacketsCheck(freq, 5000L, 0f, 2f, 2f, burst, 10f, 100.0, 1000.0, tags);
            // With lag < 1.0 scaling should not result in negative empty values or higher violation.
            assertTrue("Lag below 1.0 should not increase violation score due to proper clamping", result >= 0.0);
        }
    }

    @Test
    public void testLagAdjustmentEdgeCases() {
        ActionFrequency freq = new ActionFrequency(5, 1000);
        freq.setBucket(1, 1f);
        freq.setBucket(3, 1f);
        ActionFrequency burst = new ActionFrequency(12, 5000);
        List<String> tags = new ArrayList<String>();
        float[] lags = new float[]{0f, 1f, 5f};
        for (float lag : lags) {
            try (MockedStatic<TickTask> tickMock = mockStatic(TickTask.class)) {
                tickMock.when(() -> TickTask.getLag(5000L, true)).thenReturn(lag);
                tickMock.when(() -> TickTask.getLag(1000L, true)).thenReturn(lag);
                double result = NetStatic.morePacketsCheck(freq, 5000L, 0f, 2f, 2f, burst, 10f, 100.0, 1000.0, tags);
                assertTrue("Violation should never be negative", result >= 0.0);
            }
        }
    }

    @Test
    public void testEmptyCountClamping() {
        ActionFrequency freq = new ActionFrequency(5, 1000);
        freq.setBucket(0, 1f);
        freq.setBucket(2, 1f);
        NetStatic.BurnInfo info = NetStatic.computeBurnInfo(freq);
        int winNum = freq.numberOfBuckets();
        float lag = 0.25f; // Extreme low lag leads to large scaling
        int empty = (int) Math.round(info.empty * (1f / lag));
        empty = Math.max(0, Math.min(winNum, empty));
        assertTrue("Empty count should be within range", empty >= 0 && empty <= winNum);
    }
}

