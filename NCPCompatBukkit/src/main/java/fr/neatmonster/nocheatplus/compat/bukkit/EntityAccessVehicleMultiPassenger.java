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

import org.bukkit.entity.Entity;

import fr.neatmonster.nocheatplus.components.entity.IEntityAccessVehicle;
import fr.neatmonster.nocheatplus.support.Feature;
import fr.neatmonster.nocheatplus.support.FeatureSupportRegistry;

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
        if (!FeatureSupportRegistry.isSupported(Feature.VEHICLE_MULTI_PASSENGER)) {
            return null;
        }
        return new EntityAccessVehicleMultiPassenger();
    }


    @Override
    public List<Entity> getEntityPassengers(final Entity vehicle) {
        return vehicle.getPassengers();
    }

    /**
     * Add {@code passenger} to {@code vehicle}.
     * The first argument is always the passenger entity and the second is the vehicle.
     */
    @Override
    public boolean addPassenger(final Entity passenger, final Entity vehicle) {
        return vehicle.addPassenger(passenger);
    }

}
