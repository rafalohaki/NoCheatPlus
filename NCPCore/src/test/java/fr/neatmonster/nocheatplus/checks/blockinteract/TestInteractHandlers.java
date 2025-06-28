package fr.neatmonster.nocheatplus.checks.blockinteract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.lang.reflect.Method;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import fr.neatmonster.nocheatplus.checks.combined.CombinedConfig;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;

public class TestInteractHandlers {

    private sun.misc.Unsafe unsafe;

    @BeforeEach
    public void setup() throws Exception {
        java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        unsafe = (sun.misc.Unsafe) f.get(null);
    }

    private BlockInteractListener createListener() throws Exception {
        return (BlockInteractListener) unsafe.allocateInstance(BlockInteractListener.class);
    }

    private CombinedConfig createConfig() throws Exception {
        CombinedConfig cfg = (CombinedConfig) unsafe.allocateInstance(CombinedConfig.class);
        java.lang.reflect.Field f;
        f = CombinedConfig.class.getDeclaredField("enderPearlCheck");
        f.setAccessible(true);
        f.setBoolean(cfg, true);
        f = CombinedConfig.class.getDeclaredField("enderPearlPreventClickBlock");
        f.setAccessible(true);
        f.setBoolean(cfg, true);
        return cfg;
    }

    private static Object invoke(Method m, Object instance, Object... args) throws Exception {
        m.setAccessible(true);
        return m.invoke(instance, args);
    }

    @Test
    public void testHandleRightClickAir() throws Exception {
        BlockInteractListener listener = createListener();
        Method m = BlockInteractListener.class.getDeclaredMethod("handleRightClickAir", boolean.class);
        BlockInteractListener.ActionResult ar = (BlockInteractListener.ActionResult) invoke(m, listener, true);
        assertNull(ar.stack());
        assertTrue(ar.blockChecks());
    }

    @Test
    public void testHandleLeftClickAir() throws Exception {
        BlockInteractListener listener = createListener();
        Method m = BlockInteractListener.class.getDeclaredMethod("handleLeftClickAir", boolean.class);
        BlockInteractListener.ActionResult ar = (BlockInteractListener.ActionResult) invoke(m, listener, false);
        assertNull(ar.stack());
        assertFalse(ar.blockChecks());
    }

    @Test
    public void testHandleLeftClickBlock() throws Exception {
        BlockInteractListener listener = createListener();
        Method m = BlockInteractListener.class.getDeclaredMethod("handleLeftClickBlock", boolean.class);
        BlockInteractListener.ActionResult ar = (BlockInteractListener.ActionResult) invoke(m, listener, true);
        assertNull(ar.stack());
        assertTrue(ar.blockChecks());
    }

    @Test
    public void testHandleRightClickBlockScaffolding() throws Exception {
        BlockInteractListener listener = createListener();
        Method m = BlockInteractListener.class.getDeclaredMethod("handleRightClickBlock", Player.class,
                org.bukkit.block.Block.class, BlockFace.class, PlayerInteractEvent.class, int.class,
                BlockInteractData.class, IPlayerData.class, boolean.class);
        Player player = mock(Player.class);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        BlockInteractData data = new BlockInteractData();
        IPlayerData pData = mock(IPlayerData.class);
        when(pData.getGenericInstance(CombinedConfig.class)).thenReturn(createConfig());
        ItemStack stack = new ItemStack(Material.SCAFFOLDING);
        try (MockedStatic<Bridge1_9> b = mockStatic(Bridge1_9.class); MockedStatic<BlockProperties> bp = mockStatic(BlockProperties.class)) {
            b.when(() -> Bridge1_9.getUsedItem(player, event)).thenReturn(stack);
            bp.when(() -> BlockProperties.isScaffolding(Material.SCAFFOLDING)).thenReturn(true);
            BlockInteractListener.ActionResult ar = (BlockInteractListener.ActionResult) invoke(m, listener, player,
                    null, BlockFace.UP, event, 0, data, pData, true);
            assertEquals(stack, ar.stack());
            assertFalse(ar.blockChecks());
        }
    }

    @Test
    public void testHandleRightClickBlockEnderPearl() throws Exception {
        BlockInteractListener listener = createListener();
        Method m = BlockInteractListener.class.getDeclaredMethod("handleRightClickBlock", Player.class,
                org.bukkit.block.Block.class, BlockFace.class, PlayerInteractEvent.class, int.class,
                BlockInteractData.class, IPlayerData.class, boolean.class);
        Player player = mock(Player.class);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.useItemInHand()).thenReturn(Result.DENY);
        BlockInteractData data = new BlockInteractData();
        IPlayerData pData = mock(IPlayerData.class);
        when(pData.getGenericInstance(CombinedConfig.class)).thenReturn(createConfig());
        ItemStack stack = new ItemStack(Material.ENDER_PEARL);
        try (MockedStatic<Bridge1_9> b = mockStatic(Bridge1_9.class)) {
            b.when(() -> Bridge1_9.getUsedItem(player, event)).thenReturn(stack);
            BlockInteractListener.ActionResult ar = (BlockInteractListener.ActionResult) invoke(m, listener, player,
                    null, BlockFace.UP, event, 0, data, pData, true);
            assertEquals(stack, ar.stack());
            verify(event).setUseItemInHand(Result.DENY);
            assertTrue(ar.blockChecks());
        }
    }
}
