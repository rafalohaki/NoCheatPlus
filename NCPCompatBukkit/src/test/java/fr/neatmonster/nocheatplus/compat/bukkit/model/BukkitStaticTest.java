package fr.neatmonster.nocheatplus.compat.bukkit.model;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class BukkitStaticTest {

    @Test
    public void testOfHeightValid() {
        assertNotNull(BukkitStatic.ofHeight(0.0));
        assertNotNull(BukkitStatic.ofHeight(0.5));
        assertNotNull(BukkitStatic.ofHeight(1.0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOfHeightBelowZero() {
        BukkitStatic.ofHeight(-0.1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOfHeightAboveOne() {
        BukkitStatic.ofHeight(1.1);
    }

    @Test
    public void testOfInsetAndHeightValid() {
        assertNotNull(BukkitStatic.ofInsetAndHeight(0.1, 0.5));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOfInsetAndHeightInvalidLow() {
        BukkitStatic.ofInsetAndHeight(0.0, -0.01);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOfInsetAndHeightInvalidHigh() {
        BukkitStatic.ofInsetAndHeight(0.0, 1.01);
    }
}
