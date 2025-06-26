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
package fr.neatmonster.nocheatplus.checks.moving.vehicle;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Pig;

import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;
import fr.neatmonster.nocheatplus.checks.moving.model.VehicleMoveData;
import fr.neatmonster.nocheatplus.checks.moving.model.VehicleMoveInfo;

/**
 * Per-vehicle behavior strategy used by {@link VehicleEnvelope}.
 */
enum VehicleBehavior {

    BOAT {
        @Override
        void apply(final VehicleEnvelope env, final Entity vehicle,
                    final VehicleMoveInfo info, final VehicleMoveData data) {
            env.applyBoatSettings();
        }
    },
    MINECART {
        @Override
        void apply(final VehicleEnvelope env, final Entity vehicle,
                    final VehicleMoveInfo info, final VehicleMoveData data) {
            env.applyMinecartSettings(info, data);
        }
    },
    HORSE {
        @Override
        void apply(final VehicleEnvelope env, final Entity vehicle,
                    final VehicleMoveInfo info, final VehicleMoveData data) {
            env.applyHorseSettings();
        }
    },
    STRIDER {
        @Override
        void apply(final VehicleEnvelope env, final Entity vehicle,
                    final VehicleMoveInfo info, final VehicleMoveData data) {
            env.applyStriderSettings(data);
        }
    },
    CAMEL {
        @Override
        void apply(final VehicleEnvelope env, final Entity vehicle,
                    final VehicleMoveInfo info, final VehicleMoveData data) {
            env.applyCamelSettings();
        }
    },
    PIG {
        @Override
        void apply(final VehicleEnvelope env, final Entity vehicle,
                    final VehicleMoveInfo info, final VehicleMoveData data) {
            env.applyPigSettings();
        }
    },
    GENERIC {
        @Override
        void apply(final VehicleEnvelope env, final Entity vehicle,
                    final VehicleMoveInfo info, final VehicleMoveData data) {
            env.useGenericSettings(data.vehicleType);
        }
    };

    abstract void apply(VehicleEnvelope env, Entity vehicle,
                        VehicleMoveInfo info, VehicleMoveData data);

    static VehicleBehavior fromEntity(final Entity vehicle,
                                      final VehicleEnvelope env) {
        if (vehicle == null) {
            return GENERIC;
        }
        if (MaterialUtil.isBoat(vehicle.getType())) {
            return BOAT;
        }
        if (vehicle instanceof Minecart) {
            return MINECART;
        }
        if (env.isCamel(vehicle)) {
            return CAMEL;
        }
        if (env.isStrider(vehicle)) {
            return STRIDER;
        }
        if (env.isHorse(vehicle)) {
            return HORSE;
        }
        if (vehicle instanceof Pig) {
            return PIG;
        }
        return GENERIC;
    }
}
