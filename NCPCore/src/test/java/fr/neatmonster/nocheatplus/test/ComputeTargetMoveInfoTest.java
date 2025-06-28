package fr.neatmonster.nocheatplus.test;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.Before;
import org.junit.Test;

import fr.neatmonster.nocheatplus.checks.fight.FightData;
import fr.neatmonster.nocheatplus.checks.fight.FightListener;

public class ComputeTargetMoveInfoTest {

    private static sun.misc.Unsafe getUnsafe() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (sun.misc.Unsafe) f.get(null);
    }

    private static World createWorld() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class[]{World.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) return "dummy";
                    Class<?> r = method.getReturnType();
                    if (r == boolean.class) return false;
                    if (r.isPrimitive()) return 0;
                    return null;
                });
    }

    private FightListener listener;
    private Method compute;
    private Location location;
    private sun.misc.Unsafe unsafe;

    @Before
    public void setup() throws Exception {
        unsafe = getUnsafe();
        listener = (FightListener) unsafe.allocateInstance(FightListener.class);
        compute = FightListener.class.getDeclaredMethod("computeTargetMoveInfo",
                FightData.class, Location.class, int.class, boolean.class);
        compute.setAccessible(true);
        location = new Location(createWorld(), 0.0, 64.0, 0.0);
    }

    private FightData newData(int lastAttackTick) throws Exception {
        FightData data = (FightData) unsafe.allocateInstance(FightData.class);
        data.lastAttackTick = lastAttackTick;
        data.lastAttackedX = 0.0;
        data.lastAttackedZ = 0.0;
        return data;
    }

    @Test
    public void testOutOfOrderTickReturnsDefault() throws Exception {
        FightData data = newData(10);
        Object info = compute.invoke(listener, data, location, 5, false);
        Field f = info.getClass().getDeclaredField("tickAge");
        f.setAccessible(true);
        assertEquals(0, f.getInt(info));
    }

    @Test
    public void testNegativeTickReturnsDefault() throws Exception {
        FightData data = newData(0);
        Object info = compute.invoke(listener, data, location, -1, false);
        Field f = info.getClass().getDeclaredField("tickAge");
        f.setAccessible(true);
        assertEquals(0, f.getInt(info));
    }

    @Test
    public void testValidTickReturnsNonZeroAge() throws Exception {
        FightData data = newData(5);
        Object info = compute.invoke(listener, data, location, 10, false);
        Field f = info.getClass().getDeclaredField("tickAge");
        f.setAccessible(true);
        assertEquals(5, f.getInt(info));
    }
}
