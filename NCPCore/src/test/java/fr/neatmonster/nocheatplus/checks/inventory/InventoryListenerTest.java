package fr.neatmonster.nocheatplus.checks.inventory;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.bukkit.Material;
import org.junit.Before;
import org.junit.Test;

public class InventoryListenerTest {

    private sun.misc.Unsafe unsafe;

    @Before
    public void setup() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        unsafe = (sun.misc.Unsafe) f.get(null);
    }

    private InventoryListener newListener() throws Exception {
        return (InventoryListener) unsafe.allocateInstance(InventoryListener.class);
    }

    @Test
    public void testFoodNotSetForOldInteract() throws Exception {
        InventoryListener listener = newListener();
        InventoryData data = new InventoryData();
        Method m = InventoryListener.class.getDeclaredMethod("rememberFoodInteract", InventoryData.class, Material.class);
        m.setAccessible(true);

        long before = System.currentTimeMillis();
        data.instantEatInteract = before - 900; // older than threshold
        m.invoke(listener, data, Material.BREAD);
        long after = System.currentTimeMillis();

        assertNull(data.instantEatFood);
        assertEquals("Expected reset timestamp on slow interact", after, data.instantEatInteract, 50L);
    }

    @Test
    public void testFoodSetForRecentInteract() throws Exception {
        InventoryListener listener = newListener();
        InventoryData data = new InventoryData();
        Method m = InventoryListener.class.getDeclaredMethod("rememberFoodInteract", InventoryData.class, Material.class);
        m.setAccessible(true);

        data.instantEatInteract = System.currentTimeMillis() - 100; // within threshold
        m.invoke(listener, data, Material.APPLE);

        assertEquals(Material.APPLE, data.instantEatFood);
        assertTrue(data.instantEatInteract > 0);
    }
}
