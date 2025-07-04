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
package fr.neatmonster.nocheatplus.compat.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.Bukkit;
import fr.neatmonster.nocheatplus.compat.MCAccess;
import fr.neatmonster.nocheatplus.compat.bukkit.MCAccessBukkit;
import fr.neatmonster.nocheatplus.compat.bukkit.MCAccessBukkitModern;
import fr.neatmonster.nocheatplus.compat.cbreflect.MCAccessCBReflect;
import fr.neatmonster.nocheatplus.compat.versions.GenericVersion;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.logging.StaticLog;

/**
 * Factory class to hide potentially dirty stuff.
 * @author mc_dev
 *
 */
public class MCAccessFactory {

    /**
     * Get a new MCAccess instance.
     * @param bukkitOnly Set to true to force using an API-only module.
     * @return
     * @throws RuntimeException if no access can be set.
     */
    public MCAccess getMCAccess(final MCAccessConfig config) {
        final List<Throwable> throwables = new ArrayList<Throwable>();
        MCAccess mcAccess = null;
        // Try to set up native access.
        
        // CraftBukkit (dedicated).
        // Use CraftBukkit dedicated if the server version is below/equal to 1.12.2
        if (GenericVersion.compareVersions(ServerVersion.getMinecraftVersion(), "1.12.2") <= 0) {
            if (config.enableCBDedicated) {
                mcAccess = getMCAccessCraftBukkit(throwables);
                if (mcAccess != null) {
                    return mcAccess;
                }
            }
        }

        // Bukkit API only: 1.13 (and possibly later).
        try {
            return new MCAccessBukkitModern();
        }
        catch(Throwable t) {
            throwables.add(t);
        }

        // CraftBukkit (reflection).
        if (config.enableCBReflect) {
            try {
                return new MCAccessCBReflect();
            }
            catch (Throwable t) {
                throwables.add(t);
            }
        }

        // Lets try it one more time?
        if (config.enableCBDedicated) {
            mcAccess = getMCAccessCraftBukkit(throwables);
            if (mcAccess != null) {
                return mcAccess;
            }
        }

        // Try to set up api-only access (since 1.4.6).
        try {
            mcAccess = new MCAccessBukkit();
            StaticLog.logWarning("Running in Bukkit-API-only mode (" + Bukkit.getServer().getVersion() + "). If this is not intended, please check for updates and consider to request support.");
            //            if (ConfigManager.getConfigFile().getBoolean(ConfPaths.LOGGING_EXTENDED_STATUS)) {
            //                log(throwables); // Maybe later activate with TRACE explicitly set
            //            }
            StaticLog.logWarning("Bukkit-API-only mode: Some features will likely not function properly, performance might suffer.");
            return mcAccess;
        }
        catch(Throwable t) {
            throwables.add(t);
        }

        // All went wrong. A proper fall-back solution such as disabling the plugin
        // or particular checks is still to be implemented.
        StaticLog.logSevere("Your version of NoCheatPlus is not compatible with the version of the server-mod (" + Bukkit.getServer().getVersion() + "). Please check for updates and consider to request support.");
        StaticLog.logSevere(">>> Failed to set up MCAccess <<<");
        log(throwables);
        // The plugin should probably be disabled here to avoid further issues.
        throw new RuntimeException("Could not set up native access to the server mod, neither to the Bukkit-API.");
    }

    private static void log(Collection<Throwable> throwables) {
        for (Throwable t : throwables ) {
            StaticLog.logSevere(t);
        }
    }

    /**
     * Get MCaccess for CraftBukkit (dedicated only). Must not throw anything.
     * @param throwables
     * @return Valid MCAccess instance or null.
     */
    private MCAccess getMCAccessCraftBukkit(List<Throwable> throwables) {

        // Quick return check might be added later (note special forks and package information not being usable).

        final String[] classNames = new String[] {
                // Current DEV / LATEST: CB (Spigot)
                "fr.neatmonster.nocheatplus.compat.cbdev.MCAccessCBDev", // latest / tests.

                // Dedicated: CB (Spigot)
                "fr.neatmonster.nocheatplus.compat.spigotcb1_11_R1.MCAccessSpigotCB1_11_R1", // 1.11.2 (1_11_R1)
                "fr.neatmonster.nocheatplus.compat.spigotcb1_10_R1.MCAccessSpigotCB1_10_R1", // 1.10-1.10.2 (1_10_R1)
                "fr.neatmonster.nocheatplus.compat.spigotcb1_9_R2.MCAccessSpigotCB1_9_R2", // 1.9.4 (1_9_R2)
                "fr.neatmonster.nocheatplus.compat.spigotcb1_9_R1.MCAccessSpigotCB1_9_R1", // 1.9-1.9.3 (1_9_R1)
                "fr.neatmonster.nocheatplus.compat.spigotcb1_8_R3.MCAccessSpigotCB1_8_R3", // 1.8.4-1.8.8 (1_8_R3)
                "fr.neatmonster.nocheatplus.compat.spigotcb1_8_R2.MCAccessSpigotCB1_8_R2", // 1.8.3 (1_8_R2)
                "fr.neatmonster.nocheatplus.compat.spigotcb1_8_R1.MCAccessSpigotCB1_8_R1", // 1.8 (1_8_R1)
        };

        for (String className : classNames) {
            try{
                return (MCAccess) Class.forName(className).newInstance();
            }
            catch(Throwable t) {
                throwables.add(t);
            }
        }

        // None worked.
        return null;
    }

}
