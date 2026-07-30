package org.loom;

/**
 * Root configuration for Loom.
 *
 * Configurations are saved and loaded automatically by ZenithProxy to JSON files.
 * All fields should be public and mutable.
 * Fields typed to static inner classes generate nested JSON objects.
 *
 * <p>This config is registered in {@link LoomPlugin#onLoad} via
 * {@code pluginAPI.registerConfig("loom", LoomConfig.class)}.
 */
public class LoomConfig {

    // ---------------------------------------
    // Build settings
    // ---------------------------------------

    /** Path to the schematic file to build. */
    public String schematicPath = "schematics/map_art.litematic";

    /** World X origin of the build area. */
    public int buildOriginX = 0;

    /** World Y origin (base layer) of the build area. */
    public int buildOriginY = 64;

    /** World Z origin of the build area. */
    public int buildOriginZ = 0;

    /** Number of ticks to wait between consecutive block placements. */
    public int placementDelayTicks = 4;

    /** Maximum number of placement retries before giving up on a block. */
    public int maxPlacementRetries = 3;

    /** Whether to verify placement via world scan after placing. */
    public boolean verifyPlacements = true;

    // ---------------------------------------
    // Storage / Restock settings
    // ---------------------------------------

    /** X coordinate of the restock chest area. */
    public int storageX = 0;

    /** Y coordinate of the restock chest area. */
    public int storageY = 64;

    /** Z coordinate of the restock chest area. */
    public int storageZ = 0;

    /** When a material count drops below this threshold, trigger a restock. */
    public int restockThreshold = 16;

    // ---------------------------------------
    // Recovery settings
    // ---------------------------------------

    /** Maximum number of recovery attempts before pausing the job. */
    public int maxRecoveryAttempts = 5;

    /** Distance (in blocks) to flee when combat is detected. */
    public int combatFleeDistance = 128;

    /** Delay in seconds to wait before reconnecting after a disconnect. */
    public int reconnectDelaySeconds = 30;

    // ---------------------------------------
    // Anti-AFK settings
    // ---------------------------------------

    /** Whether anti-AFK is enabled when idle. */
    public boolean antiAfkEnabled = true;

    /** Interval in ticks between anti-AFK movements. */
    public int antiAfkIntervalTicks = 200;

    // ---------------------------------------
    // Debug settings
    // ---------------------------------------

    /** Whether debug-level logging is enabled. */
    public boolean debugEnabled = false;

    /** Whether to emit metric log entries. */
    public boolean metricsEnabled = true;
}
