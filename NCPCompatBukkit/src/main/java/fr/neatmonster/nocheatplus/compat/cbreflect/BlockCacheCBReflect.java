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
package fr.neatmonster.nocheatplus.compat.cbreflect;

import org.bukkit.World;
import org.bukkit.entity.Entity;

import fr.neatmonster.nocheatplus.compat.blocks.LegacyBlocks;
import fr.neatmonster.nocheatplus.compat.bukkit.BlockCacheBukkit;
import fr.neatmonster.nocheatplus.compat.cbreflect.reflect.ReflectHelper;
import fr.neatmonster.nocheatplus.compat.cbreflect.reflect.ReflectHelper.ReflectFailureException;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

/**
 * Block cache using CraftBukkit reflection to obtain additional block data.
 *
 * <p>The supplied {@link ReflectHelper} instance must not be modified after it
 * is passed to this class. Changing the helper can invalidate cached reflection
 * references and lead to unpredictable results.</p>
 */
public class BlockCacheCBReflect extends BlockCacheBukkit {

    // NOTE: Unsure if reflection can outperform the Bukkit API; experimentation may be needed.

    protected final ReflectHelper helper;

    protected Object nmsWorld = null;

    /**
     * Create a new instance using the given helper and world.
     *
     * @param reflectHelper
     *            helper for reflection access; must not be modified after
     *            passing it here
     * @param world
     *            initial world to access
     */
    public BlockCacheCBReflect(ReflectHelper reflectHelper, World world) {
        super(null);      // Avoid premature setAccess
        this.helper = reflectHelper;
        setAccess(world);
    }

    @Override
    public BlockCache setAccess(World world) {
        super.setAccess(world);
        this.nmsWorld = world == null ? null : helper.getHandle(world);
        return this;
    }

    @Override
    public double[] fetchBounds(int x, int y, int z) {
        final org.bukkit.Material mat = getType(x, y, z);
        final double[] shape = LegacyBlocks.getShape(this, mat, x, y, z, true);
        if (shape != null) return shape;
        try {
            return helper.nmsWorld_fetchBlockShape(this.nmsWorld, this.getType(x, y, z), x, y, z);
        }
        catch (ReflectFailureException ex) {
            return super.fetchBounds(x, y, z);
        }
    }

    @Override
    public boolean standsOnEntity(Entity entity, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        // NOTE: Implementation to be added when this becomes relevant.
        return super.standsOnEntity(entity, minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public void cleanup() {
        super.cleanup();
        this.nmsWorld = null;
    }

}
