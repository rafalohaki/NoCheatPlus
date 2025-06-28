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
package fr.neatmonster.nocheatplus.checks.inventory;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.TickTask;

/**
 * The InstantBow check will find out if a player pulled the string of their bow too fast.
 */
public class InstantBow extends Check {

    private static final float maxTime = 800f;
    /** Mutable holder to avoid repeated allocations. */
    private final long[] pullDurationHolder = new long[1];

    /**
     * Instantiates a new instant bow check.
     */
    public InstantBow() {
        super(CheckType.INVENTORY_INSTANTBOW);
    }

    /**
     * Checks a player.
     * 
     * @param player
     *            the player
     * @param force
     *            the force
     * @return true, if successful
     */
    public boolean check(final Player player, final float force, final long now) {

        boolean cancel = false;
        if (player != null) {
            final IPlayerData pData = DataManager.getPlayerData(player);
            final InventoryData data = pData.getGenericInstance(InventoryData.class);
            final InventoryConfig cc = pData.getGenericInstance(InventoryConfig.class);

            final long expectedPullDuration = computeExpectedPullDuration(force, cc);
            final boolean valid = isValidBowPull(cc, data, now, pullDurationHolder);
            final long pullDuration = pullDurationHolder[0];

            cancel = handlePullTiming(player, pData, cc, data,
                    expectedPullDuration, valid, pullDuration, now);

            debugOutput(player, pData, cc, force, pullDuration, expectedPullDuration);

            data.bowTracker.reset();
            data.instantBowShoot = now;
        }
        return cancel;
    }

    private long computeExpectedPullDuration(final float force, final InventoryConfig cc) {
        return (long) (maxTime - maxTime * (1f - force) * (1f - force)) - cc.instantBowDelay;
    }

    private boolean isValidBowPull(final InventoryConfig cc, final InventoryData data,
            final long now, final long[] durationHolder) {
        if (cc.instantBowStrict) {
            final boolean valid = data.bowTracker.getLast() != 0;
            durationHolder[0] = valid ? (now - data.bowTracker.getLast()) : 0L;
            return valid;
        }
        durationHolder[0] = now - data.instantBowShoot;
        return true;
    }

    private boolean handlePullTiming(final Player player, final IPlayerData pData,
            final InventoryConfig cc, final InventoryData data,
            final long expectedPullDuration, final boolean valid,
            final long pullDuration, final long now) {
        if (valid && (!cc.instantBowStrict || data.bowTracker.getLast() > 0L)
                && pullDuration >= expectedPullDuration) {
            data.instantBowVL *= 0.9D;
            return false;
        }
        if (valid && data.bowTracker.getLast() > now) {
            return false;
        }
        return handleInstantBowViolation(player, pData, cc, data,
                expectedPullDuration, valid, pullDuration);
    }

    private boolean handleInstantBowViolation(final Player player, final IPlayerData pData,
            final InventoryConfig cc, final InventoryData data,
            final long expectedPullDuration, final boolean valid,
            final long pullDuration) {
        final long correctedPullduration = valid
                ? (pData.getCurrentWorldData().shouldAdjustToLag(type)
                        ? (long) (TickTask.getLag(expectedPullDuration, true) * pullDuration)
                        : pullDuration)
                : 0;
        if (correctedPullduration >= expectedPullDuration) {
            return false;
        }
        final double difference = (expectedPullDuration - pullDuration) / 100D;
        data.instantBowVL += difference;
        return executeActions(player, data.instantBowVL, difference,
                cc.instantBowActions).willCancel();
    }

    private void debugOutput(final Player player, final IPlayerData pData,
            final InventoryConfig cc, final float force, final long pullDuration,
            final long expectedPullDuration) {
        if (pData.isDebugActive(type)
                && pData.hasPermission(Permissions.ADMINISTRATION_DEBUG, player)) {
            player.sendMessage(ChatColor.YELLOW + "NCP: " + ChatColor.GRAY +
                    "Bow shot - force: " + force + ", " +
                    (cc.instantBowStrict || pullDuration < 2 * expectedPullDuration
                        ? ("pull time: " + pullDuration)
                        : "") + "(" + expectedPullDuration + ")");
        }
    }
}
