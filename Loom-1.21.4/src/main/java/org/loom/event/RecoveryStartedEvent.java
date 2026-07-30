package org.loom.event;

import org.loom.recovery.RecoveryReason;

/**
 * Emitted when the recovery system begins a recovery action.
 */
public record RecoveryStartedEvent(RecoveryReason reason) implements LoomEvent {}
