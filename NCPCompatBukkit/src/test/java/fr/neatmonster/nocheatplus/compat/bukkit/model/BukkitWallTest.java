package fr.neatmonster.nocheatplus.compat.bukkit.model;

import static org.junit.Assert.assertArrayEquals;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Wall;
import org.junit.Test;
import static org.mockito.Mockito.*;

public class BukkitWallTest {

    @Test
    public void testMultipleFacingTwoFaces() {
        BukkitWall wall = new BukkitWall(0.25, 1.5, 0.3125);
        MultipleFacing data = mock(MultipleFacing.class);
        Set<BlockFace> faces = EnumSet.of(BlockFace.SOUTH, BlockFace.EAST);
        when(data.getFaces()).thenReturn(faces);
        double[] expected = {0.3125, 0.0, 0.0, 1.0 - 0.3125, 1.0, 1.0};
        double[] res = invokeMultipleFacing(wall, data);
        assertArrayEquals(expected, res, 0.0);
    }

    @Test
    public void testWallUp() {
        BukkitWall wall = new BukkitWall(0.25, 1.5, 0.3125);
        Wall data = mock(Wall.class);
        when(data.isUp()).thenReturn(true);
        when(data.getHeight(BlockFace.WEST)).thenReturn(Wall.Height.LOW);
        when(data.getHeight(BlockFace.EAST)).thenReturn(Wall.Height.NONE);
        when(data.getHeight(BlockFace.NORTH)).thenReturn(Wall.Height.NONE);
        when(data.getHeight(BlockFace.SOUTH)).thenReturn(Wall.Height.NONE);
        double[] expected = {0.25,0.0,0.25,0.75,1.0,0.75,0.0,0.0,0.3125,0.25,1.0,0.6875};
        double[] res = invokeWall(wall, data);
        assertArrayEquals(expected, res, 0.0);
    }

    private double[] invokeMultipleFacing(BukkitWall wall, MultipleFacing data) {
        try {
            java.lang.reflect.Method m = BukkitWall.class.getDeclaredMethod("getShapeForMultipleFacing", MultipleFacing.class);
            m.setAccessible(true);
            return (double[]) m.invoke(wall, data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private double[] invokeWall(BukkitWall wall, Wall data) {
        try {
            java.lang.reflect.Method m = BukkitWall.class.getDeclaredMethod("getShapeForWall", Wall.class);
            m.setAccessible(true);
            return (double[]) m.invoke(wall, data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
