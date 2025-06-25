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

import java.util.concurrent.ThreadLocalRandom;

import fr.neatmonster.nocheatplus.components.registry.feature.TickListener;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.TurtleEgg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.magic.Magic;
import fr.neatmonster.nocheatplus.checks.moving.model.LocationData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.BridgeEnchant;
import fr.neatmonster.nocheatplus.compat.BridgeHealth;
import fr.neatmonster.nocheatplus.compat.BridgeMaterial;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;

/**
 * A check to see if people cheat by tricking the server to not deal them fall damage.
 */
public class NoFall extends Check {

    /*
     * Due to farmland or soil not converting back to dirt with the current
     * implementation, packet synchronization with moving events should be
     * implemented. Then alter packet on-ground and Minecraft fall distance for a
     * new default concept. As a fallback either the old method, or an adaptation
     * with scheduled/later fall damage dealing could be considered, detecting
     * the actual cheat with a slight delay. Packet synchronization will need a
     * better tracking than the last n packets, e.g. include the
     * latest/oldest significant packet for (...) and if a packet has already
     * been related to a Bukkit event.
     */

    /** For temporary use: LocUtil.clone before passing deeply, call setWorld(null) after use. */
    private final Location useLoc = new Location(null, 0, 0, 0);
    private final Location useLoc2 = new Location(null, 0, 0, 0);

    private final static boolean ServerIsAtLeast1_12 = ServerVersion.compareMinecraftVersion("1.12") >= 0;

    /**
     * Instantiates a new no fall check.
     */
    public NoFall() {
        super(CheckType.MOVING_NOFALL);
    }


    /**
     * Calculate the damage in hearts from the given fall distance.
     * @param fallDistance
     * @return
     */
    public static final double getDamage(final float fallDistance) {
        return fallDistance - Magic.FALL_DAMAGE_DIST;
    }


    /**
     * Deal damage if appropriate. To be used for if the player is on ground
     * somehow. Contains checking for skipping conditions (getAllowFlight set +
     * configured to skip).
     * 
     * @param mcPlayer
     * @param data
     * @param y
     * @param previousSetBackY
     *            The set back y from lift-off. If not present:
     *            Double.NEGATIVE_INFINITY.
     */
    private void handleOnGround(final Player player, final double y, final double previousSetBackY,
                                final boolean reallyOnGround, final MovingData data, final MovingConfig cc,
                                final IPlayerData pData) {

        // Damage to be dealt.
        float fallDist = (float) getApplicableFallHeight(player, y, previousSetBackY, data);
        // Consider cleaning up NoFall to only track for ground state and override as mentioned above
        // (saves performance with packet dependency and precise ground state requirements)
        // or still maintain it
        // (requires tracking impulse changes, block changes on land and damage recalculation -> degraded efficiency but packet independent
        if (fallDist - Magic.FALL_DAMAGE_DIST > 0.0 && data.noFallCurrentLocOnWindChargeHit != null) {
            final double lastImpluseY = data.noFallCurrentLocOnWindChargeHit.getY();
            data.clearWindChargeImpulse();
            fallDist = (float) (lastImpluseY < y ? 0.0 : lastImpluseY - y);
        }
        double maxD = getDamage(fallDist);
        maxD = calcDamagewithfeatherfalling(player, calcReducedDamageByBlock(player, data, maxD), 
                                            mcAccess.getHandle().dealFallDamageFiresAnEvent().decide());
        fallOn(player, fallDist);

        if (maxD >= Magic.FALL_DAMAGE_MINIMUM) {
            // Check skipping conditions.
            if (cc.noFallSkipAllowFlight && player.getAllowFlight()) {
                data.clearNoFallData();
                data.noFallSkipAirCheck = true;
                // Not resetting the fall distance here, let Minecraft or the issue tracker deal with that.
            }
            else {
                // Additional effects such as sounds could be added here via a custom event with violation information.
                if (pData.isDebugActive(type)) {
                    debug(player, "NoFall deal damage" + (reallyOnGround ? "" : "violation") + ": " + maxD);
                }
                // Detect fake fall distance accumulation here as well if necessary.
                data.noFallSkipAirCheck = true;
                dealFallDamage(player, maxD);
            }
        }
        else {
            data.clearNoFallData();
            player.setFallDistance(0);
        }
    }


    /**
     * Change state of some blocks when they fall on like Farmland
     * 
     * @param player
     * @param fallDist
     * @return if allow to change the block
     */
    private void fallOn(final Player player, final double fallDist) {

        // Note: move data pTo is required because this location isn't updated
        Block block = player.getLocation(useLoc2).subtract(0.0, 1.0, 0.0).getBlock();
        if (block.getType() == BridgeMaterial.FARMLAND && fallDist > 0.5 && ThreadLocalRandom.current().nextFloat() < fallDist - 0.5) {
            final BlockState newState = block.getState();
            newState.setType(Material.DIRT);
            //if (Bridge1_13.hasIsSwimming()) newState.setBlockData(Bukkit.createBlockData(newState.getType()));
            if (canChangeBlock(player, block, newState, true, true, true)) {
                // Move up a little bit in order not to stuck in a block
                player.setVelocity(new Vector(player.getVelocity().getX() * -1, 0.062501, player.getVelocity().getZ() * -1));
                block.setType(Material.DIRT);
            }
            return;
        }
        if (Bridge1_13.hasIsSwimming() && block.getType() == Material.TURTLE_EGG && ThreadLocalRandom.current().nextInt(3) == 0) {
            final TurtleEgg egg = (TurtleEgg) block.getBlockData();
            final BlockState newState = block.getState();
            if (canChangeBlock(player, block, newState, true, false, false)) {
                if (egg.getEggs() - 1 > 0) {
                    egg.setEggs(egg.getEggs() - 1);
                } else block.setType(Material.AIR);
            }
        }
        useLoc2.setWorld(null);
    }
    

    /**
     * Fire events to see if other plugins allow to change the block
     * 
     * @param player
     * @param block
     * @param newState the BlockState of new block
     * @param interact if fire PlayerInteractEvent
     * @param entityChangeBlock if fire EntityChangeBlockEvent
     * @param fade if fire BlockFadeEvent
     * @return if can change the block
     */
    private boolean canChangeBlock(final Player player, final Block block, final BlockState newState,
            final boolean interact, final boolean entityChangeBlock, final boolean fade) {

        if (interact) {
            final PlayerInteractEvent interactevent = new PlayerInteractEvent(player, Action.PHYSICAL, null, block, BlockFace.SELF);
            Bukkit.getPluginManager().callEvent(interactevent);
            if (interactevent.isCancelled()) return false;
        }

        if (entityChangeBlock) {
            if (!Bridge1_13.hasIsSwimming()) {
                // 1.6.4-1.12.2 backward compatibility
                Object o = ReflectionUtil.newInstance(
                    ReflectionUtil.getConstructor(EntityChangeBlockEvent.class, Entity.class, Block.class, Material.class, byte.class),
                    player, block, Material.DIRT, (byte)0
                );
                if (o instanceof EntityChangeBlockEvent) {
                    EntityChangeBlockEvent event = (EntityChangeBlockEvent)o;
                    Bukkit.getPluginManager().callEvent(event);
                    if (event.isCancelled()) return false;
                }
            } 
            else {
                final EntityChangeBlockEvent blockevent = new EntityChangeBlockEvent(player, block, newState.getBlockData()); 
                Bukkit.getPluginManager().callEvent(blockevent);
                if (blockevent.isCancelled()) return false;
            }
        }

        // Not fire on 1.8 below
        if (fade && Bridge1_9.hasGetItemInOffHand()) {
            final BlockState newstate = block.getState();
            newstate.setType(Material.DIRT);
            final BlockFadeEvent fadeevent = new BlockFadeEvent(block, newstate);
            Bukkit.getPluginManager().callEvent(fadeevent);
            if (fadeevent.isCancelled()) return false;
        }
        return true;
    }


    /**
     * Correct fall damage according to the feather fall enchant
     * 
     * @param player
     * @param damage
     * @param active
     * @return corrected fall damage
     */
    public static double calcDamagewithfeatherfalling(Player player, double damage, boolean active) {

        if (active) return damage;
        if (BridgeEnchant.hasFeatherFalling() && damage > 0.0) {
            int levelench = BridgeEnchant.getFeatherFallingLevel(player);
            if (levelench > 0) {
                int tmp = levelench * 3;
                if (tmp > 20) tmp = 20;
                return damage * (1.0 - tmp / 25.0);
            }
        }
        return damage;
    }
    

    /**
     * Reduce the fall damage if the player lands on a specific block
     * 
     * @param player
     * @param data
     * @param damage
     * @return reduced damage
     */
    public static double calcReducedDamageByBlock(final Player player, final MovingData data,final double damage) {

        final PlayerMoveData validmove = data.playerMoves.getLatestValidMove();
        if (validmove != null && validmove.toIsValid) {
            // Note: move data pTo is required because this location isn't updated
            final Material blockmat = player.getWorld().getBlockAt(
                    Location.locToBlock(validmove.to.getX()), Location.locToBlock(validmove.to.getY()), Location.locToBlock(validmove.to.getZ())
                    ).getType();
            if ((BlockFlags.getBlockFlags(blockmat) & BlockFlags.F_STICKY) != 0) {
                return damage / 5D;
            }
            if (ServerIsAtLeast1_12 && MaterialUtil.BEDS.contains(blockmat)) {
                return damage / 2D;
            }
            if (Bridge1_9.hasEndRod() && blockmat == Material.HAY_BLOCK) {
                return damage / 5D;
            }
        }
        return damage;
    }


    /**
     * Estimate the applicable fall height for the given data.
     * 
     * @param player
     * @param y
     * @param previousSetBackY
     *            The set back y from lift-off. If not present:
     *            Double.NEGATIVE_INFINITY.
     * @param data
     * @return
     */
    private static double getApplicableFallHeight(final Player player, final double y, final double previousSetBackY, final MovingData data) {

        //return getDamage(Math.max((float) (data.noFallMaxY - y), Math.max(data.noFallFallDistance, player.getFallDistance())));
        final double yDistance = Math.max(data.noFallMaxY - y, data.noFallFallDistance);
        if (yDistance > 0.0 && data.jumpAmplifier > 0.0 
            && previousSetBackY != Double.NEGATIVE_INFINITY) {
            // Fall height counts below previous set-back-y.
            // Updating the amplifier after lift-off likely does not make sense.
            // In case of velocity consider skipping or calculate a maximum exempt height.
            final double correction = data.noFallMaxY - previousSetBackY;
            if (correction > 0.0) {
                final float effectiveDistance = (float) Math.max(0.0, yDistance - correction);
                return effectiveDistance;
            }
        }
        return yDistance;
    }


    public static double getApplicableFallHeight(final Player player, final double y, final MovingData data) {
        return getApplicableFallHeight(player, y, 
                data.hasSetBack() ? data.getSetBackY() : Double.NEGATIVE_INFINITY, data);
    }


    /**
     * Test if fall damage would be dealt accounting for the given data.
     * 
     * @param player
     * @param y
     * @param previousSetBackY
     *            The set back y from lift-off. If not present:
     *            Double.NEGATIVE_INFINITY.
     * @param data
     * @return
     */
    public boolean willDealFallDamage(final Player player, final double y, 
                                      final double previousSetBackY, final MovingData data) {

        return getDamage((float) getApplicableFallHeight(player, y, previousSetBackY, data))
                                 - Magic.FALL_DAMAGE_DIST >= Magic.FALL_DAMAGE_MINIMUM;
    }

    /**
     * 
     * @param player
     * @param minY
     * @param reallyOnGround
     * @param data
     * @param cc
     */
    private void adjustFallDistance(final Player player, final double minY, final boolean reallyOnGround, 
                                    final MovingData data, final MovingConfig cc) {

        final float noFallFallDistance = Math.max(data.noFallFallDistance, (float) (data.noFallMaxY - minY));
        if (noFallFallDistance >= Magic.FALL_DAMAGE_DIST) {
            final float fallDistance = player.getFallDistance();

            if (noFallFallDistance - fallDistance >= 0.5f // Adjustment threshold could be reviewed
                || noFallFallDistance >= Magic.FALL_DAMAGE_DIST
                && fallDistance < Magic.FALL_DAMAGE_DIST // Ensure damage.
                ) {
                player.setFallDistance(noFallFallDistance);
            }
        }
        data.clearNoFallData();
        // Force damage on event fire, no need air checking!
        // Future improvement: use deal damage and override on ground at packet level
        // (avoid recalculating reduced damage or accounting for block changes)
        data.noFallSkipAirCheck = true;
    }


    private void dealFallDamage(final Player player, final double damage) {
        if (mcAccess.getHandle().dealFallDamageFiresAnEvent().decide()) {
            // Consider whether deciding optimistically is the better approach
            mcAccess.getHandle().dealFallDamage(player, damage);
        }
        else {
            final EntityDamageEvent event = BridgeHealth.getEntityDamageEvent(player, DamageCause.FALL, damage);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                // For some odd reason, player#setNoDamageTicks does not actually
                // set the no damage ticks. As a workaround, wait for it to be zero and then damage the player.
                if (player.getNoDamageTicks() > 0) {
                    TickListener damagePlayer = new TickListener() {
                        @Override
                        public void onTick(int tick, long timeLast) {
                            if (player.getNoDamageTicks() > 0) return;
                            player.setLastDamageCause(event);
                            mcAccess.getHandle().dealFallDamage(player, BridgeHealth.getRawDamage(event));
                            TickTask.removeTickListener(this);
                        }
                    };
                    TickTask.addTickListener(damagePlayer);
                } 
                else {
                    player.setLastDamageCause(event);
                    mcAccess.getHandle().dealFallDamage(player, BridgeHealth.getRawDamage(event));
                }
            }
        }

        // Currently resetting is done from within the damage event handler.
        // Detect whether the event fired at all and override if necessary. A probe once per class might be sufficient.
        //        data.clearNoFallData();
        player.setFallDistance(0);
    }

    /**
     * Checks a player. Expects from and to using cc.yOnGround.
     * 
     * @param player
     *            the player
     * @param from
     *            the from
     * @param to
     *            the to
     * @param previousSetBackY
     *            The set back y from lift-off. If not present:
     *            Double.NEGATIVE_INFINITY.
     */
    public void check(final Player player, final PlayerLocation pFrom, final PlayerLocation pTo, 
                      final double previousSetBackY,
                      final MovingData data, final MovingConfig cc, final IPlayerData pData) {

        final boolean debug = pData.isDebugActive(type);
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final LocationData from = thisMove.from;
        final LocationData to = thisMove.to;
        final double fromY = from.getY();
        final double toY = to.getY();
        final double yDiff = toY - fromY;
        final double oldNFDist = data.noFallFallDistance;
        // Reset-cond is not touched by yOnGround.
        // Distinguish water depth versus fall distance?
        /*
         * Account for flags instead (F_FALLDIST_ZERO and
         * F_FALLDIST_HALF). Reset condition as trigger: if (resetFrom) { ...
         */
        // Also handle from and to independently (rather fire twice than wait for next time).
        final boolean fromReset = from.resetCond;
        final boolean toReset = to.resetCond;
        final boolean fromOnGround, toOnGround;
        
        // Adapt yOnGround if necessary (sf uses another setting).
        if (yDiff < 0 && cc.yOnGround < cc.noFallyOnGround) {
            // In fact this is somewhat heuristic, but it seems to work well.
            // Missing on-ground seems to happen with running down pyramids rather.
            // This adjustment should be obsolete in future versions.
            adjustYonGround(pFrom, pTo , cc.noFallyOnGround);
            fromOnGround = pFrom.isOnGround();
            toOnGround = pTo.isOnGround();
        } 
        else {
            fromOnGround = from.onGround;
            toOnGround = to.onGround;
        }

        // Consider early returns for clarity

        final double minY = Math.min(fromY, toY);

        if (fromReset) {
            // Just reset.
            data.clearNoFallData();
            // Ensure very big/strange moves don't yield violations.
            if (toY - fromY <= -Magic.FALL_DAMAGE_DIST) {
                data.noFallSkipAirCheck = true;
            }
        }
        else if (fromOnGround || !toOnGround && thisMove.touchedGround) {
            // Check if to deal damage (fall back damage check).
            touchDown(player, minY, previousSetBackY, data, cc, pData); // Includes the current y-distance on descend!
            // Ensure very big/strange moves don't yield violations.
            if (toY - fromY <= -Magic.FALL_DAMAGE_DIST) {
                data.noFallSkipAirCheck = true;
            }
        }
        else if (toReset) {
            // Just reset.
            data.clearNoFallData();
        }
        else if (toOnGround) {
            // Check if to deal damage.
            if (yDiff < 0) {
                // In this case the player has traveled further: add the difference.
                data.noFallFallDistance -= yDiff;
            }
            touchDown(player, minY, previousSetBackY, data, cc, pData);
        }
        else {
            // Ensure fall distance is correct, or "anyway"?
        }

        // Set reference y for nofall (always).
        /*
         * Consider setting this before handleOnGround (at least for
         * resetTo). This is after dealing damage and may need to be handled differently.
         */
        data.noFallMaxY = Math.max(Math.max(fromY, toY), data.noFallMaxY);

        // Fall distance might be behind
        // Should data.noFallMaxY be counted in?
        final float mcFallDistance = player.getFallDistance(); // Note: it has to be fetched here.
        // SKIP: data.noFallFallDistance = Math.max(mcFallDistance, data.noFallFallDistance);

        // Add y distance.
        if (!toReset && !toOnGround && yDiff < 0) {
            data.noFallFallDistance -= yDiff;
        }
        else if (cc.noFallAntiCriticals && (toReset || toOnGround || (fromReset || fromOnGround || thisMove.touchedGround) && yDiff >= 0)) {
            final double max = Math.max(data.noFallFallDistance, mcFallDistance);
            if (max > 0.0 && max < 0.75) { // (Ensure this does not conflict with deal-damage set to false.) 

                if (debug) {
                    debug(player, "NoFall: Reset fall distance (anticriticals): mc=" + mcFallDistance +" / nf=" + data.noFallFallDistance);
                }

                if (data.noFallFallDistance > 0) {
                    data.noFallFallDistance = 0;
                }
                
                if (mcFallDistance > 0f) {
                    player.setFallDistance(0f);
                }
            }
        }

        if (debug) {
            debug(player, "NoFall: mc=" + mcFallDistance +" / nf=" + data.noFallFallDistance + (oldNFDist < data.noFallFallDistance ? " (+" + (data.noFallFallDistance - oldNFDist) + ")" : "") + " | ymax=" + data.noFallMaxY);
        }

    }

    /**
     * Called during check.
     * 
     * @param player
     * @param minY
     * @param previousSetBackY
     *            The set back y from lift-off. If not present:
     *            Double.NEGATIVE_INFINITY.
     * @param data
     * @param cc
     */
    private void touchDown(final Player player, final double minY, final double previousSetBackY,
            final MovingData data, final MovingConfig cc, IPlayerData pData) {
        if (cc.noFallDealDamage) {
            handleOnGround(player, minY, previousSetBackY, true, data, cc, pData);
        }
        else {
            adjustFallDistance(player, minY, true, data, cc);
        }
    }

    /**
     * Set yOnGround for from and to, if needed, should be obsolete.
     * @param from
     * @param to
     * @param cc
     */
    private void adjustYonGround(final PlayerLocation from, final PlayerLocation to, final double yOnGround) {
        if (!from.isOnGround()) {
            from.setyOnGround(yOnGround);
        }
        if (!to.isOnGround()) {
            to.setyOnGround(yOnGround);
        }
    }

    /**
     * Quit or kick: adjust fall distance if necessary.
     * @param player
     */
    public void onLeave(final Player player, final MovingData data, 
            final IPlayerData pData) {
        final float fallDistance = player.getFallDistance();
        // Might also detect an excessively high Minecraft fall distance
        if (data.noFallFallDistance > fallDistance) {
            final double playerY = player.getLocation(useLoc).getY();
            useLoc.setWorld(null);
            if (player.isFlying() || player.getGameMode() == GameMode.CREATIVE
                    || player.getAllowFlight() 
                    && pData.getGenericInstance(MovingConfig.class).noFallSkipAllowFlight) {
                // Forestall potential issues with flying plugins.
                player.setFallDistance(0f);
                data.noFallFallDistance = 0f;
                data.noFallMaxY = playerY;
            } else {
                // Might use tolerance, might log, might use method (compare: MovingListener.onEntityDamage).
                // Might consider triggering violations here as well.
                final float yDiff = (float) (data.noFallMaxY - playerY);
                // Consider using only one accounting method (maxY)
                final float maxDist = Math.max(yDiff, data.noFallFallDistance);
                player.setFallDistance(maxDist);
            }
        }
    }

    /**
     * This is called if a player fails a check and gets set back, to avoid using that to avoid fall damage the player might be dealt damage here. 
     * @param player
     * @param data
     */
    public void checkDamage(final Player player,  final double y, 
            final MovingData data, final IPlayerData pData) {
        final MovingConfig cc = pData.getGenericInstance(MovingConfig.class);
        // Deal damage.
        handleOnGround(player, y, data.hasSetBack() ? data.getSetBackY() : Double.NEGATIVE_INFINITY, 
                false, data, cc, pData);
    }

}
