package fr.neatmonster.nocheatplus.compat.bukkit.model;

/**
 * Utility methods for working with Bukkit block shape models.
 */
public final class BukkitModelUtil {

    private BukkitModelUtil() {}

    /**
     * Clamp the given height so it does not exceed one block.
     *
     * @param height the requested height
     * @return {@code Math.min(height, 1.0)}
     */
    public static double clampHeight(double height) {
        return Math.min(height, 1.0);
    }
}
