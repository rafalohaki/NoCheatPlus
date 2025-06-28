package fr.neatmonster.nocheatplus.test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

import fr.neatmonster.nocheatplus.logging.StaticLog;

public class TestFoliaLogging {

    @Test
    public void testLogSevereOutputsStackTrace() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream orig = System.out;
        System.setOut(new PrintStream(out));
        StaticLog.setUseLogManager(false);
        try {
            StaticLog.logSevere(new RuntimeException("boom"));
        } finally {
            System.setOut(orig);
        }
        String log = out.toString();
        assertTrue(log.contains("RuntimeException"));
    }
}
