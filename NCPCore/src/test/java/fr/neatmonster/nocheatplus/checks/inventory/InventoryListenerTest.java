package fr.neatmonster.nocheatplus.checks.inventory;

import static org.junit.Assert.*;

import org.bukkit.Material;
import org.junit.Test;

public class InventoryListenerTest {

    @Test
    public void testFoodNotSetForOldInteract() throws Exception {
        InventoryData data = new InventoryData();

        long before = System.currentTimeMillis();
        data.eatTracker.setLast(before - 900); // older than threshold
        data.eatTracker.updateAndQualifies(System.currentTimeMillis());
        long after = System.currentTimeMillis();

        assertNull(data.instantEatFood);
        assertEquals("Expected reset timestamp on slow interact", after, data.eatTracker.getLast(), 50L);
    }

    @Test
    public void testFoodSetForRecentInteract() throws Exception {
        InventoryData data = new InventoryData();

        data.eatTracker.setLast(System.currentTimeMillis() - 100); // within threshold
        if (data.eatTracker.updateAndQualifies(System.currentTimeMillis())) {
            data.instantEatFood = Material.APPLE;
        }

        assertEquals(Material.APPLE, data.instantEatFood);
        assertTrue(data.eatTracker.getLast() > 0);
    }
}
