package org.loom.util;

import org.loom.event.LoomEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A simple thread-safe event bus for Loom-internal events.
 *
 * <p>Separate from ZenithProxy's global {@code EVENT_BUS} to avoid
 * polluting it with Loom-specific event types and to keep internal
 * communication decoupled.
 *
 * <p>Usage:
 * <pre>
 * AsyncLoomEventBus bus = new AsyncLoomEventBus();
 * bus.subscribe(PrintCompletedEvent.class, event -> { ... });
 * bus.publish(new PrintCompletedEvent("job1", 1000, 12.5));
 * </pre>
 */
public class AsyncLoomEventBus {

    private final Map<Class<? extends LoomEvent>, List<Consumer<? extends LoomEvent>>> listeners;

    public AsyncLoomEventBus() {
        this.listeners = new ConcurrentHashMap<>();
    }

    /**
     * Registers a listener for a specific event type.
     *
     * @param eventType the event class to listen for
     * @param listener  the callback to invoke when the event is published
     * @param <T>       the event type
     */
    public <T extends LoomEvent> void subscribe(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * Publishes an event to all registered listeners of that type.
     *
     * <p>Listeners are called synchronously on the publishing thread.
     * If asynchronous dispatch is needed, the listener should handle it.
     *
     * @param event the event to publish
     * @param <T>   the event type
     */
    @SuppressWarnings("unchecked")
    public <T extends LoomEvent> void publish(T event) {
        List<Consumer<? extends LoomEvent>> eventListeners = listeners.get(event.getClass());
        if (eventListeners != null) {
            for (Consumer<? extends LoomEvent> listener : eventListeners) {
                try {
                    ((Consumer<T>) listener).accept(event);
                } catch (Exception e) {
                    // TODO: Log listener exception
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Unregisters all listeners for a specific event type.
     */
    public void unsubscribe(Class<? extends LoomEvent> eventType) {
        listeners.remove(eventType);
    }

    /**
     * Unregisters all listeners for all event types.
     */
    public void clear() {
        listeners.clear();
    }
}
