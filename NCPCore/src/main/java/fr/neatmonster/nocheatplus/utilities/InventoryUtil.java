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
package fr.neatmonster.nocheatplus.utilities;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.checks.inventory.InventoryData;

// TODO: Auto-generated Javadoc
/**
 * Auxiliary/convenience methods for inventories.
 * @author asofold
 *
 */
public class InventoryUtil {

    private static final Map<String, InventoryType> all = new HashMap<String, InventoryType>();
    static {
        for (InventoryType type : InventoryType.values()) {
            String name = type.name().toLowerCase(Locale.ROOT);
            all.put(name, type);
        }
    }

    public static InventoryType get(String name) {
        return all.get(name.toLowerCase());
    }

    public static final Set<InventoryType> CONTAINER_LIST = Collections.unmodifiableSet(
            getAll("chest", "ender_chest", "dispenser", "dropper", "hopper", "barrel", "shulker_box", "chiseled_bookshelf")
            );

    public static Set<InventoryType> getAll(String... names) {
        final LinkedHashSet<InventoryType> res = new LinkedHashSet<InventoryType>();
        for (final String name : names) {
            final InventoryType mat = get(name);
            if (mat != null) {
                res.add(mat);
            }
        }
        return res;
    }

    /**
     * Collect non-block items by suffix of their Material name (case insensitive).
     * @param suffix
     * @return
     */
    public static List<Material> collectItemsBySuffix(String suffix) {
        suffix = suffix.toLowerCase();
        final List<Material> res = new LinkedList<Material>();
        for (final Material mat : Material.values()) {
            if (!mat.isBlock() && mat.name().toLowerCase().endsWith(suffix)) {
                res.add(mat);
            }
        }
        return res;
    }

    /**
     * Collect non-block items by suffix of their Material name (case insensitive).
     * @param prefix
     * @return
     */
    public static List<Material> collectItemsByPrefix(String prefix) {
        prefix = prefix.toLowerCase();
        final List<Material> res = new LinkedList<Material>();
        for (final Material mat : Material.values()) {
            if (!mat.isBlock() && mat.name().toLowerCase().startsWith(prefix)) {
                res.add(mat);
            }
        }
        return res;
    }

    /**
     * Does not account for special slots like armor.
     *
     * @param inventory
     *            the inventory
     * @return the free slots
     */
    public static int getFreeSlots(final Inventory inventory) {
        final ItemStack[] contents = inventory.getContents();
        int count = 0;
        for (ItemStack content : contents) {
            if (BlockProperties.isAir(content)) {
                count ++;
            }
        }
        return count;
    }

    /**
     * Count slots with type-id and data (enchantments and other meta data are
     * ignored at present).
     *
     * @param inventory
     *            the inventory
     * @param reference
     *            the reference
     * @return the stack count
     */
    public static int getStackCount(final Inventory inventory, final ItemStack reference) {
        if (inventory == null) return 0;
        if (reference == null) return getFreeSlots(inventory);
        final Material mat = reference.getType();
        // Use modern ItemMeta API instead of deprecated getDurability()
        final int durability = (reference.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable) 
            ? ((org.bukkit.inventory.meta.Damageable) reference.getItemMeta()).getDamage() 
            : reference.getDurability(); // Fallback for older items
        final ItemStack[] contents = inventory.getContents();
        int count = 0;
        for (final ItemStack stack : contents) {
            if (stack == null) {
                continue;
            }
            else if (stack.getType() == mat && getDamage(stack) == durability) {
                count ++;
            }
        }
        return count;
    }

    /**
     * Sum of bottom + top inventory slots with item type / data, see:
     * getStackCount(Inventory, reference).
     *
     * @param view
     *            the view
     * @param reference
     *            the reference
     * @return the stack count
     */
    public static int getStackCount(final InventoryView view, final ItemStack reference) {
        return getStackCount(view.getBottomInventory(), reference) + getStackCount(view.getTopInventory(), reference);
    }

    //    /**
    //     * Search for players / passengers (broken by name: closes the inventory of
    //     * first player found including entity and passengers recursively).
    //     *
    //     * @param entity
    //     *            the entity
    //     * @return true, if successful
    //     */
    //    public static boolean closePlayerInventoryRecursively(Entity entity) {
    //        // Find a player.
    //        final Player player = PassengerUtil.getFirstPlayerIncludingPassengersRecursively(entity);
    //        if (player != null && closeOpenInventory((Player) entity)) {
    //            return true;
    //        } else {
    //            return false;
    //        }
    //    }

    /**
     * Close one players inventory, if open. This might ignore
     * InventoryType.CRAFTING (see: hasInventoryOpen).
     *
     * @param player
     *            the player
     * @return If closed.
     */
    public static boolean closeOpenInventory(final Player player) {
        if (hasInventoryOpen(player) || hasAnyInventoryOpen(player)) {
            player.closeInventory();
            return true;
        } else {
            return true;
        }
    }

    /**
     * Check if the player's inventory is open by looking up the InventoryView type,
     * excluding InventoryType.CRAFTING due to the player not sending any packet for their own.
     *
     * @param player
     *            the player
     * @return true, if successful
     */
    public static boolean hasInventoryOpen(final Player player) {
        final InventoryView view = player.getOpenInventory();
        return view != null && view.getType() != InventoryType.CRAFTING;
    }

   /**
    * Check if the player's inventory is open (including their own) by
    * looking up the first time an inventory click was registered. Resets once
    * we receive an InventoryCloseEvent (which the player sends for their own inventory).
    * 
    * @param player
    *            the player
    * @return true, if successful
    */
    public static boolean hasAnyInventoryOpen(final Player player) {
        final IPlayerData pData = DataManager.getPlayerData(player);
        final InventoryData iData = pData.getGenericInstance(InventoryData.class);
        return iData.firstClickTime != 0;
    }
    
   /**
    * Test if players have recently opened an inventory.
    * Rather meant to check if they opened their own.
    * 
    * @param player
    * @param timeAge In milliseconds to be considered as 'recent activity'
    * @return True if the player has had recent inventory activity, 
    *         false if they've been in their own inventory for some time (beyond age).
    */
    public static boolean hasOpenedInvRecently(final Player player, final long timeAge) {
        final long now = System.currentTimeMillis();
        final IPlayerData pData = DataManager.getPlayerData(player);
        final InventoryData iData = pData.getGenericInstance(InventoryData.class);
        return iData.firstClickTime != 0 && (now - iData.firstClickTime <= timeAge);     
    }
    
   /**
    * Test if the player has recently interacted with an inventory that's a container type.
    * 
    * @param player
    * @param timeAge In milliseconds between the BLOCK interaction and inventory click 
    *                to be considered as 'recent activity' (Excluded)
    * @return true if the time between interaction and inventory click is too recent, false otherwise (beyond age).
    */
    public static boolean hasOpenedContainerRecently(final Player player, final long timeAge) {
        final IPlayerData pData = DataManager.getPlayerData(player);
        final InventoryData iData = pData.getGenericInstance(InventoryData.class);
        return 
                // This represents an error, will need to investigate why the times get set to 0.
                (iData.containerOpenTime != 0 || iData.lastClickTime != 0) 
                && Math.abs(iData.lastClickTime - iData.containerOpenTime) < timeAge;

    }

    /**
     * Return the first consumable item found, checking main hand first and then
     * off hand, if available. Concerns food/edible, potions, milk bucket.
     *
     * @param player
     *            the player
     * @return null in case no item is consumable.
     */
    public static ItemStack getFirstConsumableItemInHand(final Player player) {
        ItemStack actualStack = Bridge1_9.getItemInMainHand(player);
        if (
                Bridge1_9.hasGetItemInOffHand()
                && (actualStack == null || !InventoryUtil.isConsumable(actualStack.getType()))
                ) {
            // Assume this to make sense.
            actualStack = Bridge1_9.getItemInOffHand(player);
            if (actualStack == null || !InventoryUtil.isConsumable(actualStack.getType())) {
                actualStack = null;
            }
        }
        return actualStack;
    }

    /**
     * Test if the item is consumable, like food, potions, milk bucket.
     *
     * @param stack
     *            May be null.
     * @return true, if is consumable
     */
    public static boolean isConsumable(final ItemStack stack) {
        return stack == null ? false : isConsumable(stack.getType());
    }

    /**
     * Test if the inventory type can hold items.
     *
     * @param stack
     *            May be null.
     * @return true, if is container
     */
    public static boolean isContainterInventory(final InventoryType type) {
        if (type == null) {
            return false;
        }
        return CONTAINER_LIST.contains(type);
    }

    /**
     * Test if the item is consumable, like food, potions, milk bucket.
     *
     * @param type
     *            May be null.
     * @return true, if is consumable
     */
    public static boolean isConsumable(final Material type) {
        return type != null &&
                (type.isEdible() || type == Material.POTION || type == Material.MILK_BUCKET);
    }

    /**
     * Test for max durability, only makes sense with items that can be in
     * inventory once broken, such as elytra. This method does not (yet) provide
     * legacy support. This tests for ItemStack.getDurability() >=
     * Material.getMaxDurability, so it only is suited for a context where this
     * is what you want to check for.
     * 
     * @param stack
     *            May be null, would yield true.
     * @return
     */
    public static boolean isItemBroken(final ItemStack stack) {
        if (stack == null) {
            return true;
        }
        final Material mat = stack.getType();
        return getDamage(stack) >= mat.getMaxDurability();
    }

    /**
     * Helper method to get damage/durability from ItemStack using modern API
     * with fallback to deprecated method for compatibility.
     */
    public static int getDamage(final ItemStack stack) {
        if (stack == null) return 0;
        if (stack.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable) {
            return ((org.bukkit.inventory.meta.Damageable) stack.getItemMeta()).getDamage();
        }
        // Fallback to deprecated method for compatibility
        return stack.getDurability();
    }

}
