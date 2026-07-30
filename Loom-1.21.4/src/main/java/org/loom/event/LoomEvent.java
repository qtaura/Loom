package org.loom.event;

/**
 * Marker interface for all Loom-specific events.
 *
 * <p>Loom has its own internal event bus ({@link org.loom.util.AsyncLoomEventBus})
 * for subsystem communication, separate from ZenithProxy's global event bus.
 */
public interface LoomEvent {
}
