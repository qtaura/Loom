package org.loom.event;

/**
 * Emitted when the printer resumes after being paused.
 */
public record PrintResumedEvent(String jobId, int resumedAtX, int resumedAtY) implements LoomEvent {}
