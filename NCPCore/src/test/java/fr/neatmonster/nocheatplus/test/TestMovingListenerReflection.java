package fr.neatmonster.nocheatplus.test;

import fr.neatmonster.nocheatplus.checks.moving.MovingListener;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class TestMovingListenerReflection {

    @Test
    public void testDetermineEarlyReturnExists() throws Exception {
        Method m = MovingListener.class.getDeclaredMethod(
                "determineEarlyReturn",
                Player.class,
                Location.class,
                Location.class,
                PlayerMoveEvent.class,
                MovingData.class,
                MovingConfig.class,
                IPlayerData.class
        );
        assertNotNull(m);
    }
}
