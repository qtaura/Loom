package org.loom.event;

/**
 * Emitted when the navigator detects the bot is stuck.
 */
public record BotStuckEvent(int stuckX, int stuckZ, long stuckDurationTicks) implements LoomEvent {}
