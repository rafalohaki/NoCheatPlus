package org.mockbukkit.mockbukkit.command;

import org.bukkit.command.CommandMap;
import org.bukkit.command.SimpleCommandMap;
import org.mockbukkit.mockbukkit.ServerMock;

/** Simplified command map for tests compatible with older Spigot APIs. */
public class CommandMapMock extends SimpleCommandMap implements CommandMap {
    public CommandMapMock(ServerMock server) {
        super(server);
    }
}
