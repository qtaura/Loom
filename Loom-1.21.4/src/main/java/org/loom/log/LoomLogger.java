package org.loom.log;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

/**
 * Structured logging facade wrapping ZenithProxy's {@link ComponentLogger}.
 *
 * <p>Provides subsystem-tagged log methods and structured metric emission.
 */
public class LoomLogger {

    private final ComponentLogger logger;

    public LoomLogger(ComponentLogger logger) {
        this.logger = logger;
    }

    /**
     * Logs an info-level message with a subsystem tag.
     *
     * @param tag     the subsystem tag (e.g. "Printer", "Recovery")
     * @param message the log message
     * @param args    format arguments
     */
    public void info(String tag, String message, Object... args) {
        logger.info("[{}] " + message, tag, args);
    }

    /**
     * Logs a debug-level message with a subsystem tag.
     */
    public void debug(String tag, String message, Object... args) {
        logger.debug("[{}] " + message, tag, args);
    }

    /**
     * Logs a warning-level message with a subsystem tag.
     */
    public void warn(String tag, String message, Object... args) {
        logger.warn("[{}] " + message, tag, args);
    }

    /**
     * Logs an error-level message with a subsystem tag and optional throwable.
     */
    public void error(String tag, String message, Throwable throwable) {
        logger.error("[{}] " + message, tag, throwable);
    }

    /**
     * Emits a structured metric log entry.
     *
     * @param name  the metric name
     * @param value the metric value
     * @param tags  additional tags (e.g. "job=map1", "row=5")
     */
    public void metric(String name, double value, String... tags) {
        // TODO: Structured metric logging
        logger.debug("[Metrics] {} = {} tags={}", name, value, String.join(",", tags));
    }
}
