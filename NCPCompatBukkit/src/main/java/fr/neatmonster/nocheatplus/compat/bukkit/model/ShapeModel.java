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

import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public interface ShapeModel<W> {

    // TODO: Rather fill in all into node directly (data as well), avoid redundant casting etc.
    // TODO: Best route passable workaround through here too (base on a flag), + getGroundMinHeight?.

    // TODO: Refine +- might have BukkitBlockCacheNode etc.
    public double[] getShape(BlockCache blockCache, W world, int x, int y, int z);

    /**
     * Allow faking data.
     * 
     * @return Integer.MAX_VALUE, in case fake data is not supported, and the
     *         Bukkit method is used (as long as possible). 0 may be returned
     *         for performance.
     */
    public int getFakeData(BlockCache blockCache, W world, int x, int y, int z);

    /**
     * Fill the given cache node with shape and data if not already present.
     *
     * <p>This helps to avoid redundant lookups and casting operations.</p>
     *
     * @param blockCache the cache to use for fallback operations
     * @param node the node to update
     * @param world the world instance
     * @param x block x
     * @param y block y
     * @param z block z
     */
    default void fillNode(BlockCache blockCache, BlockCache.BlockCacheNode node,
                          W world, int x, int y, int z) {
        if (node == null) {
            return;
        }
        if (!node.isBoundsFetched()) {
            node.setBounds(getShape(blockCache, world, x, y, z));
        }
        if (!node.isDataFetched()) {
            int data = getFakeData(blockCache, world, x, y, z);
            if (data != Integer.MAX_VALUE) {
                node.setData(data);
            }
        }
    }

}
