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
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;

/**
 * Vehicle moving envelope check, for Minecraft 1.9 and higher.
 * 
 * @author asofold
 *
 */
public class VehicleEnvelope extends Check {

    /** Tolerance for floating point comparisons. */
    private static final double EPSILON = 1.0E-6;

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

    private static enum VehicleBehavior {
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
                env.applyGenericSettings(data.vehicleType);
            }
        };

        abstract void apply(VehicleEnvelope env, Entity vehicle,
                             VehicleMoveInfo info, VehicleMoveData data);

        static VehicleBehavior resolve(final Entity vehicle,
                                        final VehicleMoveData move,
                                        final VehicleEnvelope env) {
            if (vehicle != null) {
                if (MaterialUtil.isBoat(vehicle.getType())) {
                    return BOAT;
                }
                if (vehicle instanceof Minecart) {
                    return MINECART;
                }
                if (move.isCamel) {
                    return CAMEL;
                }
                if (env.isHorse(vehicle)) {
                    return HORSE;
                }
                if (env.isStrider(vehicle)) {
                    return STRIDER;
                }
                if (vehicle instanceof Pig) {
                    return PIG;
                }
            }
            return GENERIC;
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
    
    
   /*
    *
    * Instantiates a new VehicleEnvelope check
    *
    */
    public VehicleEnvelope() {
        super(CheckType.MOVING_VEHICLE_ENVELOPE);
        Class<?> clazz = ReflectionUtil.getClass("org.bukkit.entity.AbstractHorse");
        bestHorse = clazz == null ? ReflectionUtil.getClass("org.bukkit.entity.Horse") : clazz;
        strider = ReflectionUtil.getClass("org.bukkit.entity.Strider");
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
     * @param debug whether debug logging is active
     */
    private double getHDistCap(final EntityType type, final MovingConfig cc, final VehicleMoveData thisMove,
                               final MovingData data, final boolean debug) {

        final Double cap = cc.vehicleEnvelopeHorizontalSpeedCap.get(type);
        final Double globalcap = cc.vehicleEnvelopeHorizontalSpeedCap.get(null);

        if (cap == null) {
            if(MaterialUtil.isBoat(type)){
                return getHDistCapBoats(thisMove, data, 1.0, globalcap, debug);
            }
            return globalcap;
        }
        else {
            if(MaterialUtil.isBoat(type)) {
                return getHDistCapBoats(thisMove, data, cap, globalcap, debug);
            }
            return cap;
        }
    }
    /**
     * Return the horizontal distance cap for the boat
     * @param thisMove
     * @param data
     * @param multiplier
     * @param debug whether debug logging is active
     */
    private double getHDistCapBoats(final VehicleMoveData thisMove, final MovingData data,
                                    final double multiplier, final double globalcap,
                                    final boolean debug) {
        updateBoatIceVelocityTicks(thisMove, data);

        final double terrainMultiplier = calcBoatTerrainMultiplier(thisMove, data, multiplier);
        if (!Double.isNaN(terrainMultiplier)) {
            return terrainMultiplier;
        }
        if (debug) {
            debugDetails.add("No terrain multiplier applied");
        }
        return multiplier == 1.0 ? globalcap : multiplier;
    }

    /**
     * Update {@link MovingData#boatIceVelocityTicks} based on leaving ice blocks.
     */
    private void updateBoatIceVelocityTicks(final VehicleMoveData move, final MovingData data) {
        if (move.from.onBlueIce && !move.to.onBlueIce) { // workaround for when the boat leaves icy places
            data.boatIceVelocityTicks = 20;
        } else if (move.from.onIce && !move.to.onIce) {
            data.boatIceVelocityTicks = 10;
        }
    }

    /**
     * Calculate the multiplier influenced by the surface the boat moves on.
     *
     * @return A computed multiplier or {@link Double#NaN} if none applies.
     */
    private double calcBoatTerrainMultiplier(final VehicleMoveData move, final MovingData data,
                                             final double multiplier) {
        if (move.from.onBlueIce || move.to.onBlueIce) {
            return multiplier * 4.1;
        }
        if (move.from.onIce || move.to.onIce) {
            return multiplier * 2.3;
        }
        if (data.boatIceVelocityTicks-- > 0) { // allow high speed for a moment
            return multiplier * (data.boatIceVelocityTicks > 10 ? 4.1 : 2.3);
        }
        if ((move.from.onGround && !move.from.inWater) || (move.to.onGround && !move.to.inWater)) {
            return multiplier * 0.4;
        }
        if (move.from.inWater || move.to.inWater) {
            return multiplier * 0.5;
        }
        return Double.NaN;
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

        if ((moveInfo.from.getBlockFlags() & BlockFlags.F_BUBBLECOLUMN) != 0) {
            data.timeVehicletoss = System.currentTimeMillis();
        }

        violation = violation || checkHorizontalSpeed(vehicle, thisMove, data, cc, debug);
        violation = violation || evaluateMediumState(thisMove, data, debug, vehicle, moveInfo);

        if (applyLevitationModifier(vehicle)) {
            violation = false;
        }

        violation = violation || checkVerticalLimits(thisMove, now, data);
        violation = violation || checkLiquidSpecialCases(vehicle, thisMove, data);

        if (!violation) {
            if (checkDetails.inAir) {
                data.sfJumpPhase++;
            }
            else {
                if (checkDetails.toIsSafeMedium) {
                    data.vehicleSetBacks.setSafeMediumEntry(thisMove.to);
                    data.sfJumpPhase = 0;
                }
                else if (checkDetails.fromIsSafeMedium) {
                    data.vehicleSetBacks.setSafeMediumEntry(thisMove.from);
                    data.sfJumpPhase = 0;
                }
                data.ws.resetConditions(WRPT.G_RESET_NOTINAIR);
            }
            data.vehicleSetBacks.setLastMoveEntry(thisMove.to);
        }

        return violation;
    }

    /**
     * Check horizontal movement speed limits.
     *
     * @param vehicle    the vehicle being moved
     * @param thisMove   current vehicle movement data
     * @param data       cached moving data
     * @param cc         moving configuration
     * @param debug      whether debug logging is active
     */
    private boolean checkHorizontalSpeed(final Entity vehicle, final VehicleMoveData thisMove,
                                         final MovingData data, final MovingConfig cc,
                                         final boolean debug) {
        boolean violation = false;
        double cap = getHDistCap(checkDetails.simplifiedType, cc, thisMove, data, debug);
        if (vehicle instanceof LivingEntity) {
            Double speed = PotionUtil.getPotionEffectAmplifier((LivingEntity) vehicle, PotionEffectType.SPEED);
            if (thisMove.isCamel) {
                VehicleMoveData firstPastMove = data.vehicleMoves.getFirstPastMove();
                double camelCap = cap;
                if (!Double.isInfinite(speed)) camelCap *= (1 + 0.2 * (speed + 1));
                if (thisMove.hDistance > camelCap) {
                    long current = System.currentTimeMillis();
                    if (data.timeCamelDash + 2000 < current) {
                        data.timeCamelDash = current;
                        violation = maxDistHorizontal(thisMove, camelCap * 2.7);
                    } else {
                        violation = maxDistHorizontal(thisMove, firstPastMove.toIsValid ? (firstPastMove.hDistance * 0.98) : camelCap);
                    }
                    return violation;
                }
            }
            if (!Double.isInfinite(speed)) {
                violation = maxDistHorizontal(thisMove, cap * (1 + 0.2 * (speed + 1)));
            } else {
                violation = maxDistHorizontal(thisMove, cap + (checkDetails.canJump ? 0.18 : 0.0));
            }
        } else {
            violation = maxDistHorizontal(thisMove, cap);
        }
        return violation;
    }

    private boolean evaluateMediumState(final VehicleMoveData thisMove, final MovingData data,
                                        final boolean debug, final Entity vehicle, final VehicleMoveInfo moveInfo) {
        boolean violation = false;
        if (thisMove.from.inWeb) {
            handleWebState(debug);
        }
        else if (checkDetails.canClimb && thisMove.from.onClimbable) {
            violation = violation || handleClimbableState(thisMove);
        }
        else if (checkDetails.canRails && thisMove.fromOnRails) {
            handleRailsState(thisMove);
        }
        else if (thisMove.from.inWater && thisMove.to.inWater) {
            handleWaterMovement(thisMove, data, debug);
        }
        else if (thisMove.from.onGround && thisMove.to.onGround) {
            handleGroundMovement(thisMove, debug);
        }
        else if (checkDetails.inAir) {
            violation = violation || handleInAirState(thisMove, data, debug, vehicle, moveInfo);
        }
        else {
            handleUnknownState(debug);
        }
        return violation;
    }

    private void handleWebState(final boolean debug) {
        if (debug) {
            debugDetails.add("");
        }
    }

    private boolean handleClimbableState(final VehicleMoveData thisMove) {
        checkDetails.checkAscendMuch = checkDetails.checkDescendMuch = false;
        if (Math.abs(thisMove.yDistance) > MagicVehicle.climbSpeed) {
            tags.add("climbspeed");
            return true;
        }
        return false;
    }

    private void handleRailsState(final VehicleMoveData thisMove) {
        if (Math.abs(thisMove.yDistance) < MagicVehicle.maxRailsVertical) {
            checkDetails.checkAscendMuch = checkDetails.checkDescendMuch = false;
        }
    }

    private void handleWaterMovement(final VehicleMoveData thisMove, final MovingData data, final boolean debug) {
        if (debug) {
            debugDetails.add("water-water");
        }
        if (MagicVehicle.oddInWater(thisMove, checkDetails, data)) {
            checkDetails.checkDescendMuch = checkDetails.checkAscendMuch = false;
        }
    }

    private void handleGroundMovement(final VehicleMoveData thisMove, final boolean debug) {
        if (checkDetails.canStepUpBlock && thisMove.yDistance > 0.0 && thisMove.yDistance <= 1.0) {
            checkDetails.checkAscendMuch = false;
            tags.add("step_up");
        }
        if (thisMove.from.onBlueIce && thisMove.to.onBlueIce) {
            if (debug) {
                debugDetails.add("blueIce-blueIce");
            }
        }
        else if (thisMove.from.onIce && thisMove.to.onIce) {
            if (debug) {
                debugDetails.add("ice-ice");
            }
        }
        else if (debug) {
            debugDetails.add("ground-ground");
        }
    }

    private boolean handleInAirState(final VehicleMoveData thisMove, final MovingData data, final boolean debug,
                                     final Entity vehicle, final VehicleMoveInfo moveInfo) {
        return checkInAir(thisMove, data, debug, vehicle, moveInfo);
    }

    private void handleUnknownState(final boolean debug) {
        if (debug) {
            debugDetails.add("?-?");
        }
    }

    private boolean applyLevitationModifier(final Entity vehicle) {
        if (vehicle instanceof LivingEntity) {
            Double levitation = Bridge1_9.getLevitationAmplifier((LivingEntity) vehicle);
            if (!Double.isInfinite(levitation)) {
                checkDetails.maxAscend += 0.046 * (levitation + 1);
                return true;
            }
        }
        return false;
    }

    private boolean checkVerticalLimits(final VehicleMoveData thisMove, final long now, final MovingData data) {
        boolean violation = false;
        if (checkDetails.checkAscendMuch && thisMove.yDistance > checkDetails.maxAscend) {
            tags.add("ascend_much");
            violation = true;
        }
        if (data.timeVehicletoss + 2000 > now && thisMove.yDistance < 4.0) {
            violation = false;
        }
        if (checkDetails.checkDescendMuch && thisMove.yDistance < -MagicVehicle.maxDescend) {
            tags.add("descend_much");
            violation = true;
        }
        return violation;
    }

    private boolean checkLiquidSpecialCases(final Entity vehicle, final VehicleMoveData thisMove,
                                            final MovingData data) {
        boolean violation = false;
        if (vehicle instanceof LivingEntity) {
            final VehicleMoveData firstPastMove = data.vehicleMoves.getFirstPastMove();
            if (thisMove.hDistance > 0.1D && thisMove.yDistance == 0D && !thisMove.to.onGround && !thisMove.from.onGround
                && firstPastMove.valid && firstPastMove.yDistance == 0D
                && thisMove.to.inLiquid && thisMove.from.inLiquid
                && !thisMove.headObstructed
                && !((strider != null && strider.isAssignableFrom(vehicle.getClass())) && thisMove.to.inLava && thisMove.from.inLava)) {
                violation = true;
                tags.add("liquidwalk");
            }
            Material blockUnder = vehicle.getLocation().subtract(0, 0.3, 0).getBlock().getType();
            Material blockAbove = vehicle.getLocation().add(0, 0.10, 0).getBlock().getType();
            if (blockUnder != null && blockAbove != null && BlockProperties.isAir(blockAbove)
                && BlockProperties.isLiquid(blockUnder) && !(strider != null && strider.isAssignableFrom(vehicle.getClass()))) {
                if (thisMove.hDistance > 0.11D && thisMove.yDistance <= 0.1D && !thisMove.to.onGround && !thisMove.from.onGround
                    && firstPastMove.valid && (Math.abs(firstPastMove.yDistance - thisMove.yDistance) < EPSILON
                    || Math.abs(firstPastMove.yDistance + thisMove.yDistance) < EPSILON)
                    && firstPastMove.yDistance != 0D
                    && !thisMove.headObstructed) {
                    if (!(thisMove.yDistance < 0 && thisMove.yDistance != 0 && firstPastMove.yDistance < 0 && firstPastMove.yDistance != 0)) {
                        violation = true;
                        tags.add("liquidmove");
                    }
                }
            }
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
        setBasicMediumState(thisMove);

        VehicleBehavior behavior = VehicleBehavior.resolve(vehicle, thisMove, this);
        behavior.apply(this, vehicle, moveInfo, thisMove);
        
        applyJumpSettings();
        applyClimbSettings(thisMove);
    }

    private void setBasicMediumState(final VehicleMoveData thisMove) {
        checkDetails.fromIsSafeMedium = thisMove.from.inWater || thisMove.from.onGround || thisMove.from.inWeb;
        checkDetails.toIsSafeMedium = thisMove.to.inWater || thisMove.to.onGround || thisMove.to.inWeb;
        checkDetails.inAir = !checkDetails.fromIsSafeMedium && !checkDetails.toIsSafeMedium;
    }

    private void applyBoatSettings() {
        checkDetails.simplifiedType = EntityType.BOAT;
        checkDetails.maxAscend = MagicVehicle.maxAscend;
    }

    private void applyMinecartSettings(final VehicleMoveInfo moveInfo, final VehicleMoveData thisMove) {
        checkDetails.simplifiedType = EntityType.MINECART;
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

    private void applyHorseSettings() {
        checkDetails.simplifiedType = EntityType.HORSE; // Consider using AbstractHorse from 1.11 onward.
        checkDetails.canJump = true;
        checkDetails.canStepUpBlock = true;
    }

    private void applyStriderSettings(final VehicleMoveData thisMove) {
        checkDetails.canJump = false;
        checkDetails.canStepUpBlock = true;
        checkDetails.canClimb = true;
        checkDetails.maxAscend = 1.1;
        if (thisMove.from.inLava || thisMove.to.inLava) {
            checkDetails.inAir = false;
        }
        if (!thisMove.from.onGround && thisMove.to.onGround) {
            checkDetails.gravityTargetSpeed = 0.07;
        }
    }

    private void applyCamelSettings() {
        checkDetails.simplifiedType = EntityType.CAMEL;
        checkDetails.canStepUpBlock = true;
        checkDetails.canClimb = false;
        checkDetails.canJump = false;
    }

    private void applyPigSettings() {
        checkDetails.simplifiedType = EntityType.PIG;
        checkDetails.canJump = false;
        checkDetails.canStepUpBlock = true;
        checkDetails.canClimb = true;
    }

    private void applyGenericSettings(final EntityType type) {
        checkDetails.simplifiedType = type;
    }

    private void applyJumpSettings() {
        if (checkDetails.canJump) {
            checkDetails.maxAscend = 1.2; // Coarse envelope. Actual lift off gain should be checked on demand.
        }
    }

    private void applyClimbSettings(final VehicleMoveData thisMove) {
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

    private boolean isHorse(final Entity vehicle) {
        return bestHorse != null && bestHorse.isAssignableFrom(vehicle.getClass());
    }

    private boolean isStrider(final Entity vehicle) {
        return strider != null && strider.isAssignableFrom(vehicle.getClass());
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

        boolean violation = isAscendingViolation(thisMove);

        // Absolute vertical distance to set back.
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

        violation = violation || evaluateFallSpeed(thisMove, data, debug, vehicle, from, minDescend, maxDescend);

        return violation;
    }

    private boolean isAscendingViolation(final VehicleMoveData thisMove) {
        boolean violation = false;
        if (!checkDetails.canJump && thisMove.yDistance > 0.0) {
            tags.add("ascend_at_all");
            violation = true;
        }
        return violation;
    }

    private boolean evaluateFallSpeed(final VehicleMoveData thisMove, final MovingData data,
                                      final boolean debug, final Entity vehicle,
                                      final RichEntityLocation from, final double minDescend,
                                      final double maxDescend) {
        boolean violation = false;
        if (data.sfJumpPhase > (checkDetails.canJump ? MagicVehicle.maxJumpPhaseAscend : 1)
            && thisMove.yDistance > Math.max(minDescend, -checkDetails.gravityTargetSpeed)) {
            boolean noViolation = ColliesHoneyBlock(from)
                    || (vehicle instanceof LivingEntity && !Double.isInfinite(Bridge1_13.getSlowfallingAmplifier((LivingEntity) vehicle)))
                    || !vehicle.hasGravity();
            if (ColliesHoneyBlock(from)) {
                data.sfJumpPhase = 5;
            }
            if (!noViolation) {
                tags.add("slow_fall_vdist");
                violation = true;
            }
        } else if (data.sfJumpPhase > 1 && thisMove.yDistance < maxDescend) {
            tags.add("fast_fall_vdist");
            violation = true;
        }
        if (violation) {
            if (MagicVehicle.oddInAir(thisMove, minDescend, maxDescend, checkDetails, data)) {
                violation = false;
                checkDetails.checkDescendMuch = checkDetails.checkAscendMuch = false;
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
