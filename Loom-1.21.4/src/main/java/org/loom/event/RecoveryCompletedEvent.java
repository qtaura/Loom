package org.loom.event;

import org.loom.recovery.RecoveryReason;

/**
 * Emitted when a recovery action completes successfully.
 */
public record RecoveryCompletedEvent(RecoveryReason reason) implements LoomEvent {}
