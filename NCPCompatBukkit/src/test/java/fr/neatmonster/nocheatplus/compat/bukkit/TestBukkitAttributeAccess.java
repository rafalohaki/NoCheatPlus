package fr.neatmonster.nocheatplus.compat.bukkit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.junit.Assume;
import org.junit.Test;

public class TestBukkitAttributeAccess {

    @Test
    public void testGetSpeedAttributeMultiplierNull() {
        // Get instance via factory method.
        BukkitAttributeAccess access = BukkitAttributeAccess.createIfSupported();
        // Skip this test if the class is not supported in the test environment.
        Assume.assumeNotNull(access);
        
        Player player = mock(Player.class);
        when(player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)).thenReturn(null);
        assertEquals(1.0, access.getSpeedAttributeMultiplier(player), 0.0);
    }

    @Test
    public void testGetSprintAttributeMultiplierNull() {
        // Get instance via factory method.
        BukkitAttributeAccess access = BukkitAttributeAccess.createIfSupported();
        // Skip this test if the class is not supported in the test environment.
        Assume.assumeNotNull(access);
        
        Player player = mock(Player.class);
        when(player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)).thenReturn(null);
        assertEquals(1.0, access.getSprintAttributeMultiplier(player), 0.0);
    }

    @Test
    public void testNormalSpeed() {
        // Get instance via factory method.
        BukkitAttributeAccess access = BukkitAttributeAccess.createIfSupported();
        // Skip this test if the class is not supported in the test environment.
        Assume.assumeNotNull(access);
        
        Player player = mock(Player.class);
        AttributeInstance inst = mock(AttributeInstance.class);
        when(player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)).thenReturn(inst);
        when(inst.getValue()).thenReturn(0.1);
        when(inst.getBaseValue()).thenReturn(0.1);
        when(inst.getModifiers()).thenReturn(Collections.emptySet());
        assertEquals(1.0, access.getSpeedAttributeMultiplier(player), 0.0);
        assertEquals(1.0, access.getSprintAttributeMultiplier(player), 0.0);
    }
}
