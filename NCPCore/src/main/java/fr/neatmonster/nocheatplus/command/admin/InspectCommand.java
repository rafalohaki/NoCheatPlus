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
package fr.neatmonster.nocheatplus.command.admin;

import com.google.common.collect.Lists;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import fr.neatmonster.nocheatplus.command.BaseCommand;
import fr.neatmonster.nocheatplus.compat.BridgeHealth;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;

public class InspectCommand extends BaseCommand {
    private static final DecimalFormat f1 = new DecimalFormat("#.#");

    public InspectCommand(JavaPlugin plugin) {
        super(plugin, "inspect", Permissions.COMMAND_INSPECT);
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.command.AbstractCommand#onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            if (sender instanceof Player) {
                args = new String[]{args[0], sender.getName()};
            } 
            else {
                sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Please specify a player to inspect.");
                return true;
            }
        }

        final String c1, c2, c3, cI;
        if (sender instanceof Player) {
            c1 = ChatColor.GRAY.toString();
            c2 = ChatColor.BOLD.toString();
            c3 = ChatColor.RED.toString();
            cI = ChatColor.ITALIC.toString();
        } 
        else {
            c1 = c2 = c3 = cI = "";
        }
        
        for (int i = 1; i < args.length; i++) {
            final Player player = DataManager.getPlayer(args[i].trim().toLowerCase());
            if (player == null) {
                sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Not online: " + c3 +""+ args[i]);
            } 
            else {
                sender.sendMessage(getInspectMessage(player, c1, c2, c3, cI));
            }
        }
        return true;
    }

    public static String getInspectMessage(final Player player, final String c1, final String c2, final String c3, final String cI) {

        final StringBuilder builder = new StringBuilder(256);

        if (player == null) {
            builder.append(TAG).append(c1).append("Player is null.");
            return builder.toString();
        }

        final IPlayerData pData = DataManager.getPlayerData(player);
        final MovingData mData = pData.getGenericInstance(MovingData.class);
        final MovingConfig mCC = pData.getGenericInstance(MovingConfig.class);
        final PlayerMoveData thisMove = mData.playerMoves.getCurrentMove();

        // More spaghetti.
        builder.append(TAG).append(c1).append("Status information for player: ").append(c3).append(player.getName());

        final String bullet = "\n " + c1 + c2 + "•" + c1 + " ";

        builder.append(bullet).append(cI).append(pData.isBedrockPlayer() ? "Is a Bedrock player" : "Is a Java player").append(c1).append('.');

        appendBulletIf(builder, Bukkit.getPluginManager().isPluginEnabled("ViaVersion"), bullet,
                "Is playing with version " + ProtocolVersion.getProtocol(Via.getAPI().getPlayerVersion(pData.getPlayerId())));
        //builder.append("\n "+ c1 + "" + c2 + "•" + c1 +" Is playing with version " + pData.getClientVersion().getReleaseName() + "(" + pData.getClientVersionID() + ")");

        appendBulletIf(builder, player.isOp(), bullet, cI + "Is OP" + c1 + '.');

        builder.append(bullet).append(player.isOnline() ? "Is currently online." : "Is offline.");

        builder.append(bullet).append(player.isValid() ? "Player is valid." : "Player is invalid.");

        builder.append(bullet).append("Current health: " + f1.format(BridgeHealth.getHealth(player)) + "/" + f1.format(BridgeHealth.getMaxHealth(player)));

        builder.append(bullet).append("Current food level: " + player.getFoodLevel());

        builder.append(bullet).append("Is in " + player.getGameMode() + " gamemode.");

        builder.append(bullet).append(mCC.assumeSprint ? "Is assumed to be sprinting." : "Assume sprint workaround disabled.");

        builder.append(bullet).append("FlySpeed: " + player.getFlySpeed());

        builder.append(bullet).append("WalkSpeed: " + player.getWalkSpeed());

        appendBulletIf(builder, thisMove.modelFlying != null, bullet,
                "Movement model for this move " + thisMove.modelFlying.getId().toString());

        appendBulletIf(builder, player.getExp() > 0f, bullet,
                "Experience Lvl: " + f1.format(player.getExpToLevel()) + "(exp=" + f1.format(player.getExp()) + ")");

        appendBulletIf(builder, Bridge1_9.isGlidingWithElytra(player), bullet, "Is gliding with elytra.");

        appendBulletIf(builder, Bridge1_13.isRiptiding(player), bullet, "Is riptiding.");

        appendBulletIf(builder, Bridge1_13.isSwimming(player), bullet, "Is swimming (1.13).");

        appendBulletIf(builder, player.isSneaking(), bullet, "Is sneaking.");

        appendBulletIf(builder, player.isBlocking(), bullet, "Is blocking.");

        appendBulletIf(builder, player.isSprinting(), bullet, "Is sprinting.");

        appendBulletIf(builder, mData.isUsingItem, bullet, "Is using an item.");

        appendBulletIf(builder, mData.lostSprintCount > 0, bullet,
                "Their sprint status has been lost for: " + mData.lostSprintCount + " ticks.");

        appendBulletIf(builder, player.isInsideVehicle(), bullet,
                "Is riding a vehicle (" + player.getVehicle().getType() + ") at " + locString(player.getVehicle().getLocation()));

        appendBulletIf(builder, player.isDead(), bullet, "Is currently dead.");

        appendBulletIf(builder, player.isFlying(), bullet, "Is currently flying.");

        appendBulletIf(builder, player.getAllowFlight(), bullet, "Is allowed to fly.");

        // Potion effects.
        final Collection<PotionEffect> effects = player.getActivePotionEffects();
        if (!effects.isEmpty()) {
            builder.append(bullet).append("Has the following effects: ");
            for (final PotionEffect effect : effects) {
                builder.append(effect.getType()).append(" at level ").append(effect.getAmplifier()).append(", ");
            }
        }
        // Finally the block location.
        final Location loc = player.getLocation();
        builder.append(bullet).append("Position: ").append(locString(loc));
        return builder.toString();
    }

    private static final String locString(Location loc) {
        return loc.getWorld().getName() + " at " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private static void appendBulletIf(StringBuilder builder, boolean condition, String bulletPrefix, String text) {
        if (condition) {
            builder.append(bulletPrefix).append(text);
        }
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.command.AbstractCommand#onTabComplete(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Complete Players
        if (args.length == 2) {
            List<String> players = Lists.newArrayList();
            for (Player player : Bukkit.getOnlinePlayers()) {
                players.add(player.getName());
            }
            return players;
        }
        return null;
    }
}
