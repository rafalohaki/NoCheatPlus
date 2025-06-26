package fr.neatmonster.nocheatplus.checks.blockplace;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.junit.Before;
import org.junit.Test;

import fr.neatmonster.nocheatplus.actions.ActionList;
import fr.neatmonster.nocheatplus.checks.blockinteract.BlockInteractData;
import fr.neatmonster.nocheatplus.checks.access.ACheckConfig;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.PlayerDataManager;

public class TestAgainstCheck {
    @Before
    public void initAPI() throws Exception {
        Object api = java.lang.reflect.Proxy.newProxyInstance(
                NCPAPIProvider.class.getClassLoader(),
                new Class<?>[]{fr.neatmonster.nocheatplus.components.NoCheatPlusAPI.class},
                (p, m, a) -> null);
        java.lang.reflect.Field f = NCPAPIProvider.class.getDeclaredField("noCheatPlusAPI");
        f.setAccessible(true);
        f.set(null, api);
        sun.misc.Unsafe u = getUnsafe();
        Object pdm = u.allocateInstance(PlayerDataManager.class);
        java.lang.reflect.Field eh = PlayerDataManager.class.getDeclaredField("executionHistories");
        eh.setAccessible(true);
        eh.set(pdm, new java.util.HashMap<>());
        java.lang.reflect.Field dm = DataManager.class.getDeclaredField("instance");
        dm.setAccessible(true);
        dm.set(null, pdm);
    }

    static class DummyAgainst extends Against {
        @Override
        public ViolationData executeActions(ViolationData violationData) {
            return violationData;
        }
    }

    private Against check;
    private Player player;
    private Block block;
    private Block blockAgainst;
    private BlockPlaceData data;
    private BlockPlaceConfig config;
    private IPlayerData pData;
    private BlockInteractData biData;

    private static sun.misc.Unsafe getUnsafe() throws Exception {
        java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (sun.misc.Unsafe) f.get(null);
    }

    private static BlockPlaceConfig createConfig() {
        try {
            sun.misc.Unsafe u = getUnsafe();
            BlockPlaceConfig cfg = (BlockPlaceConfig) u.allocateInstance(BlockPlaceConfig.class);
            java.lang.reflect.Field wf = ACheckConfig.class.getDeclaredField("worldData");
            wf.setAccessible(true);
            wf.set(cfg, mock(fr.neatmonster.nocheatplus.worlds.IWorldData.class));
            java.lang.reflect.Field af = BlockPlaceConfig.class.getDeclaredField("againstActions");
            af.setAccessible(true);
            ActionList al = new ActionList(null);
            al.setActions(0, new fr.neatmonster.nocheatplus.actions.types.CancelAction[ ]{ new fr.neatmonster.nocheatplus.actions.types.CancelAction<>() });
            af.set(cfg, al);
            return cfg;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Before
    public void setup() {
        check = new DummyAgainst();
        player = mock(Player.class);
        block = mock(Block.class);
        blockAgainst = mock(Block.class);
        data = new BlockPlaceData();
        config = createConfig();
        pData = mock(IPlayerData.class);
        biData = new BlockInteractData();
        when(pData.getGenericInstance(BlockInteractData.class)).thenReturn(biData);
        when(pData.hasBypass(any(), any())).thenReturn(false);
        when(pData.isExempted(any())).thenReturn(false);
        when(pData.isDebugActive(any())).thenReturn(false);
        when(pData.hasPermission(any(), any())).thenReturn(false);
        when(block.getRelative(BlockFace.DOWN)).thenReturn(block);
    }

    @Test
    public void testConsumedCheckViolation() {
        biData.addConsumedCheck(check.getType());
        check.check(player, block, Material.DIRT, blockAgainst, false, data, config, pData);
        assertTrue(data.againstVL > 0.0);
    }

    @Test
    public void testInteractBlockAllowed() {
        when(blockAgainst.getType()).thenReturn(Material.STONE);
        biData.setLastBlock(blockAgainst, null);
        check.check(player, block, Material.DIRT, blockAgainst, true, data, config, pData);
        assertEquals(0.0, data.againstVL, 0.0001);
    }

    @Test
    public void testAirCaseViolation() {
        when(blockAgainst.getType()).thenReturn(Material.AIR);
        check.check(player, block, Material.DIRT, blockAgainst, false, data, config, pData);
        assertTrue(data.againstVL > 0.0);
    }

}
