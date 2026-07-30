package org.loom.event;

/**
 * Emitted when a hostile entity or player is detected nearby.
 */
public record CombatDetectedEvent(String threatName, double threatX, double threatZ, float distance) implements LoomEvent {}
