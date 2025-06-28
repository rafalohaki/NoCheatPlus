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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;

import org.bukkit.entity.Player;
import org.junit.Before;
import org.junit.Test;

import fr.neatmonster.nocheatplus.actions.ActionList;
import fr.neatmonster.nocheatplus.checks.combined.CombinedConfig;
import fr.neatmonster.nocheatplus.checks.combined.CombinedData;
import fr.neatmonster.nocheatplus.checks.combined.Improbable;
import fr.neatmonster.nocheatplus.permissions.RegisteredPermission;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.worlds.IWorldData;
import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.PlayerDataManager;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.Bukkit;

public class TestImprobable {

    private static sun.misc.Unsafe getUnsafe() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (sun.misc.Unsafe) f.get(null);
    }

    private CombinedConfig createConfig(float level) throws Exception {
        sun.misc.Unsafe u = getUnsafe();
        CombinedConfig cc = (CombinedConfig) u.allocateInstance(CombinedConfig.class);
        Field wf = CombinedConfig.class.getSuperclass().getDeclaredField("worldData");
        wf.setAccessible(true);
        wf.set(cc, mock(IWorldData.class));
        Field lf = CombinedConfig.class.getDeclaredField("improbableLevel");
        lf.setAccessible(true);
        lf.setFloat(cc, level);
        Field af = CombinedConfig.class.getDeclaredField("improbableActions");
        af.setAccessible(true);
        af.set(cc, new ActionList(new RegisteredPermission(1, "dummy")));
        return cc;
    }

    private IPlayerData pData;
    private CombinedData data;
    private CombinedConfig config;
    private Player player;
    private IWorldData worldData;
    private Server server;
    private Server previousServer;

    @Before
    public void setup() throws Exception {
        java.lang.reflect.Field f = NCPAPIProvider.class.getDeclaredField("noCheatPlusAPI");
        f.setAccessible(true);
        f.set(null, new TestGodModeHelpers.DummyAPI());
        sun.misc.Unsafe un = getUnsafe();
        PlayerDataManager pdm = (PlayerDataManager) un.allocateInstance(PlayerDataManager.class);
        java.lang.reflect.Field eh = PlayerDataManager.class.getDeclaredField("executionHistories");
        eh.setAccessible(true);
        eh.set(pdm, new java.util.HashMap<>());
        new DataManager(pdm);
        PluginManager pluginManager = mock(PluginManager.class);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        server = (Server) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Server.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getPluginManager": return pluginManager;
                        case "getConsoleSender": return console;
                        case "getLogger": return java.util.logging.Logger.getLogger("TestServer");
                        case "isPrimaryThread": return true;
                        default: return null;
                    }
                });
        previousServer = Bukkit.getServer();
        if (previousServer == null) {
            Bukkit.setServer(server);
        } else {
            server = previousServer;
        }
        player = mock(Player.class);
        worldData = mock(IWorldData.class);
        when(worldData.shouldAdjustToLag(any())).thenReturn(false);
        data = new CombinedData();
        config = createConfig(1f);
        pData = mock(IPlayerData.class);
        when(pData.isCheckActive(any(), any())).thenReturn(true);
        when(pData.getGenericInstance(CombinedData.class)).thenReturn(data);
        when(pData.getGenericInstance(CombinedConfig.class)).thenReturn(config);
        when(pData.getCurrentWorldData()).thenReturn(worldData);
        new Improbable();
    }

    @org.junit.After
    public void teardown() {
        if (previousServer != null && Bukkit.getServer() != previousServer) {
            Bukkit.setServer(previousServer);
        }
    }

    @Test
    public void testFeedAndCheckOnly() {
        long now = System.currentTimeMillis();
        Improbable.feed(player, 1f, now, pData);
        Improbable.checkOnly(player, now, "test", pData);
        assertTrue(data.improbableVL > 0d);
    }

    @Test
    public void testCheckFeedsAndEvaluates() {
        long now = System.currentTimeMillis();
        Improbable.check(player, 1f, now, "test", pData);
        assertTrue(data.improbableCount.bucketScore(0) > 0f);
        assertTrue(data.improbableVL > 0d);
    }
}
