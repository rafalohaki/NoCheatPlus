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
package fr.neatmonster.nocheatplus.checks.net;

import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.net.model.DataPacketFlying;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.compat.Folia;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.utilities.location.LocUtil;
import fr.neatmonster.nocheatplus.utilities.location.SafeLocations;
import fr.neatmonster.nocheatplus.utilities.location.TrigUtil;

/**
 * Misc. checks related to flying packets.
 */
public class Moving extends Check {

    
    public Moving() {
        super(CheckType.NET_MOVING);
    }

    long timeRespawn;
    long timeJoin;
    long timeTeleport;


    /**
     * Checks a player
     * 
     * @param player
     * @param packetData
     * @param data
     * @param cc
     * @param pData
     * @param cc
     * @return if to cancel
     */
    public boolean check(final Player player, final DataPacketFlying packetData, final NetData data, final NetConfig cc, 
                         final IPlayerData pData, final Plugin plugin) {
        
        boolean cancel = false;
        // Early return tests, for the case the player has teleported somewhere/respawned/joined too recently.
        final long now = System.currentTimeMillis();
        // NOTE: can still trigger on join if chunk isn't loaded and the player is sinking
        if (now - timeJoin < 20000 || now - timeTeleport < 5000 || now - timeRespawn < 5000 || !player.isOnline()) {
            return false;
        }
        // Work as ExtremeMove but for packet sent!
        // Observed: this seems to prevent long/mid distance blink cheats.
        else if (packetData.hasPos) {
            final MovingData mData = pData.getGenericInstance(MovingData.class);
            final Optional<Location> optLocation = SafeLocations.get(player);
            if (!optLocation.isPresent()) {
                return false;
            }
            final Location knownLocation = optLocation.get();
            final double hDistanceDiff = TrigUtil.distance(
                    knownLocation.getX(), knownLocation.getY(), knownLocation.getZ(),
                    packetData.getX(), packetData.getY(), packetData.getZ());
            final double yDistanceDiff = Math.abs(knownLocation.getY() - packetData.getY());

            // Vertical move.
            if (yDistanceDiff > 100.0) {
                data.movingVL++ ;
            }
            // Horizontal move.
            else if (hDistanceDiff > 100.0) {
                data.movingVL++ ;
            } 
            else data.movingVL *= 0.98;

            if (data.movingVL > 7) {
                cancel = executeActions(player, data.movingVL, 1, cc.movingActions).willCancel();
            }
            
            if (data.movingVL > 15) {
                // Player might be freezed by canceling, set back might turn it to normal
                data.movingVL = 0.0;
                Object task = null;
                task = Folia.runSyncTaskForEntity(player, plugin, (arg) -> {
                    final Location newTo = mData.hasSetBack() ? mData.getSetBack(knownLocation) :
                                           mData.hasMorePacketsSetBack() ? mData.getMorePacketsSetBack() :
                                           // Unsafe position! Null world or world not updated
                                           knownLocation;
                                           //null;
                    if (newTo == null) {
                        StaticLog.logSevere("[NoCheatPlus] Could not restore location for " + player.getName() + ", kicking them.");
                        CheckUtils.kickIllegalMove(player, pData.getGenericInstance(MovingConfig.class));
                    } 
                    else {
                        // Mask player teleport as a set back.
                        mData.prepareSetBack(newTo);
                        Folia.teleportEntity(player, LocUtil.clone(newTo), BridgeMisc.TELEPORT_CAUSE_CORRECTION_OF_POSITION);
                        // Request an Improbable update, unlikely that this is legit.
                        TickTask.requestImprobableUpdate(player.getUniqueId(), 0.3f);
                        if (pData.isDebugActive(CheckType.NET_MOVING)) 
                            debug(player, "Set back player: " + player.getName() + ":" + LocUtil.simpleFormat(newTo));
                    }
                }, null);
                if (!Folia.isTaskScheduled(task)) {
                    StaticLog.logWarning("[NoCheatPlus] Failed to schedule task (plugin="
                            + plugin.getName() + ", delay=1) Player: " + player.getName());
                }
                mData.resetTeleported(); // Cleanup, just in case.
            }
        }
        return cancel;
    }
    
    // Currently only related to these, no need to make another class.
    @EventHandler(priority = EventPriority.MONITOR)
    public void playerTeleport(PlayerTeleportEvent e) {
        timeTeleport = System.currentTimeMillis();
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void playerJoin(PlayerJoinEvent e) {
        timeJoin = System.currentTimeMillis();
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void playerRespawn(PlayerRespawnEvent e) {
        timeRespawn = System.currentTimeMillis();
    }
}
