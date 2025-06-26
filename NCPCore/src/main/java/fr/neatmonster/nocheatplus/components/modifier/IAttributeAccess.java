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
 * Encapsulate attribute access. Note that some of the methods may exclude
 * specific modifiers, or otherwise perform calculations, e.g. in order to
 * return a multiplier to be applied to typical walking speed.
 * 
 * @author asofold
 *
 */
public interface IAttributeAccess {

    /**
     * Sentinel value used when an attribute can not be determined.
     * <p>
     * {@link Double#NaN} is used rather than {@link Double#MAX_VALUE} to
     * simplify checks and avoid comparisons with large numbers.
     */
    double ATTRIBUTE_UNAVAILABLE = Double.NaN;

    /**
     * Generic speed modifier as a multiplier.
     * 
     * @param player
     * @return A multiplier for the allowed speed, excluding the sprint boost
     *         modifier (!). If not possible to determine,
     *         {@link #ATTRIBUTE_UNAVAILABLE} is returned.
     */
    public double getSpeedAttributeMultiplier(Player player);

    /**
     * Sprint boost modifier as a multiplier.
     * 
     * @param player
     * @return The sprint boost modifier as a multiplier. If not possible to
     *         determine, {@link #ATTRIBUTE_UNAVAILABLE} is returned.
     */
    public double getSprintAttributeMultiplier(Player player);

}
