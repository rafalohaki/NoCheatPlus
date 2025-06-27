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

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.logging.StaticLog;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.CheckType;


/**
 * Static access API remaining from the previous mix of static/non-static.
 * 
 * Will be moved or removed.
 * 
 * @author asofold
 *
 */
public class DataManager {

    private static DataManager instance;

    private final PlayerDataManager playerDataManager;

    /**
     * Create a new DataManager service wrapping the given manager. The
     * last created instance is used for legacy access via {@link #getInstance()}.
     *
     * @param playerDataManager the backing PlayerDataManager
     */
    public DataManager(final PlayerDataManager playerDataManager) {
        this.playerDataManager = playerDataManager;
        instance = this;
    }

    /**
     * Legacy accessor for callers still using the singleton pattern.
     *
     * @return current DataManager instance
     * @deprecated inject the DataManager service instead of calling this method
     */
    @Deprecated
    public static DataManager getInstance() {
        return instance;
    }

    /*
     * DataManager currently acts as a static facade to {@link PlayerDataManager}.
     * A future refactoring might detach the underlying manager and replace this
     * class with a dedicated static API.  Investigate whether data structures
     * could share locks for efficiency.
     */


    /**
     * Get the exact player name, stored internally.
     * @param playerId
     */
    public String getPlayerName(final UUID playerId) {
        return playerDataManager.getPlayerName(playerId);
    }

    /**
     * Used by checks to register the history for external access.<br>
     * NOTE: This method is not really meant ot be used from outside NCP.
     *
     * @param type
     * @param histories
     * @deprecated Replaced by the history service API. Will be removed in
     *             version 2.0.
     *             <p>
     *             Migration: call
     *             {@code HistoryService#registerExecutionHistory(CheckType, Map)}
     *             instead of this utility method.
     *             </p>
     */
    public void registerExecutionHistory(CheckType type, Map<String, ExecutionHistory> histories) {
        playerDataManager.registerExecutionHistory(type, histories);
    }

    /**
     * Access method to the the execution history for check type for a player.
     *
     * @param type
     * @param playerName
     *            Exact case for player name.
     * @return null if not present.
     * @deprecated Superseded by the history service. Removal scheduled for
     *             version 2.0.
     *             <p>
     *             Migration: query the history service instead of calling this
     *             utility method.
     *             </p>
     */
    public ExecutionHistory getExecutionHistory(final CheckType type, final String playerName) {
        return playerDataManager.getExecutionHistory(type, playerName);
    }

    /**
     * Remove the execution history for a player for the given check type.
     *
     * @param type
     * @param playerName
     * @return {@code true} if any history was removed
     * @deprecated Moved to the history service. This will be removed in 2.0.
     *             <p>
     *             Migration: call
     *             {@code HistoryService#removeExecutionHistory(CheckType, String)}
     *             instead.
     *             </p>
     */
    public boolean removeExecutionHistory(final CheckType type, final String playerName) {
        return playerDataManager.removeExecutionHistory(type, playerName);
    }

    /**
     * Remove data and history of all players for the given check type and sub
     * checks. Also removes check related data from the world manager.
     * 
     * @param checkType
     */
    public void clearData(final CheckType checkType) {
        NCPAPIProvider.getNoCheatPlusAPI().getWorldDataManager().clearData(checkType);
        playerDataManager.clearData(checkType);
    }

    /**
     * Adjust to the system time having run backwards. This is much like
     * clearData(CheckType.ALL), with the exception of calling
     * ICanHandleTimeRunningBackwards.handleTimeRanBackwards for data instances
     * which implement this.
     */
    public void handleSystemTimeRanBackwards() {
        // This is currently called through the static API. Refactoring may
        // invoke the underlying manager directly from the core plugin.
        playerDataManager.handleSystemTimeRanBackwards();
    }

    /**
     * Restore the default debug flags within player data, as given in
     * corresponding configurations. This only yields the correct result, if the
     * the data uses the same configuration for initialization which is
     * registered under the same check type.
     * 
     * Further documentation will clarify how default debug flags are derived
     * from the configuration in a future refactoring.
     */
    public void restoreDefaultDebugFlags() {
        playerDataManager.restoreDefaultDebugFlags();
    }

    /**
     * Remove the player data for a given player and a given check type.
     * CheckType.ALL and null will be interpreted as removing all data.<br>
     * 
     * @param playerName
     *            Exact player name.
     * @param checkType
     *            Check type to remove data for, null is regarded as ALL.
     * @return If any data was present (not strict).
     */
    public boolean removeData(final String playerName, CheckType checkType) {
        return playerDataManager.removeData(playerName, checkType);
    }

    /**
     * Clear player related data, only for registered components (not execution
     * history, violation history, normal check data).<br>
     * That should at least go for chat engine data.
     * 
     * @param CheckType
     * @param PlayerName
     * @return If something was removed.
     */
    public boolean clearComponentData(final CheckType checkType, final String PlayerName) {
        // This method still relies on player names. Refactoring should switch
        // to UUID based lookups to avoid ambiguity.
        return playerDataManager.clearComponentData(checkType, PlayerName);
    }

    /**
     * This gets an online player by exact player name or lower-case player name
     * only [subject to change].
     * 
     * @param playerName
     * @return
     */
    public Player getPlayerExact(final String playerName) {
        return playerDataManager.getPlayerExact(playerName);
    }

    /**
     * Retrieve the UUID for a given input (name or UUID string of with or
     * without '-'). Might later also query a cache, if appropriate. Convenience
     * method for use with commands.
     * 
     * @param input
     * @return
     */
    public UUID getUUID(final String input) {
        return playerDataManager.getUUID(input);
    }

    /**
     * Get an online player by UUID.
     * 
     * @param id
     * @return
     */
    public Player getPlayer(final UUID id) {
        return playerDataManager.getPlayer(id);
    }

    /**
     * This gets the online player with the exact name, but transforms the input
     * to lower case.
     * 
     * @param playerName
     * @return
     */
    public Player getPlayer(final String playerName) {
        return playerDataManager.getPlayer(playerName);
    }

    /**
     * Get a PlayerData instance in any case - always creates a PlayerData
     * instance, if none is present. This method should be preferred, as it
     * hides details.
     * 
     * @param player
     * @return
     */
    public IPlayerData getPlayerData(final Player player) {
        return playerDataManager.getPlayerData(player, true);
    }

    /**
     * Get the player data, if present.
     * 
     * @param playerName
     * @return The PlayerData instance if present, null otherwise.
     */
    public IPlayerData getPlayerData(final String playerName) {
        return playerDataManager.getPlayerData(playerName);
    }

    /**
     * Get the player data, if present.
     * 
     * @param playerId
     * @return The PlayerData instance if present, null otherwise.
     */
    public IPlayerData getPlayerData(final UUID playerId) {
        return playerDataManager.getPlayerData(playerId);
    }
    boolean isFrequentPlayerTaskScheduled(final UUID playerId) {
        return playerDataManager.isFrequentPlayerTaskScheduled(playerId);
    }
    void registerFrequentPlayerTaskPrimaryThread(final UUID playerId) {
        playerDataManager.registerFrequentPlayerTaskPrimaryThread(playerId);
    }
    void registerFrequentPlayerTaskAsynchronous(final UUID playerId) {
        playerDataManager.registerFrequentPlayerTaskAsynchronous(playerId);
    }

    /**
     * 
     */
    public void clearAllExemptions() {
        playerDataManager.clearAllExemptions();
    }

    /**
     * Convenience method, allowing to skip fetching PlayerData.
     * 
     * @param player
     * @param registeredFor
     * @return
     */
    public <T> T getGenericInstance(final Player player, final Class<T> registeredFor) {
        return playerDataManager.getPlayerData(player).getGenericInstance(registeredFor);
    }
    <T> T getFromFactory(final Class<T> registeredFor, 
            final PlayerFactoryArgument arg) {
        return playerDataManager.getNewInstance(registeredFor, arg);
    }

    /**
     * Attempt to get or create an IPlayerData playerDataManager. Creation will only be
     * done, if the player name, UUID and world can be fetched.
     * 
     * @param player
     * @return null in case of failures.
    */
    public IPlayerData getPlayerDataSafe(final Player player) {
        if (player == null) {
            return null;
        }
        try {
            return getPlayerData(player);
        }
        catch (UnsupportedOperationException e) {
            // ignore - fall through to alternate lookup
        }
        try {
            return getPlayerData(player.getUniqueId());
        }
        catch (UnsupportedOperationException e) {
            // ignore - fall through to name-based lookup
        }
        try {
            return getPlayerData(player.getName());
        }
        catch (UnsupportedOperationException e) {
            StaticLog.logWarning("All player data retrieval methods failed for player: " + player.getName());
        }
        // Failure.
        return null;
    }

}
