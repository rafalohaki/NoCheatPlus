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
package fr.neatmonster.nocheatplus.checks.blockbreak;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.CheckListener;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.blockinteract.BlockInteractData;
import fr.neatmonster.nocheatplus.checks.blockinteract.BlockInteractListener;
import fr.neatmonster.nocheatplus.checks.inventory.Items;
import fr.neatmonster.nocheatplus.checks.moving.util.MovingUtil;
import fr.neatmonster.nocheatplus.checks.net.FlyingQueueHandle;
import fr.neatmonster.nocheatplus.checks.net.model.DataPacketFlying;
import fr.neatmonster.nocheatplus.compat.AlmostBoolean;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.components.data.ICheckData;
import fr.neatmonster.nocheatplus.components.data.IData;
import fr.neatmonster.nocheatplus.components.registry.factory.IFactoryOne;
import fr.neatmonster.nocheatplus.hooks.NCPExemptionManager;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.players.PlayerFactoryArgument;
import fr.neatmonster.nocheatplus.stats.Counters;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.time.monotonic.Monotonic;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.checks.blockbreak.FastBreakContext;
import fr.neatmonster.nocheatplus.worlds.WorldFactoryArgument;

/**
 * Central location to listen to events that are relevant for the block break checks.
 * 
 * @see BlockBreakEvent
 */
public class BlockBreakListener extends CheckListener {

    /** The direction check. */
    private final Direction direction = addCheck(new Direction());

    /** The fast break check (per block breaking speed). */
    private final FastBreak fastBreak = addCheck(new FastBreak());

    /** The frequency check (number of blocks broken) */
    private final Frequency frequency = addCheck(new Frequency());

    /** The no swing check. */
    private final NoSwing   noSwing   = addCheck(new NoSwing());

    /** The reach check. */
    private final Reach     reach     = addCheck(new Reach());

    /** The wrong block check. */
    private final WrongBlock wrongBlock = addCheck(new WrongBlock());

    private AlmostBoolean isInstaBreak = AlmostBoolean.NO;

    private final Counters counters = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstance(Counters.class);
    private final int idCancelDIllegalItem = counters.registerKey("illegalitem");

    /** For temporary use: LocUtil.clone before passing deeply, call setWorld(null) after use. */
    private final Location useLoc = new Location(null, 0, 0, 0);

    @SuppressWarnings("unchecked")
    public BlockBreakListener(){
        super(CheckType.BLOCKBREAK);
        final NoCheatPlusAPI api = NCPAPIProvider.getNoCheatPlusAPI();
        // Register config and data.
        api.register(api.newRegistrationContext()
                // BlockBreakConfig
                .registerConfigWorld(BlockBreakConfig.class)
                .factory(arg -> new BlockBreakConfig(arg.worldData))
                .registerConfigTypesPlayer(CheckType.BLOCKBREAK, true)
                .context() //
                // BlockBreakData
                .registerDataPlayer(BlockBreakData.class)
                .factory(arg -> new BlockBreakData(
                        arg.playerData.getGenericInstance(BlockBreakConfig.class)))
                // (Complete data removal for now.)
                .addToGroups(CheckType.BLOCKBREAK, true, IData.class, ICheckData.class)
                .context() //
                );
    }

    /**
     * Check if the player reference is valid and online.
     *
     * @param player the player to validate
     * @return {@code true} if the player is non-null and online
     */
    private boolean isPlayerValid(final Player player) {
        return player != null && player.isOnline();
    }

    /**
     * Determine if block break checks should run for the player.
     *
     * @param pData  the player data instance
     * @param player the player reference
     * @return {@code true} if checks are active
     */
    private boolean isCheckActive(final IPlayerData pData, final Player player) {
        return pData != null && pData.isCheckActive(CheckType.BLOCKBREAK, player);
    }

    /**
     * Validate the block for processing.
     *
     * @param block the block to validate
     * @return {@code true} if the block is not scaffolding
     */
    private boolean isBlockValid(final Block block) {
        return !BlockProperties.isScaffolding(block.getType());
    }

    /**
     * We listen to BlockBreak events for obvious reasons.
     * 
     * @param event
     *            the event
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onBlockBreak(final BlockBreakEvent event) {
        final long now = Monotonic.millis();
        final Player player = event.getPlayer();
        final IPlayerData pData = player != null ? DataManager.getInstance().getPlayerData(player) : null;
        final Block block = event.getBlock();

        boolean process = isPlayerValid(player);
        if (process && !isCheckActive(pData, player)) {
            process = false;
        }
        if (process && initialCancelChecks(event, player, pData)) {
            isInstaBreak = AlmostBoolean.NO;
            process = false;
        }
        if (process && !isBlockValid(block)) {
            process = false;
        }

        if (process) {
            final BreakCheckResult result = performBreakChecks(player, block, pData);
            finalizeBreak(event, player, block, pData, result, now);
        }
    }

    private boolean initialCancelChecks(final BlockBreakEvent event, final Player player,
            final IPlayerData pData) {
        if (Items.checkIllegalEnchantmentsAllHands(player, pData)) {
            event.setCancelled(true);
            counters.addPrimaryThread(idCancelDIllegalItem, 1);
            return true;
        }
        if (MovingUtil.hasScheduledPlayerSetBack(player)) {
            event.setCancelled(true);
            return true;
        }
        return event.isCancelled();
    }

    private BreakCheckResult performBreakChecks(final Player player, final Block block,
            final IPlayerData pData) {
        final BreakCheckResult result = new BreakCheckResult();
        if (player == null) {
            return result;
        }

        final BlockBreakConfig cc = pData.getGenericInstance(BlockBreakConfig.class);
        final BlockBreakData data = pData.getGenericInstance(BlockBreakData.class);
        result.data = data;
        final BlockInteractData bdata = pData.getGenericInstance(BlockInteractData.class);
        final int tick = TickTask.getTick();
        final boolean isInteractBlock = block != null && !bdata.getLastIsCancelled()
                && bdata.matchesLastBlock(tick, block);
        final GameMode gameMode = player.getGameMode();

        applyWrongBlockCheck(result, player, block, cc, data, pData);
        applyFrequencyCheck(result, player, tick, cc, data, pData);
        applyFastBreakCheck(result, player, block, gameMode, cc, data, pData);
        applyNoSwingCheck(result, player, data, pData);
        applyReachDirectionChecks(result, player, block, isInteractBlock, bdata, cc, data, pData);
        applyLiquidBreakCheck(result, player, block, pData);

        return result;
    }

    private void applyWrongBlockCheck(final BreakCheckResult result, final Player player,
            final Block block, final BlockBreakConfig cc, final BlockBreakData data,
            final IPlayerData pData) {
        if (!result.cancelled && wrongBlock.isEnabled(player, pData)
                && wrongBlock.check(player, block, cc, data, pData)) {
            result.cancelled = true;
        }
    }

    private void applyFrequencyCheck(final BreakCheckResult result, final Player player,
            final int tick, final BlockBreakConfig cc, final BlockBreakData data,
            final IPlayerData pData) {
        if (!result.cancelled && frequency.isEnabled(player, pData)
                && frequency.check(player, tick, cc, data, pData)) {
            result.cancelled = true;
        }
    }

    private void applyFastBreakCheck(final BreakCheckResult result, final Player player,
            final Block block, final GameMode gameMode, final BlockBreakConfig cc,
            final BlockBreakData data, final IPlayerData pData) {
        if (block != null && !result.cancelled && gameMode != GameMode.CREATIVE
                && fastBreak.isEnabled(player, pData)
                && !FastBreakDecision.shouldSkip(isInstaBreak)) {
            final FastBreakContext ctx = new FastBreakContext(player, block, cc, data, pData,
                    isInstaBreak);
            if (fastBreak.check(ctx)) {
                result.cancelled = true;
            }
        }
    }

    private void applyNoSwingCheck(final BreakCheckResult result, final Player player,
            final BlockBreakData data, final IPlayerData pData) {
        if (!result.cancelled && noSwing.isEnabled(player, pData)
                && noSwing.check(player, data, pData)) {
            result.cancelled = true;
        }
    }

    private void applyReachDirectionChecks(final BreakCheckResult result, final Player player,
            final Block block, final boolean isInteractBlock, final BlockInteractData bdata,
            final BlockBreakConfig cc, final BlockBreakData data, final IPlayerData pData) {
        if (block == null) {
            return;
        }
        final boolean reachEnabled = reach.isEnabled(player, pData);
        final boolean directionEnabled = direction.isEnabled(player, pData);
        if (!(reachEnabled || directionEnabled)) {
            return;
        }

        result.flyingHandle = new FlyingQueueHandle(pData);
        final Location loc = player.getLocation(useLoc);
        final double eyeHeight = MovingUtil.getEyeHeight(player);
        if (!result.cancelled) {
            if (isInteractBlock && bdata.isPassedCheck(CheckType.BLOCKINTERACT_REACH)) {
                result.skippedRedundantChecks++;
            } else if (reachEnabled && reach.check(player, eyeHeight, block, data, cc)) {
                result.cancelled = true;
            }
        }
        if (!result.cancelled) {
            if (isInteractBlock && (bdata.isPassedCheck(CheckType.BLOCKINTERACT_DIRECTION)
                    || bdata.isPassedCheck(CheckType.BLOCKINTERACT_VISIBLE))) {
                result.skippedRedundantChecks++;
            } else if (directionEnabled && direction.check(player, loc, eyeHeight, block, null,
                    result.flyingHandle, data, cc, pData)) {
                result.cancelled = true;
            }
        }
        useLoc.setWorld(null);
    }

    private void applyLiquidBreakCheck(final BreakCheckResult result, final Player player,
            final Block block, final IPlayerData pData) {
        if (block == null) {
            return;
        }
        if (!result.cancelled && BlockProperties.isLiquid(block.getType())
                && !BlockProperties.isWaterPlant(block.getType())
                && !pData.hasPermission(Permissions.BLOCKBREAK_BREAK_LIQUID, player)
                && !NCPExemptionManager.isExempted(player, CheckType.BLOCKBREAK_BREAK)) {
            result.cancelled = true;
        }
    }

    private void finalizeBreak(final BlockBreakEvent event, final Player player, final Block block,
            final IPlayerData pData, final BreakCheckResult result, final long now) {
        final BlockBreakData data = result.data;
        if (result.cancelled) {
            event.setCancelled(true);
            data.clickedX = block.getX();
            data.clickedY = block.getY();
            data.clickedZ = block.getZ();
        } else if (pData.isDebugActive(CheckType.BLOCKBREAK)) {
            debugBlockBreakResult(player, block, result.skippedRedundantChecks, result.flyingHandle, pData);
        }
        if (isInstaBreak.decideOptimistically()) {
            data.wasInstaBreak = now;
        } else {
            data.wasInstaBreak = 0;
        }
        data.fastBreakBreakTime = now;
        isInstaBreak = AlmostBoolean.NO;
    }

    private static final class BreakCheckResult {
        boolean cancelled;
        int skippedRedundantChecks;
        FlyingQueueHandle flyingHandle;
        BlockBreakData data;
    }

    private void debugBlockBreakResult(final Player player, final Block block, final int skippedRedundantChecks, 
            final FlyingQueueHandle flyingHandle, final IPlayerData pData) {
        debug(player, "Block break(" + block.getType() + "): " + block.getX() + ", " + block.getY() + ", " + block.getZ());
        BlockInteractListener.debugBlockVSBlockInteract(player, checkType, block, "onBlockBreak", 
                Action.LEFT_CLICK_BLOCK, pData);
        if (skippedRedundantChecks > 0) {
            debug(player, "Skipped redundant checks: " + skippedRedundantChecks);
        }
        if (flyingHandle != null && flyingHandle.isFlyingQueueFetched()) {
            final int flyingIndex = flyingHandle.getFirstIndexWithContentIfFetched();
            final DataPacketFlying packet = flyingHandle.getIfFetched(flyingIndex);
            if (packet != null) {
                debug(player, "Flying packet queue used at index " + flyingIndex + ": pitch=" + packet.getPitch() + ",yaw=" + packet.getYaw());
                return;
            }
        }
    }

    /**
     * We listen to PlayerAnimation events because it is (currently) equivalent to "player swings arm" and we want to
     * check if they did that between block breaks.
     * 
     * @param event
     *            the event
     */
    @EventHandler(
            priority = EventPriority.MONITOR)
    public void onPlayerAnimation(final PlayerAnimationEvent event) {
        // Just set a flag to true when the arm was swung.
        // debug(player, "Animation");
        final BlockBreakData data = DataManager.getInstance().getPlayerData(event.getPlayer()).getGenericInstance(BlockBreakData.class);
        data.noSwingCount = Math.max(data.noSwingCount - 1, 0);
    }

    /**
     * We listen to BlockInteract events to be (at least in many cases) able to distinguish between block break events
     * that were triggered by players actually digging and events that were artificially created by plugins.
     * 
     * @param event
     *            the event
     */
    @EventHandler(
            ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        // debug(player, "Interact("+event.isCancelled()+"): " + event.getClickedBlock());
        // The following is to set the "first damage time" for a block.
        boolean handle = DataManager.getInstance().getPlayerData(event.getPlayer())
                .isCheckActive(CheckType.BLOCKBREAK, event.getPlayer());
        if (handle) {
            isInstaBreak = AlmostBoolean.NO;
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                checkBlockDamage(event.getPlayer(), event.getClickedBlock(), event);
            }
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onBlockDamageLowest(final BlockDamageEvent event) {
        /*
         * Potential improvement: add a check type BLOCKDAMAGE_CONFIRM (no permission)
         * that cancels if the block does not match (MC 1.11.2, other ...).
         */
        if (MovingUtil.hasScheduledPlayerSetBack(event.getPlayer())) {
            event.setCancelled(true);
        }
        else if (event.getInstaBreak()) {
            // Indicate that this might have been set by CB/MC.
            // Should be set in BlockInteractListener.
            isInstaBreak = AlmostBoolean.MAYBE;
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.MONITOR)
    public void onBlockDamage(final BlockDamageEvent event) {
        boolean handle = DataManager.getInstance().getPlayerData(event.getPlayer())
                .isCheckActive(CheckType.BLOCKBREAK, event.getPlayer());
        if (handle) {
            if (!event.isCancelled() && event.getInstaBreak()) {
                if (isInstaBreak != AlmostBoolean.MAYBE) {
                    isInstaBreak = AlmostBoolean.YES;
                }
            } else {
                isInstaBreak = AlmostBoolean.NO;
            }
            checkBlockDamage(event.getPlayer(), event.getBlock(), event);
        }
    }

    private void checkBlockDamage(final Player player, final Block block, final Cancellable event){
        final long now = Monotonic.millis();
        final IPlayerData pData = DataManager.getInstance().getPlayerData(player);
        final BlockBreakData data = pData.getGenericInstance(BlockBreakData.class);

        //        if (event.isCancelled()){
        //        	// Reset the time, to avoid certain kinds of cheating. => WHICH ?
        //        	data.fastBreakfirstDamage = now;
        //        	data.clickedX = Integer.MAX_VALUE; // Should be enough to reset that one.
        //        	return;
        //        }


        final int tick = TickTask.getTick();
        // Skip if already set to the same block without breaking within one tick difference.
        final ItemStack stack = Bridge1_9.getItemInMainHand(player);
        final Material tool = stack == null ? null: stack.getType();

        boolean record = true;
        if (data.toolChanged(tool)) {
            // Update.
        } else if (tick < data.clickedTick || now < data.fastBreakfirstDamage || now < data.fastBreakBreakTime) {
            // Time/tick ran backwards: Update.
        } else if (data.fastBreakBreakTime < data.fastBreakfirstDamage && data.clickedX == block.getX()
                && data.clickedZ == block.getZ() && data.clickedY == block.getY()) {
            if (tick - data.clickedTick <= 1) {
                record = false;
            }
        }

        if (record) {
            data.setClickedBlock(block, tick, now, tool);
            if (pData.isDebugActive(CheckType.BLOCKBREAK)) {
                BlockInteractListener.debugBlockVSBlockInteract(player, this.checkType,
                        block, "checkBlockDamage", Action.LEFT_CLICK_BLOCK, pData);
            }
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.MONITOR)
    public void onItemHeld(final PlayerItemHeldEvent event) {
        boolean handle = DataManager.getInstance().getPlayerData(event.getPlayer())
                .isCheckActive(CheckType.BLOCKBREAK, event.getPlayer());
        if (handle) {
            final Player player = event.getPlayer();
            final BlockBreakData data = DataManager.getInstance().getPlayerData(player)
                    .getGenericInstance(BlockBreakData.class);
            if (data.toolChanged(player.getInventory().getItem(event.getNewSlot()))) {
                data.resetClickedBlock();
            }
        }
    }

}
