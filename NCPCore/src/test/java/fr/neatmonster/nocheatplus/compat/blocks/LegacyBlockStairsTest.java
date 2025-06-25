package fr.neatmonster.nocheatplus.compat.blocks;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.Test;

public class LegacyBlockStairsTest {

    private boolean invokeSameShape(double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            double tminX, double tminY, double tminZ,
            double tmaxX, double tmaxY, double tmaxZ) throws Exception {
        LegacyBlocks.BlockStairs stairs = new LegacyBlocks.BlockStairs();
        Method m = LegacyBlocks.BlockStairs.class.getDeclaredMethod("sameshape", double.class, double.class, double.class,
                double.class, double.class, double.class, double.class, double.class, double.class,
                double.class, double.class, double.class);
        m.setAccessible(true);
        return (boolean) m.invoke(stairs, minX, minY, minZ, maxX, maxY, maxZ,
                tminX, tminY, tminZ, tmaxX, tmaxY, tmaxZ);
    }

    @Test
    public void testSameShapeExact() throws Exception {
        assertTrue(invokeSameShape(0.0, 0.0, 0.0, 1.0, 1.0, 1.0,
                0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
    }

    @Test
    public void testSameShapeTolerance() throws Exception {
        assertTrue(invokeSameShape(0.0, 0.0, 0.0, 1.0, 1.0, 1.0,
                0.0, 0.0, 0.0, 1.0 + 1e-10, 1.0, 1.0));
    }

    @Test
    public void testDifferentShape() throws Exception {
        assertFalse(invokeSameShape(0.0, 0.0, 0.0, 1.0, 1.0, 1.0,
                0.0, 0.0, 0.0, 1.0 + 1e-6, 1.0, 1.0));
    }
}
