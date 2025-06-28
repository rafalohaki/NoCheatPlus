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

import java.util.List;
import java.lang.reflect.Method;

import org.bukkit.entity.Entity;

import fr.neatmonster.nocheatplus.components.entity.IEntityAccessVehicle;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;

public class EntityAccessVehicleMultiPassenger implements IEntityAccessVehicle {

    private EntityAccessVehicleMultiPassenger() {
        // Empty constructor.
    }

    /**
     * Create an instance if supported by the running server implementation.
     *
     * @return Instance or {@code null} if not supported.
     */
    public static EntityAccessVehicleMultiPassenger createIfSupported() {
        // Check getPassengers method.
        final Method getPassengers = ReflectionUtil.getMethodNoArgs(Entity.class, "getPassengers");
        if (getPassengers == null) {
            StaticLog.logDebug("Entity#getPassengers() method not found.");
            return null;
        }
        if (!List.class.isAssignableFrom(getPassengers.getReturnType())) {
            StaticLog.logDebug(
                    "Entity#getPassengers() has unexpected return type: " + getPassengers.getReturnType().getName());
            return null;
        }

        // Check addPassenger method and return type.
        if (!hasAddPassenger()) {
            return null;
        }

        return new EntityAccessVehicleMultiPassenger();
    }

    private static boolean hasAddPassenger() {
        final Method method = ReflectionUtil.getMethod(Entity.class, "addPassenger", Entity.class);
        if (method == null) {
            StaticLog.logDebug("Entity#addPassenger(Entity) method not found.");
            return false;
        }
        final Class<?> returnType = method.getReturnType();
        if (returnType != boolean.class && returnType != void.class) {
            StaticLog.logDebug("Entity#addPassenger(Entity) has unexpected return type: "
                    + returnType.getName());
            return false;
        }
        return true;
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
