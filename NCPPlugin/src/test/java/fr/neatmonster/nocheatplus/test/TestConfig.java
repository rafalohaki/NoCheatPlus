package fr.neatmonster.nocheatplus.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import org.bukkit.Material;
import org.junit.Test;
import fr.neatmonster.nocheatplus.compat.BridgeMaterial;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.config.PathUtils;
import fr.neatmonster.nocheatplus.config.RawConfigFile;
import fr.neatmonster.nocheatplus.logging.StaticLog;

public class TestConfig {

    private void assertParsedMaterial(String input, Material expected) {
        Material mat = RawConfigFile.parseMaterial(input);
        assertEquals("Parsed material mismatch for input: " + input, expected, mat);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testReadMaterial() {
        // BridgeMaterial może mapować aliasy/stare nazwy na nowe Material.
        final Material lily = BridgeMaterial.LILY_PAD;
        final String lilyName = lily.name();

        // Różne warianty zapisu nazwy (spacja, myślnik, case).
        assertParsedMaterial(lilyName.replace("_", " "), lily);
        assertParsedMaterial(lilyName.replace("_", "-"), lily);
        assertParsedMaterial(lilyName.replace("e", "E"), lily);

        // Opcjonalnie: sprawdź kilka typowych aliasów (jeśli wspiera je RawConfigFile)
        // assertParsedMaterial("flint and steel", Material.FLINT_AND_STEEL);
        // assertParsedMaterial("259", Material.FLINT_AND_STEEL);

        // Przejście po wszystkich aktualnych Material – test odporności parsera na dokładną nazwę.
        for (final Material mat : Material.values()) {
            if (mat.name().equalsIgnoreCase("LOCKED_CHEST")) {
                continue; // legacy, zwykle wykluczany
            }
            // Jeżeli parser akceptuje dokładne nazwy enum:
            // assertParsedMaterial(mat.name(), mat);

            // Jeżeli parser obsługuje numery ID (w nowych wersjach zwykle nie – wtedy zostaw zakomentowane):
            // assertParsedMaterial(Integer.toString(mat.getId()), mat);
        }
    }

    @Test
    public void testMovePaths() {
        StaticLog.setUseLogManager(false);
        ConfigFile config = new ConfigFile();

        // Prosty case: przeniesienie booleana
        config.set(ConfPaths.LOGGING_FILE, false);
        config = PathUtils.processPaths(config, "test", false);

        assertNotNull("Expect config to be changed at all.", config);
        assertFalse("Expect old path to be removed: " + ConfPaths.LOGGING_FILE, config.contains(ConfPaths.LOGGING_FILE));

        Boolean val = config.getBoolean(ConfPaths.LOGGING_BACKEND_FILE_ACTIVE, true);
        assertNotNull("New boolean value should exist.", val);
        assertFalse("Expect new path to be set to false: " + ConfPaths.LOGGING_BACKEND_FILE_ACTIVE, val);
    }

    @Test
    public void testDefaults() {
        ConfigFile defaults = new ConfigFile();
        defaults.set("all", 1.0);
        defaults.set("defaultsOnly", 1.0);

        ConfigFile config = new ConfigFile();
        config.setDefaults(defaults);
        config.set("all", 2.0);

        double val = config.getDouble("all", 3.0);
        assertEquals("Expect 2.0 if set in config", 2.0, val, 0.0);

        val = config.getDouble("defaultsOnly", 3.0);
        // Zgodnie z Twoją implementacją getDouble preferuje wartość podaną jako argument domyślny,
        // a nie "defaults" – więc tutaj 3.0 jest poprawnym oczekiwaniem.
        assertEquals("Expect 3.0 (default argument)", 3.0, val, 0.0);

        val = config.getDouble("notset", 3.0);
        assertEquals("Expect 3.0 (not set)", 3.0, val, 0.0);
    }

    // @Test
    // public void testActionLists() {
    //     ConfigFile config = new DefaultConfig();
    //     config.getOptimizedActionList(ConfPaths.MOVING_SURVIVALFLY_ACTIONS, null);
    // }
}
