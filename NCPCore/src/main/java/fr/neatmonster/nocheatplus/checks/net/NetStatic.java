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
        int burnStart = winNum;
        int empty = 0;
        boolean firstUsed = false;
        boolean counting = false;
        final float[] bucketScores = snapshotBucketScores(packetFreq);
        for (int i = 1; i < winNum; i++) {
            final float bucket = bucketScores[i];
            if (bucket > 0f) {
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

    /**
     * Snapshot all bucket scores from the given frequency instance.
     *
     * @param packetFreq the packet frequency source
     * @return an array of bucket scores
     */
    private static float[] snapshotBucketScores(final ActionFrequency packetFreq) {
        final int winNum = packetFreq.numberOfBuckets();
        final float[] scores = new float[winNum];
        for (int i = 0; i < winNum; i++) {
            scores[i] = packetFreq.bucketScore(i);
        }
        return scores;
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
    public static double morePacketsCheck(final ActionFrequency packetFreq, final long time, final float packets,
            final float maxPackets, final float idealPackets, final ActionFrequency burstFreq, final float burstPackets,
            final double burstDirect, final double burstEPM, final List<String> tags) {
        // Note: this logic could be refactored into a dedicated PacketFrequency class.
        final long winDur = packetFreq.bucketDuration();
        final int winNum = packetFreq.numberOfBuckets();
        final long totalDur = winDur * winNum;

        relaxBurstBuckets(packetFreq, time, maxPackets, winDur, winNum, totalDur);

        packetFreq.add(time, packets);

        final float burnScore = (float) idealPackets * (float) winDur / 1000f;
        final BurnInfo burnInfo = computeBurnInfo(packetFreq);
        final int burnStart = burnInfo.burnStart;
        int empty = adjustEmptyForLag(burnInfo.empty, totalDur, winNum);

        final double fullCount = computeFullCount(packetFreq, burnScore, burnStart, empty, winNum);

        return computeViolations(packetFreq, burstFreq, burstPackets, burstDirect, burstEPM, time, fullCount,
                maxPackets, winDur, winNum, tags);
    }

    private static void relaxBurstBuckets(final ActionFrequency packetFreq, final long time, final float maxPackets,
            final long winDur, final int winNum, final long totalDur) {
        final long tDiff = time - packetFreq.lastAccess();
        if (tDiff < winDur || tDiff >= totalDur) {
            return;
        }

        final float[] originalBucketScores = snapshotBucketScores(packetFreq);
        float firstBucketScore = originalBucketScores[0];
        if (firstBucketScore <= maxPackets) {
            return;
        }

        firstBucketScore -= maxPackets;
        for (int i = 1; i < winNum; i++) {
            final float currentBucketScore = originalBucketScores[i];
            if (currentBucketScore >= maxPackets) {
                break;
            }
            final float consume = Math.min(firstBucketScore, maxPackets - currentBucketScore);
            firstBucketScore -= consume;
            packetFreq.setBucket(i, currentBucketScore + consume);
            if (currentBucketScore > 0f) {
                break;
            }
        }
        packetFreq.setBucket(0, maxPackets + firstBucketScore);
    }

    /**
     * Adjust the number of empty windows for server lag in a conservative way.
     * <p>
     * Negative values are ignored to avoid increasing the violation score when
     * lag measurements would otherwise suggest shrinking the window. This keeps
     * the check strict under normal conditions while still allowing extra room
     * when the server is actually lagging behind.
     * <p>
     * The scaling is applied only if the measured lag is at least {@code 1.0f}
     * to avoid unintended leniency during optimal tick conditions.
     */
    private static int adjustEmptyForLag(int empty, final long totalDur, final int winNum) {
        if (empty <= 0) {
            return 0;
        }
        final float lag = TickTask.getLag(totalDur, true);
        if (lag >= 1.0f) {
            empty = (int) Math.round(empty * (1f / lag));
            empty = Math.max(0, Math.min(winNum, empty));
        }
        return empty;
    }

    private static double computeFullCount(final ActionFrequency packetFreq, final float burnScore, final int burnStart,
            final int empty, final int winNum) {
        if (burnStart < winNum) {
            final float trailing = Math.max(packetFreq.trailingScore(burnStart, 1f),
                    burnScore * (winNum - burnStart - empty));
            final float leading = packetFreq.leadingScore(burnStart, 1f);
            return leading + trailing;
        }
        return packetFreq.score(1f);
    }

    private static double computeViolations(final ActionFrequency packetFreq, final ActionFrequency burstFreq,
            final float burstPackets, final double burstDirect, final double burstEPM, final long time,
            final double fullCount, final float maxPackets, final long winDur, final int winNum,
            final List<String> tags) {
        double violation = 0.0;
        final double vEPSAcc = (double) fullCount - (double) (maxPackets * winNum * winDur / 1000f);
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
                        - burstEPM * (double) (burstFreq.bucketDuration() * burstFreq.numberOfBuckets()) / 60000.0;
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
