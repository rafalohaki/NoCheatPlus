package fr.neatmonster.nocheatplus.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import fr.neatmonster.nocheatplus.permissions.PermissionPolicy;
import fr.neatmonster.nocheatplus.permissions.PermissionSettings;
import fr.neatmonster.nocheatplus.permissions.PermissionSettings.PermissionRule;

public class TestPermissionSettings {

    @Test
    public void testRegex() {
        PermissionPolicy dummy = new PermissionPolicy();
        String regex = "^nocheatplus\\.checks\\..*\\.silent$";
        String permissionName = "nocheatplus.checks.moving.survivalfly.silent";

        assertTrue("Expect regex to match.", permissionName.matches(regex));

        PermissionRule rule = PermissionSettings.getMatchingRule("regex:" + regex, dummy);
        assertNotNull("Expect factory to return a regex rule.", rule);

        assertTrue("Expect rule to match permissions name.", rule.matches(permissionName));
        assertFalse("Rule matches wrong start.", rule.matches("xy" + permissionName));
        assertFalse("Rule matches wrong end.", rule.matches(permissionName + "yx"));
    }
}
