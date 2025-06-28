package fr.neatmonster.nocheatplus.compat.bukkit.model;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.Mockito.*;

import java.util.Collections;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.block.ShulkerBox;
import org.junit.Test;

import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class BukkitModelHeightCapTest {

    @Test
    public void testFenceHeightCapped() {
        BukkitFence fence = new BukkitFence(0.375, 1.5);
        World world = mock(World.class);
        Block block = mock(Block.class);
        BlockState state = mock(BlockState.class);
        MultipleFacing data = mock(MultipleFacing.class, withSettings().extraInterfaces(BlockData.class));
        when(world.getBlockAt(0, 0, 0)).thenReturn(block);
        when(block.getState()).thenReturn(state);
        when(state.getBlockData()).thenReturn((BlockData) data);
        when(data.getFaces()).thenReturn(Collections.emptySet());

        double[] expected = {0.375, 0.0, 0.375, 0.625, 1.0, 0.625};
        double[] res = fence.getShape(mock(BlockCache.class), world, 0, 0, 0);
        assertArrayEquals(expected, res, 0.0);
    }

    @Test
    public void testGateHeightCapped() {
        BukkitGate gate = new BukkitGate(0.375, 1.5);
        World world = mock(World.class);
        Block block = mock(Block.class);
        BlockState state = mock(BlockState.class);
        BlockData data = mock(BlockData.class, withSettings().extraInterfaces(Directional.class));
        when(world.getBlockAt(0, 0, 0)).thenReturn(block);
        when(block.getState()).thenReturn(state);
        when(state.getBlockData()).thenReturn(data);
        when(((Directional) data).getFacing()).thenReturn(org.bukkit.block.BlockFace.EAST);

        double[] expected = {0.375, 0.0, 0.0, 0.625, 1.0, 1.0};
        double[] res = gate.getShape(mock(BlockCache.class), world, 0, 0, 0);
        assertArrayEquals(expected, res, 0.0);
    }

    @Test
    public void testShulkerBoxHeightCapped() {
        BukkitShulkerBox model = new BukkitShulkerBox();
        World world = mock(World.class);
        Block block = mock(Block.class);
        ShulkerBox state = mock(ShulkerBox.class);
        Inventory inv = mock(Inventory.class);
        HumanEntity viewer = mock(HumanEntity.class);
        when(world.getBlockAt(0,0,0)).thenReturn(block);
        when(block.getState()).thenReturn(state);
        when(state.getInventory()).thenReturn(inv);
        when(inv.getViewers()).thenReturn(Collections.singletonList(viewer));

        double[] expected = {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
        double[] res = model.getShape(mock(BlockCache.class), world, 0, 0, 0);
        assertArrayEquals(expected, res, 0.0);
    }
}
