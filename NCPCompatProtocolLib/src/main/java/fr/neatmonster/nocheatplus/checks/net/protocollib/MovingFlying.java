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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.PacketType.Protocol;
import com.comphenix.protocol.PacketType.Sender;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.net.FlyingFrequency;
import fr.neatmonster.nocheatplus.checks.net.Moving;
import fr.neatmonster.nocheatplus.checks.net.NetConfig;
import fr.neatmonster.nocheatplus.checks.net.NetData;
import fr.neatmonster.nocheatplus.checks.net.model.DataPacketFlying;
import fr.neatmonster.nocheatplus.checks.net.model.DataPacketFlying.PACKET_CONTENT;
import fr.neatmonster.nocheatplus.checks.net.model.TeleportQueue.AckReference;
import fr.neatmonster.nocheatplus.compat.AlmostBoolean;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.time.monotonic.Monotonic;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.ds.count.ActionFrequency;
import fr.neatmonster.nocheatplus.utilities.location.LocUtil;
import fr.neatmonster.nocheatplus.utilities.location.TrigUtil;
import fr.neatmonster.nocheatplus.worlds.IWorldData;

/**
 * Run checks related to moving (pos/look/flying). Skip packets that shouldn't
 * get processed anyway due to a teleport. Also update lastKeepAliveTime.
 * 
 * @author dev1mc
 *
 */
public class MovingFlying extends BaseAdapter {

    // Setup for flying packets.
    public static final int indexOnGround = 0;
    public static final int indexhasPos = 1;
    public static final int indexhasLook = 2;
    public static final int indexX = 0;
    public static final int indexY = 1;
    public static final int indexZ = 2;
    /** 1.7.10 */
    public static final int indexStance = 3;
    public static final int indexYaw = 0;
    public static final int indexPitch = 1;

    // Setup for teleport accept packet.
    private static PacketType confirmTeleportType;
    private static boolean acceptConfirmTeleportPackets;

    private final Plugin plugin = Bukkit.getPluginManager().getPlugin("NoCheatPlus");
    private static PacketType[] initPacketTypes() {
        final List<PacketType> types = new LinkedList<PacketType>(Arrays.asList(
                PacketType.Play.Client.LOOK,
                PacketType.Play.Client.POSITION,
                PacketType.Play.Client.POSITION_LOOK
        ));
        if (ServerVersion.compareMinecraftVersion("1.17") < 0) {
            types.add(PacketType.Play.Client.FLYING);
            StaticLog.logInfo("Add listener for legacy PlayInFlying packet.");
        } 
        else types.add(PacketType.Play.Client.GROUND);
        // Add confirm teleport.
        // PacketPlayInTeleportAccept
        confirmTeleportType = ProtocolLibComponent.findPacketTypeByName(Protocol.PLAY, Sender.CLIENT, "AcceptTeleportation");
        if (confirmTeleportType == null) { // Fallback check for the old packet name.
            confirmTeleportType = ProtocolLibComponent.findPacketTypeByName(Protocol.PLAY, Sender.CLIENT, "TeleportAccept");
        }

        if (confirmTeleportType != null && ServerVersion.compareMinecraftVersion("1.9") >= 0) {
            StaticLog.logInfo("Confirm teleport packet available (via name): " + confirmTeleportType);
            types.add(confirmTeleportType);
            acceptConfirmTeleportPackets = true;
        } else {
            acceptConfirmTeleportPackets = false;
        }

        return types.toArray(new PacketType[types.size()]);
    }

    /** Frequency check for flying packets. */
    private final FlyingFrequency flyingFrequency = new FlyingFrequency();
    /** Other checks related to packet content. */
    private final Moving moving = new Moving();
    private final int idFlying = counters.registerKey("packet.flying");
    private final int idAsyncFlying = counters.registerKey("packet.flying.asynchronous");
    /** If a packet can't be parsed, this time stamp is set for occasional logging. */
    private long packetMismatch = Long.MIN_VALUE;
    private long packetMismatchLogFrequency = 60000; // Every minute max, good for updating :).
    private final HashSet<PACKET_CONTENT> validContent = new LinkedHashSet<PACKET_CONTENT>();

    public MovingFlying(Plugin plugin) {
        // PacketPlayInFlying[3, legacy: 10]
        super(plugin, ListenerPriority.LOW, initPacketTypes());
        // Keep the CheckType NET for now.
        // Add feature tags for checks.
        if (NCPAPIProvider.getNoCheatPlusAPI().getWorldDataManager().isActiveAnywhere(CheckType.NET_FLYINGFREQUENCY)) {
            NCPAPIProvider.getNoCheatPlusAPI().addFeatureTags( "checks", Collections.singletonList(FlyingFrequency.class.getSimpleName()));
        }
        NCPAPIProvider.getNoCheatPlusAPI().addComponent(flyingFrequency);
    }

    @Override
    public void onPacketReceiving(final PacketEvent event) {
        try {
            if (event.isPlayerTemporary()) return;
        } 
        catch(NoSuchMethodError e) {
            if (event.getPlayer() == null) return;
            if (DataManager.getInstance().getPlayerDataSafe(event.getPlayer()) == null) return;
        }
        if (event.getPacketType().equals(confirmTeleportType)) {
            if (acceptConfirmTeleportPackets) {
                onConfirmTeleportPacket(event);
            }
        }
        else onFlyingPacket(event);
    }

    private void onConfirmTeleportPacket(final PacketEvent event) {
        try {
            processConfirmTeleport(event);
        }
        catch (Throwable t) {
            noConfirmTeleportPacket();
        }
    }

    private void processConfirmTeleport(final PacketEvent event) {
        final PacketContainer packet = event.getPacket();
        final StructureModifier<Integer> integers = packet.getIntegers();
        if (integers.size() != 1) {
            noConfirmTeleportPacket();
            return;
        }
        Integer teleportId = integers.read(0);
        if (teleportId == null) {
            return;
        }
        final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getInstance().getPlayerDataSafe(player);
        final NetData data = pData.getGenericInstance(NetData.class);
        final AlmostBoolean matched = data.teleportQueue.processAck(teleportId);
        if (matched.decideOptimistically()) {
            ActionFrequency.subtract(System.currentTimeMillis(), 1, data.flyingFrequencyAll);
        }
        if (pData.isDebugActive(this.checkType)) { 
            debug(player, "Confirm teleport packet" + (matched.decideOptimistically() ? (" (matched=" + matched + ")") : "") + ": " + teleportId);
        }
    }

    private void noConfirmTeleportPacket() {
        acceptConfirmTeleportPackets = false;
        NCPAPIProvider.getNoCheatPlusAPI().getLogManager().info(Streams.STATUS, "Confirm teleport packet not available.");
    }

    private void onFlyingPacket(final PacketEvent event) {
        final boolean primaryThread = Bukkit.isPrimaryThread();
        trackAsync(primaryThread, event);

        final long time = System.currentTimeMillis();
        final Player player = event.getPlayer();
        if (player == null) {
            handleNullPlayer(event, primaryThread);
            return;
        }

        final IPlayerData pData = DataManager.getInstance().getPlayerDataSafe(player);
        final NetData data = pData.getGenericInstance(NetData.class);
        data.lastKeepAliveTime = time;
        if (!pData.getCurrentWorldDataSafe().isCheckActive(CheckType.NET_FLYINGFREQUENCY)) {
            return;
        }

        final NetConfig cc = pData.getGenericInstance(NetConfig.class);
        final DataPacketFlying packetData = interpretPacket(event, time);

        boolean cancel = false;
        boolean skipFlyingFrequency = false;
        if (packetData != null) {
            final int result = processPacketData(event, player, pData, data, cc, packetData);
            if (event.isCancelled()) {
                if (pData.isDebugActive(this.checkType)) {
                    debug(player, packetData.toString() + " CANCEL");
                }
                return;
            }
            cancel = (result & RESULT_CANCEL) != 0;
            skipFlyingFrequency = (result & RESULT_SKIP_FLY) != 0;
        }

        if (!cancel && shouldCancelFrequency(player, pData, packetData, time, data, cc, skipFlyingFrequency)) {
            cancel = true;
        }

        if (!cancel && shouldCancelMoving(player, pData, packetData, data, cc, skipFlyingFrequency)) {
            cancel = true;
        }

        if (cancel) {
            event.setCancelled(true);
        }

        if (pData.isDebugActive(this.checkType)) {
            debug(player, (packetData == null ? "(Incompatible data)" : packetData.toString()) + (event.isCancelled() ? " CANCEL" : ""));
        }
    }

    private void trackAsync(final boolean primaryThread, final PacketEvent event) {
        counters.add(idFlying, 1, primaryThread);
        if (event.isAsync() == primaryThread) {
            counters.add(ProtocolLibComponent.idInconsistentIsAsync, 1, primaryThread);
        }
        if (!primaryThread) {
            counters.addSynchronized(idAsyncFlying, 1);
        }
    }

    private void handleNullPlayer(final PacketEvent event, final boolean primaryThread) {
        counters.add(ProtocolLibComponent.idNullPlayer, 1, primaryThread);
        event.setCancelled(true);
    }

    private boolean shouldCancelFrequency(final Player player, final IPlayerData pData, final DataPacketFlying packetData,
            final long time, final NetData data, final NetConfig cc, final boolean skip) {
        return !skip && !pData.hasBypass(CheckType.NET_FLYINGFREQUENCY, player)
                && flyingFrequency.check(player, packetData, time, data, cc, pData);
    }

    private boolean shouldCancelMoving(final Player player, final IPlayerData pData, final DataPacketFlying packetData,
            final NetData data, final NetConfig cc, final boolean skip) {
        return !skip && !pData.hasBypass(CheckType.NET_MOVING, player)
                && moving.check(player, packetData, data, cc, pData, plugin);
    }

    private static final int RESULT_CANCEL = 1;
    private static final int RESULT_SKIP_FLY = 2;

    private int processPacketData(final PacketEvent event, final Player player, final IPlayerData pData,
            final NetData data, final NetConfig cc, final DataPacketFlying packetData) {
        if (isInvalidContent(packetData)) {
            event.setCancelled(true);
            return RESULT_CANCEL;
        }

        int result = 0;
        switch (data.teleportQueue.processAck(packetData)) {
            case WAITING:
                if (pData.isDebugActive(this.checkType)) {
                    debug(player, "Incoming packet, still waiting for ACK on outgoing position.");
                }
                if (confirmTeleportType != null && cc.supersededFlyingCancelWaiting) {
                    final AckReference ackReference = data.teleportQueue.getLastAckReference();
                    if (ackReference.lastOutgoingId != Integer.MIN_VALUE
                            && ackReference.lastOutgoingId != ackReference.maxConfirmedId) {
                        result |= RESULT_CANCEL;
                    }
                }
                break;
            case ACK:
                result |= RESULT_SKIP_FLY;
                if (pData.isDebugActive(this.checkType)) {
                    debug(player, "Incoming packet, interpret as ACK for outgoing position.");
                }
                //$FALL-THROUGH$
            default:
                data.addFlyingQueue(packetData);
                break;
        }
        validContent.add(packetData.getSimplifiedContentType());
        return result;
    }



    private boolean isInvalidContent(final DataPacketFlying packetData) {
        if (packetData.hasPos && LocUtil.isBadCoordinate(packetData.getX(), packetData.getY(), packetData.getZ())) {
            return true;
        }
        return packetData.hasLook && LocUtil.isBadCoordinate(packetData.getYaw(), packetData.getPitch());
    }

    /**
     * Interpret the packet content and do with it whatever is suitable.
     * @param player
     * @param event
     * @param allScore
     * @param time
     * @param data
     * @param cc
     * @return Packet data if successful, or null on packet mismatch.
     */
    private DataPacketFlying interpretPacket(final PacketEvent event, final long time) {

        final PacketContainer packet = event.getPacket();
        final List<Boolean> booleans = packet.getBooleans().getValues();
        if (!isValidBooleanSize(booleans)) {
            packetMismatch(event);
            return null;
        }

        final boolean onGround = booleans.get(MovingFlying.indexOnGround);
        final boolean hasPos = booleans.get(MovingFlying.indexhasPos);
        final boolean hasLook = booleans.get(MovingFlying.indexhasLook);

        if (!hasPos && !hasLook) {
            return new DataPacketFlying(onGround, time);
        }

        final List<Double> doubles = hasPos ? packet.getDoubles().getValues() : null;
        if (hasPos && !isValidPosSize(doubles)) {
            packetMismatch(event);
            return null;
        }

        final List<Float> floats = hasLook ? packet.getFloat().getValues() : null;
        if (hasLook && !isValidLookSize(floats)) {
            packetMismatch(event);
            return null;
        }

        return createPacketData(onGround, hasPos, hasLook, doubles, floats, time);
    }

    private boolean isValidBooleanSize(final List<Boolean> booleans) {
        return booleans.size() == 3 || booleans.size() == 4;
    }

    private boolean isValidPosSize(final List<Double> doubles) {
        return doubles != null && (doubles.size() == 3 || doubles.size() == 4);
    }

    private boolean isValidLookSize(final List<Float> floats) {
        return floats != null && floats.size() == 2;
    }

    private DataPacketFlying createPacketData(final boolean onGround, final boolean hasPos,
            final boolean hasLook, final List<Double> doubles, final List<Float> floats, final long time) {
        if (hasPos && hasLook) {
            return new DataPacketFlying(onGround, doubles.get(indexX), doubles.get(indexY), doubles.get(indexZ),
                    floats.get(indexYaw), floats.get(indexPitch), time);
        }
        if (hasLook) {
            return new DataPacketFlying(onGround, floats.get(indexYaw), floats.get(indexPitch), time);
        }
        if (hasPos) {
            return new DataPacketFlying(onGround, doubles.get(indexX), doubles.get(indexY), doubles.get(indexZ), time);
        }
        throw new IllegalStateException(
                "Unexpected state: neither hasPos nor hasLook is true, but they were checked earlier");
    }

    /**
     * Log warning to console, stop interpreting packet content.
     */
    private void packetMismatch(final PacketEvent packetEvent) {
        final long time = Monotonic.synchMillis();
        if (time - packetMismatchLogFrequency > packetMismatch) {
            packetMismatch = time;
            StringBuilder builder = new StringBuilder(512);
            builder.append(CheckUtils.getLogMessagePrefix(packetEvent.getPlayer(), checkType));
            builder.append("Incoming packet could not be interpreted. Are server and plugins up to date (NCP/ProtocolLib...)? This message is logged every ");
            builder.append(packetMismatchLogFrequency / 1000);
            builder.append(" seconds, disregarding for which player this happens.");
            if (!validContent.isEmpty()) {
                builder.append(" On other occasion, valid content was received for: ");
                StringUtil.join(validContent, ", ", builder);
            }
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().warning(Streams.STATUS, builder.toString());
        }
    }
}
