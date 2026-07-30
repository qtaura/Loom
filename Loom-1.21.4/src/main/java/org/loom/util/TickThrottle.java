package org.loom.util;

import com.zenith.util.timer.Timer;
import com.zenith.util.timer.Timers;

/**
 * Rate limiting helper using ZenithProxy's tick-aligned timer.
 *
 * <p>Usage:
 * <pre>
 * TickThrottle throttle = new TickThrottle(20);  // once per second
 * if (throttle.ready()) {
 *     // perform action
 * }
 * </pre>
 */
public class TickThrottle {

    private final Timer timer;
    private final long delayTicks;

    /**
     * Creates a throttle that returns true at most once per {@code delayTicks} ticks.
     *
     * @param delayTicks minimum ticks between true returns (20 ticks = 1 second)
     */
    public TickThrottle(long delayTicks) {
        this.timer = Timers.tickTimer();
        this.delayTicks = delayTicks;
    }

    /**
     * Returns true if the throttle period has elapsed since the last true return.
     * Resets the internal timer on true.
     */
    public boolean ready() {
        return timer.tick(delayTicks);
    }

    /**
     * Resets the throttle timer so the next {@link #ready()} will return true.
     */
    public void skip() {
        timer.skip();
    }

    /**
     * Resets the throttle timer to the current tick.
     */
    public void reset() {
        timer.reset();
    }
}
