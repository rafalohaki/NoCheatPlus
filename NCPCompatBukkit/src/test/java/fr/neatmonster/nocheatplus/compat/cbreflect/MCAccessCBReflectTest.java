package fr.neatmonster.nocheatplus.compat.cbreflect;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MCAccessCBReflectTest {

    @Test
    public void testValidBounds() {
        double[] bounds = {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
        assertTrue(MCAccessCBReflect.isValidBounds(bounds));
    }

    @Test
    public void testInvalidLength() {
        double[] bounds = {0.0, 0.0, 0.0};
        assertFalse(MCAccessCBReflect.isValidBounds(bounds));
    }

    @Test
    public void testBadCoordinate() {
        double[] bounds = {0.0, Double.NaN, 0.0, 1.0, 1.0, 1.0};
        assertFalse(MCAccessCBReflect.isValidBounds(bounds));
    }

    @Test
    public void testExtremeCoordinate() {
        double[] bounds = {0.0, 0.0, 0.0, 1.0e10, 1.0, 1.0};
        assertFalse(MCAccessCBReflect.isValidBounds(bounds));
    }

    @Test
    public void testReversedBounds() {
        double[] bounds = {1.0, 0.0, 0.0, 0.0, 1.0, 1.0};
        assertFalse(MCAccessCBReflect.isValidBounds(bounds));
    }
}
