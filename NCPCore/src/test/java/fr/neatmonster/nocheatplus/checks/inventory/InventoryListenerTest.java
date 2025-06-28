package fr.neatmonster.nocheatplus.checks.inventory;

import static org.junit.Assert.*;

import org.bukkit.Material;
import org.junit.Test;

public class InventoryListenerTest {

    @Test
    public void testFoodNotSetForOldInteract() throws Exception {
        InventoryData data = new InventoryData();

        long before = System.currentTimeMillis();
        data.instantEatInteract = before - 900; // older than threshold
        InventoryInteractHelper.applyFoodInteract(data, Material.BREAD, System.currentTimeMillis());
        long after = System.currentTimeMillis();

        assertNull(data.instantEatFood);
        assertEquals("Expected reset timestamp on slow interact", after, data.instantEatInteract, 50L);
    }

    @Test
    public void testFoodSetForRecentInteract() throws Exception {
        InventoryData data = new InventoryData();

        data.instantEatInteract = System.currentTimeMillis() - 100; // within threshold
        InventoryInteractHelper.applyFoodInteract(data, Material.APPLE, System.currentTimeMillis());

        assertEquals(Material.APPLE, data.instantEatFood);
        assertTrue(data.instantEatInteract > 0);
    }
}
