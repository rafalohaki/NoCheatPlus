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
package fr.neatmonster.nocheatplus.checks.inventory;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import java.util.List;
import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.CheckListener;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.combined.Combined;
import fr.neatmonster.nocheatplus.checks.combined.Improbable;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.util.MovingUtil;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.BridgeHealth;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.components.data.ICheckData;
import fr.neatmonster.nocheatplus.components.data.IData;
import fr.neatmonster.nocheatplus.components.entity.IEntityAccessVehicle;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.components.registry.feature.JoinLeaveListener;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.stats.Counters;
import fr.neatmonster.nocheatplus.utilities.InventoryUtil;
import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;

/**
 * Central location to listen to events that are relevant for the inventory checks.
 * 
 * @see InventoryEvent
 */
public class InventoryListener  extends CheckListener implements JoinLeaveListener{
    
    /** Inventory Move check */
    private final InventoryMove invMove = addCheck(new InventoryMove());
    
    /** More Inventory check */
    private final MoreInventory moreInv = addCheck(new MoreInventory());

    /** The fast click check. */
    private final FastClick  fastClick  = addCheck(new FastClick());

    /** The instant bow check. */
    private final InstantBow instantBow = addCheck(new InstantBow());

    /** The instant eat check. */
    private final InstantEat instantEat = addCheck(new InstantEat());

    protected final Items items = addCheck(new Items());

    private final Open open = addCheck(new Open());
    
    private boolean keepCancel = false;

    private final boolean hasInventoryAction;

    /** For temporary use: LocUtil.clone before passing deeply, call setWorld(null) after use. */
    private final Location useLoc = new Location(null, 0, 0, 0);

    private final Counters counters = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstance(Counters.class);

    private final int idCancelDead = counters.registerKey("cancel.dead");

    private final int idIllegalItem = counters.registerKey("illegalitem");

    private final int idEggOnEntity = counters.registerKey("eggonentity");

    private final IGenericInstanceHandle<IEntityAccessVehicle> handleVehicles = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstanceHandle(IEntityAccessVehicle.class);

    @SuppressWarnings("unchecked")
    public InventoryListener() {
        super(CheckType.INVENTORY);
        final NoCheatPlusAPI api = NCPAPIProvider.getNoCheatPlusAPI();
        api.register(api.newRegistrationContext()
                // InventoryConfig
                .registerConfigWorld(InventoryConfig.class)
                .factory(arg -> new InventoryConfig(arg.worldData))
                .registerConfigTypesPlayer()
                .context() //
                // InventoryData
                .registerDataPlayer(InventoryData.class)
                .factory(arg -> new InventoryData())
                .addToGroups(CheckType.INVENTORY, true, List.of(IData.class, ICheckData.class))
                .context() //
                );
        // Move to BridgeMisc?
        hasInventoryAction = ReflectionUtil.getClass("org.bukkit.event.inventory.InventoryAction") != null;
    }

    /**
     * We listen to EntityShootBow events for the InstantBow check.
     * 
     * @param event
     *            the event
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityShootBow(final EntityShootBowEvent event) {
        
        // Only if a player shot the arrow.
        if (event.getEntity() instanceof Player) {

            final Player player = (Player) event.getEntity();
            final IPlayerData pData = DataManager.getInstance().getPlayerData(player);
                if (instantBow.isEnabled(player, pData)) {
                    final long now = System.currentTimeMillis();
                    final Location loc = player.getLocation(useLoc);
                    if (Combined.checkYawRate(player, loc.getYaw(), now, loc.getWorld().getName(), pData)) {
                        // No else if with this, could be cancelled due to other checks feeding, does not have actions.
                        event.setCancelled(true);
                    }

                    final InventoryConfig cc = pData.getGenericInstance(InventoryConfig.class);
                    // Still check instantBow, whatever yawrate says.
                    if (instantBow.check(player, event.getForce(), now)) {
                        event.setCancelled(true);
                    }
                    else if (cc.instantBowImprobableWeight > 0.0f) {
                        if (cc.instantBowImprobableFeedOnly) {
                            Improbable.feed(player, cc.instantBowImprobableWeight, now);
                        }
                        else if (Improbable.check(player, cc.instantBowImprobableWeight, now, "inventory.instantbow", pData)) {
                            // Combined fighting speed (Else if: Matter of taste, preventing extreme cascading and actions spam).
                            event.setCancelled(true);
                        }
                    }
                    useLoc.setWorld(null);
                }
        }
    }

    /**
     * We listen to FoodLevelChange events because Bukkit doesn't provide a PlayerFoodEating Event (or whatever it would
     * be called).
     * 
     * @param event
     *            the event
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onFoodLevelChange(final FoodLevelChangeEvent event) {

        // Only if a player ate food.
        if (event.getEntity() instanceof Player) {
            final Player player = (Player) event.getEntity();
            final IPlayerData pData = DataManager.getInstance().getPlayerData(player);
                if (instantEat.isEnabled(player, pData)
                        && instantEat.check(player, event.getFoodLevel())) {
                    event.setCancelled(true);
                }
                else if (player.isDead() && BridgeHealth.getHealth(player) <= 0.0) {
                    // Eat after death.
                    event.setCancelled(true);
                    counters.addPrimaryThread(idCancelDead, 1);
                }
        }
    }

    /**
     * We listen to InventoryClick events for the FastClick check.
     * 
     * @param event
     *            the event
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    
    public void onInventoryClick(final InventoryClickEvent event) {
        final HumanEntity entity = event.getWhoClicked();
        if (entity instanceof Player) {
            final Player player = (Player) entity;
            final IPlayerData pData = DataManager.getInstance().getPlayerData(player);
            if (pData.isCheckActive(CheckType.INVENTORY, player)) {
                handleInventoryClick(event, player, pData);
            }
        }
    }
    private void handleInventoryClick(final InventoryClickEvent event, final Player player, final IPlayerData pData) {
        final long now = System.currentTimeMillis();
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        final int slot = event.getSlot();
        final String action = hasInventoryAction ? event.getAction().name() : null;

        if (pData.isDebugActive(checkType)) {
            outputDebugInventoryClick(player, slot, event, action);
        }

        if (slot == InventoryView.OUTSIDE || slot < 0) {
            handleOutsideInventoryClick(data, now);
            return;
        }

        final ItemStack cursor = event.getCursor();
        final ItemStack clicked = event.getCurrentItem();

        boolean cancel = checkIllegalEnchantments(player, cursor, clicked, pData);
        cancel = cancel || checkFastClick(event, player, pData, now, slot, cursor, clicked, action, data);
        cancel = cancel || checkInventoryMove(event, player, pData, data);

        updateClickTimes(data, now);

        if (cancel || keepCancel) {
            event.setCancelled(true);
        }
    }

    private void handleOutsideInventoryClick(final InventoryData data, final long now) {
        data.lastClickTime = now;
        if (data.firstClickTime == 0) {
            data.firstClickTime = now;
        }
    }

    private boolean checkIllegalEnchantments(final Player player, final ItemStack cursor,
            final ItemStack clicked, final IPlayerData pData) {
        try {
            if (Items.checkIllegalEnchantments(player, clicked, pData)) {
                counters.addPrimaryThread(idIllegalItem, 1);
                return true;
            }
        } catch (final ArrayIndexOutOfBoundsException ignore) {
            // Safe to ignore - CraftBukkit issue where slot can sometimes be out of range
            // See: https://hub.spigotmc.org/jira/browse/SPIGOT-123
        }
        try {
            if (Items.checkIllegalEnchantments(player, cursor, pData)) {
                counters.addPrimaryThread(idIllegalItem, 1);
                return true;
            }
        } catch (final ArrayIndexOutOfBoundsException ignore) {
            // Safe to ignore - CraftBukkit issue where slot can sometimes be out of range
            // See: https://hub.spigotmc.org/jira/browse/SPIGOT-123
        }
        return false;
    }

    private boolean checkFastClick(final InventoryClickEvent event, final Player player, final IPlayerData pData,
            final long now, final int slot, final ItemStack cursor, final ItemStack clicked, final String action,
            final InventoryData data) {
        if (!fastClick.isEnabled(player, pData)) {
            return false;
        }

        final InventoryConfig cc = pData.getGenericInstance(InventoryConfig.class);
        if ((event.getView().getType().equals(InventoryType.CREATIVE) || player.getGameMode() == GameMode.CREATIVE)
                && cc.fastClickSpareCreative) {
            return false;
        }

        boolean check = true;
        try {
            check = !cc.inventoryExemptions.contains(ChatColor.stripColor(event.getView().getTitle()));
        } catch (final IllegalStateException e) {
            check = true;
        }

        boolean cancel = false;
        if (check && InventoryUtil.isContainterInventory(event.getInventory().getType())
                && fastClick.fastClickChest(player, data, cc)) {
            keepCancel = true;
            cancel = true;
        }
        if (check && fastClick.check(player, now, event.getView(), slot, cursor, clicked, event.isShiftClick(),
                action, data, cc, pData)) {
            cancel = true;
        }
        return cancel;
    }

    private boolean checkInventoryMove(final InventoryClickEvent event, final Player player, final IPlayerData pData,
            final InventoryData data) {
        return invMove.isEnabled(player, pData)
                && invMove.check(player, data, pData, pData.getGenericInstance(InventoryConfig.class),
                        event.getSlotType());
    }

    private void updateClickTimes(final InventoryData data, final long now) {
        data.lastClickTime = now;
        if (data.firstClickTime == 0) {
            data.firstClickTime = now;
        }
    }
   /** 
    * Listens for when a player closes a chest.
    * We do this to keep canceling the attempt to click within the chest if
    * fastClickChest is true.
    * Also resets firstClickTime.
    */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInventoryClose(final InventoryCloseEvent event) {
        
        final HumanEntity entity = event.getPlayer();
        if (entity instanceof Player) {

            final Player player = (Player) entity;
            final IPlayerData pData = DataManager.getInstance().getPlayerData(player);
            final InventoryData data = pData.getGenericInstance(InventoryData.class);
            data.firstClickTime = 0;
            data.containerOpenTime = 0;
        }
        keepCancel = false;
    }
    
   /** 
    * Listens for when a player opens a container.
    * We do this to compare the times between opening a container and
    * interacting with it.
    */
    @EventHandler(priority = EventPriority.MONITOR)
    public void containerOpen(PlayerInteractEvent event) {

        final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getInstance().getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);

        if (!pData.isCheckActive(CheckType.INVENTORY, player)) return;
        
        // Check left click too to prevent any bypasses
        // Set the container opening time.
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null 
            || event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
            
            // Issue (important): Sneaking and right clicking with a block in hand will 
            // cause the player to place the block down, not to open the container.
            if (BlockProperties.isContainer(event.getClickedBlock().getType())) {
               data.containerOpenTime = System.currentTimeMillis();
            }
        } 
    }

    /**
     * Debug inventory classes. Contains information about classes, to indicate
     * if cross-plugin compatibility issues can be dealt with easily. The output
     * includes the current time in milliseconds and, if available, the current
     * server tick.
     *
     * @param player
     * @param slot
     * @param event
     */
    private void outputDebugInventoryClick(final Player player, final int slot, final InventoryClickEvent event, 
                                           final String action) {

        // Check if this breaks legacy compatibility and disable there if needed.
        // Consider logging only where different from expected (CraftXY, more/other viewer than player).

        final StringBuilder builder = new StringBuilder(512);
        builder.append("Inventory click: slot: ").append(slot);
        builder.append(" , Time: ").append(System.currentTimeMillis());
        final int tick = TickTask.getTick();
        if (tick > 0) {
            builder.append(" , Tick: ").append(tick);
        }

        // Viewers.
        builder.append(" , Viewers: ");
        for (final HumanEntity entity : event.getViewers()) {
            builder.append(entity.getName());
            builder.append("(");
            builder.append(entity.getClass().getName());
            builder.append(")");
        }

        // Inventory view.
        builder.append(" , View: ");
        final InventoryView view = event.getView();
        builder.append(view.getClass().getName());

        // Bottom inventory.
        addInventory(view.getBottomInventory(), view, " , Bottom: ", builder);

        // Top inventory.
        addInventory(view.getBottomInventory(), view, " , Top: ", builder);
        
        if (action != null) {
            builder.append(" , Action: ");
            builder.append(action);
        }

        // Event class.
        builder.append(" , Event: ");
        builder.append(event.getClass().getName());

        // Log debug.
        debug(player, builder.toString());
    }

    private void addInventory(final Inventory inventory, final InventoryView view, final String prefix,
            final StringBuilder builder) {
        builder.append(prefix);
        if (inventory == null) {
            builder.append("(none)");
        }
        else {
            String name = view.getTitle();
            builder.append(name);
            builder.append("/");
            builder.append(inventory.getClass().getName());
        }
    }

    /**
     * We listen to DropItem events for the Drop check.
     * 
     * @param event
     *            the event
     */
    @EventHandler( ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerDropItem(final PlayerDropItemEvent event) {

        final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getInstance().getPlayerData(player);

        if (!pData.isCheckActive(CheckType.INVENTORY, player)) return;

        // Illegal enchantments hotfix check.
        final Item item = event.getItemDrop();
        if (item != null) {
            // No cancel here.
            Items.checkIllegalEnchantments(player, item.getItemStack(), pData);
        }
    }

    /**
     * We listen to PlayerInteract events for the InstantEat and InstantBow checks.
     * 
     * @param event
     *            the event
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public final void onPlayerInteract(final PlayerInteractEvent event) {

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            final Player player = event.getPlayer();
            final IPlayerData pData = DataManager.getInstance().getPlayerData(player);
            if (pData.isCheckActive(CheckType.INVENTORY, player)) {
                final InventoryData data = pData.getGenericInstance(InventoryData.class);

                final boolean resetAll = handleInteractItem(event, player, pData, data);
                if (resetAll) {
                    resetInteractionData(player, pData, data);
                }
            }
        }
    }

    private boolean handleInteractItem(final PlayerInteractEvent event, final Player player,
            final IPlayerData pData, final InventoryData data) {
        boolean resetAll = true;
        if (event.hasItem()) {
            final ItemStack item = event.getItem();
            if (item != null) {
                final Material type = item.getType();
                if (type == Material.BOW) {
                    rememberBowInteract(data);
                    resetAll = false;
                } else if (InventoryUtil.isConsumable(type)) {
                    rememberFoodInteract(data, type);
                    resetAll = false;
                }

                if (Items.checkIllegalEnchantments(player, item, pData)) {
                    event.setCancelled(true);
                    counters.addPrimaryThread(idIllegalItem, 1);
                }
            }
        }
        return resetAll;
    }

    private void rememberBowInteract(final InventoryData data) {
        final long now = System.currentTimeMillis();
        data.bowTracker.updateAndQualifies(now);
    }

    private void rememberFoodInteract(final InventoryData data, final Material type) {
        final long now = System.currentTimeMillis();
        if (data.eatTracker.updateAndQualifies(now)) {
            data.instantEatFood = type; // Interaction within 800 ms qualifies as fast food.
        } else {
            data.instantEatFood = null; // Too slow, clear previous food.
        }
        data.bowTracker.reset();
    }

    private void resetInteractionData(final Player player, final IPlayerData pData, final InventoryData data) {
        if (pData.isDebugActive(CheckType.INVENTORY_INSTANTEAT) && data.instantEatFood != null) {
            debug(player, "PlayerInteractEvent, reset fastconsume (legacy: instanteat).");
        }
        data.bowTracker.reset();
        data.eatTracker.reset();
        data.instantEatFood = null;
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public final void onPlayerInteractEntity(final PlayerInteractEntityEvent event) {

        final Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || !DataManager.getInstance().getPlayerData(player).isCheckActive(CheckType.INVENTORY, player)) {
            return;
        }
        if (player.isDead() && BridgeHealth.getHealth(player) <= 0.0) {
            // No zombies.
            event.setCancelled(true);
            counters.addPrimaryThread(idCancelDead, 1);
            return;
        }
        else if (MovingUtil.hasScheduledPlayerSetBack(player)) {
            event.setCancelled(true);
            return;
        }
        // Activate mob-egg check only for specific server versions (pending review).
        final ItemStack stack = Bridge1_9.getUsedItem(player, event);
        Entity entity = event.getRightClicked();
        if (stack != null &&  MaterialUtil.isSpawnEgg(stack.getType())
            && (entity == null || entity instanceof LivingEntity  || entity instanceof ComplexEntityPart)
            && items.isEnabled(player, DataManager.getInstance().getPlayerData(player))) {
            event.setCancelled(true);
            counters.addPrimaryThread(idEggOnEntity, 1);
            return;
        }
    }
    
     /**
     * We listen to InventoryOpen events because we want to force-close
     * containers if a movement setback is scheduled. Also to updated the
     * last inventory activity.
     * 
     * @param event
     *            the event
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public final void onPlayerInventoryOpen(final InventoryOpenEvent event) {

        // Possibly already prevented by block + entity interaction.
        final long now = System.currentTimeMillis();
        final HumanEntity entity = event.getPlayer();
        if (entity instanceof Player) {

            final Player player = (Player) entity;
            final IPlayerData pData = DataManager.getInstance().getPlayerData(player);
            final InventoryData data = pData.getGenericInstance(InventoryData.class);
            if (data.firstClickTime == 0) data.firstClickTime = now;
            if (MovingUtil.hasScheduledPlayerSetBack(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeldChange(final PlayerItemHeldEvent event) {

        final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getInstance().getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);

        if (!pData.isCheckActive(CheckType.INVENTORY, player)) return;

        if (pData.isDebugActive(checkType) && data.instantEatFood != null) {
            debug(player, "PlayerItemHeldEvent, reset fastconsume (legacy: instanteat).");
        }
        data.bowTracker.reset();
        data.eatTracker.setLast(System.currentTimeMillis());
        data.instantEatFood = null;

        // Illegal enchantments hotfix check.
        final PlayerInventory inv = player.getInventory();
        Items.checkIllegalEnchantments(player, inv.getItem(event.getNewSlot()), pData);
        Items.checkIllegalEnchantments(player, inv.getItem(event.getPreviousSlot()), pData);
    }
    
    /**
     * We listen to PlayetChangeWorld events because we want to reset
     * the activity time.
     * 
     * @param event
     *            the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(final PlayerChangedWorldEvent event) {

        final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getInstance().getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        open.check(event.getPlayer());
        data.firstClickTime = 0;
        data.containerOpenTime = 0;
    }
    
    /**
     * We listen to PlayerPortal events because of the Open check.
     * 
     * @param event
     *            the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerPortal(final PlayerPortalEvent event) {

        // Note: ignore cancelother setting.
        open.check(event.getPlayer());
    }
    
    /**
     * We listen to PlayerRespawn events because we want to reset
     * the activity time.
     * 
     * @param event
     *            the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(final PlayerRespawnEvent event) {

        final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getInstance().getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        data.firstClickTime = 0;
        data.containerOpenTime = 0;
    }
    
    /**
     * We listen to EntityDeath events because we want to reset
     * the activity time.
     * 
     * @param event
     *            the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(final EntityDeathEvent event) {

        final LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {

            final Player player = (Player) entity;
            final IPlayerData pData = DataManager.getInstance().getPlayerData(player);
            final InventoryData data = pData.getGenericInstance(InventoryData.class);
            open.check(player);
            data.firstClickTime = 0;
            data.containerOpenTime = 0;
        }
    }
    
    /**
     * We listen to PlayerBedEnter events because we want to reset
     * the activity time.
     * 
     * @param event
     *            the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerSleep(final PlayerBedEnterEvent event) {

        final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getInstance().getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        open.check(player);
        data.firstClickTime = 0;
        data.containerOpenTime = 0;
    }

    /**
     * We listen to PortalEnter events for the Open check and to reset
     * the last activity time.
     * 
     * @param event
     *            the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityPortal(final EntityPortalEnterEvent event) {

        // Check passengers flat for now.
        final Entity entity = event.getEntity();
        if (entity instanceof Player) {
            open.check((Player) entity);
        }
        else {
            for (final Entity passenger : handleVehicles.getHandle().getEntityPassengers(entity)) {
                if (passenger instanceof Player) {
                    // Note: ignore cancelother setting.
                    open.check((Player) passenger);
                }
            }
        }
    }
    
    /**
     * We listen to PlayerMoveEvents because we want to make sure
     * players cannot move and click/interact with their own inventory at the same time.
     * 
     * @param event
     *            the event
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(final PlayerMoveEvent event) {

        final Player player = event.getPlayer();
        final Location from = event.getFrom();
        final Location to = event.getTo();
        final boolean PoYdiff = from.getPitch() != to.getPitch() || from.getYaw() != to.getYaw();
        final IPlayerData pData = DataManager.getInstance().getPlayerData(player);

        if (!pData.isCheckActive(CheckType.INVENTORY, player)) return;

        final InventoryData iData = pData.getGenericInstance(InventoryData.class);
        final MovingData data = pData.getGenericInstance(MovingData.class);
        final Inventory inv = player.getOpenInventory().getTopInventory();
        if (moreInv.isEnabled(player, pData) 
            && moreInv.check(player, data, pData, inv.getType(), inv, PoYdiff)) {

            for (int i = 1; i <= 4; i++) {
                final ItemStack item = inv.getItem(i);
                // Ensure air-clicking is not detected... :)
                if (item != null && item.getType() != Material.AIR) {
                    // Note: dropItemsNaturally does not fire InvDrop events, simply close the inventory
                    player.closeInventory();
                    if (pData.isDebugActive(CheckType.INVENTORY_MOREINVENTORY)) {
                        debug(player, "Force-close inventory on MoreInv detection.");
                    }
                    break;
                }
            }
        }
        // Evaluate if the player is actually moving or triggered by external events.
        // Consider merging InventoryMove and MoreInventory handling.
        iData.lastMoveEvent = System.currentTimeMillis();
    }
    
    /**
     * We listen to PlayerTeleport events for the Open check and to reset
     * the last activity time.
     * 
     * @param event
     *            the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(final PlayerTeleportEvent event) {
        // Note: ignore cancelother setting.
        open.check(event.getPlayer());
        final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getInstance().getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        data.firstClickTime = 0; // Fix false positives with InvMove when teleporting.
        data.containerOpenTime = 0;
    }

    @Override
    public void playerJoins(Player player) {
        
        // Just to be sure...
        final IPlayerData pData = DataManager.getInstance().getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        data.firstClickTime = 0;
        data.containerOpenTime = 0;
    }

    @Override
    public void playerLeaves(Player player) {

        final IPlayerData pData = DataManager.getInstance().getPlayerData(player);
        final InventoryData data = pData.getGenericInstance(InventoryData.class);
        data.firstClickTime = 0;
        data.containerOpenTime = 0;
        open.check(player);
    }

    //    @EventHandler(priority = EventPriority.MONITOR)
    //    public void onVehicleDestroy(final VehicleDestroyEvent event) {
    //      final Entity entity = event.getVehicle();
    //      if (entity instanceof InventoryHolder) { // Fail on 1.4 ?
    //          checkInventoryHolder((InventoryHolder) entity);
    //      }
    //    }
    //    
    //    @EventHandler(priority = EventPriority.MONITOR)
    //    public void onBlockBreak(final BlockBreakEvent event) {
    //      final Block block = event.getBlock();
    //      if (block == null) {
    //          return;
    //      }
    //      // Handling explosions and entity-change-block events could be added here.
    //    }
    //
    //  private void checkInventoryHolder(InventoryHolder entity) {
    //      // Implementation placeholder.
    //      
    //  }

}
