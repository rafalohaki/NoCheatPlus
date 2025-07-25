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
package fr.neatmonster.nocheatplus.checks.combined;

import java.util.Collection;

import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.access.ACheckData;
import fr.neatmonster.nocheatplus.components.data.IDataOnRemoveSubCheckData;
import fr.neatmonster.nocheatplus.utilities.PenaltyTime;
import fr.neatmonster.nocheatplus.utilities.ds.count.ActionFrequency;

/**
 * Stores runtime data for the combined checks. When the {@link CheckType#COMBINED}
 * group is removed the entire data instance gets discarded. Therefore the
 * {@code COMBINED} case in {@link #dataOnRemoveSubCheckData(Collection)} does
 * not reset fields explicitly, but simply signals removal by returning
 * {@code true}.
 */
public class CombinedData extends ACheckData implements IDataOnRemoveSubCheckData {

    // VLs
    public double improbableVL = 0;
    public double munchHausenVL = 0;

    // Invulnerable management:
    /** This is the tick from which on the player is vulnerable again. */
    public int invulnerableTick = Integer.MIN_VALUE;

    // Yawrate check.
    public float lastYaw;
    public long  lastYawTime;
    public float sumYaw;
    public final ActionFrequency yawFreq = new ActionFrequency(3, 333);

    // General penalty time. Used for fighting mainly, but not only close combat (!), set by yawrate check.
    public final PenaltyTime timeFreeze = new PenaltyTime();

    // Improbable check
    public final ActionFrequency improbableCount = new ActionFrequency(20, 3000);

    // General data
    // Note: may be replaced with PlayerData / OfflinePlayerData in the future
    public String lastWorld = "";
    public long lastJoinTime;
    public long lastLogoutTime;
    public long lastMoveTime;

    @Override
    public boolean dataOnRemoveSubCheckData(Collection<CheckType> checkTypes) {
        for (final CheckType checkType : checkTypes)
        {
            switch(checkType) {
                // Reset only the fields relevant for the individual sub checks.
                case COMBINED_IMPROBABLE:
                    improbableVL = 0;
                    improbableCount.clear(System.currentTimeMillis()); // Note: Document here which timestamp source should be used.
                    break;
                case COMBINED_YAWRATE:
                    yawFreq.clear(System.currentTimeMillis()); // Note: Document here which timestamp source should be used.
                    break;
                case COMBINED_MUNCHHAUSEN:
                    munchHausenVL = 0;
                    break;
                case COMBINED:
                    // The entire data instance will be removed, so no explicit
                    // field reset is required here.
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

}
