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
package fr.neatmonster.nocheatplus.utilities.map;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.utilities.StringUtil;

/**
 * Map-related static utility.
 * 
 * @author asofold
 *
 */
public class MapUtil {

    /** Default cooldown in milliseconds between chunk load attempts. */
    private static final long CHUNK_LOAD_COOLDOWN_MS = TimeUnit.SECONDS.toMillis(2);

    /**
     * Track the last attempted chunk load per world.
     * Map key is the world UUID, value maps chunk key to last attempt time.
     */
    private static final Map<UUID, Map<Long, Long>> CHUNK_LOAD_CACHE = new ConcurrentHashMap<>();

    /** Generate a unique key for chunk coordinates. */
    private static long chunkKey(final int cx, final int cz) {
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
    }

    /**
     * Determine whether a chunk is currently on cooldown for loading.
     *
     * @param world the world
     * @param cx chunk x coordinate
     * @param cz chunk z coordinate
     * @param now the current timestamp
     * @return true if a new load should be skipped
     */
    private static boolean isOnCooldown(final World world, final int cx, final int cz, final long now) {
        final Map<Long, Long> worldCache = CHUNK_LOAD_CACHE.get(world.getUID());
        if (worldCache == null) {
            return false;
        }
        final Long last = worldCache.get(chunkKey(cx, cz));
        return last != null && now - last < CHUNK_LOAD_COOLDOWN_MS;
    }

    /**
     * Mark a chunk as attempted to load.
     *
     * @param world the world
     * @param cx chunk x coordinate
     * @param cz chunk z coordinate
     * @param now the current timestamp
     */
    private static void markLoadAttempt(final World world, final int cx, final int cz, final long now) {
        CHUNK_LOAD_CACHE
            .computeIfAbsent(world.getUID(), k -> new ConcurrentHashMap<>())
            .put(chunkKey(cx, cz), now);
    }

    /**
     * Find the appropriate BlockFace.
     * @param x Exact increments.
     * @param y
     * @param z
     * @return
     */
    public static BlockFace matchBlockFace(int x, int y, int z) {
        for (BlockFace blockFace : BlockFace.values()) {
            if (blockFace.getModX() == x && blockFace.getModY() == y && blockFace.getModZ() == z) {
                return blockFace;
            }
        }
        return null;
    }

    /**
     * Convenience method to check if the bounds as returned by getBounds cover
     * a whole block.
     *
     * @param bounds
     *            Can be null, must have 6 fields.
     * @return true, if is full bounds
     */
    public static final boolean isFullBounds(final double[] bounds) {
        if (bounds == null) return false;
        for (int i = 0; i < 3; i ++) {
            if (bounds[i] > 0.0) return false;
            if (bounds[i + 3] < 1.0) return false;
        }
        return true;
    }

    /**
     * Check if at least one chunk within the coordinates should be attempted to
     * load. This consults an internal cooldown cache.
     *
     * @param world the world
     * @param x the x coordinate
     * @param z the z coordinate
     * @param xzMargin search radius
     * @return {@code true} if any chunk should be loaded
     */
    public static boolean shouldLoadChunks(final World world, final double x, final double z,
            final double xzMargin) {
        if (world == null) return false;
        final int minX = Location.locToBlock(x - xzMargin) / 16;
        final int maxX = Location.locToBlock(x + xzMargin) / 16;
        final int minZ = Location.locToBlock(z - xzMargin) / 16;
        final int maxZ = Location.locToBlock(z + xzMargin) / 16;
        final long now = System.currentTimeMillis();
        final Map<Long, Long> worldCache = CHUNK_LOAD_CACHE.get(world.getUID());
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    if (worldCache == null) {
                        return true;
                    }
                    final Long last = worldCache.get(chunkKey(cx, cz));
                    if (last == null || now - last >= CHUNK_LOAD_COOLDOWN_MS) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check if chunks are loaded and load all not yet loaded chunks, using
     * normal world coordinates.<br>
     * NOTE: Not sure where to put this. Method does not use any caching.
     *
     * @param world
     *            the world
     * @param x
     *            the x
     * @param z
     *            the z
     * @param xzMargin
     *            the xz margin
     * @return Number of loaded chunks.
     */
    public static int ensureChunksLoaded(final World world, final double x, final double z, final double xzMargin) {
        int loaded = 0;
        final int minX = Location.locToBlock(x - xzMargin) / 16;
        final int maxX = Location.locToBlock(x + xzMargin) / 16;
        final int minZ = Location.locToBlock(z - xzMargin) / 16;
        final int maxZ = Location.locToBlock(z + xzMargin) / 16;
        final long now = System.currentTimeMillis();
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    if (isOnCooldown(world, cx, cz, now)) {
                        continue;
                    }
                    markLoadAttempt(world, cx, cz, now);
                    try {
                        world.getChunkAt(cx, cz);
                        loaded++;
                    } catch (Exception ex) {
                        // (Can't seem to catch more precisely: TileEntity with CB 1.7.10)
                        NCPAPIProvider.getNoCheatPlusAPI().getLogManager().severe(Streams.STATUS,
                                "Failed to load chunk at " + (cx * 16) + "," + (cz * 16)
                                        + " (real coordinates):\n" + StringUtil.throwableToString(ex));
                        // (Don't count as loaded.)
                    }
                }
            }
        }
        return loaded;
    }

}
