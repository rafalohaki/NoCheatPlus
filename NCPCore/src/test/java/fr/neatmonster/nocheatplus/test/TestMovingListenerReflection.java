package fr.neatmonster.nocheatplus.test;

import fr.neatmonster.nocheatplus.checks.moving.MovingListener;
import fr.neatmonster.nocheatplus.checks.moving.MovementContext;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class TestMovingListenerReflection {

    @Test
    public void testDetermineEarlyReturnExists() throws Exception {
        Method m = MovingListener.class.getDeclaredMethod(
                "determineEarlyReturn",
                MovementContext.class
        );
        assertNotNull(m);
    }
}
