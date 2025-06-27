package fr.neatmonster.nocheatplus.checks.inventory;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.bukkit.Material;
import org.junit.Before;
import org.junit.Test;

public class TestInstantEatInteraction {

    private sun.misc.Unsafe unsafe;
    private InventoryListener listener;

    @Before
    public void setup() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        unsafe = (sun.misc.Unsafe) f.get(null);
        listener = (InventoryListener) unsafe.allocateInstance(InventoryListener.class);
    }

    private void invokeRememberFood(InventoryData data, Material mat) throws Exception {
        Method m = InventoryListener.class.getDeclaredMethod("rememberFoodInteract", InventoryData.class, Material.class);
        m.setAccessible(true);
        m.invoke(listener, data, mat);
    }

    @Test
    public void testValidTimingSetsFood() throws Exception {
        InventoryData data = new InventoryData();
        data.instantEatInteract = System.currentTimeMillis() - 500;
        invokeRememberFood(data, Material.BREAD);
        assertEquals(Material.BREAD, data.instantEatFood);
    }

    @Test
    public void testInvalidTimingClearsFood() throws Exception {
        InventoryData data = new InventoryData();
        data.instantEatInteract = System.currentTimeMillis() - 900;
        invokeRememberFood(data, Material.APPLE);
        assertNull(data.instantEatFood);
    }
}
