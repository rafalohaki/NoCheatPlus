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
package fr.neatmonster.nocheatplus.checks.moving.player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.Collection;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.World;
import org.bukkit.block.Block;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.combined.Improbable;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.magic.LostGround;
import fr.neatmonster.nocheatplus.checks.moving.magic.Magic;
import fr.neatmonster.nocheatplus.checks.moving.magic.MagicBunny;
import fr.neatmonster.nocheatplus.checks.moving.magic.AirWorkarounds;
import fr.neatmonster.nocheatplus.checks.moving.magic.LiquidWorkarounds;
import fr.neatmonster.nocheatplus.checks.moving.model.LiftOffEnvelope;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.util.AuxMoving;
import fr.neatmonster.nocheatplus.checks.moving.util.MovingUtil;
import fr.neatmonster.nocheatplus.checks.moving.velocity.VelocityFlags;
import fr.neatmonster.nocheatplus.checks.moving.velocity.SimpleEntry;
import fr.neatmonster.nocheatplus.checks.workaround.WRPT;
import fr.neatmonster.nocheatplus.compat.Bridge1_17;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.BridgeEnchant;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.compat.BridgePotionEffect;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker.Direction;
import fr.neatmonster.nocheatplus.components.modifier.IAttributeAccess;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;
import fr.neatmonster.nocheatplus.utilities.PotionUtil;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.ds.count.ActionAccumulator;
import fr.neatmonster.nocheatplus.utilities.collision.CollisionUtil;
import fr.neatmonster.nocheatplus.utilities.location.LocUtil;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.location.TrigUtil;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;

/**
 * The counterpart to the CreativeFly check. People that are not allowed to fly get checked by this. It will try to
 * identify when they are jumping, check if they aren't jumping too high or far, check if they aren't moving too fast on
 * normal ground, while sprinting, sneaking, swimming, etc.
 */
public class SurvivalFly extends Check {

    private final boolean ServerIsAtLeast1_9 = ServerVersion.compareMinecraftVersion("1.9") >= 0;
    private final boolean ServerIsAtLeast1_10 = ServerVersion.compareMinecraftVersion("1.10") >= 0;
    private final boolean ServerIsAtLeast1_13 = ServerVersion.compareMinecraftVersion("1.13") >= 0;
    /** Flag to indicate whether the buffer should be used for this move (only work inside setAllowedhDist). */
    private boolean bufferUse;
    /** To join some tags with moving check violations. */
    private final ArrayList<String> tags = new ArrayList<String>(15);
    private final ArrayList<String> justUsedWorkarounds = new ArrayList<String>();
    private final Set<String> reallySneaking = new HashSet<String>(30);
    /** For temporary use: LocUtil.clone before passing deeply, call setWorld(null) after use. */
    private final Location useLoc = new Location(null, 0, 0, 0);
    private final BlockChangeTracker blockChangeTracker;
    private final AuxMoving aux = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstance(AuxMoving.class);
    private IGenericInstanceHandle<IAttributeAccess> attributeAccess = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstanceHandle(IAttributeAccess.class);
    //private final Plugin plugin = Bukkit.getPluginManager().getPlugin("NoCheatPlus");

    /**
     * Helper structure for passing around state for allowed distance
     * calculations.
     */
    private static record AllowedDistanceContext(Player player, boolean sprinting,
            PlayerMoveData thisMove, MovingData data,
            MovingConfig cc, IPlayerData pData, PlayerLocation from,
            PlayerLocation to, boolean checkPermissions) {
    }

    /**
     * Helper structure for passing around modifiers during allowed distance
     * calculations.
     */
    private static class DistanceState {
        double allowed;
        double friction;
        boolean useBaseModifiers;
        boolean useBaseModifiersSprint;
        boolean useSneakModifier;

        DistanceState(final double allowed, final double friction,
                final boolean useBaseModifiers,
                final boolean useBaseModifiersSprint,
                final boolean useSneakModifier) {
            this.allowed = allowed;
            this.friction = friction;
            this.useBaseModifiers = useBaseModifiers;
            this.useBaseModifiersSprint = useBaseModifiersSprint;
            this.useSneakModifier = useSneakModifier;
        }
    }

    /** Validate player movement parameters and log if invalid. */
    private boolean validateMoveInputs(final Player player, final PlayerLocation from,
                                       final PlayerLocation to, final String method) {
        if (player == null || from == null || to == null) {
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().warning(
                    Streams.STATUS,
                    CheckUtils.getLogMessagePrefix(player, type)
                            + method + ": null arguments.");
            return false;
        }
        return true;
    }

    /**
     * Some note for mcbe compatibility:
     * - New step pattern 0.42-0.58-0.001 ?
     * - Maximum step height 0.75 ?
     * - Ladder descends speed 0.2
     * - Jump on grass_path blocks will result in jump height 0.42 + 0.0625
     *   but next move friction still base on 0.42 ( not sure this does happen
     *   on others )
     * - honey block: yDistance < -0.118 && yDistance > -0.128 ?
     */

    /**
     * Instantiates a new survival fly check.
     */
    public SurvivalFly() {
        super(CheckType.MOVING_SURVIVALFLY);
        blockChangeTracker = NCPAPIProvider.getNoCheatPlusAPI().getBlockChangeTracker();
    }


    /**
     * Checks a player
     * @param player
     * @param from
     * @param to
     * @param multiMoveCount
     *            0: Ordinary, 1/2: first/second of a split move.
     * @param data
     * @param cc
     * @param tick
     * @param now
     * @param useBlockChangeTracker
     * @return
     */
    public Location check(final Player player, final PlayerLocation from, final PlayerLocation to,
                          final int multiMoveCount,
                          final MovingData data, final MovingConfig cc, final IPlayerData pData,
                          final int tick, final long now, final boolean useBlockChangeTracker) {

        if (!validateMoveInputs(player, from, to, "check") || data == null || cc == null || pData == null) {
            return null;
        }

        tags.clear();
        // Shortcuts:
        final boolean debug = pData.isDebugActive(type);
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final boolean isSamePos = from.isSamePos(to);
        final double xDistance, yDistance, zDistance, hDistance;
        final boolean HasHorizontalDistance;
        final boolean fromOnGround = thisMove.from.onGround;
        final boolean toOnGround = thisMove.to.onGround || useBlockChangeTracker && toOnGroundPastStates(from, to, thisMove, tick, data, cc);
        final boolean resetTo = toOnGround || to.isResetCond();

        if (debug) {
            justUsedWorkarounds.clear();
            data.ws.setJustUsedIds(justUsedWorkarounds);
        }

        // Calculate some distances.
        if (isSamePos) {
            xDistance = yDistance = zDistance = hDistance = 0.0;
            HasHorizontalDistance = false;
        }
        else {
            xDistance = to.getX() - from.getX();
            yDistance = thisMove.yDistance;
            zDistance = to.getZ() - from.getZ();
            if (xDistance == 0.0 && zDistance == 0.0) {
                hDistance = 0.0;
                HasHorizontalDistance = false;
            }
            else {
                HasHorizontalDistance = true;
                hDistance = thisMove.hDistance;
            }
        }

        // Recover from data removal (somewhat random insertion point).
        if (data.liftOffEnvelope == LiftOffEnvelope.UNKNOWN) {
            data.adjustMediumProperties(from);
        }

        // Determine if the player is actually sprinting.
        final boolean sprinting = determineSprintingState(from, to, fromOnGround, toOnGround,
                resetTo, hDistance, now, data, cc, tags);

        // Use the player-specific walk speed.
        thisMove.walkSpeed = Magic.WALK_SPEED * ((double) data.walkSpeed / Magic.DEFAULT_WALKSPEED);

        data.setNextFriction(thisMove);



        ////////////////////////////////////
        // Mixed checks (lost ground)    ///
        ////////////////////////////////////
        final boolean resetFrom;
        if (fromOnGround || from.isResetCond()) {
            resetFrom = true;
        }
        else if (isSamePos) {

            if (useBlockChangeTracker && from.isOnGroundOpportune(cc.yOnGround, 0L, blockChangeTracker, data.blockChangeRef, tick)) {
                resetFrom = true;
                tags.add("pastground_from");
            }
            else if (lastMove.toIsValid) {
                // Note that to is not on ground either.
                resetFrom = LostGround.lostGroundStill(player, from, to, hDistance, yDistance, sprinting, lastMove, data, cc, tags);
            }
            else resetFrom = false;
        }
        // Check lost-ground workarounds.
        else resetFrom = LostGround.lostGround(player, from, to, hDistance, yDistance, sprinting, lastMove, data, cc, useBlockChangeTracker ? blockChangeTracker : null, tags);

        if (thisMove.touchedGround) {
            // Lost ground workaround has just been applied, check resetting of the dirty flag.
            if (!thisMove.from.onGround && !thisMove.to.onGround) {
                data.resetVelocityJumpPhase(tags);
            }
            // Ground somehow appeared out of thin air (block place).
            else if (multiMoveCount == 0 && thisMove.from.onGround && Magic.inAir(lastMove)
                    && TrigUtil.isSamePosAndLook(thisMove.from, lastMove.to)) {
                data.setSetBack(from);
                // Schedule a no low jump flag because the setback update will then cause a low-jump with the subsequent descending phase
                data.sfNoLowJump = true;
                if (debug) {
                    debug(player, "Ground appeared due to a block-place: schedule sfNoLowJump and adjust set-back location.");
                }
            }
        }

        // Renew the "dirty"-flag (in-air phase affected by velocity).
        // (Reset is done after checks run.)
        if (data.isVelocityJumpPhase() || data.resetVelocityJumpPhase(tags)) {
            tags.add("dirty");
        }

        // HACK: Force sfNoLowJump by a flag.
        if ((from.getBlockFlags() & BlockFlags.F_ALLOW_LOWJUMP) != 0) {
            data.sfNoLowJump = true;
        }

        // Alter some data before checking anything
        setHorVerDataExAnte(thisMove, from, to, data, yDistance, pData, player, cc, xDistance, zDistance);




        /////////////////////////////////////
        // Horizontal move                ///
        /////////////////////////////////////
        bufferUse = true;
        double hAllowedDistance = 0.0, hDistanceAboveLimit = 0.0, hFreedom = 0.0;

        // Run through all hDistance checks if the player has actually some horizontal distance
        if (HasHorizontalDistance) {
            final double attrMod = attributeAccess.getHandle().getSpeedAttributeMultiplier(player);
            // Set the allowed distance and determine the distance above limit
            hAllowedDistance = setAllowedhDist(new AllowedDistanceContext(player, sprinting, thisMove,
                    data, cc, pData, from, to, true));
            hDistanceAboveLimit = hDistance - hAllowedDistance;
            // The player went beyond the allowed limit, execute the after failure checks.
            if (hDistanceAboveLimit > 0.0) {
                final double[] resultH = hDistAfterFailure(player, from, to, hAllowedDistance, hDistanceAboveLimit,
                                                           sprinting, thisMove, lastMove, data, cc, pData, false);
                hAllowedDistance = resultH[0];
                hDistanceAboveLimit = resultH[1];
                hFreedom = resultH[2];
            }
            // Clear active velocity if the distance is within limit (clearly not needed. :))2
            else {
                data.clearActiveHorVel();
                hFreedom = 0.0;
                // Distance is within limit and the player is coming from ground after a not too recent bunnyhop, do reset the delay
                if (resetFrom && data.bunnyhopDelay <= 6) {
                    data.bunnyhopDelay = 0;
                }
            }

            // The hacc subcheck: monitor medium term speed (if enabled, always update)
            if (cc.survivalFlyAccountingH) {
                hDistanceAboveLimit = horizontalAccounting(data, hDistance, hDistanceAboveLimit, thisMove, from);
            }

            // Prevent players from walking on a liquid in a too simple way.
            if (!pData.hasPermission(Permissions.MOVING_SURVIVALFLY_WATERWALK, player)) {
                hDistanceAboveLimit = waterWalkChecks(data, player, hDistance, yDistance, thisMove, lastMove,
                                                     fromOnGround, hDistanceAboveLimit, toOnGround, from, to);
            }

            // vDistSBLow: monitor setback distances and check if they are too low.
            if (cc.survivalFlyAccountingStep && !pData.hasPermission(Permissions.MOVING_SURVIVALFLY_STEP, player)) {
                hDistanceAboveLimit = vDistSBLow(yDistance, hDistanceAboveLimit, hDistance, player, cc, data, thisMove, lastMove, pData, to);
            }

            // Prevent players from illegally sprinting.
            //if (!pData.hasPermission(Permissions.MOVING_SURVIVALFLY_SPRINTING, player)){
            //    hDistanceAboveLimit = sprintingChecks(sprinting, data, player, hDistance, hDistanceAboveLimit, thisMove,
            //                                          xDistance, zDistance, from);
            //}

        }
        // No horizontal distance present
        else {
            // Prevent way too easy abuse by simply collecting queued entries while standing still with no-knockback on. (Experimental, likely too strict)
            if (cc.velocityStrictInvalidation && lastMove.hAllowedDistanceBase == 0.0
                && data.hasQueuedHorVel()) {
                data.clearAllHorVel();
                hFreedom = 0.0;
            }
            // Always clear active velocity, regardless of velocityStrictInvalidation.
            data.clearActiveHorVel();
            thisMove.hAllowedDistanceBase = 0.0;
            thisMove.hAllowedDistance = 0.0;
        }
        // Adjust some data after horizontal checking but before vertical
        data.setHorDataExPost();



        /////////////////////////////////////
        // Vertical move                  ///
        /////////////////////////////////////
        final double[] vResult = computeVerticalMovement(player, from, to,
                thisMove, lastMove, fromOnGround, toOnGround, resetFrom, resetTo,
                yDistance, hDistanceAboveLimit, now, multiMoveCount, data, cc, pData);
        double vAllowedDistance = vResult[0];
        double vDistanceAboveLimit = vResult[1];



        //////////////////////////////////////////////////////////////////////
        // Vertical push/pull. (Horizontal is done in hDistanceAfterFailure)//
        //////////////////////////////////////////////////////////////////////
        if (useBlockChangeTracker && vDistanceAboveLimit > 0.0) {
            double[] blockMoveResult = getVerticalBlockMoveResult(yDistance, from, to, tick, data);
            if (blockMoveResult != null) {
                vAllowedDistance = blockMoveResult[0];
                vDistanceAboveLimit = blockMoveResult[1];
            }
        }



        ////////////////////////////
        // Debug output.          //
        ////////////////////////////
        final int tagsLength;
        if (debug) {
            outputDebug(player, to, data, cc, hDistance, hAllowedDistance, hFreedom,
                        yDistance, vAllowedDistance, fromOnGround, resetFrom, toOnGround,
                        resetTo, thisMove, vDistanceAboveLimit);
            tagsLength = tags.size();
            data.ws.setJustUsedIds(null);
        }
        else tagsLength = 0; // JIT vs. IDE.



        //////////////////////////////////////
        // Handle violations               ///
        //////////////////////////////////////
        final boolean inAir = Magic.inAir(thisMove);
        final double result = (Math.max(hDistanceAboveLimit, 0D) + Math.max(vDistanceAboveLimit, 0D)) * 100D;
        if (result > 0D) {

            final Location vLoc = handleViolation(now, Double.isInfinite(result) ? 30.0 : result, player, from, to, data, cc);
            if (inAir) {
                data.sfVLInAir = true;
            }
            // Request a new to-location
            if (vLoc != null) {
                return vLoc;
            }
        }
        else {
            // Slowly reduce the level with each event, if violations have not recently happened.
            if (data.getPlayerMoveCount() - data.sfVLMoveCount > cc.survivalFlyVLFreezeCount
                && (!cc.survivalFlyVLFreezeInAir || !inAir
                    // Favor bunny-hopping slightly: clean descend.
                    || !data.sfVLInAir
                    && data.liftOffEnvelope == LiftOffEnvelope.NORMAL
                    && lastMove.toIsValid
                    && lastMove.yDistance < -Magic.GRAVITY_MIN
                    && thisMove.yDistance - lastMove.yDistance < -Magic.GRAVITY_MIN)) {
                // Relax VL.
                data.survivalFlyVL *= 0.95;
                // Finally check horizontal buffer regain.
                if (hDistanceAboveLimit < 0.0 && result <= 0.0 && !isSamePos && data.sfHorizontalBuffer < cc.hBufMax
                    && !data.sfLowJump) {
                    hBufRegain(hDistance, Math.min(0.2, Math.abs(hDistanceAboveLimit)), data, cc);
                }
            }
        }



        //////////////////////////////////////////////////////////////////////////////////////////////
        //  Set data for normal move or violation without cancel (cancel would have returned above) //
        //////////////////////////////////////////////////////////////////////////////////////////////
        // 1: Adjust lift off envelope to medium
        // NOTE: isNextToGround(0.15, 0.4) allows a little much (yMargin), but reduces false positives.
        // Related commit: https://github.com/NoCheatPlus/NoCheatPlus/commit/c8ac66de2c94ac9f70f29c350054dd7896cd8646#diff-b8df089e2a4295e12695420f6066320d96119aa12c80e1c64efcb959f089db2d
        final LiftOffEnvelope oldLiftOffEnvelope = data.liftOffEnvelope;
        final Material blockUnder = from.getTypeId(from.getBlockX(), Location.locToBlock(from.getY() - cc.sfStepHeight), from.getBlockZ());
        if (thisMove.to.inLiquid) {
            if (fromOnGround && !toOnGround && data.liftOffEnvelope == LiftOffEnvelope.NORMAL
                && data.sfJumpPhase <= 0 && !thisMove.from.inLiquid) {
                // KEEP
            }
            // Moving in liquid near ground, weak/no jump limit.
            else if (to.isNextToGround(0.15, 0.2)) {
                data.liftOffEnvelope = LiftOffEnvelope.LIMIT_NEAR_GROUND;
            }
            // Minecraft 1.13 allows players to swim up to the surface and have two consecutive in-air moves with higher jump height.
            // (Moving near ground takes precedence)
            else if (Magic.inAir(lastMove) && Magic.intoWater(thisMove) && data.liftOffEnvelope == LiftOffEnvelope.LIMIT_SURFACE
                    && BlockProperties.isAir(to.getTypeIdAbove()) && !thisMove.headObstructed
                    && !thisMove.inWaterfall) {
                // KEEP
            }
            // Fallback to default liquid lift-off limit.
            else data.liftOffEnvelope = LiftOffEnvelope.LIMIT_LIQUID;
        }
        else if (thisMove.to.inPowderSnow) {
            data.liftOffEnvelope = LiftOffEnvelope.POWDER_SNOW;
        }
        else if (thisMove.to.inWeb) {
            data.liftOffEnvelope = LiftOffEnvelope.NO_JUMP;
        }
        else if (thisMove.to.inBerryBush) {
            data.liftOffEnvelope = LiftOffEnvelope.BERRY_JUMP;
        }
        else if (thisMove.to.onHoneyBlock) {
            data.liftOffEnvelope = LiftOffEnvelope.HALF_JUMP;
        }
        else if (resetTo) {
            data.liftOffEnvelope = LiftOffEnvelope.NORMAL;
        }
        else if (thisMove.from.inLiquid) {
            if (!resetTo && data.liftOffEnvelope == LiftOffEnvelope.NORMAL && data.sfJumpPhase <= 0) {
                // KEEP
            }
            // Moving in liquid near ground, weak/no jump limit.
            else if (to.isNextToGround(0.15, 0.2)) {
                data.liftOffEnvelope = LiftOffEnvelope.LIMIT_NEAR_GROUND;
            }
            // Minecraft 1.13 allows players to swim up to the surface and have two consecutive in-air moves.
            // (Moving near ground takes precedence)
            else if (Magic.inWater(lastMove) && Magic.leavingWater(thisMove)
                    // && BlockProperties.isLiquid(blockUnder)
                    && !thisMove.headObstructed && !Magic.recentlyInWaterfall(data, 10)) {// && BlockProperties.isAir(from.getTypeIdAbove())
                data.liftOffEnvelope = LiftOffEnvelope.LIMIT_SURFACE;
            }
            // Fallback to default liquid lift-off limit.
            else data.liftOffEnvelope = LiftOffEnvelope.LIMIT_LIQUID;

        }
        else if (thisMove.from.inPowderSnow) {
            data.liftOffEnvelope = LiftOffEnvelope.POWDER_SNOW;
        }
        else if (thisMove.from.inWeb) {
            data.liftOffEnvelope = LiftOffEnvelope.NO_JUMP;
        }
        else if (thisMove.from.inBerryBush) {
            data.liftOffEnvelope = LiftOffEnvelope.BERRY_JUMP;
        }
        else if (thisMove.from.onHoneyBlock) {
            data.liftOffEnvelope = LiftOffEnvelope.HALF_JUMP;
        }
        else if (resetFrom || thisMove.touchedGround) {
            data.liftOffEnvelope = LiftOffEnvelope.NORMAL;
        }
        else {
            // Air, Keep medium.
        }

        // 2: Count how long one is moving inside of a medium.
        if (oldLiftOffEnvelope != data.liftOffEnvelope) {
            data.insideMediumCount = 0;
            data.clearHAccounting();
        }
        else if (!resetFrom || !resetTo) {
            data.insideMediumCount = 0;
        }
        else data.insideMediumCount ++;

        // 3: Apply reset conditions.
        if (resetTo) {
            // The player has moved onto ground.
            if (toOnGround) {
                // Moving onto ground but still ascending (jumping next to a block).
                if (yDistance > 0.0 && to.getY() > data.getSetBackY() + 0.13 // 0.15 ?
                    && !from.isResetCond() && !to.isResetCond()) {

                    // Too early abort, remember when the delay was reset (see MagicBunny.bunnyHop)
                    if (data.bunnyhopDelay > 0) {
                        if (data.bunnyhopDelay > 6) {
                            data.lastbunnyhopDelay = data.bunnyhopDelay;
                        }
                        data.bunnyhopDelay = 0;
                    }
                    // Schedule a no low jump flag, because this low descending phase is legit
                    data.sfNoLowJump = true;
                    if (debug) {
                        debug(player, "Slope: schedule sfNoLowJump and reset bunnyfly.");
                    }
                }
                // Ordinary
                else data.sfNoLowJump = false;
            }
            // Lost ground or reset condition
            else data.sfNoLowJump = false;
            // Reset data.
            data.setSetBack(to);
            data.sfJumpPhase = 0;
            data.clearAccounting();
            if (data.sfLowJump && resetFrom) {
                // Prevent reset if coming from air (purpose of the flag).
                data.sfLowJump = false;
            }
            if (hFreedom <= 0.0 && thisMove.verVelUsed == null) {
                data.resetVelocityJumpPhase(tags);
            }
        }
        // The player moved from ground.
        else if (resetFrom) {
            // Keep old setback if coming from a 1 block high slope.
            data.setSetBack(from);
            data.sfJumpPhase = 1; // This event is already in air.
            data.clearAccounting();
            data.sfLowJump = false;
        }
        else {
            data.sfJumpPhase ++;
            if (to.getY() < 0.0 && cc.sfSetBackPolicyVoid) {
                data.setSetBack(to);
            }
        }

        // 4: Adjust in-air counters.
        if (inAir) {
            if (yDistance == 0.0) {
                data.sfZeroVdistRepeat ++;
            }
            else data.sfZeroVdistRepeat = 0;
        }
        else {
            data.sfZeroVdistRepeat = 0;
            data.ws.resetConditions(WRPT.G_RESET_NOTINAIR);
            data.sfVLInAir = false;
        }

        // 5: Horizontal velocity invalidation.
        if (hDistance <= (cc.velocityStrictInvalidation ? thisMove.hAllowedDistanceBase : thisMove.hAllowedDistanceBase / 2.0)) {
            data.clearActiveHorVel();
        }

        // 6: Update unused velocity tracking.
        if (debug) {
            data.getVerticalVelocityTracker().updateBlockedState(tick,
                    // Assume blocked with being in web/water, despite not entirely correct.
                    thisMove.headObstructed || thisMove.from.resetCond,
                    // (Similar here.)
                    thisMove.touchedGround || thisMove.to.resetCond);
            UnusedVelocity.checkUnusedVelocity(player, type, data, cc);
        }

        // 7: Adjust friction.
        data.lastFrictionHorizontal = data.nextFrictionHorizontal;
        data.lastFrictionVertical = data.nextFrictionVertical;

        // 8: Log tags added after violation handling.
        if (debug && tags.size() > tagsLength) {
            logPostViolationTags(player);
        }
        // Nothing to do, newTo (MovingListener) stays null
        return null;
    }






    /**
     * A check to prevent players from bed-flying.
     * (This increases VL and sets tag only. Setback is done in MovingListener).
     *
     * @param player
     *            the player
     * @return If to prevent action (use the set back location of survivalfly).
     */
    public boolean checkBed(final Player player, final IPlayerData pData, final MovingConfig cc, final MovingData data) {
        if (player == null || pData == null || cc == null || data == null) {
            validateMoveInputs(player, null, null, "checkBed");
            return false;
        }

        boolean cancel = false;
        // Check if the player had been in bed at all.
        if (!data.wasInBed) {
            // Violation ...
            tags.add("bedfly");
            data.survivalFlyVL += 100D;
            final ViolationData vd = new ViolationData(this, player, data.survivalFlyVL, 100D, cc.survivalFlyActions);
            if (vd.needsParameters()) vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
            cancel = executeActions(vd).willCancel();
        }
        // Nothing detected.
        else data.wasInBed = false;
        return cancel;
    }


   /**
    * The horizontal accounting subcheck:
    * It monitors average combined-medium (e.g. air+ground or air+water) speed, with a rather simple bucket(s)-overflow mechanism.
    * We feed 1.0 whenever we're below the allowed BASE speed, and (actual / base) if we're above.
    * (hAllowedDistanceBase is about what a player can run at without using special techniques like extra jumping,
    * not necessarily the finally allowed speed).
    *
    * @return hDistanceAboveLimit
    */
    private double horizontalAccounting(final MovingData data, double hDistance, double hDistanceAboveLimit, final PlayerMoveData thisMove, final PlayerLocation from) {

        /** Final combined-medium horizontal value */
        final double fcmhv = Math.max(1.0, Math.min(10.0, thisMove.hDistance / thisMove.hAllowedDistanceBase));
        final boolean movingBackwards = TrigUtil.isMovingBackwards(thisMove.to.getX()-thisMove.from.getX(), thisMove.to.getZ()-thisMove.from.getZ(), LocUtil.correctYaw(from.getYaw()));
        // With each horizontal move, add the calculated value to the bucket.
        data.hDistAcc.add((float) fcmhv);

        // We have enough events.
        if (data.hDistAcc.count() > 30) {

            // Get the ratio between: accumulated value / total events counted
            // (Currently, only 1 bucket is present, so it shouldn't matter using score instead of bucketScore)
            final double accValue = (double) data.hDistAcc.score() / data.hDistAcc.count();
            final double limit;
            if (data.liftOffEnvelope == LiftOffEnvelope.NORMAL) {
                limit = from.isInWaterLogged() ? 1.1 : movingBackwards ? 1.15 : 1.34;
            }
            else if (data.liftOffEnvelope == LiftOffEnvelope.LIMIT_LIQUID
                    || data.liftOffEnvelope == LiftOffEnvelope.LIMIT_NEAR_GROUND) {
                // 1.8.8 in-water moves with jumping near/on surface. 1.2 is max factor for one move (!).
                limit = ServerIsAtLeast1_10 ? 1.05 : 1.1;
            }
            else if (data.liftOffEnvelope == LiftOffEnvelope.POWDER_SNOW) {
                limit = 1.047;
            }
            else if (data.liftOffEnvelope == LiftOffEnvelope.BERRY_JUMP) {
                limit = 1.057;
            }
            else limit = 1.0;

            // Violation
            if (accValue > limit) {
                hDistanceAboveLimit = Math.max(hDistanceAboveLimit, hDistance);
                bufferUse = false;
                tags.add("hacc("+ StringUtil.fdec3.format(accValue) +"/" + limit +")");
                // Reset for now.
                data.clearHAccounting();
            }
            else {
                // Clear and add
                data.clearHAccounting();
                data.hDistAcc.add((float) fcmhv);
            }
        }
        return hDistanceAboveLimit;
    }


    /**
     * The vDistSBLow subcheck: after taking a height difference, ensure setback distance did decrease properly.
     * (Taken out of vDistAir because this checks ex-post, once the player is on ground. Also the reason why we invalidate hDistance and not yDistance, despite it being tied to vertical checking, rather)
     * @return
     */
    private double vDistSBLow(final double yDistance, double hDistanceAboveLimit, final double hDistance, final Player player, final MovingConfig cc,
                              final MovingData data, final PlayerMoveData thisMove, final PlayerMoveData lastMove, final IPlayerData pData,
                              final PlayerLocation to) {

        final PlayerMoveData pastMove2 = data.playerMoves.getSecondPastMove();
        final PlayerMoveData pastMove3 = data.playerMoves.getThirdPastMove();
        final PlayerMoveData pastMove4 = data.playerMoves.getPastMove(3);
        final double minJumpGain = data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier);
        final double jumpGainMargin = 0.005;

        if (!data.isVelocityJumpPhase() && data.hasSetBack() && !(thisMove.from.aboveStairs || lastMove.from.aboveStairs)) {

            // Monitor setback distance between this and the last 9 moves.
            if (Magic.getPastLiftOffAvailable(10, data) && data.liftOffEnvelope == LiftOffEnvelope.NORMAL) { // Currently, only for normal env.

                if (
                    // 0: The usual case
                    thisMove.setBackYDistance < data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier)
                    && (
                        lastMove.setBackYDistance == thisMove.setBackYDistance
                        || lastMove.setBackYDistance - pastMove2.setBackYDistance < minJumpGain / 1.7
                    )
                    && pastMove2.setBackYDistance > pastMove3.setBackYDistance && pastMove3.setBackYDistance <= minJumpGain + jumpGainMargin
                    && pastMove3.setBackYDistance >= minJumpGain - (Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN)
                    // 0: Too little dropoff
                    || thisMove.setBackYDistance == 0.0 && lastMove.setBackYDistance < data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier)
                    && pastMove2.setBackYDistance > lastMove.setBackYDistance && pastMove2.setBackYDistance - lastMove.setBackYDistance < jumpGainMargin
                    // 0: Sharp distance dropoff
                    // (Not observed nor tested though. This is just an educated guess.)
                    || thisMove.setBackYDistance > 0.0 && lastMove.yDistance > 0.0 && yDistance <= 0.0
                    && thisMove.setBackYDistance < data.liftOffEnvelope.getMinJumpHeight(data.jumpAmplifier)) {

                    // If the player has been on ground for 1 event and setback distance didn't decrease properly, flag. (Assume everything else gets caught by vDistRel and vDistSB)
                    if (!lastMove.from.onGround && lastMove.to.onGround && thisMove.to.onGround) { // from.onGround is ignored on purpose.
                        if (data.getOrUseVerticalVelocity(yDistance) == null) {
                            hDistanceAboveLimit = Math.max(hDistanceAboveLimit, hDistance);
                            tags.add("lowvdistsb");
                        }
                    }
                }
            }
        }
        return hDistanceAboveLimit;
    }


   /**
    * Catch rather simple waterwalk cheat types.
    * Do note that the speed for moving on the surface is restricted anyway in setAllowedhDist (in case these methods get bypassed).
    *
    * @return hDistanceAboveLimit
    */
    private double waterWalkChecks(final MovingData data, final Player player, double hDistance, double yDistance,
                                   final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                   final boolean fromOnGround, double hDistanceAboveLimit,
                                   final boolean toOnGround, final PlayerLocation from, final PlayerLocation to) {

        Material blockUnder = from.getTypeId(from.getBlockX(), Location.locToBlock(from.getY() - 0.3), from.getBlockZ());
        Material blockAbove = from.getTypeId(from.getBlockX(), Location.locToBlock(from.getY() + 0.1), from.getBlockZ());

        // Checks for no gravity when moving in a liquid
        if (hDistanceAboveLimit <= 0.0 && yDistance == 0.0 && lastMove.yDistance == 0.0 && lastMove.toIsValid
            && hDistance > 0.090 && lastMove.hDistance > 0.090 // Do not check lower speeds. The cheat would be purely cosmetic at that point, it wouldn't offer any advantage.
            && BlockProperties.isLiquid(to.getTypeId())
            && BlockProperties.isLiquid(from.getTypeId())
            && !toOnGround && !fromOnGround
            && !from.isHeadObstructed() && !to.isHeadObstructed()
            && !Bridge1_13.isSwimming(player)) {
            hDistanceAboveLimit = Math.max(hDistanceAboveLimit, hDistance);
            bufferUse = false;
            tags.add("liquidwalk");
        }

        // Checks for micro y deltas when moving above liquid.
        if (blockUnder != null && BlockProperties.isLiquid(blockUnder) && BlockProperties.isAir(blockAbove)) {

            if (hDistanceAboveLimit <= 0.0
                && hDistance > 0.11 && yDistance <= LiftOffEnvelope.LIMIT_LIQUID.getMaxJumpGain(0.0)
                && !toOnGround && !fromOnGround
                && lastMove.toIsValid && lastMove.yDistance == yDistance
                || lastMove.yDistance == yDistance * -1 && lastMove.yDistance != 0.0
                && !from.isHeadObstructed() && !to.isHeadObstructed()
                && !Bridge1_13.isSwimming(player)) {

                // Prevent being flagged if a player transitions from a block to water and the player falls into the water.
                if (!(yDistance < 0.0 && yDistance != 0.0 && lastMove.yDistance < 0.0 && lastMove.yDistance != 0.0)) {
                    hDistanceAboveLimit = Math.max(hDistanceAboveLimit, hDistance);
                    bufferUse = false;
                    tags.add("liquidmove");
                }
            }
        }
        return hDistanceAboveLimit;
    }


    /**
     * Compute allowances and violations for vertical movement.
     *
     * @return array containing the allowed distance and the distance above the limit
     */
    private double[] computeVerticalMovement(final Player player, final PlayerLocation from,
                                             final PlayerLocation to, final PlayerMoveData thisMove,
                                             final PlayerMoveData lastMove, final boolean fromOnGround,
                                             final boolean toOnGround, final boolean resetFrom,
                                             final boolean resetTo, final double yDistance,
                                             final double hDistanceAboveLimit, final long now,
                                             final int multiMoveCount, final MovingData data,
                                             final MovingConfig cc, final IPlayerData pData) {

        if (!validateMoveInputs(player, from, to, "computeVerticalMovement") ||
                thisMove == null || lastMove == null || data == null || cc == null || pData == null) {
            return new double[]{0.0, 0.0};
        }

        double vAllowedDistance = 0.0;
        double vDistanceAboveLimit = 0.0;

        if (yDistance >= 0.0 && yDistance <= cc.sfStepHeight
                && toOnGround && fromOnGround && !from.isResetCond()) {
            vAllowedDistance = cc.sfStepHeight;
            thisMove.allowstep = true;
            tags.add("groundstep");
        }
        else if (from.isInPowderSnow()) {
            final double[] resultSnow = vDistPowderSnow(yDistance, from, to, cc, data, player);
            vAllowedDistance = resultSnow[0];
            vDistanceAboveLimit = resultSnow[1];
        }
        else if (from.isOnClimbable()) {
            final double[] resultClimbable = vDistClimbable(player, from, to, fromOnGround, toOnGround,
                    thisMove, lastMove, yDistance, data);
            vAllowedDistance = resultClimbable[0];
            vDistanceAboveLimit = resultClimbable[1];
        }
        else if (from.isInWeb()) {
            final double[] resultWeb = vDistWeb(player, thisMove, toOnGround, hDistanceAboveLimit,
                    now, data, cc, from);
            vAllowedDistance = resultWeb[0];
            vDistanceAboveLimit = resultWeb[1];
        }
        else if (from.isInBerryBush()) {
            final double[] resultBush = vDistBush(player, thisMove, toOnGround, hDistanceAboveLimit,
                    now, data, cc, from, fromOnGround);
            vAllowedDistance = resultBush[0];
            vDistanceAboveLimit = resultBush[1];
        }
        else if (from.isOnHoneyBlock()) {
            vAllowedDistance = data.liftOffEnvelope.getMaxJumpGain(data.jumpAmplifier);
            if (data.getOrUseVerticalVelocity(thisMove.yDistance) == null) {
                vDistanceAboveLimit = yDistance - vAllowedDistance;
                if (vDistanceAboveLimit > 0.0) {
                    tags.add("honeyasc");
                }
            }
        }
        else if (from.isInLiquid()) {
            final double[] resultLiquid = vDistLiquid(thisMove, from, to, toOnGround, yDistance,
                    lastMove, data, player, cc);
            vAllowedDistance = resultLiquid[0];
            vDistanceAboveLimit = resultLiquid[1];

            if (vDistanceAboveLimit <= 0.0 && yDistance > 0.0
                    && Math.abs(yDistance) > Magic.swimBaseSpeedV(Bridge1_13.isSwimming(player))) {
                data.setFrictionJumpPhase();
            }
        }
        else {
            final double[] resultAir = vDistAir(now, player, from, fromOnGround, resetFrom,
                    to, toOnGround, resetTo, hDistanceAboveLimit, yDistance,
                    multiMoveCount, lastMove, data, cc, pData);
            vAllowedDistance = resultAir[0];
            vDistanceAboveLimit = resultAir[1];
        }

        return new double[]{vAllowedDistance, vDistanceAboveLimit};
    }


   /**
    * Check for toOnGround past states
    *
    * @param from
    * @param to
    * @param thisMove
    * @param tick
    * @param data
    * @param cc
    * @return
    */
    private boolean toOnGroundPastStates(final PlayerLocation from, final PlayerLocation to,
                                         final PlayerMoveData thisMove, int tick,
                                         final MovingData data, final MovingConfig cc) {

        if (to.isOnGroundOpportune(cc.yOnGround, 0L, blockChangeTracker, data.blockChangeRef, tick)) {
            tags.add("pastground_to");
            return true;
        }
        else {
            return false;
        }
    }


    /**
     * Data to be set/adjusted before checking (h/v).
     *
     * @param thisMove
     * @param from
     * @param to
     * @param data
     * @param pData
     * @param player
     * @param cc
     */
    private void setHorVerDataExAnte(final PlayerMoveData thisMove, final PlayerLocation from, final PlayerLocation to, final MovingData data, final double yDistance,
                                     final IPlayerData pData, final Player player, final MovingConfig cc, final double xDistance, final double zDistance) {

        decreaseBunnyhopCounters(data);
        updateSwimmingFlags(thisMove, from, xDistance, zDistance, yDistance);
        // Get the distance to set-back.
        thisMove.setBackYDistance = to.getY() - data.getSetBackY();

        // Reset no slow hop
        resetNoSlowHop(data, player);

        // Lift off envelope has changed or velocity is present, reset the liquid surface ID
        resetSurfaceIdIfNeeded(data);

        updateDownstreamState(data, thisMove, from);

        updateMomentumTicks(thisMove, to, data);
    }

    /** Helper to decrease bunnyhop related counters. */
    private void decreaseBunnyhopCounters(final MovingData data) {
        data.bunnyhopDelay--;
        data.lastbunnyhopDelay -= data.lastbunnyhopDelay > 0 ? 1 : 0;
    }

    /** Set downstream and waterfall flags as well as head obstruction. */
    private void updateSwimmingFlags(final PlayerMoveData move, final PlayerLocation from,
                                     final double xDist, final double zDist, final double yDist) {
        move.downStream = from.isDownStream(xDist, zDist);
        move.inWaterfall = from.isWaterfall(yDist);
        move.headObstructed = (yDist > 0.0 ? from.isHeadObstructed(yDist) : from.isHeadObstructed());
    }

    /** Reset the noSlowHop counter if conditions are met. */
    private void resetNoSlowHop(final MovingData data, final Player player) {
        if (data.noSlowHop != 0 && (data.isVelocityJumpPhase() || (!data.isUsingItem && !player.isBlocking()))) {
            data.noSlowHop = 0;
        }
    }

    /** Reset surfaceId if liftOffEnvelope changed or velocity is present. */
    private void resetSurfaceIdIfNeeded(final MovingData data) {
        if (!data.liftOffEnvelope.name().startsWith("LIMIT") || data.isVelocityJumpPhase()) {
            data.surfaceId = 0;
        }
    }

    /** Update downstream state for later usage. */
    private void updateDownstreamState(final MovingData data, final PlayerMoveData move, final PlayerLocation from) {
        if (!data.isdownstream) {
            data.isdownstream = move.downStream;
        } else if (from.isOnGround() && !from.isInLiquid()) {
            data.isdownstream = false;
        }
    }

    /**
     * Update the momentum tick counter if landing from air or lost ground.
     */
    private void updateMomentumTicks(final PlayerMoveData move, final PlayerLocation to, final MovingData data) {
        boolean landingNow = (!move.from.onGround && move.to.onGround ||
                tags.contains("lostground_pyramid") && data.sfJumpPhase <= 4) && !move.headObstructed;
        if (landingNow) {
            if (Magic.wasOnIceRecently(data)) {
                data.momentumTick = 31;
            } else if (Magic.jumpedUpSlope(data, to, 10)) {
                data.momentumTick = 11;
            } else {
                data.momentumTick = ServerIsAtLeast1_13 ? 6 : 3;
            }
        }
    }

    /**
     * Determine if the player should be considered sprinting.
     *
     * <p>Modifies {@link MovingData#lostSprintCount} and adds informational tags.</p>
     *
     * @param from         previous location
     * @param to           target location
     * @param fromOnGround whether the player was on ground
     * @param toOnGround   whether the player is on ground
     * @param resetTo      whether the move triggers a reset
     * @param hDistance    horizontal distance moved
     * @param now          current time
     * @param data         moving data to update
     * @param cc           moving configuration
     * @param localTags    tag collection for debug output
     * @return {@code true} if sprinting
     */
    private boolean determineSprintingState(final PlayerLocation from, final PlayerLocation to,
            final boolean fromOnGround, final boolean toOnGround, final boolean resetTo,
            final double hDistance, final long now, final MovingData data, final MovingConfig cc,
            final Collection<String> localTags) {
        if (from == null || to == null || data == null || cc == null || localTags == null) {
            return false;
        }
        boolean sprinting;
        if (data.lostSprintCount > 0) {
            if (resetTo && (fromOnGround || from.isResetCond()) || hDistance <= Magic.WALK_SPEED) {
                data.lostSprintCount = 0;
                localTags.add("invalidate_lostsprint");
                sprinting = now <= data.timeSprinting + cc.sprintingGrace;
            } else {
                localTags.add("lostsprint");
                sprinting = true;
                if (data.lostSprintCount < 3 && toOnGround || to.isResetCond()) {
                    data.lostSprintCount = 0;
                } else {
                    data.lostSprintCount--;
                }
            }
        } else if (now <= data.timeSprinting + cc.sprintingGrace) {
            if (now != data.timeSprinting) {
                localTags.add("sprintgrace");
            }
            sprinting = true;
        } else {
            sprinting = false;
        }
        return sprinting;
    }


    /**
     * Check for push/pull by pistons, alter data appropriately (blockChangeId).
     *
     * @param yDistance
     * @param from
     * @param to
     * @param data
     * @return
     */
    private double[] getVerticalBlockMoveResult(final double yDistance,
                                                final PlayerLocation from, final PlayerLocation to,
                                                final int tick, final MovingData data) {
        /*
         * (downwards?) moves (possibly all except downwards, which is hard to
         * test :p).
         */
        // Push (/pull) up.
        if (yDistance > 0.0) {
            if (yDistance <= 1.015) {
                /*
                 * (Full blocks: slightly more possible, ending up just above
                 * the block. Bounce allows other end positions.)
                 */
                if (from.matchBlockChange(blockChangeTracker, data.blockChangeRef, Direction.Y_POS, Math.min(yDistance, 1.0))) {
                    if (yDistance > 1.0) {
                        //
                        //                        final BlockChangeEntry entry = blockChangeTracker.getBlockChangeEntryMatchFlags(data.blockChangeRef,
                        //                                tick, from.getWorld().getUID(), from.getBlockX(), from.getBlockY() - 1, from.getBlockZ(),
                        //                                Direction.Y_POS, BlockFlags.F_BOUNCE25);
                        //                        if (entry != null) {
                        //                            data.blockChangeRef.updateSpan(entry);
                        //                            data.prependVerticalVelocity(new SimpleEntry(tick, 0.5015, 3));
                        //                            tags.add("past_bounce");
                        //                        }
                        //                        else
                        if (to.getY() - to.getBlockY() >= 0.015) {
                            // Exclude ordinary cases for this condition.
                            return null;
                        }
                    }
                    tags.add("blkmv_y_pos");
                    final double maxDistYPos = yDistance; //1.0 - (from.getY() - from.getBlockY());
                    return new double[]{maxDistYPos, 0.0};
                }
            }
            // (No else.)
            //            if (yDistance <= 1.55) {
            //
            //
            //                // Simplified: Always allow 1.5 or less with being pushed up by slime.
            //
            //                if (from.matchBlockChangeMatchResultingFlags(
            //                        blockChangeTracker, data.blockChangeRef, Direction.Y_POS,
            //                        Math.min(yDistance, 0.415), // Special limit.
            //                        BlockFlags.F_BOUNCE25)) {
            //                    tags.add("blkmv_y_pos_bounce");
            //                    final double maxDistYPos = yDistance; //1.0 - (from.getY() - from.getBlockY());
            //
            //
            //                    data.addVerticalVelocity(new SimpleEntry(tick, Math.max(0.515, yDistance - 0.5), 2));
            //                    return new double[]{maxDistYPos, 0.0};
            //                }
            //            }
        }
        // Push (/pull) down.
        else if (yDistance < 0.0 && yDistance >= -1.0) {
            if (from.matchBlockChange(blockChangeTracker, data.blockChangeRef, Direction.Y_NEG, -yDistance)) {
                tags.add("blkmv_y_neg");
                final double maxDistYNeg = yDistance; // from.getY() - from.getBlockY();
                return new double[]{maxDistYNeg, 0.0};
            }
        }
        // Nothing found.
        return null;
    }


    private static boolean isMovingBackwards(final PlayerMoveData move, final PlayerLocation from) {
        if (move == null || from == null) {
            return false;
        }
        return TrigUtil.isMovingBackwards(
                move.to.getX() - move.from.getX(),
                move.to.getZ() - move.from.getZ(),
                LocUtil.correctYaw(from.getYaw()));
    }

    private static boolean isBlockingOrUsing(final Player player, final MovingData data) {
        if (player == null || data == null) {
            return false;
        }
        return data.isUsingItem || player.isBlocking();
    }

    private static double calcModStairs(final boolean isMovingBackwards, final PlayerMoveData move) {
        if (move == null) {
            return isMovingBackwards ? 1.0 : 1.325;
        }
        return isMovingBackwards ? 1.0 : move.yDistance == 0.5 ? 1.85 : 1.325;
    }

    private static double calcModHopSprint(final MovingData data, final PlayerMoveData move, final PlayerLocation to) {
        if (data == null || move == null || to == null) {
            return Magic.modSprint;
        }
        return data.momentumTick < 3 ? Magic.modHopTick
                : Magic.jumpedUpSlope(data, to, 13) && move.hDistance > move.walkSpeed ? Magic.modSlope
                : Magic.modSprint;
    }

    /**
     * Set hAllowedDistanceBase and hAllowedDistance in thisMove. Not exact,
     * check permissions as far as necessary, if flag is set to check them.
     *
     * @param player
     * @param sprinting
     * @param thisMove
     * @param data
     * @param cc
     * @param checkPermissions
     *            If to check permissions, allowing to speed up a little bit.
     *            Only set to true after having failed with it set to false.
     * @return Allowed distance.
     */
    private double setAllowedhDist(final AllowedDistanceContext ctx) {
        final Player player = ctx.player();
        final boolean sprinting = ctx.sprinting();
        final PlayerMoveData thisMove = ctx.thisMove();
        final MovingData data = ctx.data();
        final MovingConfig cc = ctx.cc();
        final IPlayerData pData = ctx.pData();
        final PlayerLocation from = ctx.from();
        final PlayerLocation to = ctx.to();
        final boolean checkPermissions = ctx.checkPermissions();

        if (!validateMoveInputs(player, from, to, "setAllowedhDist")) {
            return 0.0;
        }

        //       - Web before liquid, because web speed can apply in water as well (same with berry bushes, despite not being able to place them underwater but you never know what plugins can do...)
        //       - Powder snow in water -> Check what movement takes precedence.
        //       - Being on medium while in water (soulsand, slime block, honey block and stairs in water)
        //       - Same thing with collision tolerance [Move it as a global modifier].
        //       - Bunnyhopping around lowest liquid levels
        //       - Bunnyhoping right into a berry bush
        //       - Swimming -> not swimming transitions
        //       - Thinkable: don't immediately restrict speed but slowly reduce speed with each event, until max speed is reached, like the current item use limit.
        final boolean isMovingBackwards   = isMovingBackwards(thisMove, from);
        final boolean actuallySneaking    = player.isSneaking() && reallySneaking.contains(player.getName());
        final boolean isBlockingOrUsing   = isBlockingOrUsing(player, data);
        final PlayerMoveData lastMove     = data.playerMoves.getFirstPastMove();
        final PlayerMoveData pastMove2    = data.playerMoves.getSecondPastMove();
        final long now                    = System.currentTimeMillis();
        final double modHoneyBlock        = Magic.modSoulSand * (thisMove.to.onGround ? 0.8 : 1.75);
        final double modStairs            = calcModStairs(isMovingBackwards, thisMove);
        final double modHopSprint         = calcModHopSprint(data, thisMove, to);
        final boolean sfDirty             = data.isVelocityJumpPhase();
        double hAllowedDistance           = 0.0;
        double friction                   = data.lastFrictionHorizontal; // Friction to use with this move.
        boolean useBaseModifiers          = false;
        boolean useBaseModifiersSprint    = true;
        boolean useSneakModifier          = false;
        if (thisMove.from.onIce) tags.add("hice");

        final DistanceState state = new DistanceState(hAllowedDistance, friction,
                useBaseModifiers, useBaseModifiersSprint, useSneakModifier);

        if (applyWebModifiers(ctx, state)) {
            // done
        } else if (applyPowderSnowModifiers(ctx, state)) {
            // done
        } else if (applyBerryBushModifiers(ctx, state)) {
            // done
        } else if (applySoulSandModifiers(ctx, state)) {
            // done
        } else if (applySlimeBlockModifiers(ctx, state)) {
            // done
        } else if (applyHoneyBlockModifiers(ctx, state, modHoneyBlock)) {
            // done
        } else if (applyStairsModifiers(ctx, state, modStairs)) {
            // done
        }

        hAllowedDistance = state.allowed;
        friction = state.friction;
        useBaseModifiers = state.useBaseModifiers;
        useBaseModifiersSprint = state.useBaseModifiersSprint;
        useSneakModifier = state.useSneakModifier;


        ////////////////////////////////////////////////////////////////////////
        // Set the allowed horizontal distance according to medium and status //
        ///////////////////////////////////////////////////////////////////////
        // Climbables (Before webs)
        //  if (thisMove.from.onClimbable && lastMove.from.onClimbable && !thisMove.touchedGround) {
        //      tags.add("hclimb");
        //      hAllowedDistance = Magic.modClimbable * thisMove.walkSpeed * cc.survivalFlyWalkingSpeed / 100D;
        //      useBaseModifiers = false;
        //      friction = 0.0;
        //  }

        // Webs
        if (thisMove.from.inWeb) {
            tags.add("hweb");
            hAllowedDistance = Magic.modWeb * thisMove.walkSpeed * cc.survivalFlyWalkingSpeed / 100D;
            useBaseModifiersSprint = false;
            useBaseModifiers = true;
            useSneakModifier = true;
            friction = 0.0;
        }

        // Powder snow
        else if (thisMove.from.inPowderSnow) {
            tags.add("hsnow");
            hAllowedDistance = Magic.modPowderSnow * thisMove.walkSpeed * cc.survivalFlyWalkingSpeed / 100D;
            // Lift-off acceleration
            if (thisMove.yDistance > data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier) - Magic.GRAVITY_SPAN
                && thisMove.from.onGround && !thisMove.to.onGround) {
                hAllowedDistance *= 2.3;
            }
            useBaseModifiers = true;
            useSneakModifier = true;
            friction = 0.0;
        }

        // Berry bush
        else if (thisMove.from.inBerryBush) {
            tags.add("hbush");
            hAllowedDistance = Magic.modBush * thisMove.walkSpeed * cc.survivalFlyWalkingSpeed / 100D;
            // Lift-off acceleration
            if (thisMove.yDistance > data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier) - Magic.GRAVITY_SPAN
                && thisMove.from.onGround && !thisMove.to.onGround) {
                hAllowedDistance *= 2.3;
            }
            useSneakModifier = true;
            useBaseModifiers = true;
            friction = 0.0;
        }

        // Soulsand
        else if (thisMove.from.onSoulSand) {
            tags.add("hsoulsand");
            hAllowedDistance = Magic.modSoulSand * thisMove.walkSpeed * cc.survivalFlyWalkingSpeed / 100D;
            // SoulSpeed stuff
            if (BridgeEnchant.hasSoulSpeed(player)) {
                hAllowedDistance *= Magic.modSoulSpeed;
                data.keepfrictiontick = 60;
            }
            useSneakModifier = true;
            useBaseModifiers = true;
            friction = 0.0;
            // NOTE: Soulsand above ice: https://bugs.mojang.com/browse/MC-163952 henche why we don't enforce slower speed here.
        }

        // Slimeblock
        else if (thisMove.from.onSlimeBlock && thisMove.to.onSlimeBlock && !Magic.jumpedUpSlope(data, to, 11)) { // See MagicBunny.bunnyhop
            tags.add("hslimeblock");
            hAllowedDistance = Magic.modSlime * thisMove.walkSpeed * cc.survivalFlyWalkingSpeed / 100D;
            useSneakModifier = true;
            useBaseModifiers = true;
            friction = 0.0;
        }

        // Honeyblock
        else if (thisMove.from.onHoneyBlock) {
            tags.add("hhoneyblock");
            hAllowedDistance = modHoneyBlock * thisMove.walkSpeed * cc.survivalFlyWalkingSpeed / 100D;
            useSneakModifier = true;
            useBaseModifiers = true;
            friction = 0.0;
        }

        // Stairs
        else if (thisMove.from.aboveStairs || thisMove.to.aboveStairs) {
            tags.add("hstairs");
            useBaseModifiers = true;
            useSneakModifier = true;
            hAllowedDistance = modStairs * thisMove.walkSpeed * cc.survivalFlyWalkingSpeed / 100D;
            friction = 0.0;
            if (!Double.isInfinite(mcAccess.getHandle().getFasterMovementAmplifier(player))) hAllowedDistance *= 0.88;
        }

        // NoSlow packet (detection, not a deterministic limit)
        else if (data.isHackingRI && (!checkPermissions || !pData.hasPermission(Permissions.MOVING_SURVIVALFLY_BLOCKING, player))) {
            tags.add("noslowpacket");
            data.isHackingRI = false;
            hAllowedDistance = 0.0;
            useBaseModifiers = false;
            friction = 0.0;
        }

        // InvalidUse packet
        else if (data.invalidItemUse && (!checkPermissions || !pData.hasPermission(Permissions.MOVING_SURVIVALFLY_BLOCKING, player))) {
            tags.add("invalidate_use");
            data.invalidItemUse = false;
            hAllowedDistance = 0.0;
            useBaseModifiers = false;
            friction = 0.0;
        }

        // Collision tolerance for entities (1.9+)
        else if (ServerIsAtLeast1_9 && CollisionUtil.isCollidingWithEntities(player, true)
                && hAllowedDistance < 0.35 && data.liftOffEnvelope == LiftOffEnvelope.NORMAL) {
            tags.add("hcollision");
            hAllowedDistance = Magic.modCollision * thisMove.walkSpeed * cc.survivalFlyWalkingSpeed / 100D;
            useBaseModifiers = true;
            data.momentumTick = 20;
            friction = 0.0;
        }

        // In liquid
        // Check all liquids (lava might demand even slower speed though).
        else if (applyInLiquidModifiers(ctx, state, lastMove, pastMove2, sprinting, sfDirty)) {
            hAllowedDistance = state.allowed;
            friction = state.friction;
            useBaseModifiers = state.useBaseModifiers;
            useBaseModifiersSprint = state.useBaseModifiersSprint;
            useSneakModifier = state.useSneakModifier;
        }

        // Speed restriction for leaving a liquid.
        else if (applyLiquidExitModifiers(ctx, state, sfDirty)) {
            hAllowedDistance = state.allowed;
            friction = state.friction;
            useBaseModifiers = state.useBaseModifiers;
            useBaseModifiersSprint = state.useBaseModifiersSprint;
            useSneakModifier = state.useSneakModifier;
        }

        // Sneaking
        // (Bet it's yet another fucking desync...)
        else if (!sfDirty && thisMove.from.onGround && actuallySneaking
                && (!checkPermissions || !pData.hasPermission(Permissions.MOVING_SURVIVALFLY_SNEAKING, player))) {
            tags.add("sneaking");
            hAllowedDistance = Magic.modSneak * thisMove.walkSpeed * cc.survivalFlySneakingSpeed / 100D;
            hAllowedDistance += 0.051 * BridgeEnchant.getSwiftSneakLevel(player);
            useBaseModifiers = true;
            friction = 0.0; // Ensure friction can't be used to speed.

            if (!Double.isInfinite(mcAccess.getHandle().getFasterMovementAmplifier(player))) {
                hAllowedDistance *= 0.88;
                useBaseModifiersSprint = true;
            }
        }

        // Using items
        else if (!sfDirty && isBlockingOrUsing && (thisMove.from.onGround || data.noSlowHop > 0 || player.isBlocking())
                && (!checkPermissions || !pData.hasPermission(Permissions.MOVING_SURVIVALFLY_BLOCKING, player))
                && data.liftOffEnvelope == LiftOffEnvelope.NORMAL) {
            tags.add("usingitem");
            if (thisMove.from.onGround) {
                // Jump/left ground
                if (!thisMove.to.onGround) {
                    final double speedAmplifier = mcAccess.getHandle().getFasterMovementAmplifier(player);
                    hAllowedDistance = (lastMove.hDistance > 0.23 ? 0.4 : 0.23 + (ServerIsAtLeast1_13 ? 0.155 : 0.0)) +
                                        0.02 * (Double.isInfinite(speedAmplifier) ? 0 : speedAmplifier + 1.0);
                    hAllowedDistance *= cc.survivalFlyBlockingSpeed / 100D;
                    data.noSlowHop = 1;
                }
                // OnGround
                else {
                    if (lastMove.toIsValid && lastMove.hDistance > 0.0) {
                       hAllowedDistance = data.noSlowHop < 7 ?
                                        // 0.6 for old vers, 0.621 for 1.13+
                                        (lastMove.hAllowedDistance * (0.63 + 0.052 * ++data.noSlowHop)) : lastMove.hAllowedDistance;
                    }
                    // Failed or no hDistance in last move, return to default speed
                    else hAllowedDistance = Magic.modBlock * thisMove.walkSpeed * cc.survivalFlyBlockingSpeed / 100D;
                }
            }
            else if (data.noSlowHop > 0) {
                if (data.noSlowHop == 1 && lastMove.toIsValid) {
                    // Second move after jump, high decay
                    hAllowedDistance = lastMove.hAllowedDistance * 0.6 * cc.survivalFlyBlockingSpeed / 100D;
                    // Fake data, prevent too much friction after slow - rejump
                    data.noSlowHop = 4;
                }
                // Air friction
                else hAllowedDistance = lastMove.hAllowedDistance * 0.96 * cc.survivalFlyBlockingSpeed / 100D;
            }
            else if (player.isBlocking() && lastMove.toIsValid) {
                // Air friction
                hAllowedDistance = lastMove.hAllowedDistance * 0.96 * cc.survivalFlyBlockingSpeed / 100D;
                // Fake data for air blocking
                data.noSlowHop = 2;
            }
            // Check if too small horizontal last move allowed
            // 0.063 for old vers, 0.08 for 1.13+
            hAllowedDistance = Math.max(hAllowedDistance, 0.08);
            friction = 0.0; // Ensure friction can't be used to speed.
            useBaseModifiers = false;
            useBaseModifiersSprint = false;
        }
        // Fallback to the default speed
        else {
            useBaseModifiers = true;
            // Landing on ground allows a slightly faster move.
            if (!thisMove.from.onGround && thisMove.to.onGround) {
                hAllowedDistance = Magic.modLanding * thisMove.walkSpeed * cc.survivalFlySprintingSpeed / 100D;
                tags.add("walkspeed_to");
            }
            // Momentum after landing.
            else if (data.momentumTick > 0) {
                hAllowedDistance = modHopSprint * thisMove.walkSpeed * cc.survivalFlySprintingSpeed / 100D;
                tags.add("walkspeed(" + data.momentumTick + ")");
            }
            // Ground -> ground or Air -> air
            else {
                hAllowedDistance = thisMove.walkSpeed * cc.survivalFlySprintingSpeed / 100D;
                tags.add("walkspeed");
            }

            // Drop friction unless is on ice.
            if (!Magic.touchedIce(thisMove)) friction = 0.0;
        }



        ////////////////////////////////////////////////////////////////////////////
        // Apply various modifiers to the BASE speed (sprinting, attributes, ...) //
        ///////////////////////////////////////////////////////////////////////////
        if (useBaseModifiers) {

            // Apply sprinting speed
            if (useBaseModifiersSprint && sprinting) {
                hAllowedDistance *= data.multSprinting;
            }
            // Note: Attributes count in slowness potions, thus leaving out isn't possible.
            final double attrMod = attributeAccess.getHandle().getSpeedAttributeMultiplier(player);
            // Count in speed potions.
            if (attrMod == Double.MAX_VALUE) {
                final double speedAmplifier = mcAccess.getHandle().getFasterMovementAmplifier(player);
                if (!Double.isInfinite(speedAmplifier) && useBaseModifiersSprint) {
                    hAllowedDistance *= 1.0D + 0.2D * speedAmplifier;
                }
            }
            else {
                hAllowedDistance *= attrMod;
                // Hack for allow sprint-jumping with slowness.
                if (sprinting && hAllowedDistance < 0.29 && cc.sfSlownessSprintHack
                    && (
                        player.hasPotionEffect(BridgePotionEffect.SLOWNESS)
                        || data.walkSpeed < Magic.DEFAULT_WALKSPEED
                        || attrMod < 1.0
                    )) {
                    hAllowedDistance = slownessSprintHack(player, hAllowedDistance);
                }
                //useBaseModifiersSprint = false mean not apply speed effect in it
                if (!useBaseModifiersSprint) {
                    final double speedAmplifier = mcAccess.getHandle().getFasterMovementAmplifier(player);
                    if (!Double.isInfinite(speedAmplifier)) {
                        hAllowedDistance /= attrMod;
                        hAllowedDistance *= attrMod - 0.15D * speedAmplifier;
                    }
                }
            }
        }

        // Account for flowing liquids (only if needed).
        if (thisMove.downStream && thisMove.hDistance > thisMove.walkSpeed * Magic.modSwim[0]
            && thisMove.from.inLiquid) {
            hAllowedDistance *= Magic.modDownStream;
        }

        // Apply sneaking speed for other modifiers.
        if (useSneakModifier && actuallySneaking && !Bridge1_13.isSwimming(player)) {
            hAllowedDistance *= 0.682;
        }

        // Edge case bullshit (undetectable jump with head obstructed and trapdoor underneath)
        if (thisMove.headObstructed
            && ((from.getBlockFlags() & BlockFlags.F_ICE) != 0 || (from.getBlockFlags() & BlockFlags.F_BLUE_ICE) != 0)
            && (from.getBlockFlags() & BlockFlags.F_ATTACHED_LOW2_SNEW) != 0) {
            hAllowedDistance *= Magic.modIce;
        }

        // Speeding bypass permission (can be combined with other bypasses).
        if (checkPermissions && pData.hasPermission(Permissions.MOVING_SURVIVALFLY_SPEEDING, player)) {
            hAllowedDistance *= cc.survivalFlySpeedingSpeed / 100D;
        }

        // Base speed is set.
        thisMove.hAllowedDistanceBase = hAllowedDistance;



        /////////////////////////////////////////////////////////////////////
        // Set the FINAL allowed speed, accounting for friction and more   //
        ////////////////////////////////////////////////////////////////////
        // Friction mechanics (next move).
        // Move is within lift-off/burst envelope, allow next time.
        if (thisMove.hDistance <= hAllowedDistance) {
            data.nextFrictionHorizontal = 1.0;
        }

        // Soul speed workaround (friction)
        if (data.keepfrictiontick > 0) {
            if (!BridgeEnchant.hasSoulSpeed(player)) {
                data.keepfrictiontick = 0;
            }
            else if (lastMove.toIsValid) {
                hAllowedDistance = Math.max(hAllowedDistance, lastMove.hAllowedDistance * 0.96);
            }
        }

        // Friction or not (this move).
        if (lastMove.toIsValid && friction > 0.0) {
            tags.add("hfrict");
            hAllowedDistance = Math.max(hAllowedDistance, lastMove.hDistance * friction);
        }

        // The final allowed speed is set.
        thisMove.hAllowedDistance = hAllowedDistance;
        return thisMove.hAllowedDistance;
    }

    private boolean applyWebModifiers(final AllowedDistanceContext ctx, final DistanceState st) {
        if (ctx == null || st == null) return false;
        final PlayerMoveData move = ctx.thisMove();
        final MovingConfig cc = ctx.cc();
        if (move == null || cc == null || move.from == null) return false;
        if (move.from.inWeb) {
            tags.add("hweb");
            st.allowed = Magic.modWeb * move.walkSpeed * cc.survivalFlyWalkingSpeed / 100D;
            st.useBaseModifiersSprint = false;
            st.useBaseModifiers = true;
            st.useSneakModifier = true;
            st.friction = 0.0;
            return true;
        }
        return false;
    }

    private boolean applyPowderSnowModifiers(final AllowedDistanceContext ctx, final DistanceState st) {
        if (ctx == null || st == null) return false;
        final PlayerMoveData move = ctx.thisMove();
        final MovingConfig cc = ctx.cc();
        final MovingData data = ctx.data();
        if (move == null || cc == null || data == null || move.from == null || move.to == null) return false;
        if (move.from.inPowderSnow) {
            tags.add("hsnow");
            st.allowed = Magic.modPowderSnow * move.walkSpeed * cc.survivalFlyWalkingSpeed / 100D;
            if (move.yDistance > data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier) - Magic.GRAVITY_SPAN
                && move.from.onGround && !move.to.onGround) {
                st.allowed *= 2.3;
            }
            st.useBaseModifiers = true;
            st.useSneakModifier = true;
            st.friction = 0.0;
            return true;
        }
        return false;
    }

    private boolean applyBerryBushModifiers(final AllowedDistanceContext ctx, final DistanceState st) {
        if (ctx == null || st == null) return false;
        final PlayerMoveData move = ctx.thisMove();
        final MovingConfig cc = ctx.cc();
        final MovingData data = ctx.data();
        if (move == null || cc == null || data == null || move.from == null || move.to == null) return false;
        if (move.from.inBerryBush) {
            tags.add("hbush");
            st.allowed = Magic.modBush * move.walkSpeed * cc.survivalFlyWalkingSpeed / 100D;
            if (move.yDistance > data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier) - Magic.GRAVITY_SPAN
                && move.from.onGround && !move.to.onGround) {
                st.allowed *= 2.3;
            }
            st.useSneakModifier = true;
            st.useBaseModifiers = true;
            st.friction = 0.0;
            return true;
        }
        return false;
    }

    private boolean applySoulSandModifiers(final AllowedDistanceContext ctx, final DistanceState st) {
        if (ctx == null || st == null) return false;
        final PlayerMoveData move = ctx.thisMove();
        final MovingConfig cc = ctx.cc();
        final MovingData data = ctx.data();
        final Player player = ctx.player();
        if (move == null || cc == null || data == null || player == null || move.from == null) return false;
        if (move.from.onSoulSand) {
            tags.add("hsoulsand");
            st.allowed = Magic.modSoulSand * move.walkSpeed * cc.survivalFlyWalkingSpeed / 100D;
            if (BridgeEnchant.hasSoulSpeed(player)) {
                st.allowed *= Magic.modSoulSpeed;
                data.keepfrictiontick = 60;
            }
            st.useSneakModifier = true;
            st.useBaseModifiers = true;
            st.friction = 0.0;
            return true;
        }
        return false;
    }

    private boolean applySlimeBlockModifiers(final AllowedDistanceContext ctx, final DistanceState st) {
        if (ctx == null || st == null) return false;
        final PlayerMoveData move = ctx.thisMove();
        final MovingConfig cc = ctx.cc();
        final MovingData data = ctx.data();
        final PlayerLocation to = ctx.to();
        if (move == null || cc == null || data == null || to == null || move.from == null) return false;
        if (move.from.onSlimeBlock && move.to.onSlimeBlock && !Magic.jumpedUpSlope(data, to, 11)) {
            tags.add("hslimeblock");
            st.allowed = Magic.modSlime * move.walkSpeed * cc.survivalFlyWalkingSpeed / 100D;
            st.useSneakModifier = true;
            st.useBaseModifiers = true;
            st.friction = 0.0;
            return true;
        }
        return false;
    }

    private boolean applyHoneyBlockModifiers(final AllowedDistanceContext ctx, final DistanceState st,
            final double modHoneyBlock) {
        if (ctx == null || st == null) return false;
        final PlayerMoveData move = ctx.thisMove();
        final MovingConfig cc = ctx.cc();
        if (move == null || cc == null || move.from == null) return false;
        if (move.from.onHoneyBlock) {
            tags.add("hhoneyblock");
            st.allowed = modHoneyBlock * move.walkSpeed * cc.survivalFlyWalkingSpeed / 100D;
            st.useSneakModifier = true;
            st.useBaseModifiers = true;
            st.friction = 0.0;
            return true;
        }
        return false;
    }

    private boolean applyStairsModifiers(final AllowedDistanceContext ctx, final DistanceState st,
            final double modStairs) {
        if (ctx == null || st == null) return false;
        final PlayerMoveData move = ctx.thisMove();
        final MovingConfig cc = ctx.cc();
        final Player player = ctx.player();
        if (move == null || cc == null || player == null || move.from == null || move.to == null) return false;
        if (move.from.aboveStairs || move.to.aboveStairs) {
            tags.add("hstairs");
            st.useBaseModifiers = true;
            st.useSneakModifier = true;
            st.allowed = modStairs * move.walkSpeed * cc.survivalFlyWalkingSpeed / 100D;
            st.friction = 0.0;
            if (!Double.isInfinite(mcAccess.getHandle().getFasterMovementAmplifier(player))) {
                st.allowed *= 0.88;
            }
            return true;
        }
        return false;
    }

    private boolean applyInLiquidModifiers(final AllowedDistanceContext ctx, final DistanceState st,
            final PlayerMoveData lastMove, final PlayerMoveData pastMove2,
            final boolean sprinting, final boolean sfDirty) {
        if (ctx == null || st == null) return false;
        final PlayerMoveData move = ctx.thisMove();
        final MovingConfig cc = ctx.cc();
        final MovingData data = ctx.data();
        final PlayerLocation from = ctx.from();
        final Player player = ctx.player();
        if (move == null || cc == null || data == null || from == null || player == null || move.to == null) {
            return false;
        }
        if (move.from.inLiquid && move.to.inLiquid) {
            tags.add("hliquid");
            final double modSwim = (from.isSubmerged(0.701) || move.from.inLava) ? Magic.modSwim[0] : Magic.modSwim[3];
            st.allowed = Bridge1_13.isSwimming(player) ? Magic.modSwim[1]
                    : modSwim * move.walkSpeed * cc.survivalFlySwimmingSpeed / 100D;
            st.useBaseModifiers = false;
            st.useSneakModifier = true;
            if (sfDirty) st.friction = 0.0;

            if (move.from.inWater || !move.from.inLava) {
                final int strider = BridgeEnchant.getDepthStriderLevel(player);
                if (strider > 0) {
                    st.useBaseModifiers = true;
                    st.useBaseModifiersSprint = true;
                    st.allowed *= Magic.modDepthStrider[strider];
                }

                if (!Double.isInfinite(Bridge1_13.getDolphinGraceAmplifier(player))) {
                    st.allowed *= Magic.modDolphinsGrace;
                    if (strider > 1) st.allowed *= 1.0 + 0.07 * strider;
                }

                if (data.liqtick < 5 && lastMove != null && lastMove.toIsValid) {
                    if (!lastMove.from.inLiquid) {
                        if (lastMove.hDistance * 0.92 > move.hDistance) {
                            st.allowed = lastMove.hDistance * 0.92;
                        }
                    } else if (lastMove.hAllowedDistance * 0.92 > move.hDistance) {
                        st.allowed = lastMove.hAllowedDistance * 0.92;
                    }
                }

                if (from.isInWaterLogged() && data.insideMediumCount <= 1
                        && !from.isSubmerged(0.75) && (lastMove == null || !lastMove.from.inLiquid || pastMove2 == null || !pastMove2.from.inLiquid)
                        && !move.headObstructed && BlockProperties.isAir(from.getTypeIdAbove())) {
                    if (Magic.XORonGround(move) || (lastMove != null && Magic.XORonGround(lastMove))) {
                        st.allowed = move.walkSpeed * data.lastFrictionHorizontal * cc.survivalFlySwimmingSpeed / 100D;
                    }
                }
            }

            final int fromBlockData = from.getData(from.getBlockX(), from.getBlockY(), from.getBlockZ());
            if (BlockProperties.isAir(from.getTypeIdAbove()) && !move.headObstructed && !from.isSubmerged(0.8)
                    && (data.insideMediumCount < 4 || data.liftOffEnvelope == LiftOffEnvelope.NORMAL)) {
                if (move.from.inLava) {
                    if (!lastMove.from.inLava || !pastMove2.from.inLava
                            || Magic.XORonGround(move) && (fromBlockData == 0 || fromBlockData == 6)) {
                        st.allowed = (sprinting ? Magic.modSprint : 1.0) * move.walkSpeed * cc.survivalFlySwimmingSpeed / 100D;
                        if (!move.from.onGround && move.to.onGround) {
                            data.momentumTick = 6;
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    private boolean applyLiquidExitModifiers(final AllowedDistanceContext ctx, final DistanceState st,
            final boolean sfDirty) {
        if (ctx == null || st == null) return false;
        final PlayerMoveData move = ctx.thisMove();
        final MovingConfig cc = ctx.cc();
        final MovingData data = ctx.data();
        final PlayerLocation from = ctx.from();
        final IPlayerData pData = ctx.pData();
        final Player player = ctx.player();
        final boolean checkPermissions = ctx.checkPermissions();
        if (move == null || cc == null || data == null || from == null || pData == null || player == null) {
            return false;
        }
        if (!sfDirty && (!checkPermissions || !pData.hasPermission(Permissions.MOVING_SURVIVALFLY_WATERWALK, player))
                && (Magic.leavingLiquid(move) || data.surfaceId == 1) && data.liftOffEnvelope.name().startsWith("LIMIT")
                && !from.isInWaterLogged()) {
            tags.add("hliquidexit");
            final int strider = BridgeEnchant.getDepthStriderLevel(player);
            st.allowed = Bridge1_13.isSwimming(player) ? Magic.modSwim[1]
                    : Magic.modSwim[0] * move.walkSpeed * Magic.modSurface[0] * cc.survivalFlySwimmingSpeed / 100D;
            st.useBaseModifiersSprint = false;
            st.friction = 0.0;

            if (strider > 0 && data.surfaceId == 0) {
                st.useBaseModifiers = true;
                st.useBaseModifiersSprint = true;
                st.friction = data.lastFrictionHorizontal;
                st.allowed *= Magic.modDepthStrider[strider];
            }

            if (!Double.isInfinite(Bridge1_13.getDolphinGraceAmplifier(player))) {
                st.allowed *= Magic.modDolphinsGrace;
                if (strider > 1) st.allowed *= 1.0 + 0.07 * strider;
            }

            if (data.surfaceId == 1) st.allowed *= Magic.modSurface[1];
            data.surfaceId = 1;
            final int blockData = from.getData(from.getBlockX(), from.getBlockY(), from.getBlockZ());
            final int blockUnderData = from.getData(from.getBlockX(), from.getBlockY() - 1, from.getBlockZ());

            if (blockData > 3 || blockUnderData > 3 || data.isdownstream) {
                data.surfaceId = 0;
                st.allowed = move.walkSpeed * cc.survivalFlySwimmingSpeed / 100D;
                data.isdownstream = false;
            }
            return true;
        }
        return false;
    }


    /**
     * Return a 'corrected' allowed horizontal speed. Call only if the player
     * has a SLOW effect.
     *
     * @param player
     * @param hAllowedDistance
     * @return
     */
    private double slownessSprintHack(final Player player, final double hAllowedDistance) {
        // Simple: up to high levels they can stay close, with a couple of hops until max base speed.
        return 0.29;
    }


    /**
     * Access method from outside.
     * @param player
     * @return
     */
    public boolean isReallySneaking(final Player player) {
        if (player == null) {
            validateMoveInputs(null, null, null, "isReallySneaking");
            return false;
        }
        return reallySneaking.contains(player.getName());
    }


    /**
     * Core y-distance checks for in-air movement (may include air -> other).
     * See AirWorkarounds to check (most of) the exemption rules.
     *
     * @return
     */
    private double[] vDistAir(final long now, final Player player, final PlayerLocation from,
                              final boolean fromOnGround, final boolean resetFrom, final PlayerLocation to,
                              final boolean toOnGround, final boolean resetTo,
                              final double hDistance, final double yDistance,
                              final int multiMoveCount, final PlayerMoveData lastMove,
                              final MovingData data, final MovingConfig cc, final IPlayerData pData) {

        //            (Aim is to fix the lowjump detection (see notes))
        double vAllowedDistance          = 0.0;
        double vDistanceAboveLimit       = 0.0;
        final PlayerMoveData thisMove    = data.playerMoves.getCurrentMove();
        final double yDistChange         = lastMove.toIsValid ? yDistance - lastMove.yDistance : Double.MAX_VALUE; // Change seen from last yDistance.
        final double maxJumpGain         = data.liftOffEnvelope.getMaxJumpGain(data.jumpAmplifier);
        final int maxJumpPhase           = data.liftOffEnvelope.getMaxJumpPhase(data.jumpAmplifier);
        final double jumpGainMargin      = 0.005;

        // Allowed distance and envelope/velocity handling.
        final double[] allowedRes = calculateAllowedVDist(from, resetFrom, to, toOnGround, resetTo, yDistance,
                lastMove, thisMove, data, cc, maxJumpGain, jumpGainMargin);
        vAllowedDistance = allowedRes[0];
        final boolean strictVdistRel = allowedRes[1] > 0.0;

        final double[] envRes = applyEnvelopeVelocityExemptions(now, player, from, to, yDistance, yDistChange,
                vAllowedDistance, strictVdistRel, fromOnGround, toOnGround, resetFrom, resetTo, thisMove, lastMove, data, cc,
                maxJumpGain);
        vDistanceAboveLimit = envRes[0];
        final boolean VEnvHack = envRes[1] > 0.0;

        vDistanceAboveLimit = applyAbsoluteLimit(now, player, from, to, toOnGround, yDistance, fromOnGround, thisMove,
                lastMove, data, cc, pData, vDistanceAboveLimit);

        vDistanceAboveLimit = checkAirStayTime(VEnvHack, maxJumpPhase, yDistance, resetFrom, thisMove, data, cc,
                vDistanceAboveLimit);

        final boolean InAirPhase = !VEnvHack && !resetFrom && !resetTo;
        final boolean ChangedYDir = lastMove.toIsValid && lastMove.yDistance != yDistance
                && (yDistance <= 0.0 && lastMove.yDistance >= 0.0 || yDistance >= 0.0 && lastMove.yDistance <= 0.0);

        vDistanceAboveLimit = handleDirectionChange(player, from, yDistance, InAirPhase, ChangedYDir, thisMove,
                lastMove, data, cc, vDistanceAboveLimit);

        vDistanceAboveLimit = applyVerticalAccounting(now, from, to, yDistance, vDistanceAboveLimit, InAirPhase,
                ChangedYDir, thisMove, lastMove, data, cc);
        if (data.sfLowJump) {
            tags.add("lowjump");
        }

        return new double[]{vAllowedDistance, vDistanceAboveLimit};
    }


    private double[] calculateAllowedVDist(final PlayerLocation from, final boolean resetFrom,
            final PlayerLocation to, final boolean toOnGround, final boolean resetTo, final double yDistance,
            final PlayerMoveData lastMove, final PlayerMoveData thisMove, final MovingData data,
            final MovingConfig cc, final double maxJumpGain, final double jumpGainMargin) {
        double vAllowedDistance;
        boolean strictVdistRel;

        if (isFallingEnvelope(lastMove, yDistance, data)) {
            vAllowedDistance = lastMove.yDistance * data.lastFrictionVertical - Magic.GRAVITY_MIN;
            strictVdistRel = true;
        } else if (resetFrom || thisMove.touchedGroundWorkaround) {
            vAllowedDistance = handleResetFrom(to, toOnGround, yDistance, thisMove, cc, maxJumpGain, jumpGainMargin);
            strictVdistRel = false;
        } else if (lastMove.toIsValid) {
            double[] result = handleLastMoveValid(lastMove, thisMove, yDistance, resetTo, data,
                    cc, maxJumpGain, jumpGainMargin);
            vAllowedDistance = result[0];
            strictVdistRel = result[1] == 1.0;
        } else {
            vAllowedDistance = handleMissingData(thisMove, lastMove, yDistance, data, cc, maxJumpGain,
                    jumpGainMargin);
            strictVdistRel = false;
        }
        return new double[]{vAllowedDistance, strictVdistRel ? 1.0 : 0.0};
    }

    private boolean isFallingEnvelope(final PlayerMoveData lastMove, final double yDistance,
                                      final MovingData data) {
        return lastMove.toIsValid
                && Magic.fallingEnvelope(yDistance, lastMove.yDistance, data.lastFrictionVertical, 0.0);
    }

    private double handleResetFrom(final PlayerLocation to, final boolean toOnGround, final double yDistance,
                                   final PlayerMoveData thisMove, final MovingConfig cc,
                                   final double maxJumpGain, final double jumpGainMargin) {
        double result;
        if (toOnGround) {
            if (yDistance > cc.sfStepHeight && yDistance - cc.sfStepHeight < 0.00000003
                    && to.isOnGroundDueToStandingOnAnEntity()) {
                result = yDistance;
            } else {
                result = Math.max(cc.sfStepHeight, maxJumpGain + jumpGainMargin);
                thisMove.allowstep = true;
                thisMove.allowjump = true;
            }
        } else {
            if (yDistance < 0.0 || yDistance > cc.sfStepHeight || !tags.contains("lostground_couldstep")) {
                result = maxJumpGain + jumpGainMargin;
                thisMove.allowjump = true;
            } else {
                result = yDistance;
            }
        }
        return result;
    }

    private double[] handleLastMoveValid(final PlayerMoveData lastMove, final PlayerMoveData thisMove,
                                         final double yDistance, final boolean resetTo, final MovingData data,
                                         final MovingConfig cc, final double maxJumpGain,
                                         final double jumpGainMargin) {
        double allowed;
        boolean strict;
        if (lastMove.yDistance >= -Math.max(Magic.GRAVITY_MAX / 2.0, 1.3 * Math.abs(yDistance))
                && lastMove.yDistance <= 0.0
                && (lastMove.touchedGround || lastMove.to.extraPropertiesValid && lastMove.to.resetCond)) {
            if (resetTo) {
                allowed = cc.sfStepHeight;
                thisMove.allowstep = true;
            } else {
                allowed = maxJumpGain + jumpGainMargin;
                thisMove.allowjump = true;
            }
            strict = false;
        } else {
            allowed = lastMove.yDistance * data.lastFrictionVertical - Magic.GRAVITY_ODD;
            strict = true;
        }
        return new double[]{allowed, strict ? 1.0 : 0.0};
    }

    private double handleMissingData(final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                     final double yDistance, final MovingData data, final MovingConfig cc,
                                     final double maxJumpGain, final double jumpGainMargin) {
        double allowed;
        tags.add(lastMove.valid ? "data_reset" : "data_missing");
        if (thisMove.yDistance > -(Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN) && yDistance < 0.0) {
            allowed = yDistance;
        } else if (thisMove.from.onGround || (lastMove.valid && lastMove.to.onGround)) {
            allowed = maxJumpGain + jumpGainMargin;
            if (lastMove.to.onGround && allowed < 0.1) {
                allowed = maxJumpGain + jumpGainMargin;
            }
            if (thisMove.to.onGround) {
                allowed = Math.max(cc.sfStepHeight, allowed);
            }
        } else if (Magic.skipPaper(thisMove, lastMove, data)) {
            allowed = Magic.PAPER_DIST;
            tags.add("skip_paper");
        } else {
            allowed = 0.0;
        }
        return allowed;
    }

    private double[] applyEnvelopeVelocityExemptions(final long now, final Player player,
            final PlayerLocation from, final PlayerLocation to, final double yDistance, final double yDistChange,
            final double vAllowedDistance, final boolean strictVdistRel, final boolean fromOnGround,
            final boolean toOnGround, final boolean resetFrom, final boolean resetTo, final PlayerMoveData thisMove, final PlayerMoveData lastMove,
            final MovingData data, final MovingConfig cc, final double maxJumpGain) {

        double vDistanceAboveLimit = 0.0;
        final double yDistDiffEx = yDistance - vAllowedDistance;
        final boolean honeyBlockCollision = MovingUtil.isCollideWithHB(from, to, data) && yDistance < -0.125 && yDistance > -0.128;
        final boolean GravityEffects = AirWorkarounds.oddJunction(from, to, yDistance, yDistChange, yDistDiffEx, maxJumpGain, resetTo, thisMove, lastMove,
                data, cc, resetFrom);
        final boolean TooBigMove = AirWorkarounds.outOfEnvelopeExemptions(yDistance, yDistDiffEx, lastMove, data, from, to, now, yDistChange, maxJumpGain, player, thisMove, resetTo);
        final boolean TooShortMove = AirWorkarounds.shortMoveExemptions(yDistance, yDistDiffEx, lastMove, data, from, to, now, strictVdistRel, maxJumpGain, vAllowedDistance, player, thisMove);
        final boolean TooFastFall = AirWorkarounds.fastFallExemptions(yDistance, yDistDiffEx, lastMove, data, from, to, now, strictVdistRel, yDistChange, resetTo, fromOnGround, toOnGround, maxJumpGain, player, thisMove, resetFrom);
        final boolean VEnvHack = AirWorkarounds.venvHacks(from, to, yDistance, yDistChange, thisMove, lastMove, data, resetFrom, resetTo);
        final boolean TooBigMoveNoData = AirWorkarounds.outOfEnvelopeNoData(yDistance, from, to, thisMove, resetTo, data);

        boolean vDistRelVL = false;
        if (yDistDiffEx > 0.0) {
            if (!(TooBigMove || TooBigMoveNoData || GravityEffects || (lastMove.toIsValid && honeyBlockCollision))) {
                vDistRelVL = true;
            }
        }
        else if (yDistance >= 0.0) {
            if (!(TooShortMove || GravityEffects)) {
                vDistRelVL = true;
            }
        }
        else {
            if (!(TooFastFall || GravityEffects || honeyBlockCollision)) {
                vDistRelVL = true;
            }
        }

        if (vDistRelVL && data.getOrUseVerticalVelocity(yDistance) == null) {
            vDistanceAboveLimit = Math.max(vDistanceAboveLimit, Math.abs(yDistance - vAllowedDistance));
            tags.add("vdistrel");
        }

        return new double[]{vDistanceAboveLimit, VEnvHack ? 1.0 : 0.0};
    }

    private double applyAbsoluteLimit(final long now, final Player player, final PlayerLocation from,
            final PlayerLocation to, final boolean toOnGround, final double yDistance, final boolean fromOnGround,
            final PlayerMoveData thisMove, final PlayerMoveData lastMove, final MovingData data, final MovingConfig cc,
            final IPlayerData pData, double vDistanceAboveLimit) {

        if (!pData.hasPermission(Permissions.MOVING_SURVIVALFLY_STEP, player) && yDistance > 0.0
                && !data.isVelocityJumpPhase() && data.hasSetBack()) {
            final double maxJumpHeight = data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier);
            final double totalVDistViolation = to.getY() - data.getSetBackY() - maxJumpHeight;
            if (totalVDistViolation > 0.0) {
                if (!AirWorkarounds.vDistSBExemptions(toOnGround, thisMove, lastMove, data, cc, now, player,
                        totalVDistViolation, yDistance, fromOnGround, tags, to, from)
                        && data.getOrUseVerticalVelocity(yDistance) == null) {
                    vDistanceAboveLimit = Math.max(vDistanceAboveLimit, Math.max(totalVDistViolation, 0.4));
                    tags.add("vdistsb(" + StringUtil.fdec3.format((to.getY() - data.getSetBackY())) + "/" + maxJumpHeight + ")");
                }
            }
        }
        return vDistanceAboveLimit;
    }

    private double checkAirStayTime(final boolean VEnvHack, final int maxJumpPhase, final double yDistance,
            final boolean resetFrom, final PlayerMoveData thisMove, final MovingData data, final MovingConfig cc,
            double vDistanceAboveLimit) {

        if (!VEnvHack && data.sfJumpPhase > maxJumpPhase && !data.isVelocityJumpPhase()) {
            if (yDistance >= Magic.GRAVITY_MIN && !resetFrom
                    && data.getOrUseVerticalVelocity(yDistance) == null) {
                vDistanceAboveLimit = Math.max(vDistanceAboveLimit, Math.abs(yDistance));
                tags.add("maxphase(" + data.sfJumpPhase + "/" + maxJumpPhase + ")");
            }
        }
        return vDistanceAboveLimit;
    }

    private double handleDirectionChange(final Player player, final PlayerLocation from, final double yDistance,
            final boolean InAirPhase, final boolean ChangedYDir, final PlayerMoveData thisMove,
            final PlayerMoveData lastMove, final MovingData data, final MovingConfig cc, double vDistanceAboveLimit) {

        if (!(InAirPhase && ChangedYDir)) {
            return vDistanceAboveLimit;
        }

        if (yDistance > 0.0) {
            return handleAscendingDirectionChange(yDistance, lastMove, data, vDistanceAboveLimit);
        }

        return handleDescendingDirectionChange(player, from, yDistance, thisMove, lastMove, data, cc,
                vDistanceAboveLimit);
    }

    private double handleAscendingDirectionChange(final double yDistance, final PlayerMoveData lastMove,
            final MovingData data, double vDistanceAboveLimit) {
        if (lastMove.touchedGround || lastMove.to.extraPropertiesValid && lastMove.to.resetCond) {
            tags.add("ychinc");
        }
        else if (data.bunnyhopDelay < 9 && !((lastMove.touchedGround || lastMove.from.onGroundOrResetCond)
                && lastMove.yDistance == 0D) && data.getOrUseVerticalVelocity(yDistance) == null) {
            vDistanceAboveLimit = Math.max(vDistanceAboveLimit, Math.abs(yDistance));
            tags.add("airjump");
        }
        else {
            tags.add("ychincair");
        }
        return vDistanceAboveLimit;
    }

    private double handleDescendingDirectionChange(final Player player, final PlayerLocation from,
            final double yDistance, final PlayerMoveData thisMove, final PlayerMoveData lastMove,
            final MovingData data, final MovingConfig cc, double vDistanceAboveLimit) {
        tags.add("ychdec");
        if (!data.sfLowJump && !data.sfNoLowJump && lastMove.toIsValid && lastMove.yDistance > 0.0
                && !data.isVelocityJumpPhase()) {
            final double setBackYDistance = from.getY() - data.getSetBackY();
            final double minJumpHeight = data.liftOffEnvelope.getMinJumpHeight(data.jumpAmplifier);
            if (setBackYDistance > 0.0 && setBackYDistance < minJumpHeight) {
                if (thisMove.headObstructed
                        || yDistance <= 0.0 && lastMove.headObstructed && lastMove.yDistance >= 0.0
                        || Bridge1_17.hasLeatherBootsOn(player)
                        && data.liftOffEnvelope == LiftOffEnvelope.POWDER_SNOW && lastMove.from.inPowderSnow
                        && lastMove.yDistance <= Magic.GRAVITY_MAX * 2.36 && lastMove.yDistance > 0.0
                        && thisMove.yDistance < 0.0) {
                    tags.add("lowjump_skip");
                }
                else if (data.getOrUseVerticalVelocity(yDistance) == null) {
                    vDistanceAboveLimit = Math.max(vDistanceAboveLimit, Math.abs(minJumpHeight - setBackYDistance));
                    data.sfLowJump = true;
                    Improbable.feed(player, (float) cc.yOnGround, System.currentTimeMillis());
                }
            }
        }
        return vDistanceAboveLimit;
    }

    private double applyVerticalAccounting(final long now, final PlayerLocation from, final PlayerLocation to,
            final double yDistance, double vDistanceAboveLimit, final boolean InAirPhase, final boolean ChangedYDir,
            final PlayerMoveData thisMove, final PlayerMoveData lastMove, final MovingData data, final MovingConfig cc) {

        if (InAirPhase && cc.survivalFlyAccountingV) {
            if (MovingUtil.isCollideWithHB(from, to, data) && thisMove.yDistance < 0.0 && thisMove.yDistance > -0.21) {
                handleResetVDist(data);
                data.vDistAcc.add((float) -0.2033);
            }
            else if (ChangedYDir && lastMove.yDistance > 0.0) {
                handleResetVDist(data);
                data.vDistAcc.add((float) yDistance);
            }
            else if (thisMove.verVelUsed == null && !(lastMove.from.inLiquid && Math.abs(yDistance) < 0.31
                    || data.timeRiptiding + 1000 > now)) {
                data.vDistAcc.add((float) yDistance);
                final double accAboveLimit = verticalAccounting(yDistance, data.vDistAcc, tags,
                        "vacc" + (data.isVelocityJumpPhase() ? "dirty" : ""));
                if (accAboveLimit > vDistanceAboveLimit && data.getOrUseVerticalVelocity(yDistance) == null) {
                    vDistanceAboveLimit = accAboveLimit;
                }
            }
            else {
                handleResetVDist(data);
            }
        }

        if (data.sfLowJump) {
            tags.add("lowjump");
        }
        return vDistanceAboveLimit;
    }



    /**
     * Demand that with time the values decrease.<br>
     * The ActionAccumulator instance must have 3 buckets, bucket 1 is checked against
     * bucket 2, 0 is ignored. [Vertical accounting: applies to both falling and jumping]<br>
     * NOTE: This just checks and adds to tags, no change to acc.
     *
     * @param yDistance
     * @param acc
     * @param tags
     * @param tag Tag to be added in case of a violation of this sub-check.
     * @return A violation value > 0.001, to be interpreted like a moving violation.
     */
    private static final double verticalAccounting(final double yDistance,
                                                   final ActionAccumulator acc, final ArrayList<String> tags,
                                                   final String tag) {

        final int count0 = acc.bucketCount(0);
        if (count0 > 0) {
            final int count1 = acc.bucketCount(1);
            if (count1 > 0) {
                final int cap = acc.bucketCapacity();
                final float sc0;
                sc0 = (count0 == cap) ? acc.bucketScore(0) :
                                        // Catch extreme changes quick.
                                        acc.bucketScore(0) * (float) cap / (float) count0 - Magic.GRAVITY_VACC * (float) (cap - count0);
                final float sc1 = acc.bucketScore(1);
                if (sc0 > sc1 - 3.0 * Magic.GRAVITY_VACC) {
                    if (yDistance <= -1.05 && sc1 < -8.0 && sc0 < -8.0) {
                        // High falling speeds may pass.
                        tags.add(tag + "grace");
                        return 0.0;
                    }
                    tags.add(tag);
                    return sc0 - (sc1 - 3.0 * Magic.GRAVITY_VACC);
                }
            }
        }
        return 0.0;
    }


    /**
     * After-failure checks for horizontal distance.
     * Buffer, velocity, bunnyhop, block move and reset-item.
     *
     * @param player
     * @param from
     * @param to
     * @param hAllowedDistance
     * @param hDistanceAboveLimit
     * @param sprinting
     * @param thisMove
     * @param lastMove
     * @param data
     * @param cc
     * @param skipPermChecks
     * @return hAllowedDistance, hDistanceAboveLimit, hFreedom
     */
    private double[] hDistAfterFailure(final Player player,
                                       final PlayerLocation from, final PlayerLocation to,
                                       double hAllowedDistance, double hDistanceAboveLimit, final boolean sprinting,
                                       final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                       final MovingData data, final MovingConfig cc, final IPlayerData pData,
                                       final boolean skipPermChecks) {

        final double speedAmplifier = mcAccess.getHandle().getFasterMovementAmplifier(player);

        // 1: Attempt to reset item on NoSlow Violation, if set so in the configuration.
        final double[] reset = attemptItemReset(player, from, to, hAllowedDistance, hDistanceAboveLimit,
                sprinting, thisMove, data, cc, pData);
        hAllowedDistance = reset[0];
        hDistanceAboveLimit = reset[1];

        // 2: Test bunny early, because it applies often and destroys as little as possible. 1st call
        // Strictly speaking, bunnyhopping backwards is not possible, so we should reset the bhop model in such case.
        // However, we'd need a better "ismovingbackwards" model first tho, as the current one in TrigUtil is unreliable.
        hDistanceAboveLimit = applyBunnyHop(from, to, player, hAllowedDistance, hDistanceAboveLimit, sprinting,
                thisMove, lastMove, data, cc, speedAmplifier);

        // 3: Check being moved by blocks.
        final double[] block = checkBlockMove(from, to, thisMove, data, cc, hAllowedDistance, hDistanceAboveLimit);
        hAllowedDistance = block[0];
        hDistanceAboveLimit = block[1];

        // 4: Check velocity.
        // See: https://github.com/NoCheatPlus/Issues/issues/374#issuecomment-296172316
        final double[] vel = applyVelocity(hDistanceAboveLimit, data);
        hDistanceAboveLimit = vel[0];
        double hFreedom = vel[1];

        // 5: Re-check for bunnyhopping if the hDistance is still above limit (2nd).
        if (hDistanceAboveLimit > 0.0) {
            hDistanceAboveLimit = applyBunnyHop(from, to, player, hAllowedDistance, hDistanceAboveLimit, sprinting,
                    thisMove, lastMove, data, cc, speedAmplifier);
        }

        // 6: Finally, check for the Horizontal buffer if the hDistance is still above limit.
        hDistanceAboveLimit = applyHorizontalBuffer(hDistanceAboveLimit, data, cc);

        // Add the hspeed tag on violation.
        if (hDistanceAboveLimit > 0.0) {
            tags.add("hspeed");
        }
        return new double[]{hAllowedDistance, hDistanceAboveLimit, hFreedom};
    }

    /** Step 1: Reset the held item if configured and possible. */
    private double[] attemptItemReset(final Player player, final PlayerLocation from, final PlayerLocation to,
                                      double hAllowedDistance, double hDistanceAboveLimit, final boolean sprinting,
                                      final PlayerMoveData thisMove, final MovingData data,
                                      final MovingConfig cc, final IPlayerData pData) {
        if (cc.survivalFlyResetItem && hDistanceAboveLimit > 0.0 && data.sfHorizontalBuffer <= 0.5
                && tags.contains("usingitem")) {
            tags.add("itemreset");
            // Handle through nms
            if (mcAccess.getHandle().resetActiveItem(player)) {
                data.isUsingItem = false;
                pData.requestUpdateInventory();
            }
            // Off hand (non nms)
            else if (Bridge1_9.hasGetItemInOffHand() && data.offHandUse) {
                ItemStack stack = Bridge1_9.getItemInOffHand(player);
                if (stack != null) {
                    if (ServerIsAtLeast1_13) {
                        if (player.isHandRaised()) {
                            // Does nothing
                        }
                        // False positive
                        else data.isUsingItem = false;
                    }
                    else {
                        player.getInventory().setItemInOffHand(stack);
                        data.isUsingItem = false;
                    }
                }
            }
            // Main hand (non nms)
            else if (!data.offHandUse) {
                ItemStack stack = Bridge1_9.getItemInMainHand(player);
                if (ServerIsAtLeast1_13) {
                    if (player.isHandRaised()) {
                        //data.olditemslot = player.getInventory().getHeldItemSlot();
                        //if (stack != null) player.setCooldown(stack.getType(), 10);
                        //player.getInventory().setHeldItemSlot((data.olditemslot + 1) % 9);
                        //data.changeslot = true;
                        // Does nothing
                    }
                    // False positive
                    else data.isUsingItem = false;
                }
                else {
                    if (stack != null) {
                        Bridge1_9.setItemInMainHand(player, stack);
                    }
                }
                data.isUsingItem = false;
            }
            if (!data.isUsingItem) {
                hAllowedDistance = setAllowedhDist(new AllowedDistanceContext(player, sprinting, thisMove,
                        data, cc, pData, from, to, true));
                hDistanceAboveLimit = thisMove.hDistance - hAllowedDistance;
            }
        }
        return new double[]{hAllowedDistance, hDistanceAboveLimit};
    }

    /** Step 2 and 5: Apply bunny hop adjustments. */
    private double applyBunnyHop(final PlayerLocation from, final PlayerLocation to, final Player player,
                                 final double hAllowedDistance, final double hDistanceAboveLimit, final boolean sprinting,
                                 final PlayerMoveData thisMove, final PlayerMoveData lastMove, final MovingData data,
                                 final MovingConfig cc, final double speedAmplifier) {
        return MagicBunny.bunnyHop(from, to, player, hAllowedDistance, hDistanceAboveLimit, sprinting, thisMove,
                lastMove, data, cc, tags, speedAmplifier);
    }

    /** Step 3: Movement caused by blocks such as pistons. */
    private double[] checkBlockMove(final PlayerLocation from, final PlayerLocation to, final PlayerMoveData thisMove,
                                    final MovingData data, final MovingConfig cc,
                                    double hAllowedDistance, double hDistanceAboveLimit) {
        if (cc.trackBlockMove && hDistanceAboveLimit > 0.0 && hDistanceAboveLimit < 1.025) {
            // Push by 0.49-0.51 in one direction. Also observed 1.02.
            final double xDistance = to.getX() - from.getX();
            final double zDistance = to.getZ() - from.getZ();
            if (Math.abs(xDistance) > 0.485 && Math.abs(xDistance) < 1.025
                && from.matchBlockChange(blockChangeTracker, data.blockChangeRef,
                        xDistance < 0 ? Direction.X_NEG : Direction.X_POS, 0.05)) {
                hAllowedDistance = thisMove.hDistance; // MAGIC
                hDistanceAboveLimit = 0.0;
            }
            else if (Math.abs(zDistance) > 0.485 && Math.abs(zDistance) < 1.025
                    && from.matchBlockChange(blockChangeTracker, data.blockChangeRef,
                        zDistance < 0 ? Direction.Z_NEG : Direction.Z_POS, 0.05)) {
                hAllowedDistance = thisMove.hDistance; // MAGIC
                hDistanceAboveLimit = 0.0;
            }
        }
        return new double[]{hAllowedDistance, hDistanceAboveLimit};
    }

    /** Step 4: Consume velocity for compensation if available. */
    private double[] applyVelocity(double hDistanceAboveLimit, final MovingData data) {
        double hFreedom = 0.0;
        if (hDistanceAboveLimit > 0.0) {
            hFreedom = data.getHorizontalFreedom();
            if (hFreedom < hDistanceAboveLimit) {
                // Use queued velocity if possible.
                hFreedom += data.useHorizontalVelocity(hDistanceAboveLimit - hFreedom);
            }
            if (hFreedom > 0.0) {
                tags.add("hvel");
                bufferUse = false; // Ensure players don't consume the buffer if velocity is present.
                hDistanceAboveLimit = Math.max(0.0, hDistanceAboveLimit - hFreedom);

                if (hDistanceAboveLimit <= 0.0) {
                    data.clearHAccounting();
                    tags.add("hvel_no_hacc");
                }
            }
        }
        return new double[]{hDistanceAboveLimit, hFreedom};
    }

    /** Step 6: Apply the horizontal buffer if configured. */
    private double applyHorizontalBuffer(double hDistanceAboveLimit, final MovingData data, final MovingConfig cc) {
        if (hDistanceAboveLimit > 0.0 && data.sfHorizontalBuffer > 0.0 && bufferUse) {
            final double amount = Math.min(data.sfHorizontalBuffer, hDistanceAboveLimit);
            hDistanceAboveLimit -= amount;
            data.sfHorizontalBuffer = Math.max(0.0, data.sfHorizontalBuffer - amount); // Ensure we never end up below zero.
            tags.add("hbufuse(" + data.sfHorizontalBuffer + "/" + cc.hBufMax + ")");

        }
        return hDistanceAboveLimit;
    }


    /**
     * Legitimate move: increase horizontal buffer somehow.
     * @param hDistance
     * @param amount Positive amount.
     * @param data
     */
    private void hBufRegain(final double hDistance, final double amount, final MovingData data, final MovingConfig cc) {
        /*
         *          - full resetting with harder conditions.
         *          - maximum regain amount.
         *          - reset or regain only every x blocks h distance.
         */
        data.sfHorizontalBuffer = Math.min(cc.hBufMax, data.sfHorizontalBuffer + amount);
    }


    /**
     * Inside liquids vertical speed checking. setFrictionJumpPhase must be set
     * externally.
     *
     * @param from
     * @param to
     * @param toOnGround
     * @param yDistance
     * @param data
     * @return vAllowedDistance, vDistanceAboveLimit
     */

    private double[] vDistLiquid(final PlayerMoveData thisMove, final PlayerLocation from, final PlayerLocation to,
                                 final boolean toOnGround, final double yDistance, final PlayerMoveData lastMove,
                                 final MovingData data, final Player player, final MovingConfig cc) {

        data.sfNoLowJump = true;
        final double yDistAbs = Math.abs(yDistance);
        final double baseSpeed = thisMove.from.onGround ? Magic.swimBaseSpeedV(Bridge1_13.isSwimming(player)) + 0.1 : Magic.swimBaseSpeedV(Bridge1_13.isSwimming(player));
        final PlayerMoveData pastMove2 = data.playerMoves.getSecondPastMove();

        if (yDistAbs <= baseSpeed) {
            return new double[]{baseSpeed, 0.0};
        }

        double[] res;

        res = handleBubbleStream(from, to, yDistance, yDistAbs, data);
        if (res != null) return res;

        res = checkWaterloggedBlocks(thisMove, from, toOnGround, yDistance, data, cc);
        if (res != null) return res;

        final double frictDist = lastMove.toIsValid ? Math.abs(lastMove.yDistance) * data.lastFrictionVertical : baseSpeed;

        res = applyFrictionEnvelope(thisMove, lastMove, pastMove2, yDistance, yDistAbs, frictDist, data);
        if (res != null) return res;

        res = applyLiquidWorkarounds(from, to, baseSpeed, frictDist, lastMove, data);
        if (res != null) return res;

        res = handleVerticalVelocity(yDistance, data);
        if (res != null) return res;

        return calculateViolation(player, yDistance, yDistAbs, baseSpeed, frictDist, lastMove);
    }
    private double[] handleBubbleStream(final PlayerLocation from, final PlayerLocation to, final double yDistance,
                                        final double yDistAbs, final MovingData data) {
        if (from.isInBubbleStream() || to.isInBubbleStream()) {
            if (from.isDraggedByBubbleStream() && to.isDraggedByBubbleStream()) {
                tags.add("bubblestream_drag");
                if (yDistance > 0.0 && data.insideBubbleStreamCount < 0) {
                    return new double[]{0.0, Math.abs(yDistance)};
                }
                return new double[]{Magic.bubbleStreamDescend, yDistAbs - Magic.bubbleStreamDescend};
            }
            tags.add("bubblestream_push(" + data.insideBubbleStreamCount + ")");
            if (yDistance < 0.0 && BlockProperties.isLiquid(from.getTypeIdAbove())) {
                return new double[]{0.0, Math.abs(yDistance)};
            }
            return new double[]{Magic.bubbleStreamAscend, yDistAbs - Magic.bubbleStreamAscend};
        }
        return null;
    }

    private double[] checkWaterloggedBlocks(final PlayerMoveData thisMove, final PlayerLocation from,
                                            final boolean toOnGround, final double yDistance, final MovingData data,
                                            final MovingConfig cc) {
        if (from.isOnGround() && !BlockProperties.isLiquid(from.getTypeIdAbove())
                && from.isInWaterLogged()
                && !from.isInBubbleStream() && !thisMove.headObstructed
                && !from.isSubmerged(0.75)) {
            data.liftOffEnvelope = LiftOffEnvelope.NORMAL;
            final double minJumpGain = data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier);
            final boolean step = (toOnGround || thisMove.to.resetCond) && yDistance > minJumpGain && yDistance <= cc.sfStepHeight;
            final double vAllowedDistance = step ? cc.sfStepHeight : minJumpGain;
            tags.add("liquidground");
            return new double[]{vAllowedDistance, yDistance - vAllowedDistance};
        }
        return null;
    }

    private double[] applyFrictionEnvelope(final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                           final PlayerMoveData pastMove2, final double yDistance,
                                           final double yDistAbs, final double frictDist, final MovingData data) {
        if (lastMove.toIsValid) {
            if (lastMove.yDistance < 0.0 && yDistance < 0.0 && yDistAbs < frictDist + Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN) {
                tags.add("frictionenv(desc)");
                return new double[]{-frictDist - Magic.GRAVITY_MAX - Magic.GRAVITY_SPAN, 0.0};
            }
            if (lastMove.yDistance > 0.0 && yDistance > 0.0 && yDistance < frictDist - Magic.GRAVITY_SPAN) {
                tags.add("frictionenv(asc)");
                return new double[]{frictDist - Magic.GRAVITY_SPAN, 0.0};
            }
            if (Bridge1_13.hasIsSwimming() && data.insideMediumCount < 19
                    && yDistance > 0.0 && lastMove.yDistance > 0.0 && yDistance <= lastMove.yDistance * 0.99
                    && thisMove.from.inWater && (thisMove.inWaterfall || pastMove2.inWaterfall)
                    && yDistance < LiftOffEnvelope.NORMAL.getMaxJumpGain(0.0)) {
                tags.add("waterfall(asc)");
                return new double[]{lastMove.yDistance * 0.99, 0.0};
            }
        }
        return null;
    }

    private double[] applyLiquidWorkarounds(final PlayerLocation from, final PlayerLocation to, final double baseSpeed,
                                            final double frictDist, final PlayerMoveData lastMove, final MovingData data) {
        final Double wRes = LiquidWorkarounds.liquidWorkarounds(from, to, baseSpeed, frictDist, lastMove, data);
        if (wRes != null) {
            return new double[]{wRes, 0.0};
        }
        return null;
    }

    private double[] handleVerticalVelocity(final double yDistance, final MovingData data) {
        if (data.getOrUseVerticalVelocity(yDistance) != null) {
            return new double[]{yDistance, 0.0};
        }
        return null;
    }

    /**
     * Reset bookkeeping related to vertical distance checks. This method
     * currently just clears the vertical distance accumulator but is kept
     * for future refactoring and unit tests.
     *
     * @param data
     *            moving data for the player
     */
    private void handleResetVDist(final MovingData data) {
        if (data != null) {
            data.clearAccounting();
        }
    }

    private double[] calculateViolation(final Player player, final double yDistance, final double yDistAbs,
                                        final double baseSpeed, final double frictDist, final PlayerMoveData lastMove) {
        tags.add(yDistance < 0.0 ? "swimdown" : "swimup");
        if (player.isSneaking() && reallySneaking.contains(player.getName())
                && yDistance > 0.0 && lastMove.yDistance > 0.0 && yDistance >= lastMove.yDistance) {
            return new double[]{0.0, yDistance};
        }
        final double vl1 = yDistAbs - baseSpeed;
        final double vl2 = Math.abs(yDistAbs - frictDist - (yDistance < 0.0 ? Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN : Magic.GRAVITY_MIN));
        if (vl1 <= vl2) {
            return new double[]{yDistance < 0.0 ? -baseSpeed : baseSpeed, vl1};
        }
        return new double[]{yDistance < 0.0 ? -frictDist - Magic.GRAVITY_MAX - Magic.GRAVITY_SPAN : frictDist - Magic.GRAVITY_SPAN, vl2};
    }

    /**
     * On-climbable vertical distance checking.
     * @param from
     * @param fromOnGround
     * @param toOnGround
     * @param lastMove
     * @param thisMove
     * @param yDistance
     * @param data
     * @return vAllowedDistance, vDistanceAboveLimit
     */
    private double[] vDistClimbable(final Player player, final PlayerLocation from, final PlayerLocation to,
                                    final boolean fromOnGround, final boolean toOnGround,
                                    final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                    final double yDistance, final MovingData data) {

        data.sfNoLowJump = true;
        data.clearActiveHorVel(); // Might want to clear ALL horizontal vel.
        double vDistanceAboveLimit = 0.0;
        double yDistAbs = Math.abs(yDistance);
        /** Climbing a ladder in water and exiting water for whatever reason speeds up the player a lot in that one transition ... */
        boolean waterStep = lastMove.from.inLiquid && yDistAbs < Magic.swimBaseSpeedV(Bridge1_13.hasIsSwimming());
        double vAllowedDistance = waterStep ? yDistAbs : yDistance < 0.0 ? Magic.climbSpeedDescend : Magic.climbSpeedAscend;
        final double jumpHeight = LiftOffEnvelope.NORMAL.getMaxJumpHeight(0.0) + (data.jumpAmplifier > 0 ? (0.6 + data.jumpAmplifier - 1.0) : 0.0);
        final double maxJumpGain = data.liftOffEnvelope.getMaxJumpGain(data.jumpAmplifier) + 0.005;
        // Quick, temporary fix for scaffolding block
        boolean scaffolding = from.isOnGround() && from.getBlockY() == Location.locToBlock(from.getY())
                              && yDistance > 0.0 && yDistance < maxJumpGain;

        if (yDistAbs > vAllowedDistance) {
            // Catch insta-ladder quick.
            if (from.isOnGround(jumpHeight, 0D, 0D, BlockFlags.F_CLIMBABLE)) {
                if (yDistance > maxJumpGain) {
                    vDistanceAboveLimit = yDistAbs - vAllowedDistance;
                    tags.add("climbstep");
                }
                else tags.add("climbheight("+ StringUtil.fdec3.format(yDistance) +")");
            }
            else if (!scaffolding) {
                vDistanceAboveLimit = yDistAbs - vAllowedDistance;
                tags.add("climbspeed");
            }
        }

        // Can't climb up with vine not attached to a solid block (legacy).
        if (yDistance > 0.0 && !thisMove.touchedGround && !from.canClimbUp(jumpHeight)) {
            vDistanceAboveLimit = yDistance;
            vAllowedDistance = 0.0;
            tags.add("climbdetached");
        }

        // Do allow friction with velocity.
        if (vDistanceAboveLimit > 0.0 && thisMove.yDistance > 0.0
            && lastMove.yDistance - (Magic.GRAVITY_MAX + Magic.GRAVITY_MIN) / 2.0 > thisMove.yDistance) {
            vDistanceAboveLimit = 0.0;
            vAllowedDistance = yDistance;
            tags.add("vfrict_climb");
        }

        // Do allow vertical velocity.
        if (vDistanceAboveLimit > 0.0 && data.getOrUseVerticalVelocity(yDistance) != null) {
            vDistanceAboveLimit = 0.0;
        }

        return new double[]{vAllowedDistance, vDistanceAboveLimit};
    }


    /**
     * In-web vertical distance checking.
     * @param player
     * @param from
     * @param to
     * @param toOnGround
     * @param hDistanceAboveLimit
     * @param yDistance
     * @param now
     * @param data
     * @param cc
     * @return vAllowedDistance, vDistanceAboveLimit
     */
    private double[] vDistWeb(final Player player, final PlayerMoveData thisMove,
                              final boolean toOnGround, final double hDistanceAboveLimit, final long now,
                              final MovingData data, final MovingConfig cc, final PlayerLocation from) {

        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final double yDistance = thisMove.yDistance;
        final boolean step = toOnGround && yDistance > 0.0 && yDistance <= cc.sfStepHeight
                || thisMove.from.inWeb && !lastMove.from.inWeb && yDistance <= cc.sfStepHeight;
        data.sfNoLowJump = true;
        data.jumpAmplifier = 0;

        if (yDistance >= 0.0) {
            return vDistWebAscend(thisMove, lastMove, step, from);
        } else {
            return vDistWebDescend(thisMove, lastMove, data);
        }
    }

    private double[] vDistWebAscend(final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                    final boolean step, final PlayerLocation from) {

        final double yDistance = thisMove.yDistance;
        final double vAllowedDistance;
        final double vDistanceAboveLimit;

        // Allow ascending if the player is on ground
        if (thisMove.from.onGround) {
            vAllowedDistance = 0.1;
            vDistanceAboveLimit = yDistance - vAllowedDistance;
        }
        // Bubble columns can slowly push the player upwards through the web.
        else if (from.isInBubbleStream() && !from.isDraggedByBubbleStream() && yDistance < Magic.bubbleStreamAscend) {
            vAllowedDistance = lastMove.yDistance * Magic.FRICTION_MEDIUM_WATER;
            vDistanceAboveLimit = yDistance - vAllowedDistance;
            tags.add("bubbleweb");
        }
        // Allow stepping anyway
        else if (step) {
            vAllowedDistance = yDistance;
            vDistanceAboveLimit = 0.0;
            tags.add("webstep");
        }
        // Don't allow players to ascend in web.
        else {
            vAllowedDistance = 0.0;
            vDistanceAboveLimit = yDistance;
        }

        if (vDistanceAboveLimit > 0.0) {
            tags.add("vweb");
        }
        return new double[]{vAllowedDistance, vDistanceAboveLimit};
    }

    private double[] vDistWebDescend(final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                     final MovingData data) {

        final double yDistance = thisMove.yDistance;
        final double vAllowedDistance;

        // Lenient on first move(s) in web.
        if (data.insideMediumCount < 4 && lastMove.yDistance <= 0.0) {
            vAllowedDistance = lastMove.yDistance * Magic.FRICTION_MEDIUM_AIR - Magic.GRAVITY_MAX;
        }
        // Ordinary.
        // We could be stricter but spamming WASD in a tower of webs results in random falling speed changes: ca. observed -0.058 (!? Mojang...)
        else {
            vAllowedDistance = -Magic.GRAVITY_MIN * Magic.FRICTION_MEDIUM_AIR;
        }
        final double vDistanceAboveLimit = yDistance < vAllowedDistance ? Math.abs(yDistance - vAllowedDistance) : 0.0;

        if (vDistanceAboveLimit > 0.0) {
            tags.add("vwebdesc");
        }
        return new double[]{vAllowedDistance, vDistanceAboveLimit};
    }


    /**
     * Berry bush vertical distance checking (1.14+)
     * @param player
     * @param from
     * @param to
     * @param toOnGround
     * @param hDistanceAboveLimit
     * @param yDistance
     * @param now
     * @param data
     * @param cc
     * @return vAllowedDistance, vDistanceAboveLimit
     */
    private double[] vDistBush(final Player player, final PlayerMoveData thisMove,
                               final boolean toOnGround, final double hDistanceAboveLimit, final long now,
                               final MovingData data, final MovingConfig cc, final PlayerLocation from,
                               final boolean fromOnGround) {

        final double yDistance = thisMove.yDistance;
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final double jumpGainMargin = 0.005;
        double vAllowedDistance, vDistanceAboveLimit;

        if (yDistance >= 0.0) {
            // Jump speed gain.
            if (thisMove.from.onGround || !lastMove.from.inBerryBush) {
                vAllowedDistance = data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier) + jumpGainMargin;
                vDistanceAboveLimit = yDistance - vAllowedDistance;
            }
            // Likewise webs, one can't ascend in berry bushes (demand immediate fall)
            else {
                vAllowedDistance = 0.0;
                vDistanceAboveLimit = yDistance;
            }
        }
        // (Falling speed is static, however if falling from high enough places, it can depend on how fast one "dives" in.)
        else {
            // Lenient on the first move(s) in bush
            if (data.insideMediumCount < 4 && lastMove.yDistance <= 0.0) {
                vAllowedDistance = lastMove.yDistance * data.lastFrictionVertical - Magic.GRAVITY_SPAN;
            }
            // Ordinary
            // (Falling speed seems to be kept reliably, unlike webs)
            else vAllowedDistance = -Magic.GRAVITY_MIN * data.lastFrictionVertical - 0.0005;
            vDistanceAboveLimit = yDistance < vAllowedDistance ? Math.abs(yDistance - vAllowedDistance) : 0.0;
        }
        // (We don't care about velocity here, though we may not be able to ignore PVP velocity)
        if (vDistanceAboveLimit > 0.0) {
            tags.add(yDistance >= 0.0 ? "vbush" : "vbushdesc");
        }
        return new double[]{vAllowedDistance, vDistanceAboveLimit};
    }


   /**
    * Powder snow vertical distance checking (1.17+): behaves similarly to a climbable block; handled in a separate method
    * because of its properties. (This block is ground with leather boots on)
    * @param yDistance
     * @param from
    * @param to
    * @param cc
    * @param data
    * @return vAllowedDistance, vDistanceAboveLimit
    *
    */
    private double[] vDistPowderSnow(final double yDistance, final PlayerLocation from, final PlayerLocation to,
                                     final MovingConfig cc, final MovingData data, final Player player) {

        boolean fall = false;
        double vAllowedDistance, vDistanceAboveLimit;
        final double yToBlock = from.getY() - from.getBlockY();

        if (yToBlock <= cc.yOnGround) {
            vAllowedDistance = data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier, 1.5);
        }
        else {
            if (Bridge1_17.hasLeatherBootsOn(player)) {
                vAllowedDistance = yDistance < 0.0 ? -Magic.snowClimbSpeedDescend : Magic.snowClimbSpeedAscend;
            }
            else {
                vAllowedDistance = -Magic.snowClimbSpeedDescend;
                fall = true;
            }
        }
        final double yDistDiffEx = yDistance - vAllowedDistance;
        final boolean violation = fall ? Math.abs(yDistDiffEx) > 0.05 : Math.abs(yDistance) > Math.abs(vAllowedDistance);
        vDistanceAboveLimit = violation ? yDistance : 0.0;
        // (Velocity?)
        if (vDistanceAboveLimit > 0.0) {
            tags.add(yDistance >= 0.0 ? "vsnowasc" : "vsnowdesc");
        }
        return new double[]{vAllowedDistance, vDistanceAboveLimit};
    }


    /**
     * Violation handling put here to have less code for the frequent processing of check.
     * @param now
     * @param result
     * @param player
     * @param from
     * @param to
     * @param data
     * @param cc
     * @return
     */
    private Location handleViolation(final long now, final double result,
                                    final Player player, final PlayerLocation from, final PlayerLocation to,
                                    final MovingData data, final MovingConfig cc){

        // Increment violation level.
        if (Double.isInfinite(data.survivalFlyVL)) data.survivalFlyVL = 0;
        data.survivalFlyVL += result;
        final ViolationData vd = new ViolationData(this, player, data.survivalFlyVL, result, cc.survivalFlyActions);
        if (vd.needsParameters()) {
            vd.setParameter(ParameterName.LOCATION_FROM, String.format(Locale.US, "%.2f, %.2f, %.2f", from.getX(), from.getY(), from.getZ()));
            vd.setParameter(ParameterName.LOCATION_TO, String.format(Locale.US, "%.2f, %.2f, %.2f", to.getX(), to.getY(), to.getZ()));
            vd.setParameter(ParameterName.DISTANCE, String.format(Locale.US, "%.2f", TrigUtil.distance(from, to)));
            vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
        }
        // Some resetting is done in MovingListener.
        if (executeActions(vd).willCancel()) {
            data.sfVLMoveCount = data.getPlayerMoveCount();
            // Set back + view direction of to (more smooth).
            return MovingUtil.getApplicableSetBackLocation(player, to.getYaw(), to.getPitch(), to, data, cc);
        }
        else {
            data.sfVLMoveCount = data.getPlayerMoveCount();
            data.clearAccounting();
            data.sfJumpPhase = 0;
            // Cancelled by other plugin, or no cancel set by configuration.
            return null;
        }
    }


    /**
     * Hover violations have to be handled in this check, because they are handled as SurvivalFly violations (needs executeActions).
     * @param player
     * @param loc
     * @param blockCache
     * @param cc
     * @param data
     */
    public final void handleHoverViolation(final Player player, final PlayerLocation loc, final MovingConfig cc, final MovingData data) {
        if (!validateMoveInputs(player, loc, loc, "handleHoverViolation") || cc == null || data == null) {
            return;
        }
        if (Double.isInfinite(data.survivalFlyVL)) data.survivalFlyVL = 0;
        data.survivalFlyVL += cc.sfHoverViolation;

        data.sfVLMoveCount = data.getPlayerMoveCount();
        data.sfVLInAir = true;
        final ViolationData vd = new ViolationData(this, player, data.survivalFlyVL, cc.sfHoverViolation, cc.survivalFlyActions);
        if (vd.needsParameters()) {
            vd.setParameter(ParameterName.LOCATION_FROM, String.format(Locale.US, "%.2f, %.2f, %.2f", loc.getX(), loc.getY(), loc.getZ()));
            vd.setParameter(ParameterName.LOCATION_TO, "(HOVER)");
            vd.setParameter(ParameterName.DISTANCE, "0.0(HOVER)");
            vd.setParameter(ParameterName.TAGS, "hover");
        }
        if (executeActions(vd).willCancel()) {
            // Set back or kick.
            final Location newTo = MovingUtil.getApplicableSetBackLocation(player, loc.getYaw(), loc.getPitch(), loc, data, cc);
            if (newTo != null) {
                data.prepareSetBack(newTo);
                player.teleport(newTo, BridgeMisc.TELEPORT_CAUSE_CORRECTION_OF_POSITION);
            }
            else {
                // Solve by extra actions ? Special case (probably never happens)?
                player.kickPlayer("Hovering?");
            }
        }
        else {
            // Ignore.
        }
    }


    /**
     * This is set with PlayerToggleSneak, to be able to distinguish players that are really sneaking from players that are set sneaking by a plugin.
     * @param player
     * @param sneaking
     */
    public void setReallySneaking(final Player player, final boolean sneaking) {
        if (player == null) {
            validateMoveInputs(null, null, null, "setReallySneaking");
            return;
        }
        if (sneaking) {
            reallySneaking.add(player.getName());
        }
        else {
            reallySneaking.remove(player.getName());
        }
    }



    /**
     * Debug output.
     * <p>
     * The formatting logic is split into helper methods for readability:
     * ground state information, distance and velocity metrics, accounting
     * statistics and tag listing.
     *
     * @param player
     * @param to
     * @param data
     * @param cc
     * @param hDistance
     * @param hAllowedDistance
     * @param hFreedom
     * @param yDistance
     * @param vAllowedDistance
     * @param fromOnGround
     * @param resetFrom
     * @param toOnGround
     * @param resetTo
     */
    private void outputDebug(final Player player, final PlayerLocation to,
                             final MovingData data, final MovingConfig cc,
                             final double hDistance, final double hAllowedDistance, final double hFreedom,
                             final double yDistance, final double vAllowedDistance,
                             final boolean fromOnGround, final boolean resetFrom,
                             final boolean toOnGround, final boolean resetTo,
                             final PlayerMoveData thisMove, double vDistanceAboveLimit) {

        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final double yDistDiffEx = yDistance - vAllowedDistance;
        final StringBuilder builder = new StringBuilder(500);

        builder.append(CheckUtils.getLogMessagePrefix(player, type));
        appendGroundStateInfo(builder, thisMove, fromOnGround, resetFrom, toOnGround, resetTo, data);
        appendDistanceVelocityMetrics(builder, lastMove, thisMove, hDistance, hAllowedDistance, hFreedom,
                yDistance, yDistDiffEx, vAllowedDistance, to, data, cc);
        appendAccountStatistics(builder, data, cc, resetFrom, resetTo);
        appendTagInfo(builder, player);

        if (!justUsedWorkarounds.isEmpty()) {
            builder.append("\n" + " Workarounds: " + StringUtil.join(justUsedWorkarounds, " , "));
        }
        builder.append("\n");
        NCPAPIProvider.getNoCheatPlusAPI().getLogManager().debug(Streams.TRACE_FILE, builder.toString());
    }

    /**
     * Append information about ground state and jump phase.
     */
    private void appendGroundStateInfo(final StringBuilder builder, final PlayerMoveData thisMove,
            final boolean fromOnGround, final boolean resetFrom, final boolean toOnGround,
            final boolean resetTo, final MovingData data) {
        builder.append("\nOnGround: "
                + (thisMove.headObstructed ? "(head obstr.) " : "")
                + (thisMove.touchedGroundWorkaround ? "(touched ground) " : "")
                + (fromOnGround ? "onground -> " : (resetFrom ? "resetcond -> " : "--- -> "))
                + (toOnGround ? "onground" : (resetTo ? "resetcond" : "---"))
                + ", jumpPhase: " + data.sfJumpPhase + ", LiftOff: " + data.liftOffEnvelope.name()
                + "(" + data.insideMediumCount + ")");
    }

    /**
     * Append distance and velocity related metrics.
     */
    private void appendDistanceVelocityMetrics(final StringBuilder builder, final PlayerMoveData lastMove,
            final PlayerMoveData thisMove, final double hDistance, final double hAllowedDistance,
            final double hFreedom, final double yDistance, final double yDistDiffEx,
            final double vAllowedDistance, final PlayerLocation to, final MovingData data, final MovingConfig cc) {
        final String hBuf = (data.sfHorizontalBuffer < cc.hBufMax
                ? (" / Buffer: " + StringUtil.fdec3.format(data.sfHorizontalBuffer)) : "");
        final String lostSprint = (data.lostSprintCount > 0 ? (" , lostSprint: " + data.lostSprintCount) : "");
        final String hVelUsed = hFreedom > 0 ? " / hVelUsed: " + StringUtil.fdec3.format(hFreedom) : "";
        final String dHDist = lastMove.toIsValid ? "(" + StringUtil.formatDiff(hDistance, lastMove.hDistance) + ")" : "";
        final String dYDist = lastMove.toIsValid ? "(" + StringUtil.formatDiff(yDistance, lastMove.yDistance) + ")" : "";
        final String hopTick = (data.momentumTick > 0 ? ("momentumTick: " + data.momentumTick) + " , " : "");
        final String frictionTick = ("keepFrictionTick= " + data.keepfrictiontick + " , ");
        builder.append("\n Tick counters: " + hopTick + frictionTick);
        builder.append("\n" + " hDist: " + StringUtil.fdec3.format(hDistance) + dHDist + " / hAD: "
                + StringUtil.fdec3.format(hAllowedDistance) + hBuf + lostSprint + hVelUsed
                + "\n" + " vDist: " + StringUtil.fdec3.format(yDistance) + dYDist + " / yDistDiffEx: "
                + StringUtil.fdec3.format(yDistDiffEx) + " / vAD: "
                + StringUtil.fdec3.format(vAllowedDistance) + " , setBackY: "
                + (data.hasSetBack() ? (data.getSetBackY() + " (setBackYDist: "
                + StringUtil.fdec3.format(to.getY() - data.getSetBackY()) + " / MaxJumpHeight: "
                + data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier) + ")") : "?"));
        if (lastMove.toIsValid) {
            builder.append("\n fdsq: " + StringUtil.fdec3.format(thisMove.distanceSquared / lastMove.distanceSquared));
            if (data.bunnyhopDelay > 0) {
                builder.append("\n Bunny ratios: " + "c/b(" + StringUtil.fdec3.format(hDistance / thisMove.hAllowedDistanceBase)
                        + ") / c/l(" + StringUtil.fdec3.format(hDistance / lastMove.hDistance) + ")");
            }
        }
        if (thisMove.verVelUsed != null) {
            builder.append(" , vVelUsed: " + thisMove.verVelUsed + " ");
        }
        data.addVerticalVelocity(builder);
        data.addHorizontalVelocity(builder);
    }

    /**
     * Append accounting related statistics.
     */
    private void appendAccountStatistics(final StringBuilder builder, final MovingData data, final MovingConfig cc,
            final boolean resetFrom, final boolean resetTo) {
        if (!resetFrom && !resetTo) {
            if (cc.survivalFlyAccountingV && data.vDistAcc.count() > data.vDistAcc.bucketCapacity()) {
                builder.append("\n" + " vAcc: " + data.vDistAcc.toInformalString());
            }
        }
        if (cc.survivalFlyAccountingH && data.hDistAcc.count() > 0) {
            builder.append("\n hAcc: " + StringUtil.fdec3.format(data.hDistAcc.score() / data.hDistAcc.count())
                    + "(" + (int) data.hDistAcc.count() + ")");
        }
    }

    /**
     * Append tag related information for the given player.
     */
    private void appendTagInfo(final StringBuilder builder, final Player player) {
        if (player.isSleeping()) {
            tags.add("sleeping");
        }
        if (player.getFoodLevel() <= 5 && player.isSprinting()) {
            // Exception: does not take into account latency.
            tags.add("lowfoodsprint");
        }
        if (Bridge1_9.isWearingElytra(player)) {
            // Just wearing (not isGliding).
            tags.add("elytra_off");
        }
        if (!tags.isEmpty()) {
            builder.append("\n" + " Tags: " + StringUtil.join(tags, "+"));
        }
    }


    private void logPostViolationTags(final Player player) {
        debug(player, "SurvivalFly Post violation handling tag update:\n" + StringUtil.join(tags, "+"));
    }
}
