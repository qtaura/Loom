package org.loom.event;

import org.loom.inventory.MaterialRequest;

import java.util.List;

/**
 * Emitted when material counts fall below the restock threshold.
 * The TaskScheduler should respond by scheduling a RestockTask.
 */
public record RestockNeededEvent(List<MaterialRequest> materials) implements LoomEvent {}
