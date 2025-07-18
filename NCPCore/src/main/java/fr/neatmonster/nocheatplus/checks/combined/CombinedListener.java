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
package fr.neatmonster.nocheatplus.checks.combined;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.CheckListener;
import fr.neatmonster.nocheatplus.checks.CheckType;
import java.util.List;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.components.data.ICheckData;
import fr.neatmonster.nocheatplus.components.data.IData;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.stats.Counters;
import fr.neatmonster.nocheatplus.utilities.TickTask;

/**
 * Class to combine some things, make available for other checks, or just because they don't fit into another section.<br>
 * This is registered before the FightListener.
 * Do note the registration order in fr.neatmonster.nocheatplus.NoCheatPlus.onEnable (within NCPPlugin).
 * 
 * @author mc_dev
 *
 */
public class CombinedListener extends CheckListener {

    protected final Improbable improbable 	= addCheck(new Improbable());

    protected final MunchHausen munchHausen = addCheck(new MunchHausen());

    private final Counters counters = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstance(Counters.class);
    private final int idFakeInvulnerable = counters.registerKey("fakeinvulnerable");

    @SuppressWarnings("unchecked")
    public CombinedListener(){
        super(CheckType.COMBINED);
        final NoCheatPlusAPI api = NCPAPIProvider.getNoCheatPlusAPI();
        api.register(api.newRegistrationContext()
                // CombinedConfig
                .registerConfigWorld(CombinedConfig.class)
                .factory(arg -> new CombinedConfig(arg.worldData))
                .registerConfigTypesPlayer()
                .context() //
                // CombinedData
                .registerDataPlayer(CombinedData.class)
                .factory(arg -> new CombinedData())
                .addToGroups(CheckType.MOVING, false, List.of(IData.class, ICheckData.class))
                .removeSubCheckData(CheckType.COMBINED, true)
                .context() //
                );
    }

    /**
     * We listen to this event to prevent players from leaving while falling, so from avoiding fall damages.
     * 
     * @param event
     *            the event
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(final PlayerJoinEvent event) {

        // Event priority intentionally lowest for join handling.

        final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getInstance().getPlayerData(player);

        if (!pData.isCheckActive(CheckType.COMBINED, player)) return;

        final CombinedData data = pData.getGenericInstance(CombinedData.class);
        final CombinedConfig cc = pData.getGenericInstance(CombinedConfig.class);

        if (cc.invulnerableCheck 
                && (cc.invulnerableTriggerAlways || cc.invulnerableTriggerFallDistance 
                        && player.getFallDistance() > 0)){
            // Consider a heuristic for small fall distances with ground underfoot to prevent abuse with jumping.
            final int invulnerableTicks = mcAccess.getHandle().getInvulnerableTicks(player);
            if (invulnerableTicks == Integer.MAX_VALUE) {
                // Consider logging a warning.
            } else {
                final int ticks = cc.invulnerableInitialTicksJoin >= 0 ? cc.invulnerableInitialTicksJoin : invulnerableTicks;
                data.invulnerableTick = TickTask.getTick() + ticks;
                mcAccess.getHandle().setInvulnerableTicks(player, 0);
            }
        }
    }


    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLeave(final PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getInstance().getPlayerData(player);
        if (!pData.isCheckActive(CheckType.COMBINED, player)) return;
        final CombinedData data = pData.getGenericInstance(CombinedData.class);
        // Don't keep Improbable's data
        data.improbableCount.clear(System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(final EntityDamageEvent event){
        final Entity entity = event.getEntity();
        if (!(entity instanceof Player)) return;
        final Player  player = (Player) entity;
        final IPlayerData pData = DataManager.getInstance().getPlayerData(player);

        if (!pData.isCheckActive(CheckType.COMBINED, player)) return;

        final CombinedConfig cc = pData.getGenericInstance(CombinedConfig.class);
        if (!cc.invulnerableCheck) return;
        final DamageCause cause = event.getCause();
        // Ignored causes.
        if (cc.invulnerableIgnore.contains(cause)) return;
        // Modified invulnerable ticks.
        Integer modifier = cc.invulnerableModifiers.get(cause);
        if (modifier == null) modifier = cc.invulnerableModifierDefault;
        final CombinedData data = pData.getGenericInstance(CombinedData.class);
        // Verify if tick task reset must be handled; data resetting should prevent issues though the API would allow it.
        if (TickTask.getTick() >= data.invulnerableTick + modifier.intValue()) return;
        // Still invulnerable.
        event.setCancelled(true);
        counters.addPrimaryThread(idFakeInvulnerable, 1);
    }

    // Clarify why improbable feeding is required for toggling sprint.
    @EventHandler(priority = EventPriority.MONITOR) // HIGHEST)
    public void onPlayerToggleSprintHighest(final PlayerToggleSprintEvent event) {
        //    	// Review the un-cancelling.
        //        // Some plugins cancel "sprinting", which makes no sense at all because it doesn't stop people from sprinting
        //        // and rewards them by reducing their hunger bar as if they were walking instead of sprinting.
        //        if (event.isCancelled() && event.isSprinting())
        //            event.setCancelled(false);
        // Feed the improbable.
        Improbable.feed(event.getPlayer(), 0.35f, System.currentTimeMillis());
    }
    
    // Clarify why improbable feeding is required for toggling sneak.
    @EventHandler(priority=EventPriority.MONITOR)
    public void onPlayerToggleSneak(final PlayerToggleSneakEvent event){
        // Check also in case of cancelled events.
        // Feed the improbable.
        Improbable.feed(event.getPlayer(), 0.35f, System.currentTimeMillis());
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onPlayerFish(final PlayerFishEvent event){
        // Check also in case of cancelled events.
        final Player player = event.getPlayer();
        if (munchHausen.isEnabled(player) && munchHausen.checkFish(player, event.getCaught(), event.getState())){
            event.setCancelled(true);
        }
    }

}
