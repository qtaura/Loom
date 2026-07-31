package org.loom.scheduling;

import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.DropItem;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.DropItemAction;
import org.loom.inventory.LoomInventoryManager;
import org.loom.log.LoomLogger;
import org.loom.schematic.Schematic;
import org.loom.schematic.SchematicManager;
import org.loom.util.Material;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.INVENTORY;

/**
 * Nerv-compatible inventory dumping task.
 *
 * <p>Matches Nerv's behavior: scans the schematic palette to determine
 * which materials to keep, and drops everything else.
 * One item per tick with configurable delay.
 *
 * <p>When dumping completes, invokes the provided callback.
 * This integrates with BatchOrchestrator's dump→reset→next chain.
 */
public class DumpTask extends Task {

    private static final int DUMP_PRIORITY = 5000;
    private static final int ACTION_DELAY_TICKS = 2;

    private final SchematicManager schematicManager;
    private final LoomInventoryManager inventoryManager;
    private final LoomLogger logger;
    private final Runnable onComplete;
    private final String schematicId;

    private Set<String> keepMaterials;
    private int currentSlot;
    private boolean anyDumped;

    public DumpTask(SchematicManager schematicManager,
                     LoomInventoryManager inventoryManager,
                     LoomLogger logger,
                     String schematicId,
                     Runnable onComplete) {
        super("DumpTask");
        this.schematicManager = schematicManager;
        this.inventoryManager = inventoryManager;
        this.logger = logger;
        this.schematicId = schematicId;
        this.onComplete = onComplete;
    }

    @Override
    public void onStart() {
        // Build keep set from schematic palette — matches Nerv's getRequiredItems()
        keepMaterials = new HashSet<>();
        Optional<Schematic> schematic = schematicManager.getSchematic(schematicId);
        if (schematic.isPresent()) {
            Map<Integer, org.loom.schematic.SchematicPalette> palette = schematic.get().getPalette();
            for (var entry : palette.values()) {
                keepMaterials.add(entry.getMaterial());
            }
        }

        inventoryManager.refresh();
        currentSlot = 0;
        anyDumped = false;
    }

    @Override
    public void tick() {
        if (currentSlot >= 45) {
            if (!anyDumped) {
                logger.info("DumpTask", "No items to dump");
            }
            inventoryManager.refresh();
            if (onComplete != null) onComplete.run();
            return;
        }

        var container = CACHE.getPlayerCache().getInventoryCache().getPlayerInventory();
        var stack = container.getItemStack(currentSlot);

        currentSlot++;

        if (stack == null) return;

        var itemData = com.zenith.mc.item.ItemRegistry.REGISTRY.get(stack.getId());
        if (itemData == null) return;

        String id = "minecraft:" + itemData.name();
        Material material = Material.fromIdentifier(id);

        // Keep schematic materials
        if (material != null && keepMaterials.contains(id)) return;

        // Drop
        INVENTORY.submit(InventoryActionRequest.builder()
            .owner(this)
            .priority(DUMP_PRIORITY)
            .actionDelayTicks(ACTION_DELAY_TICKS)
            .actions(new DropItem(currentSlot - 1, DropItemAction.DROP_SELECTED_STACK))
            .build());

        anyDumped = true;
        logger.debug("DumpTask", "Dumped: %s", itemData.name());
    }

    @Override
    public void onPause() {
        if (onComplete != null) onComplete.run();
    }

    @Override
    public void onComplete() {
        inventoryManager.refresh();
    }

    @Override
    public void onFail(Throwable cause) {
        inventoryManager.refresh();
        if (onComplete != null) onComplete.run();
    }

    @Override
    public boolean isComplete() {
        return currentSlot >= 45;
    }
}
