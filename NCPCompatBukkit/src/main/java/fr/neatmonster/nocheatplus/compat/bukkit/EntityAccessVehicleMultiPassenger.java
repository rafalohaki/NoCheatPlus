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
package fr.neatmonster.nocheatplus.compat.bukkit;

import java.lang.reflect.Method;
import java.util.List;

import org.bukkit.entity.Entity;

import fr.neatmonster.nocheatplus.components.entity.IEntityAccessVehicle;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;

public class EntityAccessVehicleMultiPassenger implements IEntityAccessVehicle {

    /** Cached getPassengers method. */
    private static Method methodGetPassengers;

    /** Cached addPassenger method. */
    private static Method methodAddPassenger;

    private EntityAccessVehicleMultiPassenger() {
        // Empty constructor.
    }

    /**
     * Resolve and cache required methods for multi passenger support.
     *
     * @return {@code true} if both methods exist with expected signatures
     */
    private static boolean resolveAccessMethods() {
        if (methodGetPassengers != null && methodAddPassenger != null) {
            return true;
        }

        methodGetPassengers = ReflectionUtil.getMethodNoArgs(Entity.class, "getPassengers");
        if (methodGetPassengers == null) {
            StaticLog.logDebug("Entity#getPassengers() method not found.");
            return false;
        }
        if (!List.class.isAssignableFrom(methodGetPassengers.getReturnType())) {
            StaticLog.logDebug("Entity#getPassengers() has unexpected return type: "
                    + methodGetPassengers.getReturnType().getName());
            methodGetPassengers = null;
            return false;
        }

        methodAddPassenger = ReflectionUtil.getMethod(Entity.class, "addPassenger", Entity.class);
        if (methodAddPassenger == null) {
            StaticLog.logDebug("Entity#addPassenger(Entity) method not found.");
            methodGetPassengers = null;
            return false;
        }
        final Class<?> returnType = methodAddPassenger.getReturnType();
        if (returnType != boolean.class && returnType != void.class) {
            StaticLog.logDebug("Entity#addPassenger(Entity) has unexpected return type: "
                    + returnType.getName());
            methodGetPassengers = null;
            methodAddPassenger = null;
            return false;
        }
        return true;
    }

    /**
     * Create an instance if supported by the running server implementation.
     *
     * @return Instance or {@code null} if not supported.
     */
    public static EntityAccessVehicleMultiPassenger createIfSupported() {
        if (!resolveAccessMethods()) {
            return null;
        }
        return new EntityAccessVehicleMultiPassenger();
    }


    @Override
    public List<Entity> getEntityPassengers(final Entity entity) {
        return entity.getPassengers();
    }

    @Override
    public boolean addPassenger(final Entity entity, final Entity vehicle) {
        return vehicle.addPassenger(entity);
    }

}
