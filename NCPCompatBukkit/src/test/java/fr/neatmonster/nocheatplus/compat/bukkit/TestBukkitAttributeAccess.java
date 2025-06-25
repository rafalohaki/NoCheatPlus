package fr.neatmonster.nocheatplus.compat.bukkit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.junit.Test;

public class TestBukkitAttributeAccess {

    @Test
    public void testGetSpeedAttributeMultiplierNull() {
        Player player = mock(Player.class);
        when(player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)).thenReturn(null);
        BukkitAttributeAccess access = new BukkitAttributeAccess();
        assertEquals(Double.MAX_VALUE, access.getSpeedAttributeMultiplier(player), 0.0);
    }

    @Test
    public void testGetSprintAttributeMultiplierNull() {
        Player player = mock(Player.class);
        when(player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)).thenReturn(null);
        BukkitAttributeAccess access = new BukkitAttributeAccess();
        assertEquals(Double.MAX_VALUE, access.getSprintAttributeMultiplier(player), 0.0);
    }

    @Test
    public void testNormalSpeed() {
        Player player = mock(Player.class);
        AttributeInstance inst = mock(AttributeInstance.class);
        when(player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)).thenReturn(inst);
        when(inst.getValue()).thenReturn(0.1);
        when(inst.getBaseValue()).thenReturn(0.1);
        when(inst.getModifiers()).thenReturn(Collections.emptySet());
        BukkitAttributeAccess access = new BukkitAttributeAccess();
        assertEquals(1.0, access.getSpeedAttributeMultiplier(player), 0.0);
        assertEquals(1.0, access.getSprintAttributeMultiplier(player), 0.0);
    }
}
