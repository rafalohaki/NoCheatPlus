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
package fr.neatmonster.nocheatplus.compat;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;
import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.logging.LogManager;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.logging.StaticLog;

import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

public class Folia {
    private static final boolean RegionizedServer = ReflectionUtil.getClass("io.papermc.paper.threadedregions.RegionizedServer") != null;
    //private static final Class<?> AsyncScheduler = ReflectionUtil.getClass("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
    private static final Class<?> GlobalRegionScheduler = ReflectionUtil.getClass("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
    private static final Class<?> EntityScheduler = ReflectionUtil.getClass("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
    private static final boolean isFoliaServer = RegionizedServer && GlobalRegionScheduler != null && EntityScheduler != null; // && AsyncScheduler != null
    
    /**
     * @return Whether the server is running Folia
     */
    public static boolean isFoliaServer() {
        return isFoliaServer;
    }

    /**
     * Run an asynchronous task. Execution is always performed via the Bukkit or
     * Folia scheduler.
     *
     * @param plugin plugin to assign for
     * @param run consumer that accepts an object or {@code null}, for Folia or
     *            Paper/Spigot respectively
     * @return the scheduled task object or {@code null} if scheduling fails
     */
    public static Object runAsyncTask(Plugin plugin, Consumer<Object> run) {
        if (!isFoliaServer) {
            return Bukkit.getScheduler().runTaskAsynchronously(plugin,
                    () -> run.accept(null)).getTaskId();
        }
        //try {
        //    Method getSchedulerMethod =
        //            ReflectionUtil.getMethodNoArgs(Server.class, "getAsyncScheduler", AsyncScheduler);
        //    Object asyncScheduler = getSchedulerMethod.invoke(Bukkit.getServer());
        //
        //    Class<?> schedulerClass = asyncScheduler.getClass();
        //    Method executeMethod = schedulerClass.getMethod("runNow", Plugin.class, Consumer.class);
        //
        //    Object taskInfo = executeMethod.invoke(asyncScheduler, plugin, run);
        //    return taskInfo;
        //}
        //catch (Exception e) {
            // Second attempt, should be happened during onDisable calling from BukkitLogNodeDispatcher
            try {
                return Bukkit.getScheduler().runTaskAsynchronously(plugin,
                        () -> run.accept(null));
            } catch (Exception ex) {
                LogManager logManager = NCPAPIProvider.getNoCheatPlusAPI().getLogManager();
                logManager.warning(Streams.STATUS,
                        "Failed to schedule async task (plugin=" + plugin.getName() + ") "
                                + ex.getClass().getSimpleName());
                logManager.warning(Streams.STATUS, ex);
            }
            return null;
        //}
    }

    /**
     * Run a repeating task using the Bukkit or Folia scheduler.
     *
     * @param plugin Plugin to assign for
     * @param run Consumer that accepts an object or {@code null}, for Folia or Paper/Spigot respectively
     * @param delay Delay in ticks
     * @param period Period in ticks
     * @return An {@link Integer} task id when running on Paper/Spigot, a {@code ScheduledTask}
     *         when running on Folia or {@code null} if scheduling failed
     */
    public static Object runSyncRepeatingTask(Plugin plugin, Consumer<Object> run, long delay, long period) {
        if (!isFoliaServer) {
            return Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> run.accept(null), delay, period);
        }
        try {
            Method getSchedulerMethod = ReflectionUtil.getMethodNoArgs(Server.class, "getGlobalRegionScheduler", GlobalRegionScheduler);
            //ReflectionUtil.invokeMethod(getSchedulerMethod, Bukkit.getServer());
            Object syncScheduler = getSchedulerMethod.invoke(Bukkit.getServer());

            Class<?> schedulerClass = syncScheduler.getClass();
            //ReflectionUtil.getMethod(schedulerClass, "runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            Method executeMethod = schedulerClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);

            //ReflectionUtil.invokeMethod(executeMethod, syncScheduler, plugin, run, delay, period);
            Object taskInfo = executeMethod.invoke(syncScheduler, plugin, run, delay, period);
            return taskInfo;
        } catch (Exception e) {
            LogManager logManager = NCPAPIProvider.getNoCheatPlusAPI().getLogManager();
            logManager.warning(Streams.STATUS,
                    "Failed to schedule repeating task (plugin=" + plugin.getName()
                            + ", delay=" + delay + ", period=" + period + ") "
                            + e.getClass().getSimpleName());
            logManager.warning(Streams.STATUS, e);
        }
        return null;
    }

    /**
     * @deprecated Use
     *             {@link #runSyncRepeatingTask(Plugin, Consumer, long, long)}
     *             instead. Removal scheduled for version 2.0.
     *             <p>
     *             Migration: replace invocations of this method with
     *             {@code runSyncRepeatingTask}.
     *             </p>
     */
    @Deprecated
    public static Object runSyncRepatingTask(Plugin plugin, Consumer<Object> run, long delay, long period) {
        return runSyncRepeatingTask(plugin, run, delay, period);
    }

    /**
     * Run a task, either with bukkit scheduler or folia scheduler
     * @param plugin Plugin to assign for
     * @param run Consumer that accepts an object or null, for Folia or Paper/Spigot respectively
     * @return An int represent for task id when running on Paper/Spigot or a ScheduledTask when running on Folia or null if can't schedule
     */
    public static Object runSyncTask(Plugin plugin, Consumer<Object> run) {
        if (!isFoliaServer) {
            return Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> run.accept(null));
        }
        try {
            Method getSchedulerMethod = ReflectionUtil.getMethodNoArgs(Server.class, "getGlobalRegionScheduler", GlobalRegionScheduler);
            Object syncScheduler = getSchedulerMethod.invoke(Bukkit.getServer());

            Class<?> schedulerClass = syncScheduler.getClass();
            Method executeMethod = schedulerClass.getMethod("run", Plugin.class, Consumer.class);

            Object taskInfo = executeMethod.invoke(syncScheduler, plugin, run);
            return taskInfo;
        } catch (Exception e) {
            LogManager logManager = NCPAPIProvider.getNoCheatPlusAPI().getLogManager();
            logManager.warning(Streams.STATUS,
                    "Failed to schedule sync task (plugin=" + plugin.getName() + ") "
                            + e.getClass().getSimpleName());
            logManager.warning(Streams.STATUS, e);
        }
        return null;
    }

    /**
     * Run a delayed task, either with bukkit scheduler or folia scheduler
     * @param plugin Plugin to assign for
     * @param run Consumer that accepts an object or null, for Folia or Paper/Spigot respectively
     * @param delay Delay in ticks
     * @return An int represent for task id when running on Paper/Spigot or a ScheduledTask when running on Folia or null if can't schedule
     */
    public static Object runSyncDelayedTask(Plugin plugin, Consumer<Object> run, long delay) {
        if (plugin == null || run == null) {
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager()
                    .warning(Streams.STATUS, "runSyncDelayedTask called with null argument.");
            return null;
        }
        if (!isFoliaServer) {
            return Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> run.accept(null), delay);
        }
        try {
            Method getSchedulerMethod = ReflectionUtil.getMethodNoArgs(Server.class, "getGlobalRegionScheduler", GlobalRegionScheduler);
            Object syncScheduler = getSchedulerMethod.invoke(Bukkit.getServer());

            Class<?> schedulerClass = syncScheduler.getClass();
            Method executeMethod = schedulerClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);

            Object taskInfo = executeMethod.invoke(syncScheduler, plugin, run, delay);
            return taskInfo;
        } catch (Exception e) {
            LogManager logManager = NCPAPIProvider.getNoCheatPlusAPI().getLogManager();
            logManager.warning(Streams.STATUS,
                    "Failed to schedule delayed task (plugin=" + plugin.getName()
                            + ", delay=" + delay + ") " + e.getClass().getSimpleName());
            logManager.warning(Streams.STATUS, e);
        }
        return null;
    }

    /**
     * Run a delayed task for an entity on next tick, either with bukkit scheduler or folia scheduler
     * @param entity The Entity to assign a task
     * @param plugin Plugin to assign for
     * @param run Consumer that accepts an object or null, for Folia or Paper/Spigot respectively
     * @param retired The task to run if entity is retired before the task is run
     * @return An int represent for task id when running on Paper/Spigot or a ScheduledTask when running on Folia or null if can't schedule
     */
    public static Object runSyncTaskForEntity(Entity entity, Plugin plugin, Consumer<Object> run, Runnable retired) {
        return runSyncDelayedTaskForEntity(entity, plugin, run, retired, 1L);
    }
    
    /**
     * Run a delayed task for an entity on next tick, either with bukkit scheduler or folia scheduler
     * @param entity The Entity to assign a task
     * @param plugin Plugin to assign for
     * @param run Consumer that accepts an object or null, for Folia or Paper/Spigot respectively
     * @param retired The task to run if entity is retired before the task is run
     * @param delay Delay in ticks
     * @return An int represent for task id when running on Paper/Spigot or a ScheduledTask when running on Folia or null if can't schedule
     */
    public static Object runSyncDelayedTaskForEntity(Entity entity, Plugin plugin, Consumer<Object> run, Runnable retired, long delay) {
        if (entity == null || plugin == null || run == null) {
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager()
                    .warning(Streams.STATUS, "runSyncDelayedTaskForEntity called with null argument.");
            return null;
        }
        if (!isFoliaServer) {
            return Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> run.accept(null), delay);
        }
        try {
            Method getSchedulerMethod = ReflectionUtil.getMethodNoArgs(Entity.class, "getScheduler", EntityScheduler);
            Object syncEntityScheduler = getSchedulerMethod.invoke(entity);

            Class<?> schedulerClass = syncEntityScheduler.getClass();
            Method executeMethod = schedulerClass.getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);

            Object taskInfo = executeMethod.invoke(syncEntityScheduler, plugin, run, retired, delay);
            return taskInfo;
        } catch (Exception e) {
            LogManager logManager = NCPAPIProvider.getNoCheatPlusAPI().getLogManager();
            logManager.warning(Streams.STATUS,
                    "Failed to schedule entity task (plugin=" + plugin.getName()
                            + ", delay=" + delay + ") " + e.getClass().getSimpleName());
            logManager.warning(Streams.STATUS, e);
        }
        return null;
    }

    /**
     * Cancel a specific task
     * @param o The task to cancel (can be an int taskID on Paper/Spigot or a ScheduledTask on Folia. If null, does nothing!
     */
    public static void cancelTask(Object o) {
        if (o == null) return;
        if (o instanceof Thread) return;// o = null ?
        if (o instanceof Integer) {
            int taskId = (int)o;
            Bukkit.getScheduler().cancelTask(taskId);
        } else {
            Method cancelMethod = ReflectionUtil.getMethodNoArgs(o.getClass(), "cancel");
            ReflectionUtil.invokeMethodNoArgs(cancelMethod, o);
        }
    }

    /**
     * Cancel all tasks from given plugin
     * @param plugin Plugin to assign for
     */
    public static void cancelTasks(Plugin plugin) {
        if (!isFoliaServer) {
            Bukkit.getScheduler().cancelTasks(plugin);
        } else {
            try {
                Method getGlobalRegionSchedulerMethod = ReflectionUtil.getMethodNoArgs(Server.class, "getGlobalRegionScheduler", GlobalRegionScheduler);
                //Method getAsyncSchedulerMethod = ReflectionUtil.getMethodNoArgs(Server.class, "getAsyncScheduler", AsyncScheduler);
                
                Object syncScheduler = getGlobalRegionSchedulerMethod.invoke(Bukkit.getServer());
                //Object asyncScheduler = getAsyncSchedulerMethod.invoke(Bukkit.getServer());

                Class<?> schedulerClass = syncScheduler.getClass();
                Method executeMethod = schedulerClass.getMethod("cancelTasks", Plugin.class);
                executeMethod.invoke(syncScheduler, plugin);
                
                //schedulerClass = asyncScheduler.getClass();
                //executeMethod = schedulerClass.getMethod("cancelTasks", Plugin.class);
                //executeMethod.invoke(asyncScheduler, plugin);
            } catch (Exception e) {
                StaticLog.logSevere(e);
            }
        }
    }

    public static boolean teleportEntity(Entity entity, Location loc, TeleportCause cause) {
        if (!isFoliaServer) {
            return entity.teleport(loc, cause);
        }
        try {
            Method teleportAsyncMethod = ReflectionUtil.getMethod(Entity.class, "teleportAsync", Location.class, TeleportCause.class);
            Object result = ReflectionUtil.invokeMethod(teleportAsyncMethod, entity, loc, cause);
            CompletableFuture<Boolean> res = (CompletableFuture<Boolean>) result;
            return res.join();
        } catch (Exception e) {
            StaticLog.logSevere(e);
        }
        return false;
    }

    /**
     * @param object task number or ScheduledTask or Thread
     * @return Whether the task is scheduled
     */
    public static boolean isTaskScheduled(Object task) {
        if (task == null) return false;
        if (task instanceof Integer) return (int)task != -1;
        return true;
    } 
}
