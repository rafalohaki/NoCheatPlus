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

    static class BurnInfo {
        final int burnStart;
        final int empty;
        BurnInfo(int burnStart, int empty) {
            this.burnStart = burnStart;
            this.empty = empty;
        }
    }

    static BurnInfo computeBurnInfo(final ActionFrequency packetFreq) {
        final int winNum = packetFreq.numberOfBuckets();
        final float[] bucketScores = new float[winNum];
        for (int i = 0; i < winNum; i++) {
            bucketScores[i] = packetFreq.bucketScore(i);
        }
        int burnStart = winNum;
        int empty = 0;
        boolean firstUsed = false;
        boolean counting = false;
        for (int i = 1; i < winNum; i++) {
            final float score = bucketScores[i];
            if (score > 0f) {
                if (!firstUsed) {
                    firstUsed = true;
                } else if (!counting) {
                    burnStart = i;
                    counting = true;
                }
            } else if (counting) {
                empty++;
            }
        }
        return new BurnInfo(burnStart, empty);
    }

    private static void relaxBursts(final ActionFrequency packetFreq, final float[] bucketScores,
            final long time, final long winDur, final long totalDur, final float maxPackets) {
        final long tDiff = time - packetFreq.lastAccess();
        if (tDiff >= winDur && tDiff < totalDur) {
            float firstBucketScore = bucketScores[0];
            if (firstBucketScore > maxPackets) {
                firstBucketScore -= maxPackets;
                for (int i = 1; i < bucketScores.length; i++) {
                    final float currentBucketScore = bucketScores[i];
                    if (currentBucketScore < maxPackets) {
                        final float consume = Math.min(firstBucketScore, maxPackets - currentBucketScore);
                        firstBucketScore -= consume;
                        bucketScores[i] = currentBucketScore + consume;
                        packetFreq.setBucket(i, bucketScores[i]);
                        if (currentBucketScore > 0f) {
                            break;
                        }
                    } else {
                        break;
                    }
                }
                bucketScores[0] = maxPackets + firstBucketScore;
                packetFreq.setBucket(0, bucketScores[0]);
            }
        }
    }

    private static double applyBurstViolations(final ActionFrequency packetFreq, final ActionFrequency burstFreq,
            final float burstPackets, final double burstDirect, final double burstEPM, final long time,
            final long winDur, final List<String> tags) {
        double violation = 0.0;
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
                        - burstEPM
                        * (double) (burstFreq.bucketDuration() * burstFreq.numberOfBuckets()) / 60000.0;
                if (vBurstEPM > 0.0) {
                    violation = Math.max(violation, vBurstEPM);
                    tags.add("burstepm");
                }
            }
        }
        return violation;
    }

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
    public static double morePacketsCheck(final ActionFrequency packetFreq, final long time, final float packets, final float maxPackets, final float idealPackets, final ActionFrequency burstFreq, final float burstPackets, final double burstDirect, final double burstEPM, final List<String> tags) {
        // Note: this logic could be refactored into a dedicated PacketFrequency class.
        // Pull down stuff.
        final long winDur = packetFreq.bucketDuration();
        final int winNum = packetFreq.numberOfBuckets();
        final long totalDur = winDur * winNum;
        final float[] bucketScores = new float[winNum];
        for (int i = 0; i < winNum; i++) {
            bucketScores[i] = packetFreq.bucketScore(i);
        }
        relaxBursts(packetFreq, bucketScores, time, winDur, totalDur, maxPackets);


        // Add packet to frequency count.
        packetFreq.add(time, packets);

        // Fill up all "used" time windows (minimum we can do without other events).
        final float burnScore = (float) idealPackets * (float) winDur / 1000f;
        final BurnInfo burnInfo = computeBurnInfo(packetFreq);
        final int burnStart = burnInfo.burnStart;
        int empty = burnInfo.empty;

        // Future: burn time windows based on other activity counting, such as matching ActinFrequency with keep-alive packets.

        // Adjust empty based on server side lag, this makes the check more strict.
        if (empty > 0) {
            // Consider adding a configuration flag to skip lag adaption when running in strict mode.
            final float lag = TickTask.getLag(totalDur, true); // Full seconds range considered.
            // Also consider increasing the allowed maximum for extreme server-side lag conditions.
            empty = Math.max(0, Math.min(empty, (int) Math.round((lag - 1f) * winNum)));
        }

        final double fullCount;
        if (burnStart < winNum) {
            // Assume all following time windows are burnt.
            // Revisit trailing score calculation to properly account for empty buckets.
            final float trailing = Math.max(packetFreq.trailingScore(burnStart, 1f), burnScore * (winNum - burnStart - empty));
            final float leading = packetFreq.leadingScore(burnStart, 1f);
            fullCount = leading + trailing;
        } else {
            // All time windows are used.
            fullCount = packetFreq.score(1f);
        }

        double violation = 0.0; // Classic processing.
        final double vEPSAcc = (double) fullCount - (double) (maxPackets * winNum * winDur / 1000f);
        if (vEPSAcc > 0.0) {
            violation = Math.max(violation, vEPSAcc);
            tags.add("epsacc");
        }

        violation = Math.max(violation,
                applyBurstViolations(packetFreq, burstFreq, burstPackets, burstDirect, burstEPM, time, winDur, tags));

        return Math.max(0.0, violation);
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
