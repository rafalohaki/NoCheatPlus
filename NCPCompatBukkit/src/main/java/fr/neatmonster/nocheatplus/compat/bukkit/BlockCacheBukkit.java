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
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.block.data.BlockData;

import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.utilities.location.LocationPool;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;

public class BlockCacheBukkit extends BlockCache {

    protected World world;

    protected final LocationPool locationPool = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstance(LocationPool.class);

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
        // Consider setting type id and data at once.
        return world.getBlockAt(x, y, z).getType();
    }

    @SuppressWarnings("deprecation")
    @Override
    public int fetchData(final int x, final int y, final int z) {
        // Consider setting type id and data at once.
        return Bridge1_13.hasIsSwimming() ? 0 : world.getBlockAt(x, y, z).getData();
    }

    /**
     * Get the {@link BlockData} for the specified block coordinates.
     *
     * <p>This method must only be called from the primary server thread as
     * Bukkit's block access is not thread-safe.</p>
     */
    public BlockData getBlockData(final int x, final int y, final int z) {
        return world.getBlockAt(x, y, z).getState().getBlockData();
    }

    @Override
    public double[] fetchBounds(final int x, final int y, final int z){
        Material mat = getType(x, y, z);
        long flags = BlockFlags.getBlockFlags(mat);
        if (flags == BlockFlags.F_IGN_PASSABLE) {
            return null;
        }
        // minX, minY, minZ, maxX, maxY, maxZ

        // Note: maintain a list with manual entries or at least half/full blocks?
        // Always return full bounds, needs extra adaption to BlockProperties (!).
        return new double[]{0D, 0D, 0D, 1D, 1D, 1D};
    }

    @Override
    public boolean standsOnEntity(final Entity entity, final double minX, final double minY, final double minZ, final double maxX, final double maxY, final double maxZ){
        final Location temp = locationPool.acquire();
        try {
            for (final Entity other : entity.getNearbyEntities(2.0, 2.0, 2.0)) {
                final EntityType type = other.getType();
                if (!MaterialUtil.isBoat(type) && type != EntityType.SHULKER) {
                    continue;
                }
                final double locY = entity.getLocation(temp).getY();
                return Math.abs(locY - minY) < 0.7;
            }
        } catch (Throwable t) {
            // Ignore exceptions (Context: DisguiseCraft).
        } finally {
            locationPool.release(temp);
        }
        return false;
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.utilities.BlockCache#cleanup()
     */
    @Override
    public void cleanup() {
        super.cleanup();
        world = null;
    }

}
