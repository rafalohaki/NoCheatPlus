package fr.neatmonster.nocheatplus.checks.fight;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.bukkit.Location;
import org.junit.Before;
import org.junit.Test;

public class ComputeTargetMoveInfoTest {

    private sun.misc.Unsafe unsafe;

    @Before
    public void setup() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        unsafe = (sun.misc.Unsafe) f.get(null);
    }

    private Object invokeCompute(FightListener listener, FightData data, Location loc, int tick, boolean worldChanged) throws Exception {
        Method m = FightListener.class.getDeclaredMethod("computeTargetMoveInfo", FightData.class, Location.class, int.class, boolean.class);
        m.setAccessible(true);
        return m.invoke(listener, data, loc, tick, worldChanged);
    }

    private int getTickAge(Object info) throws Exception {
        Class<?> c = info.getClass();
        Field f = c.getDeclaredField("tickAge");
        f.setAccessible(true);
        return f.getInt(info);
    }

    @Test
    public void testOutOfOrderTickReturnsDefault() throws Exception {
        FightListener listener = (FightListener) unsafe.allocateInstance(FightListener.class);
        FightData data = (FightData) unsafe.allocateInstance(FightData.class);
        data.lastAttackedX = 0.0;
        data.lastAttackedZ = 0.0;
        data.lastAttackTick = 10;
        Location loc = new Location(null, 0, 0, 0);
        Object info = invokeCompute(listener, data, loc, 5, false);
        assertEquals(0, getTickAge(info));
    }

    @Test
    public void testNegativeTickReturnsDefault() throws Exception {
        FightListener listener = (FightListener) unsafe.allocateInstance(FightListener.class);
        FightData data = (FightData) unsafe.allocateInstance(FightData.class);
        data.lastAttackedX = 0.0;
        data.lastAttackedZ = 0.0;
        data.lastAttackTick = 10;
        Location loc = new Location(null, 0, 0, 0);
        Object info = invokeCompute(listener, data, loc, -1, false);
        assertEquals(0, getTickAge(info));
    }
}
