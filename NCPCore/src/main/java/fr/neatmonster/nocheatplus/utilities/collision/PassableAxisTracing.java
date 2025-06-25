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

import java.util.UUID;

import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeReference;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker.BlockChangeEntry;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;

public class PassableAxisTracing extends AxisTracing implements ICollidePassable {

    private BlockCache blockCache;
    private BlockChangeTracker blockChangeTracker = null;
    private BlockChangeReference blockChangeRef = null;
    private int tick;
    private UUID worldId;

    // Consider providing another option for margins (option to skip margin for the
    // axis-start point, or alter ignoreFirst behavior).
    // An iteration margin might be useful as well, for example 0.5 below for
    // fences.

    public BlockCache getBlockCache() {
        return blockCache;
    }

    public void setBlockCache(BlockCache blockCache) {
        this.blockCache = blockCache;
    }

    @Override
    public void setBlockChangeTracker(BlockChangeTracker blockChangeTracker, 
            BlockChangeReference blockChangeReference, int tick, UUID worldId) {
        this.blockChangeTracker = blockChangeTracker;
        this.blockChangeRef = blockChangeReference;
        this.tick = tick;
        this.worldId = worldId;
    }

    @Override
    protected void collectInitiallyCollidingBlocks(double minX, double minY, double minZ, double maxX, double maxY,
            double maxZ, BlockPositionContainer results) {
        BlockProperties.collectInitiallyCollidingBlocks(blockCache, minX, minY, minZ, maxX, maxY, maxZ, results);
    }

    @Override
    protected boolean step(final int blockX, final int blockY, final int blockZ, 
            final double minX, final double minY, final double minZ, 
            final double maxX, final double maxY, final double maxZ, 
            final Axis axis, final int increment) {
        if (BlockProperties.isPassableBox(blockCache, blockX, blockY, blockZ, minX, minY, minZ, maxX, maxY, maxZ)) {
            /*
             * HEIGHT150 -> if not passable, how/where to test for the block
             * change tracker? For example, if the y-offset from the block is
             * below 0.5, check the block underneath. One method might check the
             * box normally while also handling past states opportunistically.
             * Prefer to set the air/liquid/whichever block above the fence as
             * ignored, in order to allow collision with the 1.0 height border of
             * the fence (legacy only?).
             */
            return true;
        }
        // Recovery attempt via the BlockChangeTracker.
        if (blockChangeTracker != null) {
            // Opportunistic (FCFS, no consistency).
            // Should use BlockChangeTracker.isPassableBox(...) with iteration for
            // better accuracy.
            BlockChangeEntry entry = blockChangeTracker.getBlockChangeEntry(blockChangeRef, tick, worldId, blockX, blockY + 1, blockZ, null);
            if (entry == null) entry = blockChangeTracker.getBlockChangeEntry(blockChangeRef, tick, worldId, blockX, blockY, blockZ, null);
            if (entry != null) {
                blockChangeRef.updateSpan(entry);
                return true;
            }
        }
        // No condition for passing through found.
        collides = true;
        return false;
    }

    @Override
    public void set(PlayerLocation from, PlayerLocation to) {
        set(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
        setBlockCache(from.getBlockCache());
    }

    @Override
    public boolean mightNeedSplitAxisHandling() {
        return false;
    }

    @Override
    public void cleanup() {
        blockCache = null;
        blockChangeTracker = null;
        blockChangeRef = null;
        worldId = null;
    }

}
