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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.actions.ActionList;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.access.ACheckConfig;
import fr.neatmonster.nocheatplus.checks.moving.magic.Magic;
import fr.neatmonster.nocheatplus.checks.moving.model.ModelFlying;
import fr.neatmonster.nocheatplus.checks.moving.player.PlayerSetBackMethod;
import fr.neatmonster.nocheatplus.checks.moving.util.MovingUtil;
import fr.neatmonster.nocheatplus.command.CommandUtil;
import fr.neatmonster.nocheatplus.compat.AlmostBoolean;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.versions.Bugs;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.components.config.value.OverrideType;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.utilities.ColorUtil;
import fr.neatmonster.nocheatplus.utilities.ds.prefixtree.SimpleCharPrefixTree;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.worlds.IWorldData;

/**
 * Configurations specific for the moving checks. Every world gets one of these
 * assigned to it.
 */
public class MovingConfig extends ACheckConfig {

    // Model flying ids.
    public static final String ID_JETPACK_ELYTRA = "jetpack.elytra";
    public static final String ID_POTION_LEVITATION = "potion.levitation";
    public static final String ID_POTION_SLOWFALLING = "potion.slowfalling";
    public static final String ID_EFFECT_RIPTIDING = "effect.riptiding";

    // INSTANCE

    public boolean    ignoreCreative;
    public boolean    ignoreAllowFlight;

    private Map<GameMode, ModelFlying> flyingModelGameMode = new HashMap<GameMode, ModelFlying>();
    private ModelFlying flyingModelElytra;
    private ModelFlying flyingModelLevitation;
    private ModelFlying flyingModelSlowfalling;
    private ModelFlying flyingModelRiptiding;
    public ActionList creativeFlyActions;

    /** Assumed number of packets per second under ideal conditions. */
    public float      morePacketsEPSIdeal;
    /** The maximum number of packets per second that we accept. */
    public float      morePacketsEPSMax;
    public int        morePacketsEPSBuckets;
    public float		morePacketsBurstPackets;
    public double		morePacketsBurstDirect;
    public double		morePacketsBurstEPM;
    public int        morePacketsSetBackAge;
    public ActionList morePacketsActions;

    /**
     * Deal damage instead of Minecraft, whenever a player is judged to be on
     * ground.
     */
    public boolean    noFallDealDamage;
    public boolean    noFallSkipAllowFlight;
    /**
     * Reset data on violation, i.e. a player taking fall damage without being
     * on ground.
     */
    public boolean    noFallViolationReset;
    /** Reset data on tp. */
    public boolean 	noFallTpReset;
    /** Reset if in vehicle. */
    public boolean noFallVehicleReset;
    /** Reset fd to 0  if on ground (dealdamage only). */
    public boolean noFallAntiCriticals;
    public ActionList noFallActions;

    // PassableAccuracy: also use if ray-tracing is not used
    public ActionList passableActions;
    public double     passableHorizontalMargins;
    public double     passableVerticalMargins;
    public boolean    passableUntrackedTeleportCheck;
    public boolean    passableUntrackedCommandCheck;
    public boolean    passableUntrackedCommandTryTeleport;
    public SimpleCharPrefixTree passableUntrackedCommandPrefixes = new SimpleCharPrefixTree();

    public int        survivalFlyBlockingSpeed;
    public int        survivalFlySneakingSpeed;
    public int        survivalFlySpeedingSpeed;
    public int        survivalFlySprintingSpeed;
    public int        survivalFlySwimmingSpeed;
    public int        survivalFlyWalkingSpeed;
    public boolean    sfSlownessSprintHack;
    /**
     * If true, will allow moderate bunny hop without lift off. Applies for
     * normal speed on 1.6.4 and probably below.
     */
    public boolean    sfGroundHop;
    public double     sfStepHeight;
    public boolean    survivalFlyAccountingH;
    public boolean    survivalFlyAccountingV;
    public boolean    survivalFlyAccountingStep;
    public boolean    survivalFlyResetItem;
    // Leniency settings.
    /** Horizontal buffer (rather sf), after failure leniency. */
    public double     hBufMax;
    public long       survivalFlyVLFreezeCount;
    public boolean    survivalFlyVLFreezeInAir;
    // Set back policy.
    public boolean    sfSetBackPolicyVoid;
    public boolean    sfSetBackPolicyApplyFallDamage;
    public ActionList survivalFlyActions;

    public boolean 	sfHoverCheck; // Placeholder for potential sub check
    public int 		sfHoverTicks;
    public int		sfHoverLoginTicks;
    public boolean    sfHoverTakeFallDamage;
    public double		sfHoverViolation;

    // Special tolerance values:
    /**
     * Number of moving packets until which a velocity entry must be activated,
     * in order to not be removed.
     */
    public int		velocityActivationCounter;
    /** Server ticks until invalidating queues velocity. */
    public int		velocityActivationTicks;
    public boolean	velocityStrictInvalidation;
    public double     noFallyOnGround;
    public double     yOnGround;

    // General things.
    /**
     * If to allow splitting moves, due to player.getLocation reflecting
     * something else than from/to.
     */
    public boolean splitMoves;
    public boolean ignoreStance;
    public boolean tempKickIllegal;
    public boolean loadChunksOnJoin;
    public boolean loadChunksOnMove;
    public boolean loadChunksOnTeleport;
    public boolean loadChunksOnWorldChange;
    public long sprintingGrace;
    public boolean assumeSprint;
    public int speedGrace;
    public boolean enforceLocation;
    public boolean trackBlockMove;
    public PlayerSetBackMethod playerSetBackMethod;
    public boolean resetFwOnground;
    public boolean elytraStrict;

    // Vehicles
    public boolean vehicleEnforceLocation;
    public boolean vehiclePreventDestroyOwn;
    public boolean scheduleVehicleSetBacks;
    public boolean schedulevehicleSetPassenger;

    public Set<EntityType> ignoredVehicles = new HashSet<EntityType>();

    public ActionList vehicleMorePacketsActions;

    public HashMap<EntityType, Double> vehicleEnvelopeHorizontalSpeedCap = new HashMap<EntityType, Double>();
    public ActionList vehicleEnvelopeActions;

    // Trace
    public int traceMaxAge;
    public int traceMaxSize;

    // Messages.
    public String msgKickIllegalMove;
    public String msgKickIllegalVehicleMove;

    /**
     * Instantiates a new moving configuration.
     * 
     * @param config
     *            the data
     */
    public MovingConfig(final IWorldData worldData) {
        super(worldData);
        final ConfigFile config = worldData.getRawConfiguration();

        ignoreCreative = config.getBoolean(ConfPaths.MOVING_CREATIVEFLY_IGNORECREATIVE);
        ignoreAllowFlight = config.getBoolean(ConfPaths.MOVING_CREATIVEFLY_IGNOREALLOWFLIGHT);

        loadCreativeFlyModels(config);
        loadMorePacketsSettings(config);
        loadNoFallSettings(config);
        loadPassableSettings(config);
        loadSurvivalFlySettings(config);
        loadVelocitySettings(config);
        loadGeneralSettings(config, worldData);
        loadVehicleSettings(config, worldData);
        loadMessageSettings(config);
    }


   /**
    * Retrieve the CreativeFly model to use in thisMove (Set in the MovingListener).
    * Note that the name is somewhat anachronistic. (Should be renamed to CreativeFlyModel/MovementModel/(...))
    * @param player
    * @param fromLocation
    * @param data
    * @param cc
    * 
    */
    public ModelFlying getModelFlying(final Player player, final PlayerLocation fromLocation, final MovingData data, final MovingConfig cc) {

        final GameMode gameMode = player.getGameMode();
        final ModelFlying modelGameMode = flyingModelGameMode.get(gameMode);
        final boolean isGlidingWithElytra = Bridge1_9.isGlidingWithElytra(player) && MovingUtil.isGlidingWithElytraValid(player, fromLocation, data, cc);
        final double levitationLevel = Bridge1_9.getLevitationAmplifier(player);
        final long now = System.currentTimeMillis();
        final boolean RiptidePhase = Bridge1_13.isRiptiding(player) || data.timeRiptiding + 1500 > now;
        switch(gameMode) {
            case SURVIVAL:
            case ADVENTURE:
            case CREATIVE:
                // Specific checks.
                break;
            default:
                // Default by game mode (spectator, yet unknown).
                return modelGameMode;
        }
        // Actual flying (ignoreAllowFlight is a legacy option for rocket boots like flying).

        // NOTE: Riptiding has priority over anything else 
        if (player.isFlying() && !RiptidePhase 
            || !isGlidingWithElytra && !ignoreAllowFlight && player.getAllowFlight()
            && !RiptidePhase) {
            return modelGameMode;
        }
        // Elytra.
        if (isGlidingWithElytra && !RiptidePhase) { // Defensive: don't demand isGliding.
            return flyingModelElytra;
        }
        // Levitation.
        if (gameMode != GameMode.CREATIVE && !Double.isInfinite(levitationLevel) 
            && !RiptidePhase
            && !fromLocation.isInLiquid()
            // According to minecraft wiki:
            // Levitation level over 127 = fall down at a fast or slow rate, depending on the value.
            // Using /effect minecraft:levitation 255 makes the player fly exclusively horizontally.
            && !(levitationLevel >= 128)) {
            return flyingModelLevitation;
        }
        // Slow Falling
        if (gameMode != GameMode.CREATIVE && !Double.isInfinite(Bridge1_13.getSlowfallingAmplifier(player)) 
            && !RiptidePhase) { 
            return flyingModelSlowfalling;
        }
        // Riptiding
        // Riptide should have top priority; consider adding data.timeRiptiding and removing redundant checks
        if (RiptidePhase) {
            return flyingModelRiptiding;
        }
        // Default by game mode.
        return modelGameMode;
    }

    private void loadCreativeFlyModels(final ConfigFile config) {
        final ModelFlying defaultModel = new ModelFlying("gamemode.creative", config,
                ConfPaths.MOVING_CREATIVEFLY_MODEL + "creative.", new ModelFlying().lock());
        for (final GameMode gameMode : GameMode.values()) {
            flyingModelGameMode.put(gameMode, new ModelFlying("gamemode." + gameMode.name().toLowerCase(), config,
                    ConfPaths.MOVING_CREATIVEFLY_MODEL + gameMode.name().toLowerCase() + ".", defaultModel).lock());
        }
        flyingModelLevitation = new ModelFlying(ID_POTION_LEVITATION, config,
                ConfPaths.MOVING_CREATIVEFLY_MODEL + "levitation.",
                new ModelFlying(null, defaultModel).scaleLevitationEffect(true).lock());
        flyingModelSlowfalling = new ModelFlying(ID_POTION_SLOWFALLING, config,
                ConfPaths.MOVING_CREATIVEFLY_MODEL + "slowfalling.",
                new ModelFlying(null, defaultModel).scaleSlowfallingEffect(true).lock());
        flyingModelRiptiding = new ModelFlying(ID_EFFECT_RIPTIDING, config,
                ConfPaths.MOVING_CREATIVEFLY_MODEL + "riptiding.",
                new ModelFlying(null, defaultModel).scaleRiptidingEffect(true).lock());
        flyingModelElytra = new ModelFlying(ID_JETPACK_ELYTRA, config,
                ConfPaths.MOVING_CREATIVEFLY_MODEL + "elytra.",
                new ModelFlying(null, defaultModel).verticalAscendGliding(true).lock());

        resetFwOnground = config.getBoolean(ConfPaths.MOVING_CREATIVEFLY_EYTRA_FWRESET);
        elytraStrict = config.getBoolean(ConfPaths.MOVING_CREATIVEFLY_EYTRA_STRICT);
        creativeFlyActions = config.getOptimizedActionList(ConfPaths.MOVING_CREATIVEFLY_ACTIONS,
                Permissions.MOVING_CREATIVEFLY);
    }

    private void loadMorePacketsSettings(final ConfigFile config) {
        morePacketsEPSIdeal = config.getInt(ConfPaths.MOVING_MOREPACKETS_EPSIDEAL);
        morePacketsEPSMax = Math.max(morePacketsEPSIdeal, config.getInt(ConfPaths.MOVING_MOREPACKETS_EPSMAX));
        morePacketsEPSBuckets = 2 * Math.max(1, Math.min(60, config.getInt(ConfPaths.MOVING_MOREPACKETS_SECONDS)));
        morePacketsBurstPackets = config.getInt(ConfPaths.MOVING_MOREPACKETS_BURST_EPM);
        morePacketsBurstDirect = config.getInt(ConfPaths.MOVING_MOREPACKETS_BURST_DIRECT);
        morePacketsBurstEPM = config.getInt(ConfPaths.MOVING_MOREPACKETS_BURST_EPM);
        morePacketsSetBackAge = config.getInt(ConfPaths.MOVING_MOREPACKETS_SETBACKAGE);
        morePacketsActions = config.getOptimizedActionList(ConfPaths.MOVING_MOREPACKETS_ACTIONS,
                Permissions.MOVING_MOREPACKETS);
    }

    private void loadNoFallSettings(final ConfigFile config) {
        noFallDealDamage = config.getBoolean(ConfPaths.MOVING_NOFALL_DEALDAMAGE);
        noFallSkipAllowFlight = config.getBoolean(ConfPaths.MOVING_NOFALL_SKIPALLOWFLIGHT);
        noFallViolationReset = config.getBoolean(ConfPaths.MOVING_NOFALL_RESETONVL);
        noFallTpReset = config.getBoolean(ConfPaths.MOVING_NOFALL_RESETONTP);
        noFallVehicleReset = config.getBoolean(ConfPaths.MOVING_NOFALL_RESETONVEHICLE);
        noFallAntiCriticals = config.getBoolean(ConfPaths.MOVING_NOFALL_ANTICRITICALS);
        noFallActions = config.getOptimizedActionList(ConfPaths.MOVING_NOFALL_ACTIONS,
                Permissions.MOVING_NOFALL);
    }

    private void loadPassableSettings(final ConfigFile config) {
        passableActions = config.getOptimizedActionList(ConfPaths.MOVING_PASSABLE_ACTIONS,
                Permissions.MOVING_PASSABLE);
        passableHorizontalMargins = config.getDouble(ConfPaths.MOVING_PASSABLE_RT_XZ_FACTOR, 0.1, 1.0, 0.999999);
        passableVerticalMargins = config.getDouble(ConfPaths.MOVING_PASSABLE_RT_Y_FACTOR, 0.1, 1.0, 0.999999);
        passableUntrackedTeleportCheck = config.getBoolean(ConfPaths.MOVING_PASSABLE_UNTRACKED_TELEPORT_ACTIVE);
        passableUntrackedCommandCheck = config.getBoolean(ConfPaths.MOVING_PASSABLE_UNTRACKED_CMD_ACTIVE);
        passableUntrackedCommandTryTeleport = config.getBoolean(ConfPaths.MOVING_PASSABLE_UNTRACKED_CMD_TRYTELEPORT);
        CommandUtil.feedCommands(passableUntrackedCommandPrefixes, config,
                ConfPaths.MOVING_PASSABLE_UNTRACKED_CMD_PREFIXES, true);
    }

    private void loadSurvivalFlySettings(final ConfigFile config) {
        // Default values are specified here because this settings aren't showed by default into the configuration file.
        survivalFlyBlockingSpeed = config.getInt(ConfPaths.MOVING_SURVIVALFLY_BLOCKINGSPEED, 100);
        survivalFlySneakingSpeed = config.getInt(ConfPaths.MOVING_SURVIVALFLY_SNEAKINGSPEED, 100);
        survivalFlySpeedingSpeed = config.getInt(ConfPaths.MOVING_SURVIVALFLY_SPEEDINGSPEED, 200);
        survivalFlySprintingSpeed = config.getInt(ConfPaths.MOVING_SURVIVALFLY_SPRINTINGSPEED, 100);
        survivalFlySwimmingSpeed = config.getInt(ConfPaths.MOVING_SURVIVALFLY_SWIMMINGSPEED, 100);
        survivalFlyWalkingSpeed = config.getInt(ConfPaths.MOVING_SURVIVALFLY_WALKINGSPEED, 100);
        sfSlownessSprintHack = config.getAlmostBoolean(ConfPaths.MOVING_SURVIVALFLY_SLOWNESSSPRINTHACK,
                AlmostBoolean.MAYBE).decideOptimistically();
        sfGroundHop = config.getBoolean(ConfPaths.MOVING_SURVIVALFLY_GROUNDHOP,
                ServerVersion.compareMinecraftVersion("1.7") == -1);
        survivalFlyAccountingH = config.getBoolean(ConfPaths.MOVING_SURVIVALFLY_EXTENDED_HACC);
        survivalFlyAccountingV = config.getBoolean(ConfPaths.MOVING_SURVIVALFLY_EXTENDED_VACC);
        survivalFlyAccountingStep = config.getBoolean(ConfPaths.MOVING_SURVIVALFLY_EXTENDED_STEP);
        survivalFlyResetItem = config.getBoolean(ConfPaths.MOVING_SURVIVALFLY_EXTENDED_RESETITEM);
        sfSetBackPolicyApplyFallDamage = config.getBoolean(ConfPaths.MOVING_SURVIVALFLY_SETBACKPOLICY_APPLYFALLDAMAGE);
        sfSetBackPolicyVoid = config.getBoolean(ConfPaths.MOVING_SURVIVALFLY_SETBACKPOLICY_VOIDTOVOID);
        final double sfStepHeight = config.getDouble(ConfPaths.MOVING_SURVIVALFLY_STEPHEIGHT, Double.MAX_VALUE);
        if (sfStepHeight == Double.MAX_VALUE) {
            final String ref;
            if (Bukkit.getVersion().toLowerCase().contains("spigot")) {
                ref = "1.7.10";
            } else {
                ref = "1.8";
            }
            this.sfStepHeight = ServerVersion.select(ref, 0.5, 0.6, 0.6, 0.5).doubleValue();
        } else {
            this.sfStepHeight = sfStepHeight;
        }
        hBufMax = config.getDouble(ConfPaths.MOVING_SURVIVALFLY_LENIENCY_HBUFMAX);
        survivalFlyVLFreezeCount = config.getInt(ConfPaths.MOVING_SURVIVALFLY_LENIENCY_FREEZECOUNT);
        survivalFlyVLFreezeInAir = config.getBoolean(ConfPaths.MOVING_SURVIVALFLY_LENIENCY_FREEZEINAIR);
        survivalFlyActions = config.getOptimizedActionList(ConfPaths.MOVING_SURVIVALFLY_ACTIONS,
                Permissions.MOVING_SURVIVALFLY);

        sfHoverCheck = config.getBoolean(ConfPaths.MOVING_SURVIVALFLY_HOVER_CHECK);
        sfHoverTicks = config.getInt(ConfPaths.MOVING_SURVIVALFLY_HOVER_TICKS);
        sfHoverLoginTicks = Math.max(0, config.getInt(ConfPaths.MOVING_SURVIVALFLY_HOVER_LOGINTICKS));
        sfHoverTakeFallDamage = config.getBoolean(ConfPaths.MOVING_SURVIVALFLY_HOVER_TAKEFALLDAMAGE);
        sfHoverViolation = config.getDouble(ConfPaths.MOVING_SURVIVALFLY_HOVER_SFVIOLATION);
    }

    private void loadVelocitySettings(final ConfigFile config) {
        velocityActivationCounter = config.getInt(ConfPaths.MOVING_VELOCITY_ACTIVATIONCOUNTER);
        velocityActivationTicks = config.getInt(ConfPaths.MOVING_VELOCITY_ACTIVATIONTICKS);
        velocityStrictInvalidation = config.getBoolean(ConfPaths.MOVING_VELOCITY_STRICTINVALIDATION);
        yOnGround = config.getDouble(ConfPaths.MOVING_YONGROUND, Magic.Y_ON_GROUND_MIN, Magic.Y_ON_GROUND_MAX,
                Magic.Y_ON_GROUND_DEFAULT);
        noFallyOnGround = config.getDouble(ConfPaths.MOVING_NOFALL_YONGROUND, Magic.Y_ON_GROUND_MIN,
                Magic.Y_ON_GROUND_MAX, yOnGround);
    }

    private void loadGeneralSettings(final ConfigFile config, final IWorldData worldData) {
        AlmostBoolean refSplitMoves = config.getAlmostBoolean(ConfPaths.MOVING_SPLITMOVES, AlmostBoolean.MAYBE);
        splitMoves = refSplitMoves.decideOptimistically();
        AlmostBoolean refIgnoreStance = config.getAlmostBoolean(ConfPaths.MOVING_IGNORESTANCE, AlmostBoolean.MAYBE);
        ignoreStance = refIgnoreStance == AlmostBoolean.MAYBE
                ? ServerVersion.compareMinecraftVersion("1.8") >= 0
                : refIgnoreStance.decide();
        tempKickIllegal = config.getBoolean(ConfPaths.MOVING_TEMPKICKILLEGAL);
        loadChunksOnJoin = config.getBoolean(ConfPaths.MOVING_LOADCHUNKS_JOIN);
        loadChunksOnMove = config.getBoolean(ConfPaths.MOVING_LOADCHUNKS_MOVE);
        loadChunksOnTeleport = config.getBoolean(ConfPaths.MOVING_LOADCHUNKS_TELEPORT);
        loadChunksOnWorldChange = config.getBoolean(ConfPaths.MOVING_LOADCHUNKS_WORLDCHANGE);
        sprintingGrace = Math.max(0L, (long) (config.getDouble(ConfPaths.MOVING_SPRINTINGGRACE) * 1000.0));
        assumeSprint = config.getBoolean(ConfPaths.MOVING_ASSUMESPRINT);
        speedGrace = Math.max(0, (int) Math.round(config.getDouble(ConfPaths.MOVING_SPEEDGRACE) * 20.0));
        AlmostBoolean ref = config.getAlmostBoolean(ConfPaths.MOVING_ENFORCELOCATION, AlmostBoolean.MAYBE);
        if (ref == AlmostBoolean.MAYBE) {
            enforceLocation = Bugs.shouldEnforceLocation();
        } else {
            enforceLocation = ref.decide();
        }
        trackBlockMove = config.getBoolean(ConfPaths.COMPATIBILITY_BLOCKS_CHANGETRACKER_ACTIVE)
                && (config.getBoolean(ConfPaths.COMPATIBILITY_BLOCKS_CHANGETRACKER_PISTONS));
        final PlayerSetBackMethod playerSetBackMethod = PlayerSetBackMethod.fromString(
                "extern.fromconfig", config.getString(ConfPaths.MOVING_SETBACK_METHOD));
        if (playerSetBackMethod.doesThisMakeSense()) {
            this.playerSetBackMethod = playerSetBackMethod;
        } else if (ServerVersion.compareMinecraftVersion("1.9") < 0) {
            this.playerSetBackMethod = PlayerSetBackMethod.LEGACY;
        } else {
            this.playerSetBackMethod = PlayerSetBackMethod.MODERN;
        }
        traceMaxAge = config.getInt(ConfPaths.MOVING_TRACE_MAXAGE, 30);
        traceMaxSize = config.getInt(ConfPaths.MOVING_TRACE_MAXSIZE, 30);
    }

    private void loadVehicleSettings(final ConfigFile config, final IWorldData worldData) {
        AlmostBoolean ref = config.getAlmostBoolean(ConfPaths.MOVING_VEHICLE_ENFORCELOCATION, AlmostBoolean.MAYBE);
        vehicleEnforceLocation = ref.decideOptimistically();
        vehiclePreventDestroyOwn = config.getBoolean(ConfPaths.MOVING_VEHICLE_PREVENTDESTROYOWN);
        scheduleVehicleSetBacks = config.getAlmostBoolean(ConfPaths.MOVING_VEHICLE_SCHEDULESETBACKS,
                AlmostBoolean.MAYBE).decide();
        vehicleMorePacketsActions = config.getOptimizedActionList(ConfPaths.MOVING_VEHICLE_MOREPACKETS_ACTIONS,
                Permissions.MOVING_MOREPACKETS);
        schedulevehicleSetPassenger = config.getAlmostBoolean(ConfPaths.MOVING_VEHICLE_DELAYADDPASSENGER,
                AlmostBoolean.MAYBE).decideOptimistically();
        ref = config.getAlmostBoolean(ConfPaths.MOVING_VEHICLE_ENVELOPE_ACTIVE, AlmostBoolean.MAYBE);
        if (ServerVersion.compareMinecraftVersion("1.9") < 0) {
            worldData.overrideCheckActivation(CheckType.MOVING_VEHICLE_ENVELOPE,
                    AlmostBoolean.NO, OverrideType.PERMANENT, true);
        }
        config.readDoubleValuesForEntityTypes(ConfPaths.MOVING_VEHICLE_ENVELOPE_HSPEEDCAP,
                vehicleEnvelopeHorizontalSpeedCap, 4.0, true);
        vehicleEnvelopeActions = config.getOptimizedActionList(ConfPaths.MOVING_VEHICLE_ENVELOPE_ACTIONS,
                Permissions.MOVING_VEHICLE_ENVELOPE);
        List<String> types;
        if (config.get(ConfPaths.MOVING_VEHICLE_IGNOREDVEHICLES) == null) {
            types = Arrays.asList("arrow", "spectral_arrow", "tipped_arrow");
        } else {
            types = config.getStringList(ConfPaths.MOVING_VEHICLE_IGNOREDVEHICLES);
        }
        for (String stype : types) {
            try {
                EntityType type = EntityType.valueOf(stype.toUpperCase());
                if (type != null) {
                    ignoredVehicles.add(type);
                }
            } catch (IllegalArgumentException e) {
                // ignore - unknown vehicle type
            }
        }
    }

    private void loadMessageSettings(final ConfigFile config) {
        msgKickIllegalMove = ColorUtil.replaceColors(config.getString(ConfPaths.MOVING_MESSAGE_ILLEGALPLAYERMOVE));
        msgKickIllegalVehicleMove = ColorUtil.replaceColors(config.getString(ConfPaths.MOVING_MESSAGE_ILLEGALVEHICLEMOVE));
    }

}
