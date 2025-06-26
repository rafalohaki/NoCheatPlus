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
    public static double morePacketsCheck(final ActionFrequency packetFreq, final long time, final float packets,
            final float maxPackets, final float idealPackets, final ActionFrequency burstFreq,
            final float burstPackets, final double burstDirect, final double burstEPM, final List<String> tags) {
        // Note: this logic could be refactored into a dedicated PacketFrequency class.
        final long winDur = packetFreq.bucketDuration();
        final int winNum = packetFreq.numberOfBuckets();
        final long totalDur = winDur * winNum;

        // Smooth excessive first-bucket burst into following buckets.
        relaxBurst(packetFreq, time, maxPackets, winDur, winNum, totalDur);

        // Add packet to frequency count and cache current scores.
        packetFreq.add(time, packets);
        final float[] scores = cacheScores(packetFreq, winNum);

        final int burnStart = findSecondUsedBucket(scores, winNum);
        int empty = burnStart < winNum ? countEmptyAfter(scores, burnStart, winNum) : 0;
        final float burnScore = idealPackets * winDur / 1000f;

        // Future: burn time windows based on other activity counting, such as matching ActinFrequency with keep-alive packets.

        // Adjust empty based on server side lag, this makes the check more strict.
        if (empty > 0) {
            final float lag = TickTask.getLag(totalDur, true); // Full seconds range considered.
            empty = Math.min(empty, (int) Math.round((lag - 1f) * winNum));
        }

        final double fullCount = computeFullCount(scores, burnStart, empty, burnScore, winNum);

        return computeViolation(scores, burstFreq, time, burstPackets, burstDirect, burstEPM, fullCount,
                maxPackets, winDur, winNum, tags);
    }

    private static void relaxBurst(final ActionFrequency packetFreq, final long time, final float maxPackets,
            final long winDur, final int winNum, final long totalDur) {
        final long tDiff = time - packetFreq.lastAccess();
        if (tDiff >= winDur && tDiff < totalDur) {
            float firstBucketScore = packetFreq.bucketScore(0);
            if (firstBucketScore > maxPackets) {
                firstBucketScore -= maxPackets;
                for (int i = 1; i < winNum; i++) {
                    final float sci = packetFreq.bucketScore(i);
                    if (sci < maxPackets) {
                        final float consume = Math.min(firstBucketScore, maxPackets - sci);
                        firstBucketScore -= consume;
                        packetFreq.setBucket(i, sci + consume);
                        if (sci > 0f) {
                            break;
                        }
                    } else {
                        break;
                    }
                }
                packetFreq.setBucket(0, maxPackets + firstBucketScore);
            }
        }
    }

    private static float[] cacheScores(final ActionFrequency packetFreq, final int winNum) {
        final float[] scores = new float[winNum];
        for (int i = 0; i < winNum; i++) {
            scores[i] = packetFreq.bucketScore(i);
        }
        return scores;
    }

    private static int findSecondUsedBucket(final float[] scores, final int winNum) {
        boolean used = false;
        for (int i = 1; i < winNum; i++) {
            if (scores[i] > 0f) {
                if (used) {
                    return i;
                }
                used = true;
            } else if (!used) {
                continue;
            } else {
                return winNum;
            }
        }
        return winNum;
    }

    private static int countEmptyAfter(final float[] scores, final int startIndex, final int winNum) {
        int empty = 0;
        for (int j = startIndex; j < winNum; j++) {
            if (scores[j] == 0f) {
                empty += 1;
            }
        }
        return empty;
    }

    private static double computeFullCount(final float[] scores, final int burnStart, final int empty,
            final float burnScore, final int winNum) {
        if (burnStart < winNum) {
            float trailing = 0f;
            for (int i = burnStart; i < winNum; i++) {
                trailing += scores[i];
            }
            trailing = Math.max(trailing, burnScore * (winNum - burnStart - empty));

            float leading = 0f;
            for (int i = 0; i < burnStart; i++) {
                leading += scores[i];
            }
            return leading + trailing;
        }

        float total = 0f;
        for (float score : scores) {
            total += score;
        }
        return total;
    }

    private static double computeViolation(final float[] scores, final ActionFrequency burstFreq,
            final long time, final float burstPackets, final double burstDirect, final double burstEPM,
            final double fullCount, final float maxPackets, final long winDur, final int winNum,
            final List<String> tags) {
        double violation = 0.0;
        final double vEPSAcc = fullCount - (maxPackets * winNum * winDur / 1000f);
        if (vEPSAcc > 0.0) {
            violation = Math.max(violation, vEPSAcc);
            tags.add("epsacc");
        }

        float burst = scores[0];
        if (burst > burstPackets) {
            burst /= TickTask.getLag(winDur, true);
            if (burst > burstPackets) {
                final double vBurstDirect = burst - burstDirect;
                if (vBurstDirect > 0.0) {
                    violation = Math.max(violation, vBurstDirect);
                    tags.add("burstdirect");
                }
                burstFreq.add(time, 1f);
                final double vBurstEPM = burstFreq.score(0f)
                        - burstEPM * (burstFreq.bucketDuration() * burstFreq.numberOfBuckets()) / 60000.0;
                if (vBurstEPM > 0.0) {
                    violation = Math.max(violation, vBurstEPM);
                    tags.add("burstepm");
                }
            }
        }
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
