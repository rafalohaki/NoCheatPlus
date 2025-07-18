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
package fr.neatmonster.nocheatplus;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.jetbrains.annotations.NotNull;
import fr.neatmonster.nocheatplus.actions.ActionFactory;
import fr.neatmonster.nocheatplus.actions.ActionFactoryFactory;
import fr.neatmonster.nocheatplus.checks.blockbreak.BlockBreakListener;
import fr.neatmonster.nocheatplus.checks.blockinteract.BlockInteractListener;
import fr.neatmonster.nocheatplus.checks.blockplace.BlockPlaceListener;
import fr.neatmonster.nocheatplus.checks.chat.ChatConfig;
import fr.neatmonster.nocheatplus.checks.chat.ChatListener;
import fr.neatmonster.nocheatplus.checks.combined.CombinedListener;
import fr.neatmonster.nocheatplus.checks.fight.FightListener;
import fr.neatmonster.nocheatplus.checks.inventory.InventoryListener;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.MovingListener;
import fr.neatmonster.nocheatplus.checks.moving.location.tracking.LocationTrace.TraceEntryPool;
import fr.neatmonster.nocheatplus.checks.moving.util.AuxMoving;
import fr.neatmonster.nocheatplus.checks.net.NetConfig;
import fr.neatmonster.nocheatplus.checks.net.NetStatic;
import fr.neatmonster.nocheatplus.checks.workaround.WRPT;
import fr.neatmonster.nocheatplus.command.NoCheatPlusCommand;
import fr.neatmonster.nocheatplus.command.admin.VersionCommand;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.compat.Folia;
import fr.neatmonster.nocheatplus.compat.MCAccess;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeListener;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.IBlockChangeTracker;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.UnmodifiableBlockChangeTracker;
import fr.neatmonster.nocheatplus.compat.meta.BridgeCrossPluginLoader;
import fr.neatmonster.nocheatplus.compat.registry.AttributeAccessFactory;
import fr.neatmonster.nocheatplus.compat.registry.DefaultComponentFactory;
import fr.neatmonster.nocheatplus.compat.registry.EntityAccessFactory;
import fr.neatmonster.nocheatplus.compat.registry.MCAccessConfig;
import fr.neatmonster.nocheatplus.compat.registry.MCAccessFactory;
import fr.neatmonster.nocheatplus.compat.versions.Bugs;
import fr.neatmonster.nocheatplus.compat.versions.BukkitVersion;
import fr.neatmonster.nocheatplus.compat.versions.GenericVersion;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.components.registry.ComponentRegistry;
import fr.neatmonster.nocheatplus.components.registry.DefaultGenericInstanceRegistry;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.components.registry.feature.ConsistencyChecker;
import fr.neatmonster.nocheatplus.components.registry.feature.IDisableListener;
import fr.neatmonster.nocheatplus.components.registry.feature.IHoldSubComponents;
import fr.neatmonster.nocheatplus.components.registry.feature.INeedConfig;
import fr.neatmonster.nocheatplus.components.registry.feature.INotifyReload;
import fr.neatmonster.nocheatplus.components.registry.feature.IPostRegisterRunnable;
import fr.neatmonster.nocheatplus.components.registry.feature.IRegisterAsGenericInstance;
import fr.neatmonster.nocheatplus.components.registry.feature.IRemoveData;
import fr.neatmonster.nocheatplus.components.registry.feature.JoinLeaveListener;
import fr.neatmonster.nocheatplus.components.registry.feature.NCPListener;
import fr.neatmonster.nocheatplus.components.registry.feature.TickListener;
import fr.neatmonster.nocheatplus.components.registry.lockable.BasicLockable;
import fr.neatmonster.nocheatplus.components.registry.lockable.ILockable;
import fr.neatmonster.nocheatplus.components.registry.order.SetupOrder;
import fr.neatmonster.nocheatplus.components.registry.setup.RegistrationContext;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.config.ConfigManager;
import fr.neatmonster.nocheatplus.event.mini.EventRegistryBukkit;
import fr.neatmonster.nocheatplus.event.mini.EventRegistryBukkitView;
import fr.neatmonster.nocheatplus.event.mini.IEventRegistry;
import fr.neatmonster.nocheatplus.event.mini.MiniListener;
import fr.neatmonster.nocheatplus.hooks.ExemptionSettings;
import fr.neatmonster.nocheatplus.hooks.NCPExemptionManager;
import fr.neatmonster.nocheatplus.hooks.NCPHookManager;
import fr.neatmonster.nocheatplus.hooks.allviolations.AllViolationsConfig;
import fr.neatmonster.nocheatplus.hooks.allviolations.AllViolationsHook;
import fr.neatmonster.nocheatplus.hooks.violationfrequency.ViolationFrequencyConfig;
import fr.neatmonster.nocheatplus.hooks.violationfrequency.ViolationFrequencyHook;
import fr.neatmonster.nocheatplus.logging.BukkitLogManager;
import fr.neatmonster.nocheatplus.logging.LogManager;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.logging.StreamID;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.permissions.PermissionRegistry;
import fr.neatmonster.nocheatplus.permissions.PermissionUtil;
import fr.neatmonster.nocheatplus.permissions.PermissionUtil.CommandProtectionEntry;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.permissions.RegisteredPermission;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.players.IPlayerDataManager;
import fr.neatmonster.nocheatplus.players.PlayerDataManager;
import fr.neatmonster.nocheatplus.players.PlayerMessageSender;
import fr.neatmonster.nocheatplus.stats.Counters;
import fr.neatmonster.nocheatplus.utilities.ColorUtil;
import fr.neatmonster.nocheatplus.utilities.Misc;
import fr.neatmonster.nocheatplus.utilities.OnDemandTickListener;
import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.utilities.entity.PassengerUtil;
import fr.neatmonster.nocheatplus.utilities.location.LocationPool;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.worlds.IWorldData;
import fr.neatmonster.nocheatplus.worlds.IWorldDataManager;
import fr.neatmonster.nocheatplus.worlds.WorldDataManager;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;

/**
 * This is the main class of NoCheatPlus. The commands, events listeners and tasks are registered here.
 */
public class NoCheatPlus extends JavaPlugin implements NoCheatPlusAPI {

    /**
     * Required public no-argument constructor for modern plugin loading.
     */
    public NoCheatPlus() {
        super();
    }

    protected NoCheatPlus(JavaPluginLoader loader, PluginDescriptionFile desc, File dataFolder, File file) {
        super(loader, desc, dataFolder, file);
    }


    private static final Object lockableAPIsecret = new Object();
    private static final ILockable lockableAPI = new BasicLockable(lockableAPIsecret, 
            true, true, true);

    private static final String MSG_NOTIFY_OFF = ChatColor.RED + "NCP: " + ChatColor.WHITE + "Notifications are turned " + ChatColor.RED + "OFF" + ChatColor.WHITE + ".";

    // Static API

    /**
     * Convenience method.
     *
     * @deprecated Use
     *             {@link fr.neatmonster.nocheatplus.utilities.NCPAPIProvider#getNoCheatPlusAPI()}
     *             instead. Scheduled for removal in version 2.0.
     *             <p>
     *             Migration: replace calls to this method with
     *             {@code NCPAPIProvider.getNoCheatPlusAPI()}.
     *             </p>
     * @return the API instance
     */
    public static NoCheatPlusAPI getAPI() {
        return NCPAPIProvider.getNoCheatPlusAPI();
    }

    // Not static.

    /** Central logging access point. */
    private BukkitLogManager logManager = null; // Not final, but intended to stay, once set [change to init=syso?].

    /** Lower case player name to milliseconds point of time of release */
    private final Map<String, Long> denyLoginNames = Collections.synchronizedMap(new HashMap<String, Long>());

    /** Configuration problems (send to chat). */
    private String configProblemsChat = null;
    /** Configuration problems (send to log files). */
    private String configProblemsFile = null;

    // Permission registry is currently global; per-world entries might be introduced later.
    private final PermissionRegistry permissionRegistry = new PermissionRegistry(10000);
    /** Per world data. */
    private final WorldDataManager worldDataManager = new WorldDataManager();
    /** Player data / general data manager +- soon to be legacy static API. */
    private final PlayerDataManager pDataMan = new PlayerDataManager(worldDataManager, permissionRegistry);
    /** Service facade for accessing player data. */
    private final DataManager dataManager = new DataManager(pDataMan);

    private Object dataManTaskId = null;

    /**
     * Commands that were changed for protecting them against tab complete or
     * use.
     */
    final LinkedList<CommandProtectionEntry> changedCommands = new LinkedList<CommandProtectionEntry>();

    private final EventRegistryBukkit eventRegistry = new EventRegistryBukkit(this);

    /** The event listeners (Bukkit Listener, MiniListener). */
    private final List<Object> listeners       = new ArrayList<Object>();

    /** Components that need notification on reloading.
     * (Kept here, for if during runtime some might get added.)*/
    private final List<INotifyReload> notifyReload = new LinkedList<INotifyReload>();

    /** Components that check consistency. */
    private final List<ConsistencyChecker> consistencyCheckers = new ArrayList<ConsistencyChecker>();

    /** Index at which to continue. */
    private int consistencyCheckerIndex = 0;

    private Object consistencyCheckerTaskId = null;

    /** Listeners for players joining and leaving (monitor level) */
    private final List<JoinLeaveListener> joinLeaveListeners = new ArrayList<JoinLeaveListener>();

    /** Players for which {@link #onLeave(Player)} has run. */
    private final Set<UUID> processedLeave = new HashSet<UUID>();

    /** Sub component registries. */
    private final List<ComponentRegistry<?>> subRegistries = new ArrayList<ComponentRegistry<?>>();

    /** Queued sub component holders, emptied on the next tick usually. */
    private final List<IHoldSubComponents> subComponentholders = new ArrayList<IHoldSubComponents>(20);

    private final List<IDisableListener> disableListeners = new ArrayList<IDisableListener>();

    /** All registered components.  */
    private Set<Object> allComponents = new LinkedHashSet<Object>(50);

    /** Feature tags by keys, for features that might not be available. */
    private final LinkedHashMap<String, LinkedHashSet<String>> featureTags = new LinkedHashMap<String, LinkedHashSet<String>>();

    /** Hook for logging all violations. */
    private final AllViolationsHook allViolationsHook = new AllViolationsHook();
    
    private final ViolationFrequencyHook vlFrequencyHook = new ViolationFrequencyHook();

    /** Block change tracking (pistons, other). */
    private final BlockChangeTracker blockChangeTracker = new BlockChangeTracker();
    /** Listener for the BlockChangeTracker (register once, lazy). */
    private BlockChangeListener blockChangeListener = null;

    private final DefaultGenericInstanceRegistry genericInstanceRegistry = new DefaultGenericInstanceRegistry();

    /** Self-updating MCAccess reference. */
    private final IGenericInstanceHandle<MCAccess> mcAccess = genericInstanceRegistry.getGenericInstanceHandle(MCAccess.class);

    /** Tick listener that is only needed sometimes (component registration). */
    private final OnDemandTickListener onDemandTickListener = new OnDemandTickListener() {
        @Override
        public boolean delegateTick(final int tick, final long timeLast) {
            processQueuedSubComponentHolders();
            return false;
        }
    };

    /**
     * Run post-enable for the players who were already online during onEnable,
     * assuming that there would've been events between.
     * 
     * @author asofold
     *
     */
    private class PostEnableTask implements Runnable {

        private final Player[] onlinePlayers;

        private PostEnableTask(Player[] onlinePlayers) {
            this.onlinePlayers = onlinePlayers;
        }

        @Override
        public void run() {
            postEnable(onlinePlayers);
        }

    }

    @SetupOrder(priority = - 100)
    private class ReloadHook implements INotifyReload{
        @Override
        public void onReload() {
            // Only for reloading, not INeedConfig.
            processReload();
        }
    }

    private INotifyReload reloadHook = new ReloadHook();

    /** Access point for thread safe message queuing.
     *  Future versions may unify message handling here. */
    // Message sending might later be relayed asynchronously depending on settings.
    private final PlayerMessageSender playerMessageSender  = new PlayerMessageSender();

    private boolean clearExemptionsOnJoin = true;
    private boolean clearExemptionsOnLeave = true;

    private StreamID getRegistryStreamId() {
        // Choose log target based on configuration; prefer file output unless extended status is set.
        return ConfigManager.getConfigFile().getBoolean(ConfPaths.LOGGING_EXTENDED_STATUS)
                ? Streams.STATUS : Streams.DEFAULT_FILE;
    }

    /**
     * Remove expired entries.
     * 
     * @param playerName
     * @return If playerName is not null and the player is denied login after
     *         expiration checks, true is returned. Otherwise false is returned.
     */
    private boolean checkDenyLoginsNames(String playerName) {
        final long ts = System.currentTimeMillis();
        final List<String> rem = new LinkedList<String>();
        boolean res = false;
        synchronized (denyLoginNames) {
            for (final Entry<String, Long> entry : denyLoginNames.entrySet()) {
                if (entry.getValue().longValue() < ts) {
                    rem.add(entry.getKey());
                }
            }
            for (final String name : rem) {
                denyLoginNames.remove(name);
            }
            if (playerName != null) {
                res = isLoginDenied(playerName);
            }
        }
        return res;
    }

    @Override
    public boolean allowLogin(String playerName) {
        playerName = playerName.trim().toLowerCase();
        final Long time = denyLoginNames.remove(playerName);
        if (time == null) return false;
        return System.currentTimeMillis() <= time;
    }

    @Override
    public int allowLoginAll() {
        int denied = 0;
        final long now = System.currentTimeMillis();
        for (final Map.Entry<String, Long> entry : denyLoginNames.entrySet()) {
            final Long time = entry.getValue();
            if (time != null && time > now) {
                denied++;
            }
        }
        denyLoginNames.clear();
        return denied;
    }

    @Override
    public void denyLogin(String playerName, long duration) {
        final long ts = System.currentTimeMillis() + duration;
        playerName = playerName.trim().toLowerCase();
        synchronized (denyLoginNames) {
            final Long oldTs = denyLoginNames.get(playerName);
            if (oldTs != null && ts < oldTs.longValue()) {
                return;
            }
            denyLoginNames.put(playerName, ts);
            // Could persist these values if denial should survive restarts.
        }
        checkDenyLoginsNames(null);
    }

    @Override
    public boolean isLoginDenied(String playerName) {
        return isLoginDenied(playerName, System.currentTimeMillis());
    }

    @Override
    public String[] getLoginDeniedPlayers() {
        checkDenyLoginsNames(null);
        String[] kicked = new String[denyLoginNames.size()];
        denyLoginNames.keySet().toArray(kicked);
        return kicked;
    }

    @Override
    public boolean isLoginDenied(String playerName, long time) {
        playerName = playerName.trim().toLowerCase();
        final Long oldTs = denyLoginNames.get(playerName);
        if (oldTs == null) {
            return false; 
        }
        else {
            return time < oldTs.longValue();
        }
    }

    @Override
    public int sendAdminNotifyMessage(final String message) {
        // Forward to the subscription-based notification mechanism.
        return sendAdminNotifyMessageSubscriptions(message);
    }

    /**
     * Send notification to all CommandSenders found in permission subscriptions for the notify-permission as well as players that have stored permissions (those get re-checked here).
     * @param message
     * @return
     */
    public int sendAdminNotifyMessageSubscriptions(final String message) {
        // Permissibles are fetched directly; caching subscriptions might improve efficiency.
        final String lcPerm = Permissions.NOTIFY.getLowerCaseStringRepresentation();
        final Permission bukkitPerm = Permissions.NOTIFY.getBukkitPermission();
        final Set<Permissible> permissibles = Bukkit.getPluginManager().getPermissionSubscriptions(
                lcPerm);
        final Set<String> done = new HashSet<String>(permissibles.size());
        for (final Permissible permissible : permissibles) {
            if (permissible instanceof CommandSender) {
                final CommandSender sender = (CommandSender) permissible;
                if (sender instanceof Player) {
                    // Use the permission caching feature.
                    final Player player = (Player) sender;
                    if (player == null) {
                        continue;
                    }
                    final IPlayerData data = NCPAPIProvider.getNoCheatPlusAPI().getPlayerDataManager().getPlayerData(player);
                    if (data.getNotifyOff() || !data.hasPermission(Permissions.NOTIFY, player)) {
                        continue;
                    }
                }
                else if (!sender.hasPermission(bukkitPerm)) {
                    // Non-player permissibles are checked directly without caching.
                    continue;
                }
                // Finally send.
                sender.sendMessage(message);
                done.add(sender.getName());
            }
        }
        return done.size();
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.components.NoCheatPlusAPI#sendMessageDelayed(java.lang.String, java.lang.String)
     */
    @Override
    public void sendMessageOnTick(final String playerName, final String message) {
        // Message dispatch is delegated to the playerMessageSender.
        playerMessageSender.sendMessageThreadSafe(playerName, message);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Collection<ComponentRegistry<T>> getComponentRegistries(final Class<ComponentRegistry<T>> clazz) {
        final List<ComponentRegistry<T>> result = new LinkedList<ComponentRegistry<T>>();
        for (final ComponentRegistry<?> registry : subRegistries) {
            if (clazz.isAssignableFrom(registry.getClass())) {
                try{
                    result.add((ComponentRegistry<T>) registry);
                }
                catch(Throwable t) {
                    // Ignore.
                }
            }
        }
        return result;
    }

    /**
     * Convenience method to add components according to implemented interfaces,
     * like Listener, INotifyReload, INeedConfig.<br>
     * For the NoCheatPlus instance this must be done after the configuration
     * has been initialized. This will also register ComponentRegistry instances
     * if given.
     */
    @Override
    public boolean addComponent(final Object obj) {
        return addComponent(obj, true);
    }

    /**
     * Convenience method to add components according to implemented interfaces,
     * like Listener, INotifyReload, INeedConfig.<br>
     * For the NoCheatPlus instance this must be done after the configuration
     * has been initialized.
     * 
     * @param allowComponentRegistry
     *            Only registers ComponentRegistry instances if this is set to
     *            true.
     */
    @Override
    public boolean addComponent(final Object obj, final boolean allowComponentRegistry) {

        // Future versions may accept ComponentFactory implementations for reloadable components.
        if (obj == this) {
            throw new IllegalArgumentException("Can not register NoCheatPlus with itself.");
        }

        // Don't register twice.
        if (allComponents.contains(obj)) {
            // All added components are in here.
            return false;
        }

        boolean added = false;

        if (obj instanceof IRegisterAsGenericInstance) {
            registerGenericInstance(obj);
        }

        added = added || registerInterfaces(obj);
        added = added || addToSubRegistries(obj);

        if (allowComponentRegistry) {
            added = added || registerComponentRegistry(obj);
        }

        added = added || handleSubComponentHolder(obj);

        if (added) {
            allComponents.add(obj);
        }

        runPostRegisterHook(obj);

        return added;
    }

    /**
     * Register objects implementing framework interfaces.
     *
     * @param obj the component to register
     * @return true if the object was added to at least one registry
     */
    @SuppressWarnings("unchecked")
    private boolean registerInterfaces(final Object obj) {
        boolean added = false;

        if (obj instanceof Listener) {
            addListener((Listener) obj);
            added = true;
        }
        if (obj instanceof MiniListener<?>) {
            addMiniListener((MiniListener<? extends Event>) obj);
            added = true;
        }
        if (obj instanceof INotifyReload) {
            notifyReload.add((INotifyReload) obj);
            if (obj instanceof INeedConfig) {
                ((INeedConfig) obj).onReload();
            }
            added = true;
        }
        if (obj instanceof TickListener) {
            TickTask.addTickListener((TickListener) obj);
            added = true;
        }
        if (obj instanceof ConsistencyChecker) {
            consistencyCheckers.add((ConsistencyChecker) obj);
            added = true;
        }
        if (obj instanceof JoinLeaveListener) {
            joinLeaveListeners.add((JoinLeaveListener) obj);
            added = true;
        }
        if (obj instanceof IDisableListener) {
            disableListeners.add((IDisableListener) obj);
            added = true;
        }

        return added;
    }

    private boolean addToSubRegistries(final Object obj) {
        boolean added = false;
        for (final ComponentRegistry<?> registry : subRegistries) {
            final Object res;
            if (registry instanceof fr.neatmonster.nocheatplus.players.PlayerDataManager) {
                res = ((fr.neatmonster.nocheatplus.players.PlayerDataManager) registry)
                        .addComponentReflectively(obj);
            } else {
                res = ReflectionUtil.invokeGenericMethodOneArg(registry, "addComponent", obj);
            }
            if (res instanceof Boolean && ((Boolean) res).booleanValue()) {
                added = true;
            }
        }
        return added;
    }

    /**
     * Attempt to add a component to player data registries.
     *
     * @param registry the registry to add to
     * @param obj      the component
     * @return {@code true} if added
     */

    private boolean registerComponentRegistry(final Object obj) {
        if (obj instanceof ComponentRegistry<?>) {
            subRegistries.add((ComponentRegistry<?>) obj);
            return true;
        }
        return false;
    }

    private boolean handleSubComponentHolder(final Object obj) {
        if (obj instanceof IHoldSubComponents) {
            subComponentholders.add((IHoldSubComponents) obj);
            onDemandTickListener.register();
            return true;
        }
        return false;
    }

    private void runPostRegisterHook(final Object obj) {
        if (obj instanceof IPostRegisterRunnable) {
            ((IPostRegisterRunnable) obj).runPostRegister();
        }
    }

    /**
     * Interfaces checked for managed listeners: IHaveMethodOrder (method), ComponentWithName (tag)<br>
     * @param listener
     */
    private void addListener(final Listener listener) {
        // private: Use addComponent.
        eventRegistry.register(listener);
        listeners.add(listener);
    }

    private <E extends Event> void addMiniListener(final MiniListener<E> listener) {
        // private: Use addComponent.
        eventRegistry.register(listener);
        listeners.add(listener);
    }

    @Override
    public void removeComponent(final Object obj) {
        if (obj instanceof Listener) {
            listeners.remove(obj);
            eventRegistry.unregisterAttached(obj);
        }
        else if (obj instanceof MiniListener) {
            listeners.remove(obj);
            eventRegistry.unregisterAttached(obj); // Never know (e.g. attach all listeners to each other).
        }
        if (obj instanceof TickListener) {
            TickTask.removeTickListener((TickListener) obj);
        }
        if (obj instanceof INotifyReload) {
            notifyReload.remove((INotifyReload) obj);
        }
        if (obj instanceof ConsistencyChecker) {
            consistencyCheckers.remove((ConsistencyChecker) obj);
        }
        if (obj instanceof JoinLeaveListener) {
            joinLeaveListeners.remove((JoinLeaveListener) obj);
        }
        if (obj instanceof IDisableListener) {
            disableListeners.remove((IDisableListener) obj);
        }

        // Remove sub registries.
        if (obj instanceof ComponentRegistry<?>) {
            subRegistries.remove((ComponentRegistry<?>) obj);
        }
        // Remove from present registries, order prevents to remove from itself.
        for (final ComponentRegistry<?> registry : subRegistries) {
            if (registry instanceof fr.neatmonster.nocheatplus.players.PlayerDataManager) {
                ((fr.neatmonster.nocheatplus.players.PlayerDataManager) registry).removeComponentReflectively(obj);
            } else {
                ReflectionUtil.invokeGenericMethodOneArg(registry, "removeComponent", obj);
            }
        }

        allComponents.remove(obj);
    }

    /**
     * (Called on the plugin getting disabled.)
     * <hr>
     * Rough order of disabling:<br>
     * <ul>
     * <li>Prevent further registration. For now also disable listeners, though
     * this might get shifted still.</li>
     * <li><b>Call onDisable for IDisableListener instances, in reversed
     * order of registration.</b> This includes clearing all data.</li>
     * <li>Implement priority based sorting for IDisableListener instances using
     * the component registry. Configuration should allow overriding via
     * {@code components.disable-priority}. Contributors can track progress in
     * issue&nbsp;<a href="https://github.com/Updated-NoCheatPlus/NoCheatPlus/issues/388">#388</a>.</li>
     * <li>Random sequence of cleanup calls for other registries and logging
     * statistics.</li>
     * <li>Call removeComponent for all registered components.</li>
     * <li>Cleanup BlockProperties and clear most internal mappings for
     * components (needs a more clean registry approach).</li>
     * <li>(Command changes cleanup: currently disabled due to compatibility
     * issues, could cause other issues with dynamic plugin managers.)</li>
     * <li>Cleanup ConfigManager.</li>
     * <li>Shutdown LogManager.</li>
     * </ul>
     */
    @Override
    public void onDisable() {
        if(this.adventure != null) {
            this.adventure.close();
            this.adventure = null;
        }

        stopTasks();
        callDisableListeners();
        cleanupRegistries();
        shutdownLogging();
    }

    /** Cancel running tasks and reset TickTask. */
    private void stopTasks() {
        final boolean verbose = ConfigManager.getConfigFile().getBoolean(ConfPaths.LOGGING_EXTENDED_STATUS);

        if (verbose) {
            logManager.info(Streams.INIT, "Cleanup event registry (Bukkit)...");
        }
        eventRegistry.clear();

        if (Folia.isTaskScheduled(dataManTaskId)) {
            Folia.cancelTask(dataManTaskId);
            dataManTaskId = null;
        }

        if (verbose) {
            logManager.info(Streams.INIT, "Stop TickTask...");
        }
        TickTask.setLocked(true);
        TickTask.purge();
        TickTask.cancel();
        TickTask.removeAllTickListeners();

        if (Folia.isTaskScheduled(consistencyCheckerTaskId)) {
            Folia.cancelTask(consistencyCheckerTaskId);
            consistencyCheckerTaskId = null;
        }

        if (verbose) {
            logManager.info(Streams.INIT, "Stop all remaining tasks...");
        }
        Folia.cancelTasks(this);
    }

    /** Call all registered disable listeners. */
    private void callDisableListeners() {
        final boolean verbose = ConfigManager.getConfigFile().getBoolean(ConfPaths.LOGGING_EXTENDED_STATUS);
        if (verbose) {
            logManager.info(Streams.INIT, "onDisable calls (include DataManager cleanup)...");
        }
        final ArrayList<IDisableListener> disableList = new ArrayList<IDisableListener>(this.disableListeners);
        Collections.reverse(disableList);
        for (final IDisableListener dl : disableList) {
            try {
                dl.onDisable();
            } catch (Throwable t) {
                logManager.severe(Streams.INIT, "IDisableListener (" + dl.getClass().getName() + "): " + t.getClass().getSimpleName() + " / " + t.getMessage());
                logManager.severe(Streams.INIT, t);
            }
        }
    }

    /** Cleanup registries and internal mappings. */
    private void cleanupRegistries() {
        final boolean verbose = ConfigManager.getConfigFile().getBoolean(ConfPaths.LOGGING_EXTENDED_STATUS);

        if (verbose) {
            logManager.info(Streams.INIT, "Reset ExemptionManager...");
        }
        NCPExemptionManager.clear();

        vlFrequencyHook.unregister();
        allViolationsHook.unregister();
        NCPHookManager.removeAllHooks();

        final Counters counters = getGenericInstance(Counters.class);
        if (counters != null) {
            if (verbose) {
                logManager.info(Streams.INIT, counters.getMergedCountsString(true));
            } else {
                logManager.debug(Streams.TRACE_FILE, counters.getMergedCountsString(true));
            }
        }

        if (verbose) {
            logManager.info(Streams.INIT, "Unregister all registered components...");
        }
        final ArrayList<Object> components = new ArrayList<Object>(this.allComponents);
        for (int i = components.size() - 1; i >= 0; i--) {
            removeComponent(components.get(i));
        }

        if (verbose) {
            logManager.info(Streams.INIT, "Cleanup BlockProperties...");
        }
        BlockProperties.cleanup();

        if (verbose) {
            logManager.info(Streams.INIT, "Cleanup some mappings...");
        }
        disableListeners.clear();
        listeners.clear();
        notifyReload.clear();
        subRegistries.clear();
        subComponentholders.clear();
        genericInstanceRegistry.clear();
        featureTags.clear();
        if (blockChangeListener != null) {
            blockChangeListener.setEnabled(false);
            blockChangeListener = null;
        }
        blockChangeTracker.clear();
        changedCommands.clear();

        if (verbose) {
            logManager.info(Streams.INIT, "Cleanup ConfigManager...");
        }
        ConfigManager.cleanup();
    }

    /** Shutdown the logger and print final messages. */
    private void shutdownLogging() {
        final boolean verbose = ConfigManager.getConfigFile().getBoolean(ConfPaths.LOGGING_EXTENDED_STATUS);
        if (verbose) {
            logManager.info(Streams.INIT, "Shutdown LogManager...");
        }
        StaticLog.setUseLogManager(false);
        StaticLog.setStreamID(Streams.INIT);
        logManager.shutdown();

        if (verbose) {
            Bukkit.getLogger().info("[NoCheatPlus] All cleanup done.");
        }
        final PluginDescriptionFile pdfFile = getDescription();
        Bukkit.getLogger().info("[NoCheatPlus] Version " + pdfFile.getVersion() + " is disabled.");
    }

    /**
     * Does not undo 100%, but restores
     * <ul>
     * <li>old permission,</li>
     * <li>permission-message,</li>
     * <li>label (unlikely to be changed),</li>
     * <li>permission default.</li>
     * </ul>
     * 
     * @deprecated Leads to compatibility issues with NPC plugins such as
     *             Citizens 2, due to recalculation of permissions (specifically
     *             during disabling). This helper will be removed in version 2.0.
     *             <p>
     *             Migration: avoid calling this method and rely on the command
     *             state stored by the history service instead.
     *             </p>
     */
    public void undoCommandChanges() {
        if (!changedCommands.isEmpty()) {
            final Iterator<CommandProtectionEntry> it = changedCommands.descendingIterator();
            while (it.hasNext()) {
                it.next().restore();
            }
            changedCommands.clear();
        }
    }

    private void setupCommandProtection() {
        // Read lists and messages from config. Dynamic plugin managers may require this to run again on runtime enables.
        final ConfigFile config = ConfigManager.getConfigFile();
        // (Might add options to invert selection.)
        // "No permission".
        // The default permission message could be used by specifying "default" in the configuration.
        final List<String> noPerm = config.getStringList(ConfPaths.PROTECT_PLUGINS_HIDE_NOPERMISSION_CMDS);
        if (noPerm != null && !noPerm.isEmpty()) {
            final String noPermMsg = ColorUtil.replaceColors(ConfigManager.getConfigFile().getString(ConfPaths.PROTECT_PLUGINS_HIDE_NOPERMISSION_MSG));
            // Setup and add changes to history for undoing.
            changedCommands.addAll(PermissionUtil.protectCommands(this,
                    Permissions.FILTER_COMMAND.getLowerCaseStringRepresentation(), 
                    noPerm,  true, false, noPermMsg));
        }
        // "Unknown command", override the other option.
        final List<String> noCommand = config.getStringList(ConfPaths.PROTECT_PLUGINS_HIDE_NOCOMMAND_CMDS);
        if (noCommand != null && !noCommand.isEmpty()) {
            final String noCommandMsg = ColorUtil.replaceColors(ConfigManager.getConfigFile().getString(ConfPaths.PROTECT_PLUGINS_HIDE_NOCOMMAND_MSG));
            // Setup and add changes to history for undoing.
            changedCommands.addAll(PermissionUtil.protectCommands(this,
                    Permissions.FILTER_COMMAND.getLowerCaseStringRepresentation(), 
                    noCommand,  true, false, noCommandMsg));
        }
    }

    /* (non-Javadoc)
     * @see org.bukkit.plugin.java.JavaPlugin#onLoad()
     */
    @Override
    public void onLoad() {
        Bukkit.getLogger().info("[NoCheatPlus] onLoad: Early set up of static API, configuration, logging."); // Bukkit logger.
        setupBasics();
    }

    /**
     * Lazy initialization of basics (static API, configuration, logging).
     */
    private void setupBasics() {
        // Ensure permissions are registered early.
        for (RegisteredPermission rp : Permissions.getPermissions()) {
            if (permissionRegistry.getPermissionInfo(rp.getId()) == null) {
                permissionRegistry.addRegisteredPermission(rp);
            }
        }
        // API.
        updateNoCheatPlusAPI();
        // Initialize server version.
        if (ServerVersion.getMinecraftVersion() == GenericVersion.UNKNOWN_VERSION) {
            BukkitVersion.init();
        }
        // Pre config setup.
        if (getGenericInstance(ActionFactoryFactory.class) == null) {
            setActionFactoryFactory(null); // Set to default.
        }
        // Configuration.
        if (!ConfigManager.isInitialized()) {
            ConfigManager.init(this, worldDataManager);
            // Basic setup for exemption (uses CheckType). This is redundant, but should not hurt.
            NCPExemptionManager.setExemptionSettings(new ExemptionSettings(ConfigManager.getConfigFile()));
        }
        // Logging.
        if (logManager == null || logManager.getStreamID(Streams.STATUS.name) != Streams.STATUS) {
            logManager = new BukkitLogManager(this);
            StaticLog.setStreamID(Streams.INIT);
            StaticLog.setUseLogManager(true);
            logManager.info(Streams.INIT, "Logging system initialized.");
            logManager.info(Streams.INIT, "Detected Minecraft version: " + ServerVersion.getMinecraftVersion());
            genericInstanceRegistry.setLogger(
                    logManager, NoCheatPlus.this::getRegistryStreamId, "[GenericInstanceRegistry] ");
        }
    }

    private void updateNoCheatPlusAPI() {
        if (NCPAPIProvider.getNoCheatPlusAPI() == null) {
            lockableAPI.unlock(lockableAPIsecret);
            NCPAPIProvider.setNoCheatPlusAPI(this, lockableAPI);
            lockableAPI.lock(lockableAPIsecret);
        }
    }

    /* (non-Javadoc)
     * @see org.bukkit.plugin.java.JavaPlugin#onEnable()
     */
    @Override
    public void onEnable() {

        // Create BukkitAudiences
        this.adventure = BukkitAudiences.create(this);

        // Reset TickTask (just in case).
        TickTask.setLocked(true);
        TickTask.purge();
        TickTask.cancel();
        TickTask.reset();

        // Allow entries to TickTask.
        TickTask.setLocked(false);

        // Re-check basic setup (if onLoad gets skipped by some custom thing).
        setupBasics();
        if (Bugs.shouldEnforceLocation()) {
            addFeatureTags("defaults", Collections.singletonList("enforceLocation"));
        }
        if (Bugs.shouldPvpKnockBackVelocity()) {
            addFeatureTags("defaults", Collections.singletonList("pvpKnockBackVelocity"));
        }
        if (pDataMan.storesPlayerInstances()) {
            addFeatureTags("defaults", Collections.singletonList("storePlayers"));
        }

        // Start logger task(s).
        logManager.startTasks();

        final ConfigFile config = ConfigManager.getConfigFile();

        // Set some instance members.
        setInstanceMembers(config);

        // Register some generic stuff.
        // (Deny change some.)
        registerGenericInstance(new Counters());
        genericInstanceRegistry.denyChangeExistingRegistration(Counters.class);
        registerGenericInstance(new WRPT());
        genericInstanceRegistry.denyChangeExistingRegistration(WRPT.class);
        registerGenericInstance(new TraceEntryPool(1000)); // Random number.
        genericInstanceRegistry.denyChangeExistingRegistration(TraceEntryPool.class);
        registerGenericInstance(new LocationPool());
        genericInstanceRegistry.denyChangeExistingRegistration(LocationPool.class);
        registerGenericInstance(new PassengerUtil());
        genericInstanceRegistry.denyChangeExistingRegistration(PassengerUtil.class);
        // (Allow override others.)
        BridgeCrossPluginLoader.load().ifPresent(this::addComponent);

        // World data init (basic).
        for (final World world : Bukkit.getWorlds()) {
            onWorldPresent(world);
        }

        // Initialize MCAccess.
        initMCAccess(config);

        // Initialize BlockProperties.
        initBlockProperties(config);

        // Initialize data manager.
        worldDataManager.onEnable();
        pDataMan.onEnable();

        // Register components. 

        // Add the "low level" system components first.
        for (final Object obj : new Object[]{
                getCoreListener(),
                reloadHook,
                pDataMan,
                new AuxMoving(),
        }) {
            addComponent(obj);
            // Register sub-components (allow later added to use registries, if any).
            processQueuedSubComponentHolders();
        }
        updateBlockChangeTracker(config);

        // Register "higher level" components (check listeners).
        for (final Object obj : new Object[]{
                new BlockInteractListener(),
                new BlockBreakListener(),
                new BlockPlaceListener(),
                new ChatListener(),
                new CombinedListener(),
                // Do mind registration order: Combined must come before Fight.
                new FightListener(),
                new InventoryListener(),
                new MovingListener(dataManager),
        }) {
            addComponent(obj);
            // Register sub-components (allow later added to use registries, if any).
            processQueuedSubComponentHolders();
        }
        // Ensure net types are registered.
        NetStatic.registerTypes();

        // Register optional default components.
        final DefaultComponentFactory dcf = new DefaultComponentFactory();
        for (final Object obj : dcf.getAvailableComponentsOnEnable(this)) {
            addComponent(obj);
            // Register sub-components to enable registries for optional components.
            processQueuedSubComponentHolders();
        }

        // Register the commands handler.
        final PluginCommand command = getCommand("nocheatplus");
        if (command != null) {
            final NoCheatPlusCommand commandHandler = new NoCheatPlusCommand(this, notifyReload);
            command.setExecutor(commandHandler);
        } else {
            getLogger().severe("Command 'nocheatplus' not registered; cannot set executor.");
        }
        // (CommandHandler is TabExecutor.)

        // Tell the permission registry which permissions should get updated. This might be restricted by check settings later.
        permissionRegistry.preferKeepUpdated(NetConfig.getPreferKeepUpdatedPermissions());
        permissionRegistry.preferKeepUpdated(ChatConfig.getPreferKeepUpdatedPermissions());
        permissionRegistry.arrangePreferKeepUpdated();

        ////////////////////////////////
        // Tasks, post-rumble-logging
        ////////////////////////////////

        // Set up the tick task.
        TickTask.start(this);

        // dataMan expiration checking.
        this.dataManTaskId = Folia.runSyncRepeatingTask(this, (arg) -> pDataMan.checkExpiration(), 1207, 1207);

        // Ensure dataMan is first on disableListeners so it cleans up after others.
        Misc.putFirst(pDataMan, disableListeners);
        Misc.putFirst(pDataMan, notifyReload);
        Misc.putFirst(worldDataManager, notifyReload);
        // Put ReloadListener first, because Checks could also listen to it.
        Misc.putFirst(reloadHook, notifyReload);

        // Set up consistency checking.
        scheduleConsistencyCheckers();

        // Setup allViolationsHook
        allViolationsHook.setConfig(new AllViolationsConfig(config));
        vlFrequencyHook.setConfig(new ViolationFrequencyConfig(config));

        // Log other notes.
        logOtherNotes(config);

        // Is the version the configuration was created with consistent with the current one?
        if (configProblemsFile != null && config.getBoolean(ConfPaths.CONFIGVERSION_NOTIFY)) {
            logManager.warning(Streams.INIT, "" + configProblemsFile);
        }

        // Care for already online players.
        final Player[] onlinePlayers = BridgeMisc.getOnlinePlayers();
        // Re-initialize player related data after enable.
        Folia.runSyncTask(this, (arg) -> new PostEnableTask(onlinePlayers).run());

        // Mid-term cleanup (seconds range).
        Folia.runSyncRepeatingTask(this, (arg) -> midTermCleanup(), 83, 83);

        // Set StaticLog to more efficient output.
        StaticLog.setStreamID(Streams.STATUS);
        // Tell the server administrator that we finished loading NoCheatPlus now.
        logManager.info(Streams.INIT, "Version " + getDescription().getVersion() + " is enabled.");
    }

    /**
     * Initialize the BukkitAudiences
     */
    private BukkitAudiences adventure;

    @Override
    public @NotNull BukkitAudiences adventure() {
        if(this.adventure == null) {
            throw new IllegalStateException("Tried to access Adventure when the plugin was disabled!");
        }
        return this.adventure;
    }

    /**
     * Log other notes once on enabling.
     * 
     * @param config
     */
    private void logOtherNotes(ConfigFile config) {
        if (ServerVersion.compareMinecraftVersion("1.9") >= 0) {
            logManager.info(Streams.INIT, "Force disable FastHeal, FastConsume, PacketFrequency and InstantBow on Minecraft 1.9 and later.");
        }
    }

    /**
     * Actions to be done after enable of  all plugins. This aims at reloading mainly.
     */
    private void postEnable(final Player[] onlinePlayers) {
        logManager.info(Streams.INIT, "Post-enable running...");
        // Update permission registry internals for permissions preferred to be updated.
        // (By now checks should have noted what they want.)
        permissionRegistry.arrangePreferKeepUpdated();

        final ConfigFile config = ConfigManager.getConfigFile();
        try {
            // Command protection feature.
            if (config.getBoolean(ConfPaths.PROTECT_PLUGINS_HIDE_ACTIVE)) {
                setupCommandProtection();
            }
        } catch (Throwable t) {
            logManager.severe(Streams.INIT, "Failed to apply command protection: " + t.getClass().getSimpleName());
            logManager.severe(Streams.INIT, t);
        }
        // Update some moving data for players that logged in while the plugin was disabled.
        for (final Player player : onlinePlayers) {
            if (player == null) {
                continue;
            }
            final IPlayerData pData = NCPAPIProvider.getNoCheatPlusAPI().getPlayerDataManager().getPlayerData(player);
            if (player.isSleeping()) {
                pData.getGenericInstance(MovingData.class).wasInBed = true;
            }
        }
        if (onlinePlayers.length > 0) {
            logManager.info(Streams.INIT, "Updated data for " + onlinePlayers.length + " players (post-enable).");
        }
        // Finished.
        logManager.info(Streams.INIT, "Post-enable finished.");
        // Log version to file (queued).
        logManager.info(Streams.DEFAULT_FILE, VersionCommand.getFormattedVersionInfo());
    }

    /**
     * Empties and registers the subComponentHolders list.
     */
    private void processQueuedSubComponentHolders() {
        if (subComponentholders.isEmpty()) return;
        final List<IHoldSubComponents> copied = new ArrayList<IHoldSubComponents>(subComponentholders);
        subComponentholders.clear();
        for (final IHoldSubComponents holder : copied) {
            for (final Object component : holder.getSubComponents()) {
                addComponent(component);
            }
        }
    }

    /**
     * All action done on reload.
     */
    private void processReload() {
        final ConfigFile config = ConfigManager.getConfigFile();
        setInstanceMembers(config);
        // Process components provided by registered factories.
        // Set up MCAccess.
        initMCAccess(config);
        // Initialize BlockProperties
        initBlockProperties(config);
        // Reset Command protection.
        undoCommandChanges();
        if (config.getBoolean(ConfPaths.PROTECT_PLUGINS_HIDE_ACTIVE)) {
            setupCommandProtection();
        }
        // (Re-) schedule consistency checking.
        scheduleConsistencyCheckers();
        // Re-setup allViolationsHook.
        allViolationsHook.setConfig(new AllViolationsConfig(config));
        vlFrequencyHook.setConfig(new ViolationFrequencyConfig(config));
        // Set block change tracker.
        updateBlockChangeTracker(config);
    }

    /**
     * Set instance members based on the given configuration. This is meant to
     * work after reloading the configuration too.
     * 
     * @param config
     */
    private void setInstanceMembers(final ConfigFile config) {
        configProblemsChat = ConfigManager.isConfigUpToDate(config, config.getInt(ConfPaths.CONFIGVERSION_NOTIFYMAXPATHS));
        configProblemsFile = configProblemsChat == null ? null : ConfigManager.isConfigUpToDate(config, -1);
        clearExemptionsOnJoin = config.getBoolean(ConfPaths.COMPATIBILITY_EXEMPTIONS_REMOVE_JOIN);
        clearExemptionsOnLeave = config.getBoolean(ConfPaths.COMPATIBILITY_EXEMPTIONS_REMOVE_LEAVE);
        NCPExemptionManager.setExemptionSettings(new ExemptionSettings(config));
    }

    private void updateBlockChangeTracker(final ConfigFile config) {
        // Activation / listener.
        if (config.getBoolean(ConfPaths.COMPATIBILITY_BLOCKS_CHANGETRACKER_ACTIVE) 
                && config.getBoolean(ConfPaths.COMPATIBILITY_BLOCKS_CHANGETRACKER_PISTONS)) {
            if (blockChangeListener == null) {
                blockChangeListener = new BlockChangeListener(blockChangeTracker);
                blockChangeListener.register();
            }
            blockChangeListener.setEnabled(true);
        }
        else if (blockChangeListener != null) {
            blockChangeListener.setEnabled(false);
            blockChangeTracker.clear();
        }
        // Configuration.
        blockChangeTracker.setExpirationAgeTicks(config.getInt(ConfPaths.COMPATIBILITY_BLOCKS_CHANGETRACKER_MAXAGETICKS));
        blockChangeTracker.setWorldNodeSkipSize(config.getInt(ConfPaths.COMPATIBILITY_BLOCKS_CHANGETRACKER_PERWORLD_MAXENTRIES));
        blockChangeTracker.updateBlockCacheHandle();
    }

    @Override
    public LogManager getLogManager() {
        return logManager;
    }

    /**
     * (Re-) Setup MCAccess and other access providers from the internal
     * factories. Only call from the primary thread.
     * 
     * @param config
     */
    private MCAccess initMCAccess(final ConfigFile config) {
        // Reset MCAccess. Automatic registration with reload hooks may be added in the future.
        // An event could be fired when MCAccess is changed.
        final MCAccessConfig mcaC = new MCAccessConfig(config);
        final MCAccess mcAccess = new MCAccessFactory().getMCAccess(mcaC);
        /*
         * NOTE: previously registration was done last, to allow fetching the
         * previous registration. That has been discarded, due to the number of
         * related objects registering anyway.
         */
        // Set in registry.
        genericInstanceRegistry.registerGenericInstance(MCAccess.class, mcAccess);
        // Register a BlockCache instance for temporary use (enables using handle thing).
        genericInstanceRegistry.registerGenericInstance(BlockCache.class, mcAccess.getBlockCache());
        // Spin-off.
        new AttributeAccessFactory().setupAttributeAccess(mcAccess, mcaC);
        new EntityAccessFactory().setupEntityAccess(mcAccess, mcaC);

        // Additional callbacks could be triggered here to inform dependent components.

        // Log.
        logManager.info(Streams.INIT, "McAccess set to: " + mcAccess.getMCVersion() + " / " + mcAccess.getServerVersionTag());

        return mcAccess;
    }

    /**
     * Initialize BlockProperties, including config. Needs initMCAccess to be
     * called before.
     */
    private void initBlockProperties(ConfigFile config) {
        // Set up BlockProperties.
        BlockProperties.init(mcAccess, ConfigManager.getWorldConfigProvider());
        BlockProperties.applyConfig(config, ConfPaths.COMPATIBILITY_BLOCKS);
        // Schedule dumping the blocks properties (to let other plugins override).
        Folia.runSyncTask(this, (arg) -> {
            // Debug information about unknown blocks.
            // (Probably removed later.)
            ConfigFile cf = ConfigManager.getConfigFile();
            BlockProperties.dumpBlocks(cf.getBoolean(ConfPaths.BLOCKBREAK_DEBUG, cf.getBoolean(ConfPaths.CHECKS_DEBUG, false)));
        });
    }

    /**
     * Quick solution to hide the listener methods, expect refactoring.
     * @return
     */
    private Listener getCoreListener() {
        return new CoreListener();
    }

    private class CoreListener extends NCPListener {

        @EventHandler(priority = EventPriority.NORMAL)
        public void onPlayerLogin(final PlayerLoginEvent event) {
            // (NORMAL to have chat checks come after this.)
            if (event.getResult() != Result.ALLOWED) {
                return;
            }
            final Player player = event.getPlayer();
            // Check if login is denied (plus expiration check).
            // Could switch to using player UUIDs and handle AsyncPlayerPreLogin.
            if (checkDenyLoginsNames(player.getName())) {
                if (player != null && NCPAPIProvider.getNoCheatPlusAPI().getPlayerDataManager()
                        .getPlayerData(player).hasPermission(Permissions.BYPASS_DENY_LOGIN, player)) {
                    return;
                }
                // An alternative would be to use the built in temporary ban feature and include the remaining duration.
                event.setResult(Result.KICK_OTHER);
                // Kick message is configurable independently from the checks.
                event.setKickMessage(ColorUtil.replaceColors(ConfigManager.getConfigFile(player.getWorld().getName()).getString(ConfPaths.STRINGS + ".msgtempdenylogin")));
            }
        }

        @EventHandler(priority = EventPriority.LOWEST) // Do update comment in NoCheatPlusAPI with changing.
        public void onPlayerJoinLowest(final PlayerJoinEvent event) {
            if (clearExemptionsOnJoin) {
                final Player player = event.getPlayer();
                NCPExemptionManager.unexempt(player);
            }
            clearLeaveProcessed(event.getPlayer());
        }

        @EventHandler(priority = EventPriority.LOW)
        public void onPlayerJoinLow(final PlayerJoinEvent event) {
            // LOWEST is for DataMan and CombinedListener.
            onJoinLow(event.getPlayer());
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onPlayerKick(final PlayerKickEvent event) {
            final Player player = event.getPlayer();
            markLeaveProcessed(player);
            onLeave(player);
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPlayerQuit(final PlayerQuitEvent event) {
            final Player player = event.getPlayer();
            if (isLeaveProcessed(player)) {
                clearLeaveProcessed(player);
            } else {
                markLeaveProcessed(player);
                onLeave(player);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onWorldLoad(final WorldLoadEvent event) {
            NoCheatPlus.this.onWorldLoad(event);
        }
    }

    private void onWorldLoad(final WorldLoadEvent event) {
        onWorldPresent(event.getWorld());
    }

    /**
     * Called for all worlds we encounter (onEnable, WorldLoadEvent).
     * 
     * @param world
     */
    private void onWorldPresent(final World world) {
        worldDataManager.updateWorldIdentifier(world);
    }

    private void onJoinLow(final Player player) {
        if (player == null) {
            return;
        }
        final String playerName = player.getName();
        final IPlayerData data = NCPAPIProvider.getNoCheatPlusAPI().getPlayerDataManager().getPlayerData(player);
        if (data.hasPermission(Permissions.NOTIFY, player)) { // Updates the cache.
            // Login notifications...
            //            // Update available.
            // Inconsistent config version.
            if (configProblemsChat != null && ConfigManager.getConfigFile().getBoolean(ConfPaths.CONFIGVERSION_NOTIFY)) {
                // Could use custom prefix from logging, however ncp should be mentioned then.
                sendMessageOnTick(playerName, ChatColor.RED + "NCP: " + ChatColor.WHITE + configProblemsChat);
            }
            // Message if notify is turned off.
            if (data.getNotifyOff()) {
                sendMessageOnTick(playerName, MSG_NOTIFY_OFF);
            }
        }
        // JoinLeaveListenerS: Do update comment in NoCheatPlusAPI with changing event priority.
        for (final JoinLeaveListener jlListener : joinLeaveListeners) {
            try{
                jlListener.playerJoins(player);
            }
            catch(Throwable t) {
                logManager.severe(Streams.INIT, "JoinLeaveListener(" + jlListener.getClass().getName() + ") generated an exception (join): " + t.getClass().getSimpleName());
                logManager.severe(Streams.INIT, t);
            }
        }
    }

    private void onLeave(final Player player) {
        for (final JoinLeaveListener jlListener : joinLeaveListeners) {
            try{
                jlListener.playerLeaves(player);
            }
            catch(Throwable t) {
                logManager.severe(Streams.INIT, "JoinLeaveListener(" + jlListener.getClass().getName() + ") generated an exception (leave): " + t.getClass().getSimpleName());
                logManager.severe(Streams.INIT, t);
            }
        }
        if (clearExemptionsOnLeave) {
            NCPExemptionManager.unexempt(player);
        }
    }

    private void markLeaveProcessed(Player player) {
        if (player != null) {
            processedLeave.add(player.getUniqueId());
        }
    }

    private boolean isLeaveProcessed(Player player) {
        if (player == null) {
            return false;
        }
        return processedLeave.contains(player.getUniqueId());
    }

    private void clearLeaveProcessed(Player player) {
        if (player != null) {
            processedLeave.remove(player.getUniqueId());
        }
    }

    private void scheduleConsistencyCheckers() {
        if (Folia.isTaskScheduled(consistencyCheckerTaskId)) {
            Folia.cancelTask(consistencyCheckerTaskId);
        }
        ConfigFile config = ConfigManager.getConfigFile();
        if (!config.getBoolean(ConfPaths.DATA_CONSISTENCYCHECKS_CHECK, true)) {
            return;
        }
        // Schedule task in seconds.
        final long delay = 20L * config.getInt(ConfPaths.DATA_CONSISTENCYCHECKS_INTERVAL, 1, 3600, 10);
        consistencyCheckerTaskId = Folia.runSyncRepeatingTask(this, (arg) -> runConsistencyChecks(), delay, delay);
    }

    /**
     * Several seconds, repeating.
     */
    private void midTermCleanup() {
        if (blockChangeListener != null && blockChangeListener.isEnabled()) {
            blockChangeTracker.checkExpiration(TickTask.getTick());
        }
    }

    /**
     * Run consistency checks for at most the configured duration. If not finished, a task will be scheduled to continue.
     */
    private void runConsistencyChecks() {
        final long tStart = System.currentTimeMillis();
        final ConfigFile config = ConfigManager.getConfigFile();
        if (!config.getBoolean(ConfPaths.DATA_CONSISTENCYCHECKS_CHECK) || consistencyCheckers.isEmpty()) {
            consistencyCheckerIndex = 0;
            return;
        }
        final long tEnd = tStart + config.getLong(ConfPaths.DATA_CONSISTENCYCHECKS_MAXTIME, 1, 50, 2);
        if (consistencyCheckerIndex >= consistencyCheckers.size()) consistencyCheckerIndex = 0;
        final Player[] onlinePlayers = BridgeMisc.getOnlinePlayers();
        // Loop
        while (consistencyCheckerIndex < consistencyCheckers.size()) {
            final ConsistencyChecker checker = consistencyCheckers.get(consistencyCheckerIndex);
            try{
                checker.checkConsistency(onlinePlayers);
            }
            catch (Throwable t) {
                logManager.severe(Streams.INIT, "ConsistencyChecker(" + checker.getClass().getName() + ") encountered an exception:");
                logManager.severe(Streams.INIT, t);
            }
            consistencyCheckerIndex ++; // Do not remove :).
            final long now = System.currentTimeMillis();
            if (now < tStart || now >= tEnd) {
                break;
            }
        }
        // (The index might be bigger than size by now.)

        // If not finished, schedule further checks.
        if (consistencyCheckerIndex < consistencyCheckers.size()) {
            Folia.runSyncTask(this, (arg) -> runConsistencyChecks());
            if (config.getBoolean(ConfPaths.LOGGING_EXTENDED_STATUS)) {
                logManager.info(Streams.STATUS, "Interrupted consistency checking until next tick.");
            }
        }
    }

    @Override
    public <T> T getGenericInstance(Class<T> registeredFor) {
        return genericInstanceRegistry.getGenericInstance(registeredFor);
    }

    @Override
    public <T> T registerGenericInstance(T instance) {
        return genericInstanceRegistry.registerGenericInstance(instance);
    }

    @Override
    public <T, TI extends T> T registerGenericInstance(Class<T> registerFor, TI instance) {
        return genericInstanceRegistry.registerGenericInstance(registerFor, instance);
    }

    @Override
    public <T> T unregisterGenericInstance(Class<T> registeredFor) {
        return genericInstanceRegistry.unregisterGenericInstance(registeredFor);
    }

    @Override
    public <T> IGenericInstanceHandle<T> getGenericInstanceHandle(Class<T> registeredFor) {
        return genericInstanceRegistry.getGenericInstanceHandle(registeredFor);
    }

    @Override
    public void addFeatureTags(String key, Collection<String> featureTags) {
        LinkedHashSet<String> present = this.featureTags.computeIfAbsent(key, k -> new LinkedHashSet<>());
        present.addAll(featureTags);
    }

    @Override
    public void setFeatureTags(String key, Collection<String> featureTags) {
        LinkedHashSet<String> present = new LinkedHashSet<String>();
        this.featureTags.put(key, present);
        present.addAll(featureTags);
    }

    @Override
    public boolean hasFeatureTag(final String key, final String feature) {
        final Collection<String>  features = this.featureTags.get(key);
        return features == null ? false : features.contains(feature);
    }

    @Override
    public Map<String, Set<String>> getAllFeatureTags() {
        final LinkedHashMap<String, Set<String>> allTags = new LinkedHashMap<String, Set<String>>();
        for (final Entry<String, LinkedHashSet<String>> entry : this.featureTags.entrySet()) {
            allTags.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        return Collections.unmodifiableMap(allTags);
    }

    @Override
    public IBlockChangeTracker getBlockChangeTracker() {
        return new UnmodifiableBlockChangeTracker(blockChangeTracker);
    }

    @Override
    public IEventRegistry getEventRegistry() {
        return new EventRegistryBukkitView(eventRegistry);
    }

    @Override
    public PermissionRegistry getPermissionRegistry() {
        return permissionRegistry;
    }

    @Override
    public IWorldDataManager getWorldDataManager() {
        return worldDataManager;
    }

    @Override
    public IPlayerDataManager getPlayerDataManager() {
        return pDataMan;
    }

    /**
     * Delegates the registration of plugin components to the supplied
     * {@link RegistrationContext}. This should be invoked during plugin
     * initialization once all managers have been set up.
     *
     * @param context the context responsible for registering this plugin
     */
    @Override
    public void register(RegistrationContext context) {
        // Perform the registration using the provided context.
        context.doRegister();
    }

    @Override
    public RegistrationContext newRegistrationContext() {
        return new RegistrationContext();
    }

    @Override
    public ActionFactoryFactory getActionFactoryFactory() {
        ActionFactoryFactory factory = getGenericInstance(ActionFactoryFactory.class);
        if (factory == null) {
            setActionFactoryFactory(null);
            factory = getGenericInstance(ActionFactoryFactory.class);
        }
        return factory;
    }

    @Override
    public ActionFactoryFactory setActionFactoryFactory(
            ActionFactoryFactory actionFactoryFactory) {
        if (actionFactoryFactory == null) {
            actionFactoryFactory = ActionFactory::new;
        }
        final ActionFactoryFactory previous = registerGenericInstance(
                ActionFactoryFactory.class, actionFactoryFactory);
        // Use lazy resetting.
        final IWorldDataManager worldMan = NCPAPIProvider.getNoCheatPlusAPI().getWorldDataManager();
        final Iterator<Entry<String, IWorldData>> it = worldMan.getWorldDataIterator();
        while (it.hasNext()) {
            final ConfigFile config = it.next().getValue().getRawConfiguration();
            config.setActionFactory(actionFactoryFactory);
        }
        // (Removing cached configurations and update are to be called externally.)
        return previous;
    }

}
