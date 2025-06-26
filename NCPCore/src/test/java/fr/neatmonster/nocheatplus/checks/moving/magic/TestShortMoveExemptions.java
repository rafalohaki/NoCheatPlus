package fr.neatmonster.nocheatplus.checks.moving.magic;

import static org.junit.Assert.*;

import java.lang.reflect.Field;

import org.junit.Test;

import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.workaround.WRPT;
import fr.neatmonster.nocheatplus.workaround.IWorkaround;
import fr.neatmonster.nocheatplus.workaround.IWorkaroundRegistry.WorkaroundSet;
import fr.neatmonster.nocheatplus.utilities.ds.count.acceptdeny.AcceptDenyCounter;
import fr.neatmonster.nocheatplus.utilities.ds.count.acceptdeny.IAcceptDenyCounter;

public class TestShortMoveExemptions {

    private static class DummyWorkaround implements IWorkaround {
        private final String id;
        DummyWorkaround(String id) { this.id = id; }
        @Override public String getId() { return id; }
        @Override public boolean use() { return false; }
        @Override public boolean canUse() { return false; }
        @Override public IAcceptDenyCounter getAllTimeCounter() { return new AcceptDenyCounter(); }
        @Override public IWorkaround getNewInstance() { return new DummyWorkaround(id); }
    }

    private static WorkaroundSet createWorkaroundSet() {
        IWorkaround[] blueprints = new IWorkaround[] {
                new DummyWorkaround(WRPT.W_M_SF_SHORTMOVE_1),
                new DummyWorkaround(WRPT.W_M_SF_SHORTMOVE_2),
                new DummyWorkaround(WRPT.W_M_SF_SHORTMOVE_3),
                new DummyWorkaround(WRPT.W_M_SF_SHORTMOVE_4)
        };
        return new WorkaroundSet(blueprints, null);
    }

    private static MovingData newData(WorkaroundSet ws) throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        sun.misc.Unsafe u = (sun.misc.Unsafe) f.get(null);
        MovingData data = (MovingData) u.allocateInstance(MovingData.class);
        Field wsField = MovingData.class.getDeclaredField("ws");
        wsField.setAccessible(true);
        wsField.set(data, ws);
        return data;
    }

    @Test
    public void testFireworkBoostTransition() throws Exception {
        WorkaroundSet ws = createWorkaroundSet();
        MovingData data = newData(ws);
        data.fireworksBoostDuration = 5;
        data.keepfrictiontick = -1;
        PlayerMoveData lastMove = new PlayerMoveData();
        lastMove.toIsValid = true;
        PlayerMoveData thisMove = new PlayerMoveData();
        boolean result = AirWorkarounds.shortMoveExemptions(0.1, 0.0, lastMove, data, null, null, 0L, false, 1.0, 0.0, null, thisMove);
        assertTrue(result);
        assertEquals(0, data.keepfrictiontick);
    }
}
