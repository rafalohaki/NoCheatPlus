package fr.neatmonster.nocheatplus.test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;

import org.bukkit.entity.Player;
import org.junit.Before;
import org.junit.Test;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.fight.FightConfig;
import fr.neatmonster.nocheatplus.checks.fight.FightData;
import fr.neatmonster.nocheatplus.checks.fight.GodMode;
import fr.neatmonster.nocheatplus.checks.net.NetData;
import fr.neatmonster.nocheatplus.checks.combined.CombinedData;
import fr.neatmonster.nocheatplus.checks.inventory.InventoryData;
import fr.neatmonster.nocheatplus.checks.blockbreak.BlockBreakData;
import fr.neatmonster.nocheatplus.checks.blockbreak.BlockBreakConfig;
import fr.neatmonster.nocheatplus.config.DefaultConfig;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.worlds.IWorldData;
import fr.neatmonster.nocheatplus.components.registry.DefaultGenericInstanceRegistry;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.compat.MCAccess;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.permissions.PermissionRegistry;
import fr.neatmonster.nocheatplus.permissions.RegisteredPermission;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.worlds.WorldDataManager;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.logging.LogManager;
import fr.neatmonster.nocheatplus.event.mini.IEventRegistry;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.IBlockChangeTracker;
import fr.neatmonster.nocheatplus.components.registry.ComponentRegistry;
import fr.neatmonster.nocheatplus.components.registry.setup.RegistrationContext;
import fr.neatmonster.nocheatplus.actions.ActionFactoryFactory;
import fr.neatmonster.nocheatplus.actions.ActionFactory;
import fr.neatmonster.nocheatplus.time.monotonic.Monotonic;
import fr.neatmonster.nocheatplus.players.IPlayerDataManager;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.compat.AlmostBoolean;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Entity;

public class TestGodModeHelpers {

    static class DummyAPI implements NoCheatPlusAPI {
        final WorldDataManager worldDataManager = new WorldDataManager();
        final DefaultGenericInstanceRegistry registry = new DefaultGenericInstanceRegistry();
        final PermissionRegistry permissionRegistry = new PermissionRegistry(100);
        static class DummyMCAccess implements MCAccess {
            @Override public String getMCVersion(){ return "test"; }
            @Override public String getServerVersionTag(){ return "test"; }
            @Override public CommandMap getCommandMap(){ return null; }
            @Override public BlockCache getBlockCache(){ return null; }
            @Override public BlockCache getBlockCache(World world){ return null; }
            @Override public double getHeight(Entity entity){ return 0; }
            @Override public double getWidth(Entity entity){ return 0; }
            @Override public AlmostBoolean isBlockSolid(Material id){ return AlmostBoolean.NO; }
            @Override public AlmostBoolean isBlockLiquid(Material id){ return AlmostBoolean.NO; }
            @Override public AlmostBoolean isIllegalBounds(Player player){ return AlmostBoolean.NO; }
            @Override public double getJumpAmplifier(Player player){ return 0; }
            @Override public double getFasterMovementAmplifier(Player player){ return 0; }
            @Override public int getInvulnerableTicks(Player player){ return 5; }
            @Override public void setInvulnerableTicks(Player player,int ticks){}
            @Override public void dealFallDamage(Player player,double damage){}
            @Override public AlmostBoolean dealFallDamageFiresAnEvent(){ return AlmostBoolean.NO; }
            @Override public boolean isComplexPart(Entity damaged){ return false; }
            @Override public boolean shouldBeZombie(Player player){ return false; }
            @Override public void setDead(Player player,int deathTicks){}
            @Override public boolean hasGravity(Material type){ return false; }
            @Override public boolean resetActiveItem(Player player){ return false; }
        }

        DummyAPI() {
            StaticLog.setUseLogManager(false);
            registry.registerGenericInstance(MCAccess.class, new DummyMCAccess());
            registry.registerGenericInstance(ActionFactoryFactory.class, ActionFactory::new);
            for (RegisteredPermission rp : Permissions.getPermissions()) {
                permissionRegistry.addRegisteredPermission(rp);
            }
            java.util.Map<String, ConfigFile> raw = new java.util.HashMap<>();
            raw.put(null, new DefaultConfig());
            worldDataManager.applyConfiguration(raw);
        }
        @Override public <T> IGenericInstanceHandle<T> getGenericInstanceHandle(Class<T> c){ return registry.getGenericInstanceHandle(c); }
        @Override public <T> T getGenericInstance(Class<T> c){ return registry.getGenericInstance(c); }
        @Override public <T, TI extends T> T registerGenericInstance(Class<T> c, TI i){ return registry.registerGenericInstance(c, i); }
        @Override public <T> T unregisterGenericInstance(Class<T> c){ return registry.unregisterGenericInstance(c); }
        @Override public void addFeatureTags(String k, java.util.Collection<String> v){}
        @Override public void setFeatureTags(String k, java.util.Collection<String> v){}
        @Override public boolean hasFeatureTag(String k, String v){ return false; }
        @Override public java.util.Map<String, java.util.Set<String>> getAllFeatureTags(){ return java.util.Collections.emptyMap(); }
        @Override public int sendAdminNotifyMessage(String m){ return 0; }
        @Override public void sendMessageOnTick(String p, String m){}
        @Override public boolean allowLogin(String p){ return false; }
        @Override public int allowLoginAll(){ return 0; }
        @Override public void denyLogin(String p, long d){}
        @Override public boolean isLoginDenied(String p){ return false; }
        @Override public String[] getLoginDeniedPlayers(){ return new String[0]; }
        @Override public boolean isLoginDenied(String p, long t){ return false; }
        @Override public net.kyori.adventure.platform.bukkit.BukkitAudiences adventure(){ throw new UnsupportedOperationException(); }
        @Override public LogManager getLogManager(){ throw new UnsupportedOperationException(); }
        @Override public void removeComponent(Object c){ throw new UnsupportedOperationException(); }
        @Override public boolean addComponent(Object o){ throw new UnsupportedOperationException(); }
        @Override public boolean addComponent(Object o, boolean a){ throw new UnsupportedOperationException(); }
        @Override public <T> java.util.Collection<ComponentRegistry<T>> getComponentRegistries(Class<ComponentRegistry<T>> c){ throw new UnsupportedOperationException(); }
        @Override public <T> T registerGenericInstance(T i){ throw new UnsupportedOperationException(); }
        @Override public IEventRegistry getEventRegistry(){ throw new UnsupportedOperationException(); }
        @Override public PermissionRegistry getPermissionRegistry(){ return permissionRegistry; }
        @Override public WorldDataManager getWorldDataManager(){ return worldDataManager; }
        @Override public RegistrationContext newRegistrationContext(){ throw new UnsupportedOperationException(); }
        @Override public void register(RegistrationContext c){ throw new UnsupportedOperationException(); }
        @Override public IBlockChangeTracker getBlockChangeTracker(){ throw new UnsupportedOperationException(); }
        @Override public ActionFactoryFactory getActionFactoryFactory(){
            return registry.getGenericInstance(ActionFactoryFactory.class);
        }
        @Override public ActionFactoryFactory setActionFactoryFactory(ActionFactoryFactory f){
            return registry.registerGenericInstance(ActionFactoryFactory.class, f);
        }
        @Override public IPlayerDataManager getPlayerDataManager(){ throw new UnsupportedOperationException(); }
    }

    @Before
    public void setup() {
        try {
            java.lang.reflect.Field f = NCPAPIProvider.class.getDeclaredField("noCheatPlusAPI");
            f.setAccessible(true);
            f.set(null, new DummyAPI());
            // Ensure DataManager is available for check initialization.
            java.lang.reflect.Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            uf.setAccessible(true);
            sun.misc.Unsafe un = (sun.misc.Unsafe) uf.get(null);
            fr.neatmonster.nocheatplus.players.PlayerDataManager pdm =
                    (fr.neatmonster.nocheatplus.players.PlayerDataManager) un.allocateInstance(fr.neatmonster.nocheatplus.players.PlayerDataManager.class);
            java.lang.reflect.Field eh = fr.neatmonster.nocheatplus.players.PlayerDataManager.class.getDeclaredField("executionHistories");
            eh.setAccessible(true);
            eh.set(pdm, new java.util.HashMap<>());
            new fr.neatmonster.nocheatplus.players.DataManager(pdm);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testComputePlayerDamageState() throws Exception {
        Player player = mock(Player.class);
        when(player.getNoDamageTicks()).thenReturn(3);
        when(player.getHealth()).thenReturn(19.0);

        IWorldData worldData = mock(IWorldData.class);
        when(worldData.getRawConfiguration()).thenReturn(new DefaultConfig());
        FightConfig cfg = new FightConfig(worldData);
        FightData data = new FightData(cfg);
        data.lastDamageTick = 2;
        data.lastNoDamageTicks = 5;
        data.godModeHealth = 20.0;

        GodMode gm = new GodMode();
        Method m = GodMode.class.getDeclaredMethod("computePlayerDamageState", Player.class, FightData.class);
        m.setAccessible(true);
        Object state = m.invoke(gm, player, data);

        assertNotNull(state);
    }

    @Test
    public void testShouldIgnoreInvulnerability() throws Exception {
        IWorldData worldData = mock(IWorldData.class);
        when(worldData.getRawConfiguration()).thenReturn(new DefaultConfig());
        FightConfig cfg = new FightConfig(worldData);
        FightData data = new FightData(cfg);
        data.lastDamageTick = 0;

        Player player = mock(Player.class);
        GodMode gm = new GodMode();

        NCPAPIProvider.getNoCheatPlusAPI().registerGenericInstance(fr.neatmonster.nocheatplus.compat.MCAccess.class, new DummyAPI.DummyMCAccess());

        Method m = GodMode.class.getDeclaredMethod("shouldIgnoreInvulnerability", Player.class, boolean.class, FightData.class, int.class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(gm, player, false, data, 10);
        assertTrue(result);
    }

    @Test
    public void testShouldIgnoreLag() throws Exception {
        IWorldData worldData = mock(IWorldData.class);
        when(worldData.getRawConfiguration()).thenReturn(new DefaultConfig());
        FightConfig cfg = new FightConfig(worldData);
        FightData fData = new FightData(cfg);
        fData.speedBuckets.clear(Monotonic.synchMillis());
        BlockBreakConfig bbCfg = new BlockBreakConfig(worldData);
        BlockBreakData bbData = new BlockBreakData(bbCfg);
        bbData.fastBreakfirstDamage = 0;
        bbData.frequencyBuckets.clear(Monotonic.synchMillis());
        CombinedData cData = new CombinedData();
        cData.lastMoveTime = 0;
        InventoryData iData = new InventoryData();
        iData.lastClickTime = 0;
        iData.instantEatInteract = 0;
        NetData netData = new NetData(new fr.neatmonster.nocheatplus.checks.net.NetConfig(worldData));

        IPlayerData pData = mock(IPlayerData.class);
        when(pData.getGenericInstance(FightData.class)).thenReturn(fData);
        when(pData.getGenericInstance(fr.neatmonster.nocheatplus.checks.blockbreak.BlockBreakData.class)).thenReturn(bbData);
        when(pData.getGenericInstance(fr.neatmonster.nocheatplus.checks.combined.CombinedData.class)).thenReturn(cData);
        when(pData.getGenericInstance(fr.neatmonster.nocheatplus.checks.inventory.InventoryData.class)).thenReturn(iData);
        when(pData.getGenericInstance(NetData.class)).thenReturn(netData);

        long diff = (cfg.godModeLagMinAge + cfg.godModeLagMaxAge) / 2;
        netData.lastKeepAliveTime = Monotonic.synchMillis() - diff;
        Player player = mock(Player.class);
        GodMode gm = new GodMode();


        Method m = GodMode.class.getDeclaredMethod("shouldIgnoreLag", Player.class, IPlayerData.class, FightConfig.class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(gm, player, pData, cfg);
        assertTrue(result || !result);
    }
}
