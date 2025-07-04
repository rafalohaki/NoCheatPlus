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
package fr.neatmonster.nocheatplus.checks.moving;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;
import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.CheckListener;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.combined.Combined;
import fr.neatmonster.nocheatplus.checks.combined.CombinedConfig;
import fr.neatmonster.nocheatplus.checks.combined.CombinedData;
import fr.neatmonster.nocheatplus.checks.moving.helper.ElytraBoostHandler;
import fr.neatmonster.nocheatplus.checks.moving.helper.ExtremeMoveHandler;
import fr.neatmonster.nocheatplus.checks.moving.helper.MoveCheckContext;
import fr.neatmonster.nocheatplus.checks.moving.helper.MovePreChecks;
import fr.neatmonster.nocheatplus.checks.moving.helper.VelocityAdjustment;
import fr.neatmonster.nocheatplus.checks.moving.helper.VelocityProcessor;
import fr.neatmonster.nocheatplus.checks.moving.magic.Magic;
import fr.neatmonster.nocheatplus.checks.moving.model.ModelFlying;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveInfo;
import fr.neatmonster.nocheatplus.checks.moving.player.CreativeFly;
import fr.neatmonster.nocheatplus.checks.moving.player.MorePackets;
import fr.neatmonster.nocheatplus.checks.moving.player.NoFall;
import fr.neatmonster.nocheatplus.checks.moving.player.Passable;
import fr.neatmonster.nocheatplus.checks.moving.player.PlayerSetBackMethod;
import fr.neatmonster.nocheatplus.checks.moving.player.SurvivalFly;
import fr.neatmonster.nocheatplus.checks.moving.player.SurvivalFlyCheckContext;
import fr.neatmonster.nocheatplus.checks.moving.util.AuxMoving;
import fr.neatmonster.nocheatplus.checks.moving.util.MovingUtil;
import fr.neatmonster.nocheatplus.checks.moving.util.bounce.BounceType;
import fr.neatmonster.nocheatplus.checks.moving.util.bounce.BounceUtil;
import fr.neatmonster.nocheatplus.checks.moving.vehicle.VehicleChecks;
import fr.neatmonster.nocheatplus.checks.moving.velocity.AccountEntry;
import fr.neatmonster.nocheatplus.checks.moving.velocity.SimpleEntry;
import fr.neatmonster.nocheatplus.checks.moving.velocity.VelocityFlags;
import fr.neatmonster.nocheatplus.checks.net.NetData;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.Bridge1_17;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.BridgeEnchant;
import fr.neatmonster.nocheatplus.compat.BridgeEntityType;
import fr.neatmonster.nocheatplus.compat.BridgeHealth;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.compat.BridgePotionEffect;
import fr.neatmonster.nocheatplus.compat.Folia;
import fr.neatmonster.nocheatplus.compat.MCAccess;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker.BlockChangeEntry;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker.Direction;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.IBlockChangeTracker;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.components.data.ICheckData;
import fr.neatmonster.nocheatplus.components.data.IData;
import fr.neatmonster.nocheatplus.components.location.SimplePositionWithLook;
import fr.neatmonster.nocheatplus.components.modifier.IAttributeAccess;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.components.registry.feature.IHaveCheckType;
import fr.neatmonster.nocheatplus.components.registry.feature.INeedConfig;
import fr.neatmonster.nocheatplus.components.registry.feature.IRemoveData;
import fr.neatmonster.nocheatplus.components.registry.feature.JoinLeaveListener;
import fr.neatmonster.nocheatplus.components.registry.feature.TickListener;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.ConfigManager;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.logging.debug.DebugUtil;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.stats.Counters;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;
import fr.neatmonster.nocheatplus.utilities.PotionUtil;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.utilities.build.BuildParameters;
import fr.neatmonster.nocheatplus.utilities.location.LocUtil;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.location.TrigUtil;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.MapUtil;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;

/**
 * Central location to listen to events that are relevant for the moving checks.
 * 
 * @see MovingEvent
 */
public class MovingListener extends CheckListener implements TickListener, IRemoveData, IHaveCheckType, INeedConfig, JoinLeaveListener {

    /** Tolerance for floating point comparisons. */
    private static final double EPSILON = 1.0E-6;

    /** The no fall check. **/
    public final NoFall noFall = addCheck(new NoFall());

    /** The creative fly check. */
    private final CreativeFly creativeFly = addCheck(new CreativeFly());

    /** The more packets check. */
    private final MorePackets morePackets = addCheck(new MorePackets());
    
    /** Vehicle checks */
    private final VehicleChecks vehicleChecks = new VehicleChecks();

    /** The survival fly check. */
    private final SurvivalFly survivalFly = addCheck(new SurvivalFly());

    /** The Passable check.*/
    private final Passable passable = addCheck(new Passable());
    
    /** Store events by player name, in order to invalidate moving processing on higher priority level in case of teleports. */
    private final Map<String, PlayerMoveEvent> processingEvents = new ConcurrentHashMap<String, PlayerMoveEvent>();

    /** Player names to check hover for, case insensitive. */
    private final Set<String> hoverTicks = ConcurrentHashMap.newKeySet(30); // Rename

    /** Player names to check enforcing the location for in onTick, case insensitive. */
    private final Set<String> playersEnforce = ConcurrentHashMap.newKeySet(30);

    private int hoverTicksStep = 5;

    /** Location for temporary use with getLocation(useLoc). Always call setWorld(null) after use. Use LocUtil.clone before passing to other API. */
    final Location useLoc = new Location(null, 0, 0, 0); // Put to use...
    final Location useBedLeaveLoc = new Location(null, 0, 0, 0);
    final Location useChangeWorldLoc = new Location(null, 0, 0, 0);
    final Location useDeathLoc = new Location(null, 0, 0, 0);
    final Location useFallLoc = new Location(null, 0, 0, 0);
    final Location useJoinLoc = new Location(null, 0, 0, 0);
    final Location useLeaveLoc = new Location(null, 0, 0, 0);
    final Location useToggleFlightLoc = new Location(null, 0, 0, 0);
    final Location useTickLoc = new Location(null, 0, 0, 0);
    /** Location reused in {@link #standsOnEntity(Entity, double)}. */
    final Location useEntityCheckLoc = new Location(null, 0, 0, 0);

    /** Auxiliary functionality. */
    private final AuxMoving aux = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstance(AuxMoving.class);

    private IGenericInstanceHandle<IAttributeAccess> attributeAccess = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstanceHandle(IAttributeAccess.class);

    private final IBlockChangeTracker blockChangeTracker;

    /** Statistics / debugging counters. */
    private final Counters counters = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstance(Counters.class);

    private final int idMoveEvent = counters.registerKey("event.player.move");

    /**
     * From Minecraft 1.19.4 on, the server stops firing PlayerMoveEvents while
     * riding a minecart (see issue #290). Upon leaving the cart a single move
     * event covering the whole travelled distance triggers. This flag lets NCP
     * apply extra checks for minecart exits and disables an old boat fix. It
     * prevents huge setbacks when dismounting.  
     * https://github.com/Updated-NoCheatPlus/NoCheatPlus/issues/290
     */
    private final boolean specialMinecart = ServerVersion.compareMinecraftVersion("1.19.4") >= 0;

    private final DataManager dataManager;

    @SuppressWarnings("unchecked")
    public MovingListener(final DataManager dataManager) {
        super(CheckType.MOVING);
        this.dataManager = dataManager;
        // Register vehicleChecks.
        final NoCheatPlusAPI api = NCPAPIProvider.getNoCheatPlusAPI();
        api.addComponent(vehicleChecks);
        blockChangeTracker = NCPAPIProvider.getNoCheatPlusAPI().getBlockChangeTracker();
        if (Bridge1_9.hasEntityToggleGlideEvent()) {
            queuedComponents.add(new Listener() {
                @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
                public void onEntityToggleGlide(final EntityToggleGlideEvent event) {
                    if (handleEntityToggleGlideEvent(event.getEntity(), event.isGliding())) {
                        event.setCancelled(true);
                    }
                }
            });
        }

        // Register config and data.
        // Should register before creating Check instances ?
        api.register(api.newRegistrationContext()
                // MovingConfig
                .registerConfigWorld(MovingConfig.class)
                .factory(arg -> new MovingConfig(arg.worldData))
                .registerConfigTypesPlayer(CheckType.MOVING, true)
                .context() //
                // MovingData
                .registerDataPlayer(MovingData.class)
                .factory(arg -> new MovingData(arg.worldData.getGenericInstance(
                        MovingConfig.class), arg.playerData))
                .addToGroups(CheckType.MOVING, false, List.of(IData.class, ICheckData.class))
                .removeSubCheckData(CheckType.MOVING, true)
                .context() //
                );
    }

    /**
     * 
     * @param entity
     * @param isGliding
     * @return True, if the event is to be cancelled.
     */
    private boolean handleEntityToggleGlideEvent(final Entity entity, final boolean isGliding) {

        // Ignore non players.
        if (!(entity instanceof Player)) {
            return false;
        }
        final Player player = (Player) entity;
        if (isGliding && !Bridge1_9.isGlidingWithElytra(player)) { // Includes check for elytra item.
            final PlayerMoveInfo info = aux.usePlayerMoveInfo();
            info.set(player, player.getLocation(info.useLoc), null, 0.001); // Only restrict very near ground.
            final IPlayerData pData = fetchPlayerData(player, "toggle glide");
            if (pData == null) {
                aux.returnPlayerMoveInfo(info);
                return false;
            }
            final MovingData data = pData.getGenericInstance(MovingData.class);
            final boolean res = !MovingUtil.canLiftOffWithElytra(player, info.from, data);
            info.cleanup();
            aux.returnPlayerMoveInfo(info);
            if (res && pData.isDebugActive(checkType)) {
                debug(player, "Prevent toggle glide on.");
            }
            return res;
        }
        return false;
    }


    /**
     * We listen to this event to prevent player from flying by sending bed leaving packets.
     * 
     * @param event
     *            the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBedEnter(final PlayerBedEnterEvent event) {
        final Player player = event.getPlayer();
        final IPlayerData pData = fetchPlayerData(player, "bed enter");
        if (pData == null) {
            return;
        }
        pData.getGenericInstance(MovingData.class).wasInBed = true;
    }


    /**
     * We listen to this event to prevent player from flying by sending bed leaving packets.
     * 
     * @param event
     *            the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBedLeave(final PlayerBedLeaveEvent event) {

        final Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        handleBedLeave(player);
    }

    private void handleBedLeave(final Player player) {
        final IPlayerData pData = fetchPlayerData(player, "bed leave");
        if (!shouldProcessBedLeave(player, pData)) {
            return;
        }

        final MovingData data = pData.getGenericInstance(MovingData.class);
        final MovingConfig cc = pData.getGenericInstance(MovingConfig.class);
        if (!shouldResetAfterBedLeave(player, pData, cc, data)) {
            data.wasInBed = false;
            return;
        }

        final Location loc = getCurrentBedLeaveLocation(player);
        if (loc == null) {
            data.wasInBed = false;
            return;
        }

        final PlayerMoveInfo moveInfo = aux.usePlayerMoveInfo();
        final boolean sfCheck = shouldCheckSurvivalFly(player, pData, cc, data, loc, moveInfo);
        final Location newTo = determineBedLeaveLocation(player, pData, cc, data, moveInfo.from, loc, sfCheck);
        aux.returnPlayerMoveInfo(moveInfo);
        applyBedLeaveDamage(player, pData, cc, data, loc, sfCheck);
        finalizeBedLeave(player, pData, data, newTo);
    }

    private boolean shouldProcessBedLeave(final Player player, final IPlayerData pData) {
        return player != null && pData != null
                && pData.isCheckActive(CheckType.MOVING, player)
                && !pData.hasBypass(CheckType.MOVING_SURVIVALFLY, player)
                && !pData.isExempted(CheckType.MOVING_SURVIVALFLY);
    }

    private Location getCurrentBedLeaveLocation(final Player player) {
        return player != null ? player.getLocation(useBedLeaveLoc) : null;
    }

    private boolean shouldResetAfterBedLeave(final Player player, final IPlayerData pData,
                                             final MovingConfig cc, final MovingData data) {
        return pData.isCheckActive(CheckType.MOVING_SURVIVALFLY, player)
                && survivalFly.checkBed(player, pData, cc, data);
    }

    private boolean shouldCheckSurvivalFly(final Player player, final IPlayerData pData,
                                           final MovingConfig cc, final MovingData data,
                                           final Location loc, final PlayerMoveInfo moveInfo) {
        moveInfo.set(player, loc, null, cc.yOnGround);
        return MovingUtil.shouldCheckSurvivalFly(player, moveInfo.from, moveInfo.to, data, cc, pData);
    }

    private Location determineBedLeaveLocation(final Player player, final IPlayerData pData,
                                               final MovingConfig cc, final MovingData data,
                                               final PlayerLocation from, final Location loc,
                                               final boolean sfCheck) {
        if (player == null || pData == null
                || pData.hasBypass(CheckType.MOVING_SURVIVALFLY, player)
                || pData.isExempted(CheckType.MOVING_SURVIVALFLY)) {
            return null;
        }

        Location newTo = null;
        if (sfCheck) {
            newTo = MovingUtil.getApplicableSetBackLocation(player, loc.getYaw(), loc.getPitch(), from, data, cc);
        }
        if (newTo == null) {
            newTo = LocUtil.clone(loc);
        }
        return newTo;
    }

    private void applyBedLeaveDamage(final Player player, final IPlayerData pData,
                                     final MovingConfig cc, final MovingData data,
                                     final Location loc, final boolean sfCheck) {
        if (player == null || pData == null
                || pData.hasBypass(CheckType.MOVING_SURVIVALFLY, player)
                || pData.isExempted(CheckType.MOVING_SURVIVALFLY)) {
            return;
        }
        if (sfCheck && cc.sfSetBackPolicyApplyFallDamage && noFall.isEnabled(player, pData)) {
            double y = loc.getY();
            if (data.hasSetBack()) {
                y = Math.min(y, data.getSetBackY());
            }
            noFall.checkDamage(player, y, data, pData);
        }
    }

    private void finalizeBedLeave(final Player player, final IPlayerData pData,
                                  final MovingData data, final Location newTo) {
        if (player == null || pData == null
                || pData.hasBypass(CheckType.MOVING_SURVIVALFLY, player)
                || pData.isExempted(CheckType.MOVING_SURVIVALFLY) || newTo == null) {
            return;
        }
        useBedLeaveLoc.setWorld(null);
        data.prepareSetBack(newTo);
        player.teleport(newTo, BridgeMisc.TELEPORT_CAUSE_CORRECTION_OF_POSITION);
    }


    // Temporary fix "stuck" on boat for 1.14-1.19
    @EventHandler(priority = EventPriority.LOWEST)
    public void onUnknowBoatTeleport(final PlayerTeleportEvent event) {
        if (!Bridge1_13.hasIsSwimming() || specialMinecart) return;
        if (event.getCause() == TeleportCause.UNKNOWN) {
            final Player player = event.getPlayer();
            final IPlayerData pData = fetchPlayerData(player, "unknown boat teleport");
            if (pData == null) {
                return;
            }
            final MovingData data = pData.getGenericInstance(MovingData.class);
            if (data.lastVehicleType != null && standsOnEntity(player, player.getLocation().getY())) {
                event.setCancelled(true);
                player.setSwimming(false);
            }
        }
    }


    /**
     * Determine if the entity is effectively standing on a boat located on a
     * liquid block. Assumes only standard boat types recognised by
     * {@link MaterialUtil#isBoat(EntityType)} and that the boat block checks use
     * {@link BlockProperties#isLiquid(Material)}.
     */
    private boolean standsOnEntity(final Entity entity, final double minY) {
        if (entity == null) {
            return false;
        }
        boolean onBoat = false;
        for (final Entity other : entity.getNearbyEntities(1.5, 1.5, 1.5)) {
            if (other == null) {
                continue;
            }
            final EntityType type = other.getType();
            if (!MaterialUtil.isBoat(type)) {
                continue;
            }
            final Location otherLoc = other.getLocation(useEntityCheckLoc);
            if (otherLoc != null) {
                final Material mat = otherLoc.getBlock().getType();
                if (Math.abs(otherLoc.getY() - minY) < 0.7 && BlockProperties.isLiquid(mat)) {
                    onBoat = true;
                }
                useEntityCheckLoc.setWorld(null);
            }
        }
        return onBoat;
    }

    /**
     * Just for security, if a player switches between worlds, reset the fly and more packets checks data, because it is
     * definitely invalid now.
     * 
     * @param event
     *            the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(final PlayerChangedWorldEvent event) {

        // Maybe this helps with people teleporting through Multiverse portals having problems?
        final Player player = event.getPlayer();
        final IPlayerData pData = fetchPlayerData(player, "world change");
        if (pData == null || !pData.isCheckActive(CheckType.MOVING, player)) {
            return;
        }
        final MovingData data = pData.getGenericInstance(MovingData.class);
        final MovingConfig cc = pData.getGenericInstance(MovingConfig.class);
        data.clearMostMovingCheckData();
        final Location loc = player.getLocation(useChangeWorldLoc);
        data.setSetBack(loc);
        if (cc.loadChunksOnWorldChange) MovingUtil.ensureChunksLoaded(player, loc, "world change", data, cc, pData);
        aux.resetPositionsAndMediumProperties(player, loc, data, cc);
        data.resetTrace(player, loc, TickTask.getTick(), mcAccess.getHandle(), cc);
        // Just in case.
        if (cc.enforceLocation) playersEnforce.add(player.getName());
        useChangeWorldLoc.setWorld(null);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWindChargeExplode(EntityExplodeEvent e) {
        if (e.getEntity().getType() == BridgeEntityType.WIND_CHARGE) {
            Location loc = e.getLocation();
            for (Entity affectedPlayer : loc.getWorld().getNearbyEntities(loc, 1.2, 1.2, 1.2, (entity) -> entity.getType() == EntityType.PLAYER)) {
                final Player player = (Player) affectedPlayer;
                final IPlayerData pData = fetchPlayerData(player, "wind charge explode");
                if (pData == null || !pData.isCheckActive(CheckType.MOVING, player)) {
                    continue;
                }
                final MovingData data = pData.getGenericInstance(MovingData.class);
                data.noFallCurrentLocOnWindChargeHit = player.getLocation().clone();
            }
        }
    }


    /**
     * When a player changes their gamemode, all information related to the moving checks becomes invalid.
     * 
     * @param event
     *            the event
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerGameModeChange(final PlayerGameModeChangeEvent event) {

        final Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || event.getNewGameMode() == GameMode.CREATIVE) {
            final IPlayerData pData = fetchPlayerData(player, "gamemode change");
            if (pData == null) {
                return;
            }
            final MovingData data = pData.getGenericInstance(MovingData.class);
            data.clearWindChargeImpulse();
            data.clearFlyData();
            data.clearPlayerMorePacketsData();
            // Set new set back if any fly check is activated.
            // (Keep vehicle data as is.)
        }
    }


    /**
     * When a player moves, they will be checked for various suspicious behaviors.<br>
     * (lowest priority)
     * 
     * @param event
     *            the event
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerMove(final PlayerMoveEvent event) {

        counters.add(idMoveEvent, 1);
        final Player player = event.getPlayer();
        // Store the event for monitor level checks.
        processingEvents.put(player.getName(), event);
        final IPlayerData pData = fetchPlayerData(player, "move");
        if (pData == null || !pData.isCheckActive(CheckType.MOVING, player)) {
            return;
        }
        final MovingConfig cc = pData.getGenericInstance(MovingConfig.class);
        final MovingData data = pData.getGenericInstance(MovingData.class);
        final boolean debug = pData.isDebugActive(checkType);
        data.increasePlayerMoveCount();
        final Location from = event.getFrom().clone();
        final Location to = event.getTo().clone();
        Location newTo = null;


        //////////////////////////////////////////  
        // Check problematic yaw/pitch values.  //
        //////////////////////////////////////////
        if (LocUtil.needsDirectionCorrection2(from.getYaw(), from.getPitch())) {
            from.setYaw(LocUtil.correctYaw2(from.getYaw()));
            from.setPitch(LocUtil.correctPitch(from.getPitch()));
        }

        if (LocUtil.needsDirectionCorrection2(to.getYaw(), to.getPitch())) {
            to.setYaw(LocUtil.correctYaw2(to.getYaw()));
            to.setPitch(LocUtil.correctPitch(to.getPitch()));
        }
        

        ////////////////////////////////////////////////////
        // Early return checks (no full processing).      //
        ////////////////////////////////////////////////////
        EarlyReturnDecision er = determineEarlyReturn(player, from, to, event, data, cc, pData);
        final boolean earlyReturn = er.earlyReturn;
        final String token = er.token;
        newTo = er.newTo;

        // Reset duplicate move flag.
        if (!TrigUtil.isSamePos(from, to)) {
            data.lastMoveNoMove = false;
        }

        if (earlyReturn) {
            if (debug) {
                debug(player, "Early return" + (token == null ? "" : (" (" + token + ")")) +  " on PlayerMoveEvent: from: " + from + " , to: " + to);
            }
            if (newTo != null) {
                // Illegal Yaw/Pitch.
                if (LocUtil.needsYawCorrection(newTo.getYaw())) {
                    newTo.setYaw(LocUtil.correctYaw(newTo.getYaw()));
                }

                if (LocUtil.needsPitchCorrection(newTo.getPitch())) {
                    newTo.setPitch(LocUtil.correctPitch(newTo.getPitch()));
                }
                // Set.
                prepareSetBack(player, event, newTo, data, cc, pData); // Logs set back details.
            }
            data.joinOrRespawn = false;
            return;
        }

        // Change world miss. Not efficient, require first move event fire to know 
        if (Folia.isFoliaServer()) {
            if (data.currentWorldToChange != null && !data.currentWorldToChange.equals(from.getWorld())) {
                final PlayerChangedWorldEvent e = new PlayerChangedWorldEvent(player, data.currentWorldToChange);
                Bukkit.getPluginManager().callEvent(e);
            }
            data.currentWorldToChange = from.getWorld();
        }



        handleSplitMoves(player, from, to, event, debug, data, cc, pData);
    }


    /**
     * During early player move handling: data.hasTeleported() returned true.
     * 
     * @param player
     * @param event
     * @param data
     * @param cc 
     * 
     * @return
     */
    private boolean handleTeleportedOnMove(final Player player, final PlayerMoveEvent event, final MovingData data,
                                           final MovingConfig cc, final IPlayerData pData) {

        // This could also happen with a packet based set back such as with cancelling move events.
        final boolean debug = pData.isDebugActive(checkType);
        if (data.isTeleportedPosition(event.getFrom())) {
            // Treat as ACK (!).
            // Adjust.
            confirmSetBack(player, false, data, cc, pData, event.getFrom());
            // Log.
            if (debug) debug(player, "Implicitly confirm set back with the start point of a move.");
            return false;
        }
        else if (dataManager.getPlayerData(player).isPlayerSetBackScheduled()) {
            // A set back has been scheduled, but the player is moving randomly.
            // Instead alter the move from location and let it get through? +- when
            event.setCancelled(true);
            if (debug) debug(player, "Cancel move, due to a scheduled teleport (set back).");
            return true;
        }
        else {
            // Left-over (Demand: schedule or teleport before moving events arrive).
            if (debug) debug(player, "Invalidate left-over teleported (set back) location: " + data.getTeleported());
            data.resetTeleported();
            // More to do?
            return false;
        }
    }

    private static record EarlyReturnDecision(boolean earlyReturn, Location newTo, String token) {}

    private EarlyReturnDecision determineEarlyReturn(final Player player, final Location from, final Location to,
                                                     final PlayerMoveEvent event, final MovingData data,
                                                     final MovingConfig cc, final IPlayerData pData) {

        EarlyReturnDecision res;

        // Vehicle related handling first to prevent conflicting physics.
        res = checkVehicleConditions(player, from, to, data, pData);
        if (res != null) return res;

        // Ignore moves while the player is dead or sleeping.
        res = checkPlayerState(player, data);
        if (res != null) return res;

        // Handle world changes and scheduled teleports.
        res = checkWorldAndTeleport(player, from, to, event, data, cc, pData);
        if (res != null) return res;

        // Skip duplicate move events created by the server.
        res = checkDuplicateMove(from, to, data);
        if (res != null) return res;

        return new EarlyReturnDecision(false, null, null);
    }

    /**
     * Check for various vehicle related conditions that require an early return.
     *
     * @return A decision if an early return is required or {@code null}.
     */
    private EarlyReturnDecision checkVehicleConditions(final Player player, final Location from,
                                                       final Location to, final MovingData data,
                                                       final IPlayerData pData) {
        if (player.isInsideVehicle()) {
            // Movement is handled by VehicleChecks to avoid conflicts with vehicle physics.
            Location newTo = vehicleChecks.onPlayerMoveVehicle(player, from, to, data, pData);
            return new EarlyReturnDecision(true, newTo, "vehicle");
        }
        if (data.lastVehicleType == EntityType.MINECART && specialMinecart
                && to.distance(data.getSetBack(from)) < 3) {
            // Skip to wait for a full minecart transition after world changes.
            data.lastVehicleType = null;
            return new EarlyReturnDecision(true, null, "minecart-total");
        }
        if (data.vehicleLeave && to.distance(from) > 3) {
            // Player left a vehicle but moved away before normal handling.
            Location newTo = data.getSetBack(from);
            data.vehicleLeave = false;
            return new EarlyReturnDecision(true, newTo, "vehicle-leave-sb");
        }
        return null;
    }

    /**
     * Detect if the player state prevents processing the move.
     */
    private EarlyReturnDecision checkPlayerState(final Player player, final MovingData data) {
        if (player.isDead()) {
            // Dead players shouldn't be processed by movement checks.
            data.sfHoverTicks = -1;
            return new EarlyReturnDecision(true, null, "dead");
        }
        if (player.isSleeping()) {
            // Sleeping players can't move legitimately.
            data.sfHoverTicks = -1;
            return new EarlyReturnDecision(true, null, "sleeping");
        }
        return null;
    }

    /**
     * Handle world transitions and pending teleports.
     */
    private EarlyReturnDecision checkWorldAndTeleport(final Player player, final Location from, final Location to,
                                                      final PlayerMoveEvent event, final MovingData data,
                                                      final MovingConfig cc, final IPlayerData pData) {
        if (!from.getWorld().equals(to.getWorld())) {
            // Changing world: wait for the proper teleport event first.
            return new EarlyReturnDecision(true, null, "worldchange");
        }
        if (data.hasTeleported()) {
            // A teleport is pending and must be resolved before processing moves.
            boolean early = handleTeleportedOnMove(player, event, data, cc, pData);
            return new EarlyReturnDecision(early, null, "awaitsetback");
        }
        return null;
    }

    /**
     * Ignore duplicate move events with identical positions.
     */
    private EarlyReturnDecision checkDuplicateMove(final Location from, final Location to, final MovingData data) {
        if (TrigUtil.isSamePos(from, to) && !data.lastMoveNoMove) {
            data.lastMoveNoMove = true;
            return new EarlyReturnDecision(true, null, "duplicate");
        }
        return null;
    }

    private void handleSplitMoves(final Player player, final Location from, final Location to,
                                  final PlayerMoveEvent event, final boolean debug,
                                  final MovingData data, final MovingConfig cc, final IPlayerData pData) {

        final PlayerMoveInfo moveInfo = aux.usePlayerMoveInfo();
        final Location loc = player.getLocation(moveInfo.useLoc);
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        if (cc.loadChunksOnMove) {
            MovingUtil.ensureChunksLoaded(player, from, to, lastMove, "move", cc, pData);
        }

        if (!cc.splitMoves || TrigUtil.isSamePos(from, loc)
                || lastMove.valid && TrigUtil.isSamePos(loc, lastMove.from.getX(), lastMove.from.getY(), lastMove.from.getZ())) {
            moveInfo.set(player, from, to, cc.yOnGround);
            checkPlayerMove(player, from, to, 0, moveInfo, debug, data, cc, pData, event);
        }
        else {
            if (debug) debug(player, "Split move 1 (from -> loc):");
            moveInfo.set(player, from, loc, cc.yOnGround);
            if (!checkPlayerMove(player, from, loc, 1, moveInfo, debug, data, cc, pData, event)
                    && processingEvents.containsKey(player.getName())) {
                onMoveMonitorNotCancelled(player, from, loc, System.currentTimeMillis(), TickTask.getTick(),
                        pData.getGenericInstance(CombinedData.class), data, cc, pData);
                data.joinOrRespawn = false;
                if (debug) debug(player, "Split move 2 (loc -> to):");
                moveInfo.set(player, loc, to, cc.yOnGround);
                checkPlayerMove(player, loc, to, 2, moveInfo, debug, data, cc, pData, event);
            }
        }
        data.joinOrRespawn = false;
        aux.returnPlayerMoveInfo(moveInfo);
    }


    /**
     * Core move checks.
     * @param player
     * @param from
     * @param to
     * @param multiMoveCount
     *            0: An ordinary move, not split. 1/2: first/second of a split
     *            move.
     * @param moveInfo
     * @param data
     * @param cc
     * @param event
     * @return If cancelled/done, i.e. not to process further split moves.
     */
    private boolean checkPlayerMove(final Player player, final Location from, final Location to, final int multiMoveCount,
                                    final PlayerMoveInfo moveInfo, final boolean debug, final MovingData data,
                                    final MovingConfig cc, final IPlayerData pData, final PlayerMoveEvent event) {

        if (player == null || from == null || to == null || moveInfo == null || data == null || cc == null ||
                pData == null) {
            return true;
        }

        Location newTo = null;
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final String playerName = player.getName(); // Could switch to UUID here (needs more changes).
        final long time = System.currentTimeMillis();
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        data.resetTeleported();
        // Horizontal distances are calculated on-demand in dedicated handlers
        // (e.g. checkPastStateHorizontalPush).

        debugOutput(player, moveInfo, cc, debug);

        // 1: Illegal coordinate check.
        if (handleIllegalCoordinates(player, event, data, cc, moveInfo)) {
            return true;
        }

        // 2: Location consistency.
        newTo = validateLocationConsistency(player, from, data, cc);

        // 3: Sprinting state update.
        updateSprintingState(player, time, data, cc);

        // 4: Prepare locations and vehicle state.
        final PlayerLocation pFrom = moveInfo.from;
        final PlayerLocation pTo = moveInfo.to;
        prepareMoveLocations(player, pFrom, pTo, data, cc, pData, debug);

        // 5: Initialize move data.
        initCurrentMove(thisMove, pFrom, pTo, multiMoveCount);

        // 6: Jump amplifier.
        final double jumpAmplifier = updateJumpAmplifier(player, data);

        // 7: Velocity tick.
        final int tick = updateVelocityTick(data, cc);


        ////////////////////////////////////
        // Check which fly check to use.  //
        ////////////////////////////////////
        FlyCheckResult flyResult = determineFlyCheck(player, pFrom, pTo, thisMove,
                multiMoveCount, tick, data, cc, pData, from, to, moveInfo);
        boolean checkCf = flyResult.checkCf;
        boolean checkSf = flyResult.checkSf;


        ////////////////////////////////////////////////////////////////////////
        // Pre-check checks (hum), either for cf or for sf.                   //
        ////////////////////////////////////////////////////////////////////////
        boolean checkNf = true;
        BounceType verticalBounce = BounceType.NO_BOUNCE;
        final boolean useBlockChangeTracker;
        final double previousSetBackY;
        final boolean checkPassable = pData.isCheckActive(CheckType.MOVING_PASSABLE, player);

        final Location portalTo = handleEndPortalFromBottom(player, lastMove, thisMove, pFrom, pTo, from, data);
        if (portalTo != null) {
            newTo = portalTo;
            checkNf = false;
        }

        updateMoveStatistics(player, pFrom, pTo, thisMove, lastMove, from, tick, data, cc);
        
        // 5: Pre-checks relevant to Sf or Cf.
        PreCheckData preData = runPreChecks(player, from, to, pFrom, pTo, thisMove, lastMove, checkSf,
                checkCf, checkPassable, tick, data, cc, pData, debug);
        if (newTo == null) {
            newTo = preData.newTo;
        }
        verticalBounce = preData.verticalBounce;
        useBlockChangeTracker = preData.useBlockChangeTracker;
        previousSetBackY = preData.previousSetBackY;
        checkNf = preData.checkNf;

        // 6: Check passable first to prevent set back override.
        if (newTo == null && checkPassable && player.getGameMode() != BridgeMisc.GAME_MODE_SPECTATOR) {
            newTo = runPassableCheck(player, pFrom, pTo, data, cc, pData, tick, useBlockChangeTracker);
        }
        
        // 7/8: Handle explosion velocity and bubble column launch.
        applyVelocityAdjustments(player, thisMove, lastMove, pFrom, tick, data, cc, checkSf, debug);

        newTo = runMovingChecks(player, pFrom, pTo, from, to, thisMove, lastMove, newTo, checkSf, checkCf,
                checkNf, previousSetBackY, useBlockChangeTracker, debug, multiMoveCount, time, tick, playerName,
                data, cc, pData);
        

        ////////////////////////////////////////////
        // Reset jump amplifier if needed.        //
        ////////////////////////////////////////////
        processNoFall(checkSf, checkCf, jumpAmplifier, thisMove, pFrom, pTo, data);

        
        ////////////////////////////////////////
        // Update BlockChangeTracker          //
        ////////////////////////////////////////
        updateBlockChangeTracker(player, pTo, data, useBlockChangeTracker, tick, debug);
        
        
        //////////////////////////////////////////////
        // Check if the move is to be allowed       //
        //////////////////////////////////////////////
        return finalizeMove(player, event, newTo, verticalBounce, tick, pFrom, pTo,
                thisMove, playerName, data, cc, pData, debug);
    }


    private void prepareCreativeFlyCheck(final Player player, final Location from, final Location to,
                                         final PlayerMoveInfo moveInfo, final PlayerMoveData thisMove, final int multiMoveCount,
                                         final int tick, final MovingData data, final MovingConfig cc) {

        data.adjustFlySpeed(player.getFlySpeed(), tick, cc.speedGrace);
        data.adjustWalkSpeed(player.getWalkSpeed(), tick, cc.speedGrace);
        // Adjust height of PlayerLocation more efficiently / fetch model early.
        final ModelFlying model = cc.getModelFlying(player, moveInfo.from, data, cc);
        if (MovingConfig.ID_JETPACK_ELYTRA.equals(model.getId())) {
            final MCAccess mcAccess = this.mcAccess.getHandle();
            MovingUtil.setElytraProperties(player, moveInfo.from, from, cc.yOnGround, mcAccess);
            MovingUtil.setElytraProperties(player, moveInfo.to, to, cc.yOnGround, mcAccess);
            thisMove.set(moveInfo.from, moveInfo.to);
            if (multiMoveCount > 0) thisMove.multiMoveCount = multiMoveCount;
        }
        thisMove.modelFlying = model;
    }

    private static class FlyCheckResult {
        final boolean checkCf;
        final boolean checkSf;
        FlyCheckResult(boolean cf, boolean sf) {
            this.checkCf = cf;
            this.checkSf = sf;
        }
    }

    private FlyCheckResult determineFlyCheck(final Player player, final PlayerLocation pFrom,
            final PlayerLocation pTo, final PlayerMoveData thisMove, final int multiMoveCount, final int tick,
            final MovingData data, final MovingConfig cc, final IPlayerData pData, final Location from,
            final Location to, final PlayerMoveInfo moveInfo) {
        boolean checkCf;
        boolean checkSf;
        if (MovingUtil.shouldCheckSurvivalFly(player, pFrom, pTo, data, cc, pData)) {
            checkCf = false;
            checkSf = true;
            data.adjustWalkSpeed(player.getWalkSpeed(), tick, cc.speedGrace);
        }
        else if (pData.isCheckActive(CheckType.MOVING_CREATIVEFLY, player)) {
            checkCf = true;
            checkSf = false;
            prepareCreativeFlyCheck(player, from, to, moveInfo, thisMove, multiMoveCount, tick, data, cc);
        }
        else {
            checkCf = false;
            checkSf = false;
        }
        return new FlyCheckResult(checkCf, checkSf);
    }

    private Location runPassableCheck(final Player player, final PlayerLocation pFrom, final PlayerLocation pTo,
            final MovingData data, final MovingConfig cc, final IPlayerData pData, final int tick,
            final boolean useBlockChangeTracker) {
        return passable.check(player, pFrom, pTo, data, cc, pData, tick, useBlockChangeTracker);
    }

    private boolean finalizeMove(final Player player, final PlayerMoveEvent event, Location newTo,
            final BounceType verticalBounce, final int tick, final PlayerLocation pFrom,
            final PlayerLocation pTo, final PlayerMoveData thisMove, final String playerName,
            final MovingData data, final MovingConfig cc, final IPlayerData pData, final boolean debug) {
        if (newTo == null) {
            if (data.hasTeleported()) {
                data.resetTeleported();
                if (debug) {
                    debug(player, "Ignore hook-induced set-back: actions not set to cancel.");
                }
            }
            if (verticalBounce != BounceType.NO_BOUNCE) {
                BounceUtil.processBounce(player, pFrom.getY(), pTo.getY(), verticalBounce, tick, this, data, cc, pData);
            }
            if (processingEvents.containsKey(playerName)) {
                data.playerMoves.finishCurrentMove();
            } else {
                thisMove.invalidate();
            }
            data.timeSinceSetBack++;
            return false;
        }
        if (data.hasTeleported()) {
            if (debug) {
                debug(player, "The set back has been overridden from(" + newTo + ") to: " + data.getTeleported());
            }
            newTo = data.getTeleported();
        }
        if (debug) {
            if (verticalBounce != BounceType.NO_BOUNCE) {
                debug(player, "Bounce effect not processed: " + verticalBounce);
            }
            if (data.verticalBounce != null) {
                debug(player, "Bounce effect not used: " + data.verticalBounce);
            }
        }
        prepareSetBack(player, event, newTo, data, cc, pData);
        if ((thisMove.flyCheck == CheckType.MOVING_SURVIVALFLY || thisMove.flyCheck == CheckType.MOVING_CREATIVEFLY
                && pFrom.isInLiquid()) && Bridge1_9.isGlidingWithElytra(player)) {
            player.setGliding(false);
        }
        return true;
    }

    private void debugOutput(final Player player, final PlayerMoveInfo moveInfo, final MovingConfig cc, final boolean debug) {
        if (debug) {
            outputMoveDebug(player, moveInfo.from, moveInfo.to,
                    Math.max(cc.noFallyOnGround, cc.yOnGround), mcAccess.getHandle());
        }
    }

    private boolean handleIllegalCoordinates(final Player player, final PlayerMoveEvent event,
            final MovingData data, final MovingConfig cc, final PlayerMoveInfo moveInfo) {
        if ((moveInfo.from.hasIllegalCoords() || moveInfo.to.hasIllegalCoords())
                || !cc.ignoreStance && (moveInfo.from.hasIllegalStance() || moveInfo.to.hasIllegalStance())) {
            MovingUtil.handleIllegalMove(event, player, data, cc);
            return true;
        }
        return false;
    }

    private Location validateLocationConsistency(final Player player, final Location from,
            final MovingData data, final MovingConfig cc) {
        return MovePreChecks.checkLocationConsistency(player, from, data, cc, playersEnforce, this::enforceLocation);
    }

    private void updateSprintingState(final Player player, final long time, final MovingData data,
            final MovingConfig cc) {
        MovePreChecks.updateSprinting(player, time, data, cc, attributeAccess.getHandle());
    }

    private void handlePowderSnow(final Player player, final PlayerLocation from, final PlayerLocation to,
            final MovingConfig cc, final boolean debug) {
        if (Bridge1_17.hasIsFrozen()) {
            final boolean hasBoots = Bridge1_17.hasLeatherBootsOn(player);
            if (to.isOnGround() && !hasBoots
                    && to.adjustOnGround(!to.isOnGroundDueToStandingOnAnEntity()
                            && !to.isOnGround(cc.yOnGround, BlockFlags.F_POWDERSNOW)) && debug) {
                debug(player, "Collide ground surface but not actually on ground. Adjusting To location.");
            }
            if (from.isOnGround() && !hasBoots
                    && from.adjustOnGround(!from.isOnGroundDueToStandingOnAnEntity()
                            && !from.isOnGround(cc.yOnGround, BlockFlags.F_POWDERSNOW)) && debug) {
                debug(player, "Collide ground surface but not actually on ground. Adjusting From location");
            }
        }
    }

    private void resetWindChargeImpulse(final Player player, final PlayerLocation from, final MovingData data) {
        if (data.timeRiptiding + 1500 > System.currentTimeMillis() || from.isInLiquid()
                || from.isOnClimbable() || Bridge1_9.isGlidingWithElytra(player)) {
            data.clearWindChargeImpulse();
        }
    }

    private void applyVelocityAdjustments(final MovingData data, final MovingConfig cc, final int tick) {
        data.velocityTick(tick - cc.velocityActivationTicks);
    }

    private void applyVelocityAdjustments(final Player player, final PlayerMoveData thisMove,
            final PlayerMoveData lastMove, final PlayerLocation pFrom, final int tick, final MovingData data,
            final MovingConfig cc, final boolean checkSf, final boolean debug) {
        VelocityProcessor.handleVelocity(player, thisMove, lastMove, data, cc, tick, checkSf, debug, pFrom);
    }

    private void updateBlockChangeTracker(final Player player, final PlayerLocation pTo, final MovingData data,
            final boolean useBlockChangeTracker, final int tick, final boolean debug) {
        if (useBlockChangeTracker && data.blockChangeRef.firstSpanEntry != null) {
            if (debug) {
                debug(player, "BlockChangeReference: " + data.blockChangeRef.firstSpanEntry.tick + " .. "
                        + data.blockChangeRef.lastSpanEntry.tick + " / " + tick);
            }
            data.blockChangeRef.updateFinal(pTo);
        }
    }

    private void processNoFall(final boolean checkSf, final boolean checkCf, final double jumpAmplifier,
            final PlayerMoveData thisMove, final PlayerLocation pFrom, final PlayerLocation pTo,
            final MovingData data) {
        if ((checkSf || checkCf) && jumpAmplifier != data.jumpAmplifier) {
            if (thisMove.touchedGround || !checkSf && (pFrom.isOnGround() || pTo.isOnGround())) {
                data.jumpAmplifier = jumpAmplifier;
            }
        }
    }

    private void prepareMoveLocations(final Player player, final PlayerLocation pFrom,
            final PlayerLocation pTo, final MovingData data, final MovingConfig cc,
            final IPlayerData pData, final boolean debug) {
        if (player == null || pFrom == null || pTo == null || data == null || cc == null || pData == null) {
            return;
        }
        handlePowderSnow(player, pFrom, pTo, cc, debug);
        resetWindChargeImpulse(player, pFrom, data);
        if (data.wasInVehicle) {
            vehicleChecks.onVehicleLeaveMiss(player, data, cc, pData);
        }
    }

    private void initCurrentMove(final PlayerMoveData move, final PlayerLocation pFrom,
            final PlayerLocation pTo, final int multiMoveCount) {
        if (move == null || pFrom == null || pTo == null) {
            return;
        }
        move.set(pFrom, pTo);
        if (multiMoveCount > 0) {
            move.multiMoveCount = multiMoveCount;
        }
    }

    private double updateJumpAmplifier(final Player player, final MovingData data) {
        if (player == null || data == null) {
            return 0.0;
        }
        final double amplifier = aux.getJumpAmplifier(player);
        if (amplifier > data.jumpAmplifier) {
            data.jumpAmplifier = amplifier;
        }
        return amplifier;
    }

    private int updateVelocityTick(final MovingData data, final MovingConfig cc) {
        final int tick = TickTask.getTick();
        applyVelocityAdjustments(data, cc, tick);
        return tick;
    }

    private PreCheckData runPreChecks(final Player player, final Location from, final Location to,
            final PlayerLocation pFrom, final PlayerLocation pTo, final PlayerMoveData thisMove,
            final PlayerMoveData lastMove, final boolean checkSf, final boolean checkCf,
            final boolean checkPassable, final int tick, final MovingData data,
            final MovingConfig cc, final IPlayerData pData, final boolean debug) {

        if (player == null || pFrom == null || pTo == null || thisMove == null || lastMove == null
                || data == null || cc == null || pData == null) {
            return new PreCheckData(null, BounceType.NO_BOUNCE, false, Double.NEGATIVE_INFINITY, true);
        }

        boolean checkNf = true;
        BounceType verticalBounce = BounceType.NO_BOUNCE;
        boolean useBlockChangeTracker;
        double previousSetBackY;
        Location newTo = null;

        if (checkSf || checkCf) {
            previousSetBackY = data.hasSetBack() ? data.getSetBackY() : Double.NEGATIVE_INFINITY;
            MovingUtil.checkSetBack(player, pFrom, data, pData, this);

            final Location cross = handleCrossWorldTeleport(player, from, pFrom, pTo, data);
            if (cross != null) {
                newTo = cross;
                checkNf = false;
            }

            if (newTo == null && isExtremeMove(thisMove)) {
                newTo = checkExtremeMove(player, pFrom, pTo, data, cc);
                if (newTo != null) {
                    thisMove.flyCheck = checkSf ? CheckType.MOVING_SURVIVALFLY : CheckType.MOVING_CREATIVEFLY;
                }
            }

            useBlockChangeTracker = shouldTrackBlockChanges(from, pFrom, pTo, checkPassable || checkSf || checkCf, cc,
                    newTo);

            if (newTo == null) {
                BounceResult res = processBounceAndPush(player, from, to, pFrom, pTo, thisMove, lastMove, tick, debug,
                        data, cc, pData, useBlockChangeTracker);
                verticalBounce = res.verticalBounce;
                checkNf = res.checkNf;
            }
        } else {
            useBlockChangeTracker = false;
            previousSetBackY = Double.NEGATIVE_INFINITY;
        }

        return new PreCheckData(newTo, verticalBounce, useBlockChangeTracker, previousSetBackY, checkNf);
    }

    private boolean isExtremeMove(final PlayerMoveData move) {
        if (move == null) {
            return false;
        }
        return Math.abs(move.yDistance) > Magic.EXTREME_MOVE_DIST_VERTICAL
                || move.hDistance > Magic.EXTREME_MOVE_DIST_HORIZONTAL;
    }

    private Location handleCrossWorldTeleport(final Player player, final Location from, final PlayerLocation pFrom,
            final PlayerLocation pTo, final MovingData data) {
        if (data.crossWorldFrom != null) {
            if (!TrigUtil.isSamePosAndLook(pFrom, pTo) && TrigUtil.isSamePosAndLook(pTo, data.crossWorldFrom)) {
                NCPAPIProvider.getNoCheatPlusAPI().getLogManager().warning(Streams.STATUS,
                        CheckUtils.getLogMessagePrefix(player, CheckType.MOVING)
                                + " Player move end point seems to be set wrongly.");
                data.crossWorldFrom = null;
                return data.getSetBack(from);
            }
            data.crossWorldFrom = null;
        }
        return null;
    }

    private boolean shouldTrackBlockChanges(final Location from, final PlayerLocation pFrom,
            final PlayerLocation pTo, final boolean hasCheck, final MovingConfig cc, final Location newTo) {
        if (newTo != null || !hasCheck || cc == null || !cc.trackBlockMove || from == null
                || from.getWorld() == null) {
            return false;
        }
        return blockChangeTracker.hasActivityShuffled(from.getWorld().getUID(), pFrom, pTo, 1.5625);
    }

    private BounceResult processBounceAndPush(final Player player, final Location from, final Location to,
            final PlayerLocation pFrom, final PlayerLocation pTo, final PlayerMoveData thisMove,
            final PlayerMoveData lastMove, final int tick, final boolean debug, final MovingData data,
            final MovingConfig cc, final IPlayerData pData, final boolean useBlockChangeTracker) {

        BounceType verticalBounce = BounceType.NO_BOUNCE;
        boolean checkNf = true;

        if (thisMove.yDistance < 0.0) {
            if (!survivalFly.isReallySneaking(player)
                    && BounceUtil.checkBounceEnvelope(player, pFrom, pTo, data, cc, pData)) {
                if ((pTo.getBlockFlags() & BlockFlags.F_BOUNCE25) != 0L) {
                    verticalBounce = BounceType.STATIC;
                    checkNf = false;
                }
                if (verticalBounce == BounceType.NO_BOUNCE && useBlockChangeTracker && BounceUtil
                        .checkPastStateBounceDescend(player, pFrom, pTo, thisMove, lastMove, tick, data, cc,
                                blockChangeTracker) != BounceType.NO_BOUNCE) {
                    checkNf = false;
                }
            }
        } else {
            if ((data.verticalBounce != null
                    && BounceUtil.onPreparedBounceSupport(player, from, to, thisMove, lastMove, tick, data))
                    || useBlockChangeTracker && thisMove.yDistance <= 1.515) {
                verticalBounce = BounceUtil.checkPastStateBounceAscend(player, pFrom, pTo, thisMove, lastMove, tick,
                        pData, this, data, cc, blockChangeTracker);
                if (verticalBounce != BounceType.NO_BOUNCE) {
                    checkNf = false;
                }
            }
        }

        if (useBlockChangeTracker && checkNf
                && !checkPastStateVerticalPush(player, pFrom, pTo, thisMove, lastMove, tick, debug, data, cc)) {
            checkPastStateHorizontalPush(player, pFrom, pTo, thisMove, lastMove, tick, debug, data, cc);
        }

        return new BounceResult(verticalBounce, checkNf);
    }

    private static class BounceResult {
        final BounceType verticalBounce;
        final boolean checkNf;
        BounceResult(BounceType verticalBounce, boolean checkNf) {
            this.verticalBounce = verticalBounce;
            this.checkNf = checkNf;
        }
    }

    private static class PreCheckData {
        final Location newTo;
        final BounceType verticalBounce;
        final boolean useBlockChangeTracker;
        final double previousSetBackY;
        final boolean checkNf;
        PreCheckData(Location newTo, BounceType verticalBounce, boolean useBlockChangeTracker,
                double previousSetBackY, boolean checkNf) {
            this.newTo = newTo;
            this.verticalBounce = verticalBounce;
            this.useBlockChangeTracker = useBlockChangeTracker;
            this.previousSetBackY = previousSetBackY;
            this.checkNf = checkNf;
        }
    }

    private Location handleEndPortalFromBottom(final Player player, final PlayerMoveData lastMove,
            final PlayerMoveData thisMove, final PlayerLocation pFrom, final PlayerLocation pTo,
            final Location from, final MovingData data) {
        if (lastMove.to.getWorldName() != null
                && !lastMove.to.getWorldName().equals(thisMove.from.getWorldName())) {
            if (TrigUtil.distance(pFrom, pTo) > 5.5) {
                NCPAPIProvider.getNoCheatPlusAPI().getLogManager().warning(Streams.STATUS,
                        CheckUtils.getLogMessagePrefix(player, CheckType.MOVING)
                                + " Player move end point seems to be set wrongly.");
                return data.getSetBack(from);
            }
        }
        return null;
    }

    private void updateMoveStatistics(final Player player, final PlayerLocation pFrom, final PlayerLocation pTo,
            final PlayerMoveData thisMove, final PlayerMoveData lastMove, final Location from, final int tick,
            final MovingData data, final MovingConfig cc) {
        if (data == null || lastMove == null || thisMove == null) {
            return;
        }

        handleFireworkBoost(player, lastMove, thisMove, tick, data, cc);
        updateLiquidTick(pFrom, data);
        updateRiptideState(player, data);
        updateBubbleStreamState(pFrom, pTo, thisMove, lastMove, data);
        handleVehicleExit(from, thisMove, data);
    }

    private void handleFireworkBoost(final Player player, final PlayerMoveData lastMove,
            final PlayerMoveData thisMove, final int tick, final MovingData data, final MovingConfig cc) {
        if (data.fireworksBoostDuration <= 0) {
            data.hasFireworkBoost = false;
            return;
        }
        final int remaining = data.fireworksBoostDuration;
        final boolean invalidBoost = !lastMove.valid
                || (cc != null && cc.resetFwOnground
                        && (lastMove.flyCheck != CheckType.MOVING_CREATIVEFLY
                                || lastMove.modelFlying != thisMove.modelFlying))
                || data.fireworksBoostTickExpire < tick;
        if (invalidBoost) {
            data.fireworksBoostDuration = 0;
            data.hasFireworkBoost = false;
            ElytraBoostHandler.logBoostEvent(player, "reset", tick, remaining);
        } else {
            data.fireworksBoostDuration--;
            if (data.fireworksBoostDuration == 0) {
                data.hasFireworkBoost = false;
                ElytraBoostHandler.logBoostEvent(player, "ended", tick, remaining);
            }
        }
    }

    private void updateLiquidTick(final PlayerLocation from, final MovingData data) {
        if (from != null && from.isInLiquid()) {
            data.liqtick = data.liqtick < 10 ? data.liqtick + 1 : data.liqtick > 0 ? data.liqtick - 1 : 0;
        } else {
            data.liqtick = data.liqtick > 0 ? data.liqtick - 2 : 0;
        }
    }

    private void updateRiptideState(final Player player, final MovingData data) {
        if (player != null && Bridge1_13.isRiptiding(player)) {
            data.timeRiptiding = System.currentTimeMillis();
        }
    }

    private void updateBubbleStreamState(final PlayerLocation from, final PlayerLocation to,
            final PlayerMoveData thisMove, final PlayerMoveData lastMove, final MovingData data) {
        if (from != null && to != null && !from.isDraggedByBubbleStream() && !to.isDraggedByBubbleStream()
                && (from.isInBubbleStream() || to.isInBubbleStream()) && thisMove.yDistance > 0.0
                && data.insideBubbleStreamCount <= 50) {
            data.insideBubbleStreamCount++;
        }
        if (data.insideBubbleStreamCount > 0) {
            if (!lastMove.valid || lastMove.flyCheck != CheckType.MOVING_SURVIVALFLY
                    || !data.liftOffEnvelope.name().startsWith("LIMIT")) {
                data.insideBubbleStreamCount = 0;
            } else if ((from == null || !from.isInBubbleStream()) && (to == null || !to.isInBubbleStream())
                    || thisMove.yDistance < 0.0) {
                data.insideBubbleStreamCount--;
            }
        }
    }

    private void handleVehicleExit(final Location from, final PlayerMoveData thisMove, final MovingData data) {
        if (data.lastVehicleType != null && thisMove.distanceSquared < 5) {
            if (from != null) {
                data.setSetBack(from);
            }
            data.addHorizontalVelocity(new AccountEntry(thisMove.hDistance, 1, 1));
            data.addVerticalVelocity(new SimpleEntry(thisMove.yDistance, 1));
            data.lastVehicleType = null;
        }
    }

    private Location runMovingChecks(final Player player, final PlayerLocation pFrom, final PlayerLocation pTo,
            final Location from, final Location to, final PlayerMoveData thisMove, final PlayerMoveData lastMove,
            Location newTo, final boolean checkSf, final boolean checkCf, final boolean checkNf,
            final double previousSetBackY, final boolean useBlockChangeTracker, final boolean debug,
            final int multiMoveCount, final long time, final int tick, final String playerName,
            final MovingData data, final MovingConfig cc, final IPlayerData pData) {

        if (checkSf) {
            final MoveCheckResult result = handleSurvivalFlyCheck(player, pFrom, pTo, from, to, thisMove, lastMove,
                    newTo, checkNf, previousSetBackY, useBlockChangeTracker, debug, multiMoveCount, time, tick,
                    playerName, data, cc, pData);
            newTo = result.newTo;
        } else if (checkCf) {
            newTo = handleCreativeFlyCheck(player, pFrom, pTo, thisMove, newTo, checkNf, previousSetBackY,
                    useBlockChangeTracker, time, tick, data, cc, pData);
        } else {
            data.clearFlyData();
        }

        newTo = handleMorePacketsCheck(player, pFrom, pTo, newTo, debug, data, cc, pData);

        return newTo;
    }

    private MoveCheckResult handleSurvivalFlyCheck(final Player player, final PlayerLocation pFrom,
            final PlayerLocation pTo, final Location from, final Location to, final PlayerMoveData thisMove,
            final PlayerMoveData lastMove, Location newTo, boolean checkNf, final double previousSetBackY,
            final boolean useBlockChangeTracker, final boolean debug, final int multiMoveCount, final long time,
            final int tick, final String playerName, final MovingData data, final MovingConfig cc,
            final IPlayerData pData) {

        if (player == null || pFrom == null || pTo == null || from == null || to == null
                || data == null || cc == null || pData == null) {
            return new MoveCheckResult(newTo);
        }

        MovingUtil.prepareFullCheck(pFrom, pTo, thisMove, Math.max(cc.noFallyOnGround, cc.yOnGround));
        handleFlyCheckTransition(lastMove, player, time, tick, debug, data, cc);

        if (newTo == null) {
            newTo = performSurvivalFlyCheck(player, pFrom, pTo, thisMove, multiMoveCount,
                    data, cc, pData, tick, time, useBlockChangeTracker);
        }

        if (checkNf) {
            checkNf = noFall.isEnabled(player, pData);
        }

        if (newTo == null) {
            handleHoverAndNoFall(player, pFrom, pTo, lastMove, playerName, checkNf,
                    previousSetBackY, data, cc, pData);
        } else {
            handleFallDamageForSetBack(player, from, to, pFrom, pTo, checkNf,
                    previousSetBackY, data, cc, pData);
        }

        return new MoveCheckResult(newTo);
    }

    private void handleFlyCheckTransition(final PlayerMoveData lastMove, final Player player,
            final long time, final int tick, final boolean debug, final MovingData data, final MovingConfig cc) {
        if (lastMove.toIsValid && lastMove.flyCheck == CheckType.MOVING_CREATIVEFLY) {
            final long tickHasLag = data.delayWorkaround + Math.round(200 / TickTask.getLag(200, true));
            if (data.delayWorkaround > time || tickHasLag < time) {
                workaroundFlyCheckTransition(player, tick, debug, data, cc);
                data.delayWorkaround = time;
            }
        }
    }

    private Location performSurvivalFlyCheck(final Player player, final PlayerLocation pFrom,
            final PlayerLocation pTo, final PlayerMoveData thisMove, final int multiMoveCount,
            final MovingData data, final MovingConfig cc, final IPlayerData pData, final int tick,
            final long time, final boolean useBlockChangeTracker) {
        thisMove.flyCheck = CheckType.MOVING_SURVIVALFLY;
        final SurvivalFlyCheckContext ctx = new SurvivalFlyCheckContext(player, pFrom, pTo,
                multiMoveCount, data, cc, pData, tick, time, useBlockChangeTracker);
        return survivalFly.check(ctx);
    }

    private void handleHoverAndNoFall(final Player player, final PlayerLocation pFrom,
            final PlayerLocation pTo, final PlayerMoveData lastMove, final String playerName,
            final boolean checkNf, final double previousSetBackY, final MovingData data,
            final MovingConfig cc, final IPlayerData pData) {
        if (cc.sfHoverCheck && !(lastMove.toIsValid && lastMove.to.extraPropertiesValid
                && lastMove.to.onGroundOrResetCond) && !pTo.isOnGround()) {
            hoverTicks.add(playerName);
            data.sfHoverTicks = 0;
        } else {
            data.sfHoverTicks = -1;
        }
        if (checkNf) {
            noFall.check(player, pFrom, pTo, previousSetBackY, data, cc, pData);
        }
    }

    private void handleFallDamageForSetBack(final Player player, final Location from, final Location to,
            final PlayerLocation pFrom, final PlayerLocation pTo, final boolean checkNf,
            final double previousSetBackY, final MovingData data, final MovingConfig cc,
            final IPlayerData pData) {
        if (checkNf && cc.sfSetBackPolicyApplyFallDamage) {
            boolean skip = !noFall.willDealFallDamage(player, from.getY(), previousSetBackY, data);
            if (!skip && (!pFrom.isOnGround() && !pFrom.isResetCond())) {
                skip = false;
            }
            if (!skip && (!pTo.isResetCond() || !pFrom.isResetCond())) {
                noFall.checkDamage(player, Math.min(from.getY(), to.getY()), data, pData);
            }
        }
    }

    private Location handleCreativeFlyCheck(final Player player, final PlayerLocation pFrom,
            final PlayerLocation pTo, final PlayerMoveData thisMove, Location newTo, final boolean checkNf,
            final double previousSetBackY, final boolean useBlockChangeTracker, final long time, final int tick,
            final MovingData data, final MovingConfig cc, final IPlayerData pData) {

        if (newTo == null) {
            thisMove.flyCheck = CheckType.MOVING_CREATIVEFLY;
            newTo = creativeFly.check(player, pFrom, pTo, data, cc, pData, time, tick, useBlockChangeTracker);
            if (checkNf && noFall.isEnabled(player, pData)) {
                noFall.check(player, pFrom, pTo, previousSetBackY, data, cc, pData);
            }
        }
        data.sfHoverTicks = -1;
        data.sfLowJump = false;
        return newTo;
    }

    private Location handleMorePacketsCheck(final Player player, final PlayerLocation pFrom,
            final PlayerLocation pTo, Location newTo, final boolean debug, final MovingData data,
            final MovingConfig cc, final IPlayerData pData) {

        if (pData.isCheckActive(CheckType.MOVING_MOREPACKETS, player)
                && (newTo == null || data.isMorePacketsSetBackOldest())) {
            final Location mpNewTo = morePackets.check(player, pFrom, pTo, newTo == null, data, cc, pData);
            if (mpNewTo != null) {
                if (newTo != null && debug) {
                    debug(player, "Override set back by the older morepackets set back.");
                }
                newTo = mpNewTo;
            }
        } else {
            data.clearPlayerMorePacketsData();
        }

        return newTo;
    }

    private static class MoveCheckResult {
        final Location newTo;
        MoveCheckResult(final Location newTo) {
            this.newTo = newTo;
        }
    }

    
    /**
     * Vertical block push
     * @param player
     * @param from
     * @param to
     * @param thisMove
     * @param lastMove
     * @param debug
     * @param data
     * @param cc
     * @return
     */
    private boolean checkPastStateVerticalPush(final Player player, final PlayerLocation from, final PlayerLocation to,
                                               final PlayerMoveData thisMove, final PlayerMoveData lastMove, final int tick,
                                               final boolean debug, final MovingData data, final MovingConfig cc) {

        final UUID worldId = from.getWorld().getUID();
        final VerticalPush push = calculateVerticalPush(player, from, to, thisMove, lastMove, tick, debug, data, cc,
                worldId);
        if (push != null) {
            applyVerticalPush(player, tick, thisMove, data, push, debug);
            return true;
        }
        return false;
    }

    private VerticalPush calculateVerticalPush(final Player player, final PlayerLocation from,
            final PlayerLocation to, final PlayerMoveData thisMove, final PlayerMoveData lastMove, final int tick,
            final boolean debug, final MovingData data, final MovingConfig cc, final UUID worldId) {

        double amount = -1.0;
        boolean addvel = false;
        final BlockChangeEntry entryBelowY_POS = blockChangeSearch(from, tick, Direction.Y_POS, debug, data, cc,
                worldId, true);

        if (entryBelowY_POS != null) {
            if (debug) {
                final StringBuilder builder = new StringBuilder(150);
                builder.append("Direct block push at (");
                builder.append("x:" + entryBelowY_POS.x);
                builder.append(" y:" + entryBelowY_POS.y);
                builder.append(" z:" + entryBelowY_POS.z);
                builder.append(" direction:" + entryBelowY_POS.direction.name());
                builder.append(")");
                debug(player, builder.toString());
            }
            if (lastMove.valid && thisMove.yDistance >= 0.0) {
                if ((from.isOnGroundOrResetCond() || thisMove.touchedGroundWorkaround) && from.isOnGround(1.0)) {
                    amount = Math.min(thisMove.yDistance, 0.5625);
                } else if (lastMove.yDistance < -Magic.GRAVITY_MAX) {
                    amount = Math.min(thisMove.yDistance, 0.34);
                }
                if (thisMove.yDistance == 0.0) {
                    amount = 0.0;
                }
            }
            if (lastMove.toIsValid && amount < 0.0 && thisMove.yDistance < 0.0 && thisMove.yDistance > -1.515
                    && lastMove.yDistance >= 0.0) {
                amount = thisMove.yDistance;
                addvel = true;
            }
            data.blockChangeRef.updateSpan(entryBelowY_POS);
        }

        if (amount >= 0.0 || addvel) {
            return new VerticalPush(amount);
        }
        return null;
    }

    private void applyVerticalPush(final Player player, final int tick, final PlayerMoveData thisMove,
            final MovingData data, final VerticalPush push, final boolean debug) {
        data.removeLeadingQueuedVerticalVelocityByFlag(VelocityFlags.ORIGIN_BLOCK_MOVE);
        final SimpleEntry vel = new SimpleEntry(tick, push.amount, VelocityFlags.ORIGIN_BLOCK_MOVE, 1);
        data.verticalBounce = vel;
        data.useVerticalBounce(player);
        data.useVerticalVelocity(thisMove.yDistance);
        if (debug) {
            debug(player, "checkPastStateVerticalPush: set velocity: " + vel);
        }
    }

    private static class VerticalPush {
        final double amount;

        VerticalPush(final double amount) {
            this.amount = amount;
        }
    }


    /**
     * Search for blockchange entries.
     * @param from
     * @param tick
     * @param direction
     * @param debug
     * @param data
     * @param cc
     * @param worldId
     * @param searchBelow
     * @return
     */
    private BlockChangeEntry blockChangeSearch(final PlayerLocation from, final int tick, Direction direction,
                                               final boolean debug, final MovingData data, final MovingConfig cc,
                                               final UUID worldId, final boolean searchBelow) {

        final int iMinX = Location.locToBlock(from.getMinX());
        final int iMaxX = Location.locToBlock(from.getMaxX());
        final int iMinZ = Location.locToBlock(from.getMinZ());
        final int iMaxZ = Location.locToBlock(from.getMaxZ());
        final int belowY = from.getBlockY() - (searchBelow ? 1 : 0);
        for (int x = iMinX; x <= iMaxX; x++) {
            for (int z = iMinZ; z <= iMaxZ; z++) {
                for (int y = belowY; y <= belowY + 1; y++) {
                    BlockChangeEntry entryBelowY_POS = blockChangeTracker.getBlockChangeEntry(
                    data.blockChangeRef, tick, worldId, x, y, z, 
                    direction);
                    if (entryBelowY_POS != null) return entryBelowY_POS;
                }
            }
        }
        return null;
    }
    

    /**
     * Horizontal block push
     * @param player
     * @param from
     * @param to
     * @param thisMove
     * @param lastMove
     * @param debug
     * @param data
     * @param cc
     * @return
     */
    private boolean checkPastStateHorizontalPush(final Player player, final PlayerLocation from, final PlayerLocation to,
                                                 final PlayerMoveData thisMove, final PlayerMoveData lastMove, final int tick, 
                                                 final boolean debug, final MovingData data, final MovingConfig cc) {

        final UUID worldId = from.getWorld().getUID();
        final double xDistance = to.getX() - from.getX();
        final double zDistance = to.getZ() - from.getZ();
        final Direction dir;
        if (Math.abs(xDistance) > Math.abs(zDistance)) dir = xDistance > 0.0 ? Direction.X_POS : Direction.X_NEG;
        else  dir = zDistance > 0.0 ? Direction.Z_POS : Direction.Z_NEG;
    
        final BlockChangeEntry entry = blockChangeSearch(from, tick, dir, debug, data, cc, worldId, false);
        if (entry != null) {
            final int count = MovingData.getHorVelValCount(0.6);
            // Clear active horizontal velocity?
            data.clearActiveHorVel();
            data.addHorizontalVelocity(new AccountEntry(tick, 0.6, count, count));
            // Stuck in block, Hack
            data.addVerticalVelocity(new SimpleEntry(-0.35, 6));
            data.blockChangeRef.updateSpan(entry);

            if (debug) {
                final StringBuilder builder = new StringBuilder(150);
                builder.append("Direct block push at (");
                builder.append("x:" + entry.x);
                builder.append(" y:" + entry.y);
                builder.append(" z:" + entry.z);
                builder.append(" direction:" + entry.direction.name());
                builder.append(")");
                debug(player, builder.toString());
                debug(player, "checkPastStateHorizontalPush: set velocity: " + 0.6);
            }
            return true;
        }
        return false;
    }


    /**
     * Check for extremely large moves. Initial intention is to prevent cheaters
     * from creating extreme load. SurvivalFly or CreativeFly is needed.
     * 
     * @param player
     * @param from
     * @param to
     * @param data
     * @param cc
     * @return
     */
    @SuppressWarnings("unused")
    private Location checkExtremeMove(final Player player, final PlayerLocation from, final PlayerLocation to,
                                      final MovingData data, final MovingConfig cc) {
        if (player == null || data == null) {
            return null;
        }
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final MoveCheckContext ctx = new MoveCheckContext(player, thisMove, lastMove, data);
        return ExtremeMoveHandler.handleExtremeMove(ctx, from, to, cc, survivalFly, creativeFly);
    }


    /**
     * Add velocity, in order to work around issues with transitions between Fly checks.
     * Asserts last distances are set.
     * 
     * @param tick
     * @param data
     */
    private void workaroundFlyCheckTransition(final Player player, final int tick, final boolean debug, 
                                              final MovingData data, final MovingConfig cc) {

        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final double amount = guessVelocityAmount(player, data.playerMoves.getCurrentMove(), lastMove, data, cc);
        data.clearActiveHorVel(); // Clear active velocity due to adding actual speed here.
        data.setBunnyhopDelay(0); // Remove bunny hop due to add velocity
        if (amount > 0.0) data.addHorizontalVelocity(new AccountEntry(tick, amount, cc.velocityActivationCounter, MovingData.getHorVelValCount(amount)));
        data.addVerticalVelocity(new SimpleEntry(lastMove.yDistance, cc.velocityActivationCounter));
        data.addVerticalVelocity(new SimpleEntry(0.0, cc.velocityActivationCounter));
        data.setFrictionJumpPhase();
        if (debug) debug(player, "*** Transition from CreativeFly to SurvivalFly: Add velocity.");
    }


    private static double guessVelocityAmount(final Player player, final PlayerMoveData thisMove, final PlayerMoveData lastMove, 
                                              final MovingData data, final MovingConfig cc) {

        // Default margin: Allow slightly less than the previous speed.
        final double defaultAmount = lastMove.hDistance * (1.0 + Magic.FRICTION_MEDIUM_AIR) / 2.0;
        // Test for exceptions.
        if (Bridge1_9.isWearingElytra(player) && lastMove.modelFlying != null && lastMove.modelFlying.getId().equals(MovingConfig.ID_JETPACK_ELYTRA)) {
            // Still elytra move, not forcing CreativeFly check, just pass the res to velocity
            final MoveCheckContext ctx = new MoveCheckContext(player, thisMove, lastMove, data);
            final VelocityAdjustment res = CreativeFly.guessElytraVelocityAmount(ctx);
            //data.addVerticalVelocity(new SimpleEntry(lastMove.yDistance < -0.1034 ? (lastMove.yDistance * Magic.FRICTION_MEDIUM_AIR + 0.1034) 
            //                                        : lastMove.yDistance, cc.velocityActivationCounter));
            data.keepfrictiontick = -15;
            data.addVerticalVelocity(new SimpleEntry(res.vertical(), cc.velocityActivationCounter));
            return res.horizontal();
            //if (thisMove.hDistance > defaultAmount) {
                // Allowing the same speed won't always work on elytra (still increasing, differing modeling on client side with motXYZ).
                // (Doesn't seem to be overly effective.)
            //    if (data.fireworksBoostDuration > 0) {
            //        return 2.0;
            //    } 
            //    else if (lastMove.toIsValid && lastMove.hAllowedDistance > 0.0) return lastMove.hAllowedDistance; // This one might replace below?
            //    return defaultAmount + 0.5;
            //}
        }
        else if (lastMove.modelFlying != null && lastMove.modelFlying.getId().equals(MovingConfig.ID_EFFECT_RIPTIDING)){
            data.addVerticalVelocity(new SimpleEntry(0.0, 10)); // Not using cc.velocityActivationCounter to be less exploitable.
            data.keepfrictiontick = -7;
        }
        return defaultAmount;
    }


    /**
     * Called during PlayerMoveEvent for adjusting to a to-be-done/scheduled set
     * back. <br>
     * NOTE: Meaning differs from data.onSetBack (to be cleaned up).
     * 
     * @param player
     * @param event
     * @param newTo
     *            Must be a cloned or new Location instance, free for whatever
     *            other plugins do with it.
     * @param data
     * @param cc
     */
    private void prepareSetBack(final Player player, final PlayerMoveEvent event, final Location newTo, 
                                final MovingData data, final MovingConfig cc, final IPlayerData pData) {

        // Illegal Yaw/Pitch.
        if (LocUtil.needsYawCorrection(newTo.getYaw())) {
            newTo.setYaw(LocUtil.correctYaw(newTo.getYaw()));
        }
        if (LocUtil.needsPitchCorrection(newTo.getPitch())) {
            newTo.setPitch(LocUtil.correctPitch(newTo.getPitch()));
        }
        // Reset some data.
        data.prepareSetBack(newTo);
        aux.resetPositionsAndMediumProperties(player, newTo, data, cc); // Might move into prepareSetBack, experimental here.

        // Set new to-location, distinguish method by settings.
        final PlayerSetBackMethod method = cc.playerSetBackMethod;
        if (method.shouldSetTo()) {
            event.setTo(newTo); // LEGACY: pre-2017-03-24
            if (pData.isDebugActive(checkType)) debug(player, "Set back type: SET_TO");
        }
        if (method.shouldCancel()) {
            event.setCancelled(true);
            if (pData.isDebugActive(checkType)) debug(player, "Set back type: CANCEL (schedule:" + method.shouldSchedule() + " updatefrom:" + method.shouldUpdateFrom() + ")");
        } 
        else if (pData.isDebugActive(checkType)) debug(player, "No setback performed!");
        // NOTE: A teleport is scheduled on MONITOR priority, if set so.
        // enforcelocation?
        // Debug.
        if (pData.isDebugActive(checkType)) {
            debug(player, "Prepare set back to: " + newTo.getWorld().getName() + "/" + LocUtil.simpleFormatPosition(newTo) + " (" + method.getId() + ")");
        }
    }


    /**
     * Monitor level PlayerMoveEvent. Uses useLoc.
     * @param event
     */
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerMoveMonitor(final PlayerMoveEvent event) {

        // Use stored move data to verify if from/to have changed (thus a teleport will result, possibly a minor issue due to the teleport).
        final long now = System.currentTimeMillis();
        final Player player = event.getPlayer();
        final IPlayerData pData = fetchPlayerData(player, "move monitor");
        if (pData == null) {
            return;
        }
        // This means moving data has been reset by a teleport.
        if (processingEvents.remove(player.getName()) == null) return;
        if (player.isDead() || player.isSleeping()) return;
        if (!pData.isCheckActive(CheckType.MOVING, player)) return;
        // Feed combined check.
        final CombinedData data = pData.getGenericInstance(CombinedData.class);
        data.lastMoveTime = now; 
        final Location from = event.getFrom();
        // Feed yawrate and reset moving data positions if necessary.
        final MovingData mData = pData.getGenericInstance(MovingData.class);
        final int tick = TickTask.getTick();
        final MovingConfig mCc = pData.getGenericInstance(MovingConfig.class);

        if (!event.isCancelled()) {
            final Location pLoc = player.getLocation(useLoc);
            onMoveMonitorNotCancelled(player, TrigUtil.isSamePosAndLook(pLoc, from) ? from : pLoc, event.getTo(), now, tick, data, mData, mCc, pData);
            useLoc.setWorld(null);
        }
        else onCancelledMove(player, from, tick, now, mData, mCc, data, pData);
    }


    /**
     * Adjust data for a cancelled move. No teleport event will fire, but an
     * outgoing position is sent. Note that event.getFrom() may be overridden by
     * a plugin, which the server will ignore, but can lead to confusion.
     * 
     * @param player
     * @param from
     * @param tick
     * @param now
     * @param mData
     * @param data
     */
    private void onCancelledMove(final Player player, final Location from, final int tick, final long now, 
                                 final MovingData mData, final MovingConfig mCc, final CombinedData data,
                                 final IPlayerData pData) {

        final boolean debug = pData.isDebugActive(checkType);
        // Detect our own set back, choice of reference location.
        if (mData.hasTeleported()) {
            final Location ref = mData.getTeleported();
            // Initiate further action depending on settings.
            final PlayerSetBackMethod method = mCc.playerSetBackMethod;
            if (method.shouldUpdateFrom()) {
                // Attempt to do without a PlayerTeleportEvent as follow up.
                // Doing this on MONITOR priority is problematic, despite optimal.
                LocUtil.set(from, ref);
            }
            if (method.shouldSchedule()) {
                // Schedule the teleport, because it might be faster than the next incoming packet.
                final IPlayerData pd = dataManager.getPlayerData(player);
                if (pd.isPlayerSetBackScheduled()) debug(player, "Teleport (set back) already scheduled to: " + ref);
                else if (debug) {
                    pd.requestPlayerSetBack();
                    if (debug)  debug(player, "Schedule teleport (set back) to: " + ref);
                }
            }
            // (Position adaption will happen with the teleport on tick, or with the next move.)
        }

        // Assume the implicit teleport to the from-location (no Bukkit event fires).
        Combined.resetYawRate(player, from.getYaw(), now, false, pData); // Not reset frequency, but do set yaw.
        aux.resetPositionsAndMediumProperties(player, from, mData, mCc); 
        // Should probably leave this to the teleport event!
        mData.resetTrace(player, from, tick, mcAccess.getHandle(), mCc);

        // Expect a teleport to the from location (packet balance, no Bukkit event will fire).
        if (pData.isCheckActive(CheckType.NET_FLYINGFREQUENCY, player)) { // A summary method.
            pData.getGenericInstance(NetData.class).teleportQueue.onTeleportEvent(from.getX(), from.getY(), from.getZ(), from.getYaw(), from.getPitch());
        }
    }


    /**
     * Uses useLoc if in vehicle.
     * @param player
     * @param from Might use useLoc, but will reset it, if in vehicle.
     * @param to Do not use useLoc for this.
     * @param now
     * @param tick
     * @param data
     * @param mData
     */
    private void onMoveMonitorNotCancelled(final Player player, final Location from, final Location to, 
                                           final long now, final long tick, final CombinedData data, 
                                           final MovingData mData, final MovingConfig mCc, final IPlayerData pData) {

        final String toWorldName = to.getWorld().getName();
        Combined.feedYawRate(player, to.getYaw(), now, toWorldName, data, pData);
        // maybe even not count vehicles at all ?
        if (player.isInsideVehicle()) {
            // refine (!).
            final Location ref = player.getVehicle().getLocation(useLoc);
            aux.resetPositionsAndMediumProperties(player, ref, mData, mCc); // Consider using to and intercept cheat attempts in another way.
            useLoc.setWorld(null);
            mData.updateTrace(player, to, tick, mcAccess.getHandle()); // Can you become invincible by sending special moves?
        }
        else if (!from.getWorld().getName().equals(toWorldName)) {
            // A teleport event should follow.
            aux.resetPositionsAndMediumProperties(player, to, mData, mCc);
            mData.resetTrace(player, to, tick, mcAccess.getHandle(), mCc);
        }
        else {
            // Detect differing location (a teleport event would follow).
            final PlayerMoveData lastMove = mData.playerMoves.getFirstPastMove();
            if (!lastMove.toIsValid || !TrigUtil.isSamePos(to, lastMove.to.getX(), lastMove.to.getY(), lastMove.to.getZ())) {
                // Something odd happened, e.g. a set back.
                aux.resetPositionsAndMediumProperties(player, to, mData, mCc);
            }
            else {
                // Normal move, nothing to do.
            }
            mData.updateTrace(player, to, tick, mcAccess.getHandle());
            if (mData.hasTeleported()) onPlayerMoveMonitorNotCancelledHasTeleported(player, to, mData, pData, pData.isDebugActive(checkType));
        }
    }


    private void onPlayerMoveMonitorNotCancelledHasTeleported(final Player player, final Location to, 
                                                              final MovingData mData, final IPlayerData pData, 
                                                              final boolean debug) {

        if (mData.isTeleportedPosition(to)) {
            // Skip resetting, especially if legacy setTo is enabled.
            // Might skip this condition, if legacy setTo is not enabled.
            if (debug) debug(player, "Event not cancelled, with teleported (set back) set, assume legacy behavior.");
            return;
        }
        else if (pData.isPlayerSetBackScheduled()) {
            // Skip, because the scheduled teleport has been overridden.
            // Only do this, if cancel is set, because it is not an un-cancel otherwise.
            if (debug) debug(player, "Event not cancelled, despite a set back has been scheduled. Cancel set back.");
            mData.resetTeleported(); // (PlayerTickListener will notice it's not set.)
        }
        else {
            if (debug) debug(player, "Inconsistent state (move MONITOR): teleported has been set, but no set back is scheduled. Ignore set back.");
            mData.resetTeleported();
        }
    }


    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerPortalLowest(final PlayerPortalEvent event) {

        final Player player = event.getPlayer();
        final IPlayerData pData = fetchPlayerData(player, "portal lowest");
        if (pData == null) {
            return;
        }
        if (MovingUtil.hasScheduledPlayerSetBack(player)) {
            if (pData.isDebugActive(checkType)) debug(player, "[PORTAL] Prevent use, due to a scheduled set back.");
            event.setCancelled(true);
        }
    }


    /**
     * When a player uses a portal, all information related to the moving checks becomes invalid.
     * 
     * @param event
     *            the event
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerPortal(final PlayerPortalEvent event) {

        final Location to = event.getTo();
        final IPlayerData pData = fetchPlayerData(event.getPlayer(), "portal");
        if (pData == null) {
            return;
        }
        final MovingData data = pData.getGenericInstance(MovingData.class);
        if (pData.isDebugActive(checkType)) debug(event.getPlayer(), "[PORTAL] to=" + to);
        // This should be redundant, might remove anyway.
        // Rather add something like setLatencyImmunity(...ms / conditions).
        if (to != null) data.clearMostMovingCheckData();
        
    }


    /**
     * Clear fly data on death.
     * @param event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(final PlayerDeathEvent event) {

        final Player player = event.getEntity();
        final IPlayerData pData = fetchPlayerData(player, "death");
        if (pData == null) {
            return;
        }
        final MovingData data = pData.getGenericInstance(MovingData.class);
        //final MovingConfig cc = pData.getGenericInstance(MovingConfig.class);
        data.clearMostMovingCheckData();
        data.setSetBack(player.getLocation(useDeathLoc)); // Monitor this change (!).
        data.isUsingItem = false;
        // Log location.
        if (pData.isDebugActive(checkType)) debug(player, "Death: " + player.getLocation(useDeathLoc));
        useDeathLoc.setWorld(null);
    }


    /**
     * LOWEST: Checks, indicate cancel processing player move.
     * 
     * @param event
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onPlayerTeleportLowest(final PlayerTeleportEvent event) {

        final Player player = event.getPlayer();
        if (player == null) {
            event.setCancelled(true);
            return;
        }

        // Prevent further moving processing for nested events.
        processingEvents.remove(player.getName());

        if (!shouldProcessTeleport(event)) {
            return;
        }

        final IPlayerData pData = fetchPlayerData(player, "teleport lowest");
        if (pData == null || !isMovingCheckActive(pData, player)) {
            return;
        }

        final boolean debug = pData.isDebugActive(checkType);
        final MovingData data = pData.getGenericInstance(MovingData.class);
        final Location to = event.getTo();

        if (handleInvalidTarget(event, player, to, debug)) {
            return;
        }

        if (handleScheduledTeleport(data, event, player, to, debug)) {
            return;
        }

        if (evaluateTeleport(event, player, to, pData, data)) {
            event.setCancelled(true);
            if (debug) {
                debug(player, "TP " + event.getCause() + " (cancel): " + to);
            }
        }
    }


    /**
     * HIGHEST: Revert cancel on set back.
     * 
     * @param event
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onPlayerTeleport(final PlayerTeleportEvent event) {
        // Only check cancelled events.
        if (event.isCancelled()) checkUndoCancelledSetBack(event);
    }


    /**
     * Called for cancelled events only, before EventPriority.MONITOR.
     * 
     * @param event
     */
    private void checkUndoCancelledSetBack(final PlayerTeleportEvent event) {

        final Player player = event.getPlayer();
        final IPlayerData pData = fetchPlayerData(player, "undo cancelled set back");
        if (pData == null) {
            return;
        }
        final MovingData data = pData.getGenericInstance(MovingData.class);
        // Revert cancel on set back (only precise match).
        // Teleport by NCP.
        // What if not scheduled.
        if (data.hasTeleported()) undoCancelledSetBack(player, event, data, pData);
    }


    private final void undoCancelledSetBack(final Player player, final PlayerTeleportEvent event,
                                            final MovingData data, final IPlayerData pData) {

        // Prevent cheaters getting rid of flying data (morepackets, other).
        // even more strict enforcing ?
        event.setCancelled(false); // Does this make sense? Have it configurable rather?
        if (!data.isTeleported(event.getTo())) {
            final Location teleported = data.getTeleported();
            event.setTo(teleported);
            /*
             * Setting from ... not sure this is relevant. Idea was to avoid
             * subtleties with other plugins, but it probably can't be
             * estimated, if this means more or less 'subtleties' in the end
             * (amortized).
             */
            event.setFrom(teleported);
        }
        if (pData.isDebugActive(checkType)) {
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().warning(Streams.TRACE_FILE, player.getName() + " TP " + event.getCause()+ " (revert cancel on set back): " + event.getTo());
        }
    }


    /**
     * MONITOR: Adjust data to what happened.
     * 
     * @param event
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.MONITOR)
    public void onPlayerTeleportMonitor(final PlayerTeleportEvent event) {

        // Evaluate result and adjust data.
        final Player player = event.getPlayer();
        final IPlayerData pData = fetchPlayerData(player, "teleport monitor");
        if (pData == null || !pData.isCheckActive(CheckType.MOVING, player)) {
            return;
        }
        final MovingData data = pData.getGenericInstance(MovingData.class);
        // Invalidate first-move thing.
        // Might conflict with 'moved wrongly' on join.
        data.joinOrRespawn = false;
        
        // Special cases.
        final Location to = event.getTo();
        if (event.isCancelled()) {
            handleCancelledTeleport(player, event, to, data, pData);
            return;
        }
        else if (to == null) {
            // Weird event.
            handleNullTargetTeleport(player, event, to, data, pData);
            return;
        }
        data.clearWindChargeImpulse();
        final MovingConfig cc = pData.getGenericInstance(MovingConfig.class);
        // Detect our own player set backs.
        if (data.hasTeleported() && onPlayerTeleportMonitorHasTeleported(player, event, to, data, cc, pData)) {
            return;
        }

        boolean skipExtras = handleVehicleSetBack(player, event, data);

        // Normal teleport
        final double fallDistance = data.noFallFallDistance;
        //        final LiftOffEnvelope oldEnv = data.liftOffEnvelope; // Remember for workarounds.
        data.clearFlyData();
        data.clearPlayerMorePacketsData();
        data.setSetBack(to);
        data.sfHoverTicks = -1; // Important against concurrent modification exception.
        if (cc.loadChunksOnTeleport) MovingUtil.ensureChunksLoaded(player, to, "teleport", data, cc, pData);
        aux.resetPositionsAndMediumProperties(player, to, data, cc);
        // Reset stuff.
        Combined.resetYawRate(player, to.getYaw(), System.currentTimeMillis(), true, pData); // Not sure.
        data.resetTeleported();

        if (!skipExtras) {
            adjustFallDistance(player, event, fallDistance, data, cc);
        }

        recordCrossWorldTeleport(event, to, data);

        // Log.
        if (pData.isDebugActive(checkType)) {
            debugTeleportMessage(player, event, "(normal)", to);
        }
    }


    /**
     * 
     * @param player
     * @param event
     * @param to
     * @param data
     * @param cc
     * @return True, if processing the teleport event should be aborted, false
     *         otherwise.
     */
    private boolean onPlayerTeleportMonitorHasTeleported(final Player player, final PlayerTeleportEvent event, 
                                                         final Location to, final MovingData data, final MovingConfig cc, 
                                                         final IPlayerData pData) {

        if (data.isTeleportedPosition(to)) {
            // Set back.
            confirmSetBack(player, true, data, cc, pData, to);
            // Reset some more data.
            // Some more?
            data.reducePlayerMorePacketsData(1);
            // Log.
            if (pData.isDebugActive(checkType)) {
                debugTeleportMessage(player, event, "(set back)", to);
            }
            return true;
        }
        else {
            /*
             * In this case another plugin has prevented NCP cancelling that
             * teleport during before this EventPriority stage, or another
             * plugin has altered the target location (to).
             */
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().warning(
                    Streams.TRACE_FILE, CheckUtils.getLogMessagePrefix(player, CheckType.MOVING) 
                    + " TP " + event.getCause() + " (set back was overridden): " + to);
            return false;
        }
    }


    /**
     * A set back has been performed, or applying it got through to
     * EventPriority.MONITOR.
     * 
     * @param player
     * @param fakeNews
     *            True, iff it's not really been applied yet (, but should get
     *            applied, due to reaching EventPriority.MONITOR).
     * @param data
     * @param cc
     */
    private void confirmSetBack(final Player player, final boolean fakeNews, final MovingData data, 
                                final MovingConfig cc, final IPlayerData pData, final Location fallbackTeleported) {

        // Find the reason why it can be null even passed the precondition not null.
        final Location teleported = data.getTeleported();
        final PlayerMoveInfo moveInfo = aux.usePlayerMoveInfo();
        moveInfo.set(player, teleported != null ? teleported : fallbackTeleported, null, cc.yOnGround);
        if (cc.loadChunksOnTeleport) {
            MovingUtil.ensureChunksLoaded(player, teleported != null ? teleported : fallbackTeleported, 
                    "teleport", data, cc, pData);
        }
        data.onSetBack(moveInfo.from);
        aux.returnPlayerMoveInfo(moveInfo);
        // Reset stuff.
        final Float yaw = yawForReset(teleported, fallbackTeleported);
        if (yaw != null) {
            Combined.resetYawRate(player, yaw, System.currentTimeMillis(), true, pData); // Not sure.
        }
        data.resetTeleported();
    }

    /**
     * Determine the yaw to use when resetting yaw rate.
     *
     * @param teleported the teleported location, may be null
     * @param fallbackTeleported fallback location to use if teleported is null
     * @return the yaw angle or {@code null} if neither location is available
     */
    private static Float yawForReset(final Location teleported, final Location fallbackTeleported) {
        if (teleported != null) {
            return teleported.getYaw();
        }
        if (fallbackTeleported != null) {
            return fallbackTeleported.getYaw();
        }
        return null;
    }


    private void handleCancelledTeleport(final Player player, final PlayerTeleportEvent event,
                                         final Location to, final MovingData data, final IPlayerData pData) {

        if (data.isTeleported(to)) {
            // (Only precise match.)
            // Schedule a teleport to set back with PlayerData (+ failure count)?
            // Log once per player always?
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().warning(Streams.TRACE_FILE, CheckUtils.getLogMessagePrefix(player, CheckType.MOVING) 
                                                                       +  " TP " + event.getCause() + " (set back was prevented): " + to);
        }
        else {
            if (pData.isDebugActive(checkType)) {
                debugTeleportMessage(player, event, to);
            }
        }
        data.resetTeleported();
    }


    private void handleNullTargetTeleport(final Player player,
                                          final PlayerTeleportEvent event, final Location to,
                                          final MovingData data, final IPlayerData pData) {

        final boolean debug = pData.isDebugActive(checkType);
        if (debug) {
            debugTeleportMessage(player, event, "No target location (to) set.");
        }

        if (data.hasTeleported()) {
            if (dataManager.getPlayerData(player).isPlayerSetBackScheduled()) {
                // Assume set back event following later.
                event.setCancelled(true);
                if (debug) debugTeleportMessage(player, event, "Cancel, due to a scheduled set back.");
                
            }
            else {
                data.resetTeleported();
                if (debug) debugTeleportMessage(player, event, "Skip set back, not being scheduled.");
            }
        }
    }

    private boolean handleVehicleSetBack(final Player player, final PlayerTeleportEvent event,
                                         final MovingData data) {

        if (data.isVehicleSetBack) {
            if (event.getCause() != BridgeMisc.TELEPORT_CAUSE_CORRECTION_OF_POSITION) {
                NCPAPIProvider.getNoCheatPlusAPI().getLogManager().warning(Streams.STATUS,
                        CheckUtils.getLogMessagePrefix(player, CheckType.MOVING_VEHICLE)
                        + "Unexpected teleport cause on vehicle set back: " + event.getCause());
            }
            return true;
        }
        return false;
    }

    private void adjustFallDistance(final Player player, final PlayerTeleportEvent event,
                                     final double fallDistance, final MovingData data,
                                     final MovingConfig cc) {

        // Adjust fall distance, if set so.
        // How to account for plugins that reset the fall distance here?
        // Detect transition from valid flying that needs resetting the fall distance.
        if (event.getCause() == TeleportCause.UNKNOWN || event.getCause() == TeleportCause.COMMAND) {
            player.setFallDistance((float) fallDistance);
            data.noFallFallDistance = (float) fallDistance;
        }
        else if (fallDistance > 1.0 && fallDistance - player.getFallDistance() > 0.0) {
            if (!cc.noFallTpReset) {
                player.setFallDistance((float) fallDistance);
                data.noFallFallDistance = (float) fallDistance;
            }
            else if (fallDistance >= Magic.FALL_DAMAGE_DIST) {
                data.noFallSkipAirCheck = true;
            }
        }
        if (event.getCause() == TeleportCause.ENDER_PEARL) {
            data.noFallSkipAirCheck = true;
        }
    }

    private void recordCrossWorldTeleport(final PlayerTeleportEvent event, final Location to,
                                          final MovingData data) {

        final Location from = event.getFrom();
        if (from != null
            && event.getCause() == TeleportCause.END_PORTAL
            && !from.getWorld().getName().equals(to.getWorld().getName())) {
            data.crossWorldFrom = new SimplePositionWithLook(from.getX(), from.getY(), from.getZ(),
                    from.getYaw(), from.getPitch());
        }
        else {
            data.crossWorldFrom = null;
        }
    }

    /** Decide if the teleport event should be processed at all. */
    private boolean shouldProcessTeleport(final PlayerTeleportEvent event) {
        if (event == null || event.isCancelled()) {
            return false;
        }
        final TeleportCause cause = event.getCause();
        return cause == TeleportCause.COMMAND || cause == TeleportCause.ENDER_PEARL;
    }

    /** Check if moving checks are enabled for the player. */
    private boolean isMovingCheckActive(final IPlayerData pData, final Player player) {
        return pData != null && pData.isCheckActive(CheckType.MOVING, player);
    }

    /** Retrieve player data or log a debug message if absent. */
    private IPlayerData fetchPlayerData(final Player player, final String context) {
        final IPlayerData pData = dataManager.getPlayerData(player);
        if (pData == null) {
            debug(player, "Player data missing in " + context);
        }
        return pData;
    }

    /** Handle missing or invalid target locations. */
    private boolean handleInvalidTarget(final PlayerTeleportEvent event, final Player player,
                                        final Location to, final boolean debug) {
        if (to == null || to.getWorld() == null) {
            if (!event.isCancelled()) {
                if (debug) {
                    debugTeleportMessage(player, event, "Cancel event, that has no target location (to) set.");
                }
                event.setCancelled(true);
            }
            return true;
        }
        return false;
    }

    /**
     * Handle teleport events when a set back is pending.
     *
     * @return true if processing should stop.
     */
    private boolean handleScheduledTeleport(final MovingData data, final PlayerTeleportEvent event,
                                            final Player player, final Location to, final boolean debug) {
        if (!data.hasTeleported()) {
            return false;
        }
        if (data.isTeleportedPosition(to)) {
            return true;
        }
        if (debug) {
            debugTeleportMessage(player, event, "Prevent teleport, due to a scheduled set back: ", to);
        }
        event.setCancelled(true);
        return true;
    }

    /**
     * Apply teleport related checks. Returns true to cancel the event.
     */
    private boolean evaluateTeleport(final PlayerTeleportEvent event, final Player player, final Location to,
                                     final IPlayerData pData, final MovingData data) {
        final TeleportCause cause = event.getCause();
        final MovingConfig cc = pData.getGenericInstance(MovingConfig.class);

        if (cause == TeleportCause.ENDER_PEARL) {
            return isBlockedEnderPearl(to, pData);
        }

        if (cause == TeleportCause.COMMAND) {
            adjustUntrackedLocation(event, player, to, cc, pData, data);
        }
        return false;
    }

    /** Detect ender pearls used into solid blocks. */
    private boolean isBlockedEnderPearl(final Location to, final IPlayerData pData) {
        if (to == null || to.getWorld() == null) {
            return false;
        }
        return pData.getGenericInstance(CombinedConfig.class).enderPearlCheck
                && !BlockProperties.isPassable(to)
                && blockChangeTracker.getBlockChangeEntry(null, TickTask.getTick(), to.getWorld().getUID(),
                        to.getBlockX(), to.getBlockY(), to.getBlockZ(), null) == null;
    }

    /** Adjust untracked teleport destinations if necessary. */
    private void adjustUntrackedLocation(final PlayerTeleportEvent event, final Player player, final Location to,
                                         final MovingConfig cc, final IPlayerData pData, final MovingData data) {
        if (!cc.passableUntrackedTeleportCheck) {
            return;
        }
        if (cc.loadChunksOnTeleport) {
            MovingUtil.ensureChunksLoaded(player, to, "teleport", data, cc, pData);
        }
        if (MovingUtil.shouldCheckUntrackedLocation(player, to, pData)) {
            final Location newTo = MovingUtil.checkUntrackedLocation(to);
            if (newTo != null) {
                event.setTo(newTo);
                NCPAPIProvider.getNoCheatPlusAPI().getLogManager().warning(Streams.TRACE_FILE,
                        player.getName() + " correct untracked teleport destination (" + to + " corrected to " + newTo + ").");
            }
        }
    }


    /**
     * 
     * @param player
     * @param event
     * @param message
     * @param extra
     *            Added in the end, with a leading space each.
     */
    private void debugTeleportMessage(final Player player, final PlayerTeleportEvent event, 
                                      final Object... extra) {

        final StringBuilder builder = new StringBuilder(128);
        builder.append("TP ");
        builder.append(event.getCause());
        if (event.isCancelled()) {
            builder.append(" (cancelled)");
        }
        if (extra != null && extra.length > 0) {
            for (final Object obj : extra) {
                if (obj != null) {
                    builder.append(' ');
                    if (obj instanceof String) {
                        builder.append((String) obj);
                    }
                    else {
                        builder.append(obj.toString());
                    }
                }
            }
        }
        debug(player, builder.toString());
    }


    /**
     * Player got a velocity packet. The server can't keep track of actual velocity values (by design), so we have to
     * try and do that ourselves. Very rough estimates.
     * 
     * @param event
     *            the event
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerVelocity(final PlayerVelocityEvent event) {

        final Player player = event.getPlayer();
        final IPlayerData pData = fetchPlayerData(player, "velocity");
        if (pData == null || !pData.isCheckActive(CheckType.MOVING, player)) {
            return;
        }
        final MovingData data = pData.getGenericInstance(MovingData.class);
        // Ignore players who are in vehicles.
        if (player.isInsideVehicle()) {
            data.removeAllVelocity();
            return;
        }
        // Process velocity.
        final Vector velocity = event.getVelocity();
        final MovingConfig cc = pData.getGenericInstance(MovingConfig.class);
        data.addVelocity(player, cc, velocity.getX(), velocity.getY(), velocity.getZ());
    }


    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntityDamage(final EntityDamageEvent event) {

        if (event.getCause() != DamageCause.FALL) {
            return;
        }
        final Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        checkFallDamageEvent((Player) entity, event);
    }


    private void checkFallDamageEvent(final Player player, final EntityDamageEvent event) {
        final IPlayerData pData = fetchPlayerData(player, "entity damage");
        if (pData == null) {
            return;
        }
        final MovingData data = pData.getGenericInstance(MovingData.class);
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final MovingConfig cc = pData.getGenericInstance(MovingConfig.class);
        final PlayerMoveInfo moveInfo = aux.usePlayerMoveInfo();
        final double yOnGround = Math.max(cc.noFallyOnGround, cc.yOnGround);
        final Location loc = player.getLocation(useFallLoc);
        moveInfo.set(player, loc, null, yOnGround);
        final PlayerLocation pLoc = moveInfo.from;
        pLoc.collectBlockFlags(yOnGround);

        if (shouldSkipFallDamageCheck(player, event, pData, data, pLoc, cc, moveInfo)) {
            useFallLoc.setWorld(null);
            aux.returnPlayerMoveInfo(moveInfo);
            return;
        }

        final boolean debug = pData.isDebugActive(CheckType.MOVING_NOFALL);
        float fallDistance = player.getFallDistance();
        final float yDiff = (float) (data.noFallMaxY - loc.getY());
        final double damage = BridgeHealth.getRawDamage(event); // Raw damage.
        if (debug) debug(player, "Damage(FALL/PRE): " + damage + " / mc=" + player.getFallDistance() + " nf=" + data.noFallFallDistance + " yDiff=" + yDiff);

        NoFallBypassResult result = handleNoFallBypass(player, event, data, thisMove, moveInfo, damage, yDiff, fallDistance, cc, pLoc, debug);
        if (result.earlyExit) {
            useFallLoc.setWorld(null);
            aux.returnPlayerMoveInfo(moveInfo);
            return;
        }
        fallDistance = result.fallDistance;
        boolean allowReset = result.allowReset;
        aux.returnPlayerMoveInfo(moveInfo);

        adjustFallDamage(player, event, data, loc, fallDistance, yDiff, allowReset, damage, debug);
        resetNoFallData(player, allowReset, data, cc, debug);

        useFallLoc.setWorld(null);
    }

    /** Decide if fall damage checks should be skipped for the given event. */
    private boolean shouldSkipFallDamageCheck(final Player player, final EntityDamageEvent event,
                                              final IPlayerData pData, final MovingData data,
                                              final PlayerLocation pLoc, final MovingConfig cc,
                                              final PlayerMoveInfo moveInfo) {

        if (!pData.isCheckActive(CheckType.MOVING, player)) {
            return true;
        }
        if (player.isInsideVehicle()) {
            data.clearNoFallData();
            return true;
        }
        if (event.isCancelled() ||
                !MovingUtil.shouldCheckSurvivalFly(player, pLoc, moveInfo.to, data, cc, pData) ||
                !noFall.isEnabled(player, pData)) {
            data.clearNoFallData();
            return true;
        }
        return false;
    }

    /** Container for results of {@link #handleNoFallBypass}. */
    private static class NoFallBypassResult {
        final float fallDistance;
        final boolean allowReset;
        final boolean earlyExit;
        NoFallBypassResult(float fallDistance, boolean allowReset, boolean earlyExit) {
            this.fallDistance = fallDistance;
            this.allowReset = allowReset;
            this.earlyExit = earlyExit;
        }
    }

    /**
     * Handle NoFall bypass logic, possibly adjusting damage and returning early.
     */
    private NoFallBypassResult handleNoFallBypass(final Player player, final EntityDamageEvent event,
                                                  final MovingData data, final PlayerMoveData thisMove,
                                                  final PlayerMoveInfo moveInfo, final double damage,
                                                  final float yDiff, final float fallDistance,
                                                  final MovingConfig cc, final PlayerLocation pLoc,
                                                  final boolean debug) {

        if (player == null || event == null || data == null || thisMove == null
                || moveInfo == null || cc == null || pLoc == null) {
            return new NoFallBypassResult(fallDistance, true, false);
        }

        float fDist = fallDistance;
        boolean allowReset = true;
        boolean earlyExit = false;

        if (!data.noFallSkipAirCheck) {
            final float dataDist = Math.max(yDiff, data.noFallFallDistance);
            final double dataDamage = NoFall.getDamage(dataDist);
            if (damage > dataDamage + 0.5 || dataDamage <= 0.0) {
                final PlayerMoveData firstPastMove = data.playerMoves.getFirstPastMove();
                if (!isFakeFallException(player, pLoc, moveInfo, thisMove, firstPastMove, debug)
                        && noFallVL(player, "fakefall", data, cc)) {
                    player.setFallDistance(dataDist);
                    if (dataDamage <= 0.0) {
                        event.setCancelled(true);
                        earlyExit = true;
                    } else {
                        if (debug) {
                            debug(player, "NoFall/Damage: override player fall distance and damage (" + fDist + " -> " + dataDist + ").");
                        }
                        fDist = dataDist;
                        BridgeHealth.setRawDamage(event, dataDamage);
                    }
                }
            }
            data.noFallFallDistance += 1.0;
            if (requiresFakeGroundCheck(pLoc)) {
                if (noFallVL(player, "fakeground", data, cc) && data.hasSetBack()) {
                    allowReset = false;
                }
            } else {
                data.vDistAcc.clear();
            }
        }
        return new NoFallBypassResult(fDist, allowReset, earlyExit);
    }

    private boolean isFakeFallException(final Player player, final PlayerLocation pLoc,
                                        final PlayerMoveInfo moveInfo, final PlayerMoveData thisMove,
                                        final PlayerMoveData firstPastMove, final boolean debug) {
        if (pLoc.isOnGround() && pLoc.isInLava() && firstPastMove.toIsValid && firstPastMove.yDistance < 0.0) {
            if (debug) {
                debug(player, "NoFall/Damage: allow fall damage in lava (hotfix).");
            }
            return true;
        }
        if (moveInfo.from.isOnClimbable()
                && ((firstPastMove.modelFlying != null && firstPastMove.modelFlying.getVerticalAscendGliding())
                        || firstPastMove.elytrafly
                        || (thisMove.modelFlying != null && thisMove.modelFlying.getVerticalAscendGliding())
                        || thisMove.elytrafly)) {
            if (debug) {
                debug(player, "Ignore fakefall on climbable on elytra move");
            }
            return true;
        }
        return false;
    }

    private boolean requiresFakeGroundCheck(final PlayerLocation pLoc) {
        return !pLoc.isOnGround(1.0, 0.3, 0.1) && !pLoc.isResetCond()
                && !pLoc.isAboveLadder() && !pLoc.isAboveStairs();
    }

    /** Adjust fall damage based on calculated values. */
    private void adjustFallDamage(final Player player, final EntityDamageEvent event,
                                  final MovingData data, final Location loc,
                                  final float fallDistance, final float yDiff,
                                  final boolean allowReset, final double damage,
                                  final boolean debug) {

        final double maxD = data.jumpAmplifier > 0.0
                ? NoFall.getDamage((float) NoFall.getApplicableFallHeight(player, loc.getY(), data))
                : NoFall.getDamage(Math.max(yDiff, Math.max(data.noFallFallDistance, fallDistance)))
                        + (allowReset ? 0.0 : Magic.FALL_DAMAGE_DIST);
        if (maxD > damage) {
            double damageAfter = NoFall.calcDamagewithfeatherfalling(player,
                    NoFall.calcReducedDamageByBlock(player, data, maxD),
                    mcAccess.getHandle().dealFallDamageFiresAnEvent().decide());
            BridgeHealth.setRawDamage(event, damageAfter);
            if (debug) {
                final double shown = Math.abs(damageAfter - maxD) < EPSILON ? maxD : damageAfter;
                debug(player, "Adjust fall damage to: " + shown);
            }
        }
    }

    /** Reset NoFall related data after damage has been processed. */
    private void resetNoFallData(final Player player, final boolean allowReset,
                                 final MovingData data, final MovingConfig cc,
                                 final boolean debug) {
        if (allowReset) {
            data.clearNoFallData();
            if (debug) debug(player, "Reset NoFall data on fall damage.");
        } else {
            if (cc.noFallViolationReset) {
                data.clearNoFallData();
            }
            if (cc.sfHoverCheck && data.sfHoverTicks < 0) {
                data.sfHoverTicks = 0;
                hoverTicks.add(player.getName());
            }
        }
    }


    private final boolean noFallVL(final Player player, final String tag, final MovingData data, final MovingConfig cc) {

        data.noFallVL += 1.0;
        final ViolationData vd = new ViolationData(noFall, player, data.noFallVL, 1.0, cc.noFallActions);
        if (tag != null) vd.setParameter(ParameterName.TAGS, tag);
        return noFall.executeActions(vd).willCancel();
    }


    /**
     * When a player respawns, all information related to the moving checks
     * becomes invalid.
     * 
     * @param event
     *            the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(final PlayerRespawnEvent event) {

        final Player player = event.getPlayer();
        final IPlayerData pData = fetchPlayerData(player, "respawn");
        if (pData == null || !pData.isCheckActive(CheckType.MOVING, player)) return;
        final MovingData data = pData.getGenericInstance(MovingData.class);
        // Prevent/cancel scheduled teleport (use PlayerData/task for teleport, or a sequence count).
        data.clearMostMovingCheckData();
        data.resetSetBack(); // To force dataOnJoin to set it to loc.
        // Handle respawn like join.
        dataOnJoin(player, event.getRespawnLocation(), true, data, pData.getGenericInstance(MovingConfig.class), pData.isDebugActive(checkType));
        // Patch up issues.
        if (Bridge1_9.hasGetItemInOffHand() && player.isBlocking()) {
            // Attempt to fix server-side-only blocking after respawn.
            redoShield(player);
        }
    }


    /**
     * Attempt to fix server-side-only blocking after respawn.
     * @param player
     */
    private void redoShield(final Player player) {

        // Does not work: dataManager.getPlayerData(player).requestUpdateInventory();
        if (mcAccess.getHandle().resetActiveItem(player)) return;
        final PlayerInventory inv = player.getInventory();
        ItemStack stack = inv.getItemInOffHand();
        if (stack != null && stack.getType() == Material.SHIELD) {
            // Shield in off-hand.
            inv.setItemInOffHand(stack);
            return;
        }
        stack = inv.getItemInMainHand();
        if (stack != null && stack.getType() == Material.SHIELD) {
            // Shield in off-hand.
            inv.setItemInMainHand(stack);
            return;
        }
    }


    @Override
    public void playerJoins(final Player player) {

        final IPlayerData pData = dataManager.getPlayerData(player);
        if (!pData.isCheckActive(CheckType.MOVING, player)) return;
        dataOnJoin(player, player.getLocation(useJoinLoc), false, pData.getGenericInstance(MovingData.class), 
                  pData.getGenericInstance(MovingConfig.class), pData.isDebugActive(checkType));
        // Cleanup.
        useJoinLoc.setWorld(null);
    }


    /**
     * Alter data for players joining (join, respawn).<br>
     * Do before, if necessary:<br>
     * <li>data.clearFlyData()</li>
     * <li>data.setSetBack(...)</li>
     * @param player
     * @param loc Can be useLoc (!).
     * @param isRespawn
     * @param data
     * @param cc
     * @param debug
     */
    private void dataOnJoin(Player player, Location loc, boolean isRespawn, MovingData data, 
                            MovingConfig cc, final boolean debug) {

        final int tick = TickTask.getTick();
        final String tag = isRespawn ? "Respawn" : "Join";
        // Check loaded chunks.
        if (cc.loadChunksOnJoin) {
            // (Don't use past-move heuristic for skipping here.)
            final int loaded = MapUtil.ensureChunksLoaded(loc.getWorld(), loc.getX(), loc.getZ(), Magic.CHUNK_LOAD_MARGIN_MIN);
            if (loaded > 0 && debug) {
                StaticLog.logInfo("Player " + tag + ": Loaded " + loaded + " chunk" + (loaded == 1 ? "" : "s") + " for the world " + loc.getWorld().getName() +  " for player: " + player.getName());
            }
        }

        // Correct set back on join.
        if (!data.hasSetBack() || !data.hasSetBackWorldChanged(loc)) {
            data.clearFlyData();
            data.setSetBack(loc);
            // (resetPositions is called below)
            data.joinOrRespawn = true; // Review if to always set (!).
        }
        else {
            // Check consistency/distance.
            //final Location setBack = data.getSetBack(loc);
            //final double d = loc.distanceSquared(setBack);
            // If to reset positions: relate to previous ones and set back.
            // (resetPositions is called below)
        }
        // (Note: resetPositions resets lastFlyCheck and other.)
        data.clearVehicleData(); // Uncertain here, what to check.
        data.clearAllMorePacketsData();
        data.removeAllVelocity();
        data.resetTrace(player, loc, tick, mcAccess.getHandle(), cc); // Might reset to loc instead of set back ?
        // More resetting.
        data.vDistAcc.clear();
        aux.resetPositionsAndMediumProperties(player, loc, data, cc);
        data.sfHorizontalBuffer = cc.hBufMax;

        // Enforcing the location.
        if (cc.enforceLocation) {
            playersEnforce.add(player.getName());
        }

        // Hover.
        initHover(player, data, cc, data.playerMoves.getFirstPastMove().from.onGroundOrResetCond);

        // Check for vehicles.
        // Order / exclusion of items.
        if (player.isInsideVehicle()) {
            vehicleChecks.onPlayerVehicleEnter(player, player.getVehicle());
        }

        if (debug) {
            debug(player, tag + ": " + loc);
        }
    }


    /**
     * Initialize the hover check for a player (login, respawn). 
     * @param player
     * @param data
     * @param cc
     * @param isOnGroundOrResetCond 
     */
    private void initHover(final Player player, final MovingData data, final MovingConfig cc, final boolean isOnGroundOrResetCond) {
        
        // Reset hover ticks until a better method is used.
        if (!isOnGroundOrResetCond && cc.sfHoverCheck) {
            // Start as if hovering already.
            // Could check shouldCheckSurvivalFly(player, data, cc), but this should be more sharp (gets checked on violation).
            data.sfHoverTicks = 0;
            data.sfHoverLoginTicks = cc.sfHoverLoginTicks;
            hoverTicks.add(player.getName());
        }
        else {
            data.sfHoverLoginTicks = 0;
            data.sfHoverTicks = -1;
        }
    }


    @Override
    public void playerLeaves(final Player player) {

        final IPlayerData pData = dataManager.getPlayerData(player);
        if (!pData.isCheckActive(CheckType.MOVING, player)) return;
        final MovingData data = pData.getGenericInstance(MovingData.class);
        final Location loc = player.getLocation(useLeaveLoc);
        // Debug logout.
        if (pData.isDebugActive(checkType)) StaticLog.logInfo("Player " + player.getName() + " leaves at location: " + loc.toString());

        if (player.isSleeping() || player.isDead() || BlockProperties.isPassable(loc)) {
            useLeaveLoc.setWorld(null);
            survivalFly.setReallySneaking(player, false);
            noFall.onLeave(player, data, pData);
            data.onPlayerLeave();
            resetVehicleTasks(data);
            return;
        }

        checkMissedMovesOnLeave(player, pData, data, loc);
        useLeaveLoc.setWorld(null);
        // Adjust data.
        survivalFly.setReallySneaking(player, false);
        noFall.onLeave(player, data, pData);
        // Add a method for ordinary presence-change resetting (use in join + leave).
        data.onPlayerLeave();
        resetVehicleTasks(data);
    }

    /**
     * Check for missed moves and correct the player position if necessary.
     */
    private void checkMissedMovesOnLeave(final Player player, final IPlayerData pData,
                                         final MovingData data, final Location loc) {

        if (player == null || pData == null || data == null || loc == null) return;

        // Check for missed moves. Skip if passable or on modern versions.
        if (BlockProperties.isPassable(loc) || Bridge1_13.hasIsSwimming()) return;

        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        if (!lastMove.valid) return;
        final PlayerMoveData lastMove2 = data.playerMoves.getNumberOfPastMoves() > 1
                ? data.playerMoves.getSecondPastMove() : null;

        Location refLoc = determineReferenceLocation(loc, lastMove, lastMove2);
        refLoc = adjustReferenceLocation(loc, refLoc);
        final double distSq = refLoc.distanceSquared(loc);
        if (distSq <= 0.0) return;

        if (shouldSetBack(player, pData, loc, refLoc)) {
            setBackPlayer(player, pData, data, refLoc, distSq);
        }
    }

    /**
     * Determine a reference location based on the last valid move.
     */
    private Location determineReferenceLocation(final Location loc, final PlayerMoveData lastMove,
                                                final PlayerMoveData lastMove2) {
        final Location refLoc = lastMove.toIsValid
                ? new Location(loc.getWorld(), lastMove.to.getX(), lastMove.to.getY(), lastMove.to.getZ())
                : new Location(loc.getWorld(), lastMove.from.getX(), lastMove.from.getY(), lastMove.from.getZ());

        if (TrigUtil.isSamePos(loc, refLoc) && !lastMove.toIsValid && lastMove2 != null) {
            return lastMove2.toIsValid
                    ? new Location(loc.getWorld(), lastMove2.to.getX(), lastMove2.to.getY(), lastMove2.to.getZ())
                    : new Location(loc.getWorld(), lastMove2.from.getX(), lastMove2.from.getY(), lastMove2.from.getZ());
        }
        return refLoc;
    }

    /**
     * Adjust the reference location if it points into a block or too far away.
     */
    private Location adjustReferenceLocation(final Location loc, Location refLoc) {
        if (!BlockProperties.isPassable(refLoc) || refLoc.distanceSquared(loc) > 1.25) {
            final double y = Math.ceil(loc.getY());
            refLoc = loc.clone();
            refLoc.setY(y);
            if (!BlockProperties.isPassable(refLoc)) {
                refLoc = loc;
            }
        }
        return refLoc;
    }

    /**
     * Decide if a set back should be executed for the given locations.
     */
    private boolean shouldSetBack(final Player player, final IPlayerData pData,
                                  final Location loc, final Location refLoc) {
        return (TrigUtil.manhattan(loc, refLoc) > 0 || BlockProperties.isPassable(refLoc))
                && passable.isEnabled(player, pData);
    }

    /**
     * Attempt to set the player back to a reference location and log actions.
     */
    private void setBackPlayer(final Player player, final IPlayerData pData,
                               final MovingData data, final Location refLoc, final double distSq) {
        StaticLog.logWarning("Potential exploit: Player" + player.getName()
                + " leaves, having moved into a block (not tracked by moving checks): "
                + player.getWorld().getName() + " / " + DebugUtil.formatMove(refLoc, player.getLocation()));
        if (distSq > 1.25) {
            StaticLog.logWarning("SKIP set back for " + player.getName()
                    + ", because distance is too high (risk of false positives): " + distSq);
        } else {
            StaticLog.logInfo("Set back player " + player.getName() + ": "
                    + LocUtil.simpleFormat(refLoc));
            data.prepareSetBack(refLoc);
            if (!player.teleport(refLoc, BridgeMisc.TELEPORT_CAUSE_CORRECTION_OF_POSITION)) {
                StaticLog.logWarning("FAILED to set back player " + player.getName());
            }
        }
    }

    /** Reset scheduled vehicle tasks for the player. */
    private void resetVehicleTasks(final MovingData data) {
        if (Folia.isTaskScheduled(data.vehicleSetBackTaskId)) {
            // Reset the id, assume the task will still teleport the vehicle.
            // Should rather force teleport (needs storing the task + data).
            data.vehicleSetBackTaskId = null;
        }
        data.vehicleSetPassengerTaskId = null;
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerToggleSneak(final PlayerToggleSneakEvent event) {
        survivalFly.setReallySneaking(event.getPlayer(), event.isSneaking());
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerToggleSprint(final PlayerToggleSprintEvent event) {
        if (!event.isSprinting()) {
            final IPlayerData pData = fetchPlayerData(event.getPlayer(), "toggle sprint");
            if (pData != null) {
                pData.getGenericInstance(MovingData.class).timeSprinting = 0;
            }
        }
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerToggleFlight(final PlayerToggleFlightEvent event) {

        // (ignoreCancelled = false: we track the bit of vertical extra momentum/thing).
        final Player player = event.getPlayer();
        if (player.isFlying() || event.isFlying() && !event.isCancelled())  return;
        final IPlayerData pData = fetchPlayerData(player, "toggle flight");
        if (pData == null || !pData.isCheckActive(CheckType.MOVING, player)) return;
        final MovingData data = pData.getGenericInstance(MovingData.class);
        final MovingConfig cc = pData.getGenericInstance(MovingConfig.class);
        final PlayerMoveInfo moveInfo = aux.usePlayerMoveInfo();
        final Location loc = player.getLocation(useToggleFlightLoc);
        moveInfo.set(player, loc, null, cc.yOnGround);
        // data.isVelocityJumpPhase() might be too harsh, but prevents too easy abuse.
        if (!MovingUtil.shouldCheckSurvivalFly(player, moveInfo.from, moveInfo.to, data, cc, pData) 
            || data.isVelocityJumpPhase() || BlockProperties.isOnGroundOrResetCond(player, loc, cc.yOnGround)) {
            useToggleFlightLoc.setWorld(null);
            aux.returnPlayerMoveInfo(moveInfo);
            return;
        }
        aux.returnPlayerMoveInfo(moveInfo);
        useToggleFlightLoc.setWorld(null);
        // Confine to minimum activation ticks.
        data.addVelocity(player, cc, 0.0, 0.3, 0.0);
    }


    @Override
    public void onTick(final int tick, final long timeLast) {

        // Change to per world checking (as long as configs are per world).
        // Legacy: enforcing location consistency.
        if (!playersEnforce.isEmpty()) checkOnTickPlayersEnforce();
        // Hover check (SurvivalFly).
        if (tick % hoverTicksStep == 0 && !hoverTicks.isEmpty()) {
            // Only check every so and so ticks.
            checkOnTickHover();
        }
        // Cleanup.
        useTickLoc.setWorld(null);
    }


    /**
     * Check for hovering.<br>
     * NOTE: Makes use of useLoc, without resetting it.
     */
    private void checkOnTickHover() {

        final List<String> rem = new ArrayList<String>(hoverTicks.size()); // Pessimistic.
        final PlayerMoveInfo info = aux.usePlayerMoveInfo();
        for (final String playerName : hoverTicks) {
            // put players into the set (+- one tick would not matter ?)
            // might add an online flag to data !
            final Player player = dataManager.getPlayerExact(playerName);
            if (player == null || !player.isOnline()) {
                rem.add(playerName);
                continue;
            }
            final IPlayerData pData = dataManager.getPlayerData(player);
            final MovingData data = pData.getGenericInstance(MovingData.class);
            if (player.isDead() || player.isSleeping() || player.isInsideVehicle()) {
                data.sfHoverTicks = -1;
                // (Removed below.)
            }
            if (data.sfHoverTicks < 0) {
                data.sfHoverLoginTicks = 0;
                rem.add(playerName);
                continue;
            }
            else if (data.sfHoverLoginTicks > 0) {
                // Additional "grace period".
                data.sfHoverLoginTicks --;
                continue;
            }
            final MovingConfig cc = pData.getGenericInstance(MovingConfig.class);
            // Check if enabled at all.
            if (!cc.sfHoverCheck) {
                rem.add(playerName);
                data.sfHoverTicks = -1;
                continue;
            }
            // Increase ticks here.
            data.sfHoverTicks += hoverTicksStep;
            if (data.sfHoverTicks < cc.sfHoverTicks) {
                // Don't do the heavier checking here, let moving checks reset these.
                continue;
            }
            if (checkHover(player, data, cc, pData, info)) {
                rem.add(playerName);
            }
        }
        hoverTicks.removeAll(rem);
        aux.returnPlayerMoveInfo(info);
    }


    /**
     * Legacy check: Enforce location of players, in case of inconsistencies.
     * First move exploit / possibly vehicle leave.<br>
     * NOTE: Makes use of useLoc, without resetting it.
     */
    private void checkOnTickPlayersEnforce() {

        final List<String> rem = new ArrayList<String>(playersEnforce.size()); // Pessimistic.
        for (final String playerName : playersEnforce) {
            final Player player = dataManager.getPlayerExact(playerName);
            if (player == null || !player.isOnline()) {
                rem.add(playerName);
                continue;
            } 
            else if (player.isDead() || player.isSleeping() || player.isInsideVehicle()) {
                // Don't remove but also don't check [subject to change].
                continue;
            }
            final MovingData data = dataManager.getGenericInstance(player, MovingData.class);
            final Location newTo = enforceLocation(player, player.getLocation(useTickLoc), data);
            if (newTo != null) {
                data.prepareSetBack(newTo);
                player.teleport(newTo, BridgeMisc.TELEPORT_CAUSE_CORRECTION_OF_POSITION);
            }
        }
        if (!rem.isEmpty()) playersEnforce.removeAll(rem);
    }


    private Location enforceLocation(final Player player, final Location loc, final MovingData data) {

        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        if (lastMove.toIsValid && TrigUtil.distanceSquared(lastMove.to.getX(), lastMove.to.getY(), lastMove.to.getZ(), loc.getX(), loc.getY(), loc.getZ()) > 1.0 / 256.0) {
            // Teleport back. 
            if (data.hasSetBack()) {
                // Might have to re-check all context with playerJoins and keeping old set backs...
                // Could use a flexible set back policy (switch to in-air on login). 
                return data.getSetBack(loc); // (OK? ~ legacy)
            }
            else {
                return new Location(player.getWorld(), lastMove.to.getX(), lastMove.to.getY(), lastMove.to.getZ(), loc.getYaw(), loc.getPitch());
            }
        }
        else return null;
    }


    /**
     * The heavier checking including on.ground etc., check if enabled/valid to check before this. 
     * @param player
     * @param data
     * @param cc
     * @param info
     * @return
     */
    private boolean checkHover(final Player player, final MovingData data, final MovingConfig cc, final IPlayerData pData,
                               final PlayerMoveInfo info) {

        // Check if player is on ground.
        final Location loc = player.getLocation(useTickLoc); // useLoc.setWorld(null) is done in onTick.
        info.set(player, loc, null, cc.yOnGround);
        // (Could use useLoc of MoveInfo here. Note orderm though.)
        final boolean res;
        // Collect flags, more margin ?
        final int loaded = info.from.ensureChunksLoaded();
        if (loaded > 0 && pData.isDebugActive(checkType)) {
            // DEBUG
            StaticLog.logInfo("Hover check: Needed to load " + loaded + " chunk" + (loaded == 1 ? "" : "s") + " for the world " + loc.getWorld().getName() +  " around " + loc.getBlockX() + "," + loc.getBlockZ() + " in order to check player: " + player.getName());
        }
        if (info.from.isOnGroundOrResetCond() || info.from.isAboveLadder() || info.from.isAboveStairs()) {
            res = true;
            data.sfHoverTicks = 0;
        }
        else {
            if (data.sfHoverTicks > cc.sfHoverTicks) {
                // Re-Check if survivalfly can apply at all.
                final PlayerMoveInfo moveInfo = aux.usePlayerMoveInfo();
                moveInfo.set(player, loc, null, cc.yOnGround);
                if (MovingUtil.shouldCheckSurvivalFly(player, moveInfo.from, moveInfo.to, data, cc, pData)) {
                    handleHoverViolation(player, moveInfo.from, cc, data, pData);
                    // Assume the player might still be hovering.
                    res = false;
                    data.sfHoverTicks = 0;
                }
                else {
                    // Reset hover ticks and check next period.
                    res = false;
                    data.sfHoverTicks = 0;
                }
                aux.returnPlayerMoveInfo(moveInfo);
            }
            else res = false;
        }
        info.cleanup();
        return res;
    }


    private void handleHoverViolation(final Player player, final PlayerLocation loc, 
                                      final MovingConfig cc, final MovingData data, final IPlayerData pData) {

        // Check nofall damage (!).
        if (cc.sfHoverTakeFallDamage && noFall.isEnabled(player, pData)) {
            // Consider adding 3/3.5 to fall distance if fall distance > 0?
            noFall.checkDamage(player, loc.getY(), data, pData);
        }
        // Delegate violation handling.
        survivalFly.handleHoverViolation(player, loc, cc, data);
    }


    @Override
    public CheckType getCheckType() {
        // this is for the hover check only...
        // ugly.
        return CheckType.MOVING_SURVIVALFLY;
    }


    @Override
    public IData removeData(String playerName) {
        // Let TickListener handle automatically
        //hoverTicks.remove(playerName);
        //playersEnforce.remove(playerName);
        return null;
    }


    @Override
    public void removeAllData() {
        hoverTicks.clear();
        playersEnforce.clear();
        aux.clear();
    }


    @Override
    public void onReload() {
        aux.clear();
        hoverTicksStep = Math.max(1, ConfigManager.getConfigFile().getInt(ConfPaths.MOVING_SURVIVALFLY_HOVER_STEP));
    }


    /**
     * Output information specific to player-move events.
     * @param player
     * @param from
     * @param to
     * @param mcAccess
     */
    private void outputMoveDebug(final Player player, final PlayerLocation from, final PlayerLocation to,
            final double maxYOnGround, final MCAccess mcAccess) {
        final StringBuilder builder = new StringBuilder(250);
        final Location loc = player.getLocation();
        builder.append(CheckUtils.getLogMessagePrefix(player, checkType));
        builder.append("MOVE in world " + from.getWorld().getName() + ":\n");
        DebugUtil.addMove(from, to, loc, builder);
        if (BuildParameters.debugLevel > 0) {
            appendPlayerAttributeDetails(player, builder);
        }

        appendPotionEffectDetails(player, mcAccess, builder);
        appendFlightInfo(player, builder);
        // Print basic info first in order
        NCPAPIProvider.getNoCheatPlusAPI().getLogManager().debug(Streams.TRACE_FILE, builder.toString());
        // Extended info.
        if (BuildParameters.debugLevel > 0) {
            appendBlockFlagDetails(from, to, maxYOnGround, builder);
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().debug(Streams.TRACE_FILE, builder.toString());
        }
    }

    /**
     * Append player attribute information such as walk speed or sprinting state.
     *
     * @param player the player to read data from
     * @param builder the builder to append to
     */
    private void appendPlayerAttributeDetails(final Player player, final StringBuilder builder) {
        try {
            // Check backwards compatibility (1.4.2). Remove try-catch
            builder.append("\n(walkspeed=" + player.getWalkSpeed() + " flyspeed=" + player.getFlySpeed() + ")");
        } catch (Throwable t) {
            // Ignore missing methods on older server versions.
        }
        if (player.isSprinting()) {
            builder.append("(sprinting)");
        }
        if (player.isSneaking()) {
            builder.append("(sneaking)");
        }
        if (player.isBlocking()) {
            builder.append("(blocking)");
        }
        final Vector v = player.getVelocity();
        if (v.lengthSquared() > 0.0) {
            builder.append("(svel=" + v.getX() + "," + v.getY() + "," + v.getZ() + ")");
        }
    }

    /**
     * Append potion effect related information.
     *
     * @param player the player to read data from
     * @param mcAccess the MCAccess instance to use
     * @param builder the builder to append to
     */
    private void appendPotionEffectDetails(final Player player, final MCAccess mcAccess,
            final StringBuilder builder) {
        final double speed = mcAccess.getFasterMovementAmplifier(player);
        // See MCAccessSpigotCB class JavaDocs for the meaning of NEGATIVE_INFINITY.
        if (!Double.isInfinite(speed)) {
            builder.append("(e_speed=" + (speed + 1) + ")");
        }
        final double slow = PotionUtil.getPotionEffectAmplifier(player, BridgePotionEffect.SLOWNESS);
        if (!Double.isInfinite(slow)) {
            builder.append("(e_slow=" + (slow + 1) + ")");
        }
        final double jump = mcAccess.getJumpAmplifier(player);
        // Sentinel NEGATIVE_INFINITY indicates no jump potion effect.
        if (!Double.isInfinite(jump)) {
            builder.append("(e_jump=" + (jump + 1) + ")");
        }
        final double strider = BridgeEnchant.getDepthStriderLevel(player);
        if (strider != 0) {
            builder.append("(e_depth_strider=" + strider + ")");
        }
    }

    /**
     * Append information about flight or gliding states.
     *
     * @param player the player to read data from
     * @param builder the builder to append to
     */
    private void appendFlightInfo(final Player player, final StringBuilder builder) {
        if (Bridge1_9.isGliding(player)) {
            builder.append("(gliding)");
        }
        if (player.getAllowFlight()) {
            builder.append("(allow_flight)");
        }
        if (player.isFlying()) {
            builder.append("(flying)");
        }
    }

    /**
     * Collect and append block flag information for the given locations.
     *
     * @param from the starting location
     * @param to the target location
     * @param maxYOnGround the max y on ground value
     * @param builder the builder to append to
     */
    private void appendBlockFlagDetails(final PlayerLocation from, final PlayerLocation to,
            final double maxYOnGround, final StringBuilder builder) {
        builder.setLength(0);
        // Note: the block flags are for normal on-ground checking, not with yOnGrond set to 0.5.
        from.collectBlockFlags(maxYOnGround);
        if (from.getBlockFlags() != 0) {
            builder.append("\nFrom flags: " + StringUtil.join(BlockFlags.getFlagNames(from.getBlockFlags()), "+"));
        }
        if (!BlockProperties.isAir(from.getTypeId())) {
            DebugUtil.addBlockInfo(builder, from, "\nFrom");
        }
        if (!BlockProperties.isAir(from.getTypeIdBelow())) {
            DebugUtil.addBlockBelowInfo(builder, from, "\nFrom");
        }
        if (!from.isOnGround() && from.isOnGround(0.5)) {
            builder.append(" (ground within 0.5)");
        }
        to.collectBlockFlags(maxYOnGround);
        if (to.getBlockFlags() != 0) {
            builder.append("\nTo flags: " + StringUtil.join(BlockFlags.getFlagNames(to.getBlockFlags()), "+"));
        }
        if (!BlockProperties.isAir(to.getTypeId())) {
            DebugUtil.addBlockInfo(builder, to, "\nTo");
        }
        if (!BlockProperties.isAir(to.getTypeIdBelow())) {
            DebugUtil.addBlockBelowInfo(builder, to, "\nTo");
        }
        if (!to.isOnGround() && to.isOnGround(0.5)) {
            builder.append(" (ground within 0.5)");
        }
    }
}
