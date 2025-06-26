package fr.neatmonster.nocheatplus.test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.junit.Before;
import org.junit.Test;

import fr.neatmonster.nocheatplus.checks.blockinteract.BlockInteractData;
import fr.neatmonster.nocheatplus.checks.blockplace.Against;
import fr.neatmonster.nocheatplus.checks.blockplace.BlockPlaceConfig;
import fr.neatmonster.nocheatplus.checks.blockplace.BlockPlaceData;
import fr.neatmonster.nocheatplus.compat.BridgeMaterial;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.permissions.RegisteredPermission;

public class TestBlockPlaceAgainst {

    private static class TestableAgainst extends Against {
        @Override
        public fr.neatmonster.nocheatplus.checks.ViolationData executeActions(fr.neatmonster.nocheatplus.checks.ViolationData violationData) {
            violationData.forceCancel();
            return violationData;
        }
    }

    private BlockPlaceConfig config;
    private BlockPlaceData data;
    private Against against;

    @Before
    public void setup() throws Exception {
        sun.misc.Unsafe unsafe;
        java.lang.reflect.Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        unsafe = (sun.misc.Unsafe) uf.get(null);
        config = (BlockPlaceConfig) unsafe.allocateInstance(BlockPlaceConfig.class);
        java.lang.reflect.Field f = BlockPlaceConfig.class.getDeclaredField("againstActions");
        f.setAccessible(true);
        f.set(config, new fr.neatmonster.nocheatplus.actions.ActionList(null));
        data = new BlockPlaceData();
        against = new TestableAgainst();
    }

    private boolean runCheck(Material placedMat, Material baseMat) {
        Block placed = mock(Block.class);
        Block below = mock(Block.class);
        when(placed.getRelative(BlockFace.DOWN)).thenReturn(below);
        when(below.getType()).thenReturn(baseMat);

        Block againstBlock = mock(Block.class);
        when(againstBlock.getType()).thenReturn(baseMat);
        when(againstBlock.getX()).thenReturn(0);
        when(againstBlock.getY()).thenReturn(0);
        when(againstBlock.getZ()).thenReturn(0);

        Player player = mock(Player.class);
        BlockInteractData biData = new BlockInteractData();
        biData.setLastBlock(againstBlock, Action.RIGHT_CLICK_BLOCK);
        biData.setLastIsCancelled(false);

        IPlayerData pData = mock(IPlayerData.class);
        when(pData.getGenericInstance(BlockInteractData.class)).thenReturn(biData);
        when(pData.hasPermission(any(RegisteredPermission.class), any(Player.class))).thenReturn(false);
        when(pData.isDebugActive(any())).thenReturn(false);

        boolean isInteract = !biData.getLastIsCancelled() && biData.matchesLastBlock(againstBlock);
        try (org.mockito.MockedStatic<fr.neatmonster.nocheatplus.utilities.map.BlockProperties> bp = org.mockito.Mockito.mockStatic(fr.neatmonster.nocheatplus.utilities.map.BlockProperties.class)) {
            bp.when(() -> fr.neatmonster.nocheatplus.utilities.map.BlockProperties.isLiquid(any(Material.class))).thenAnswer(inv -> ((Material) inv.getArgument(0)) == Material.WATER);
            bp.when(() -> fr.neatmonster.nocheatplus.utilities.map.BlockProperties.isWaterPlant(any(Material.class))).thenReturn(false);
            bp.when(() -> fr.neatmonster.nocheatplus.utilities.map.BlockProperties.isAir(any(Material.class))).thenReturn(false);
            return against.check(player, placed, placedMat, againstBlock, isInteract, data, config, pData);
        }
    }

    @Test
    public void testLilyPadOnWaterAllowed() {
        assertFalse(runCheck(BridgeMaterial.LILY_PAD, Material.WATER));
    }

    @Test
    public void testFrogspawnOnWaterAllowed() {
        assertFalse(runCheck(BridgeMaterial.FROGSPAWN, Material.WATER));
    }

    @Test
    public void testStoneOnWaterDenied() {
        assertTrue(runCheck(Material.STONE, Material.WATER));
    }
}
