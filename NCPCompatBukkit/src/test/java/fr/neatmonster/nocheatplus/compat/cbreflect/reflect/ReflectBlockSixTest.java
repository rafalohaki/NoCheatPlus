package fr.neatmonster.nocheatplus.compat.cbreflect.reflect;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.Test;

public class ReflectBlockSixTest {

    private static sun.misc.Unsafe getUnsafe() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (sun.misc.Unsafe) f.get(null);
    }

    private static String[] guess(Class<?> clazz) throws Exception {
        sun.misc.Unsafe u = getUnsafe();
        ReflectBlockSix instance = (ReflectBlockSix) u.allocateInstance(ReflectBlockSix.class);
        Method m = ReflectBlockSix.class.getDeclaredMethod("guessBoundsMethodNames", Class.class);
        m.setAccessible(true);
        return (String[]) m.invoke(instance, clazz);
    }

    public static class MockSuccess {
        public double d() { return 0; }
        public double a() { return 0; }
        public double f() { return 0; }
        public double c() { return 0; }
        public double e() { return 0; }
        public double b() { return 0; }
    }

    public static class MockTooFew {
        public double a() { return 0; }
        public double b() { return 0; }
        public double c() { return 0; }
        public double d() { return 0; }
        public double e() { return 0; }
    }

    public static class MockTooMany {
        public double a() { return 0; }
        public double b() { return 0; }
        public double c() { return 0; }
        public double d() { return 0; }
        public double e() { return 0; }
        public double f() { return 0; }
        public double g() { return 0; }
    }

    public static class MockMultiple {
        public double a() { return 0; }
        public double b() { return 0; }
        public double c() { return 0; }
        public double d() { return 0; }
        public double e() { return 0; }
        public double f() { return 0; }
        public double k() { return 0; }
        public double l() { return 0; }
        public double m() { return 0; }
        public double n() { return 0; }
        public double o() { return 0; }
        public double p() { return 0; }
    }

    @Test
    public void testGuessBoundsSuccess() throws Exception {
        String[] names = guess(MockSuccess.class);
        assertArrayEquals(new String[]{"a", "b", "c", "d", "e", "f"}, names);
    }

    @Test
    public void testGuessBoundsTooFew() throws Exception {
        assertNull(guess(MockTooFew.class));
    }

    @Test
    public void testGuessBoundsTooMany() throws Exception {
        assertNull(guess(MockTooMany.class));
    }

    @Test
    public void testGuessBoundsMultipleSequences() throws Exception {
        assertNull(guess(MockMultiple.class));
    }
}
