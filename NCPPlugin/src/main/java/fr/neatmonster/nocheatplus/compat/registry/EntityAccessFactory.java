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
import fr.neatmonster.nocheatplus.components.entity.IEntityAccessLastPositionAndLook;
import fr.neatmonster.nocheatplus.components.entity.IEntityAccessVehicle;
import fr.neatmonster.nocheatplus.compat.bukkit.EntityAccessVehicleMultiPassenger;

/**
 * Set up more fine grained entity access providers, registered as generic
 * instances for interfaces for now. Namely:
 * <ul>
 * <li>IEntityAccessPositionAndLook</li>
 * </ul>
 * 
 * @author asofold
 *
 */
public class EntityAccessFactory {

    /**
     * Set up alongside with MCAccess. The MCAccess instance is passed here,
     * before it has been set internally and before it has been advertised to
     * MCAccessHolder instances, so the latter can get other specific access
     * providers during handling setMCAccess.
     * 
     * @param mcAccess
     * @param config
     */
    public void setupEntityAccess(final MCAccess mcAccess, final MCAccessConfig config) {

        // Register implementation for IEntityAccessLastPositionAndLook if available.

        // IEntityAccessVehicle
        IEntityAccessVehicle vehicleAccess = EntityAccessVehicleMultiPassenger.createIfSupported();
        if (vehicleAccess == null) {
            vehicleAccess = new fr.neatmonster.nocheatplus.compat.bukkit.EntityAccessVehicleLegacy();
        }
        RegistryHelper.registerGenericInstance(IEntityAccessVehicle.class, vehicleAccess);
    }

}
