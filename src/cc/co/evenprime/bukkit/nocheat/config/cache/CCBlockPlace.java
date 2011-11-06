package cc.co.evenprime.bukkit.nocheat.config.cache;

import cc.co.evenprime.bukkit.nocheat.config.Configuration;
import cc.co.evenprime.bukkit.nocheat.config.util.ActionList;

/**
 * 
 */
public class CCBlockPlace {

    public final boolean    check;

    public final boolean    reachCheck;
    public final double     reachDistance;
    public final ActionList reachActions;

    public final boolean    directionCheck;
    public final ActionList directionActions;
    public final long       directionPenaltyTime;
    public final double     directionPrecision;

    public CCBlockPlace(Configuration data) {

        check = data.getBoolean(Configuration.BLOCKPLACE_CHECK);

        reachCheck = data.getBoolean(Configuration.BLOCKPLACE_REACH_CHECK);
        reachDistance = data.getInteger(Configuration.BLOCKPLACE_REACH_LIMIT);
        reachActions = data.getActionList(Configuration.BLOCKPLACE_REACH_ACTIONS);

        directionCheck = data.getBoolean(Configuration.BLOCKPLACE_DIRECTION_CHECK);
        directionPenaltyTime = data.getInteger(Configuration.BLOCKPLACE_DIRECTION_PENALTYTIME);
        directionPrecision = ((double) data.getInteger(Configuration.BLOCKPLACE_DIRECTION_PRECISION)) / 100D;
        directionActions = data.getActionList(Configuration.BLOCKPLACE_DIRECTION_ACTIONS);

    }
}
