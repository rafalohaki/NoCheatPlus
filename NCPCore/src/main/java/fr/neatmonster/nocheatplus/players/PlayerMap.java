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
package fr.neatmonster.nocheatplus.players;

import java.util.Map;
import java.util.UUID;

import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;

/**
 * Map (online) players in various ways. Fall back to Bukkit methods if not
 * mapped.
 *
 * @author asofold
 */
public final class PlayerMap {

    // TODO: Remove, store in PlayerData (WeakHandle rather), perhaps keep some of the fetching logic.

    /**
     * Carry basic information about players.
     *
     * @author asofold
     */
    public static final class PlayerInfo {

        public final UUID id;
        public final String name;
        public Player player = null;

        public PlayerInfo(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        public boolean matchesExact(UUID id, String name) {
            return this.id.equals(id) && this.name.equals(name);
        }

    }


    private final boolean storePlayerInstances;
    private final boolean hasGetPlayer_UUID = ReflectionUtil.getMethod(Bukkit.class, "getPlayer", UUID.class) != null;

    // TODO: Map types (copy on write, lazy erase, or just keep ordinary maps?)
    private final Map<UUID, PlayerInfo> idInfoMap = new ConcurrentHashMap<>();
    private final Map<String, PlayerInfo> nameInfoMap = new ConcurrentHashMap<>();
    // TODO: Consider: Get players by prefix (primary thread only, e.g. for use with commands).
    // TODO: get uuid/name methods?
    // TODO: unlink Player references on remove for better gc?


    public PlayerMap(boolean storePlayerInstances) {
        this.storePlayerInstances = storePlayerInstances;
        if (storePlayerInstances) {
            StaticLog.logInfo("Player instances are stored for efficiency.");
        }
    }

    // Public methods.

    public boolean storesPlayerInstances() {
        return storePlayerInstances;
    }

    public boolean hasPlayerInfo(final UUID id) {
        return idInfoMap.containsKey(id);
    }

    public boolean hasPlayerInfoExact(final String name) {
        return nameInfoMap.containsKey(name);
    }

    public PlayerInfo getPlayerInfo(final UUID id) {
        return idInfoMap.get(id);
    }

    public PlayerInfo getPlayerInfoExact(final String name) {
        return nameInfoMap.get(name);
    }

    public Player getPlayer(final UUID id) {
        final PlayerInfo info = idInfoMap.get(id);
        if (info != null) {
            if (info.player != null) {
                return info.player;
            }
            if (storePlayerInstances) {
                info.player = getPlayerBukkit(id);
                return info.player;
            } else {
                return getPlayerBukkit(id);
            }
        } else {
            return getPlayerBukkit(id);
        }
    }

    public Player getPlayer(final String name) {
        return getPlayerExact(name);
    }

    @SuppressWarnings("deprecation")
    public Player getPlayerExact(final String name) {
        final PlayerInfo info = nameInfoMap.get(name);
        if (info != null) {
            if (info.player != null) {
                return info.player;
            }
            if (storePlayerInstances) {
                info.player = getPlayerBukkit(info.id);
                return info.player;
            } else {
                return getPlayerBukkit(info.id);
            }
        } else {
            return Bukkit.getPlayerExact(name);
        }
    }

    private Player getPlayerBukkit(final UUID id) {
        if (hasGetPlayer_UUID) {
            return Bukkit.getPlayer(id);
        } else {
            LogManager.getLogger(Bukkit.getLogger()).log(Level.WARN, "getPlayer should not be null, please investigate");
            // HACKS
            final IPlayerData pData = DataManager.getPlayerData(id);
            if (pData != null) {
                return getPlayer(pData.getPlayerName());
            } else {
                // Backwards compatibility.
                return scanForPlayer(id);
            }
        }
    }

    public PlayerInfo updatePlayer(final Player player) {
        final UUID id = player.getUniqueId();
        final String name = player.getName();
        PlayerInfo info = idInfoMap.get(id);
        if (info != null) {
            if (info.matchesExact(id, name)) {
                // Nothing to do, except updating the player instance.
                if (storePlayerInstances) {
                    info.player = player;
                }
                return info;
            } else {
                // Remove the info.
                remove(info);
            }
        }
        // Create and link a new info.
        info = new PlayerInfo(id, name);
        if (storePlayerInstances) {
            info.player = player;
        }
        ensureRemoved(info);
        idInfoMap.put(id, info);
        nameInfoMap.put(name, info);
        return info;
    }

    /**
     * Remove the instance reference, if present at all.
     *
     * @param id
     */
    public void removePlayerInstance(final UUID id) {
        final PlayerInfo info = idInfoMap.get(id);
        if (info != null) {
            info.player = null;
        }
    }

    public boolean remove(final Player player) {
        return ensureRemoved(new PlayerInfo(player.getUniqueId(), player.getName()));
    }

    public void clear() {
        idInfoMap.clear();
        nameInfoMap.clear();
    }

    public int size() {
        // TODO: Consistency?
        return idInfoMap.size();
    }

    // Private methods.

    private Player scanForPlayer(final UUID id) {
        // TODO: Add a mapping for id->name for this case?
        for (final Player player : BridgeMisc.getOnlinePlayers()) {
            if (id.equals(player.getUniqueId())) {
                return player;
            }
        }
        return null;
    }

    /**
     * Ensure there are no entries for the given info (for the case of
     * inconsistent combinations).
     *
     * @param info
     * @return
     */
    private boolean ensureRemoved(final PlayerInfo info) {
        PlayerInfo ref = idInfoMap.get(info.id);
        boolean changed = false;
        if (ref != null) {
            remove(ref);
            changed = true;
        }
        ref = nameInfoMap.get(info.name);
        if (ref != null) {
            remove(ref);
            changed = true;
        }
        return changed;
    }

    /**
     * Remove an existing info from all mappings, only call for consistent
     * states.
     *
     * @param info
     * @return
     */
    private boolean remove(final PlayerInfo info) {
        boolean altered = false;
        altered |= idInfoMap.remove(info.id) != null;
        altered |= nameInfoMap.remove(info.name) != null;
        return altered;
    }

}