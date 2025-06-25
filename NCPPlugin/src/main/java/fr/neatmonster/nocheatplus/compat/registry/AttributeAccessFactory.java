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

import fr.neatmonster.nocheatplus.compat.MCAccess;
import fr.neatmonster.nocheatplus.compat.bukkit.BukkitAttributeAccess;
import fr.neatmonster.nocheatplus.compat.bukkit.NSBukkitAttributeAccess;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.components.modifier.DummyAttributeAccess;
import fr.neatmonster.nocheatplus.components.modifier.IAttributeAccess;
import fr.neatmonster.nocheatplus.logging.StaticLog;

public class AttributeAccessFactory {

    /**
     * Set up alongside with MCAccess. The MCAccess instance is passed here,
     * before it has been set internally and before it has been advertised to
     * MCAccessHolder instances, so the latter can get other specific access
     * providers during handling setMCAccess.
     * 
     * @param mcAccess
     * @param config
     */
    public void setupAttributeAccess(final MCAccess mcAccess, final MCAccessConfig config) {
        final IAttributeAccess fallBackReflect = new DummyAttributeAccess();
        IAttributeAccess fallBackDedicated = null;
        try {
            fallBackDedicated = ServerVersion.compareMinecraftVersion("1.21") < 0 ? new BukkitAttributeAccess() : new NSBukkitAttributeAccess();
        }
        catch (Throwable t) {
            StaticLog.logDebug(t);
        }
        RegistryHelper.setupGenericInstance(new String[] {
                "fr.neatmonster.nocheatplus.compat.cbdev.AttributeAccess",
                "fr.neatmonster.nocheatplus.compat.spigotcb1_10_R1.AttributeAccess",
                "fr.neatmonster.nocheatplus.compat.spigotcb1_9_R2.AttributeAccess",
                "fr.neatmonster.nocheatplus.compat.spigotcb1_9_R1.AttributeAccess",
                "fr.neatmonster.nocheatplus.compat.spigotcb1_8_R3.AttributeAccess",
                "fr.neatmonster.nocheatplus.compat.spigotcb1_8_R2.AttributeAccess",
                "fr.neatmonster.nocheatplus.compat.spigotcb1_8_R1.AttributeAccess"
        }, fallBackDedicated, new String[] {
                "fr.neatmonster.nocheatplus.compat.cbreflect.reflect.ReflectAttributeAccess" // Legacy
        }, fallBackReflect, IAttributeAccess.class, config, false);
    }

}
