package org.loom.event;

/**
 * Emitted when the printer starts a new build job.
 */
public record PrintStartedEvent(String jobId, String schematicId) implements LoomEvent {}
