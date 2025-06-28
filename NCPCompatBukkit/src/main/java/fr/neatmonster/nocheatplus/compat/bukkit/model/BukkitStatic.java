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

import org.bukkit.World;

import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class BukkitStatic implements BukkitShapeModel {

    /**
     * Bounding box coordinates. Every six consecutive values describe one box
     * in the order {@code {minX, minY, minZ, maxX, maxY, maxZ}}. If multiple
     * boxes are used, they are stored sequentially in this array.
     */
    private final double[] bounds;

    private BukkitStatic(double... bounds) {
        this.bounds = bounds;
    }

    /**
     * Create a shape model using full xz-bounds for the given height.
     *
     * @param height
     * @return New instance.
     */
    public static BukkitStatic ofHeight(double height) {
        if (height <= 0.0) {
            throw new IllegalArgumentException("Height must be positive: " + height);
        }
        return ofInsetAndHeight(0.0, Math.min(height, 1.0));
    }

    /**
     * Create a shape model with the given xz-inset and height.
     *
     * @param xzInset
     * @param height
     * @return New instance.
     */
    public static BukkitStatic ofInsetAndHeight(double xzInset, double height) {
        if (height <= 0.0) {
            throw new IllegalArgumentException("Height must be positive: " + height);
        }
        height = Math.min(height, 1.0);
        return ofBounds(xzInset, 0.0, xzInset, 1.0 - xzInset, height, 1.0 - xzInset);
    }

    /**
     * Create a shape model from the given bounds.
     * <p>
     * The {@code bounds} array defines one or more axis aligned bounding boxes.
     * Each bounding box is described by six doubles in the order
     * {@code minX}, {@code minY}, {@code minZ}, {@code maxX}, {@code maxY} and
     * {@code maxZ}. Consequently the length of the array must be a multiple of
     * six.
     *
     * @param bounds The bounding box values.
     * @return New instance.
     */
    public static BukkitStatic ofBounds(double... bounds) {
        if (bounds.length % 6 != 0) {
            throw new IllegalArgumentException(
                    "The length must be a multiple of 6, but was: " + bounds.length);
        }
        return new BukkitStatic(bounds);
    }

    @Override
    public double[] getShape(final BlockCache blockCache,
            final World world, final int x, final int y, final int z) {
        return bounds.clone();
    }

    @Override
    public int getFakeData(final BlockCache blockCache, 
            final World world, final int x, final int y, final int z) {
        return 0;
    }

}
