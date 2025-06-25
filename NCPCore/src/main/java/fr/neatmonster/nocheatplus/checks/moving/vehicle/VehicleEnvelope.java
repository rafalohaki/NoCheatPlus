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

import java.util.LinkedList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.location.setback.SetBackEntry;
import fr.neatmonster.nocheatplus.checks.moving.magic.LostGroundVehicle;
import fr.neatmonster.nocheatplus.checks.moving.magic.MagicVehicle;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.model.VehicleMoveData;
import fr.neatmonster.nocheatplus.checks.moving.model.VehicleMoveInfo;
import fr.neatmonster.nocheatplus.checks.workaround.WRPT;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.PotionUtil;
import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.location.RichEntityLocation;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;

/**
 * Vehicle moving envelope check, for Minecraft 1.9 and higher.
 * 
 * @author asofold
 *
 */
public class VehicleEnvelope extends Check {

    /**
     * Check specific details for re-use.
     * 
     * @author asofold
     *
     */
    public class CheckDetails {

        public boolean canClimb;
        public boolean canRails;
        public boolean canJump, canStepUpBlock; // Indicates supported jumping and stepping heights.
        public double maxAscend;
        public double gravityTargetSpeed;
        /** Simplified type, like BOAT, MINECART. */
        public EntityType simplifiedType; // Not sure can be kept up.
        public boolean checkAscendMuch;
        public boolean checkDescendMuch;
        /** From could be a new set back location. */
        public boolean fromIsSafeMedium;
        /** To could be a new set back location. */
        public boolean toIsSafeMedium;
        /** Interpreted differently depending on check. */
        public boolean inAir;

        public void reset() {
            canClimb = canRails = canJump = canStepUpBlock = false;
            maxAscend = 0.0;
            checkAscendMuch = checkDescendMuch = true;
            fromIsSafeMedium = toIsSafeMedium = inAir = false;
            simplifiedType = null;
            gravityTargetSpeed = MagicVehicle.boatVerticalFallTarget;
        }

    }

    /** Tags for checks. */
    private final List<String> tags = new LinkedList<String>();

    /** Extra details to log on debug. */
    private final List<String> debugDetails = new LinkedList<String>();

    /** Details for re-use. */
    private final CheckDetails checkDetails = new CheckDetails();

    private final Class<?> bestHorse;
    
    private final Class<?> strider;
    
    private final Class<?> camel;
    
   /*
    *
    * Instanties a new VehicleEnvelope check
    *
    */
    public VehicleEnvelope() {
        super(CheckType.MOVING_VEHICLE_ENVELOPE);
        Class<?> clazz = ReflectionUtil.getClass("org.bukkit.entity.AbstractHorse");
        bestHorse = clazz == null ? ReflectionUtil.getClass("org.bukkit.entity.Horse") : clazz;
        strider = ReflectionUtil.getClass("org.bukkit.entity.Strider");
        camel = ReflectionUtil.getClass("org.bukkit.entity.Camel");
    }


  /**
    *
    * @param player
    * @param vehicle
    * @param thisMove
    * @param isFake
    * @param data
    * @param cc
    * @param pData
    * @param moveInfo
    *
    */
    public SetBackEntry check(final Player player, final Entity vehicle, 
                              final VehicleMoveData thisMove, final boolean isFake, 
                              final MovingData data, final MovingConfig cc, 
                              final IPlayerData pData, final VehicleMoveInfo moveInfo) {

        final boolean debug = pData.isDebugActive(type);

        // Delegate to a sub-check.
        tags.clear();
        tags.add("entity." + vehicle.getType());

        if (debug) {
            debugDetails.clear();
            data.ws.setJustUsedIds(debugDetails); // Add just used workaround ids to this list directly, for now.
        }
        // Additional confinement may be required for certain vehicle situations.
        LostGroundVehicle.lostGround(vehicle, moveInfo.from, moveInfo.to, thisMove.hDistance, thisMove.yDistance, false, data.vehicleMoves.getFirstPastMove(), data, cc, null, tags);
        final boolean violation = checkEntity(player, vehicle, thisMove, isFake, data, cc, debug, moveInfo);

        if (debug && !debugDetails.isEmpty()) {
            debugDetails(player);
            debugDetails.clear();
        }

        if (violation) {
            data.vehicleEnvelopeVL += 1.0; // Add up one for now.
            final ViolationData vd = new ViolationData(this, player, data.vehicleEnvelopeVL, 1, cc.vehicleEnvelopeActions);
            vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
            if (executeActions(vd).willCancel()) {
                return data.vehicleSetBacks.getValidSafeMediumEntry();
            }
        }
        else {
            data.vehicleEnvelopeVL *= 0.99; // Random cool down for now.
            // Do not set a set back here.
        }
        return null;
    }


  /**
    *
    * @param player
    */
    private void debugDetails(final Player player) {

        if (!tags.isEmpty()) {
            debugDetails.add("tags:");
            debugDetails.add(StringUtil.join(tags, "+"));
        }

        final StringBuilder builder = new StringBuilder(500);
        builder.append("Details:\n");

        for (final String detail : debugDetails) {
            builder.append(" , ");
            builder.append(detail);
        }

        debug(player, builder.toString());
        debugDetails.clear();
    }


  /**
    * Return the horizontal distance cap for the vehicle
    * @param type
    * @param cc
    * @param thisMove
    * @param data
    *
    */
    private double getHDistCap(final EntityType type, final MovingConfig cc, final VehicleMoveData thisMove, final MovingData data) {

        final Double cap = cc.vehicleEnvelopeHorizontalSpeedCap.get(type);
        final Double globalcap = cc.vehicleEnvelopeHorizontalSpeedCap.get(null);

        if (cap == null) {
            if(MaterialUtil.isBoat(type)){
                return getHDistCapBoats(thisMove,data,1.0,globalcap);
            }
            return globalcap;
        }
        else {
            if(MaterialUtil.isBoat(type)) {
                return getHDistCapBoats(thisMove,data,cap,globalcap);
            }
            return cap;
        }
    }
    /**
     * Return the horizontal distance cap for the boat
     * @param thisMove
     * @param data
     * @param multiplier
     *
     */
    private double getHDistCapBoats(final VehicleMoveData thisMove, final MovingData data, final double multiplier, final double globalcap) {
        if(thisMove.from.onBlueIce && !thisMove.to.onBlueIce){ //workaround for when the boat leaves icy places
            data.boatIceVelocityTicks = 20;
        }
        else if (thisMove.from.onIce && !thisMove.to.onIce){
            data.boatIceVelocityTicks = 10;
        }
        if (thisMove.from.onBlueIce || thisMove.to.onBlueIce) return multiplier * 4.1;
        if (thisMove.from.onIce || thisMove.to.onIce) return multiplier * 2.3;
        if(data.boatIceVelocityTicks-- > 0){ // allow high speed for a moment
            if (data.boatIceVelocityTicks > 10) return multiplier * 4.1;
            return multiplier * 2.3;
        }
        if ((thisMove.from.onGround && !thisMove.from.inWater) || thisMove.to.onGround && !thisMove.to.inWater) return multiplier * 0.4;
        if (thisMove.from.inWater || thisMove.to.inWater) return multiplier * 0.5;
        return multiplier == 1.0 ? globalcap : multiplier;
    }

  /**
    * Actual check
    * @param player
    * @param vehicle
    * @param thisMove
    * @param thisMove
    * @param isFake
    * @param data
    * @param cc
    * @param debug
    * @param moveInfo
    *
    * @return
    */
    private boolean checkEntity(final Player player, final Entity vehicle, 
                                final VehicleMoveData thisMove, final boolean isFake, 
                                final MovingData data, final MovingConfig cc,
                                final boolean debug, final VehicleMoveInfo moveInfo) {

        boolean violation = false;
        long now = System.currentTimeMillis();

        if (debug) {
            debugDetails.add("inair: " + data.sfJumpPhase);
        }

        if ((moveInfo.from.getBlockFlags() & BlockFlags.F_BUBBLECOLUMN) != 0
            // Should use BlockTraceTracker instead blind leniency
            //|| (isBouncingBlock(moveInfo.from) && thisMove.yDistance >= 0.0 && thisMove.yDistance <= 1.0)
            ) {
            data.timeVehicletoss = System.currentTimeMillis();
        }

        // Medium dependent checking.
        // Pigs on layered snow might need special handling using bounding boxes or lost-ground checks.
        // Maximum thinkable horizontal speed.
        // Further distinctions should be stored within CheckDetails for clarity.
        if (vehicle instanceof LivingEntity) {
            Double speed = PotionUtil.getPotionEffectAmplifier((LivingEntity)vehicle, PotionEffectType.SPEED);
            // The most terrible code I ever written due to poor infrastructure. Should be thinking of recode the vehicle check after the hspeed refactor
            if (camel != null && camel.isAssignableFrom(vehicle.getClass())) {
                final VehicleMoveData firstPastMove = data.vehicleMoves.getFirstPastMove();
                double cap = getHDistCap(checkDetails.simplifiedType, cc, thisMove, data);
                if (!Double.isInfinite(speed)) cap *= (1 + 0.2 * (speed + 1));
                if (thisMove.hDistance > cap) {
                    final long current = System.currentTimeMillis();
                    // This delay should be stored on the ridden entity rather than the player.
                    if (data.timeCamelDash + 2000 < current) {
                        data.timeCamelDash = current;
                        return maxDistHorizontal(thisMove, cap * 2.7);
                    } else {
                        return maxDistHorizontal(thisMove, firstPastMove.toIsValid ? (firstPastMove.hDistance * 0.98) : cap);
                    }
                }
            }
            if (!Double.isInfinite(speed)) {
                if (maxDistHorizontal(thisMove, getHDistCap(checkDetails.simplifiedType, cc, thisMove, data) * (1 + 0.2 * (speed + 1)))) {
                    return true;
                }
            } 
            else if (maxDistHorizontal(thisMove, getHDistCap(checkDetails.simplifiedType, cc, thisMove,data) + (checkDetails.canJump ? 0.18 : 0.0))) {
                return true;
            }
        } 
        else if (maxDistHorizontal(thisMove, getHDistCap(checkDetails.simplifiedType, cc, thisMove,data))) { // Override type for now.
            return true;
        }

        // Consider limiting descent to twice maxDescend and using a lower limit for ascent.
        // The logic in this section might be better organized in helper methods.
        // Extended liquid specifications could allow confining to certain flags such as water.
        if (thisMove.from.inWeb) {
            // Additional checks might be required for web movement.
            if (debug) {
                debugDetails.add("");
            }
            //            if (thisMove.yDistance > 0.0) {
            //                tags.add("ascend_web");
            //                return true;
            //            }
            // Ascending may need to be disallowed here.
            // Maximum speed limits should also be enforced.
        }
        else if (checkDetails.canClimb && thisMove.from.onClimbable) {
            // Ensure the order of checks remains consistent.
            checkDetails.checkAscendMuch = checkDetails.checkDescendMuch = false;
            if (Math.abs(thisMove.yDistance) > MagicVehicle.climbSpeed) {
                violation = true;
                tags.add("climbspeed");
            }
        }
        else if (checkDetails.canRails && thisMove.fromOnRails) {
            // This could be inverted to trigger a violation when the distance limit is exceeded.
            if (Math.abs(thisMove.yDistance) < MagicVehicle.maxRailsVertical) {
                checkDetails.checkAscendMuch = checkDetails.checkDescendMuch = false;
            }
        }
        else if (thisMove.from.inWater && thisMove.to.inWater) {
            // Default in-medium move.
            if (debug) {
                debugDetails.add("water-water");
            }
            // Extreme moves should still be accounted for in this section.

            // Special case moving up after falling.
            // Past moves might need inspection to detect falling.
            // Verify whether the target location is part of the surface.
            if (MagicVehicle.oddInWater(thisMove, checkDetails, data)) {
                // (Assume players can't control sinking boats for now.)
                checkDetails.checkDescendMuch = checkDetails.checkAscendMuch = false;
                violation = false;
            }
        }
        else if (thisMove.from.onGround && thisMove.to.onGround) {
            // Default on-ground move.
            // Extreme moves should still be validated here.
            if (checkDetails.canStepUpBlock && thisMove.yDistance > 0.0 && thisMove.yDistance <= 1.0) {
                checkDetails.checkAscendMuch = false;
                tags.add("step_up");
            }
            if (thisMove.from.onBlueIce && thisMove.to.onBlueIce) {
                // Default on-blueIce move.
                if (debug) {
                    debugDetails.add("blueIce-blueIce");
                }
                // Extreme moves should still be validated here.
            }
            else if (thisMove.from.onIce && thisMove.to.onIce) {
                // Default on-ice move.
                if (debug) {
                    debugDetails.add("ice-ice");
                }
                // Extreme moves should still be validated here.
            }
            else {
                // This case resembles a typical on-ground movement.
                if (debug) {
                    debugDetails.add("ground-ground");
                }
            }
        }
        else if (checkDetails.inAir) {
            // In-air move.
            if (checkInAir(thisMove, data, debug, vehicle, moveInfo)) {
                violation = true;
            }
        }
        else {
            // Some transition to probably handle.
            if (debug) {
                debugDetails.add("?-?");
            }
            // Lift off speed and related parameters might need evaluation.
            // This branch overlaps with other cases.
            // Skipped vehicle move events may also occur here.
            if (!checkDetails.toIsSafeMedium) {
                // A fallback action might be required here.
            }
        }

        if (vehicle instanceof LivingEntity) {
            Double levitation = Bridge1_9.getLevitationAmplifier((LivingEntity)vehicle);
            if (!Double.isInfinite(levitation)) {
                checkDetails.maxAscend += 0.046 * (levitation + 1);
                violation = false;
            }
        }

        // Maximum ascend speed.
        if (checkDetails.checkAscendMuch && thisMove.yDistance > checkDetails.maxAscend) {
            tags.add("ascend_much");
            violation = true;
        }
        
        // Workaround
        if (data.timeVehicletoss + 2000 > now && thisMove.yDistance < 4.0) {
            violation = false;
        }
        
        // Maximum descend speed.
        if (checkDetails.checkDescendMuch && thisMove.yDistance < -MagicVehicle.maxDescend) {
            // A skipped move can cause a large negative distance and trigger 'vehicle moved too quickly'.
            // Logging may help verify the order of events.
            tags.add("descend_much");
            violation = true;
        }

        if (vehicle instanceof LivingEntity) {
            final VehicleMoveData firstPastMove = data.vehicleMoves.getFirstPastMove();
            
            if (thisMove.hDistance > 0.1D && thisMove.yDistance == 0D && !thisMove.to.onGround && !thisMove.from.onGround
                && firstPastMove.valid && firstPastMove.yDistance == 0D 
                && thisMove.to.inLiquid && thisMove.from.inLiquid
                && !thisMove.headObstructed 
                && !((strider != null && strider.isAssignableFrom(vehicle.getClass())) && thisMove.to.inLava && thisMove.from.inLava) // The strider can walk on lava
                ) {
                violation = true;
                tags.add("liquidwalk");
            }

            Material blockUnder = vehicle.getLocation().subtract(0, 0.3, 0).getBlock().getType();
            Material blockAbove = vehicle.getLocation().add(0, 0.10, 0).getBlock().getType();
            if (blockUnder != null && blockAbove != null && BlockProperties.isAir(blockAbove)
                && BlockProperties.isLiquid(blockUnder) && !(strider != null && strider.isAssignableFrom(vehicle.getClass()))
                ) {
                if (thisMove.hDistance > 0.11D && thisMove.yDistance <= 0.1D && !thisMove.to.onGround && !thisMove.from.onGround
                    && firstPastMove.valid && firstPastMove.yDistance == thisMove.yDistance || firstPastMove.yDistance == thisMove.yDistance * -1 
                    && firstPastMove.yDistance != 0D
                    && !thisMove.headObstructed) {

                    // Prevent being flagged if a vehicle transitions from a block to water and the player falls into the water.
                    if (!(thisMove.yDistance < 0 && thisMove.yDistance != 0 && firstPastMove.yDistance < 0 && firstPastMove.yDistance != 0)) {
                        violation = true;
                        tags.add("liquidmove");
                    }
                }
            }
        }

        if (!violation) {
            // No violation.
            // sfJumpPhase is currently reused for in-air move counting here.
            if (checkDetails.inAir) {
                data.sfJumpPhase ++;
            }
            else {
                // Adjust set back.
                if (checkDetails.toIsSafeMedium) {
                    data.vehicleSetBacks.setSafeMediumEntry(thisMove.to);
                    data.sfJumpPhase = 0;
                }
                else if (checkDetails.fromIsSafeMedium) {
                    data.vehicleSetBacks.setSafeMediumEntry(thisMove.from);
                    data.sfJumpPhase = 0;
                }
                // Reset the resetNotInAir workarounds.
                data.ws.resetConditions(WRPT.G_RESET_NOTINAIR);
            }
            data.vehicleSetBacks.setLastMoveEntry(thisMove.to);
        }

        return violation;
    }
    

  /**
    * @param from
    *
    */
    private boolean isBouncingBlock(RichEntityLocation from) {
        return (from.getBlockFlags() & BlockFlags.F_BOUNCE25) != 0;
    }


   /**
     * Prepare checkDetails according to vehicle-specific interpretation of side
     * conditions.
     * 
     * @param vehicle
     * @param moveInfo Cheating.
     * @param thisMove
     */
    protected void prepareCheckDetails(final Entity vehicle, final VehicleMoveInfo moveInfo, final VehicleMoveData thisMove) {

        checkDetails.reset();
        // These properties are tuned for boats; other vehicles may require different values.
        checkDetails.fromIsSafeMedium = thisMove.from.inWater || thisMove.from.onGround || thisMove.from.inWeb;
        checkDetails.toIsSafeMedium = thisMove.to.inWater || thisMove.to.onGround || thisMove.to.inWeb;
        checkDetails.inAir = !checkDetails.fromIsSafeMedium && !checkDetails.toIsSafeMedium;
        // Distinguish by entity class (needs future proofing at all?).
        if (vehicle != null && MaterialUtil.isBoat(vehicle.getType())) {
            checkDetails.simplifiedType = EntityType.BOAT;
            checkDetails.maxAscend = MagicVehicle.maxAscend;
        }
        else if (vehicle instanceof Minecart) {
            checkDetails.simplifiedType = EntityType.MINECART;
            // Bind to rails.
            checkDetails.canRails = true;
            thisMove.setExtraMinecartProperties(moveInfo); // Cheating.
            if (thisMove.fromOnRails) {
                checkDetails.fromIsSafeMedium = true;
                checkDetails.inAir = false;
            }

            if (thisMove.toOnRails) {
                checkDetails.toIsSafeMedium = true;
                checkDetails.inAir = false;
            }
            checkDetails.gravityTargetSpeed = 0.79;
        }
        else if (bestHorse != null && bestHorse.isAssignableFrom(vehicle.getClass())) {
            // Horses currently do not appear to climb.
            checkDetails.simplifiedType = EntityType.HORSE; // Consider using AbstractHorse from 1.11 onward.
            checkDetails.canJump = checkDetails.canStepUpBlock = true;
        }
        else if (strider != null && strider.isAssignableFrom(vehicle.getClass())) {
            //checkDetails.simplifiedType = EntityType.PIG;
            checkDetails.canJump = false;
            checkDetails.canStepUpBlock = true;
            checkDetails.canClimb = true;
            // Step problem
            checkDetails.maxAscend = 1.1;
            // Fall in lava
            if (thisMove.from.inLava || thisMove.to.inLava) checkDetails.inAir = false;
            // ....
            if (!thisMove.from.onGround && thisMove.to.onGround) checkDetails.gravityTargetSpeed = 0.07;
            // Updated by PlayerMoveEvent, hdist fps when a player want to ride on strider
        }
        else if (camel != null && camel.isAssignableFrom(vehicle.getClass())) {
            checkDetails.canStepUpBlock = true;
            checkDetails.canClimb = false;
            checkDetails.canJump = false;
        }
        else if (vehicle instanceof Pig) {
            // Pigs can climb certain obstacles.
            checkDetails.simplifiedType = EntityType.PIG;
            checkDetails.canJump = false;
            checkDetails.canStepUpBlock = true;
            checkDetails.canClimb = true;
        }
        else {
            checkDetails.simplifiedType = thisMove.vehicleType;
        }

        // Generic settings.
        // (maxAscend is not checked for stepping up blocks)
        if (checkDetails.canJump) {
            checkDetails.maxAscend = 1.2; // Coarse envelope. Actual lift off gain should be checked on demand.
        }

        // Climbable
        if (checkDetails.canClimb) {
            if (thisMove.from.onClimbable) {
                checkDetails.fromIsSafeMedium = true;
                checkDetails.inAir = false;
            }

            if (thisMove.to.onClimbable) {
                checkDetails.toIsSafeMedium = true;
                checkDetails.inAir = false;
            }
        }
    }


    /**
     * Generic in-air check.
     * @param thisMove
     * @param data
     * @return
     */
    private boolean checkInAir(final VehicleMoveData thisMove, final MovingData data,
                               final boolean debug, final Entity vehicle, final VehicleMoveInfo moveInfo) {

        final RichEntityLocation from = moveInfo.from;
        final RichEntityLocation to = moveInfo.to;

        // Consider distinguishing sfJumpPhase from inAirDescendCount once the jump reaches its apex.

        if (debug) {
            debugDetails.add("air-air");
        }

        if (checkDetails.canJump) {
            // Maximum Y-distance for set back is yet to be finalized.
            // Friction may also need to be accounted for.
        }
        else {
            if (thisMove.yDistance > 0.0) {
                tags.add("ascend_at_all");
                return true;
            }
        }

        boolean violation = false;
        // Absolute vertical distance to set back.
        // Future work might include a vertical distance based roll back.
        //            final double setBackYdistance = to.getY() - data.vehicleSetBacks.getValidSafeMediumEntry().getY();
        //            if (data.sfJumpPhase > 4) {
        //                double estimate = Math.min(2.0, MagicVehicle.boatGravityMin * ((double) data.sfJumpPhase / 4.0) * ((double) data.sfJumpPhase / 4.0 + 1.0) / 2.0);
        //                if (setBackYdistance > -estimate) {
        //                    tags.add("slow_fall_vdistsb");
        //                    return true;
        //                }
        //            }
        // Enforce falling speed (vdist) envelope by in-air phase count.
        // Slow falling (vdist), do not bind to descending in general.
        final double minDescend = -(thisMove.yDistance < -MagicVehicle.boatLowGravitySpeed ? 
                                    MagicVehicle.boatGravityMinAtSpeed : MagicVehicle.boatGravityMin) * 
                                    (checkDetails.canJump ? Math.max(data.sfJumpPhase - MagicVehicle.maxJumpPhaseAscend, 0) : data.sfJumpPhase);                     
        final double maxDescend = getInAirMaxDescend(thisMove, data);

        if (data.sfJumpPhase > (checkDetails.canJump ? MagicVehicle.maxJumpPhaseAscend : 1)
            && thisMove.yDistance > Math.max(minDescend, -checkDetails.gravityTargetSpeed)) {
            
            boolean noViolation = ColliesHoneyBlock(from)
                    || (vehicle instanceof LivingEntity && !Double.isInfinite(Bridge1_13.getSlowfallingAmplifier((LivingEntity)vehicle)))
                    || !vehicle.hasGravity();
            // Possible sliding on honey block may cause this behavior.
            if (ColliesHoneyBlock(from)) data.sfJumpPhase = 5;

            if (!noViolation) {
                tags.add("slow_fall_vdist");
                violation = true;
            }
        }
        // Fast falling (vdist).
        else if (data.sfJumpPhase > 1 && thisMove.yDistance < maxDescend) {
            // One skipped move per jump phase (1, 2, 3) might be acceptable.
            tags.add("fast_fall_vdist");
            violation = true;
        }
        if (violation) {
            // Post violation detection workarounds.
            if (MagicVehicle.oddInAir(thisMove, minDescend, maxDescend, checkDetails, data)) {
                violation = false;
                checkDetails.checkDescendMuch = checkDetails.checkAscendMuch = false; // (Full envelope has been checked.)
            }

            if (debug) {
                debugDetails.add("maxDescend: " + maxDescend);
            }
        }
        return violation;
    }


  /**
    * @param thisMove
    * @param data
    *
    */
    private double getInAirMaxDescend(final PlayerMoveData thisMove, final MovingData data) {

        double maxDescend = -MagicVehicle.boatGravityMax * data.sfJumpPhase - 0.5;
        final VehicleMoveData firstPastMove = data.vehicleMoves.getFirstPastMove();

        if (thisMove.yDistance < maxDescend && firstPastMove.toIsValid) {
            if (firstPastMove.yDistance < maxDescend && firstPastMove.yDistance > maxDescend * 2.5) {
                // Simply continue with friction.
                maxDescend = Math.min(maxDescend, firstPastMove.yDistance - (MagicVehicle.boatGravityMax + MagicVehicle.boatGravityMin) / 2.0);
                debugDetails.add("desc_frict");
            }
            else if (firstPastMove.specialCondition && thisMove.yDistance > -1.5) {
                // After special set-back confirm move, observed ca. -1.1.
                maxDescend = Math.min(maxDescend, -1.5);
                debugDetails.add("desc_special");
            }
        }
        return maxDescend;
    }
    

  /**
    * @param thisMove
    * @param maxDistanceHorizontal
    *
    */
    private boolean maxDistHorizontal(final VehicleMoveData thisMove, final double maxDistanceHorizontal) {
        if (thisMove.hDistance > maxDistanceHorizontal) {
            tags.add("hdist");
            return true;
        }
        else {
            return false;
        }
    }


  /**
    * @param from
    *
    */
    private boolean ColliesHoneyBlock(RichEntityLocation from) {
        return (from.getBlockFlags() & BlockFlags.F_STICKY) != 0;
    }
}
