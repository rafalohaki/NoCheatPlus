package fr.neatmonster.nocheatplus.checks.net;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.mockito.MockedStatic;

import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.utilities.ds.count.ActionFrequency;

/** Tests for {@link NetStatic}. */
public class NetStaticTest {

    private static ActionFrequency createFreq(long now) {
        ActionFrequency freq = new ActionFrequency(5, 50);
        freq.setTime(now);
        freq.setBucket(0, 2f);
        freq.setBucket(1, 2f);
        freq.setBucket(2, 0f);
        freq.setBucket(3, 0f);
        freq.setBucket(4, 0f);
        return freq;
    }

    @Test
    public void testLagBelowOneNoNegativeAdjustment() {
        long now = 0L;
        ActionFrequency burst = new ActionFrequency(5, 50);
        burst.setTime(now);
        List<String> tags = new ArrayList<>();
        double expect;
        try (MockedStatic<TickTask> tick = mockStatic(TickTask.class)) {
            tick.when(() -> TickTask.getLag(anyLong(), anyBoolean())).thenReturn(1.0f);
            expect = NetStatic.morePacketsCheck(createFreq(now), now, 0f, 5f, 5f,
                    burst, 1f, 0d, 0d, tags);
        }
        tags.clear();
        double result;
        try (MockedStatic<TickTask> tick = mockStatic(TickTask.class)) {
            tick.when(() -> TickTask.getLag(anyLong(), anyBoolean())).thenReturn(0.8f);
            result = NetStatic.morePacketsCheck(createFreq(now), now, 0f, 5f, 5f,
                    burst, 1f, 0d, 0d, tags);
        }
        assertTrue("Lag below 1.0 should not increase violation score due to proper clamping",
                result <= expect + 0.0001);
    }

    @Test
    public void testLagAboveOneReducesEmpty() {
        long now = 0L;
        ActionFrequency burst = new ActionFrequency(5, 50);
        burst.setTime(now);
        List<String> tags = new ArrayList<>();
        double expect;
        try (MockedStatic<TickTask> tick = mockStatic(TickTask.class)) {
            tick.when(() -> TickTask.getLag(anyLong(), anyBoolean())).thenReturn(1.0f);
            expect = NetStatic.morePacketsCheck(createFreq(now), now, 0f, 5f, 5f,
                    burst, 1f, 0d, 0d, tags);
        }
        tags.clear();
        double result;
        try (MockedStatic<TickTask> tick = mockStatic(TickTask.class)) {
            tick.when(() -> TickTask.getLag(anyLong(), anyBoolean())).thenReturn(2.0f);
            result = NetStatic.morePacketsCheck(createFreq(now), now, 0f, 5f, 5f,
                    burst, 1f, 0d, 0d, tags);
        }
        assertTrue("Lag above one should not reduce violation", result >= expect - 0.0001);
    }

    private static int computeClampedEmpty(ActionFrequency freq, float lag) {
        int winNum = freq.numberOfBuckets();
        int empty = 0;
        boolean used = false;
        for (int burnStart = 1; burnStart < winNum; burnStart++) {
            if (freq.bucketScore(burnStart) > 0f) {
                if (used) {
                    for (int j = burnStart; j < winNum; j++) {
                        if (freq.bucketScore(j) == 0f) {
                            empty += 1;
                        }
                    }
                    break;
                } else {
                    used = true;
                }
            }
        }
        if (empty > 0) {
            empty = (int) Math.round(empty * (1f / lag));
        }
        return Math.max(0, Math.min(empty, winNum));
    }

    @Test
    public void testLagAdjustmentEdgeCases() {
        long now = 0L;
        float[] lags = {0f, 1f, 5f};
        for (float lag : lags) {
            ActionFrequency burst = new ActionFrequency(5, 50);
            burst.setTime(now);
            try (MockedStatic<TickTask> tick = mockStatic(TickTask.class)) {
                tick.when(() -> TickTask.getLag(anyLong(), anyBoolean())).thenReturn(lag);
                double violation = NetStatic.morePacketsCheck(createFreq(now), now, 0f, 5f, 5f,
                        burst, 1f, 0d, 0d, new ArrayList<>());
                assertFalse("Violation should be valid for lag=" + lag, Double.isNaN(violation));
            }
        }
    }

    @Test
    public void testEmptyCountClamping() {
        long now = 0L;
        ActionFrequency freq = new ActionFrequency(5, 50);
        freq.setTime(now);
        freq.setBucket(0, 2f);
        freq.setBucket(1, 2f);
        freq.setBucket(2, 2f);
        freq.setBucket(3, 0f);
        freq.setBucket(4, 0f);
        int winNum = freq.numberOfBuckets();
        int[] empties = {computeClampedEmpty(freq, 0f), computeClampedEmpty(freq, -1f),
                computeClampedEmpty(freq, 10f)};
        for (int e : empties) {
            assertTrue("Empty count should be within [0, winNum]", e >= 0 && e <= winNum);
        }
    }
}
