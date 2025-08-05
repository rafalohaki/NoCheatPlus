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
package fr.neatmonster.nocheatplus.compat.bukkit;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.util.BoundingBox;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;

public class BlockCacheBukkit extends BlockCache {

    protected World world;

    /** Temporary use. Use LocUtil.clone before passing on. Call setWorld(null) after use. */
    protected final Location useLoc = new Location(null, 0, 0, 0);

    /** Tolerance for entity standing detection */
    private static final double STANDING_TOLERANCE = 0.7;
    
    /** Search radius for nearby entities */
    private static final double ENTITY_SEARCH_RADIUS = 2.0;

    public BlockCacheBukkit(World world) {
        setAccess(world);
    }

    @Override
    public BlockCache setAccess(World world) {
        this.world = world;
        if (world != null) {
            this.maxBlockY = world.getMaxHeight() - 1;
            this.minBlockY = BlockProperties.getMinWorldY();
        }
        return this;
    }

    @Override
    public Material fetchTypeId(final int x, final int y, final int z) {
        if (world == null) {
            return Material.AIR;
        }
        try {
            return world.getBlockAt(x, y, z).getType();
        } catch (Exception e) {
            // Return air if block access fails
            return Material.AIR;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public int fetchData(final int x, final int y, final int z) {
        if (world == null) {
            return 0;
        }
        
        // For 1.13+ versions, block data is handled differently
        if (Bridge1_13.hasIsSwimming()) {
            return 0;
        }
        
        try {
            return world.getBlockAt(x, y, z).getData();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public double[] fetchBounds(final int x, final int y, final int z) {
        if (world == null) {
            return null;
        }
        
        Material mat = getType(x, y, z);
        long flags = BlockFlags.getBlockFlags(mat);
        
        if (flags == BlockFlags.F_IGN_PASSABLE) {
            return null;
        }

        try {
            Block block = world.getBlockAt(x, y, z);
            
            // Try to get actual bounding box for newer versions
            if (hasGetBoundingBox()) {
                BoundingBox boundingBox = block.getBoundingBox();
                if (boundingBox != null) {
                    return new double[]{
                        boundingBox.getMinX() - x, boundingBox.getMinY() - y, boundingBox.getMinZ() - z,
                        boundingBox.getMaxX() - x, boundingBox.getMaxY() - y, boundingBox.getMaxZ() - z
                    };
                }
            }
        } catch (Exception e) {
            // Fall through to default bounds
        }

        // Default to full block bounds
        return new double[]{0.0, 0.0, 0.0, 1.0, 1.0, 1.0};
    }

    @Override
    public boolean standsOnEntity(final Entity entity, final double minX, final double minY, 
                                  final double minZ, final double maxX, final double maxY, final double maxZ) {
        if (entity == null || world == null) {
            return false;
        }

        try {
            // Get nearby entities within search radius
            for (final Entity other : entity.getNearbyEntities(ENTITY_SEARCH_RADIUS, ENTITY_SEARCH_RADIUS, ENTITY_SEARCH_RADIUS)) {
                if (other == null) {
                    continue;
                }
                
                final EntityType type = other.getType();
                
                // Only check for boats and shulkers (entities players can stand on)
                if (!MaterialUtil.isBoat(type) && type != EntityType.SHULKER) {
                    continue;
                }

                // Get entity location safely
                final Location entityLoc = entity.getLocation(useLoc);
                try {
                    final double entityY = entityLoc.getY();
                    
                    // Check if entity is close enough vertically to be standing on the other entity
                    if (Math.abs(entityY - minY) < STANDING_TOLERANCE) {
                        return true;
                    }
                } finally {
                    // Always clean up the location reference
                    useLoc.setWorld(null);
                }
            }
        } catch (Throwable t) {
            // Ignore exceptions (Context: DisguiseCraft and other plugin compatibility)
            // Log if debugging is enabled
        }
        
        return false;
    }

    /**
     * Check if the getBoundingBox method is available
     * @return true if getBoundingBox is available
     */
    private boolean hasGetBoundingBox() {
        try {
            Block.class.getMethod("getBoundingBox");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @Override
    public void cleanup() {
        super.cleanup();
        world = null;
        // Ensure the reusable location is clean
        useLoc.setWorld(null);
        useLoc.setX(0);
        useLoc.setY(0);
        useLoc.setZ(0);
    }
}
