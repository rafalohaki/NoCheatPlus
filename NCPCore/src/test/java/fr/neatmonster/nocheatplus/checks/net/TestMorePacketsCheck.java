package fr.neatmonster.nocheatplus.checks.net;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
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
    public void testAdjustEmptyForLagVarious() throws Exception {
        java.lang.reflect.Method m = NetStatic.class.getDeclaredMethod(
                "adjustEmptyForLag", int.class, long.class, int.class);
        m.setAccessible(true);
        try (MockedStatic<TickTask> tick = mockStatic(TickTask.class)) {
            // High lag should reduce the empty count but stay within bounds.
            tick.when(() -> TickTask.getLag(5000L, true)).thenReturn(2.0f);
            int result = (int) m.invoke(null, 4, 5000L, 5);
            assertTrue(result >= 0 && result <= 5);

            // Low lag increases empty count yet remains clamped to winNum.
            tick.when(() -> TickTask.getLag(5000L, true)).thenReturn(0.5f);
            result = (int) m.invoke(null, 2, 5000L, 5);
            assertTrue(result >= 0 && result <= 5);

            // Normal lag should keep the value unchanged.
            tick.when(() -> TickTask.getLag(5000L, true)).thenReturn(1.0f);
            result = (int) m.invoke(null, 3, 5000L, 5);
            assertEquals(3, result);
        }
    }
}
