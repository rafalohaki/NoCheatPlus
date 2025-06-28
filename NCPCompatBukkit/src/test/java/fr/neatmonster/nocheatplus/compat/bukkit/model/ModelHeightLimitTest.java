import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.Test;

import fr.neatmonster.nocheatplus.compat.bukkit.MCAccessBukkitModern;
import fr.neatmonster.nocheatplus.compat.bukkit.model.BukkitShapeModel;
import fr.neatmonster.nocheatplus.compat.bukkit.model.BukkitStatic;

public class ModelHeightLimitTest {

    @Test
    public void testStaticModelHeights() throws IllegalAccessException {
        for (Field field : MCAccessBukkitModern.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (!BukkitShapeModel.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            BukkitShapeModel model = (BukkitShapeModel) field.get(null);
            if (model instanceof BukkitStatic) {
                double[] shape = model.getShape(null, null, 0, 0, 0);
                for (int i = 5; i < shape.length; i += 6) {
                    assertTrue(field.getName() + " exceeds height 1.0", shape[i] <= 1.0);
                }
            }
        }
    }
}
