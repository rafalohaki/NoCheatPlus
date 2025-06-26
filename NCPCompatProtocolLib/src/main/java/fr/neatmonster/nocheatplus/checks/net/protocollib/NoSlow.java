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
package fr.neatmonster.nocheatplus.checks.net.protocollib;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.components.registry.order.RegistrationOrder.RegisterMethodWithOrder;
import fr.neatmonster.nocheatplus.event.mini.MiniListener;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.InventoryUtil;

public class NoSlow extends BaseAdapter {
    private final static String dftag = "system.nocheatplus.noslow";
    private final boolean ServerIsAtLeast1_9 = ServerVersion.compareMinecraftVersion("1.9") >= 0;
    private final static MiniListener<?>[] miniListeners = new MiniListener<?>[] {
        new MiniListener<PlayerItemConsumeEvent>() {
            @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
            @RegisterMethodWithOrder(tag = dftag)
            @Override
            public void onEvent(final PlayerItemConsumeEvent event) {
                onItemConsume(event);
            }
        },
        new MiniListener<PlayerInteractEvent>() {
            @EventHandler(priority = EventPriority.MONITOR)
            @RegisterMethodWithOrder(tag = dftag)
            @Override
            public void onEvent(final PlayerInteractEvent event) {
                onItemInteract(event);
            }
        },
        new MiniListener<InventoryOpenEvent>() {
            @EventHandler(priority = EventPriority.LOWEST)
            @RegisterMethodWithOrder(tag = dftag)
            @Override
            public void onEvent(final InventoryOpenEvent event) {
                onInventoryOpen(event);
            }
        },
        new MiniListener<PlayerItemHeldEvent>() {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            @RegisterMethodWithOrder(tag = dftag)
            @Override
            public void onEvent(final PlayerItemHeldEvent event) {
                onChangeSlot(event);
            }
        }
    };

    // Durability threshold for consumable items in legacy versions (pre-1.9),
    // values above this are considered invalid or special (e.g., custom items).
    private static final int LEGACY_CONSUMABLE_DURABILITY_THRESHOLD = 16384;
    private static int timeBetweenRL = 70;
    private static PacketType[] initPacketTypes() {
        final List<PacketType> types = new LinkedList<PacketType>(Arrays.asList(
                PacketType.Play.Client.BLOCK_DIG,
                PacketType.Play.Client.BLOCK_PLACE
                ));
        return types.toArray(new PacketType[types.size()]);
    }

    public NoSlow(Plugin plugin) {
        super(plugin, ListenerPriority.MONITOR, initPacketTypes());
        final NoCheatPlusAPI api = NCPAPIProvider.getNoCheatPlusAPI();
        for (final MiniListener<?> listener : miniListeners) {
            api.addComponent(listener, false);
        }
    }

    @Override
    public void onPacketReceiving(final PacketEvent event) {
        try {
            if (event.isPlayerTemporary()) return;
        } 
        catch(NoSuchMethodError e) {
            // Ignore.
        }

        if (event.getPacketType().equals(PacketType.Play.Client.BLOCK_DIG)) {
            handleDiggingPacket(event);
        } else {
            handleBlockPlacePacket(event);
        }
    }

    private static void onItemConsume(final PlayerItemConsumeEvent e){
        final Player p = e.getPlayer();
        
        final IPlayerData pData = DataManager.getPlayerData(p);
        final MovingData data = pData.getGenericInstance(MovingData.class);
        data.isUsingItem = false;        
    }

    private static void onInventoryOpen(final InventoryOpenEvent e){
        if (e.isCancelled()) return;
        final Player p = (Player) e.getPlayer();
        
        final IPlayerData pData = DataManager.getPlayerData(p);
        final MovingData data = pData.getGenericInstance(MovingData.class);
        data.isUsingItem = false;        
    }

    private static void onItemInteract(final PlayerInteractEvent e){
        // Note: Potential improvement - add trident support. Check for rain and whether the player is actually exposed; might not be worth doing.
        if (!isRightClick(e.getAction())) {
            return;
        }

        final Player player = e.getPlayer();
        if (player == null) {
            return;
        }

        final IPlayerData pData = DataManager.getPlayerData(player);
        if (pData == null) {
            return;
        }

        final MovingData data = pData.getGenericInstance(MovingData.class);
        data.offHandUse = false;
        if (!data.mightUseItem) {
            return;
        }
        data.mightUseItem = false;

        if (e.useItemInHand().equals(Event.Result.DENY) || player.getGameMode() == GameMode.CREATIVE) {
            data.isUsingItem = false;
            return;
        }

        boolean usingItem = false;
        if (e.hasItem()) {
            final ItemStack item = e.getItem();
            if (item != null) {
                usingItem = evaluateItemUse(player, e, item, data);
            }
        } else {
            data.isUsingItem = false;
        }

        data.isUsingItem = usingItem;
    }

    private static boolean evaluateItemUse(final Player player, final PlayerInteractEvent event,
                                           final ItemStack item, final MovingData data) {
        if (player == null || event == null || item == null || data == null) {
            return false;
        }

        final Material type = item.getType();

        if (playerHasCooldown(player, type)) {
            return false;
        }

        if (checkConsumableUse(player, event, item, data)) {
            return true;
        }

        if (isBowUsage(player, event, type, data)) {
            return true;
        }

        if (isShieldOrTrident(event, type, data)) {
            return false;
        }

        if (checkCrossbowUse(player, event, item, data)) {
            return true;
        }

        return false;
    }

    private static boolean playerHasCooldown(final Player player, final Material type) {
        return Bridge1_9.hasElytra() && player.hasCooldown(type);
    }

    private static boolean checkConsumableUse(final Player player, final PlayerInteractEvent event,
                                              final ItemStack item, final MovingData data) {
        if (!InventoryUtil.isConsumable(item)) {
            return false;
        }
        if (!Bridge1_9.hasElytra() && item.getDurability() > LEGACY_CONSUMABLE_DURABILITY_THRESHOLD) {
            return false;
        }

        final Material type = item.getType();
        if (isDrinkable(type)) {
            markOffHandUse(event, data, true);
            return true;
        }

        if (type.isEdible() && player.getFoodLevel() < 20) {
            markOffHandUse(event, data, true);
            return true;
        }

        return false;
    }

    private static boolean isDrinkable(final Material type) {
        return type == Material.POTION || type == Material.MILK_BUCKET || type.toString().endsWith("_APPLE")
                || type.name().startsWith("HONEY_BOTTLE");
    }

    private static boolean isBowUsage(final Player player, final PlayerInteractEvent event, final Material type,
                                      final MovingData data) {
        if (type == Material.BOW && hasArrow(player.getInventory(), false)) {
            markOffHandUse(event, data, true);
            return true;
        }
        return false;
    }

    private static boolean isShieldOrTrident(final PlayerInteractEvent event, final Material type,
                                             final MovingData data) {
        if (Bridge1_9.hasElytra() && type == Material.SHIELD) {
            markOffHandUse(event, data, false);
            return true;
        }
        if (Bridge1_13.hasIsRiptiding() && type == Material.TRIDENT) {
            markOffHandUse(event, data, false);
            return true;
        }
        return false;
    }

    private static boolean checkCrossbowUse(final Player player, final PlayerInteractEvent event,
                                            final ItemStack item, final MovingData data) {
        if (item.getType() != Material.CROSSBOW) {
            return false;
        }
        final ItemMeta rawMeta = item.getItemMeta();
        if (rawMeta instanceof CrossbowMeta) {
            final CrossbowMeta meta = (CrossbowMeta) rawMeta;
            if (!meta.hasChargedProjectiles() && hasArrow(player.getInventory(), true)) {
                markOffHandUse(event, data, false);
                return true;
            }
        }
        return false;
    }

    private static void markOffHandUse(final PlayerInteractEvent event, final MovingData data, final boolean requireBridge) {
        final EquipmentSlot slot = event.getHand();
        final boolean offHand = slot != null && slot == EquipmentSlot.OFF_HAND;
        data.offHandUse = (requireBridge ? Bridge1_9.hasGetItemInOffHand() : true) && offHand;
    }

    private static boolean isRightClick(final Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private static void onChangeSlot(final PlayerItemHeldEvent e) {
        final Player p = e.getPlayer();
        final IPlayerData pData = DataManager.getPlayerData(p);
        final MovingData data = pData.getGenericInstance(MovingData.class);
        //if (data.changeslot) {
        //    p.getInventory().setHeldItemSlot(data.olditemslot);
        //    data.changeslot = false;
        //}
        if (e.getPreviousSlot() != e.getNewSlot()) {
            /*if ((data.isUsingItem || p.isBlocking()) && data.playerMoves.getCurrentMove() != null) {
                p.getInventory().setHeldItemSlot(e.getPreviousSlot());
                data.invalidItemUse = true;
            }*/
            data.isUsingItem = false;
        }
    }

    private static boolean hasArrow(final PlayerInventory i, final boolean fw) {
        if (Bridge1_9.hasElytra()) {
            final Material m = i.getItemInOffHand().getType();
            return (fw && m == Material.FIREWORK_ROCKET) || m.toString().endsWith("ARROW") ||
                   i.contains(Material.ARROW) || i.contains(Material.TIPPED_ARROW) || i.contains(Material.SPECTRAL_ARROW);
        }
        return i.contains(Material.ARROW);
    }

    private void handleBlockPlacePacket(PacketEvent event) {
        final Player p = event.getPlayer();
        final IPlayerData pData = DataManager.getPlayerData(p);
        final MovingData data = pData.getGenericInstance(MovingData.class);
        final PacketContainer packet = event.getPacket();

        final StructureModifier<Integer> ints = packet.getIntegers();
        // Legacy: pre 1.9
        if (ints.size() > 0 && !ServerIsAtLeast1_9) {
            final int faceIndex = ints.read(0); // arg 3 if 1.7.10 below
            if (faceIndex <= 5) {
                data.mightUseItem = false;
                return;
            }
        }
        if (!event.isCancelled()) data.mightUseItem = true;
    }

    private void handleDiggingPacket(PacketEvent event) {
        Player p = event.getPlayer();       
        
        if (p == null) {
            counters.add(ProtocolLibComponent.idNullPlayer, 1);
            return;
        }
        final IPlayerData pData = DataManager.getPlayerDataSafe(p);
        if (pData == null) {
            StaticLog.logWarning("Failed to fetch player data with " + event.getPacketType() + " for: " + p);
            return;
        }
        final MovingData data = pData.getGenericInstance(MovingData.class);
        PlayerDigType digtype = event.getPacket().getPlayerDigTypes().read(0);
        // DROP_ALL_ITEMS when dead?
        if (digtype == PlayerDigType.DROP_ALL_ITEMS || digtype == PlayerDigType.DROP_ITEM) data.isUsingItem = false;
        
        //Advanced check
        if(digtype == PlayerDigType.RELEASE_USE_ITEM) {
            data.isUsingItem = false;
            long now = System.currentTimeMillis();
            if (data.releaseItemTime != 0) {
                if (now < data.releaseItemTime) {
                    data.releaseItemTime = now;
                    return;
                }
                if (data.releaseItemTime + timeBetweenRL > now) {
                    data.isHackingRI = true;
                }
            }
            data.releaseItemTime = now;
        }
    }

    /**
     * Set Minimum time between RELEASE_USE_ITEM packet is sent.
     * If time lower this value, A check will flag
     * Should be set from 51-100. Larger number, more protection more false-positive
     * 
     * @param milliseconds
     */ 
    public static void setuseRLThreshold(int time) {
        timeBetweenRL = time;
    }   
}
