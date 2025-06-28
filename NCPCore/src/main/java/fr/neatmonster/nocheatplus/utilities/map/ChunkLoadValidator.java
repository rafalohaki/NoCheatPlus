package fr.neatmonster.nocheatplus.utilities.map;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.permissions.Permissions;

/**
 * Utility for validating and rate limiting chunk loads per player.
 */
public final class ChunkLoadValidator {

    private static final Map<UUID, Long> LAST_LOAD = new ConcurrentHashMap<>();

    /** Minimum time between chunk loads in milliseconds. */
    private static final long RATE_LIMIT_MS = 1000L;

    private static int maxDistance = 0;

    private ChunkLoadValidator() {}

    /**
     * Set the maximum allowed distance from the world spawn before chunks are loaded.
     * Set to {@code 0} to disable.
     *
     * @param distance the maximum distance in blocks
     */
    public static void setMaxDistance(int distance) {
        maxDistance = Math.max(0, distance);
    }

    /**
     * Test if chunk loading is allowed for the player at the given location.
     *
     * @param player the player requesting chunk loading
     * @param location the target location
     * @return {@code true} if loading should proceed
     */
    public static boolean canLoad(Player player, Location location) {
        if (player == null || location == null) {
            return false;
        }
        if (player.hasPermission(Permissions.CHUNKLOAD_BYPASS.getLowerCaseStringRepresentation())) {
            return true;
        }
        if (!rateLimit(player.getUniqueId())) {
            StaticLog.logInfo("Skipping chunk load for " + player.getName() + ": rate limit exceeded.");
            return false;
        }
        if (!validateLocation(location)) {
            StaticLog.logInfo("Skipping chunk load for " + player.getName() + ": outside allowed area.");
            return false;
        }
        return true;
    }

    private static boolean rateLimit(UUID id) {
        long now = System.currentTimeMillis();
        Long last = LAST_LOAD.get(id);
        if (last != null && now - last < RATE_LIMIT_MS) {
            return false;
        }
        LAST_LOAD.put(id, now);
        return true;
    }

    private static boolean validateLocation(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        WorldBorder border = world.getWorldBorder();
        if (border != null && !border.isInside(location)) {
            return false;
        }
        if (maxDistance > 0) {
            Location spawn = world.getSpawnLocation();
            if (spawn != null && spawn.getWorld() != null) {
                if (spawn.distanceSquared(location) > (double) maxDistance * maxDistance) {
                    return false;
                }
            }
        }
        return true;
    }
}
