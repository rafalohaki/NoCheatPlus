package fr.neatmonster.nocheatplus.compat;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.entity.Player;
import org.junit.Assume;
import org.junit.Test;

public class Bridge1_9Test {

    @Test
    public void testGetUsedItemInteractEventNullHand() {
        Assume.assumeTrue(Bridge1_9.hasGetItemInOffHand());
        Player player = mock(Player.class);
        PlayerInventory inv = mock(PlayerInventory.class);
        ItemStack main = new ItemStack(Material.STONE);
        when(player.getInventory()).thenReturn(inv);
        when(inv.getItemInMainHand()).thenReturn(main);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getHand()).thenReturn(null);
        assertSame(main, Bridge1_9.getUsedItem(player, event));
    }

    @Test
    public void testGetUsedItemInteractEntityEventNullHand() {
        Assume.assumeTrue(Bridge1_9.hasGetItemInOffHand());
        Player player = mock(Player.class);
        PlayerInventory inv = mock(PlayerInventory.class);
        ItemStack main = new ItemStack(Material.STONE);
        when(player.getInventory()).thenReturn(inv);
        when(inv.getItemInMainHand()).thenReturn(main);
        PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
        when(event.getHand()).thenReturn(null);
        assertSame(main, Bridge1_9.getUsedItem(player, event));
    }
}
