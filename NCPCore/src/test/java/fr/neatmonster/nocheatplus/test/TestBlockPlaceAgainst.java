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
package fr.neatmonster.nocheatplus.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import fr.neatmonster.nocheatplus.actions.ActionList;
// We need the Check class to access its static field.
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.blockinteract.BlockInteractData;
import fr.neatmonster.nocheatplus.checks.blockplace.Against;
import fr.neatmonster.nocheatplus.checks.blockplace.BlockPlaceConfig;
import fr.neatmonster.nocheatplus.checks.blockplace.BlockPlaceData;
import fr.neatmonster.nocheatplus.compat.BridgeMaterial;
import fr.neatmonster.nocheatplus.permissions.PermissionRegistry;
import fr.neatmonster.nocheatplus.permissions.RegisteredPermission;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.players.PlayerDataManager;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.worlds.WorldDataManager;

public class TestBlockPlaceAgainst {

    private static class TestableAgainst extends Against {
        @Override
        public ViolationData executeActions(ViolationData violationData) {
            violationData.forceCancel();
            return violationData;
        }
    }

    private BlockPlaceConfig config;
    private BlockPlaceData data;
    private Against against;

    @BeforeEach
    public void setup() throws Exception {
        // Use Unsafe to initialize the config object for the test
        sun.misc.Unsafe unsafe;
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        unsafe = (sun.misc.Unsafe) uf.get(null);

        config = (BlockPlaceConfig) unsafe.allocateInstance(BlockPlaceConfig.class);
        Field f = BlockPlaceConfig.class.getDeclaredField("againstActions");
        f.setAccessible(true);
        f.set(config, new ActionList(null));

        data = new BlockPlaceData();
        
        // ✅ FINAL FIX: The 'Check' class has a static 'dataManager' field that is null during tests.
        // We must create a valid PlayerDataManager and use reflection to inject it into this static field
        // before any Check (like 'Against') is instantiated.
        
        // 1. Create dependencies for PlayerDataManager.
        WorldDataManager wdm = mock(WorldDataManager.class);
        PermissionRegistry pr = mock(PermissionRegistry.class);

        // 2. Create the real PlayerDataManager instance without invoking the constructor.
        PlayerDataManager realDataManager = (PlayerDataManager) unsafe.allocateInstance(PlayerDataManager.class);
        Field execHist = PlayerDataManager.class.getDeclaredField("executionHistories");
        execHist.setAccessible(true);
        execHist.set(realDataManager, new java.util.HashMap<>());

        // 3. Set the static DataManager instance.
        new DataManager(realDataManager);
        
        // 4. Now that the static dependency is met, we can safely create the check.
        this.against = new TestableAgainst();
    }

    private boolean runCheck(Material placedMat, Material baseMat, Material belowMat,
            Material aboveMat) {
        Block placed = mock(Block.class);
        Block below = mock(Block.class);
        Block above = mock(Block.class);
        when(placed.getRelative(BlockFace.DOWN)).thenReturn(below);
        when(placed.getRelative(BlockFace.UP)).thenReturn(above);
        when(below.getType()).thenReturn(belowMat);
        when(above.getType()).thenReturn(aboveMat);

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

        try (MockedStatic<fr.neatmonster.nocheatplus.utilities.map.BlockProperties> bpMock =
                mockStatic(fr.neatmonster.nocheatplus.utilities.map.BlockProperties.class)) {
            bpMock.when(() -> fr.neatmonster.nocheatplus.utilities.map.BlockProperties.isLiquid(any(Material.class)))
                  .thenAnswer(inv -> inv.getArgument(0) == Material.WATER);
            bpMock.when(() -> fr.neatmonster.nocheatplus.utilities.map.BlockProperties.isWaterPlant(any(Material.class)))
                  .thenReturn(false);
            bpMock.when(() -> fr.neatmonster.nocheatplus.utilities.map.BlockProperties.isAir(any(Material.class)))
                  .thenAnswer(inv -> inv.getArgument(0) == Material.AIR);

            return against.check(player, placed, placedMat, againstBlock, isInteract, data, config, pData);
        }
    }

    @Test
    public void testLilyPadShallowWaterAllowed() {
        boolean result = runCheck(BridgeMaterial.LILY_PAD, Material.WATER, Material.DIRT, Material.AIR);
        assertFalse(result);
        assertEquals(0.0, data.againstVL, 0.0001);
    }

    @Test
    public void testFrogspawnShallowWaterAllowed() {
        boolean result = runCheck(BridgeMaterial.FROGSPAWN, Material.WATER, Material.STONE, Material.AIR);
        assertFalse(result);
        assertEquals(0.0, data.againstVL, 0.0001);
    }

}