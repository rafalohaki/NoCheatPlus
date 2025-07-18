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
package fr.neatmonster.nocheatplus.compat.meta;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.compat.IBridgeCrossPlugin;
import fr.neatmonster.nocheatplus.compat.cbreflect.reflect.ReflectBase;
import fr.neatmonster.nocheatplus.components.registry.feature.IPostRegisterRunnable;
import fr.neatmonster.nocheatplus.utilities.DefensiveReflection;

/**
 * Utility to probe for cross-plugin issues, such as Player delegates.
 * Registered as generic instance for ICrossPlugin.
 * 
 * @author asofold
 *
 */
public class BridgeCrossPlugin implements IBridgeCrossPlugin, IPostRegisterRunnable {

    // Checking might need to be more sophisticated.

    private final Class<?> playerClass;
    private final Class<?> entityClass;

    public BridgeCrossPlugin() {
        this(BridgeCrossPluginLoader.createReflectBase().orElse(null));
    }

    public BridgeCrossPlugin(final ReflectBase reflectBase) {
        if (reflectBase != null) {
            this.playerClass = getEntityClass(reflectBase, "Player");
            this.entityClass = getEntityClass(reflectBase, "Entity", "");
        } else {
            this.playerClass = null;
            this.entityClass = null;
        }
    }

    // Convenience overload that uses the same suffix for OBC and NMS classes.
    private Class<?> getEntityClass(ReflectBase reflectBase, String entityName) {
        return getEntityClass(reflectBase, entityName, entityName);
    }

    private Class<?> getEntityClass(ReflectBase reflectBase, String obcSuffix, String nmsSuffix) {
        if (reflectBase.nmsPackageName == null || reflectBase.obcPackageName == null) {
            return null;
        }
        Class<?> obcPlayer = DefensiveReflection.getClassOptional(
                reflectBase.obcPackageName + ".entity.Craft" + obcSuffix).orElse(null);
        Class<?> nmsPlayer = DefensiveReflection.getClassOptional(
                reflectBase.nmsPackageName + ".Entity" + nmsSuffix).orElse(null);
        if (obcPlayer == null || nmsPlayer == null) {
            return null;
        } else {
            return obcPlayer;
        }
    }

    @Override
    public void runPostRegister() {
        NCPAPIProvider.getNoCheatPlusAPI().registerGenericInstance(IBridgeCrossPlugin.class, this);
    }

    @Override
    public boolean isNativePlayer(final Player player) {
        return playerClass != null && playerClass.isAssignableFrom(player.getClass());
    }

    @Override
    public boolean isNativeEntity(final Entity entity) {
        return entityClass != null && entityClass.isAssignableFrom(entity.getClass());
    }

}
