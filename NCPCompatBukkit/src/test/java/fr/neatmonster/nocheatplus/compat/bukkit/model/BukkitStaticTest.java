package fr.neatmonster.nocheatplus.compat.bukkit.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class BukkitStaticTest {

    @Test
    public void testOfHeightValid() {
        BukkitStatic model = BukkitStatic.ofHeight(0.5);
        assertNotNull(model);
        double[] expected = {0.0, 0.0, 0.0, 1.0, 0.5, 1.0};
        assertArrayEquals(expected, model.getShape(null, null, 0, 0, 0), 0.0);
    }

    @Test
    public void testOfHeightInvalid() {
        assertThrows(IllegalArgumentException.class, () -> BukkitStatic.ofHeight(0.0));
    }

    @Test
    public void testOfInsetAndHeightValid() {
        BukkitStatic model = BukkitStatic.ofInsetAndHeight(0.25, 1.0);
        assertNotNull(model);
        double[] expected = {0.25, 0.0, 0.25, 0.75, 1.0, 0.75};
        assertArrayEquals(expected, model.getShape(null, null, 0, 0, 0), 0.0);
    }

    @Test
    public void testOfInsetAndHeightInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> BukkitStatic.ofInsetAndHeight(0.25, -0.1));
    }
}
