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
package fr.neatmonster.nocheatplus.components.entity;

import java.util.List;

import org.bukkit.entity.Entity;

/**
 * Vehicle specific access to entities.
 * 
 * @author asofold
 *
 */
public interface IEntityAccessVehicle {

    /**
     * Get the current passengers for the given vehicle entity.
     *
     * @param vehicle the entity acting as a vehicle
     * @return list of passengers, never {@code null}
     */
    public List<Entity> getEntityPassengers(Entity vehicle);

    /**
     * Add a passenger entity to the given vehicle entity.
     *
     * @param passenger the entity to add as passenger
     * @param vehicle the vehicle to add the passenger to
     * @return {@code true} if the passenger was added successfully
     */
    public boolean addPassenger(Entity passenger, Entity vehicle);

}
