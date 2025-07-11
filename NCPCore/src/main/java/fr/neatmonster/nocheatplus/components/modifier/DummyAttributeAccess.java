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
package fr.neatmonster.nocheatplus.components.modifier;

import org.bukkit.entity.Player;

/**
 * Default implementation for access not being available.
 * 
 * @author asofold
 *
 */
public class DummyAttributeAccess implements IAttributeAccess {

    @Override
    public double getSpeedAttributeMultiplier(Player player) {
        return ATTRIBUTE_UNAVAILABLE;
    }

    @Override
    public double getSprintAttributeMultiplier(Player player) {
        return ATTRIBUTE_UNAVAILABLE;
    }

}
