package fr.neatmonster.nocheatplus.event.mini;

import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import fr.neatmonster.nocheatplus.components.registry.order.RegistrationOrder;

public class EventRegistryBukkitView implements IEventRegistry {

    private final EventRegistryBukkit delegate;

    public EventRegistryBukkitView(EventRegistryBukkit delegate) {
        this.delegate = delegate;
    }

    @Override
    public void register(Listener listener) {
        delegate.register(listener);
    }

    @Override
    public void register(Listener listener, RegistrationOrder order) {
        delegate.register(listener, order);
    }

    @Override
    public void register(Listener listener, Plugin plugin) {
        delegate.register(listener, plugin);
    }

    @Override
    public void register(Listener listener, RegistrationOrder order, Plugin plugin) {
        delegate.register(listener, order, plugin);
    }

    @Override
    public <E extends Event> void register(Class<E> eventClass, MiniListener<E> listener) {
        delegate.register(eventClass, listener);
    }

    @Override
    public <E extends Event> void register(MiniListener<E> listener) {
        delegate.register(listener);
    }
}
