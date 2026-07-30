package org.loom.event;

/**
 * Emitted when progress state is loaded from disk, typically at plugin startup
 * or job resumption.
 */
public record StateLoadedEvent(String jobId, int placedBlocks, int totalBlocks) implements LoomEvent {}
