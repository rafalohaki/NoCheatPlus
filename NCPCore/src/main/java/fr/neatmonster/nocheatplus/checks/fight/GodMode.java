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
package fr.neatmonster.nocheatplus.checks.fight;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.net.NetData;
import fr.neatmonster.nocheatplus.compat.BridgeHealth;
import fr.neatmonster.nocheatplus.compat.Folia;
import fr.neatmonster.nocheatplus.compat.IBridgeCrossPlugin;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;
import fr.neatmonster.nocheatplus.utilities.TickTask;

/**
 * The GodMode check will find out if a player tried to stay invulnerable after being hit or after dying.
 */
public class GodMode extends Check {

    private final IGenericInstanceHandle<IBridgeCrossPlugin> crossPlugin = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstanceHandle(IBridgeCrossPlugin.class);

    /**
     * Instantiates a new god mode check.
     */
    public GodMode() {
        super(CheckType.FIGHT_GODMODE);
    }

    /**
     * New style god mode check. Much more sensitive.
     * @param player
     * @param damage
     * @return
     */
    public boolean check(final Player player, final boolean playerIsFake,
            final double damage, final FightData data, final IPlayerData pData){
        final DamageState dState = computePlayerDamageState(player, data);

        final Decision decision = new Decision();

        if (dState.healthDecreased){
            data.godModeHealthDecreaseTick = dState.tick;
            decision.markLegit();
            decision.markSetTicks();
            decision.markResetAcc();
        }

        final boolean ignoreInvul = shouldIgnoreInvulnerability(player, playerIsFake, data, dState.tick);
        if (ignoreInvul) {
            decision.markLegit();
            decision.markSetTicks();
            decision.markResetAcc();
        }

        // Reset accumulator.
        if (20 + data.godModeAcc < dState.dTick || dState.dTick > 40){
            decision.markLegit();
            decision.markResetAcc();
            decision.markSetTicks();
        }

        // Check if reduced more than expected or new/count down fully.
        // NOTE: remove workaround logic
        if (dState.delta <= 0  || data.lastNoDamageTicks <= player.getMaximumNoDamageTicks() / 2 ||
                dState.dTick > data.lastNoDamageTicks || damage > BridgeHealth.getLastDamage(player) || damage == 0.0){
            decision.markLegit();
            decision.markSetTicks();
        }

        if (dState.dTick == 1 && dState.noDamageTicks < 19){
            decision.markSetTicks();
        }

        if (dState.delta == 1){
            // Ignore these, but keep reference value from before.
            decision.markLegit();
        }

        //    	Bukkit.getServer().broadcastMessage("God " + player.getName() + " delta=" + delta + " dt=" + dTick + " dndt=" + dNDT + " acc=" + data.godModeAcc + " d=" + damage + " ndt=" + noDamageTicks + " h=" + health + " slag=" + TickTask.getLag(dTick, true));

        // NOTE: check last damage taken as well

        // Resetting
        data.godModeHealth = dState.health;

        if (decision.shouldResetAcc() || decision.shouldResetAll()){
            data.godModeAcc = 0;
        }
        if (decision.isLegit()){
            data.godModeVL *= 0.97;
        }
        if (decision.shouldResetAll()){
            // Reset all.
            data.lastNoDamageTicks = 0;
            data.lastDamageTick = 0;
            return false;
        }
        else if (decision.shouldSetTicks()){
            // Only set the tick values.
            data.lastNoDamageTicks = dState.noDamageTicks;
            data.lastDamageTick = dState.tick;
            return false;
        }
        else if (decision.isLegit()){
            // Just return;
            return false;
        }

        if (dState.tick < data.godModeHealthDecreaseTick){
            data.godModeHealthDecreaseTick = 0;
        }
        else {
            final int dht = dState.tick - data.godModeHealthDecreaseTick;
            if (dht <= 20) {
                return false;
            }
        }

        final FightConfig cc = pData.getGenericInstance(FightConfig.class);

        if (shouldIgnoreLag(player, pData, cc)) {
            return false;
        }

        // Violation probably.
        data.godModeAcc += dState.delta;

        boolean cancel = false;
        // NOTE: implement bounds
        if (data.godModeAcc > 2){
            // NOTE: adjust vl scaling to match legacy actions
            data.godModeVL += dState.delta;
            if (executeActions(player, data.godModeVL, dState.delta,
                    pData.getGenericInstance(FightConfig.class).godModeActions).willCancel()){
                cancel = true;
            }
            else {
                cancel = false;
            }
        }
        else {
            cancel = false;
        }

        // Set tick values.
        data.lastNoDamageTicks = dState.noDamageTicks;
        data.lastDamageTick = dState.tick;

        return cancel;
    }

    private DamageState computePlayerDamageState(Player player, FightData data) {
        final int tick = TickTask.getTick();
        final int noDamageTicks = Math.max(0, player.getNoDamageTicks());
        final int dTick = tick - data.lastDamageTick;
        final int dNDT = data.lastNoDamageTicks - noDamageTicks;
        final int delta = dTick - dNDT;
        final double health = BridgeHealth.getHealth(player);
        final boolean healthDecreased = data.godModeHealth > health;
        return new DamageState(tick, noDamageTicks, dTick, dNDT, delta, health, healthDecreased);
    }

    private boolean shouldIgnoreInvulnerability(Player player, boolean playerIsFake, FightData data, int tick) {
        final int invulnerabilityTicks = playerIsFake ? 0 : mcAccess.getHandle().getInvulnerableTicks(player);
        return (invulnerabilityTicks != Integer.MAX_VALUE && invulnerabilityTicks > 0) || tick < data.lastDamageTick;
    }

    private boolean shouldIgnoreLag(Player player, IPlayerData pData, FightConfig cc) {
        final long now = System.currentTimeMillis();
        final long maxAge = cc.godModeLagMaxAge;
        long keepAlive = Long.MIN_VALUE;
        if (NCPAPIProvider.getNoCheatPlusAPI().hasFeatureTag("checks", "KeepAliveFrequency")) {
            keepAlive = pData.getGenericInstance(NetData.class).lastKeepAliveTime;
        }
        keepAlive = Math.max(keepAlive, CheckUtils.guessKeepAliveTime(player, now, maxAge, pData));
        return keepAlive != Long.MIN_VALUE && now - keepAlive > cc.godModeLagMinAge && now - keepAlive < maxAge;
    }

    private static final class DamageState {
        final int tick;
        final int noDamageTicks;
        final int dTick;
        final int dNDT;
        final int delta;
        final double health;
        final boolean healthDecreased;

        DamageState(int tick, int noDamageTicks, int dTick, int dNDT, int delta, double health, boolean healthDecreased) {
            this.tick = tick;
            this.noDamageTicks = noDamageTicks;
            this.dTick = dTick;
            this.dNDT = dNDT;
            this.delta = delta;
            this.health = health;
            this.healthDecreased = healthDecreased;
        }
    }

    private static final class Decision {
        private boolean legit;
        private boolean setTicks;
        private boolean resetAcc;
        private boolean resetAll;

        void markLegit() { this.legit = true; }
        void markSetTicks() { this.setTicks = true; }
        void markResetAcc() { this.resetAcc = true; }
        void markResetAll() { this.resetAll = true; }

        boolean isLegit() { return legit; }
        boolean shouldSetTicks() { return setTicks; }
        boolean shouldResetAcc() { return resetAcc; }
        boolean shouldResetAll() { return resetAll; }
    }

    /**
     * If a player apparently died, make sure they really die after some time if they didn't already, by setting up a
     * Bukkit task.
     * 
     * @param player
     *            the player
     */
    public void death(final Player player) {
        // NOTE: confirm if still relevant
        // First check if the player is really dead (e.g. another plugin could have just fired an artificial event).
        if (BridgeHealth.getHealth(player) <= 0.0 && player.isDead()
                && crossPlugin.getHandle().isNativeEntity(player)) {
            try {
                // Schedule a task to be executed in roughly 1.5 seconds.
                // NOTE: verify plugin retrieval here
            	Folia.runSyncDelayedTaskForEntity(player, Bukkit.getPluginManager().getPlugin("NoCheatPlus"), (arg) -> {
            		try {
                        // Check again if the player should be dead, and if the game didn't mark them as dead.
                        if (mcAccess.getHandle().shouldBeZombie(player)){
                            // Artificially "kill" them.
                            mcAccess.getHandle().setDead(player, 19);
                        }
                    } catch (final Exception e) {
                        StaticLog.logWarning("Failed to set player dead: " + e.getMessage());
                    }
                }, null, 30);
            } catch (final Exception e) {
                // ignore - scheduled task failed
            }
        }
    }
}
