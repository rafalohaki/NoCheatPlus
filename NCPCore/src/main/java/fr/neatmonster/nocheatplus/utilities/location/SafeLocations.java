package fr.neatmonster.nocheatplus.utilities.location;

import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Utility methods for obtaining player locations safely.
 */
public final class SafeLocations {

    private SafeLocations() {}

    private static final ThreadLocal<Location> CACHE =
            ThreadLocal.withInitial(() -> new Location(null, 0.0, 0.0, 0.0));

    /**
     * Get the current location of the player, if available.
     *
     * @param player the player
     * @return an Optional containing the current location if available
     */
    public static Optional<Location> get(final Player player) {
        if (player == null) {
            return Optional.empty();
        }
        try {
            final Location use = CACHE.get();
            player.getLocation(use);
            if (use.getWorld() == null) {
                return Optional.empty();
            }
            return Optional.of(use);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
