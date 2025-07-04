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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.PacketType.Protocol;
import com.comphenix.protocol.PacketType.Sender;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.net.NetData;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.components.registry.feature.IDisableListener;
import fr.neatmonster.nocheatplus.components.registry.feature.INotifyReload;
import fr.neatmonster.nocheatplus.components.registry.feature.JoinLeaveListener;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.ConfigManager;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.stats.Counters;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.worlds.IWorldDataManager;

/**
 * Quick and dirty ProtocolLib setup.
 * 
 * @author asofold
 *
 */
public class ProtocolLibComponent implements IDisableListener, INotifyReload, JoinLeaveListener, Listener {

    // Static reference is problematic; consider using an accessible Counters instance.
    public static final int idNullPlayer = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstance(Counters.class).registerKey("packet.nullplayer");
    /** Unlikely to happen; requires protocol plugin review. */
    public static final int idInconsistentIsAsync = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstance(Counters.class).registerKey("packet.inconsistent.isasync");

    /**
     * Auxiliary method for suppressing exceptions.
     *
     * @param protocol
     * @param sender
     * @param name
     *            PacketType if available, null otherwise.
     * @return The matching {@link PacketType} or {@code null} if none is
     *         found.
     */
    public static PacketType findPacketTypeByName(Protocol protocol, Sender sender, String name) {
        try {
            return PacketType.findCurrent(protocol, sender, name);
        }
        catch (Throwable t) {
            // uh
            return null;
        }
    }

    // INSTANCE ----

    private static final List<PacketAdapter> registeredPacketAdapters = new LinkedList<PacketAdapter>();

    public ProtocolLibComponent(Plugin plugin) {
        try {
            register(plugin);
        } catch (RuntimeException ex) {
            StaticLog.logWarning("Failed to register ProtocolLib component.");
            if (ConfigManager.getConfigFile().getBoolean(ConfPaths.LOGGING_EXTENDED_STATUS)) {
                NCPAPIProvider.getNoCheatPlusAPI().getLogManager().debug(Streams.INIT, ex);
            }
        }
        /*
         * Register listeners only if a check is enabled; they
         * will be unregistered via EventRegistry when calling unregister.
         */
    }

    private void register(Plugin plugin) {
        final IWorldDataManager worldMan = NCPAPIProvider.getNoCheatPlusAPI().getWorldDataManager();
        if (worldMan.isActiveAnywhere(CheckType.NET)) {
            StaticLog.logInfo("Adding packet level hooks for ProtocolLib (MC "
                    + ProtocolLibrary.getProtocolManager().getMinecraftVersion().getVersion() + ")...");
            registerDebugAdapterIfNeeded(plugin);
            registerUseEntityAdapterIfNeeded(plugin, worldMan);
            registerMovementAdaptersIfNeeded(plugin, worldMan);
            registerKeepAliveAdapterIfNeeded(plugin, worldMan);
            registerIfActive(CheckType.NET_SOUNDDISTANCE, plugin,
                    "fr.neatmonster.nocheatplus.checks.net.protocollib.SoundDistance");
            registerIfActive(CheckType.NET_WRONGTURN, plugin,
                    "fr.neatmonster.nocheatplus.checks.net.protocollib.WrongTurnAdapter");
            registerCatchAllAdapterIfNeeded(plugin, worldMan);
            registerNoSlowAdapterIfNeeded(plugin);
            registerFightAdapterIfSupported(plugin);
            logActivationSummary();
        } else {
            StaticLog.logInfo("No packet level checks activated.");
        }
    }

    /** Register the debug adapter if debugging is enabled. */
    private void registerDebugAdapterIfNeeded(Plugin plugin) {
        if (ConfigManager.isTrueForAnyConfig(ConfPaths.NET + ConfPaths.SUB_DEBUG)
                || ConfigManager.isTrueForAnyConfig(ConfPaths.CHECKS_DEBUG)) {
            // Debug logging. Only activates if debug is set for checks or checks.net, not on the fly.
            register("fr.neatmonster.nocheatplus.checks.net.protocollib.DebugAdapter", plugin);
        }
    }

    /** Register UseEntityAdapter depending on server version and check state. */
    private void registerUseEntityAdapterIfNeeded(Plugin plugin, IWorldDataManager worldMan) {
        if (ServerVersion.compareMinecraftVersion("1.6.4") <= 0) {
            // Don't use this listener.
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().info(Streams.STATUS,
                    "Disable EntityUseAdapter due to incompatibilities. Use fight.speed instead of net.attackfrequency.");
        } else {
            registerIfActive(CheckType.NET_ATTACKFREQUENCY, plugin,
                    "fr.neatmonster.nocheatplus.checks.net.protocollib.UseEntityAdapter");
        }
    }

    /** Register adapters related to moving/flying packets. */
    private void registerMovementAdaptersIfNeeded(Plugin plugin, IWorldDataManager worldMan) {
        if (worldMan.isActiveAnywhere(CheckType.NET_FLYINGFREQUENCY)
                || worldMan.isActiveAnywhere(CheckType.NET_MOVING)) {
            // (Also sets lastKeepAliveTime, if enabled.)
            register("fr.neatmonster.nocheatplus.checks.net.protocollib.MovingFlying", plugin);
            register("fr.neatmonster.nocheatplus.checks.net.protocollib.OutgoingPosition", plugin);
        }
    }

    /** Register KeepAliveAdapter if relevant checks are enabled. */
    private void registerKeepAliveAdapterIfNeeded(Plugin plugin, IWorldDataManager worldMan) {
        if (worldMan.isActiveAnywhere(CheckType.NET_KEEPALIVEFREQUENCY)
                || worldMan.isActiveAnywhere(CheckType.FIGHT_GODMODE)) {
            // (Set lastKeepAlive if this or fight.godmode is enabled.)
            register("fr.neatmonster.nocheatplus.checks.net.protocollib.KeepAliveAdapter", plugin);
        }
    }

    /** Register CatchAllAdapter if supported by the server version. */
    private void registerCatchAllAdapterIfNeeded(Plugin plugin, IWorldDataManager worldMan) {
        if (ServerVersion.compareMinecraftVersion("1.9") < 0
                && worldMan.isActiveAnywhere(CheckType.NET_PACKETFREQUENCY)) {
            register("fr.neatmonster.nocheatplus.checks.net.protocollib.CatchAllAdapter", plugin);
        }
    }

    /** Register NoSlow adapter if configured and supported. */
    private void registerNoSlowAdapterIfNeeded(Plugin plugin) {
        if (ConfigManager.isTrueForAnyConfig(ConfPaths.MOVING_SURVIVALFLY_EXTENDED_NOSLOW)
                && ServerVersion.compareMinecraftVersion("1.8") >= 0) {
            register("fr.neatmonster.nocheatplus.checks.net.protocollib.NoSlow", plugin);
        }
    }

    /** Always register the fight adapter for supported server versions. */
    private void registerFightAdapterIfSupported(Plugin plugin) {
        registerIfVersionAtLeast("1.8", plugin,
                "fr.neatmonster.nocheatplus.checks.net.protocollib.Fight");
    }

    /** Log a summary of activated packet listeners. */
    private void logActivationSummary() {
        if (!registeredPacketAdapters.isEmpty()) {
            List<String> names = new ArrayList<String>(registeredPacketAdapters.size());
            for (PacketAdapter adapter : registeredPacketAdapters) {
                names.add(adapter.getClass().getSimpleName());
            }
            StaticLog.logInfo("Available (and activated) packet level hooks: "
                    + StringUtil.join(names, " | "));
            NCPAPIProvider.getNoCheatPlusAPI().addFeatureTags("packet-listeners", names);
        } else {
            StaticLog.logInfo("No packet level hooks activated.");
        }
    }

    @SuppressWarnings("unchecked")
    private void register(String name, Plugin plugin) {
        Throwable t = null;
        try {
            Class<?> clazz = Class.forName(name);
            register((Class<? extends PacketAdapter>) clazz, plugin);
            return;
        } catch (ClassNotFoundException e) {
            t = e;
        } catch (ClassCastException e) {
            t = e;
        }
        StaticLog.logWarning("Could not register packet level hook: " + name);
        StaticLog.logWarning(t);
    }

    private void register(Class<? extends PacketAdapter> clazz, Plugin plugin) {
        try {
            // Construct a new instance using reflection.
            PacketAdapter adapter = clazz.getDeclaredConstructor(Plugin.class).newInstance(plugin);
            ProtocolLibrary.getProtocolManager().addPacketListener(adapter);
            registeredPacketAdapters.add(adapter);
        } catch (Throwable t) {
            StaticLog.logWarning("Could not register packet level hook: " + clazz.getSimpleName());
            StaticLog.logWarning(t);
            if (t.getCause() != null) {
                StaticLog.logWarning(t.getCause());
            }
        }
    }

    /**
     * Register the adapter if the given check is active anywhere.
     *
     * @param check the {@link CheckType} to test
     * @param plugin the plugin instance
     * @param adapterName fully qualified adapter class name
     */
    private void registerIfActive(CheckType check, Plugin plugin, String adapterName) {
        if (NCPAPIProvider.getNoCheatPlusAPI().getWorldDataManager().isActiveAnywhere(check)) {
            register(adapterName, plugin);
        }
    }

    /**
     * Register the adapter if the current server version is at least the given
     * version.
     *
     * @param version minimal Minecraft version
     * @param plugin the plugin instance
     * @param adapterName fully qualified adapter class name
     */
    private void registerIfVersionAtLeast(String version, Plugin plugin, String adapterName) {
        if (ServerVersion.compareMinecraftVersion(version) >= 0) {
            register(adapterName, plugin);
        }
    }

    @Override
    public void onDisable() {
        unregister();
    }

    @Override
    public void onReload() {
        unregister();
        NCPAPIProvider.getNoCheatPlusAPI().getPlayerDataManager().removeGenericInstance(NetData.class); // Currently needed for FlyingFrequency.
        // Use the plugin manager because there is no static plugin getter.
        register(Bukkit.getPluginManager().getPlugin("NoCheatPlus"));
    }

    private void unregister() {
        final ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        final NoCheatPlusAPI api = NCPAPIProvider.getNoCheatPlusAPI();
        for (PacketAdapter adapter : registeredPacketAdapters) {
            try {
                protocolManager.removePacketListener(adapter);
                api.removeComponent(adapter); // Bit heavy, but consistent.
            } catch (Throwable t) {
                StaticLog.logWarning("Failed to unregister packet level hook: " + adapter.getClass().getName());
            }

        }
        registeredPacketAdapters.clear();
    }

    protected static void unregister(PacketAdapter adapter) {
        final ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        final NoCheatPlusAPI api = NCPAPIProvider.getNoCheatPlusAPI();
        try {
            protocolManager.removePacketListener(adapter);
            api.removeComponent(adapter); // Bit heavy, but consistent.
            registeredPacketAdapters.remove(adapter);
            List<String> names = new ArrayList<String>(registeredPacketAdapters.size());
            for (PacketAdapter adaptern : registeredPacketAdapters) {
                names.add(adaptern.getClass().getSimpleName());
            }
            api.setFeatureTags("packet-listeners", names);
            StaticLog.logInfo("Unregistered packet level hook:" + adapter.getClass().getName());
        } catch (Throwable t) {
            StaticLog.logWarning("Failed to unregister packet level hook: " + adapter.getClass().getName());
        }
    }

    @Override
    public void playerJoins(final Player player) {
        if (!registeredPacketAdapters.isEmpty()) {
            DataManager.getInstance().getGenericInstance(player, NetData.class).onJoin(player);
        }
    }

    @Override
    public void playerLeaves(final Player player) {
        if (!registeredPacketAdapters.isEmpty()) {
            DataManager.getInstance().getGenericInstance(player, NetData.class).onLeave(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerRespawn(final PlayerRespawnEvent event) {
        if (!registeredPacketAdapters.isEmpty()) {
            final Player player = event.getPlayer();
            final NetData data = DataManager.getInstance().getGenericInstance(player, NetData.class);
            data.onJoin(player);
            final Location loc = event.getRespawnLocation();
            data.teleportQueue.onTeleportEvent(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerTeleport(final PlayerTeleportEvent event) {
        if (!registeredPacketAdapters.isEmpty()) {
            // Consider moving this to MovingListener.
            // May still add cancelled UNKNOWN events; needs testing.
            final Location to = event.getTo();
            if (to == null) {
                return;
            }
            final Player player = event.getPlayer();
            final IPlayerData pData = DataManager.getInstance().getPlayerData(player);
            final NetData data = pData.getGenericInstance(NetData.class);
            if (pData.isCheckActive(CheckType.NET_FLYINGFREQUENCY, player)) {
                // Register expected location for comparison with outgoing packets.
                data.teleportQueue.onTeleportEvent(to.getX(), to.getY(), to.getZ(), to.getYaw(), to.getPitch());
            }
            data.clearFlyingQueue();
        }
    }

}
