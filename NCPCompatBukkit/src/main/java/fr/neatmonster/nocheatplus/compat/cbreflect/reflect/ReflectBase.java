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
package fr.neatmonster.nocheatplus.compat.cbreflect.reflect;

import org.bukkit.Bukkit;
import org.bukkit.Server;

import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;

public class ReflectBase {

    // Envelope check: consider an enum for envelope levels within the expected version range and beyond.

    public final String obcPackageName;

    public final String nmsPackageName;

    public ReflectBase() {
        final Server server = Bukkit.getServer();
        // Further confine detection by verifying package parts and index sequences.
        // obc
        Class<?> clazz = server.getClass();
        String name = clazz.getPackage().getName();
        if (name.equals("org.bukkit.craftbukkit") || name.indexOf("org.") == 0 && name.contains(".bukkit.") && name.contains(".craftbukkit.")) {
            obcPackageName = name;
        } else {
            obcPackageName = null;
        }
        // nms
        clazz = ServerVersion.getNMSMinecraftServer().getClass();
        name = clazz.getPackage().getName();
        if (name.equals("net.minecraft.server") || name.indexOf("net.") == 0 && name.contains(".minecraft.") && name.contains(".server.")) {
            nmsPackageName = name.contains("v1_") ? name : "net.minecraft.world.entity";
        } else {
            nmsPackageName = null;
        }
    }

}
