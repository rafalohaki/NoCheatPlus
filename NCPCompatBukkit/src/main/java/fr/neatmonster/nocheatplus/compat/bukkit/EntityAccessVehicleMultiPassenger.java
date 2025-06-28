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
        // Ensure both getPassengers and addPassenger methods exist.
        boolean hasGetPassengers =
                ReflectionUtil.getMethodNoArgs(Entity.class, "getPassengers", List.class) != null;
        boolean hasAddPassenger =
                ReflectionUtil.getMethodNoArgs(Entity.class, "addPassenger", Entity.class) != null;
        if (!hasGetPassengers || !hasAddPassenger) {
            return null;
        }
        if (!hasAddPassenger()) {
            return null;
        }
        return new EntityAccessVehicleMultiPassenger();
    }

    private static boolean hasAddPassenger() {
        Method method = ReflectionUtil.getMethod(Entity.class, "addPassenger", Entity.class);
        return method != null && method.getReturnType() == boolean.class;
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
