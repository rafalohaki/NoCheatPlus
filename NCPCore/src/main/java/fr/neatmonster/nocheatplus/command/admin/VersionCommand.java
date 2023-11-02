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

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.command.BaseCommand;
import fr.neatmonster.nocheatplus.compat.MCAccess;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.hooks.NCPHook;
import fr.neatmonster.nocheatplus.hooks.NCPHookManager;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import org.jetbrains.annotations.NotNull;

public class VersionCommand extends BaseCommand {

    public VersionCommand(JavaPlugin plugin) {
        super(plugin, "version", Permissions.COMMAND_VERSION, new String[]{"versions", "ver"});
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        NCPAPIProvider.getNoCheatPlusAPI().adventure().sender(sender).sendMessage(getVersionInfo());
        return true;
    }

    public static Component getVersionInfo() {

        final MCAccess mcAccess = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstance(MCAccess.class);

        final Map<String, Set<String>> featureTags = NCPAPIProvider.getNoCheatPlusAPI().getAllFeatureTags();
        final net.kyori.adventure.text.TextComponent.Builder features = Component.text();
        if (!featureTags.isEmpty()) {
            features.append(Component.text("Features: ", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true)
                    .appendNewline());
            // Add present features.
            for (final Entry<String, Set<String>> entry : featureTags.entrySet()) {
                features.append(Component.text()
                        .append(Component.text(entry.getKey(), NamedTextColor.YELLOW))
                        .append(Component.text(": ", NamedTextColor.GRAY))
                        .appendNewline()
                        .append(Component.join(JoinConfiguration.commas(true), Component.text(entry.getValue().toString(), NamedTextColor.GRAY)))
                        .appendNewline()
                        .build());
            }
        }

        final Collection<NCPHook> hooks = NCPHookManager.getAllHooks();
        final net.kyori.adventure.text.TextComponent.Builder fullNames = Component.text();
        if (!hooks.isEmpty()) {
            fullNames.append(Component.text()
                    .append(Component.text("Hooks:", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true))
                    .appendNewline()
                    .build());
            for (final NCPHook hook : hooks) {
                fullNames.append(Component.join(JoinConfiguration.commas(true), Component.text(NCPHookManager.getHookDescription(hook), NamedTextColor.GRAY)));
            }
        }

        final net.kyori.adventure.text.TextComponent.Builder relatedPlugins = Component.text();
        relatedPlugins.append(Component.text()
                .append(Component.text("»Related Plugins«", NamedTextColor.RED).decoration(TextDecoration.BOLD, true))
                .appendNewline()
                .build());

        for (final String name : new String[]{"CompatNoCheatPlus", "ProtocolLib", "ViaVersion", "ProtocolSupport", "PNCP", "NTAC"}) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
            if (plugin != null) {
                relatedPlugins.append(Component.join(JoinConfiguration.commas(true), Component.text(plugin.getDescription().getFullName(), NamedTextColor.GRAY)));
            }
        }

        return Component.text()
                .append(Component.text("»Version information«", NamedTextColor.RED).decoration(TextDecoration.BOLD, true))
                .append(Component.newline())
                .append(Component.text("Server: ", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true))
                .append(Component.text(Bukkit.getServer().getVersion(), NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("Detected: ", NamedTextColor.YELLOW))
                .append(Component.text(ServerVersion.getMinecraftVersion(), NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("NoCheatPlus:", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true))
                .append(Component.newline())
                .append(Component.text("Plugin: ", NamedTextColor.YELLOW))
                .append(Component.text(Bukkit.getPluginManager().getPlugin("NoCheatPlus").getDescription().getVersion(), NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("MCAccess: ", NamedTextColor.YELLOW))
                .append(Component.text((mcAccess.getMCVersion() + " / " + mcAccess.getServerVersionTag()), NamedTextColor.GRAY))
                .append(Component.newline())

                .append(features)
                .append(Component.newline())

                .append(fullNames)
                .append(Component.newline())

                .append(relatedPlugins)

                .build();
    }
}
