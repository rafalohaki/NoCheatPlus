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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.players.IPlayerData;

public class KeepAliveFrequency extends Check implements Listener {

    public KeepAliveFrequency() {
        super(CheckType.NET_KEEPALIVEFREQUENCY);
    }

    /**
     * Join timestamps per player for delaying checks after login.
     */
    private final Map<UUID, Long> joinTimestamps = new ConcurrentHashMap<>();

    /**
     * Checks hasBypass on violation only.
     * @param player
     * @param time
     * @param data
     * @param cc
     * @return If to cancel.
     */
    public boolean check(final Player player, final long time, final NetData data, final NetConfig cc, final IPlayerData pData) {
        data.keepAliveFreq.add(time, 1f);
        final float first = data.keepAliveFreq.bucketScore(0);
        final long now = System.currentTimeMillis();

        boolean cancel = false;
        if (!isJoinDelayActive(player, now, cc.keepAliveFrequencyStartupDelay) && first > 1f) {
            final double vl = Math.max(first - 1f, data.keepAliveFreq.score(1f) - data.keepAliveFreq.numberOfBuckets());
            cancel = executeActions(player, vl, 1.0, cc.keepAliveFrequencyActions).willCancel();
        }
        return cancel;
    }
    // Event listener probably shouldn't be used here, but I don't think it will be
    // needed to make another class just for this.
    @EventHandler
    public void playerJoin(PlayerJoinEvent e) {
        final Player player = e.getPlayer();
        if (player != null) {
            joinTimestamps.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void playerQuit(PlayerQuitEvent e) {
        final Player player = e.getPlayer();
        if (player != null) {
            joinTimestamps.remove(player.getUniqueId());
        }
    }

    /**
     * Check if join delay is still active for the player.
     *
     * @param player The player to check for.
     * @param now    The current system time in milliseconds.
     * @param delay  The configured delay after join.
     * @return True if join delay is active, false otherwise.
     */
    private boolean isJoinDelayActive(Player player, long now, long delay) {
        if (player == null) {
            return false;
        }
        final Long joinTime = joinTimestamps.get(player.getUniqueId());
        return joinTime != null && now - joinTime < delay;
    }

}
