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
package fr.neatmonster.nocheatplus.utilities.collision;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import fr.neatmonster.nocheatplus.checks.moving.util.MovingUtil;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker.Direction;
import fr.neatmonster.nocheatplus.utilities.ds.map.BlockCoord;
import fr.neatmonster.nocheatplus.utilities.location.TrigUtil;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;

/**
 * Collision related static utility.
 * 
 * @author asofold
 *
 */
public class CollisionUtil {


    /**
     * Check if a player looks at a target of a specific size, with a specific
     * precision value (roughly).
     *
     * @param player
     *            the player
     * @param targetX
     *            the target x
     * @param targetY
     *            the target y
     * @param targetZ
     *            the target z
     * @param targetWidth
     *            the target width
     * @param targetHeight
     *            the target height
     * @param precision
     *            the precision
     * @return the double
     *
     * Note: Callers must obtain player locations on the main thread.
     */
    public static double directionCheck(final Player player, final double targetX, final double targetY, final double targetZ, final double targetWidth, final double targetHeight, final double precision)
    {
        if (player == null)
        {
            return 0D;
        }
        final Location loc = player.getLocation();
        final Vector dir = loc.getDirection();
        return directionCheck(loc.getX(), loc.getY() + MovingUtil.getEyeHeight(player), loc.getZ(), dir.getX(), dir.getY(), dir.getZ(), targetX, targetY, targetZ, targetWidth, targetHeight, precision);
    }

    /**
     * Convenience method.
     *
     * @param sourceFoot
     *            the source foot
     * @param eyeHeight
     *            the eye height
     * @param dir
     *            the dir
     * @param target
     *            the target
     * @param precision
     *            (width/height are set to 1)
     * @return the double
     */
    public static double directionCheck(final Location sourceFoot, final double eyeHeight, final Vector dir, final Block target, final double precision)
    {
        return directionCheck(sourceFoot.getX(), sourceFoot.getY() + eyeHeight, sourceFoot.getZ(), dir.getX(), dir.getY(), dir.getZ(), target.getX(), target.getY(), target.getZ(), 1, 1, precision);
    }

    /**
     * Convenience method.
     *
     * @param sourceFoot
     *            the source foot
     * @param eyeHeight
     *            the eye height
     * @param dir
     *            the dir
     * @param targetX
     *            the target x
     * @param targetY
     *            the target y
     * @param targetZ
     *            the target z
     * @param targetWidth
     *            the target width
     * @param targetHeight
     *            the target height
     * @param precision
     *            the precision
     * @return the double
     */
    public static double directionCheck(final Location sourceFoot, final double eyeHeight, final Vector dir, final double targetX, final double targetY, final double targetZ, final double targetWidth, final double targetHeight, final double precision)
    {
        return directionCheck(sourceFoot.getX(), sourceFoot.getY() + eyeHeight, sourceFoot.getZ(), dir.getX(), dir.getY(), dir.getZ(), targetX, targetY, targetZ, targetWidth, targetHeight, precision);
    }

    /**
     * Check how far the looking direction is off the target.
     *
     * @param sourceX
     *            Source location of looking direction.
     * @param sourceY
     *            the source y
     * @param sourceZ
     *            the source z
     * @param dirX
     *            Looking direction.
     * @param dirY
     *            the dir y
     * @param dirZ
     *            the dir z
     * @param targetX
     *            Location that should be looked towards.
     * @param targetY
     *            the target y
     * @param targetZ
     *            the target z
     * @param targetWidth
     *            xz extent
     * @param targetHeight
     *            y extent
     * @param precision
     *            the precision
     * @return Some offset.
     */
    public static double directionCheck(final double sourceX, final double sourceY, final double sourceZ, final double dirX, final double dirY, final double dirZ, final double targetX, final double targetY, final double targetZ, final double targetWidth, final double targetHeight, final double precision)
    {

        //        // Legacy debug output kept for reference
        //        NCPAPIProvider.getNoCheatPlusAPI().getLogManager().debug(Streams.TRACE_FILE, "COMBINED: " + combinedDirectionCheck(sourceX, sourceY, sourceZ, dirX, dirY, dirZ, targetX, targetY, targetZ, targetWidth, targetHeight, precision, 60));

        // NOTE: Method should be reviewed and standardized.

        double dirLength = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (dirLength == 0.0) dirLength = 1.0; // ...

        final double dX = targetX - sourceX;
        final double dY = targetY - sourceY;
        final double dZ = targetZ - sourceZ;

        final double targetDist = Math.sqrt(dX * dX + dY * dY + dZ * dZ);

        final double xPrediction = targetDist * dirX / dirLength;
        final double yPrediction = targetDist * dirY / dirLength;
        final double zPrediction = targetDist * dirZ / dirLength;

        double off = 0.0D;

        off += Math.max(Math.abs(dX - xPrediction) - (targetWidth / 2 + precision), 0.0D);
        off += Math.max(Math.abs(dZ - zPrediction) - (targetWidth / 2 + precision), 0.0D);
        off += Math.max(Math.abs(dY - yPrediction) - (targetHeight / 2 + precision), 0.0D);

        if (off > 1) off = Math.sqrt(off);

        return off;
    }

    /**
     * Combined direction check.
     *
     * @param sourceFoot
     *            the source foot
     * @param eyeHeight
     *            the eye height
     * @param dir
     *            the dir
     * @param targetX
     *            the target x
     * @param targetY
     *            the target y
     * @param targetZ
     *            the target z
     * @param targetWidth
     *            the target width
     * @param targetHeight
     *            the target height
     * @param precision
     *            the precision
     * @param anglePrecision
     *            the angle precision
     * @return the double
     */
    public static double combinedDirectionCheck(final Location sourceFoot, final double eyeHeight, final Vector dir, final double targetX, final double targetY, final double targetZ, final double targetWidth, final double targetHeight, final double precision, final double anglePrecision, boolean isPlayer)
    {
        return combinedDirectionCheck(sourceFoot.getX(), sourceFoot.getY() + eyeHeight, sourceFoot.getZ(), dir.getX(), dir.getY(), dir.getZ(), targetX, targetY, targetZ, targetWidth, targetHeight, precision, anglePrecision, isPlayer);
    }

    /**
     * Combined direction check.
     *
     * @param sourceFoot
     *            the source foot
     * @param eyeHeight
     *            the eye height
     * @param dir
     *            the dir
     * @param target
     *            the target
     * @param precision
     *            the precision
     * @param anglePrecision
     *            the angle precision
     * @return the double
     */
    public static double combinedDirectionCheck(final Location sourceFoot, final double eyeHeight, final Vector dir, final Block target, final double precision, final double anglePrecision)
    {
        return combinedDirectionCheck(sourceFoot.getX(), sourceFoot.getY() + eyeHeight, sourceFoot.getZ(), dir.getX(), dir.getY(), dir.getZ(), target.getX(), target.getY(), target.getZ(), 1, 1, precision, anglePrecision, true);
    }

    /**
     * Combine directionCheck with angle, in order to prevent low-distance
     * abuse.
     *
     * @param sourceX
     *            the source x
     * @param sourceY
     *            the source y
     * @param sourceZ
     *            the source z
     * @param dirX
     *            the dir x
     * @param dirY
     *            the dir y
     * @param dirZ
     *            the dir z
     * @param targetX
     *            the target x
     * @param targetY
     *            the target y
     * @param targetZ
     *            the target z
     * @param targetWidth
     *            the target width
     * @param targetHeight
     *            the target height
     * @param blockPrecision
     *            the block precision
     * @param anglePrecision
     *            Precision in grad.
     * @return the double
     */
    public static double combinedDirectionCheck(final double sourceX, final double sourceY, final double sourceZ, final double dirX, final double dirY, final double dirZ, final double targetX, final double targetY, final double targetZ, final double targetWidth, final double targetHeight, final double blockPrecision, final double anglePrecision, final boolean isPlayer)
    {
        double dirLength = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (dirLength == 0.0) dirLength = 1.0; // ...

        final double dX = targetX - sourceX;
        final double dY = targetY - sourceY;
        final double dZ = targetZ - sourceZ;

        final double targetDist = Math.sqrt(dX * dX + dY * dY + dZ * dZ);
        final double minDist = isPlayer ? Math.max(targetHeight, targetWidth) / 2.0 : Math.max(targetHeight, targetWidth);

        if (targetDist > minDist && TrigUtil.angle(sourceX, sourceY, sourceZ, dirX, dirY, dirZ, targetX, targetY, targetZ) * TrigUtil.fRadToGrad > anglePrecision){
            return targetDist - minDist;
        }

        final double xPrediction = targetDist * dirX / dirLength;
        final double yPrediction = targetDist * dirY / dirLength;
        final double zPrediction = targetDist * dirZ / dirLength;

        double off = 0.0D;

        off += Math.max(Math.abs(dX - xPrediction) - (targetWidth / 2 + blockPrecision), 0.0D);
        off += Math.max(Math.abs(dY - yPrediction) - (targetHeight / 2 + blockPrecision), 0.0D);
        off += Math.max(Math.abs(dZ - zPrediction) - (targetWidth / 2 + blockPrecision), 0.0D);

        if (off > 1) off = Math.sqrt(off);

        return off;
    }

    /**
     * Test if the block coordinate is intersecting with min+max bounds,
     * assuming the a full block. Excludes the case of only the edges
     * intersecting.
     *
     * @param min
     *            the min
     * @param max
     *            the max
     * @param block
     *            Block coordinate of the block.
     * @return true, if successful
     */
    public static boolean intersectsBlock(final double min, final double max, final int block) {
        final double db = (double) block;
        return db + 1.0 > min && db < max;
    }

    /**
     * Test if a point is inside an AABB, including the edges.
     * 
     * @param x
     *            Position of the point.
     * @param y
     * @param z
     * @param minX
     *            Minimum coordinates of the AABB.
     * @param minY
     * @param minZ
     * @param maxX
     *            Maximum coordinates of the AABB.
     * @param maxY
     * @param maxZ
     * @return
     */
    public static boolean isInsideAABBIncludeEdges(final double x, final double y, final double z,
            final double minX, final double minY, final double minZ,
            final double maxX, final double maxY, final double maxZ) {
        return !(x < minX || x > maxX || z < minZ || z > maxZ || y < minY || y > maxY);
    }

    /**
     * Get the earliest time a collision with the min-max coordinates can occur,
     * in multiples of dir, including edges.
     * 
     * @param pos
     * @param dir
     * @param minPos
     * @param maxPos
     * @return The multiple of dir to hit the min-max coordinates, or
     *         Double.POSITIVE_INFINITY if not possible to hit.
     */
    public static double getMinTimeIncludeEdges(final double pos, final double dir, 
            final double minPos, final double maxPos) {
        if (pos >= minPos && pos <= maxPos) {
            return 0.0;
        }
        else if (dir == 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        else if (dir < 0.0) {
            return pos < minPos ? Double.POSITIVE_INFINITY : (Math.abs(pos - maxPos) / Math.abs(dir));
        }
        else {
            // dir > 0.0
            return pos > maxPos ? Double.POSITIVE_INFINITY : (Math.abs(pos - minPos) / dir);
        }
    }

    /**
     * Get the maximum time for which the min-max coordinates still are hit.
     * 
     * @param pos
     * @param dir
     * @param minPos
     * @param maxPos
     * @param minTime
     *            The earliest time of collision with the min-max coordinates,
     *            as returned by getMinTimeIncludeEdges.
     * @return The maximum time for which the min-max coordinates still are hit.
     *         If no hit is possible, Double.NaN is returned. If minTime is
     *         Double.POSITIVE_INFINITY, Double.NaN is returned directly.
     *         Double.POSITIVE_INFINITY may be returned, if coordinates are
     *         colliding always.
     */
    public static double getMaxTimeIncludeEdges(final double pos, final double dir, 
            final double minPos, final double maxPos, final double minTime) {
        if (Double.isInfinite(minTime)) {
            return Double.NaN;
        }
        else if (dir == 0.0) {
            return (pos < minPos || pos > maxPos) ? Double.NaN : Double.POSITIVE_INFINITY;
        }
        else if (dir < 0.0) {
            return pos < minPos ? Double.NaN : (Math.abs(pos - minPos) / Math.abs(dir));
        }
        else {
            // dir > 0.0
            return pos > maxPos ? Double.NaN : (Math.abs(pos - maxPos) / dir);
        }
    }

    /**
     * Get the maximum (closest) distance from the given position towards the
     * AABB regarding axes independently.
     * 
     * @param x
     *            Position of the point.
     * @param y
     * @param z
     * @param minX
     *            Minimum coordinates of the AABB.
     * @param minY
     * @param minZ
     * @param maxX
     *            Maximum coordinates of the AABB.
     * @param maxY
     * @param maxZ
     * @return
     */
    public static double getMaxAxisDistAABB(final double x, final double y, final double z,
            final double minX, final double minY, final double minZ,
            final double maxX, final double maxY, final double maxZ) {
        return Math.max(axisDistance(x,  minX, maxX), Math.max(axisDistance(y, minY, maxY), axisDistance(z, minZ, maxZ)));
    }

    /**
     * Get the maximum (closest) 'Manhattan' distance from the given position
     * towards the AABB regarding axes independently.
     * 
     * @param x
     *            Position of the point.
     * @param y
     * @param z
     * @param minX
     *            Minimum coordinates of the AABB.
     * @param minY
     * @param minZ
     * @param maxX
     *            Maximum coordinates of the AABB.
     * @param maxY
     * @param maxZ
     * @return
     */
    public static double getManhattanDistAABB(final double x, final double y, final double z,
            final double minX, final double minY, final double minZ,
            final double maxX, final double maxY, final double maxZ) {
        return axisDistance(x,  minX, maxX)+ axisDistance(y, minY, maxY) + axisDistance(z, minZ, maxZ);
    }

    /**
     * Get the squared (closest) distance from the given position towards the
     * AABB regarding axes independently.
     * 
     * @param x
     *            Position of the point.
     * @param y
     * @param z
     * @param minX
     *            Minimum coordinates of the AABB.
     * @param minY
     * @param minZ
     * @param maxX
     *            Maximum coordinates of the AABB.
     * @param maxY
     * @param maxZ
     * @return
     */
    public static double getSquaredDistAABB(final double x, final double y, final double z,
            final double minX, final double minY, final double minZ,
            final double maxX, final double maxY, final double maxZ) {
        final double dX = axisDistance(x,  minX, maxX);
        final double dY = axisDistance(y, minY, maxY);
        final double dZ = axisDistance(z, minZ, maxZ);
        return dX * dX + dY * dY + dZ * dZ;
    }

    /**
     * Get the distance towards a min-max interval (inside and edge count as 0.0
     * distance).
     * 
     * @param pos
     * @param minPos
     * @param maxPos
     * @return Positive distance always.
     */
    public static double axisDistance(final double pos, final double minPos, final double maxPos) {
        return pos < minPos ? Math.abs(pos - minPos) : (pos > maxPos ? Math.abs(pos - maxPos) : 0.0);
    }

    public static boolean isCollidingWithEntities(final Player p, final boolean onlyLivingEntities) {
        double xzRange = 0.15;
        double yRange = onlyLivingEntities ? 0.2 : 0.15;

        // Directly iterate over entities and check conditions to avoid unnecessary collection creation.
        for (Entity entity : p.getWorld().getNearbyEntities(p.getLocation(), xzRange, yRange, xzRange)) {
            if (!onlyLivingEntities || entity instanceof LivingEntity) {
                return true; // Collision detected, return early
            }
        }

        return false; // No collision detected
    }

    /**
     * Simple check to see if neighbor block is nearly same direction with block trying to interact.<br>
     * For example if block interacting below or equal eye block, neighbor must be below or equal eye block.<br>
     * 
     * @param neighbor coord to check
     * @param block coord that trying to interact
     * @param eyeBlock
     * @return true if correct.
     */
    public static boolean correctDir(int neighbor, int block, int eyeBlock) {
        int d = eyeBlock - block;
        if (d > 0) {
            if (neighbor > eyeBlock) return false;
        } else if (d < 0) {
            if (neighbor < eyeBlock) return false;
        } else {
            if (neighbor < eyeBlock || neighbor > eyeBlock) return false;
        }
        return true;
    }

    /**
     * Simple check to see if neighnor block is nearly same direction with block trying to interact.<br>
     * If the check don't satisfied but the coord to check is still within min and max, check still return true.<br>
     * Design for blocks currently colliding with a bounding box<br>
     * 
     * @param neighbor coord to check
     * @param block coord that trying to interact
     * @param eyeBlock
     * @param min Min value of one axis of bounding box
     * @param max Max value of one axis of bounding box
     * @return true if correct.
     */
    public static boolean correctDir(int neighbor, int block, int eyeBlock, int min, int max) {
        if (neighbor >= min && neighbor <= max) return true;
        int d = eyeBlock - block;
        if (d > 0) {
            if (neighbor > eyeBlock) return false;
        } else if (d < 0) {
            if (neighbor < eyeBlock) return false;
        } else {
            if (neighbor < eyeBlock || neighbor > eyeBlock) return false;
        }
        return true;
    }

    /**
     * Test if from last block, the next block can pass through
     * 
     * @param rayTracing
     * @param blockCache
     * @param lastBlock The last block
     * @param x The next block
     * @param y
     * @param z
     * @param direction Approximate normalized direction to block
     * @param eyeX Eye location
     * @param eyeY
     * @param eyeZ
     * @param eyeHeight
     * @param sCollidingBox Start of bounding box(min). Can be null
     * @param eCollidingBox End of bounding box(max). Can be null
     * @param mightEdgeInteraction 
     * @param axisData Auxiliary stuff for specific usage can be null
     * @return true if can.
     */
    public static boolean canPassThrough(InteractAxisTracing rayTracing, BlockCache blockCache, BlockCoord lastBlock,
            int x, int y, int z, Vector direction, double eyeX, double eyeY, double eyeZ, double eyeHeight,
            BlockCoord sCollidingBox, BlockCoord eCollidingBox, boolean mightEdgeInteraction, RichAxisData axisData) {
        double[] nextBounds = blockCache.getBounds(x, y, z);
        final Material mat = blockCache.getType(x, y, z);
        final long flags = BlockFlags.getBlockFlags(mat);
        if (nextBounds == null || canPassThroughWorkAround(blockCache, x, y, z, direction, eyeX, eyeY, eyeZ, eyeHeight)) {
            return true;
        }

        int dy = y - lastBlock.getY();
        int dx = x - lastBlock.getX();
        int dz = z - lastBlock.getZ();

        mightEdgeInteraction |= (BlockFlags.getBlockFlags(blockCache.getType(lastBlock.getX(), lastBlock.getY(), lastBlock.getZ())) & BlockFlags.F_LIQUID) != 0;
        double[] lastBounds = blockCache.getBounds(lastBlock.getX(), lastBlock.getY(), lastBlock.getZ());

        applyAxisDataExclude(lastBounds, nextBounds, dx, dy, dz, axisData);

        if (isInitiallyInside(sCollidingBox, eCollidingBox, x, y, z)) {
            return true;
        }

        if (rayTraceClear(rayTracing, lastBlock, x, y, z, dx, dy, dz)) {
            return true;
        }

        if ((flags & BlockFlags.F_STAIRS) != 0 && isBlockedByStairs(nextBounds, dx, dy, dz, x, y, eyeY)) {
            return false;
        }

        if (dy != 0) {
            return evaluateVerticalMove(rayTracing, nextBounds, lastBounds, dy, mightEdgeInteraction, axisData);
        }
        if (dx != 0) {
            return evaluateHorizontalMoveX(rayTracing, nextBounds, lastBounds, dx, mightEdgeInteraction, axisData);
        }
        if (dz != 0) {
            return evaluateHorizontalMoveZ(rayTracing, nextBounds, lastBounds, dz, mightEdgeInteraction, axisData);
        }
        return false;
    }

    private static void applyAxisDataExclude(double[] lastBounds, double[] nextBounds, int dx, int dy, int dz,
            RichAxisData axisData) {
        if (axisData == null || lastBounds == null || nextBounds == null) {
            return;
        }
        if (dy != 0) {
            excludeForYMove(lastBounds, nextBounds, axisData);
        }
        if (dx != 0) {
            excludeForXMove(lastBounds, nextBounds, axisData);
        }
        if (dz != 0) {
            excludeForZMove(lastBounds, nextBounds, axisData);
        }
    }

    private static void excludeForYMove(double[] lastBounds, double[] nextBounds, RichAxisData axisData) {
        if (fullYZ(nextBounds) && fullYZ(lastBounds)
                && rangeContains(nextBounds[0], lastBounds[0], nextBounds[3], lastBounds[3])) {
            axisData.exclude = chooseDirection(nextBounds[0], nextBounds[3], Direction.X_NEG, Direction.X_POS);
        }
        if (fullXY(nextBounds) && fullXY(lastBounds)
                && rangeContains(nextBounds[2], lastBounds[2], nextBounds[5], lastBounds[5])) {
            axisData.exclude = chooseDirection(nextBounds[2], nextBounds[5], Direction.Z_NEG, Direction.Z_POS);
        }
    }

    private static void excludeForXMove(double[] lastBounds, double[] nextBounds, RichAxisData axisData) {
        if (fullXZ(nextBounds) && fullXZ(lastBounds)
                && rangeContains(nextBounds[1], lastBounds[1], nextBounds[4], lastBounds[4])) {
            axisData.exclude = chooseDirection(nextBounds[1], nextBounds[4], Direction.Y_NEG, Direction.Y_POS);
        }
        if (fullXY(nextBounds) && fullXY(lastBounds)
                && rangeContains(nextBounds[2], lastBounds[2], nextBounds[5], lastBounds[5])) {
            axisData.exclude = chooseDirection(nextBounds[2], nextBounds[5], Direction.Z_NEG, Direction.Z_POS);
        }
    }

    private static void excludeForZMove(double[] lastBounds, double[] nextBounds, RichAxisData axisData) {
        if (fullXZ(nextBounds) && fullXZ(lastBounds)
                && rangeContains(nextBounds[1], lastBounds[1], nextBounds[4], lastBounds[4])) {
            axisData.exclude = chooseDirection(nextBounds[1], nextBounds[4], Direction.Y_NEG, Direction.Y_POS);
        }
        if (fullYZ(nextBounds) && fullYZ(lastBounds)
                && rangeContains(nextBounds[0], lastBounds[0], nextBounds[3], lastBounds[3])) {
            axisData.exclude = chooseDirection(nextBounds[0], nextBounds[3], Direction.X_NEG, Direction.X_POS);
        }
    }

    private static Direction chooseDirection(double min, double max, Direction negative, Direction positive) {
        if (min == 0.0) {
            return negative;
        }
        if (max == 1.0) {
            return positive;
        }
        return Direction.NONE;
    }

    private static boolean isFullRange(double[] bounds, int minIndex, int maxIndex) {
        return bounds[minIndex] == 0.0 && bounds[maxIndex] == 1.0;
    }

    private static boolean fullXY(double[] bounds) {
        return isFullRange(bounds, 0, 3) && isFullRange(bounds, 1, 4);
    }

    private static boolean fullYZ(double[] bounds) {
        return isFullRange(bounds, 1, 4) && isFullRange(bounds, 2, 5);
    }

    private static boolean fullXZ(double[] bounds) {
        return isFullRange(bounds, 0, 3) && isFullRange(bounds, 2, 5);
    }

    private static boolean isInitiallyInside(BlockCoord sCollidingBox, BlockCoord eCollidingBox, int x, int y, int z) {
        return sCollidingBox != null && eCollidingBox != null && isInsideAABBIncludeEdges(x, y, z,
                sCollidingBox.getX(), sCollidingBox.getY(), sCollidingBox.getZ(), eCollidingBox.getX(),
                eCollidingBox.getY(), eCollidingBox.getZ());
    }

    private static boolean rayTraceClear(InteractAxisTracing rayTracing, BlockCoord lastBlock, int x, int y, int z,
            int dx, int dy, int dz) {
        double stepX = dx * 0.99;
        double stepY = dy * 0.99;
        double stepZ = dz * 0.99;
        rayTracing.set(lastBlock.getX(), lastBlock.getY(), lastBlock.getZ(), x + stepX, y + stepY, z + stepZ);
        rayTracing.setIgnoreInitiallyColliding(true);
        rayTracing.loop();
        rayTracing.setIgnoreInitiallyColliding(false);
        return !rayTracing.collides();
    }

    private static boolean isBlockedByStairs(double[] nextBounds, int dx, int dy, int dz, int x, int y, double eyeY) {
        if (dy == 0) {
            int eyeBlockY = Location.locToBlock(eyeY);
            if (eyeBlockY > y && nextBounds[4] == 1.0) {
                return true;
            }
            if (eyeBlockY < y && nextBounds[1] == 0.0) {
                return true;
            }
        }
        if (dx != 0) {
            for (int i = 2; i <= (int) nextBounds.length / 6; i++) {
                if (nextBounds[i * 6 - 4] == 0.0 && nextBounds[i * 6 - 1] == 1.0
                        && (dx < 0 ? nextBounds[i * 6 - 3] == 1.0 : nextBounds[i * 6 - 6] == 0.0)) {
                    return true;
                }
            }
        }
        if (dz != 0) {
            for (int i = 2; i <= (int) nextBounds.length / 6; i++) {
                if (nextBounds[i * 6 - 6] == 0.0 && nextBounds[i * 6 - 3] == 1.0
                        && (dz < 0 ? nextBounds[i * 6 - 1] == 1.0 : nextBounds[i * 6 - 4] == 0.0)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean evaluateVerticalMove(InteractAxisTracing rayTracing, double[] nextBounds, double[] lastBounds,
            int dy, boolean mightEdgeInteraction, RichAxisData axisData) {
        if (nextBounds[0] == 0.0 && nextBounds[3] == 1.0 && nextBounds[2] == 0.0 && nextBounds[5] == 1.0) {
            if (axisData != null && (dy > 0 ? nextBounds[1] != 0.0 : nextBounds[4] != 1.0)) {
                axisData.exclude = dy > 0 ? Direction.Y_POS : Direction.Y_NEG;
                return true;
            }
            return rayTracing.getCollidingAxis() != Axis.Y_AXIS;
        }
        if (!mightEdgeInteraction && lastBounds != null
                && (dy > 0 ? lastBounds[4] == 1.0 && nextBounds[1] == 0.0 : lastBounds[1] == 0.0 && nextBounds[4] == 1.0)
                && (nextBounds[0] == 0.0 && lastBounds[0] == 0.0 && nextBounds[3] == 1.0 && lastBounds[3] == 1.0
                        && equal(getFilledSpace(lastBounds[2], lastBounds[5], nextBounds[2], nextBounds[5]), 1.0, 0.001)
                        || nextBounds[2] == 0.0 && lastBounds[2] == 0.0 && nextBounds[5] == 1.0 && lastBounds[5] == 1.0
                                && equal(getFilledSpace(lastBounds[0], lastBounds[3], nextBounds[0], nextBounds[3]),
                                        1.0, 0.001))) {
            return false;
        }
        return true;
    }

    private static boolean evaluateHorizontalMoveX(InteractAxisTracing rayTracing, double[] nextBounds,
            double[] lastBounds, int dx, boolean mightEdgeInteraction, RichAxisData axisData) {
        if (nextBounds[1] == 0.0 && nextBounds[4] == 1.0 && nextBounds[2] == 0.0 && nextBounds[5] == 1.0) {
            if (axisData != null && (dx > 0 ? nextBounds[0] != 0.0 : nextBounds[3] != 1.0)) {
                axisData.exclude = dx > 0 ? Direction.X_POS : Direction.X_NEG;
                return true;
            }
            return rayTracing.getCollidingAxis() != Axis.X_AXIS;
        }
        if (!mightEdgeInteraction && lastBounds != null
                && (dx > 0 ? lastBounds[3] == 1.0 && nextBounds[0] == 0.0 : lastBounds[0] == 0.0 && nextBounds[3] == 1.0)
                && (nextBounds[1] == 0.0 && lastBounds[1] == 0.0 && nextBounds[4] == 1.0 && lastBounds[4] == 1.0
                        && equal(getFilledSpace(lastBounds[2], lastBounds[5], nextBounds[2], nextBounds[5]), 1.0, 0.001)
                        || nextBounds[2] == 0.0 && lastBounds[2] == 0.0 && nextBounds[5] == 1.0 && lastBounds[5] == 1.0
                                && equal(getFilledSpace(lastBounds[1], lastBounds[4], nextBounds[1], nextBounds[4]),
                                        1.0, 0.001))) {
            return false;
        }
        return true;
    }

    private static boolean evaluateHorizontalMoveZ(InteractAxisTracing rayTracing, double[] nextBounds,
            double[] lastBounds, int dz, boolean mightEdgeInteraction, RichAxisData axisData) {
        if (nextBounds[0] == 0.0 && nextBounds[3] == 1.0 && nextBounds[1] == 0.0 && nextBounds[4] == 1.0) {
            if (axisData != null && (dz > 0 ? nextBounds[2] != 0.0 : nextBounds[5] != 1.0)) {
                axisData.exclude = dz > 0 ? Direction.Z_POS : Direction.Z_NEG;
                return true;
            }
            return rayTracing.getCollidingAxis() != Axis.Z_AXIS;
        }
        if (!mightEdgeInteraction && lastBounds != null
                && (dz > 0 ? lastBounds[5] == 1.0 && nextBounds[2] == 0.0 : lastBounds[2] == 0.0 && nextBounds[5] == 1.0)
                && (nextBounds[1] == 0.0 && lastBounds[1] == 0.0 && nextBounds[4] == 1.0 && lastBounds[4] == 1.0
                        && equal(getFilledSpace(lastBounds[0], lastBounds[3], nextBounds[0], nextBounds[3]), 1.0, 0.001)
                        || nextBounds[0] == 0.0 && lastBounds[0] == 0.0 && nextBounds[3] == 1.0 && lastBounds[3] == 1.0
                                && equal(getFilledSpace(lastBounds[1], lastBounds[4], nextBounds[1], nextBounds[4]),
                                        1.0, 0.001))) {
            return false;
        }
        return true;
    }

    private static boolean canPassThroughWorkAround(BlockCache blockCache, int blockX, int blockY, int blockZ, Vector direction, double eyeX, double eyeY, double eyeZ, double eyeHeight) {
        final Material mat = blockCache.getType(blockX, blockY, blockZ);
        final long flags = BlockFlags.getBlockFlags(mat);
        // Consider checking for non-solid blocks with (flags & BlockFlags.F_SOLID) == 0
        //if ((flags & BlockFlags.F_SOLID) == 0) {
            // Ignore non solid blocks anyway.
        //    return true;
        //}
        // Passable for movement does not necessarily mean passable for interaction (see F_INTERACT_PASSABLE?)
        // To achive this, first, need to change collision system to flag passable block(complicated), 
        // second add flag F_INTERACT_PASSABLE to ignore block can truly passable, 
        // third add bounds to BlockCacheBukkit.java
        if ((flags & (BlockFlags.F_LIQUID | BlockFlags.F_IGN_PASSABLE)) != 0) {
            return true;
        }

        if ((flags & (BlockFlags.F_THICK_FENCE | BlockFlags.F_THIN_FENCE)) != 0) {
            // Restore the Y location of player trying to interact
            int entityBlockY = Location.locToBlock(eyeY - eyeHeight);
            // if player is close to the block and look up or look down
            return direction.getY() > 0.76 && entityBlockY > blockY || direction.getY() < -0.76 && entityBlockY < blockY;
        }
        return false;
    }

    /**
     * Function to return the list of blocks that can be interact from.<br>
     * As we can only see maximum 3 sides of a cube at a time
     * 
     * @param currentBlock Current block to move on
     * @param direction
     * @param eyeX Eye location just to automatically prioritize with Axis will attempt to try first
     * @param eyeY
     * @param eyeZ
     * @param axisData Rich data for specific usage. Can be null. If not null will consume data 
     * @return List of blocks that can possibly interact from
     */ 
    public static List<BlockCoord> getNeighborsInDirection(BlockCoord currentBlock, Vector direction, double eyeX, double eyeY, double eyeZ, RichAxisData axisData) {
        List<BlockCoord> neighbors = new ArrayList<>();
        int stepY = getStep(direction.getY());
        int stepX = getStep(direction.getX());
        int stepZ = getStep(direction.getZ());
        AxisContext ctx = prepareAxisContext(axisData, stepX, stepY, stepZ);
        if (addPriorityNeighbors(neighbors, currentBlock, stepX, stepY, stepZ, ctx)) {
            return neighbors;
        }

        final double dYM = TrigUtil.manhattan(currentBlock.getX(), currentBlock.getY() + stepY, currentBlock.getZ(), eyeX, eyeY, eyeZ);
        final double dZM = TrigUtil.manhattan(currentBlock.getX(), currentBlock.getY(), currentBlock.getZ() + stepZ, eyeX, eyeY, eyeZ);
        final double dXM = TrigUtil.manhattan(currentBlock.getX() + stepX, currentBlock.getY(), currentBlock.getZ(), eyeX, eyeY, eyeZ);

        boolean prioritizeY = shouldPrioritizeY(direction.getY(), dXM, dYM, dZM);
        boolean xFirst = dXM < dZM;

        addNeighborsInOrder(neighbors, currentBlock, stepX, stepY, stepZ, ctx, prioritizeY, xFirst);
        return neighbors;
    }

    private static boolean shouldPrioritizeY(double dirY, double dXM, double dYM, double dZM) {
        return dYM <= dXM && dYM <= dZM && Math.abs(dirY) >= 0.5;
    }

    private static void addNeighborsInOrder(List<BlockCoord> neighbors, BlockCoord block, int stepX, int stepY, int stepZ, AxisContext ctx, boolean yFirst, boolean xFirst) {
        if (yFirst && ctx.allowY) {
            neighbors.add(new BlockCoord(block.getX(), block.getY() + stepY, block.getZ()));
        }
        if (xFirst) {
            if (ctx.allowX) neighbors.add(new BlockCoord(block.getX() + stepX, block.getY(), block.getZ()));
            if (ctx.allowZ) neighbors.add(new BlockCoord(block.getX(), block.getY(), block.getZ() + stepZ));
        } else {
            if (ctx.allowZ) neighbors.add(new BlockCoord(block.getX(), block.getY(), block.getZ() + stepZ));
            if (ctx.allowX) neighbors.add(new BlockCoord(block.getX() + stepX, block.getY(), block.getZ()));
        }
        if (!yFirst && ctx.allowY) {
            neighbors.add(new BlockCoord(block.getX(), block.getY() + stepY, block.getZ()));
        }
    }

    private static double getFilledSpace(double sA, double eA, double sB, double eB) {
        return (eA-sA) + (eB-sB) - Math.max(0, Math.min(eA, eB) - Math.max(sA, sB));
    }

    private static int getStep(double value) {
        return Integer.compare((int) Math.signum(value), 0);
    }

    private static AxisContext prepareAxisContext(RichAxisData axisData, int stepX, int stepY, int stepZ) {
        AxisContext ctx = new AxisContext();
        if (axisData != null) {
            ctx.priorityAxis = axisData.priority;
            Direction exclude = axisData.exclude;
            axisData.priority = Axis.NONE;
            axisData.exclude = Direction.NONE;
            ctx.allowX = !(exclude == Direction.X_NEG && stepX < 0 || exclude == Direction.X_POS && stepX > 0);
            ctx.allowY = !(exclude == Direction.Y_NEG && stepY < 0 || exclude == Direction.Y_POS && stepY > 0);
            ctx.allowZ = !(exclude == Direction.Z_NEG && stepZ < 0 || exclude == Direction.Z_POS && stepZ > 0);
        }
        return ctx;
    }

    private static boolean addPriorityNeighbors(List<BlockCoord> neighbors, BlockCoord block, int stepX, int stepY, int stepZ, AxisContext ctx) {
        switch (ctx.priorityAxis) {
            case X_AXIS:
                neighbors.add(new BlockCoord(block.getX() + stepX, block.getY(), block.getZ()));
                if (ctx.allowZ) neighbors.add(new BlockCoord(block.getX(), block.getY(), block.getZ() + stepZ));
                if (ctx.allowY) neighbors.add(new BlockCoord(block.getX(), block.getY() + stepY, block.getZ()));
                return true;
            case Y_AXIS:
                neighbors.add(new BlockCoord(block.getX(), block.getY() + stepY, block.getZ()));
                if (ctx.allowX) neighbors.add(new BlockCoord(block.getX() + stepX, block.getY(), block.getZ()));
                if (ctx.allowZ) neighbors.add(new BlockCoord(block.getX(), block.getY(), block.getZ() + stepZ));
                return true;
            case Z_AXIS:
                neighbors.add(new BlockCoord(block.getX(), block.getY(), block.getZ() + stepZ));
                if (ctx.allowX) neighbors.add(new BlockCoord(block.getX() + stepX, block.getY(), block.getZ()));
                if (ctx.allowY) neighbors.add(new BlockCoord(block.getX(), block.getY() + stepY, block.getZ()));
                return true;
            default:
                return false;
        }
    }

    private static class AxisContext {
        Axis priorityAxis = Axis.NONE;
        boolean allowX = true;
        boolean allowY = true;
        boolean allowZ = true;
    }
    
    private static boolean rangeContains(double lBMin, double nBMin, double lBMax, double nBMax) {
        return nBMin <= lBMin && lBMax <=nBMax || lBMin <= nBMin && nBMax <= lBMax;
    }

    public static class RichAxisData {
        public Axis priority;
        public Direction exclude;
        public RichAxisData(Axis priority, Direction exclude) {
            this.priority = priority;
            this.exclude = exclude;
        }
    }
    
    /**
     * Test if the absolute difference between two values is small enough to be considered equal.
     * 
     * @param a The minuend
     * @param b The subtrahend
     * @param c Absolute(!) value to compare the difference with
     * @return True if the absolute difference is smaller or equals C.
     *         Returns false for negative C inputs.
     */
    public static boolean equal(double a, double b, double c) {
       if (c < 0.0) return false;
       return Math.abs(a-b) <= c;
    }
}
