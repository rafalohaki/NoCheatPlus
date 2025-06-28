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
package fr.neatmonster.nocheatplus.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.plugin.Plugin;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.build.BuildParameters;
import fr.neatmonster.nocheatplus.worlds.IWorldData;
import fr.neatmonster.nocheatplus.worlds.IWorldDataManager;
import fr.neatmonster.nocheatplus.worlds.WorldDataManager;

/**
 * Central location for everything that's described in the configuration file(s).<br>
 * The synchronized methods are to ensure that changing the configurations won't lead to trouble for the asynchronous checks.
 */
public class ConfigManager {

    private static final WorldConfigProvider<ConfigFile> worldConfigProvider = new WorldConfigProvider<ConfigFile>() {

        @Override
        public ConfigFile getDefaultConfig() {
            return ConfigManager.getConfigFile();
        }

        @Override
        public ConfigFile getConfig(String worldName) {
            return ConfigManager.getConfigFile(worldName);
        }

        @Override
        public Collection<ConfigFile> getAllConfigs() {
            // Avoid duplicates, which typically should exist.
            final Set<ConfigFile> res = new LinkedHashSet<ConfigFile>();
            final IWorldDataManager worldMan = NCPAPIProvider.getNoCheatPlusAPI().getWorldDataManager();
            final Iterator<Entry<String, IWorldData>> it = worldMan.getWorldDataIterator();
            while (it.hasNext()) {
                res.add(it.next().getValue().getRawConfiguration());
            }
            return res;
        }

    };

    private static boolean isInitialized = false;

    /**
     * Get the WorldConfigProvider in use.
     * @return
     */
    public static WorldConfigProvider<ConfigFile> getWorldConfigProvider() {
        return worldConfigProvider;
    }

    /**
     * Cleanup.
     */
    public static void cleanup() {
        isInitialized = false;
    }

    /**
     * Gets the configuration file. Can be called from any thread.
     * 
     * @return the configuration file
     */
    public static ConfigFile getConfigFile() {
        return NCPAPIProvider.getNoCheatPlusAPI().getWorldDataManager().getDefaultWorldData().getRawConfiguration();
    }

    /**
     * (Synchronized version).
     *
     * @return the configuration file
     * @deprecated getConfigFile() is thread-safe now. Use
     *             {@link #getConfigFile()} or the upcoming history service
     *             instead. Removal scheduled for 2.0.
     *             <p>
     *             Migration: switch all synchronous calls to the non-synchronized
     *             variant or retrieve configuration via the history service.
     *             </p>
     */
    @Deprecated
    public static synchronized ConfigFile getConfigFileSync() {
        return getConfigFile();
    }

    /**
     * Gets the configuration file. Can be called from any thread.
     * 
     * @param worldName
     *            the world name
     * @return the configuration file
     */
    public static ConfigFile getConfigFile(final String worldName) {
        return NCPAPIProvider.getNoCheatPlusAPI().getWorldDataManager().getDefaultWorldData().getRawConfiguration();
    }

    /**
     * (Synchronized version).
     *
     * @param worldName the world name
     * @return the configuration file
     * @deprecated getConfigFile() is thread-safe now. Use
     *             {@link #getConfigFile(String)} or the history service. This
     *             method will be removed in version 2.0.
     *             <p>
     *             Migration: update code to call the unsynchronized variant and
     *             consider caching results if needed.
     *             </p>
     */
    @Deprecated
    public static synchronized ConfigFile getConfigFileSync(final String worldName) {
        return getConfigFile(worldName);
    }

    /**
     * Initializes the configuration manager. Must be called in the main thread.
     * 
     * @param plugin
     *            the instance of NoCheatPlus
     */
    public static synchronized void init(final Plugin plugin, final WorldDataManager worldDataManager) {
        // (This can lead to minor problems with async checks during reloading.)
        final LinkedHashMap<String, ConfigFile> newWorldsMap = new LinkedHashMap<String, ConfigFile>();

        final File globalFile = new File(plugin.getDataFolder(), "config.yml");
        final ConfigFile defaultConfig = new DefaultConfig();
        final int maxBuildContained = defaultConfig.getMaxLastChangedBuildNumber();

        PathUtils.processPaths(globalFile, "global config", false);
        final ConfigFile globalConfig = loadGlobalConfig(globalFile, defaultConfig, maxBuildContained);
        newWorldsMap.put(null, globalConfig);

        final MemoryConfiguration worldDefaults = PathUtils.getWorldsDefaultConfig(globalConfig);
        newWorldsMap.putAll(loadWorldConfigs(plugin, worldDefaults, globalConfig));

        worldDataManager.applyConfiguration(newWorldsMap);
        isInitialized = true;
    }

    private static ConfigFile loadGlobalConfig(final File globalFile, final ConfigFile defaultConfig,
            final int maxBuildContained) {
        final ConfigFile globalConfig = new ConfigFile();
        globalConfig.setDefaults(defaultConfig);
        globalConfig.options().copyDefaults(true);
        if (globalFile.exists()) {
            try {
                globalConfig.load(globalFile);
                try {
                    if (globalConfig.getBoolean(ConfPaths.SAVEBACKCONFIG)) {
                        boolean overrideCreated = false;
                        if (!globalConfig.contains(ConfPaths.CONFIGVERSION_CREATED)) {
                            overrideCreated = true;
                        }
                        if (!overrideCreated && globalConfig.getInt(ConfPaths.CONFIGVERSION_CREATED, 0) >= 0
                                && ConfigManager.isConfigUpToDate(globalConfig, 0) == null) {
                            overrideCreated = true;
                        }
                        globalConfig.set(ConfPaths.CONFIGVERSION_SAVED, maxBuildContained);
                        if (overrideCreated) {
                            globalConfig.set(ConfPaths.CONFIGVERSION_CREATED, maxBuildContained);
                        }
                        globalConfig.save(globalFile);
                    }
                } catch (final Exception e) {
                    StaticLog.logSevere("Could not save back config.yml (see exception below).");
                    StaticLog.logSevere(e);
                }
            } catch (final Exception e) {
                StaticLog.logSevere(
                        "Could not load config.yml (see exception below). Continue with default settings...");
                StaticLog.logSevere(e);
            }
        } else {
            globalConfig.options().header("This configuration was auto-generated by NoCheatPlus.");
            globalConfig.options().copyHeader(true);
            try {
                globalConfig.set(ConfPaths.CONFIGVERSION_CREATED, maxBuildContained);
                globalConfig.set(ConfPaths.CONFIGVERSION_SAVED, maxBuildContained);
                globalConfig.save(globalFile);
            } catch (final Exception e) {
                StaticLog.logSevere(e);
            }
        }
        return globalConfig;
    }

    private static Map<String, ConfigFile> loadWorldConfigs(final Plugin plugin, final MemoryConfiguration worldDefaults,
            final ConfigFile globalConfig) {
        final Map<String, ConfigFile> result = new LinkedHashMap<String, ConfigFile>();
        final File dataFolder = plugin.getDataFolder();
        final Map<String, File> worldFiles = new HashMap<String, File>();
        if (dataFolder.isDirectory()) {
            for (final File file : dataFolder.listFiles()) {
                if (file.isFile()) {
                    final String fileName = file.getName();
                    if (fileName.matches(".+_config.yml$")) {
                        final String worldName = fileName.substring(0, fileName.length() - 11);
                        worldFiles.put(worldName, file);
                    }
                }
            }
        }

        for (final Entry<String, File> worldEntry : worldFiles.entrySet()) {
            final File worldFile = worldEntry.getValue();
            PathUtils.processPaths(worldFile, "world " + worldEntry.getKey(), true);
            final ConfigFile worldConfig = new ConfigFile();
            worldConfig.setDefaults(worldDefaults);
            worldConfig.options().copyDefaults(true);
            try {
                worldConfig.load(worldFile);
                result.put(worldEntry.getKey(), worldConfig);
                try {
                    if (worldConfig.getBoolean(ConfPaths.SAVEBACKCONFIG)) {
                        worldConfig.save(worldFile);
                    }
                } catch (final Exception e) {
                    StaticLog.logSevere("Couldn't save back world-specific configuration for "
                            + worldEntry.getKey() + " (see exception below).");
                    StaticLog.logSevere(e);
                }
            } catch (final Exception e) {
                StaticLog.logSevere("Couldn't load world-specific configuration for " + worldEntry.getKey()
                        + " (see exception below). Continue with global default settings...");
                StaticLog.logSevere(e);
            }
            worldConfig.setDefaults(globalConfig);
            worldConfig.options().copyDefaults(true);
        }
        return result;
    }

    @Deprecated
    public static String isConfigUpToDate(final ConfigFile globalConfig) {
        return isConfigUpToDate(globalConfig, -1);
    }

    /**
     * Retrieve a warning string containing information about changed default
     * configuration values.
     * 
     * @param globalConfig
     * @param maxLines
     *            Maximum number of configuration paths to include in the output
     *            - note that extra lines will always be included, for
     *            introduction and in the end some hints. A negative value means
     *            no limit.
     * @return null if everything is fine, a string with a message stating
     *         problems otherwise.
     */
    public static String isConfigUpToDate(final ConfigFile globalConfig, final int maxPaths) {
        final int buildCreated = getCreatedBuild(globalConfig);
        if (buildCreated < 0) {
            return null;
        }

        final ConfigFile defaultConfig = new DefaultConfig();
        final int maxBuildContained = defaultConfig.getMaxLastChangedBuildNumber();
        final String newerVersionMessage = checkForNewerPluginVersion(buildCreated, maxBuildContained);
        if (newerVersionMessage != null) {
            return newerVersionMessage;
        }

        final List<String> problems = collectChangedPaths(globalConfig, defaultConfig, buildCreated, maxPaths);
        if (!problems.isEmpty()) {
            return compileWarningMessage(problems, maxBuildContained, maxPaths);
        }
        return null;
    }

    private static int getCreatedBuild(final ConfigFile config) {
        final Object created = config.get(ConfPaths.CONFIGVERSION_CREATED);
        if (created instanceof Integer) {
            return ((Integer) created).intValue();
        }
        return -1;
    }

    private static String checkForNewerPluginVersion(final int buildCreated, final int maxBuildContained) {
        final int currentBuild = BuildParameters.buildNumber;
        if (currentBuild != Integer.MIN_VALUE && buildCreated > Math.max(maxBuildContained, currentBuild)) {
            return "Your configuration seems to be created by a newer plugin version.\n" +
                    "Some settings could have changed, you should regenerate it!";
        }
        return null;
    }

    private static List<String> collectChangedPaths(final ConfigFile globalConfig, final ConfigFile defaultConfig,
            final int buildCreated, final int maxPaths) {
        final Map<String, Integer> lastChangedBuildNumbers = defaultConfig.getLastChangedBuildNumbers();
        final List<String> problems = new LinkedList<String>();
        for (final Entry<String, Integer> entry : lastChangedBuildNumbers.entrySet()) {
            final int defaultBuild = entry.getValue();
            if (defaultBuild <= buildCreated) {
                continue;
            }
            final String path = entry.getKey();
            final Object defaultValue = defaultConfig.get(path);
            if (defaultValue instanceof ConfigurationSection) {
                problems.add(path + (maxPaths >= 0 ? "" :
                        (" - Changed with build " + defaultBuild + ", can not handle entire configuration sections yet. ")));
                continue;
            }
            final Object currentValue = globalConfig.get(path);
            if (currentValue == null || defaultValue == null) {
                continue;
            }
            if (!defaultValue.equals(currentValue)) {
                problems.add(path + (maxPaths >= 0 ? "" : (" - Changed with build " + defaultBuild + ".")));
            }
        }
        return problems;
    }

    private static String compileWarningMessage(final List<String> problems, final int maxBuildContained,
            final int maxPaths) {
        Collections.sort(problems);
        final List<String> outList;
        if (maxPaths >= 0 && problems.size() > maxPaths) {
            outList = new ArrayList<String>(problems.subList(0, maxPaths));
        } else {
            outList = problems;
        }
        outList.add(0, "The following configuration default values have changed:");
        if (maxPaths >= 0) {
            outList.add("-> " + problems.size() + " entries in total, check the log file(s) for a complete list.");
        } else {
            outList.add("(Remove/update individual values or set configversion.created to " + maxBuildContained +
                    " to ignore all, then reload the configuration with the 'ncp reload' command.)");
        }
        return StringUtil.join(outList, "\n");
    }

    /**
     * Informal test if the init method completed (no details are reflected).
     * @return
     */
    public static boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Set a property for all configurations. Might use with
     * DataManager.getInstance().clearConfigs if check-configurations might already be in use.
     *
     * @param path
     * @param value
     * @deprecated For activation flags use the WorldDataManager.getInstance(). This helper will
     *             be removed in version 2.0.
     *             <p>
     *             Migration: use
     *             {@link fr.neatmonster.nocheatplus.api.IWorldDataManager#setFlag(String, Object)}
     *             from the new history service to modify activation flags.
     *             </p>
     */
    @Deprecated
    public static synchronized void setForAllConfigs(String path, Object value){
        final IWorldDataManager worldMan = NCPAPIProvider.getNoCheatPlusAPI().getWorldDataManager();
        final Iterator<Entry<String, IWorldData>> it = worldMan.getWorldDataIterator();
        while (it.hasNext()) {
            it.next().getValue().getRawConfiguration().set(path, value);
        }
        worldMan.updateAllWorldData();
    }

    /**
     * Check if any config has a boolean set to true for the given path.
     * <hr/>
     * NOTE: Check activation flags need a query to the WorldDataManager, as
     * MAYBE typically means to activate, if the parent is active (checks <-
     * check group <- check (<- sub check)).
     * 
     * @param path
     * @return True if any config has a boolean set to true for the given path.
     */
    public static boolean isTrueForAnyConfig(String path) {
        final IWorldDataManager worldMan = NCPAPIProvider.getNoCheatPlusAPI().getWorldDataManager();
        final Iterator<Entry<String, IWorldData>> it = worldMan.getWorldDataIterator();
        while (it.hasNext()){
            if (it.next().getValue().getRawConfiguration().getBoolean(path, false)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if any config has the path set to true, or to default in case
     * decideOptimistically is set, or not set in case trueForNotSet is set.
     * <hr/>
     * NOTE: Check activation flags need a query to the WorldDataManager, as
     * MAYBE typically means to activate, if the parent is active (checks <-
     * check group <- check (<- sub check)).
     * 
     * @param path
     * @param decideOptimistically
     * @param trueForNotSet
     * @return
     */
    public static boolean isAlmostTrueForAnyConfig(String path, boolean decideOptimistically, boolean trueForNotSet) {
        final IWorldDataManager worldMan = NCPAPIProvider.getNoCheatPlusAPI().getWorldDataManager();
        final Iterator<Entry<String, IWorldData>> it = worldMan.getWorldDataIterator();
        while (it.hasNext()){
            if (it.next().getValue().getRawConfiguration().getAlmostBoolean(path, decideOptimistically, trueForNotSet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the maximally found number for the given config path. This does not
     * throw errors. It will return null, if nothing is found or all lookups
     * failed otherwise. <br>
     * Note: What happens with things like NaN is unspecified.
     * 
     * @param path
     *            Config path.
     * @return Value or null.
     */
    public static Double getMaxNumberForAllConfigs(final String path){
        Number max = null;
        final IWorldDataManager worldMan = NCPAPIProvider.getNoCheatPlusAPI().getWorldDataManager();
        final Iterator<Entry<String, IWorldData>> it = worldMan.getWorldDataIterator();
        while (it.hasNext()){
            final ConfigFile config = it.next().getValue().getRawConfiguration();
            try{
                final Object obj = config.get(path);
                if (obj instanceof Number){
                    final Number num = (Number) obj;
                    if (max == null || num.doubleValue() > max.doubleValue()){
                        max = num; 
                    }
                }
            }
            catch (Throwable t){
                // Holzhammer
            }
        }
        return max.doubleValue();
    }

    /**
     * Get the minimally found number for the given config path. This does not throw errors. It will return null, if nothing is found or all lookups failed otherwise.
     * <br>
     * Note: What happens with things like NaN is unspecified.
     * @param path Config path.
     * @return Value or null.
     */
    public static Double getMinNumberForAllConfigs(final String path){
        Number min = null;  
        final IWorldDataManager worldMan = NCPAPIProvider.getNoCheatPlusAPI().getWorldDataManager();
        final Iterator<Entry<String, IWorldData>> it = worldMan.getWorldDataIterator();
        while (it.hasNext()){
            final ConfigFile config = it.next().getValue().getRawConfiguration();
            try{
                final Object obj = config.get(path);
                if (obj instanceof Number){
                    final Number num = (Number) obj;
                    if (min == null || num.doubleValue() < min.doubleValue()){
                        min = num; 
                    }
                }
            }
            catch (Throwable t){
                // Holzhammer
            }
        }
        return min.doubleValue();
    }

    // Consider adding filter(Max|Min)NumberForAllConfigs(String path, String filerPath, boolean filterPreset)

}
