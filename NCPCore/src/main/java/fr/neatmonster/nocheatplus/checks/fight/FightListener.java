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
package fr.neatmonster.nocheatplus.checks.fight;

import java.util.Iterator;

import org.bukkit.Location;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.CheckListener;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.combined.Combined;
import fr.neatmonster.nocheatplus.checks.combined.Improbable;
import fr.neatmonster.nocheatplus.checks.inventory.Items;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.location.tracking.LocationTrace;
import fr.neatmonster.nocheatplus.checks.moving.location.tracking.LocationTrace.ITraceEntry;
import fr.neatmonster.nocheatplus.checks.moving.model.LiftOffEnvelope;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveInfo;
import fr.neatmonster.nocheatplus.checks.moving.player.UnusedVelocity;
import fr.neatmonster.nocheatplus.checks.moving.util.AuxMoving;
import fr.neatmonster.nocheatplus.checks.moving.util.MovingUtil;
import fr.neatmonster.nocheatplus.checks.moving.velocity.VelocityFlags;
import fr.neatmonster.nocheatplus.checks.net.NetConfig;
import fr.neatmonster.nocheatplus.checks.net.NetData;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.BridgeEnchant;
import fr.neatmonster.nocheatplus.compat.BridgeHealth;
import fr.neatmonster.nocheatplus.compat.IBridgeCrossPlugin;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.components.data.ICheckData;
import fr.neatmonster.nocheatplus.components.data.IData;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.components.registry.factory.IFactoryOne;
import fr.neatmonster.nocheatplus.components.registry.feature.JoinLeaveListener;
import fr.neatmonster.nocheatplus.penalties.DefaultPenaltyList;
import fr.neatmonster.nocheatplus.penalties.IPenaltyList;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.players.PlayerFactoryArgument;
import fr.neatmonster.nocheatplus.stats.Counters;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.utilities.build.BuildParameters;
import fr.neatmonster.nocheatplus.utilities.location.LocUtil;
import fr.neatmonster.nocheatplus.utilities.location.TrigUtil;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.worlds.WorldFactoryArgument;

/**
 * Central location to listen to events that are relevant for the fight checks.<br>
 * This listener is registered after the CombinedListener.
 * 
 * @see FightEvent
 */
public class FightListener extends CheckListener implements JoinLeaveListener{

    /** The angle check. */
    private final Angle angle = addCheck(new Angle());

    /** The critical check. */
    private final Critical critical = addCheck(new Critical());

    /** The direction check. */
    private final Direction direction = addCheck(new Direction());

    /** Faster health regeneration check. */
    private final FastHeal fastHeal = addCheck(new FastHeal());

    /** The god mode check. */
    private final GodMode godMode = addCheck(new GodMode());

    /** The impossible hit check */
    private final ImpossibleHit impossibleHit = addCheck(new ImpossibleHit());

    /** The no swing check. */
    private final NoSwing noSwing = addCheck(new NoSwing());

    /** The reach check. */
    private final Reach  reach = addCheck(new Reach());

    /** The self hit check */
    private final SelfHit selfHit = addCheck(new SelfHit());

    /** The speed check. */
    private final Speed speed = addCheck(new Speed());

    /** The visible check. */
    private final Visible visible = addCheck(new Visible());

    /** For temporary use: LocUtil.clone before passing deeply, call setWorld(null) after use. */
    private final Location useLoc1 = new Location(null, 0, 0, 0);

    /** For temporary use: LocUtil.clone before passing deeply, call setWorld(null) after use. */
    private final Location useLoc2 = new Location(null, 0, 0, 0);

    /** Auxiliary utilities for moving */
    private final AuxMoving auxMoving = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstance(AuxMoving.class);
    
    /* Debug */
    private final Counters counters = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstance(Counters.class);

    /* Debug */
    private final int idCancelDead = counters.registerKey("cancel.dead");

    // Assume it to stay the same all time.
    private final IGenericInstanceHandle<IBridgeCrossPlugin> crossPlugin = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstanceHandle(IBridgeCrossPlugin.class);

    @SuppressWarnings("unchecked")
    public FightListener() {
        super(CheckType.FIGHT);
        final NoCheatPlusAPI api = NCPAPIProvider.getNoCheatPlusAPI();
        api.register(api.newRegistrationContext()
                // FightConfig
                .registerConfigWorld(FightConfig.class)
                .factory(arg -> new FightConfig(arg.worldData))
                .registerConfigTypesPlayer()
                .context() //
                // FightData
                .registerDataPlayer(FightData.class)
                .factory(arg -> new FightData(arg.playerData.getGenericInstance(FightConfig.class)))
                .addToGroups(CheckType.FIGHT, false, IData.class, ICheckData.class)
                .removeSubCheckData(CheckType.FIGHT, true)
                .context() //
                );
    }

    /**
     * A player attacked something with DamageCause ENTITY_ATTACK.
     * 
     * @param player
     *            The attacking player.
     * @param damaged
     * @param originalDamage
     *            Damage before applying modifiers.
     * @param finalDamage
     *            Damage after applying modifiers.
     * @param tick
     * @param data
     * @return
     */
    private boolean handleNormalDamage(final Player player, final boolean attackerIsFake,
                                       final Entity damaged, final boolean damagedIsFake,
                                       final double originalDamage, final double finalDamage,
                                       final int tick, final FightData data, final IPlayerData pData,
                                       final IPenaltyList penaltyList) {

        if (player == null || damaged == null || data == null || pData == null) {
            return false;
        }

        final FightConfig cc = pData.getGenericInstance(FightConfig.class);
        final MovingConfig mCc = pData.getGenericInstance(MovingConfig.class);
        final MovingData mData = pData.getGenericInstance(MovingData.class);

        boolean cancelled = Items.checkIllegalEnchantmentsAllHands(player, pData);
        final boolean debug = pData.isDebugActive(checkType);
        final String worldName = player.getWorld().getName();
        final long now = System.currentTimeMillis();
        final boolean worldChanged = !worldName.equals(data.lastWorld);
        final Location loc = player.getLocation(useLoc1);
        final Location damagedLoc = damaged.getLocation(useLoc2);

        final TargetMoveInfo moveInfo = computeTargetMoveInfo(data, damagedLoc, tick, worldChanged);
        final double targetMove = moveInfo.targetMove;
        final int tickAge = moveInfo.tickAge;
        final long msAge = moveInfo.msAge;
        final double normalizedMove = moveInfo.normalizedMove;

        final DamagedInfo dmgInfo = gatherDamagedInfo(player, damaged, damagedIsFake, tick, damagedLoc,
                pData, cc, data, debug);
        final Player damagedPlayer = dmgInfo.player;
        final LocationTrace damagedTrace = dmgInfo.trace;
        cancelled |= dmgInfo.cancelled;

        // Log generic properties of this attack.
        if (debug) {
            debug(player, "Attacks " + (damagedPlayer == null ? ("entity " + damaged.getType()) : ("player" + damagedPlayer.getName())) + " damage=" + (finalDamage == originalDamage ? finalDamage : (originalDamage + "/" + finalDamage)));
        }

        cancelled |= applyDeadChecks(cc, player, damaged, data);

        if (handleSweepAttack(player, originalDamage, loc, tick, data, debug)) {
            cleanupLocations();
            return cancelled;
        }

        if (handleThorns(damaged, originalDamage, tick, data)) {
            cleanupLocations();
            return cancelled;
        }



        cancelled |= runCombatChecks(player, damaged, damagedIsFake, loc, damagedLoc, data, pData, cc, mCc,
                mData, penaltyList, now, normalizedMove, debug, damagedTrace, tick);

        cancelled |= checkAngle(player, loc, damaged, worldChanged, data, cc, pData, worldName, now, debug);

        updateLastAttackData(data, worldName, tick, damagedLoc);
        // Care for the "lost sprint problem": sprint resets, client moves as if still...
        // If this is just in-air, model with friction, so this can be removed.
        // Use stored distance calculation same as reach check?
        // For pvp: make use of "player was there" heuristic later on.
        // Confine further with simple pre-conditions.
        // Evaluate if moving traces can help here.
        if (!cancelled) {
            checkLostSprint(player, loc, damagedLoc, now, mData, mCc, pData, debug);
        }

        cancelled |= applyAttackPenalty(player, data, now, debug);

        cleanupLocations();
        return cancelled;
    }

    /**
     * Quick split-off: Checks using a location trace.
     * @param player
     * @param loc
     * @param data
     * @param cc
     * @param damaged
     * @param damagedPlayer
     * @param damagedLoc
     * @param damagedTrace
     * @param tick
     * @param reachEnabled
     * @param directionEnabled
     * @return If to cancel (true) or not (false).
     */
    private boolean locationTraceChecks(final Player player, final Location loc,
                                        final FightData data, final FightConfig cc, final IPlayerData pData,
                                        final Entity damaged, final boolean damagedIsFake,
                                        final Location damagedLoc, LocationTrace damagedTrace, 
                                        final long tick, final long now, final boolean debug,
                                        final boolean reachEnabled, final boolean directionEnabled) {

        // Order / splitting off generic stuff.
        /*
         *  Abstract: interface with common setup/loop/post routine, only
         * pass the ACTIVATED checks on to here (e.g. IFightLoopCheck...
         * loopChecks). Support an arbitrary number of loop checks, special
         * behavior -> interface and/or order within loopChecks.
         */
        // (Might pass generic context to factories, for shared + heavy properties.)
        final ReachContext reachContext = reachEnabled ? reach.getContext(player, loc, damaged, damagedLoc, data, cc) : null;
        final DirectionContext directionContext = directionEnabled ? direction.getContext(player, loc, damaged, damagedIsFake, damagedLoc, data, cc) : null;
        final long traceOldest = tick - cc.loopMaxLatencyTicks; // Set by latency-window.
        // Iterating direction, which, static/dynamic choice.
        final Iterator<ITraceEntry> traceIt = damagedTrace.maxAgeIterator(traceOldest);
        boolean cancelled = false;

        final TraceResult result = evaluateTraceEntries(player, loc, damaged, traceIt,
                reachContext, directionContext, data, cc, reachEnabled, directionEnabled, now);

        final boolean violation = result.violation;
        final long latencyEstimate = result.latencyEstimate;
        final ITraceEntry successEntry = result.successEntry;

        // How to treat mixed state: violation && reachPassed && directionPassed [current: use min violation // thinkable: silent cancel, if actions have cancel (!)]
        // Adapt according to strictness settings?
        // violation vs. reachPassed + directionPassed (current: fail one = fail all).
        if (reachEnabled) {
            // Might ignore if already cancelled by mixed/silent cancel.
            if (reach.loopFinish(player, loc, damaged, reachContext, successEntry, violation, data, cc, pData)) {
                cancelled = true;
            }
        }
        if (directionEnabled) {
            // Might ignore if already cancelled.
            if (direction.loopFinish(player, loc, damaged, directionContext, violation, data, cc)) {
                cancelled = true;
            }
        }

        // Log exact state, probably record min/max latency (individually).
        if (debug && latencyEstimate >= 0) {
            debug(player, "Latency estimate: " + latencyEstimate + " ms."); // FCFS rather, at present.
        }
        return cancelled;
    }

    private static final class TraceResult {
        final boolean violation;
        final long latencyEstimate;
        final ITraceEntry successEntry;

        TraceResult(final boolean violation, final long latencyEstimate, final ITraceEntry successEntry) {
            this.violation = violation;
            this.latencyEstimate = latencyEstimate;
            this.successEntry = successEntry;
        }
    }

    private TraceResult evaluateTraceEntries(final Player player, final Location loc, final Entity damaged,
                                             final Iterator<ITraceEntry> traceIt, final ReachContext reachContext,
                                             final DirectionContext directionContext, final FightData data,
                                             final FightConfig cc, final boolean reachEnabled,
                                             final boolean directionEnabled, final long now) {

        boolean violation = true;
        boolean reachPassed = !reachEnabled;
        boolean directionPassed = !directionEnabled;
        long latencyEstimate = -1;
        ITraceEntry successEntry = null;

        while (traceIt.hasNext()) {
            final ITraceEntry entry = traceIt.next();
            boolean thisPassed = true;
            if (reachEnabled) {
                if (reach.loopCheck(player, loc, damaged, entry, reachContext, data, cc)) {
                    thisPassed = false;
                } else {
                    reachPassed = true;
                }
            }
            if (directionEnabled && (reachPassed || !directionPassed)) {
                if (direction.loopCheck(player, loc, damaged, entry, directionContext, data, cc)) {
                    thisPassed = false;
                } else {
                    directionPassed = true;
                }
            }
            if (thisPassed) {
                violation = false;
                latencyEstimate = now - entry.getTime();
                successEntry = entry;
                break;
            }
        }

        return new TraceResult(violation, latencyEstimate, successEntry);
    }

    /**
     * Calculate relative movement of the damaged entity since last attack.
     * <p>
     * The {@code tick} parameter must represent the current server tick at the
     * moment of the attack. It must be non-negative and monotonically
     * increasing. If the tick is older than the last known attack tick or if the
     * provided values are otherwise invalid, a default {@link TargetMoveInfo} is
     * returned.
     * </p>
     */
    private TargetMoveInfo computeTargetMoveInfo(final FightData data, final Location damagedLoc,
                                                 final int tick, final boolean worldChanged) {

        if (tick < 0) {
            throw new IllegalArgumentException("tick must be >= 0");
        }

        if (isInvalidMoveInfo(data, damagedLoc, tick, worldChanged)) {
            return DEFAULT_TARGET_MOVE_INFO;
        }
        final int age = tick - data.lastAttackTick;
        final double move = TrigUtil.distance(data.lastAttackedX, data.lastAttackedZ,
                                              damagedLoc.getX(), damagedLoc.getZ());
        final long msAge = (long) (50f * TickTask.getLag(50L * age, true) * (float) age);
        final double normalized = msAge == 0 ? move : move * Math.min(20.0, 1000.0 / (double) msAge);
        return new TargetMoveInfo(age, move, msAge, normalized);
    }

    private static boolean isInvalidMoveInfo(final FightData data, final Location damagedLoc,
                                             final int tick, final boolean worldChanged) {
        return data == null || damagedLoc == null
                || data.lastAttackedX == Double.MAX_VALUE
                || tick < data.lastAttackTick
                || worldChanged
                || tick - data.lastAttackTick > 20;
    }

    /**
     * Detect potential lost sprint when attacking.
     */
    private void checkLostSprint(final Player player, final Location loc, final Location damagedLoc,
                                 final long now, final MovingData mData, final MovingConfig mCc,
                                 final IPlayerData pData, final boolean debug) {

        if (TrigUtil.distance(loc.getX(), loc.getZ(), damagedLoc.getX(), damagedLoc.getZ()) >= 4.5) {
            return;
        }

        final PlayerMoveData lastMove = mData.playerMoves.getFirstPastMove();
        if (!lastMove.valid || mData.liftOffEnvelope != LiftOffEnvelope.NORMAL) {
            return;
        }

        final double hDist = TrigUtil.xzDistance(loc, lastMove.from);
        if (hDist < 0.23) {
            return;
        }

        final PlayerMoveInfo moveInfo = auxMoving.usePlayerMoveInfo();
        moveInfo.set(player, loc, null, mCc.yOnGround);
        if (now <= mData.timeSprinting + mCc.sprintingGrace
                && MovingUtil.shouldCheckSurvivalFly(player, moveInfo.from, moveInfo.to, mData, mCc, pData)) {
            mData.lostSprintCount = 7;
            if ((debug || pData.isDebugActive(CheckType.MOVING)) && BuildParameters.debugLevel > 0) {
                debug(player, "lostsprint: hDist to last from: " + hDist + " | targetdist="
                        + TrigUtil.distance(loc.getX(), loc.getZ(), damagedLoc.getX(), damagedLoc.getZ())
                        + " | sprinting=" + player.isSprinting() + " | food=" + player.getFoodLevel()
                        + " | hbuf=" + mData.sfHorizontalBuffer);
            }
        }
        auxMoving.returnPlayerMoveInfo(moveInfo);
    }

    private static final TargetMoveInfo DEFAULT_TARGET_MOVE_INFO = new TargetMoveInfo(0, 0.0, 0, 0.0);

    private static final class TargetMoveInfo {
        final int tickAge;
        final double targetMove;
        final long msAge;
        final double normalizedMove;

        TargetMoveInfo(final int tickAge, final double targetMove, final long msAge, final double normalizedMove) {
            this.tickAge = tickAge;
            this.targetMove = targetMove;
            this.msAge = msAge;
            this.normalizedMove = normalizedMove;
        }
    }

    private static final class DamagedInfo {
        final Player player;
        final LocationTrace trace;
        final boolean cancelled;

        DamagedInfo(final Player player, final LocationTrace trace, final boolean cancelled) {
            this.player = player;
            this.trace = trace;
            this.cancelled = cancelled;
        }
    }

    private DamagedInfo gatherDamagedInfo(final Player attacker, final Entity damaged,
            final boolean damagedIsFake, final int tick, final Location damagedLoc,
            final IPlayerData pData, final FightConfig cc, final FightData data, final boolean debug) {

        if (damaged instanceof Player) {
            final Player damagedPlayer = (Player) damaged;
            if (debug && DataManager.getPlayerData(damagedPlayer)
                    .hasPermission(Permissions.ADMINISTRATION_DEBUG, damagedPlayer)) {
                damagedPlayer.sendMessage("Attacked by " + attacker.getName() + ": inv="
                        + mcAccess.getHandle().getInvulnerableTicks(damagedPlayer) + " ndt="
                        + damagedPlayer.getNoDamageTicks());
            }
            boolean cancelled = false;
            if (selfHit.isEnabled(attacker, pData) && selfHit.check(attacker, damagedPlayer, data, cc)) {
                cancelled = true;
            }
            final LocationTrace trace = DataManager.getPlayerData(damagedPlayer)
                    .getGenericInstance(MovingData.class)
                    .updateTrace(damagedPlayer, damagedLoc, tick, damagedIsFake ? null : mcAccess.getHandle());
            return new DamagedInfo(damagedPlayer, trace, cancelled);
        }

        return new DamagedInfo(null, null, false);
    }

    private boolean applyDeadChecks(final FightConfig cc, final Player attacker, final Entity damaged,
            final FightData data) {
        if (!cc.cancelDead) {
            return false;
        }
        if (damaged.isDead()) {
            return true;
        }
        return attacker.isDead() && data.damageTakenByEntityTick != TickTask.getTick();
    }

    private boolean handleSweepAttack(final Player player, final double originalDamage, final Location loc,
            final int tick, final FightData data, final boolean debug) {
        if (BridgeHealth.DAMAGE_SWEEP != null) {
            return false;
        }
        final int locHashCode = LocUtil.hashCode(loc);
        if (originalDamage == 1.0) {
            if (tick == data.sweepTick && locHashCode == data.sweepLocationHashCode) {
                if (debug) {
                    debug(player, "(Assume sweep attack follow up damage.)");
                }
                return true;
            }
        } else {
            data.sweepTick = tick;
            data.sweepLocationHashCode = locHashCode;
        }
        return false;
    }

    private boolean handleThorns(final Entity damaged, final double originalDamage, final int tick,
            final FightData data) {
        if (BridgeHealth.DAMAGE_THORNS == null && originalDamage <= 4.0
                && tick == data.damageTakenByEntityTick && data.thornsId != Integer.MIN_VALUE
                && data.thornsId == damaged.getEntityId()) {
            data.thornsId = Integer.MIN_VALUE;
            return true;
        }
        data.thornsId = Integer.MIN_VALUE;
        return false;
    }

    private boolean runCombatChecks(final Player player, final Entity damaged, final boolean damagedIsFake,
            final Location loc, final Location damagedLoc, final FightData data, final IPlayerData pData,
            final FightConfig cc, final MovingConfig mCc, final MovingData mData, final IPenaltyList penaltyList,
            final long now, final double normalizedMove, final boolean debug, final LocationTrace damagedTrace,
            final int tick) {

        boolean cancelled = false;

        cancelled |= checkSpeed(player, data, cc, pData, normalizedMove, now);

        if (!cancelled) {
            cancelled |= checkCritical(player, loc, data, cc, pData, penaltyList);
        }

        if (!cancelled) {
            cancelled |= checkNoSwing(player, mData, pData, cc, now, data);
        }

        if (!cancelled) {
            cancelled |= checkImpossibleHit(player, mCc, data, cc, pData);
        }

        if (!cancelled) {
            cancelled |= checkVisibility(player, loc, damaged, damagedIsFake, damagedLoc, data, cc, pData);
        }

        if (!cancelled) {
            cancelled |= checkReachAndDirection(player, damaged, damagedIsFake, loc, damagedLoc, data, pData,
                    cc, mData, damagedTrace, tick, now, debug);
        }

        return cancelled;
    }

    private boolean checkSpeed(final Player player, final FightData data, final FightConfig cc,
            final IPlayerData pData, final double normalizedMove, final long now) {
        if (player == null || pData == null || !speed.isEnabled(player, pData)) {
            return false;
        }
        if (speed.check(player, now, data, cc, pData)) {
            if (data.speedVL > 50) {
                if (cc.speedImprobableWeight > 0.0f && !cc.speedImprobableFeedOnly) {
                    Improbable.check(player, cc.speedImprobableWeight, now, "fight.speed", pData);
                }
            } else if (cc.speedImprobableWeight > 0.0f) {
                Improbable.feed(player, cc.speedImprobableWeight, now);
            }
            return true;
        }
        if (normalizedMove > 2.0 && cc.speedImprobableWeight > 0.0f && !cc.speedImprobableFeedOnly
                && Improbable.check(player, cc.speedImprobableWeight, now, "fight.speed", pData)) {
            return true;
        }
        return false;
    }

    private boolean checkCritical(final Player player, final Location loc, final FightData data, final FightConfig cc,
            final IPlayerData pData, final IPenaltyList penaltyList) {
        return player != null && pData != null && critical.isEnabled(player, pData)
                && critical.check(player, loc, data, cc, pData, penaltyList);
    }

    private boolean checkNoSwing(final Player player, final MovingData mData, final IPlayerData pData,
            final FightConfig cc, final long now, final FightData data) {
        return player != null && pData != null && mData.timeRiptiding + 3000 < now
                && noSwing.isEnabled(player, pData) && noSwing.check(player, data, cc);
    }

    private boolean checkImpossibleHit(final Player player, final MovingConfig mCc, final FightData data,
            final FightConfig cc, final IPlayerData pData) {
        if (player == null || pData == null || !impossibleHit.isEnabled(player, pData)) {
            return false;
        }
        if (impossibleHit.check(player, data, cc, pData,
                mCc.survivalFlyResetItem && mcAccess.getHandle().resetActiveItem(player))) {
            if (cc.impossibleHitImprobableWeight > 0.0f) {
                Improbable.feed(player, cc.impossibleHitImprobableWeight, System.currentTimeMillis());
            }
            return true;
        }
        return false;
    }

    private boolean checkVisibility(final Player player, final Location loc, final Entity damaged,
            final boolean damagedIsFake, final Location damagedLoc, final FightData data, final FightConfig cc,
            final IPlayerData pData) {
        return player != null && pData != null && visible.isEnabled(player, pData)
                && visible.check(player, loc, damaged, damagedIsFake, damagedLoc, data, cc);
    }

    private boolean checkReachAndDirection(final Player player, final Entity damaged, final boolean damagedIsFake,
            final Location loc, final Location damagedLoc, final FightData data, final IPlayerData pData,
            final FightConfig cc, final MovingData mData, final LocationTrace damagedTrace, final int tick,
            final long now, final boolean debug) {

        if (player == null || pData == null) {
            return false;
        }
        final boolean isDamagedPlayer = damaged instanceof Player;
        final boolean reachEnabled = reach.isEnabled(player, pData) && isDamagedPlayer;
        final boolean directionEnabled = direction.isEnabled(player, pData) && mData.timeRiptiding + 3000 < now;
        if (!reachEnabled && !directionEnabled) {
            return false;
        }
        if (damagedTrace != null) {
            return locationTraceChecks(player, loc, data, cc, pData, damaged, damagedIsFake, damagedLoc, damagedTrace,
                    tick, now, debug, reachEnabled, directionEnabled);
        }
        boolean cancelled = false;
        if (reachEnabled && reach.check(player, loc, damaged, damagedIsFake, damagedLoc, data, cc, pData)) {
            cancelled = true;
        }
        if (directionEnabled && direction.check(player, loc, damaged, damagedIsFake, damagedLoc, data, cc)) {
            cancelled = true;
        }
        return cancelled;
    }

    private boolean checkAngle(final Player player, final Location loc, final Entity damaged,
            final boolean worldChanged, final FightData data, final FightConfig cc, final IPlayerData pData,
            final String worldName, final long now, final boolean debug) {
        boolean cancelled = false;
        if (angle.isEnabled(player, pData)) {
            if (Combined.checkYawRate(player, loc.getYaw(), now, worldName,
                    pData.isCheckActive(CheckType.COMBINED_YAWRATE, player), pData)) {
                cancelled = true;
            }
            if (angle.check(player, loc, damaged, worldChanged, data, cc, pData)) {
                if (!cancelled && debug) {
                    debug(player, "FIGHT_ANGLE cancel without yawrate cancel.");
                }
                cancelled = true;
            }
        }
        return cancelled;
    }

    private void updateLastAttackData(final FightData data, final String worldName, final int tick,
            final Location damagedLoc) {
        data.lastWorld = worldName;
        data.lastAttackTick = tick;
        data.lastAttackedX = damagedLoc.getX();
        data.lastAttackedY = damagedLoc.getY();
        data.lastAttackedZ = damagedLoc.getZ();
    }

    private boolean applyAttackPenalty(final Player player, final FightData data, final long now,
            final boolean debug) {
        if (data.attackPenalty.isPenalty(now)) {
            if (debug && player != null) {
                debug(player, "~ attack penalty.");
            }
            return true;
        }
        return false;
    }

    private void cleanupLocations() {
        useLoc1.setWorld(null);
        useLoc2.setWorld(null);
    }

    /**
     * We listen to EntityDamage events for obvious reasons.
     * 
     * @param event
     *            the event
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityDamage(final EntityDamageEvent event) {

        final Entity damaged = event.getEntity();
        final Player damagedPlayer = damaged instanceof Player ? (Player) damaged : null;
        final FightData damagedData;
        final boolean damagedIsDead = damaged.isDead();
        final boolean damagedIsFake = !crossPlugin.getHandle().isNativeEntity(damaged);
        IPenaltyList penaltyList = null;

        if (damagedPlayer != null) {
            
            final IPlayerData damagedPData = DataManager.getPlayerData(damagedPlayer);
            damagedData = damagedPData.getGenericInstance(FightData.class);
            if (!damagedIsDead) {
                // God mode check.
                // (Do not test the savage.)
                if (damagedPData.isCheckActive(CheckType.FIGHT_GODMODE, damagedPlayer)) {
                    if (penaltyList == null) {
                        penaltyList = new DefaultPenaltyList();
                    }
                    if (godMode.check(damagedPlayer, damagedIsFake, BridgeHealth.getRawDamage(event), damagedData, damagedPData)) {
                        // It requested to "cancel" the players invulnerability, so set their noDamageTicks to 0.
                        damagedPlayer.setNoDamageTicks(0);
                    }
                }
                // Adjust buffer for fast heal checks.
                if (BridgeHealth.getHealth(damagedPlayer) >= BridgeHealth.getMaxHealth(damagedPlayer)) {
                    // Might use the same FightData instance for GodMode.
                    if (damagedData.fastHealBuffer < 0) {
                        // Reduce negative buffer with each full health.
                        damagedData.fastHealBuffer /= 2;
                    }
                    // Set reference time.
                    damagedData.fastHealRefTime = System.currentTimeMillis();
                }
                // TEST: Check unused velocity for the damaged player. (Needs more efficient pre condition checks.)

            }
            if (damagedPData.isDebugActive(checkType)) {
                // Pass result to further checks for reference?
                UnusedVelocity.checkUnusedVelocity(damagedPlayer, CheckType.FIGHT, damagedPData);
            }
        }
        else damagedData = null;

        // Attacking entities.
        if (event instanceof EntityDamageByEntityEvent) {
            if (penaltyList == null) {
                penaltyList = new DefaultPenaltyList();
            }
            onEntityDamageByEntity(damaged, damagedPlayer, damagedIsDead, damagedIsFake,
                                   damagedData, (EntityDamageByEntityEvent) event, penaltyList);
        }

        if (penaltyList != null && !penaltyList.isEmpty()) {
            penaltyList.applyAllApplicablePenalties(event, true);
        }

    }

    /**
     * (Not an event listener method: call from EntityDamageEvent handler at
     * EventPriority.LOWEST.)
     * 
     * @param damagedPlayer
     * @param damagedIsDead
     * @param damagedData
     * @param event
     */
    private void onEntityDamageByEntity(final Entity damaged, final Player damagedPlayer,
                                        final boolean damagedIsDead, final boolean damagedIsFake,
                                        final FightData damagedData, final EntityDamageByEntityEvent event,
                                        final IPenaltyList penaltyList) {

        final Entity damager = event.getDamager();
        final int tick = TickTask.getTick();

        updateDamagedPlayerData(damagedPlayer, damagedIsDead, damagedData, damager, tick);

        final DamageCause damageCause = event.getCause();
        final Player attacker = resolveAttacker(damager);
        final IPlayerData attackerPData = attacker == null ? null : DataManager.getPlayerData(attacker);
        final FightData attackerData = attackerPData == null ? null : attackerPData.getGenericInstance(FightData.class);

        if (attacker != null) {
            handleAttackerPreChecks(attacker, attackerPData);
        }

        final boolean skip = attackerData != null && recordExplosionDamage(damaged, damageCause, attackerData, tick);

        if (!skip && attacker != null && damageCause == DamageCause.ENTITY_ATTACK) {
            processEntityAttack(event, (Player) damager, damaged, damagedIsFake, tick,
                               attackerData, attackerPData, penaltyList);
        }
    }

    private void updateDamagedPlayerData(final Player damagedPlayer, final boolean damagedIsDead,
                                         final FightData damagedData, final Entity damager,
                                         final int tick) {
        if (damagedPlayer == null || damagedIsDead) {
            return;
        }
        damagedData.damageTakenByEntityTick = tick;
        if (BridgeEnchant.hasThorns(damagedPlayer)) {
            damagedData.thornsId = damager.getEntityId();
        } else {
            damagedData.thornsId = Integer.MIN_VALUE;
        }
    }

    private Player resolveAttacker(final Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof TNTPrimed) {
            final Entity source = ((TNTPrimed) damager).getSource();
            if (source instanceof Player) {
                return (Player) source;
            }
        }
        return null;
    }

    private void handleAttackerPreChecks(final Player attacker, final IPlayerData pData) {
        if (pData != null && pData.isDebugActive(checkType)) {
            UnusedVelocity.checkUnusedVelocity(attacker, CheckType.FIGHT, pData);
        }
    }

    private boolean recordExplosionDamage(final Entity damaged, final DamageCause damageCause,
                                          final FightData attackerData, final int tick) {
        if (attackerData == null) {
            return false;
        }
        if (damageCause == DamageCause.BLOCK_EXPLOSION || damageCause == DamageCause.ENTITY_EXPLOSION) {
            attackerData.lastExplosionEntityId = damaged.getEntityId();
            attackerData.lastExplosionDamageTick = tick;
            return true;
        }
        return false;
    }

    private void processEntityAttack(final EntityDamageByEntityEvent event, final Player player,
                                     final Entity damaged, final boolean damagedIsFake, final int tick,
                                     final FightData attackerData, final IPlayerData attackerPData,
                                     final IPenaltyList penaltyList) {
        if (attackerData == null || attackerPData == null) {
            return;
        }
        if (damaged.getEntityId() == attackerData.lastExplosionEntityId
                && tick == attackerData.lastExplosionDamageTick) {
            attackerData.lastExplosionDamageTick = -1;
            attackerData.lastExplosionEntityId = Integer.MAX_VALUE;
            return;
        }
        if (MovingUtil.hasScheduledPlayerSetBack(player)) {
            if (attackerPData.isDebugActive(checkType)) {
                debug(player, "Prevent melee attack, due to a scheduled set back.");
            }
            event.setCancelled(true);
            return;
        }

        if (handleNormalDamage(player, !crossPlugin.getHandle().isNativePlayer(player),
                               damaged, damagedIsFake, BridgeHealth.getOriginalDamage(event),
                               BridgeHealth.getFinalDamage(event), tick, attackerData,
                               attackerPData, penaltyList)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDamageMonitor(final EntityDamageEvent event) {

        final Entity damaged = event.getEntity();
        if (damaged instanceof Player) {
            final Player damagedPlayer = (Player) damaged;
            final IPlayerData damagedPData = DataManager.getPlayerData(damagedPlayer);
            final FightData damagedData = damagedPData.getGenericInstance(FightData.class);
            final int ndt = damagedPlayer.getNoDamageTicks();

            if (damagedData.lastDamageTick == TickTask.getTick() && damagedData.lastNoDamageTicks != ndt) {
                // Plugin compatibility thing.
                damagedData.lastNoDamageTicks = ndt;
            }
            // Knock-back calculation (1.8: events only fire if they would count by ndt).
            switch (event.getCause()) {
                case ENTITY_ATTACK:
                    if (event instanceof EntityDamageByEntityEvent) {
                        final Entity entity = ((EntityDamageByEntityEvent) event).getDamager();
                        if ((entity instanceof Player) && !damagedPlayer.isInsideVehicle() 
                             && damagedPData.getGenericInstance(FightConfig.class).knockBackVelocityPvP) {
                            // Use the velocity event that is sent anyway and replace x/z if 0 (queue max. values).
                            applyKnockBack((Player) entity, damagedPlayer, damagedData, damagedPData);
                        }
                    }
                default:
                    break;
            }
        }
    }

    /**
     * Knock-back accounting: Add velocity.
     * @param attacker
     * @param damagedPlayer
     * @param damagedData
     */
    private void applyKnockBack(final Player attacker, final Player damagedPlayer, 
                                final FightData damagedData, final IPlayerData pData) {

        final double level = getKnockBackLevel(attacker);
        final MovingData mdata = pData.getGenericInstance(MovingData.class);
        final MovingConfig mcc = pData.getGenericInstance(MovingConfig.class);
        // How is the direction really calculated?
        // Aim at sqrt(vx * vx + vz * vz, 2), not the exact direction.
        final double[] vel2Dvec = calculateVelocity(attacker, damagedPlayer);
        final double vx = vel2Dvec[0];
        final double vz = vel2Dvec[2];
        final double vy = vel2Dvec[1];
        useLoc1.setWorld(null); // Cleanup.
        if (pData.isDebugActive(checkType) || pData.isDebugActive(CheckType.MOVING)) {
            debug(damagedPlayer, "Received knockback level: " + level);
        }
        mdata.addVelocity(damagedPlayer, mcc,  vx, vy, vz, VelocityFlags.ORIGIN_PVP);
    }

    /**
     * Get the knock-back "level", a player can deal based on sprinting +
     * item(s) in hand. The minimum knock-back level is 1.0 (1 + 1 for sprinting
     * + knock-back level), currently capped at 20. Since detecting relevance of
     * items in main vs. off hand, we use the maximum of both, for now.
     * 
     * @param player
     * @return
     */
    private double getKnockBackLevel(final Player player) {

        double level = 1.0; // 1.0 is the minimum knock-back value.
        // Get the RELEVANT item (...).
        final ItemStack stack = Bridge1_9.getItemInMainHand(player);
        if (!BlockProperties.isAir(stack)) {
            level = (double) stack.getEnchantmentLevel(Enchantment.KNOCKBACK);
        }
        if (player.isSprinting()) {
            // Lost sprint?
            level += 1.0;
        }
        // Cap the level to something reasonable. Config / cap the velocity anyway.
        return Math.min(20.0, level);
    }

    /**
     * Better method to calculate velocity including direction!
     * 
     * @param attacker
     * @param damagedPlayer
     * @return velocityX, velocityY, velocityZ
     */
    private double[] calculateVelocity(final Player attacker, final Player damagedPlayer) {

        final Location aloc = attacker.getLocation();
        final Location dloc = damagedPlayer.getLocation();
        final double Xdiff = dloc.getX() - aloc.getX();
        final double Zdiff = dloc.getZ() - aloc.getZ();
        final double diffdist = Math.sqrt(Xdiff * Xdiff + Zdiff * Zdiff);
        double vx = 0.0;
        double vz = 0.0;
        int incknockbacklevel = 0;
        // Get the RELEVANT item (...).
        final ItemStack stack = Bridge1_9.getItemInMainHand(attacker);
        if (!BlockProperties.isAir(stack)) {
            incknockbacklevel = stack.getEnchantmentLevel(Enchantment.KNOCKBACK);
        }
        if (attacker.isSprinting()) {
            // Lost sprint?
            incknockbacklevel++;
        }
        // Cap the level to something reasonable. Config / cap the velocity anyway. 
        incknockbacklevel = Math.min(20, incknockbacklevel);

        if (Math.sqrt(Xdiff * Xdiff + Zdiff * Zdiff) < 1.0E-4D) {
            if (incknockbacklevel <= 0) incknockbacklevel = -~0;
            vx = vz = incknockbacklevel / Math.sqrt(8.0);
            final double vy = incknockbacklevel > 0 ? 0.465 : 0.365;
            return new double[] {vx, vy, vz};
        } else {
            vx = Xdiff / diffdist * 0.4;
            vz = Zdiff / diffdist * 0.4;
        }

        if (incknockbacklevel > 0) {
            vx *= 1.0 + 1.25 * incknockbacklevel;
            vz *= 1.0 + 1.25 * incknockbacklevel;
            // Still not exact direction since yaw difference between packet and Location#getYaw();
            // with incknockbacklevel = 0, it still the precise direction
            //vx -= Math.sin(aloc.getYaw() * Math.PI / 180.0F) * incknockbacklevel * 0.5F;
            //vz += Math.cos(aloc.getYaw() * Math.PI / 180.0F) * incknockbacklevel * 0.5F;
        }
        final double vy = incknockbacklevel > 0 ? 0.465 : 0.365;
        return new double[] {vx, vy, vz};
    }

    /**
     * We listen to death events to prevent a very specific method of doing godmode.
     * 
     * @param event
     *            the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeathEvent(final EntityDeathEvent event) {
        // Only interested in dying players.
        final Entity entity = event.getEntity();
        if (entity instanceof Player) {
            final Player player = (Player) entity;
            if (godMode.isEnabled(player)) {
                godMode.death(player);
            }
        }
    }

    /**
     * We listen to PlayerAnimation events because it is used for arm swinging.
     * 
     * @param event
     *            the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAnimation(final PlayerAnimationEvent event) {
        // Set a flag telling us that the arm has been swung.
        final FightData data = DataManager.getGenericInstance(event.getPlayer(), FightData.class);
        data.noSwingCount = Math.max(data.noSwingCount - 1, 0);
        
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityRegainHealthLow(final EntityRegainHealthEvent event) {

        final Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        final Player player = (Player) entity;
        if (player.isDead() && BridgeHealth.getHealth(player) <= 0.0) {
            // Heal after death.
            // Problematic. At least skip CUSTOM.
            event.setCancelled(true);
            counters.addPrimaryThread(idCancelDead, 1);
            return;
        }
        if (event.getRegainReason() != RegainReason.SATIATED) {
            return;
        }
        // EATING reason / peaceful difficulty / regen potion - byCaptain SpigotMC
        final IPlayerData pData = DataManager.getPlayerData(player);
        if (pData.isCheckActive(CheckType.FIGHT_FASTHEAL, player) 
                && fastHeal.check(player, pData)) {
            // Can clients force events with 0-re-gain ?
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRegainHealth(final EntityRegainHealthEvent event) {

        final Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        final Player player = (Player) entity;
        final FightData data = DataManager.getGenericInstance(player, FightData.class);
        // Adjust god mode data:
        // Remember the time.
        data.regainHealthTime = System.currentTimeMillis();
        // Set god-mode health to maximum.
        // Mind that health regain might half the ndt.
        final double health = Math.min(BridgeHealth.getHealth(player) + BridgeHealth.getAmount(event), BridgeHealth.getMaxHealth(player));
        data.godModeHealth = Math.max(data.godModeHealth, health);
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void entityInteract(PlayerInteractEntityEvent e) {
    	Entity entity = e.getRightClicked();
    	final Player player = e.getPlayer();
    	final FightData data = DataManager.getGenericInstance(player, FightData.class);
        data.exemptArmSwing = entity != null && entity.getType().name().equals("PARROT");
    }

    @Override
    public void playerJoins(final Player player) {
    }

    @Override
    public void playerLeaves(final Player player) {
        final FightData data = DataManager.getGenericInstance(player, FightData.class);
        data.angleHits.clear();
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.MONITOR)
    public void onItemHeld(final PlayerItemHeldEvent event) {
        
        final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getPlayerData(player);
        final long penalty = pData.getGenericInstance(FightConfig.class).toolChangeAttackPenalty;
        if (penalty > 0 ) {
            pData.getGenericInstance(FightData.class).attackPenalty.applyPenalty(penalty);
        }
    }

}
