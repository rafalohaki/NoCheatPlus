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
package fr.neatmonster.nocheatplus.checks.blockinteract;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.CheckListener;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.combined.CombinedConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.util.MovingUtil;
import fr.neatmonster.nocheatplus.checks.net.FlyingQueueHandle;
import fr.neatmonster.nocheatplus.checks.net.model.DataPacketFlying;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.BridgeHealth;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.checks.moving.helper.CheckContext;
import fr.neatmonster.nocheatplus.checks.moving.helper.ElytraBoostHandler;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.components.data.ICheckData;
import fr.neatmonster.nocheatplus.components.data.IData;
import fr.neatmonster.nocheatplus.components.registry.factory.IFactoryOne;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.players.PlayerFactoryArgument;
import fr.neatmonster.nocheatplus.stats.Counters;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;
import fr.neatmonster.nocheatplus.utilities.InventoryUtil;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.utilities.location.LocUtil;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.worlds.WorldFactoryArgument;

/**
 * Central location to listen to events that are relevant for the block interact checks.
 * 
 * @see BlockInteractEvent
 */
public class BlockInteractListener extends CheckListener {

    public static void debugBlockVSBlockInteract(final Player player, final CheckType checkType, 
            final Block block, final String prefix, final Action expectedAction,
            final IPlayerData pData) {
        final BlockInteractData bdata = pData.getGenericInstance(BlockInteractData.class);
        final int manhattan = bdata.manhattanLastBlock(block);
        String msg;
        if (manhattan == Integer.MAX_VALUE) {
            msg =  "no last block set!";
        }
        else {
            msg = manhattan == 0 ? "same as last block." 
                    : ("last block differs, Manhattan: " + manhattan);
            if (bdata.getLastIsCancelled()) {
                msg += " / cancelled";
            }
            if (bdata.getLastAction() != expectedAction) {
                msg += " / action=" + bdata.getLastAction();
            }
        }
        CheckUtils.debug(player, checkType, prefix + " BlockInteract: " + msg);
    }

    // INSTANCE ----

    /** The looking-direction check. */
    private final Direction direction = addCheck(new Direction());

    /** The reach-distance check. */
    private final Reach     reach     = addCheck(new Reach());

    /** Interact with visible blocks. */
    private final Visible visible = addCheck(new Visible());

    /** Speed of interaction. */
    private final Speed speed = addCheck(new Speed());

    /** For temporary use: LocUtil.clone before passing deeply, call setWorld(null) after use. */
    private final Location useLoc = new Location(null, 0, 0, 0);

    private final Counters counters = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstance(Counters.class);
    private final int idCancelDead = counters.registerKey("cancel.dead");
    private final int idCancelOffline = counters.registerKey("cancel.offline");
    private final int idInteractLookCurrent = counters.registerKey("block.interact.look.current");
    private final int idInteractLookFlyingFirst = counters.registerKey("block.interact.look.flying.first");
    private final int idInteractLookFlyingOther = counters.registerKey("block.interact.look.flying.other");

    @SuppressWarnings("unchecked")
    public BlockInteractListener() {
        super(CheckType.BLOCKINTERACT);
        final NoCheatPlusAPI api = NCPAPIProvider.getNoCheatPlusAPI();
        api.register(api.newRegistrationContext() //
                // BlockInteractConfig
                .registerConfigWorld(BlockInteractConfig.class)
                .factory(arg -> new BlockInteractConfig(arg.worldData))
                .registerConfigTypesPlayer(CheckType.BLOCKINTERACT, true)
                .context() //
                // BlockinteractData
                .registerDataPlayer(BlockInteractData.class)
                .factory(arg -> new BlockInteractData())
                .addToGroups(CheckType.BLOCKINTERACT, true, IData.class, ICheckData.class)
                .context() //
                );
    }

    /**
     * We listen to PlayerInteractEvent events for obvious reasons.
     * 
     * @param event
     *            the event
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        final IPlayerData pData = DataManager.getPlayerData(player);
        if (pData == null || !pData.isCheckActive(CheckType.BLOCKINTERACT, player)) {
            return;
        }

        final BlockInteractData data = pData.getGenericInstance(BlockInteractData.class);
        data.resetLastBlock();

        if (handleInitialCancellation(event, player, data, pData)) {
            return;
        }

        final InteractContext ctx = prepareContext(event, player, data, pData);
        if (ctx == null) {
            data.setPlayerInteractEventResolution(event);
            return;
        }

        final BlockInteractConfig cc = pData.getGenericInstance(BlockInteractConfig.class);
        final Location loc = player.getLocation(useLoc);
        final FlyingQueueHandle flyingHandle = new FlyingQueueHandle(pData);
        final BlockInteractData.CheckResult result = runChecks(player, ctx, pData, data, cc, flyingHandle, loc);

        if (result.isCancelled()) {
            onCancelInteract(player, ctx.block(), ctx.face(), event, ctx.previousLastTick(),
                    result.isPreventUseItem(), data, cc, pData);
        } else {
            handleFlyingQueue(player, pData, flyingHandle);
        }

        data.setPlayerInteractEventResolution(event);
        useLoc.setWorld(null);
    }
    private boolean handleInitialCancellation(final PlayerInteractEvent event, final Player player,
            final BlockInteractData data, final IPlayerData pData) {
        final int cancelId;
        if (player.isDead() && BridgeHealth.getHealth(player) <= 0.0) {
            cancelId = idCancelDead;
        } else if (!player.isOnline()) {
            cancelId = idCancelOffline;
        } else if (MovingUtil.hasScheduledPlayerSetBack(player)) {
            cancelId = -1;
        } else {
            cancelId = Integer.MIN_VALUE;
        }
        if (cancelId == Integer.MIN_VALUE) {
            return false;
        }
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
        event.setCancelled(true);
        data.setPlayerInteractEventResolution(event);
        if (cancelId >= 0) {
            counters.addPrimaryThread(cancelId, 1);
        }
        return true;
    }

    private InteractContext prepareContext(final PlayerInteractEvent event, final Player player,
            final BlockInteractData data, final IPlayerData pData) {
        final Action action = event.getAction();
        final Block block = event.getClickedBlock();
        final int previousLastTick = data.getLastTick();

        boolean blockChecks = true;
        if (block == null) {
            data.resetLastBlock();
            blockChecks = false;
        } else {
            data.setLastBlock(block, action);
        }

        final BlockFace face = event.getBlockFace();
        final ActionResult aResult;
        switch (action) {
        case RIGHT_CLICK_AIR:
            aResult = handleRightClickAir(blockChecks);
            break;
        case LEFT_CLICK_AIR:
            aResult = handleLeftClickAir(blockChecks);
            break;
        case LEFT_CLICK_BLOCK:
            aResult = handleLeftClickBlock(blockChecks);
            break;
        case RIGHT_CLICK_BLOCK:
            aResult = handleRightClickBlock(player, block, face, event, previousLastTick, data, pData,
                    blockChecks);
            break;
        default:
            return null;
        }
        final ItemStack stack = aResult.stack();
        blockChecks = aResult.blockChecks();

        if (event.isCancelled() && event.useInteractedBlock() != Result.ALLOW) {
            if (event.useItemInHand() == Result.ALLOW) {
                blockChecks = false;
            } else {
                return null;
            }
        }
        return new InteractContext(action, block, face, stack, previousLastTick, blockChecks);
    }

    private BlockInteractData.CheckResult runChecks(final Player player, final InteractContext ctx, final IPlayerData pData,
            final BlockInteractData data, final BlockInteractConfig cc, final FlyingQueueHandle flyingHandle,
            final Location loc) {
        final BlockInteractData.CheckResult result = data.getCheckResult();
        result.reset();
        boolean cancelled = false;
        boolean preventUseItem = false;

        if (speed.isEnabled(player, pData) && speed.check(player, data, cc)) {
            cancelled = true;
            preventUseItem = true;
        }

        if (ctx.blockChecks()) {
            final double eyeHeight = MovingUtil.getEyeHeight(player);
            if (!cancelled && reach.isEnabled(player, pData)
                    && reach.check(player, loc, eyeHeight, ctx.block(), data, cc)) {
                cancelled = true;
            }
            if (!cancelled && direction.isEnabled(player, pData)
                    && direction.check(player, loc, eyeHeight, ctx.block(), ctx.face(), flyingHandle, data, cc, pData)) {
                cancelled = true;
            }
            if (!cancelled && visible.isEnabled(player, pData)
                    && visible.check(player, loc, eyeHeight, ctx.block(), ctx.face(), ctx.action(), flyingHandle,
                            data, cc, pData)) {
                cancelled = true;
            }
        }
        result.set(cancelled, preventUseItem);
        return result;
    }

    private void handleFlyingQueue(final Player player, final IPlayerData pData, final FlyingQueueHandle flyingHandle) {
        if (flyingHandle.isFlyingQueueFetched()) {
            final int flyingIndex = flyingHandle.getFirstIndexWithContentIfFetched();
            final Integer cId = flyingIndex == 0 ? idInteractLookFlyingFirst : idInteractLookFlyingOther;
            counters.add(cId, 1);
            if (pData.isDebugActive(CheckType.BLOCKINTERACT)) {
                logUsedFlyingPacket(player, flyingHandle, flyingIndex);
            }
        } else {
            counters.addPrimaryThread(idInteractLookCurrent, 1);
        }
    }

    private record InteractContext(Action action, Block block, BlockFace face, ItemStack stack,
            int previousLastTick, boolean blockChecks) {}

    record ActionResult(ItemStack stack, boolean blockChecks) {}

    private ActionResult handleRightClickAir(final boolean blockChecks) {
        return new ActionResult(null, blockChecks);
    }

    private ActionResult handleLeftClickAir(final boolean blockChecks) {
        return new ActionResult(null, blockChecks);
    }

    private ActionResult handleLeftClickBlock(final boolean blockChecks) {
        return new ActionResult(null, blockChecks);
    }

    private ActionResult handleRightClickBlock(final Player player, final Block block, final BlockFace face,
            final PlayerInteractEvent event, final int previousLastTick, final BlockInteractData data,
            final IPlayerData pData, boolean blockChecks) {
        final ItemStack stack = Bridge1_9.getUsedItem(player, event);
        if (stack != null && stack.getType() == Material.ENDER_PEARL) {
            checkEnderPearlRightClickBlock(player, block, face, event, previousLastTick, data, pData);
        }
        if (stack != null && BlockProperties.isScaffolding(stack.getType())) {
            blockChecks = false;
        }
        return new ActionResult(stack, blockChecks);
    }

    private void logUsedFlyingPacket(final Player player, final FlyingQueueHandle flyingHandle, 
            final int flyingIndex) {
        final DataPacketFlying packet = flyingHandle.getIfFetched(flyingIndex);
        if (packet != null) {
            debug(player, "Flying packet queue used at index " + flyingIndex + ": pitch=" + packet.getPitch() + ",yaw=" + packet.getYaw());
            return;
        }
    }

    private void onCancelInteract(final Player player, final Block block, final BlockFace face, 
            final PlayerInteractEvent event, final int previousLastTick, final boolean preventUseItem, 
            final BlockInteractData data, final BlockInteractConfig cc, final IPlayerData pData) {
        final boolean debug = pData.isDebugActive(CheckType.BLOCKINTERACT);
        if (event.isCancelled()) {
            // Just prevent using the block.
            event.setUseInteractedBlock(Result.DENY);
            if (debug) {
                genericDebug(player, block, face, event, "already cancelled: deny use block", previousLastTick, data, cc);
            }
        }
        else {
            final Result previousUseItem = event.useItemInHand();
            event.setCancelled(true);
            event.setUseInteractedBlock(Result.DENY);
            if (
                    previousUseItem == Result.DENY || preventUseItem
                    // Allow consumption still.
                    || !InventoryUtil.isConsumable(Bridge1_9.getUsedItem(player, event))
                    ) {
                event.setUseItemInHand(Result.DENY);
                if (debug) {
                    genericDebug(player, block, face, event, "deny item use", previousLastTick, data, cc);
                }
            }
            else {
                // Consumable and not prevented otherwise.
                // Ender pearl?
                event.setUseItemInHand(Result.ALLOW);
                if (debug) {
                    genericDebug(player, block, face, event, "allow edible item use", previousLastTick, data, cc);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.MONITOR)
    public void onPlayerInteractMonitor(final PlayerInteractEvent event) {
        // Set event resolution.
        final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getPlayerData(player);
        final BlockInteractData data = pData.getGenericInstance(BlockInteractData.class);
        data.setPlayerInteractEventResolution(event);

        if (!pData.isCheckActive(CheckType.MOVING, player)) return;

        /*
         * BlockDamageEvent fires before BlockInteract/MONITOR level,
         * BlockBreak after (!). Thus resolution is set on LOWEST already,
         * probably should be HIGHEST to account for other plugins.
         */
        // Elytra boost.
        /*
         * Cross check with the next incoming move: has an item been used,
         * is gliding, reset if necessary.
         */
        //final Block block = event.getClickedBlock();
        //        if (data.debug) {
        //            debug(player, "BlockInteractResolution: cancelled=" + event.isCancelled() 
        //            + " action=" + event.getAction() + " block=" + block + " item=" + Bridge1_9.getUsedItem(player, event));
        //        }
        if (
                (
                        event.getAction() == Action.RIGHT_CLICK_AIR
                        // Water doesn't happen, block typically is null.
                        //                        || event.getAction() == Action.RIGHT_CLICK_BLOCK
                        //                        && block != null && BlockProperties.isLiquid(block.getType())
                        // web ?
                        )
                && event.isCancelled() && event.useItemInHand() != Result.DENY) {
            final ItemStack stack = Bridge1_9.getUsedItem(player, event);
            if (stack != null) {
                final CheckContext ctx = new CheckContext(player,
                        pData.getGenericInstance(MovingData.class));
                final boolean boosted = ElytraBoostHandler.handleBoost(ctx, stack,
                        TickTask.getTick());
                if (boosted && pData.isDebugActive(CheckType.MOVING)) {
                    debug(player, "Elytra boost (power " +
                            BridgeMisc.getFireworksPower(stack) + "): " + stack);
                }
            }
        }
    }

    private void checkEnderPearlRightClickBlock(final Player player, final Block block, 
            final BlockFace face, final PlayerInteractEvent event, 
            final int previousLastTick, final BlockInteractData data,
            final IPlayerData pData) {
        if (block == null || !BlockProperties.isPassable(block.getType())) {
            final CombinedConfig ccc = pData.getGenericInstance(CombinedConfig.class);
            if (ccc.enderPearlCheck && ccc.enderPearlPreventClickBlock) {
                event.setUseItemInHand(Result.DENY);
                if (pData.isDebugActive(CheckType.BLOCKINTERACT)) {
                    final BlockInteractConfig cc = pData.getGenericInstance(BlockInteractConfig.class);
                    genericDebug(player, block, face, event, "click block: deny use ender pearl", previousLastTick, data, cc);
                }
            }
        }
    }

    private void genericDebug(final Player player, final Block block, final BlockFace face, 
            final PlayerInteractEvent event, final String tag, final int previousLastTick, 
            final BlockInteractData data, final BlockInteractConfig cc) {
        final StringBuilder builder = new StringBuilder(512);
        // Rate limit.
        if (data.getLastTick() == previousLastTick && data.subsequentCancel > 0) {
            data.rateLimitSkip ++;
            return;
        }
        // Debug log.
        builder.append("Interact cancel: " + event.isCancelled());
        builder.append(" (");
        builder.append(tag);
        if (block == null) {
            builder.append(") block: null");
        }
        else {
            builder.append(") block: ");
            builder.append(block.getWorld().getName() + "/" + LocUtil.simpleFormat(block));
            builder.append(" type: " + block.getType());
            if (!Bridge1_13.hasIsSwimming()) builder.append(" data: " + BlockProperties.getData(block));
            builder.append(" face: " + face);
        }

        if (data.rateLimitSkip > 0) {
            builder.append(" skipped(rate-limit: " + data.rateLimitSkip);
            data.rateLimitSkip = 0;
        }
        debug(player, builder.toString());
    }

}
