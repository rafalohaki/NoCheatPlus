package fr.neatmonster.nocheatplus.test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import java.lang.reflect.Field;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.actions.ActionList;
import fr.neatmonster.nocheatplus.checks.combined.CombinedConfig;
import fr.neatmonster.nocheatplus.checks.combined.CombinedData;
import fr.neatmonster.nocheatplus.checks.combined.Improbable;
import fr.neatmonster.nocheatplus.permissions.RegisteredPermission;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.players.PlayerDataManager;
import fr.neatmonster.nocheatplus.worlds.IWorldData;

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

    private MockedStatic<Bukkit> bukkitMock;

    @BeforeEach
    public void setup() throws Exception {
        Field f = NCPAPIProvider.class.getDeclaredField("noCheatPlusAPI");
        f.setAccessible(true);
        f.set(null, new TestGodModeHelpers.DummyAPI());

        // Bootstrap internal DataManager
        sun.misc.Unsafe un = getUnsafe();
        PlayerDataManager pdm = (PlayerDataManager) un.allocateInstance(PlayerDataManager.class);
        Field eh = PlayerDataManager.class.getDeclaredField("executionHistories");
        eh.setAccessible(true);
        eh.set(pdm, new java.util.HashMap<>());
        new DataManager(pdm);

        // Prepare dummy Bukkit server
        PluginManager pluginManager = mock(PluginManager.class);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        server = mock(Server.class);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getConsoleSender()).thenReturn(console);
        when(server.getLogger()).thenReturn(java.util.logging.Logger.getLogger("TestServer"));
        when(server.isPrimaryThread()).thenReturn(true);

        // Mock Bukkit statics
        bukkitMock = mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getServer).thenReturn(server);
        bukkitMock.when(Bukkit::isPrimaryThread).thenReturn(true);

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

    @AfterEach
    public void teardown() {
        if (bukkitMock != null) bukkitMock.close();
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
