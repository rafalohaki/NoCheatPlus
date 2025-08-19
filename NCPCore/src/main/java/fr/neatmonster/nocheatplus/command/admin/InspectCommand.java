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
import org.bukkit.Location;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

        final boolean useColors = sender instanceof Player;
        
        for (int i = 1; i < args.length; i++) {
            final Player player = DataManager.getPlayer(args[i].trim().toLowerCase());
            if (player == null) {
                if (useColors) {
                    sender.sendMessage(Component.text(TAG).append(Component.text("Not online: ", NamedTextColor.GRAY)).append(Component.text(args[i], NamedTextColor.RED)));
                } else {
                    sender.sendMessage(CTAG + "Not online: " + args[i]);
                }
            } 
            else {
                if (useColors) {
                    sender.sendMessage(getInspectComponent(player));
                } else {
                    sender.sendMessage(getInspectMessage(player));
                }
            }
        }
        return true;
    }

    public static Component getInspectComponent(final Player player) {
        final IPlayerData pData = DataManager.getPlayerData(player);
        final MovingData mData = pData.getGenericInstance(MovingData.class);
        final MovingConfig mCC = pData.getGenericInstance(MovingConfig.class);
        final PlayerMoveData thisMove = mData.playerMoves.getCurrentMove();

        Component message = Component.text(TAG, NamedTextColor.GRAY)
            .append(Component.text("Status information for player: ", NamedTextColor.GRAY))
            .append(Component.text(player.getName(), NamedTextColor.RED));

        message = message.append(Component.text("\n ", NamedTextColor.GRAY)
            .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
            .append(Component.text(pData.isBedrockPlayer() ? " Is a Bedrock player" : " Is a Java player", NamedTextColor.GRAY, TextDecoration.ITALIC))
            .append(Component.text(".", NamedTextColor.GRAY)));

        if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
            message = message.append(Component.text("\n ", NamedTextColor.GRAY)
                .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
                .append(Component.text(" Is playing with version " + ProtocolVersion.getProtocol(Via.getAPI().getPlayerVersion(pData.getPlayerId())), NamedTextColor.GRAY)));
        }

        if (player.isOp()) {
            message = message.append(Component.text("\n ", NamedTextColor.GRAY)
                .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
                .append(Component.text(" Is OP", NamedTextColor.GRAY, TextDecoration.ITALIC))
                .append(Component.text(".", NamedTextColor.GRAY)));
        }

        message = message.append(Component.text("\n ", NamedTextColor.GRAY)
            .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
            .append(Component.text(player.isOnline() ? " Is currently online." : " Is offline.", NamedTextColor.GRAY)));

        message = message.append(Component.text("\n ", NamedTextColor.GRAY)
            .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
            .append(Component.text(player.isValid() ? " Player is valid." : " Player is invalid.", NamedTextColor.GRAY)));

        message = message.append(Component.text("\n ", NamedTextColor.GRAY)
            .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
            .append(Component.text(" Current health: " + f1.format(BridgeHealth.getHealth(player)) + "/" + f1.format(BridgeHealth.getMaxHealth(player)), NamedTextColor.GRAY)));

        message = message.append(Component.text("\n ", NamedTextColor.GRAY)
            .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
            .append(Component.text(" Current food level: " + player.getFoodLevel(), NamedTextColor.GRAY)));

        message = message.append(Component.text("\n ", NamedTextColor.GRAY)
            .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
            .append(Component.text(" Is in " + player.getGameMode() + " gamemode.", NamedTextColor.GRAY)));

        message = message.append(Component.text("\n ", NamedTextColor.GRAY)
            .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
            .append(Component.text(mCC.assumeSprint ? " Is assumed to be sprinting." : " Assume sprint workaround disabled.", NamedTextColor.GRAY)));

        message = message.append(Component.text("\n ", NamedTextColor.GRAY)
            .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
            .append(Component.text(" FlySpeed: " + player.getFlySpeed(), NamedTextColor.GRAY)));

        message = message.append(Component.text("\n ", NamedTextColor.GRAY)
            .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
            .append(Component.text(" WalkSpeed: " + player.getWalkSpeed(), NamedTextColor.GRAY)));

        if (thisMove.modelFlying != null) {
            message = message.append(Component.text("\n ", NamedTextColor.GRAY)
                .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
                .append(Component.text(" Movement model for this move " + thisMove.modelFlying.getId().toString(), NamedTextColor.GRAY)));
        }

        if (player.getExp() > 0f) {
            message = message.append(Component.text("\n ", NamedTextColor.GRAY)
                .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
                .append(Component.text(" Experience Lvl: " + f1.format(player.getExpToLevel()) + "(exp=" + f1.format(player.getExp()) + ")", NamedTextColor.GRAY)));
        }

        if (Bridge1_9.isGlidingWithElytra(player)) {
            message = message.append(Component.text("\n ", NamedTextColor.GRAY)
                .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
                .append(Component.text(" Is gliding with elytra.", NamedTextColor.GRAY)));
        }

        if (Bridge1_13.isRiptiding(player)) {
            message = message.append(Component.text("\n ", NamedTextColor.GRAY)
                .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
                .append(Component.text(" Is riptiding.", NamedTextColor.GRAY)));
        }

        if (Bridge1_13.isSwimming(player)) {
            message = message.append(Component.text("\n ", NamedTextColor.GRAY)
                .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
                .append(Component.text(" Is swimming (1.13).", NamedTextColor.GRAY)));
        }

        if (player.isSneaking()) {
            message = message.append(Component.text("\n ", NamedTextColor.GRAY)
                .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
                .append(Component.text(" Is sneaking.", NamedTextColor.GRAY)));
        }

        if (player.isBlocking()) {
            message = message.append(Component.text("\n ", NamedTextColor.GRAY)
                .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
                .append(Component.text(" Is blocking.", NamedTextColor.GRAY)));
        }

        if (player.isSprinting()) {
            message = message.append(Component.text("\n ", NamedTextColor.GRAY)
                .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
                .append(Component.text(" Is sprinting.", NamedTextColor.GRAY)));
        }

        if (mData.isUsingItem) {
            message = message.append(Component.text("\n ", NamedTextColor.GRAY)
                .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
                .append(Component.text(" Is using an item.", NamedTextColor.GRAY)));
        }

        if (mData.lostSprintCount > 0) {
            message = message.append(Component.text("\n ", NamedTextColor.GRAY)
                .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
                .append(Component.text(" Their sprint status has been lost for: " + mData.lostSprintCount + " ticks.", NamedTextColor.GRAY)));
        }

        if (player.isInsideVehicle()) {
            message = message.append(Component.text("\n ", NamedTextColor.GRAY)
                .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
                .append(Component.text(" Is riding a vehicle (" + player.getVehicle().getType() + ") at " + locString(player.getVehicle().getLocation()), NamedTextColor.GRAY)));
        }

        if (player.isDead()) {
            message = message.append(Component.text("\n ", NamedTextColor.GRAY)
                .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
                .append(Component.text(" Is currently dead.", NamedTextColor.GRAY)));
        }

        if (player.isFlying()) {
            message = message.append(Component.text("\n ", NamedTextColor.GRAY)
                .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
                .append(Component.text(" Is currently flying.", NamedTextColor.GRAY)));
        }

        if (player.getAllowFlight()) {
            message = message.append(Component.text("\n ", NamedTextColor.GRAY)
                .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
                .append(Component.text(" Is allowed to fly.", NamedTextColor.GRAY)));
        }

        final Collection<PotionEffect> effects = player.getActivePotionEffects();
        if (!effects.isEmpty()) {
            message = message.append(Component.text("\n ", NamedTextColor.GRAY)
                .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
                .append(Component.text("Has the following effects: ", NamedTextColor.GRAY)));
            for (final PotionEffect effect : effects) {
                message = message.append(Component.text(effect.getType() + " at level " + effect.getAmplifier() + ", ", NamedTextColor.GRAY));
            }
        }

        final Location loc = player.getLocation();
        message = message.append(Component.text("\n ", NamedTextColor.GRAY)
            .append(Component.text("•", NamedTextColor.GRAY, TextDecoration.BOLD))
            .append(Component.text(" Position: " + locString(loc), NamedTextColor.GRAY)));

        return message;
    }

    public static String getInspectMessage(final Player player) {
        final StringBuilder builder = new StringBuilder(256);
        final IPlayerData pData = DataManager.getPlayerData(player);
        final MovingData mData = pData.getGenericInstance(MovingData.class);
        final MovingConfig mCC = pData.getGenericInstance(MovingConfig.class);
        final PlayerMoveData thisMove = mData.playerMoves.getCurrentMove();

        builder.append(TAG + "Status information for player: " + player.getName());
        
        builder.append("\n • " + (pData.isBedrockPlayer() ? "Is a Bedrock player" : "Is a Java player") + ".");

        if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
            builder.append("\n • Is playing with version " + ProtocolVersion.getProtocol(Via.getAPI().getPlayerVersion(pData.getPlayerId())));
        }

        if (player.isOp()){
            builder.append("\n • Is OP.");
        }

        builder.append("\n • " + (player.isOnline() ? "Is currently online." : "Is offline."));
        
        builder.append("\n • " + (player.isValid() ? "Player is valid." : "Player is invalid."));

        builder.append("\n • Current health: " + f1.format(BridgeHealth.getHealth(player)) + "/" + f1.format(BridgeHealth.getMaxHealth(player)));

        builder.append("\n • Current food level: " + player.getFoodLevel());

        builder.append("\n • Is in " + player.getGameMode() + " gamemode.");

        builder.append("\n • " + (mCC.assumeSprint ? "Is assumed to be sprinting." : "Assume sprint workaround disabled."));

        builder.append("\n • FlySpeed: " + player.getFlySpeed());

        builder.append("\n • WalkSpeed: " + player.getWalkSpeed());

        if (thisMove.modelFlying != null) {
            builder.append("\n • Movement model for this move " + thisMove.modelFlying.getId().toString());
        }

        if (player.getExp() > 0f) {
            builder.append("\n • Experience Lvl: " + f1.format(player.getExpToLevel()) + "(exp=" + f1.format(player.getExp()) + ")");
        }

        if (Bridge1_9.isGlidingWithElytra(player)) {
            builder.append("\n • Is gliding with elytra.");
        }

        if (Bridge1_13.isRiptiding(player)) {
            builder.append("\n • Is riptiding." );
        }

        if (Bridge1_13.isSwimming(player)) {
            builder.append("\n • Is swimming (1.13).");
        }
        
        if (player.isSneaking()) {
            builder.append("\n • Is sneaking.");
        }

        if (player.isBlocking()) {
            builder.append("\n • Is blocking.");
        }

        if (player.isSprinting()) {
            builder.append("\n • Is sprinting.");
        }

        if (mData.isUsingItem) {
            builder.append("\n • Is using an item."); // TODO: Which item?
        }

        if (mData.lostSprintCount > 0) {
            builder.append("\n • Their sprint status has been lost for: " + mData.lostSprintCount + " ticks.");
        }

        if (player.isInsideVehicle()) {
            builder.append("\n • Is riding a vehicle (" + player.getVehicle().getType() +") at " + locString(player.getVehicle().getLocation()));
        }

        if (player.isDead()) {
            builder.append("\n • Is currently dead.");
        }

        if (player.isFlying()) {
            builder.append("\n • Is currently flying.");
        }

        if (player.getAllowFlight()) {
            builder.append("\n • Is allowed to fly.");
        }

        // Potion effects.
        final Collection<PotionEffect> effects = player.getActivePotionEffects();
        if (!effects.isEmpty()) {
            builder.append("\n • Has the following effects: ");
            for (final PotionEffect effect : effects) {
                builder.append(effect.getType() + " at level " + effect.getAmplifier() +", ");
            }
        }
        // Finally the block location.
        final Location loc = player.getLocation();
        builder.append("\n • Position: " + locString(loc));
        return builder.toString();
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
