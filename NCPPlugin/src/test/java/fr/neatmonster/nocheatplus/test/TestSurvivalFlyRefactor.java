package fr.neatmonster.nocheatplus.test;

import fr.neatmonster.nocheatplus.checks.moving.player.SurvivalFly;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class TestSurvivalFlyRefactor {

    @Test
    public void testHelperMethodsExist() throws Exception {
        String[] methods = {
                "handleResetVDist",
                "handleLastMoveVDist",
                "handleMissingVDist",
                "handleDirectionIncrease",
                "handleDirectionDecrease",
                "gatherEnvelopeFlags",
                "isVdistRelViolation"
        };
        for (String name : methods) {
            Method m = null;
            for (Method candidate : SurvivalFly.class.getDeclaredMethods()) {
                if (candidate.getName().equals(name)) { m = candidate; break; }
            }
            assertNotNull("Method missing: " + name, m);
        }
    }
}

