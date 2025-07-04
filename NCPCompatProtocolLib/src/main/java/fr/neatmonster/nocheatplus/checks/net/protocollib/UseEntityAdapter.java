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
package fr.neatmonster.nocheatplus.checks.net.protocollib;

import java.lang.reflect.Method;
import java.util.Collections;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers.EntityUseAction;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.net.AttackFrequency;
import fr.neatmonster.nocheatplus.checks.net.NetConfig;
import fr.neatmonster.nocheatplus.checks.net.NetData;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;

public class UseEntityAdapter extends BaseAdapter {

    private static class LegacyReflectionSet {
        /** Hacks. */
        final Class<?> packetClass_legacy;
        final Class<?> enumClassAction_legacy;
        final Method methodGetAction_legacy;
        final Method methodName_legacy;

        /**
         *
         * @param versionDetail
         * @throws RuntimeException
         *             if not matching/supported.
         */
        private LegacyReflectionSet(String versionDetail) {
            Class<?> packetClass = ReflectionUtil.getClass("net.minecraft.server." + versionDetail + ".PacketPlayInUseEntity");
            Class<?> actionClass = ReflectionUtil.getClass("net.minecraft.server." + versionDetail + ".EnumEntityUseAction");
            Method methodGetAction = (packetClass == null || actionClass == null) ? null : ReflectionUtil.getMethodNoArgs(packetClass, "c", actionClass);
            if (packetClass == null || actionClass == null || methodGetAction == null) {
                this.packetClass_legacy = null;
                this.enumClassAction_legacy = null;
                this.methodGetAction_legacy = null;
                this.methodName_legacy = null;
            }
            else {
                this.packetClass_legacy = packetClass;
                this.enumClassAction_legacy = actionClass;
                this.methodGetAction_legacy = methodGetAction;
                this.methodName_legacy = ReflectionUtil.getMethodNoArgs(enumClassAction_legacy, "name", String.class);
            }
            // methodName_legacy can be null if not supported
        }

        String getActionFromNMSPacket(Object handle) {
            final Class<?> clazz = handle.getClass();
            if (clazz != packetClass_legacy) {
                return null;
            }
            final Object action = ReflectionUtil.invokeMethodNoArgs(methodGetAction_legacy, handle);
            if (action == null) {
                return null;
            }
            final Object actionName = ReflectionUtil.invokeMethodNoArgs(methodName_legacy, action);
            if (actionName instanceof String) {
                return (String) actionName;
            }
            else {
                return null;
            }
        }
    }

    private static final int INTERPRETED = 0x01;
    private static final int ATTACK = 0x02;

    private final AttackFrequency attackFrequency;

    private final LegacyReflectionSet legacySet;

    private static class PlayerContext {
        final Player player;
        final IPlayerData pData;

        PlayerContext(Player player, IPlayerData pData) {
            this.player = player;
            this.pData = pData;
        }

        static PlayerContext from(PacketEvent event) {
            if (event == null) {
                return null;
            }
            if (isTemporary(event)) {
                return null;
            }
            final Player player = event.getPlayer();
            if (player == null) {
                return null;
            }
            final IPlayerData pData = DataManager.getInstance().getPlayerDataSafe(player);
            if (pData == null) {
                return null;
            }
            return new PlayerContext(player, pData);
        }

        private static boolean isTemporary(PacketEvent event) {
            try {
                return event.isPlayerTemporary();
            } catch (NoSuchMethodError e) {
                return false;
            }
        }
    }

    private static class UseActionResult {
        final boolean interpreted;
        final boolean attack;

        UseActionResult(boolean interpreted, boolean attack) {
            this.interpreted = interpreted;
            this.attack = attack;
        }
    }

    public UseEntityAdapter(Plugin plugin) {
        super(plugin, PacketType.Play.Client.USE_ENTITY);
        this.checkType = CheckType.NET_ATTACKFREQUENCY;
        // Add feature tags for checks.
        if (NCPAPIProvider.getNoCheatPlusAPI().getWorldDataManager().isActiveAnywhere(
                CheckType.NET_ATTACKFREQUENCY)) {
            NCPAPIProvider.getNoCheatPlusAPI().addFeatureTags(
                    "checks", Collections.singletonList(AttackFrequency.class.getSimpleName()));
        }
        attackFrequency = new AttackFrequency();
        NCPAPIProvider.getNoCheatPlusAPI().addComponent(attackFrequency);
        this.legacySet = getLegacyReflectionSet();
    }

    private LegacyReflectionSet getLegacyReflectionSet() {
        for (String versionDetail : new String[] {"v1_7_R4", "v1_7_R1"}) {
            try {
                return new LegacyReflectionSet(versionDetail);
            }
            catch (RuntimeException e) {
                // ignore - version detail not supported
            }
        }
        return null;
    }

    private PlayerContext extractPlayerContext(PacketEvent event) {
        return PlayerContext.from(event);
    }

    private UseActionResult parseAction(PacketContainer packet) {
        boolean attack = false;
        boolean interpreted = false;
        if (legacySet != null) {
            final int flags = getActionLegacy(packet);
            if ((flags & INTERPRETED) != 0) {
                interpreted = true;
                if ((flags & ATTACK) != 0) {
                    attack = true;
                }
            }
        }
        if (!interpreted) {
            final StructureModifier<EntityUseAction> actions = packet.getEntityUseActions();
            if (actions != null && actions.size() == 1 && actions.read(0) == EntityUseAction.ATTACK) {
                attack = true;
                interpreted = true;
            }
        }
        return new UseActionResult(interpreted, attack);
    }

    @Override
    public void onPacketReceiving(final PacketEvent event) {
        final PlayerContext context = extractPlayerContext(event);
        if (context == null) {
            return;
        }

        final long time = System.currentTimeMillis();
        final NetData data = context.pData.getGenericInstance(NetData.class);
        data.lastKeepAliveTime = time;

        if (!context.pData.isCheckActive(CheckType.NET_ATTACKFREQUENCY, context.player)) {
            return;
        }

        final UseActionResult result = parseAction(event.getPacket());
        if (!result.interpreted) {
            return;
        }

        if (result.attack) {
            final NetConfig cc = context.pData.getGenericInstance(NetConfig.class);
            if (attackFrequency.isEnabled(context.player, context.pData)
                    && attackFrequency.check(context.player, time, data, cc, context.pData)) {
                event.setCancelled(true);
            }
        }
    }

    private int getActionLegacy(final PacketContainer packetContainer) {
        // (For some reason the object didn't appear work with equality checks, thus compare the short string.)
        final String actionName = legacySet.getActionFromNMSPacket(packetContainer.getHandle());
        return actionName == null ? 0 : (INTERPRETED | ("ATTACK".equals(actionName) ? ATTACK : 0));
    }

}
