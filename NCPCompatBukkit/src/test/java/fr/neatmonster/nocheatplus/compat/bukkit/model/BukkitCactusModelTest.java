package fr.neatmonster.nocheatplus.compat.bukkit.model;

import static org.junit.Assert.assertArrayEquals;

import java.lang.reflect.Field;

import org.junit.Test;

import fr.neatmonster.nocheatplus.compat.bukkit.MCAccessBukkitModern;
import fr.neatmonster.nocheatplus.compat.bukkit.model.BukkitShapeModel;

public class BukkitCactusModelTest {

    @Test
    public void testCactusModelBounds() throws Exception {
        Field f = MCAccessBukkitModern.class.getDeclaredField("MODEL_CACTUS");
        f.setAccessible(true);
        BukkitShapeModel model = (BukkitShapeModel) f.get(null);
        double[] bounds = model.getShape(null, null, 0, 0, 0);
        assertArrayEquals(new double[] {0.0625, 0.0, 0.0625, 0.9375, 1.0, 0.9375}, bounds, 0.0);
    }
}
