package org.loom.navigation;

/**
 * Default implementation of {@link Navigator}.
 *
 * <p>Wraps ZenithProxy's BARITONE pathfinder for long-range movement
 * and uses {@code BARITONE.thisWay()} for short stepping.
 */
public class LoomNavigator implements Navigator {

    private final int storageX;
    private final int storageY;
    private final int storageZ;
    private final int buildOriginX;
    private final int buildOriginZ;
    private int targetX;
    private int targetZ;

    public LoomNavigator(int storageX, int storageY, int storageZ, int buildOriginX, int buildOriginZ) {
        this.storageX = storageX;
        this.storageY = storageY;
        this.storageZ = storageZ;
        this.buildOriginX = buildOriginX;
        this.buildOriginZ = buildOriginZ;
        this.targetX = -1;
        this.targetZ = -1;
    }

    @Override
    public NavigationResult goTo(int x, int z) {
        // TODO: Set target
        // TODO: Call BARITONE.pathTo(x, z)
        // TODO: Monitor BARITONE.isActive() for completion or stuck
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public NavigationResult goToStorage() {
        // TODO: Navigate to configured storage coordinates
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public NavigationResult goToBuildArea() {
        // TODO: Navigate back to build origin
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public NavigationResult moveToPlacementPosition(int worldX, int worldY, int worldZ, String face) {
        // TODO: Calculate the exact player position needed to reach the target face
        // TODO: Navigate to that position
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public boolean isNavigating() {
        // TODO: Return BARITONE.isActive()
        return false;
    }

    @Override
    public boolean isStuck() {
        // TODO: Track position history and detect lack of progress while BARITONE is active
        return false;
    }

    @Override
    public void cancel() {
        // TODO: Call BARITONE.stop()
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public int[] getCurrentPathTarget() {
        return new int[]{targetX, targetZ};
    }
}
