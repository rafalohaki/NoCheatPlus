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
            final Player player = DataManager.getInstance().getPlayer(args[i].trim().toLowerCase());
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
        if (player == null) {
            return TAG + c3 + "Player not found.";
        }

        final StringBuilder builder = new StringBuilder(256);
        final IPlayerData pData = DataManager.getInstance().getPlayerData(player);
        final MovingData mData = pData != null ? pData.getGenericInstance(MovingData.class) : null;
        final MovingConfig mCC = pData != null ? pData.getGenericInstance(MovingConfig.class) : null;
        final PlayerMoveData thisMove = mData != null ? mData.playerMoves.getCurrentMove() : null;

        builder.append(TAG).append(c1).append("Status information for player: ").append(c3).append(player.getName());
        appendPlayerMeta(builder, player, pData, c1, c2, cI);
        appendMovementSettings(builder, player, mCC, thisMove, c1, c2);
        appendStateFlags(builder, player, mData, c1, c2);
        appendExtraStates(builder, player, c1, c2);
        appendPotionEffects(builder, player, c1, c2);
        appendLocationInfo(builder, player, c1, c2);
        return builder.toString();
    }

    private static void appendPlayerMeta(final StringBuilder builder, final Player player, final IPlayerData pData,
            final String c1, final String c2, final String cI) {
        appendBullet(builder, c1, c2, cI + ((pData != null && pData.isBedrockPlayer()) ? " Is a Bedrock player" : " Is a Java player") + c1 + ".");
        if (pData != null && Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
            appendBullet(builder, c1, c2, " Is playing with version "
                    + ProtocolVersion.getProtocol(Via.getAPI().getPlayerVersion(pData.getPlayerId())));
        }
        if (player.isOp()) {
            appendBullet(builder, c1, c2, cI + " Is OP" + c1 + ".");
        }
        appendBullet(builder, c1, c2, player.isOnline() ? " Is currently online." : " Is offline.");
        appendBullet(builder, c1, c2, player.isValid() ? " Player is valid." : " Player is invalid.");
        appendBullet(builder, c1, c2,
                " Current health: " + f1.format(BridgeHealth.getHealth(player)) + "/" + f1.format(BridgeHealth.getMaxHealth(player)));
        appendBullet(builder, c1, c2, " Current food level: " + player.getFoodLevel());
        appendBullet(builder, c1, c2, " Is in " + player.getGameMode() + " gamemode.");
    }

    private static void appendMovementSettings(final StringBuilder builder, final Player player,
            final MovingConfig mCC, final PlayerMoveData thisMove, final String c1, final String c2) {
        if (mCC != null) {
            appendBullet(builder, c1, c2,
                    mCC.assumeSprint ? " Is assumed to be sprinting." : " Assume sprint workaround disabled.");
        }
        appendBullet(builder, c1, c2, " FlySpeed: " + player.getFlySpeed());
        appendBullet(builder, c1, c2, " WalkSpeed: " + player.getWalkSpeed());
        if (thisMove != null && thisMove.modelFlying != null) {
            appendBullet(builder, c1, c2, " Movement model for this move " + thisMove.modelFlying.getId().toString());
        }
        if (player.getExp() > 0f) {
            appendBullet(builder, c1, c2,
                    " Experience Lvl: " + f1.format(player.getExpToLevel()) + "(exp=" + f1.format(player.getExp()) + ")");
        }
    }

    private static void appendStateFlags(final StringBuilder builder, final Player player, final MovingData mData,
            final String c1, final String c2) {
        if (Bridge1_9.isGlidingWithElytra(player)) {
            appendBullet(builder, c1, c2, " Is gliding with elytra.");
        }
        if (Bridge1_13.isRiptiding(player)) {
            appendBullet(builder, c1, c2, " Is riptiding.");
        }
        if (Bridge1_13.isSwimming(player)) {
            appendBullet(builder, c1, c2, " Is swimming (1.13).");
        }
        if (player.isSneaking()) {
            appendBullet(builder, c1, c2, " Is sneaking.");
        }
        if (player.isBlocking()) {
            appendBullet(builder, c1, c2, " Is blocking.");
        }
        if (player.isSprinting()) {
            appendBullet(builder, c1, c2, " Is sprinting.");
        }
        if (mData != null) {
            if (mData.isUsingItem) {
                appendBullet(builder, c1, c2, " Is using an item.");
            }
            if (mData.lostSprintCount > 0) {
                appendBullet(builder, c1, c2,
                        " Their sprint status has been lost for: " + mData.lostSprintCount + " ticks.");
            }
        }
    }

    private static void appendExtraStates(final StringBuilder builder, final Player player, final String c1,
            final String c2) {
        if (player.isInsideVehicle()) {
            appendBullet(builder, c1, c2,
                    " Is riding a vehicle (" + player.getVehicle().getType() + ") at "
                            + locString(player.getVehicle().getLocation()));
        }
        if (player.isDead()) {
            appendBullet(builder, c1, c2, " Is currently dead.");
        }
        if (player.isFlying()) {
            appendBullet(builder, c1, c2, " Is currently flying.");
        }
        if (player.getAllowFlight()) {
            appendBullet(builder, c1, c2, " Is allowed to fly.");
        }
    }

    private static void appendPotionEffects(final StringBuilder builder, final Player player, final String c1,
            final String c2) {
        final Collection<PotionEffect> effects = player.getActivePotionEffects();
        if (!effects.isEmpty()) {
            appendBullet(builder, c1, c2, "Has the following effects: ");
            for (final PotionEffect effect : effects) {
                builder.append(effect.getType()).append(" at level ").append(effect.getAmplifier()).append(", ");
            }
        }
    }

    private static void appendLocationInfo(final StringBuilder builder, final Player player, final String c1,
            final String c2) {
        final Location loc = player.getLocation();
        appendBullet(builder, c1, c2, " Position: " + locString(loc));
    }

    private static void appendBullet(final StringBuilder builder, final String c1, final String c2,
            final String message) {
        builder.append("\n ").append(c1).append(c2).append('•').append(c1).append(message);
    }
    private static final String locString(Location loc) {
        return loc.getWorld().getName() + " at " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
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
