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
package fr.neatmonster.nocheatplus.utilities.entity;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.World;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.VehicleMoveData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.util.AuxMoving;
import fr.neatmonster.nocheatplus.checks.moving.vehicle.VehicleSetPassengerTask;
import fr.neatmonster.nocheatplus.checks.workaround.WRPT;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.compat.Folia;
import fr.neatmonster.nocheatplus.components.entity.IEntityAccessVehicle;
import fr.neatmonster.nocheatplus.components.registry.event.IHandle;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;
import fr.neatmonster.nocheatplus.utilities.location.LocUtil;
import fr.neatmonster.nocheatplus.utilities.location.TrigUtil;
import java.util.concurrent.CompletableFuture;

/**
 * Vehicle/passenger related static utility. Registered as generic instance for
 * now.
 * 
 * @author asofold
 *
 */
public class PassengerUtil {

    public final IHandle<IEntityAccessVehicle> handleVehicle = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstanceHandle(IEntityAccessVehicle.class);

    /** Temp use. LocUtil.clone on passing. setWorld(null) after use. */
    private final Location useLoc = new Location(null, 0, 0, 0);
    /** Temp use. LocUtil.clone on passing. setWorld(null) after use. */
    private final Location useLoc2 = new Location(null, 0, 0, 0);
    
    private final Plugin plugin = Bukkit.getPluginManager().getPlugin("NoCheatPlus");

    private static class PassengerStats {
        boolean playerIsOriginalPassenger;
        int otherPlayers;
    }

    private static class TeleportResult {
        boolean vehicleTeleported;
        boolean playerTeleported;
        int otherPlayersTeleported;
    }

    /**
     * Test if the given entity is a passenger of the given vehicle.
     * 
     * @param entity
     * @param vehicle
     * @return
     */
    public boolean isPassenger(final Entity entity, final Entity vehicle) {
        return handleVehicle.getHandle().getEntityPassengers(vehicle).contains(entity);
    }

    /**
     * Check getPassenger recursively until a player is found, return that one
     * or null. This is intended to be the player in charge of steering the
     * vehicle.
     *
     * @param entity
     *            The vehicle.
     * @return The first player found amongst passengers recursively, excludes
     *         the given entity.
     */
    public Player getFirstPlayerPassenger(Entity entity) {
        List<Entity> passengers = handleVehicle.getHandle().getEntityPassengers(entity);
        while (!passengers.isEmpty()){
            entity = passengers.get(0); // The one in charge.
            if (entity == null) {
                break;
            }
            else if (entity instanceof Player){
                return (Player) entity;
            }
            passengers = handleVehicle.getHandle().getEntityPassengers(entity);
        }
        return null;
    }

    /**
     * Check recursively for vehicles, returns null if players are vehicles,
     * otherwise the lowest vehicle (that has no vehicle).
     *
     * @param passenger
     *            The passenger of vehicles. Typically the player.
     * @return Supposedly the vehicle that is steered.
     */
    public Entity getLastNonPlayerVehicle(final Entity passenger) {
        return getLastNonPlayerVehicle(passenger, false);
    }

    /**
     * Check recursively for vehicles, returns null if players are vehicles,
     * otherwise the lowest vehicle (that has no vehicle).
     *
     * @param passenger
     *            The passenger of vehicles. Typically the player.
     * @param includePassenger
     *            If set to true, the passenger is counted as a vehicle as well
     *            (meaning: vehicle enter, the player is not in a vehicle, test
     *            with this set to true and the vehicle returned by the event).
     * @return Supposedly the vehicle that is steered.
     */
    public Entity getLastNonPlayerVehicle(final Entity passenger, final boolean includePassenger) {
        Entity vehicle = includePassenger ? passenger : passenger.getVehicle();
        while (vehicle != null){
            if (vehicle instanceof Player){
                return null;
            }
            else if (vehicle.isInsideVehicle()) {
                vehicle = vehicle.getVehicle();
            }
            else {
                break;
            }
        }
        return vehicle;
    }

    //    /**
    //     * Get a player from an entity. This will return the first player found
    //     * amongst the entity itself and passengers checked recursively.
    //     *
    //     * @param entity
    //     *            the entity
    //     * @return the player passenger recursively
    //     */
    //    public Player getFirstPlayerIncludingPassengersRecursively(Entity entity) {
    //        while (entity != null) {
    //            if (entity instanceof Player) {
    //                // Scrap the case of players riding players for the moment.
    //                return (Player) entity;
    //            }
    //            final Entity passenger = entity.getPassenger();
    //            if (entity.equals(passenger)) {
    //                // Just in case :9.
    //                break;
    //            }
    //            else {
    //                entity = passenger;
    //            }
    //        }
    //        return null;
    //    }

    /**
     * Teleport the player with vehicle, might temporarily eject the passengers
     * and set teleported in MovingData. The passengers are fetched from the
     * vehicle with this method.
     *
     * @param vehicle
     *            The vehicle to teleport.
     * @param player
     *            The (original) player in charge, who'd also trigger
     *            violations. Should be originalPassengers[0].
     * @param location
     *            Location to teleport the vehicle to.
     * @param debug
     *            the debug
     */
    public void teleportWithPassengers(final Entity vehicle, final Player player, final Location location, final boolean debug, final IPlayerData pData) {
        final List<Entity> originalPassengers = handleVehicle.getHandle().getEntityPassengers(vehicle);
        teleportWithPassengers(vehicle, player, location, debug, originalPassengers.toArray(new Entity[originalPassengers.size()]), false, pData);
    }

    /**
     * Teleport the player with vehicle, might temporarily eject the passengers and set
     * teleported in MovingData.
     *
     * @param vehicle
     *            The vehicle to teleport.
     * @param player
     *            The (original) player in charge, who'd also trigger
     *            violations. Should be originalPassengers[0].
     * @param location
     *            Location to teleport the vehicle to.
     * @param debug
     *            the debug
     * @param originalPassengers
     *            The passengers at the time, that is to be restored. Must not be null.
     * @param CheckPassengers Set to true to compare current with original passengers.
     */

    public void teleportWithPassengers(final Entity vehicle, final Player player, final Location location,
                                       final boolean debug, final Entity[] originalPassengers, final boolean checkPassengers,
                                       final IPlayerData pData) {
        if (vehicle == null || player == null || location == null || originalPassengers == null) {
            return;
        }

        final MovingData data = pData.getGenericInstance(MovingData.class);
        final String pWorld = player.getWorld() != null ? player.getWorld().getName() : "";
        final String vWorld = vehicle.getWorld() != null ? vehicle.getWorld().getName() : "";
        final boolean vWorldMatchesPWorld = vWorld.equals(pWorld);

        data.isVehicleSetBack = true;

        final PassengerStats stats = preparePassengerStats(originalPassengers, player);

        final boolean redoPassengers = true; // false; // Some time in the future a teleport might work directly.

        if (!stats.playerIsOriginalPassenger && debug) {
            CheckUtils.debug(player, CheckType.MOVING_VEHICLE, "Vehicle setback: This player is not an original passenger.");
        }

        final boolean vehicleTeleported = teleportVehicle(vehicle, location, redoPassengers);

        TeleportResult result = new TeleportResult();
        result.vehicleTeleported = vehicleTeleported;

        if (redoPassengers) {
            result = teleportPassengers(vehicle, player, location, debug, originalPassengers, vWorldMatchesPWorld,
                    vehicleTeleported, stats.playerIsOriginalPassenger, data);
        }

        logTeleportResult(player, location, debug, result, stats.otherPlayers);

        useLoc.setWorld(null);
        useLoc2.setWorld(null);
    }

    /**
     * Teleport and set as passenger.
     * 
     * @param player
     * @param vehicle
     * @param location
     * @param vehicleTeleported
     * @param data
     * @return
     */
    private boolean teleportPlayerPassenger(final Player player, final Entity vehicle,
                                            final Location location, final boolean vehicleTeleported,
                                            final MovingData data, final boolean debug) {
        if (player == null || vehicle == null || location == null) {
            return false;
        }

        final boolean playerTeleported;
        if (player.isOnline() && !player.isDead()) {
            final MovingConfig cc = NCPAPIProvider.getNoCheatPlusAPI()
                    .getPlayerDataManager()
                    .getPlayerData(player)
                    .getGenericInstance(MovingConfig.class);

            // Mask player teleport as a set back.
            data.prepareSetBack(location);
            playerTeleported = Folia.teleportEntity(player, LocUtil.clone(location),
                    BridgeMisc.TELEPORT_CAUSE_CORRECTION_OF_POSITION);
            data.resetTeleported(); // Cleanup, just in case.

            // Allow re-use of certain workarounds.
            data.ws.resetConditions(WRPT.G_RESET_NOTINAIR);

            if (playerTeleported && vehicleTeleported
                    && player.getLocation(useLoc2).distance(vehicle.getLocation(useLoc)) < 1.5) {
                handlePassengerScheduling(player, vehicle, cc, data, debug);
                ensureSetBackLocation(player, location, data, debug);
                updatePastMove(vehicle, location, data, cc);
            }
        } else {
            playerTeleported = false;
        }

        data.isVehicleSetBack = false;
        return playerTeleported;
    }


    private PassengerStats preparePassengerStats(final Entity[] originalPassengers, final Player player) {
        final PassengerStats stats = new PassengerStats();
        if (originalPassengers != null) {
            for (final Entity passenger : originalPassengers) {
                if (passenger == null) {
                    continue;
                }
                if (passenger.equals(player)) {
                    stats.playerIsOriginalPassenger = true;
                    break;
                } else if (passenger instanceof Player) {
                    NCPAPIProvider.getNoCheatPlusAPI()
                            .getPlayerDataManager()
                            .getPlayerData((Player) passenger)
                            .getGenericInstance(MovingData.class).isVehicleSetBack = true;
                    stats.otherPlayers++;
                }
            }
        }
        return stats;
    }

    private boolean teleportVehicle(final Entity vehicle, final Location location, final boolean redoPassengers) {
        if (vehicle == null || vehicle.isDead() || !vehicle.isValid()) {
            return false;
        }
        if (redoPassengers) {
            vehicle.eject();
        }
        return Folia.teleportEntity(vehicle, LocUtil.clone(location), BridgeMisc.TELEPORT_CAUSE_CORRECTION_OF_POSITION);
    }

    private TeleportResult teleportPassengers(final Entity vehicle, final Player player, final Location location,
                                              final boolean debug, final Entity[] originalPassengers,
                                              final boolean vWorldMatchesPWorld, final boolean vehicleTeleported,
                                              final boolean playerIsOriginalPassenger, final MovingData data) {
        final TeleportResult result = new TeleportResult();
        result.vehicleTeleported = vehicleTeleported;

        if (!playerIsOriginalPassenger) {
            teleportPlayerPassenger(player, vehicle, location, vehicleTeleported, data, debug);
        }

        for (final Entity passenger : originalPassengers) {
            if (passenger == null) {
                continue;
            }
            // Skip passengers that are invalid or located in a different world than the vehicle
            if (!passenger.isValid() || passenger.isDead() || !vWorldMatchesPWorld) {
                if (debug) {
                    final String reason = !vWorldMatchesPWorld ? "world mismatch" : "invalid state";
                    CheckUtils.debug(player, CheckType.MOVING_VEHICLE,
                            "Skipping passenger due to " + reason + ". playerId=" + player.getUniqueId()
                                    + " vehicleId=" + vehicle.getEntityId());
                }
                continue;
            }
            if (passenger instanceof Player) {
                if (teleportPlayerPassenger((Player) passenger, vehicle, location, vehicleTeleported,
                        NCPAPIProvider.getNoCheatPlusAPI()
                                .getPlayerDataManager()
                                .getPlayerData((Player) passenger)
                                .getGenericInstance(MovingData.class), debug)) {
                    if (player.equals(passenger)) {
                        result.playerTeleported = true;
                    } else {
                        result.otherPlayersTeleported++;
                    }
                }
            } else {
                if (Folia.teleportEntity(passenger, location, BridgeMisc.TELEPORT_CAUSE_CORRECTION_OF_POSITION)
                        && vehicleTeleported
                        && TrigUtil.distance(passenger.getLocation(useLoc2), vehicle.getLocation(useLoc)) < 1.5) {
                    handleVehicle.getHandle().addPassenger(passenger, vehicle);
                }
            }
        }
        return result;
    }

    private void logTeleportResult(final Player player, final Location location, final boolean debug,
                                   final TeleportResult result, final int otherPlayers) {
        if (debug) {
            CheckUtils.debug(player, CheckType.MOVING_VEHICLE,
                    "Vehicle set back resolution: " + location + " pt=" + result.playerTeleported + " vt=" + result.vehicleTeleported
                            + (otherPlayers > 0 ? (" opt=" + result.otherPlayersTeleported + "/" + otherPlayers) : ""));
        }
    }

    private void handlePassengerScheduling(final Player player, final Entity vehicle,
                                           final MovingConfig cc, final MovingData data,
                                           final boolean debug) {
        boolean scheduleDelay = cc.schedulevehicleSetPassenger;
        if (data.vehicleSetPassengerTaskId == null) {
            if (vehicle.getType() == EntityType.BOAT) {
                addPassengerWithRetry(player, vehicle, 2).thenAccept(success -> {
                    if (!success) {
                        vehicle.eject();
                    }
                });
                return;
            }
            if (scheduleDelay) {
                data.vehicleSetPassengerTaskId = Folia.runSyncDelayedTaskForEntity(player, plugin,
                        (arg) -> new VehicleSetPassengerTask(handleVehicle, vehicle, player).run(), null, 2L);
                if (data.vehicleSetPassengerTaskId == null) {
                    if (debug) {
                        CheckUtils.debug(player, CheckType.MOVING_VEHICLE,
                                "Failed to schedule set passenger (plugin=" + plugin.getName()
                                        + ", delay=2)");
                    }
                    scheduleDelay = false;
                } else if (debug) {
                    CheckUtils.debug(player, CheckType.MOVING_VEHICLE,
                            "Schedule set passenger task id: " + data.vehicleSetPassengerTaskId);
                }
            }

            if (!scheduleDelay) {
                if (debug) {
                    CheckUtils.debug(player, CheckType.MOVING_VEHICLE,
                            "Attempt set passenger directly");
                }
                handleVehicle.getHandle().addPassenger(player, vehicle);
            }
        } else if (debug) {
            CheckUtils.debug(player, CheckType.MOVING_VEHICLE,
                    "Set passenger task already scheduled, skip this time.");
        }
    }

    private CompletableFuture<Boolean> addPassengerWithRetry(final Entity passenger, final Entity vehicle,
                                                             final int maxRetries) {
        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (passenger == null || vehicle == null) {
            result.complete(false);
            return result;
        }
        if (handleVehicle.getHandle().addPassenger(passenger, vehicle)) {
            result.complete(true);
            return result;
        }
        if (maxRetries <= 0 || plugin == null) {
            result.complete(false);
            return result;
        }
        final int[] remaining = { maxRetries };
        final Object task = Folia.runSyncRepeatingTask(plugin, (arg) -> {
            if (handleVehicle.getHandle().addPassenger(passenger, vehicle)) {
                result.complete(true);
                Folia.cancelTask(arg);
            } else if (--remaining[0] <= 0) {
                result.complete(false);
                Folia.cancelTask(arg);
            }
        }, 1L, 1L);
        if (task == null) {
            result.complete(false);
        }
        return result;
    }

    private void ensureSetBackLocation(final Player player, final Location location,
                                       final MovingData data, final boolean debug) {
        if (data.vehicleSetBacks.getFirstValidEntry(location) == null) {
            if (debug) {
                CheckUtils.debug(player, CheckType.MOVING_VEHICLE,
                        "No set back is matching the vehicle location that it has just been set back to. Reset all lazily to: "
                                + location);
            }
            data.vehicleSetBacks.resetAllLazily(location);
        }
    }

    private void updatePastMove(final Entity vehicle, final Location location, final MovingData data,
                                final MovingConfig cc) {
        final VehicleMoveData firstPastMove = data.vehicleMoves.getFirstPastMove();
        if (!firstPastMove.valid || firstPastMove.toIsValid || !TrigUtil.isSamePos(firstPastMove.from, location)) {
            NCPAPIProvider.getNoCheatPlusAPI().getGenericInstance(AuxMoving.class)
                    .resetVehiclePositions(vehicle, location, data, cc);
        }
    }
}
