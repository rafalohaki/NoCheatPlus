package fr.neatmonster.nocheatplus.compat.bukkit.model;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import fr.neatmonster.nocheatplus.compat.bukkit.MCAccessBukkitModern;

public class ShapeModelHeightTest {

    @Test
    public void testHeightsWithinBounds() throws Exception {
        Set<BukkitShapeModel> models = new HashSet<>();
        for (Field f : MCAccessBukkitModern.class.getDeclaredFields()) {
            if (BukkitShapeModel.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                models.add((BukkitShapeModel) f.get(null));
            }
        }
        for (BukkitShapeModel model : models) {
            if (model instanceof BukkitFence || model instanceof BukkitGate || model instanceof BukkitWall) {
                Field hf = model.getClass().getDeclaredField("height");
                hf.setAccessible(true);
                double height = hf.getDouble(model);
                assertTrue(height <= 1.0 + 1e-9);
            } else if (model instanceof BukkitStatic) {
                double[] bounds = ((BukkitStatic) model).getShape(null, null, 0, 0, 0);
                for (int i = 5; i < bounds.length; i += 6) {
                    assertTrue(bounds[i] <= 1.0 + 1e-9);
                }
            }
        }
    }
}
