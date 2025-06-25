package fr.neatmonster.nocheatplus.checks.moving.helper;

import java.util.Set;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.components.modifier.IAttributeAccess;

/**
 * Helper for initial player move checks such as location consistency and
 * sprinting state updates.
 */
public final class MovePreChecks {

    private MovePreChecks() {}

    /**
     * Ensure the player's location is consistent with server state.
     *
     * @param player the player
     * @param from previous location
     * @param data player specific data
     * @param cc moving config
     * @param playersEnforce names of players awaiting enforcement
     * @return enforced location or {@code null}
     */
    public static Location checkLocationConsistency(Player player, Location from,
            MovingData data, MovingConfig cc, Set<String> playersEnforce,
            ILocationEnforcer enforcer) {
        if (cc.enforceLocation && playersEnforce.contains(player.getName())) {
            Location newTo = enforcer.enforce(player, from, data);
            playersEnforce.remove(player.getName());
            return fr.neatmonster.nocheatplus.utilities.location.LocUtil.clone(newTo);
        }
        return null;
    }

    /**
     * Update sprinting related timing and multipliers.
     *
     * @param player the player
     * @param time current system time
     * @param data player data
     * @param cc moving config
     * @param attributeAccess attribute accessor
     */
    public static void updateSprinting(Player player, long time, MovingData data,
            MovingConfig cc, IAttributeAccess attributeAccess) {
        if (player == null) {
            return;
        }
        if (player.isSprinting() || cc.assumeSprint) {
            if (player.getFoodLevel() > 5 || player.getAllowFlight()
                    || player.isFlying()) {
                data.timeSprinting = time;
                data.multSprinting = attributeAccess.getSprintAttributeMultiplier(player);
                if (data.multSprinting == Double.MAX_VALUE) {
                    data.multSprinting = 1.30000002;
                } else if (cc.assumeSprint && data.multSprinting == 1.0) {
                    data.multSprinting = 1.30000002;
                }
            } else if (time < data.timeSprinting) {
                data.timeSprinting = 0;
            }
        } else if (time < data.timeSprinting) {
            data.timeSprinting = 0;
        }
    }

    /** Simple abstraction to enforce a location for a player. */
    @FunctionalInterface
    public interface ILocationEnforcer {
        Location enforce(Player player, Location from, MovingData data);
    }
}
