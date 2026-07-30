package org.loom;

import com.github.rfresh2.EventConsumer;
import com.zenith.event.client.ClientBotTick;
import com.zenith.module.api.Module;

import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static org.loom.LoomPlugin.CONFIG;

/**
 * Main ZenithProxy module for Loom.
 *
 * This module subscribes to the bot tick loop and advances the
 * {@link org.loom.scheduling.TaskScheduler} one step per tick.
 * It is the bridge between ZenithProxy's module lifecycle and
 * Loom's internal orchestration.
 */
public class LoomModule extends Module {

    // TODO: Inject subsystems via constructor or setter
    // private final TaskScheduler taskScheduler;
    // private final RecoverySystem recoverySystem;

    @Override
    public boolean enabledSetting() {
        // TODO: Reference a specific config boolean for module enable
        return true;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::handleBotTick),
            of(ClientBotTick.Starting.class, this::handleBotTickStarting),
            of(ClientBotTick.Stopped.class, this::handleBotTickStopped)
        );
    }

    @Override
    public void onEnable() {
        // TODO: Initialize all subsystems
        // TODO: Load progress from disk
        // TODO: If an active job exists, submit a PrintTask to the TaskScheduler
    }

    @Override
    public void onDisable() {
        // TODO: Save progress to disk
        // TODO: Shutdown all subsystems
        // TODO: Cancel all active tasks
    }

    private void handleBotTickStarting(ClientBotTick.Starting event) {
        // TODO: Reset transient state when bot control begins
    }

    private void handleBotTickStopped(ClientBotTick.Stopped event) {
        // TODO: Pause active task and save progress when bot control stops
    }

    private void handleBotTick(ClientBotTick event) {
        // TODO: recoverySystem.tick();
        // TODO: taskScheduler.tick();
    }
}
