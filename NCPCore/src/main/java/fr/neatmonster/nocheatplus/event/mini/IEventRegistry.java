package fr.neatmonster.nocheatplus.event.mini;

import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import fr.neatmonster.nocheatplus.components.registry.order.RegistrationOrder;

public interface IEventRegistry {
    void register(Listener listener);
    void register(Listener listener, RegistrationOrder order);
    void register(Listener listener, Plugin plugin);
    void register(Listener listener, RegistrationOrder order, Plugin plugin);
    <E extends Event> void register(Class<E> eventClass, MiniListener<E> listener);
    <E extends Event> void register(MiniListener<E> listener);
}
