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
package fr.neatmonster.nocheatplus.checks.net;

import java.util.List;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.components.data.ICheckData;
import fr.neatmonster.nocheatplus.components.data.IData;
import fr.neatmonster.nocheatplus.components.registry.factory.IFactoryOne;
import fr.neatmonster.nocheatplus.players.PlayerFactoryArgument;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.utilities.ds.count.ActionFrequency;
import fr.neatmonster.nocheatplus.worlds.WorldFactoryArgument;

/**
 * Static method utility for networking related stuff.
 * <hr>
 * Not sure about final location and naming... and content :p.
 * @author dev1mc
 *
 */
public class NetStatic {

    /**
     * Packet-cheating check, for catching clients that send more packets than
     * allowed. Intention is to have a more accurate check than just preventing
     * "extreme spamming".
     * 
     * @param packetFreq
     *            Records the packets. This check will update packetFreq
     *            according to the given time and packets.
     * @param time
     *            Milliseconds time to update the ActionFrequency instance with.
     * @param packets
     *            Amount to add to packetFreq with time.
     * @param maxPackets
     *            The amount of packets per second (!), that is considered
     *            legitimate.
     * @param idealPackets
     *            The "ideal" amount of packets per second. Used for "burning"
     *            time frames by setting them to this amount.
     * @param burstFreq Counting burst events, should be covering a minute or so.
     * @param burstPackets Packets in the first time window to add to burst count.
     * @param burstEPM Events per minute to trigger a burst violation.
     * @param tags List to add tags to, for which parts of this check triggered a violation.
     * @return The violation amount, i.e. "count above limit", 0.0 if no violation.
     */
    public static double morePacketsCheck(final ActionFrequency packetFreq, final long time,
            final float packets, final float maxPackets, final float idealPackets,
            final ActionFrequency burstFreq, final float burstPackets, final double burstDirect,
            final double burstEPM, final List<String> tags) {
        // Note: this logic could be refactored into a dedicated PacketFrequency class.
        final long winDur = packetFreq.bucketDuration();
        final int winNum = packetFreq.numberOfBuckets();
        final long totalDur = winDur * winNum;

        final long tDiff = time - packetFreq.lastAccess();
        relaxBurstBuckets(packetFreq, tDiff, winDur, winNum, totalDur, maxPackets);

        packetFreq.add(time, packets);

        final double violation = calculateViolation(packetFreq, winDur, winNum, totalDur,
                idealPackets, maxPackets, burstFreq, burstPackets, burstDirect, burstEPM, time,
                tags);
        return Math.max(0.0, violation);
    }

    /**
     * Smooth packet bursts after shifting the sliding window.
     *
     * <p>This redistributes overflow in the first bucket across following
     * buckets to keep overall packet counts consistent.</p>
     *
     * @param packetFreq The packet frequency structure to adjust.
     * @param tDiff Time since the last update in milliseconds.
     * @param winDur Duration of a single bucket in the sliding window.
     * @param winNum Number of buckets in the sliding window.
     * @param totalDur Combined duration of all buckets.
     * @param maxPackets Maximum allowed packets per bucket.
     */
    private static void relaxBurstBuckets(final ActionFrequency packetFreq, final long tDiff,
            final long winDur, final int winNum, final long totalDur, final float maxPackets) {
        if (tDiff >= winDur && tDiff < totalDur) {
            float sc0 = packetFreq.bucketScore(0);
            if (sc0 > maxPackets) {
                sc0 -= maxPackets;
                for (int i = 1; i < winNum; i++) {
                    final float sci = packetFreq.bucketScore(i);
                    if (sci < maxPackets) {
                        final float consume = Math.min(sc0, maxPackets - sci);
                        sc0 -= consume;
                        packetFreq.setBucket(i, sci + consume);
                        if (sci > 0f) {
                            break;
                        }
                    } else {
                        break;
                    }
                }
                packetFreq.setBucket(0, maxPackets + sc0);
            }
        }
    }

    /**
     * Compute violation levels for general packet spam and burst patterns.
     *
     * @param packetFreq Packet frequency data.
     * @param winDur Duration of a bucket in milliseconds.
     * @param winNum Number of buckets in the window.
     * @param totalDur Total window duration in milliseconds.
     * @param idealPackets Ideal packets per second used to fill gaps.
     * @param maxPackets Allowed packets per second before a violation occurs.
     * @param burstFreq Rolling frequency for detected bursts.
     * @param burstPackets Packet count considered a burst in the first bucket.
     * @param burstDirect Direct burst threshold.
     * @param burstEPM Allowed bursts per minute.
     * @param time Current timestamp used for updating burstFreq.
     * @param tags Collection to record violation tags.
     * @return Calculated violation amount, may be negative.
     */
    private static double calculateViolation(final ActionFrequency packetFreq, final long winDur,
            final int winNum, final long totalDur, final float idealPackets, final float maxPackets,
            final ActionFrequency burstFreq, final float burstPackets, final double burstDirect,
            final double burstEPM, final long time, final List<String> tags) {
        final float burnScore = idealPackets * winDur / 1000f;
        int burnStart;
        int empty = 0;
        boolean used = false;
        for (burnStart = 1; burnStart < winNum; burnStart++) {
            if (packetFreq.bucketScore(burnStart) > 0f) {
                if (used) {
                    for (int j = burnStart; j < winNum; j++) {
                        if (packetFreq.bucketScore(j) == 0f) {
                            empty += 1;
                        }
                    }
                    break;
                } else {
                    used = true;
                }
            }
        }

        if (empty > 0) {
            final float lag = TickTask.getLag(totalDur, true);
            empty = Math.min(empty, (int) Math.round((lag - 1f) * winNum));
        }

        final double fullCount;
        if (burnStart < winNum) {
            final float trailing = Math.max(packetFreq.trailingScore(burnStart, 1f),
                    burnScore * (winNum - burnStart - empty));
            final float leading = packetFreq.leadingScore(burnStart, 1f);
            fullCount = leading + trailing;
        } else {
            fullCount = packetFreq.score(1f);
        }

        double violation = 0.0;
        final double vEPSAcc = fullCount - (double) (maxPackets * winNum * winDur / 1000f);
        if (vEPSAcc > 0.0) {
            violation = Math.max(violation, vEPSAcc);
            tags.add("epsacc");
        }

        float burst = packetFreq.bucketScore(0);
        if (burst > burstPackets) {
            burst /= TickTask.getLag(winDur, true);
            if (burst > burstPackets) {
                final double vBurstDirect = burst - burstDirect;
                if (vBurstDirect > 0.0) {
                    violation = Math.max(violation, vBurstDirect);
                    tags.add("burstdirect");
                }
                burstFreq.add(time, 1f);
                final double vBurstEPM = (double) burstFreq.score(0f)
                        - burstEPM * (double) (burstFreq.bucketDuration()
                                * burstFreq.numberOfBuckets()) / 60000.0;
                if (vBurstEPM > 0.0) {
                    violation = Math.max(violation, vBurstEPM);
                    tags.add("burstepm");
                }
            }
        }
        return violation;
    }

    @SuppressWarnings("unchecked")
    public static void registerTypes() {
        final NoCheatPlusAPI api = NCPAPIProvider.getNoCheatPlusAPI();
        api.register(api.newRegistrationContext()
                // NetConfig
                .registerConfigWorld(NetConfig.class)
                .factory(arg -> new NetConfig(arg.worldData))
                .registerConfigTypesPlayer()
                .context() //
                // NetData
                .registerDataPlayer(NetData.class)
                .factory(arg -> new NetData(arg.playerData.getGenericInstance(NetConfig.class)))
                .addToGroups(CheckType.NET, true, IData.class, ICheckData.class)
                .context() //
                );
    }

}
