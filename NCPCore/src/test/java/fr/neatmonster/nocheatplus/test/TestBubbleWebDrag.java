package fr.neatmonster.nocheatplus.test;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.moving.player.SurvivalFly;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;

import org.junit.Before;
import org.junit.Test;

public class TestBubbleWebDrag {

    static class SimpleHandle<T> implements IGenericInstanceHandle<T> {
        private final T handle;
        SimpleHandle(T h){ this.handle = h; }
        @Override public T getHandle(){ return handle; }
        @Override public void disableHandle(){}
    }

    private static void setupAPI() throws Exception {
        Object api = Proxy.newProxyInstance(NCPAPIProvider.class.getClassLoader(),
                new Class<?>[]{fr.neatmonster.nocheatplus.components.NoCheatPlusAPI.class},
                (p,m,a) -> {
                    if ("getBlockChangeTracker".equals(m.getName())) return new BlockChangeTracker();
                    if ("getGenericInstanceHandle".equals(m.getName())) return new SimpleHandle<>(null);
                    Class<?> r = m.getReturnType();
                    if (r == boolean.class) return false;
                    if (r.isPrimitive()) return 0;
                    return null;
                });
        Field f = NCPAPIProvider.class.getDeclaredField("noCheatPlusAPI");
        f.setAccessible(true);
        f.set(null, api);
    }

    @Before
    public void init() throws Exception {
        setupAPI();
    }

    // Helper to centralize reflective access
    private Method getBubbleWebDragMethod() throws Exception {
        Method m = SurvivalFly.class.getDeclaredMethod("vDistBubbleWebDrag", double.class, double.class);
        m.setAccessible(true);
        return m;
    }

    // Computes the bubble-web descend limit based on Magic constants
    private double calculateExpectedLimit() {
        return -fr.neatmonster.nocheatplus.checks.moving.magic.Magic.bubbleStreamDescend
                * fr.neatmonster.nocheatplus.checks.moving.magic.Magic.FRICTION_MEDIUM_WATER;
    }

    @Test
    public void testDragViolation() throws Exception {
        SurvivalFly sf = new SurvivalFly();
        Method m = getBubbleWebDragMethod();
        // Velocity -0.5 exceeds the bubble stream limit when combined with prior velocity -0.3
        double[] res = (double[]) m.invoke(sf, -0.5, -0.3);
        double limit = calculateExpectedLimit();
        assertEquals(limit, res[0], 1e-6);
        assertEquals(Math.abs(-0.5 - limit), res[1], 1e-6);
    }

    @Test
    public void testDragNoViolation() throws Exception {
        SurvivalFly sf = new SurvivalFly();
        Method m = getBubbleWebDragMethod();
        // Velocity -0.47 remains within the bubble stream limit for prior velocity -0.3
        double[] res = (double[]) m.invoke(sf, -0.47, -0.3);
        double limit = calculateExpectedLimit();
        assertEquals(limit, res[0], 1e-6);
        assertEquals(0.0, res[1], 1e-6);
    }
}
