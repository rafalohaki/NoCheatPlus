package fr.neatmonster.nocheatplus.compat.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

import java.util.Collections;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

public class TestNSBukkitAttributeAccess {

    @Test
    public void testGetSpeedAttributeMultiplierNull() {
        // Get instance via factory method.
        NSBukkitAttributeAccess access = NSBukkitAttributeAccess.createIfSupported();
        // Skip this test if the class is not supported in the test environment.
        Assumptions.assumeTrue(access != null);
        
        ServerMock server = MockBukkit.mock();
        try {
            PlayerMock base = server.addPlayer();
            Player player = spy(base);
            doReturn(null).when(player).getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            assertEquals(1.0, access.getSpeedAttributeMultiplier(player), 0.0);
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    public void testGetSprintAttributeMultiplierNull() {
        // Get instance via factory method.
        NSBukkitAttributeAccess access = NSBukkitAttributeAccess.createIfSupported();
        // Skip this test if the class is not supported in the test environment.
        Assumptions.assumeTrue(access != null);
        
        ServerMock server = MockBukkit.mock();
        try {
            PlayerMock base = server.addPlayer();
            Player player = spy(base);
            doReturn(null).when(player).getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            assertEquals(1.0, access.getSprintAttributeMultiplier(player), 0.0);
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    public void testNormalSpeed() {
        // Get instance via factory method.
        NSBukkitAttributeAccess access = NSBukkitAttributeAccess.createIfSupported();
        // Skip this test if the class is not supported in the test environment.
        Assumptions.assumeTrue(access != null);
        
        ServerMock server = MockBukkit.mock();
        try {
            PlayerMock base = server.addPlayer();
            Player player = spy(base);
            AttributeInstance inst = mock(AttributeInstance.class);
            doReturn(inst).when(player).getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            when(inst.getValue()).thenReturn(0.1);
            when(inst.getBaseValue()).thenReturn(0.1);
            when(inst.getModifiers()).thenReturn(Collections.emptySet());
            assertEquals(1.0, access.getSpeedAttributeMultiplier(player), 0.0);
            assertEquals(1.0, access.getSprintAttributeMultiplier(player), 0.0);
        } finally {
            MockBukkit.unmock();
        }
    }
}
