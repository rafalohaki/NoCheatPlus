/*
 * This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.neatmonster.nocheatplus.compat.bukkit.model;

import java.util.Set;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Wall;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.util.BoundingBox;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class BukkitWall implements BukkitShapeModel {

    private final double minXZ;
    private final double maxXZ;
    private final double height;
    private final double sideInset;
    private final double[] east;
    private final double[] north;
    private final double[] west;
    private final double[] south;
    private final double[] eastwest;
    private final double[] southnorth;

    public BukkitWall(double inset, double height) {
        this(inset, 1.0 - inset, Math.min(height, 1.0), 0.0);
    }

    public BukkitWall(double inset, double height, double sideInset) {
        this(inset, 1.0 - inset, Math.min(height, 1.0), sideInset);
    }

    public BukkitWall(double minXZ, double maxXZ, double height, double sideInset) {
        this.minXZ = minXZ;
        this.maxXZ = maxXZ;
        this.height = Math.min(height, 1.0);
        this.sideInset = sideInset;
        east = new double[] {maxXZ, 0.0, sideInset, 1.0, this.height, 1.0 - sideInset};
        north = new double[] {sideInset, 0.0, 0.0, 1.0 - sideInset, this.height, minXZ};
        west = new double[] {0.0, 0.0, sideInset, minXZ, this.height, 1.0 - sideInset};
        south = new double[] {sideInset, 0.0, maxXZ, 1.0 - sideInset, this.height, 1.0};
        eastwest = new double[] {0.0, 0.0, sideInset, 1.0, this.height, 1.0 - sideInset};
        southnorth = new double[] {sideInset, 0.0, 0.0, 1.0 - sideInset, this.height, 1.0};
    }

    @Override
    public double[] getShape(final BlockCache blockCache,
            final World world, final int x, final int y, final int z) {

        // Relevant: https://bugs.mojang.com/browse/MC-9565

        if (!Bukkit.isPrimaryThread()) {
            try {
                return Bukkit.getScheduler().callSyncMethod(
                        JavaPlugin.getProvidingPlugin(getClass()),
                        () -> computeShape(blockCache, world, x, y, z)).get();
            } catch (Exception e) {
                return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
            }
        }

        return computeShape(blockCache, world, x, y, z);
    }

    private double[] computeShape(final BlockCache blockCache,
            final World world, final int x, final int y, final int z) {
        final Block block = world.getBlockAt(x, y, z);
        final BlockState state = block.getState();
        final BlockData blockData = state.getBlockData();
        if (blockData instanceof MultipleFacing) {
            return getShapeForMultipleFacing((MultipleFacing) blockData);
        } else if (blockData instanceof Wall) {
            return getShapeForWall((Wall) blockData);
        }
        return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
    }

    @Override
    public int getFakeData(final BlockCache blockCache, 
            final World world, final int x, final int y, final int z) {
        return 0;
    }

    private double[] getShapeForMultipleFacing(final MultipleFacing fence) {
        final Set<BlockFace> faces = fence.getFaces();

        if (!faces.contains(BlockFace.UP) && faces.size() == 2) {
            if (faces.contains(BlockFace.SOUTH)) {
                return new double[] {sideInset, 0.0, 0.0, 1.0 - sideInset, height, 1.0};
            }
            if (faces.contains(BlockFace.WEST)) {
                return new double[] {0.0, 0.0, sideInset, 1.0, height, 1.0 - sideInset};
            }
        }

        final List<BoundingBox> boxes = new ArrayList<>();
        boxes.add(new BoundingBox(minXZ, 0.0, minXZ, maxXZ, height, maxXZ));
        for (BlockFace face : faces) {
            switch (face) {
                case EAST:
                    boxes.add(new BoundingBox(east[0], east[1], east[2], east[3], east[4], east[5]));
                    break;
                case NORTH:
                    boxes.add(new BoundingBox(north[0], north[1], north[2], north[3], north[4], north[5]));
                    break;
                case WEST:
                    boxes.add(new BoundingBox(west[0], west[1], west[2], west[3], west[4], west[5]));
                    break;
                case SOUTH:
                    boxes.add(new BoundingBox(south[0], south[1], south[2], south[3], south[4], south[5]));
                    break;
                default:
                    break;
            }
        }
        return toArray(boxes);
    }

    private double[] getShapeForWall(final Wall wall) {
        final List<BoundingBox> boxes = new ArrayList<>();
        if (wall.isUp()) {
            boxes.add(new BoundingBox(minXZ, 0.0, minXZ, maxXZ, height, maxXZ));
            if (!wall.getHeight(BlockFace.WEST).equals(Wall.Height.NONE)) {
                boxes.add(new BoundingBox(west[0], west[1], west[2], west[3], west[4], west[5]));
            }
            if (!wall.getHeight(BlockFace.EAST).equals(Wall.Height.NONE)) {
                boxes.add(new BoundingBox(east[0], east[1], east[2], east[3], east[4], east[5]));
            }
            if (!wall.getHeight(BlockFace.NORTH).equals(Wall.Height.NONE)) {
                boxes.add(new BoundingBox(north[0], north[1], north[2], north[3], north[4], north[5]));
            }
            if (!wall.getHeight(BlockFace.SOUTH).equals(Wall.Height.NONE)) {
                boxes.add(new BoundingBox(south[0], south[1], south[2], south[3], south[4], south[5]));
            }
        } else {
            if (!wall.getHeight(BlockFace.WEST).equals(Wall.Height.NONE)
                    && !wall.getHeight(BlockFace.EAST).equals(Wall.Height.NONE)) {
                boxes.add(new BoundingBox(eastwest[0], eastwest[1], eastwest[2], eastwest[3], eastwest[4], eastwest[5]));
            }
            if (!wall.getHeight(BlockFace.NORTH).equals(Wall.Height.NONE)
                    && !wall.getHeight(BlockFace.SOUTH).equals(Wall.Height.NONE)) {
                boxes.add(new BoundingBox(southnorth[0], southnorth[1], southnorth[2], southnorth[3], southnorth[4], southnorth[5]));
            }
        }
        if (boxes.isEmpty()) {
            return new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
        }
        return toArray(boxes);
    }

    private double[] toArray(final List<BoundingBox> boxes) {
        final double[] res = new double[boxes.size() * 6];
        for (int i = 0; i < boxes.size(); i++) {
            final BoundingBox b = boxes.get(i);
            res[i * 6] = b.getMinX();
            res[i * 6 + 1] = b.getMinY();
            res[i * 6 + 2] = b.getMinZ();
            res[i * 6 + 3] = b.getMaxX();
            res[i * 6 + 4] = b.getMaxY();
            res[i * 6 + 5] = b.getMaxZ();
        }
        return res;
    }

}
