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
package fr.neatmonster.nocheatplus.checks.net.protocollib;

import java.util.Collections;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketEvent;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.net.KeepAliveFrequency;
import fr.neatmonster.nocheatplus.checks.net.NetConfig;
import fr.neatmonster.nocheatplus.checks.net.NetData;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;

/**
 * Limit keep alive packet frequency, set lastKeepAliveTime (even if disabled,
 * in case fight.godmode is enabled).
 * 
 * @author asofold
 *
 */
public class KeepAliveAdapter extends BaseAdapter {

    /** Dummy check for bypass checking and actions execution. */
    private final KeepAliveFrequency frequencyCheck = new KeepAliveFrequency();

    public KeepAliveAdapter(Plugin plugin) {
        super(plugin, ListenerPriority.LOW, PacketType.Play.Client.KEEP_ALIVE);
        this.checkType = CheckType.NET_KEEPALIVEFREQUENCY;
        // Add feature tags for checks.
        if (NCPAPIProvider.getNoCheatPlusAPI().getWorldDataManager().isActiveAnywhere(
                CheckType.NET_KEEPALIVEFREQUENCY)) {
            NCPAPIProvider.getNoCheatPlusAPI().addFeatureTags(
                    "checks", Collections.singletonList(KeepAliveFrequency.class.getSimpleName()));
        }
        NCPAPIProvider.getNoCheatPlusAPI().addComponent(frequencyCheck);
    }

    @Override
    public void onPacketReceiving(final PacketEvent event) {
        try {
            if (event.isPlayerTemporary()) return;
        } catch(NoSuchMethodError e) {
            // Ignore: method not available on older ProtocolLib versions
        }
        final long time = System.currentTimeMillis();
        final Player player = event.getPlayer();
        if (player == null) {
            counters.add(ProtocolLibComponent.idNullPlayer, 1);
            event.setCancelled(true);
            return;
        }
        // Always update last received time.
        final IPlayerData pData = DataManager.getInstance().getPlayerDataSafe(player);
        if (pData == null) return;
        final NetData data = pData.getGenericInstance(NetData.class);
        data.lastKeepAliveTime = time;
        final NetConfig cc = pData.getGenericInstance(NetConfig.class);

        // Run check(s).
        // Consider matching incoming keep alive responses against outgoing
        // requests.
        // Model actual packet sequences better (flying vs. keep alive vs.
        // request/ping).
        // Better integrate with the god-mode check / trigger reset ndt.
        if (frequencyCheck.isEnabled(player, pData) 
                && frequencyCheck.check(player, time, data, cc, pData)) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        // Maybe detect if a keep alive wasn't requested and allow cancelling.
    }

}
