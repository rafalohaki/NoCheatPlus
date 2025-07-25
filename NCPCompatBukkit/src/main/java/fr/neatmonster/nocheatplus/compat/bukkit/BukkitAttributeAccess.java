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

import java.util.UUID;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.compat.AttribUtil;
import fr.neatmonster.nocheatplus.components.modifier.IAttributeAccess;
import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;

public class BukkitAttributeAccess implements IAttributeAccess {

    private BukkitAttributeAccess() {
        // Empty constructor.
    }

    /**
     * Create an instance if supported by the running server implementation.
     *
     * @return Instance or {@code null} if not supported.
     */
    public static BukkitAttributeAccess createIfSupported() {
        if (ReflectionUtil.getClass("org.bukkit.attribute.AttributeInstance") == null) {
            return null;
        }
        return new BukkitAttributeAccess();
    }

    private int operationToInt(final Operation operation) {
        switch (operation) {
            case ADD_NUMBER:
                return 0;
            case ADD_SCALAR:
                return 1;
            case MULTIPLY_SCALAR_1:
                return 2;
            default:
                throw new RuntimeException("Unknown operation: " + operation);
        }
    }

    /**
     * The first modifier with the given id that can be found, or null if none
     * is found.
     * 
     * @param attrInst
     * @param id
     * @return
     */
    private AttributeModifier getModifier(final AttributeInstance attrInst, final UUID id) {
        for (final AttributeModifier mod : attrInst.getModifiers()) {
            if (id.equals(mod.getUniqueId())) {
                return mod;
            }
        }
        return null;
    }

    private double getMultiplier(final AttributeModifier mod) {
        return AttribUtil.getMultiplier(operationToInt(mod.getOperation()), mod.getAmount());
    }

    @Override
    public double getSpeedAttributeMultiplier(final Player player) {
        final AttributeInstance attrInst = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attrInst == null) {
            return 1.0;
        }
        final double val = attrInst.getValue() / attrInst.getBaseValue();
        final AttributeModifier mod = getModifier(attrInst, AttribUtil.ID_SPRINT_BOOST);
        return mod == null ? val : (val / getMultiplier(mod));
    }

    @Override
    public double getSprintAttributeMultiplier(final Player player) {
        final AttributeInstance attrInst = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attrInst == null) {
            return 1.0;
        }
        final AttributeModifier mod = getModifier(attrInst, AttribUtil.ID_SPRINT_BOOST);
        return mod == null ? 1.0 : getMultiplier(mod);
    }

}
