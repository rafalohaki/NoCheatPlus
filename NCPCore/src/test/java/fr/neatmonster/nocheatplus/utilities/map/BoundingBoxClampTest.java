package fr.neatmonster.nocheatplus.utilities.map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

public class BoundingBoxClampTest {

    @Test
    public void primaryBoxClampedToOneBlock() {
        FakeBlockCache cache = new FakeBlockCache();
        cache.set(0, 0, 0, Material.STONE, 0, new double[] {0.0, 0.0, 0.0, 1.0, 1.5, 1.0});
        BlockCache.IBlockCacheNode node = cache.getOrCreateBlockCacheNode(0, 0, 0, true);

        boolean resultAbove = BlockProperties.collidesBlock(
                cache,
                0.2, 1.05, 0.2,
                0.8, 1.2, 0.8,
                0, 0, 0,
                node,
                null,
                0);
        boolean resultBelow = BlockProperties.collidesBlock(
                cache,
                0.2, 0.5, 0.2,
                0.8, 0.8, 0.8,
                0, 0, 0,
                node,
                null,
                0);
        assertFalse(resultAbove, "Intersection above 1.0 should be ignored");
        assertTrue(resultBelow, "Intersection below 1.0 should be detected");
        cache.cleanup();
    }

    @Test
    public void subBoxClampedToOneBlock() {
        FakeBlockCache cache = new FakeBlockCache();
        double[] bounds = new double[] {
                0.0, 0.0, 0.0, 1.0, 1.0, 1.0,
                0.0, 0.0, 0.0, 1.0, 1.5, 1.0 };
        cache.set(0, 0, 0, Material.STONE, 0, bounds);
        BlockCache.IBlockCacheNode node = cache.getOrCreateBlockCacheNode(0, 0, 0, true);

        boolean resultAbove = BlockProperties.collidesBlock(
                cache,
                0.2, 1.05, 0.2,
                0.8, 1.2, 0.8,
                0, 0, 0,
                node,
                null,
                0);
        boolean resultBelow = BlockProperties.collidesBlock(
                cache,
                0.2, 0.2, 0.2,
                0.8, 0.8, 0.8,
                0, 0, 0,
                node,
                null,
                0);
        assertFalse(resultAbove, "Sub box above 1.0 should be ignored");
        assertTrue(resultBelow, "Sub box intersection below 1.0 should be detected");
        cache.cleanup();
    }
}
