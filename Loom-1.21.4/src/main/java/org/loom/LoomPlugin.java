package org.loom;

import com.zenith.plugin.api.Plugin;
import com.zenith.plugin.api.PluginAPI;
import com.zenith.plugin.api.ZenithProxyPlugin;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.loom.command.BuildCommand;
import org.loom.command.ConfigCommand;
import org.loom.command.SchematicCommand;
import org.loom.command.StatusCommand;
import org.loom.inventory.LoomInventoryManager;
import org.loom.inventory.LoomInventoryManagerImpl;
import org.loom.inventory.restock.ChestRestocker;
import org.loom.inventory.restock.LoomChestRestocker;
import org.loom.jobs.JobManager;
import org.loom.jobs.LoomJobManager;
import org.loom.log.LoomLogger;
import org.loom.navigation.LoomNavigator;
import org.loom.navigation.Navigator;
import org.loom.printing.LoomPlacementEngine;
import org.loom.printing.LoomPrinterController;
import org.loom.printing.PlacementEngine;
import org.loom.printing.PrinterController;
import org.loom.printing.PrintStrategy;
import org.loom.printing.RowMajorStrategy;
import org.loom.recovery.LoomRecoverySystem;
import org.loom.recovery.RecoverySystem;
import org.loom.scanning.LoomWorldScanner;
import org.loom.scanning.WorldScanner;
import org.loom.scheduling.LoomTaskScheduler;
import org.loom.scheduling.TaskScheduler;
import org.loom.schematic.LoomSchematicManager;
import org.loom.schematic.SchematicManager;
import org.loom.state.LoomProgressTracker;
import org.loom.state.ProgressStore;
import org.loom.state.ProgressTracker;
import org.loom.util.AsyncLoomEventBus;

/**
 * Loom plugin entry point.
 *
 * <p>Loom is a ZenithProxy plugin that fully automates carpet map art construction
 * on anarchy servers. It handles schematic loading, precise block placement,
 * inventory management, restocking from chests, and recovery from all failure
 * modes while persisting progress so work can always resume.
 *
 * <h2>Dependency Injection</h2>
 * <p>All subsystems are constructed here and passed via constructor injection.
 * There is no DI framework — wiring is explicit in {@link #onLoad(PluginAPI)}.
 */
@Plugin(
    id = BuildConstants.PLUGIN_ID,
    version = BuildConstants.VERSION,
    description = "Automated carpet map art construction for ZenithProxy",
    url = "https://github.com/qtaura/Loom",
    authors = {"qtaura"},
    mcVersions = {BuildConstants.MC_VERSION}
)
public class LoomPlugin implements ZenithProxyPlugin {

    // Global singletons for static access from commands
    public static LoomConfig CONFIG;
    public static ComponentLogger LOG;

    // Subsystems (package-private for module access)
    static AsyncLoomEventBus eventBus;
    static LoomLogger loomLogger;
    static SchematicManager schematicManager;
    static ProgressTracker progressTracker;
    static TaskScheduler taskScheduler;
    static JobManager jobManager;
    static PrinterController printerController;
    static PlacementEngine placementEngine;
    static Navigator navigator;
    static LoomInventoryManager inventoryManager;
    static ChestRestocker chestRestocker;
    static RecoverySystem recoverySystem;
    static WorldScanner worldScanner;
    static LoomModule loomModule;

    @Override
    public void onLoad(PluginAPI pluginAPI) {
        LOG = pluginAPI.getLogger();
        LOG.info("Loom loading...");

        // --- Step 1: Load configuration ---
        CONFIG = pluginAPI.registerConfig(BuildConstants.PLUGIN_ID, LoomConfig.class);

        // --- Step 2: Create cross-cutting services ---
        eventBus = new AsyncLoomEventBus();
        loomLogger = new LoomLogger(LOG);

        // --- Step 3: Create leaf services (no dependencies) ---
        schematicManager = new LoomSchematicManager(loomLogger);

        ProgressStore progressStore = new ProgressStore();
        progressTracker = new LoomProgressTracker(progressStore);

        // --- Step 4: Create mid-level services ---
        worldScanner = new LoomWorldScanner(schematicManager, loomLogger);

        navigator = new LoomNavigator(
            CONFIG.storageX, CONFIG.storageY, CONFIG.storageZ,
            CONFIG.buildOriginX, CONFIG.buildOriginZ,
            loomLogger
        );

        inventoryManager = new LoomInventoryManagerImpl(CONFIG.restockThreshold);

        placementEngine = new LoomPlacementEngine(
            worldScanner,
            inventoryManager,
            loomLogger
        );
        chestRestocker = new LoomChestRestocker();

        // --- Step 5: Create orchestration services ---
        PrintStrategy printStrategy = new RowMajorStrategy();
        printerController = new LoomPrinterController(
            navigator,
            worldScanner,
            placementEngine,
            schematicManager,
            inventoryManager,
            progressTracker,
            loomLogger,
            eventBus,
            printStrategy,
            CONFIG.maxPlacementRetries,
            CONFIG.placementDelayTicks
        );

        taskScheduler = new LoomTaskScheduler();

        jobManager = new LoomJobManager();

        recoverySystem = new LoomRecoverySystem(
            CONFIG.maxRecoveryAttempts,
            CONFIG.combatFleeDistance
        );

        // --- Step 6: Create and register the module ---
        loomModule = new LoomModule();
        pluginAPI.registerModule(loomModule);

        // --- Step 7: Register commands ---
        pluginAPI.registerCommand(new BuildCommand());
        pluginAPI.registerCommand(new SchematicCommand());
        pluginAPI.registerCommand(new StatusCommand());
        pluginAPI.registerCommand(new ConfigCommand());

        LOG.info("Loom loaded.");
    }
}
