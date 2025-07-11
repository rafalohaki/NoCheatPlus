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
package fr.neatmonster.nocheatplus.logging.details;

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

import fr.neatmonster.nocheatplus.compat.Folia;
import fr.neatmonster.nocheatplus.components.registry.feature.TickListener;
import fr.neatmonster.nocheatplus.utilities.TickTask;

/**
 * Bukkit specific implementation of {@link AbstractLogNodeDispatcher}.
 */
public class BukkitLogNodeDispatcher extends AbstractLogNodeDispatcher {


    /**
     * Permanent TickListener for logging. May be replaced with on-demand
     * scheduling in the future.
     */
    private final TickListener taskPrimary = (tick, timeLast) -> {
        if (runLogsPrimary()) {
            // Rescheduling is handled inside {@link #runLogsPrimary()}.
        }

    };

    /**
     * Needed for scheduling.
     */
    private final Plugin plugin;

    public BukkitLogNodeDispatcher(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Necessary logging to a primary thread task (TickTask) or asynchronously.
     * This can be called multiple times without causing damage.
     */
    public void startTasks() {
        TickTask.addTickListener(taskPrimary);
        scheduleAsynchronous(); // Just in case.
    }

    @Override
    protected void scheduleAsynchronous() {
        synchronized (queueAsynchronous) {
            if (!Folia.isTaskScheduled(taskAsynchronousID)) {
                // Deadlocking should not be possible.
                // The asynchronous task only processes queued log records and
                // must avoid directly invoking Bukkit API methods. Any such
                // operations must be rescheduled via Folia.runSyncTask.
                try {
                    taskAsynchronousID =
                            Folia.runAsyncTask(plugin, (arg) -> taskAsynchronous.run());
                } catch (IllegalPluginAccessException ex) {
                    // (Should be during onDisable, ignore for now.)
                }
            }
        }
    }

    @Override
    protected final boolean isPrimaryThread() {
        return Bukkit.isPrimaryThread();
    }

    @Override
    protected void cancelTask(Object taskInfo) {
        Folia.cancelTask(taskInfo);
    }

    @Override
    protected boolean isTaskScheduled(Object taskInfo) {
        return Folia.isTaskScheduled(taskInfo);
    }

}
